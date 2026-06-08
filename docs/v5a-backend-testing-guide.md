# US006 V5A Testing Guide: Refined Behavior Profile From Paper Trades

## Goal

Verify that V5A behavior fields are recalculated from paper-trading data only, using existing backend stock analysis snapshots where available.

V5A must preserve these rules:

- `GET /api/stocks/ai-suggestions` is strictly read-only.
- Behavior recalculation does not call OpenAI.
- Behavior recalculation does not create stock analysis snapshots.
- Risk category comes from latest existing `stock_analysis_snapshot.risk_category`, then metadata fallback.
- Volatility exposure comes from latest existing `stock_analysis_snapshot.volatility_label` only.
- `holding_period_score` stays `NULL` in V5A.

## 1. Start Backend

```powershell
cd C:\Users\lim\.codex\worktrees\3d96\StockMentor\backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Set variables:

```powershell
$base = "http://localhost:8080"
$beginnerAuth = "demo@stockmentor.local:Demo@12345"
$basic = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($beginnerAuth))
$headers = @{ Authorization = "Basic $basic" }
```

Create a helper:

```powershell
function Invoke-PaperTrade {
  param([string]$Side, [string]$Symbol, [int]$Quantity)

  $body = @{ symbol = $Symbol; quantity = $Quantity } | ConvertTo-Json -Compress

  Invoke-RestMethod `
    -Method Post `
    -Uri "$base/api/paper-trading/$Side" `
    -Headers $headers `
    -ContentType "application/json" `
    -Body $body
}
```

## 2. Identify Demo User

```sql
SELECT user_id, email, username
FROM app_user
WHERE email = 'demo@stockmentor.local';
```

Use this value as `<DEMO_USER_ID>`.

## 3. Verify Source Data

Check stock prices:

```sql
SELECT symbol, current_price
FROM stock
WHERE symbol IN ('MSFT', 'KO', 'JNJ', 'NVDA', 'TSLA', 'AMD', 'AAPL', 'GOOG')
ORDER BY symbol;
```

Check latest analysis snapshots:

```sql
SELECT symbol, timeframe, risk_category, volatility_label, created_at
FROM stock_analysis_snapshot
WHERE timeframe = '7D'
  AND symbol IN ('MSFT', 'KO', 'JNJ', 'NVDA', 'TSLA', 'AMD', 'AAPL', 'GOOG')
ORDER BY symbol, created_at DESC;
```

Expected:

- Paper trades require `stock.current_price > 0`.
- `risk_category` may be missing; metadata fallback will be used.
- `volatility_label` may be missing; `volatility_exposure_score` can stay `NULL`.

## 4. Baseline Behavior Profile

```sql
SELECT behavior_profile_id, behavior_confidence, behavior_style,
       behavior_risk_score, stock_risk_exposure_score,
       volatility_exposure_score, average_position_size_percent,
       concentration_score, favorite_risk_category,
       most_traded_symbols, behavior_summary_text,
       turnover_score, turnover_level, holding_period_score, updated_at
FROM user_behavior_profile
WHERE user_id = <DEMO_USER_ID>
ORDER BY updated_at DESC
LIMIT 1;
```

Expected before trades:

- New fields may be `NULL`.
- `holding_period_score` should be `NULL`.

## 5. Minimal Medium-Confidence Test

Run at least 3 trades across at least 2 symbols:

```powershell
Invoke-PaperTrade -Side "buy" -Symbol "MSFT" -Quantity 3
Invoke-PaperTrade -Side "buy" -Symbol "KO"   -Quantity 4
Invoke-PaperTrade -Side "buy" -Symbol "NVDA" -Quantity 1
```

Query:

```sql
SELECT behavior_confidence, behavior_style,
       behavior_risk_score, stock_risk_exposure_score,
       volatility_exposure_score, average_position_size_percent,
       concentration_score, favorite_risk_category,
       most_traded_symbols, behavior_summary_text,
       turnover_score, turnover_level, holding_period_score, updated_at
FROM user_behavior_profile
WHERE user_id = <DEMO_USER_ID>
ORDER BY updated_at DESC
LIMIT 1;
```

Expected:

- `behavior_confidence` should be `MEDIUM`.
- `stock_risk_exposure_score` should be numeric if BUY risk can be resolved from snapshot or metadata.
- `favorite_risk_category` should match the highest BUY gross amount by risk category.
- `most_traded_symbols` should include the traded symbols.
- `behavior_summary_text` should be non-null.
- `holding_period_score` should remain `NULL`.

## 6. Confirm Distinct Formula Meanings

Use this query after several trades:

```sql
SELECT stock_risk_exposure_score,
       volatility_exposure_score,
       turnover_score,
       concentration_score,
       behavior_risk_score,
       favorite_risk_category,
       high_volatility_exposure
FROM user_behavior_profile
WHERE user_id = <DEMO_USER_ID>
ORDER BY updated_at DESC
LIMIT 1;
```

Expected:

- `stock_risk_exposure_score` reflects BUY gross amount weighted by risk category.
- `volatility_exposure_score` reflects BUY gross amount weighted by snapshot volatility label.
- `behavior_risk_score` is a weighted composite, so it should not simply copy stock risk.
- `volatility_exposure_score` may be `NULL` if no snapshot volatility labels exist.

## 7. SELL Behavior Test

Sell only shares the user owns:

```powershell
Invoke-PaperTrade -Side "sell" -Symbol "MSFT" -Quantity 1
Invoke-PaperTrade -Side "sell" -Symbol "KO"   -Quantity 1
```

Expected:

- `turnover_score` and `turnover_level` can change.
- `most_traded_symbols` can change because SELL transactions count.
- `concentration_score` and `average_position_size_percent` can change after positions change.
- `favorite_risk_category`, `stock_risk_exposure_score`, and `volatility_exposure_score` should not be directly driven by SELL gross amount.
- `holding_period_score` remains `NULL`.

## 8. Verify Positions And Account Equity

```sql
SELECT account_id, cash_balance, starting_cash, updated_at
FROM paper_trading_account
WHERE user_id = <DEMO_USER_ID>;

SELECT position_id, symbol, quantity, average_cost, total_cost, updated_at
FROM paper_position
WHERE user_id = <DEMO_USER_ID>
ORDER BY symbol;
```

Expected:

- `average_position_size_percent` uses account equity:
  `cash_balance + valid open position value`.
- Position value uses `quantity * stock.current_price`, falling back to `paper_position.total_cost`.
- If no valid open position value exists, `concentration_score` is `NULL`.

## 9. Verify Transactions

```sql
SELECT transaction_id, symbol, side, quantity,
       execution_price, gross_amount, executed_at
FROM paper_trade_transaction
WHERE user_id = <DEMO_USER_ID>
ORDER BY executed_at DESC;
```

Expected:

- BUY and SELL rows are immutable history.
- BUY rows drive stock risk, favorite risk category, and volatility exposure.
- BUY and SELL rows both drive turnover, confidence, and most traded symbols.

## 10. Verify GET Suggestions Is Read-Only

Before GET:

```sql
SELECT updated_at
FROM user_behavior_profile
WHERE user_id = <DEMO_USER_ID>
ORDER BY updated_at DESC
LIMIT 1;
```

Call GET:

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "$base/api/stocks/ai-suggestions" `
  -Headers $headers
```

Run the timestamp query again.

Expected:

- `updated_at` does not change.
- No behavior recalculation.
- No paper trade creation.
- No suggestion batch creation.
- No OpenAI call.

## 11. Verify Suggestion Hash Reacts To Behavior

After successful trades, call refresh:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "$base/api/stocks/ai-suggestions/refresh" `
  -Headers $headers
```

Then check batches:

```sql
SELECT suggestion_batch_id, status, trigger_reason,
       prompt_version, input_hash, created_at
FROM stock_ai_suggestion_batch
WHERE user_id = <DEMO_USER_ID>
ORDER BY suggestion_batch_id DESC
LIMIT 3;
```

Expected:

- If behavior fields changed since the previous generation input, `input_hash` can change.
- If inputs are unchanged, the backend can reuse an existing batch.
- Manual cooldown may block repeated refresh attempts.

## 12. Invalid Trade Check

```powershell
try {
  Invoke-PaperTrade -Side "buy" -Symbol "META" -Quantity 1
} catch {
  $_.ErrorDetails.Message
}

try {
  Invoke-PaperTrade -Side "buy" -Symbol "MSFT" -Quantity 0
} catch {
  $_.ErrorDetails.Message
}
```

Expected:

- HTTP 400.
- No transaction row is saved.
- `user_behavior_profile.updated_at` does not change.

## Acceptance Checklist

- BUY updates behavior profile.
- SELL updates behavior profile.
- Snapshot risk category is preferred over metadata fallback.
- Metadata risk category works when snapshot risk is missing.
- Volatility exposure uses snapshot volatility labels only.
- `behavior_risk_score`, `stock_risk_exposure_score`, and `volatility_exposure_score` can differ.
- `favorite_risk_category` is driven by BUY gross amount only.
- `most_traded_symbols` is driven by BUY and SELL transaction count, then gross amount.
- `average_position_size_percent` uses account equity.
- `concentration_score` uses current valid open position values.
- `holding_period_score` remains `NULL`.
- GET suggestions remains read-only.
