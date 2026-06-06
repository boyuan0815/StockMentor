# US006 V5A Testing Guide: Behavior Profile From Paper Trades

## 1. Start Backend

```powershell
cd C:\Users\lim\.codex\worktrees\3d96\StockMentor\backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Set client variables:

```powershell
$base = "http://localhost:8080"
$beginnerAuth = "demo@stockmentor.local:Demo@12345"
$basic = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($beginnerAuth))
$headers = @{ Authorization = "Basic $basic" }
```

## 2. Find Demo User ID

```sql
SELECT user_id, email, username
FROM app_user
WHERE email = 'demo@stockmentor.local';
```

Use that `user_id` in the queries below.

## 3. Confirm Stock Prices Exist

```sql
SELECT symbol, current_price
FROM stock
WHERE symbol IN ('MSFT', 'KO', 'JNJ', 'NVDA', 'TSLA', 'AMD')
ORDER BY symbol;
```

Expected: every symbol you trade has `current_price > 0`.

## 4. Check Profile Before Trades

```sql
SELECT behavior_profile_id, behavior_confidence, behavior_style,
       behavior_risk_score, stock_risk_exposure_score,
       average_position_size_percent, volatility_exposure_score,
       favorite_risk_category, most_traded_symbols,
       behavior_summary_text, holding_period_score, updated_at
FROM user_behavior_profile
WHERE user_id = <DEMO_USER_ID>
ORDER BY updated_at DESC
LIMIT 1;
```

Expected before trades: new V5A fields may be `NULL`. That is normal.

## 5. Create Trade Helper

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

## 6. Minimal Proof Test

Run 3 BUY trades across 2+ symbols:

```powershell

sql:
        SELECT behavior_profile_id, behavior_confidence, behavior_style,
               behavior_risk_score, stock_risk_exposure_score,
               average_position_size_percent, volatility_exposure_score,
               favorite_risk_category, most_traded_symbols,
               behavior_summary_text, holding_period_score, updated_at
        FROM user_behavior_profile
        WHERE user_id = 1
        ORDER BY updated_at DESC
        LIMIT 1;

powershell:
        Invoke-PaperTrade -Side "buy" -Symbol "MSFT" -Quantity 3

powershell trigger behaviour changes:
        curl.exe -i -u $beginnerAuth -X POST "$base/api/stocks/ai-suggestions/refresh"

sql:
        SELECT behavior_profile_id, behavior_confidence, behavior_style,
               behavior_risk_score, stock_risk_exposure_score,
               average_position_size_percent, volatility_exposure_score,
               favorite_risk_category, most_traded_symbols,
               behavior_summary_text, holding_period_score, updated_at
        FROM user_behavior_profile
        WHERE user_id = 1
        ORDER BY updated_at DESC
        LIMIT 1;

```


```powershell

powershell:
        Invoke-PaperTrade -Side "buy" -Symbol "KO"   -Quantity 4

powershell trigger behaviour changes:
        curl.exe -i -u $beginnerAuth -X POST "$base/api/stocks/ai-suggestions/refresh"

sql:
        SELECT behavior_profile_id, behavior_confidence, behavior_style,
               behavior_risk_score, stock_risk_exposure_score,
               average_position_size_percent, volatility_exposure_score,
               favorite_risk_category, most_traded_symbols,
               behavior_summary_text, holding_period_score, updated_at
        FROM user_behavior_profile
        WHERE user_id = 1
        ORDER BY updated_at DESC
        LIMIT 1;

```

```powershell

powershell:
        Invoke-PaperTrade -Side "buy" -Symbol "NVDA" -Quantity 1

powershell trigger behaviour changes:
        curl.exe -i -u $beginnerAuth -X POST "$base/api/stocks/ai-suggestions/refresh"

sql:
        SELECT behavior_profile_id, behavior_confidence, behavior_style,
               behavior_risk_score, stock_risk_exposure_score,
               average_position_size_percent, volatility_exposure_score,
               favorite_risk_category, most_traded_symbols,
               behavior_summary_text, holding_period_score, updated_at
        FROM user_behavior_profile
        WHERE user_id = 1
        ORDER BY updated_at DESC
        LIMIT 1;

```

Then query:

```sql
SELECT behavior_confidence, behavior_style,
       behavior_risk_score, stock_risk_exposure_score,
       average_position_size_percent,
       volatility_exposure_score,
       favorite_risk_category,
       most_traded_symbols,
       behavior_summary_text,
       holding_period_score,
       updated_at
FROM user_behavior_profile
WHERE user_id = <DEMO_USER_ID>
ORDER BY updated_at DESC
LIMIT 1;
```

Expected:
- `behavior_confidence`: `MEDIUM`
- `favorite_risk_category`: should match the risk category with the highest BUY gross amount, not necessarily the highest quantity.
- `most_traded_symbols`: includes `KO`, `MSFT`, `NVDA`
- `behavior_summary_text`: non-null, meaningful text
- `volatility_exposure_score`: non-null number
- `average_position_size_percent`: non-null if open positions and prices exist
- `holding_period_score`: `NULL`, expected in V5A

## 7. Aggressive Pattern Test

```powershell
Invoke-PaperTrade -Side "buy" -Symbol "NVDA" -Quantity 5
Invoke-PaperTrade -Side "buy" -Symbol "TSLA" -Quantity 5
Invoke-PaperTrade -Side "buy" -Symbol "AMD"  -Quantity 5
```

Expected direction:
- `favorite_risk_category` changes only if the newly added BUY gross amount is enough to dominate total historical BUY gross amount.
- `behavior_risk_score` and `volatility_exposure_score` should increase.
- `most_traded_symbols` should include aggressive symbols if they are among the top traded.

## 8. Conservative Pattern Test

```powershell
Invoke-PaperTrade -Side "buy" -Symbol "KO"  -Quantity 10
Invoke-PaperTrade -Side "buy" -Symbol "JNJ" -Quantity 10
```

Expected direction:
- `favorite_risk_category` should move toward `conservative` if KO/JNJ BUY gross amount dominates.
- `behavior_risk_score` and `volatility_exposure_score` should decrease compared with aggressive-heavy trades.

## 9. SELL Behavior Test

Only sell shares you actually hold:

```powershell
Invoke-PaperTrade -Side "sell" -Symbol "MSFT" -Quantity 1
Invoke-PaperTrade -Side "sell" -Symbol "KO"   -Quantity 1
```

Expected:
- `turnover_level` / `turnover_score` may increase.
- `most_traded_symbols` may change because SELL transactions count.
- `favorite_risk_category` should not be directly driven by SELL gross amount.
- `holding_period_score` remains `NULL`.

## 10. Verify Transactions

```sql
SELECT transaction_id, user_id, symbol, side, quantity,
       execution_price, gross_amount, executed_at
FROM paper_trade_transaction
WHERE user_id = <DEMO_USER_ID>
ORDER BY executed_at DESC;
```

Expected: newest BUY/SELL records appear first.

## 11. Verify Positions

```sql
SELECT position_id, user_id, symbol, quantity,
       average_cost, total_cost, updated_at
FROM paper_position
WHERE user_id = <DEMO_USER_ID>
ORDER BY updated_at DESC;
```

Expected: BUY increases positions; SELL reduces them; full SELL removes the row.

## 12. Verify GET Suggestions Is Read-Only

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

Run the same SQL again.

Expected:
- `updated_at` does not change.
- No behavior recalculation.
- No paper trade.
- No suggestion batch creation.
- No OpenAI call.

## 13. Verify Behavior Affects Suggestion Hash

Before refresh:

```sql
SELECT suggestion_batch_id, status, prompt_version, input_hash, created_at
FROM stock_ai_suggestion_batch
WHERE user_id = <DEMO_USER_ID>
ORDER BY suggestion_batch_id DESC
LIMIT 3;
```

Refresh:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "$base/api/stocks/ai-suggestions/refresh" `
  -Headers $headers
```

After refresh:

```sql
SELECT suggestion_batch_id, status, trigger_reason,
       prompt_version, input_hash, created_at
FROM stock_ai_suggestion_batch
WHERE user_id = <DEMO_USER_ID>
ORDER BY suggestion_batch_id DESC
LIMIT 3;
```

Expected:
- If behavior fields changed since the previous batch, the computed `input_hash` should change.
- If input is unchanged, the backend may reuse the existing batch.
- Cooldown may block repeated manual refresh.

## 14. Invalid Trade Check

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
- No transaction row.
- Behavior profile `updated_at` does not change.

## Acceptance Checklist

- BUY creates transaction and updates profile.
- SELL creates transaction and updates profile.
- `favorite_risk_category` reflects BUY gross amount by risk category.
- `most_traded_symbols` reflects top traded symbols.
- `behavior_summary_text` becomes non-null after recalculation.
- `volatility_exposure_score` is non-null when BUY data exists.
- `average_position_size_percent` should become non-null when the user has open positions, valid prices, and portfolio value can be calculated.
- `holding_period_score` remains `NULL` in V5A.
- GET suggestions remains read-only.
- Invalid trades do not update behavior.