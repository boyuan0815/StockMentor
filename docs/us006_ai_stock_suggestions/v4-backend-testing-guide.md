# US006 V4 Backend Testing Guide

This guide verifies the backend-only V4 upgrade for smarter AI stock suggestions.
V4 keeps V1/V2/V3 API responses compatible while adding candidate-fit prompt
signals and OpenAI JSON Schema structured output for stock suggestions.

## 1. Start Backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

If the local Maven wrapper launcher fails, use the wrapper-downloaded Maven
binary as documented in the V3 workflow.

```powershell
$base = "http://localhost:8080"
$beginnerAuth = "demo@stockmentor.local:Demo@12345"
```

## 2. GET Suggestions Remains Read-Only

```powershell
curl.exe -i -u $beginnerAuth "$base/api/stocks/ai-suggestions"
```

Expected:

- Response is `200` for the authenticated demo user.
- No OpenAI call is made.
- No behavior profile, suggestion batch, paper account, or paper trade is created.
- Existing stored suggestions can be returned.
- If no stored batch exists, `suggestedStocks` is empty and `remainingStocks` is populated.

## 3. Manual Refresh

```powershell
curl.exe -i -u $beginnerAuth -X POST "$base/api/stocks/ai-suggestions/refresh"
```

Expected with valid OpenAI configuration:

- New V4 batches use `promptVersion = stock-suggestion-v2` in the database.
- `batchStatus = SUCCESS` when OpenAI returns valid structured JSON.
- `fallbackUsed = false`.
- Logs mention `promptVersion=stock-suggestion-v2`, schema mode, behavior confidence, and candidate-fit top order.

Expected when OpenAI is unavailable or invalid:

- Existing successful cached batch is used if available.
- Cached-success fallback is recorded as `FALLBACK_CACHED` for the manual attempt, so refresh cooldown still applies.
- Otherwise `batchStatus = FALLBACK_RULE_BASED`.
- Request does not crash.

## 4. Cooldown Check

Run refresh again within one hour:

```powershell
curl.exe -i -u $beginnerAuth -X POST "$base/api/stocks/ai-suggestions/refresh"
```

Expected:

- No new OpenAI call.
- No new suggestion batch.
- `refreshAllowed = false`.
- `nextRefreshAllowedAt` is latest manual refresh `created_at + 1 hour`.

## 5. Paper Trade Behavior Change

Use the safer PowerShell file-body method:

```powershell
$body = @{ symbol = "MSFT"; quantity = 3 } | ConvertTo-Json -Compress
$body | Set-Content -Encoding utf8 .\paper-trade-request.json

curl.exe -i -u $beginnerAuth `
  -H "Content-Type: application/json" `
  --data-binary "@paper-trade-request.json" `
  "$base/api/paper-trading/buy"
```

Expected:

- Buy succeeds if stock data has a valid backend `currentPrice`.
- `user_behavior_profile` recalculates after the trade.
- A later suggestion refresh, once cooldown allows or through scheduled flow, uses the updated behavior summary in its input hash.

## 6. Schema Fallback

Schema fallback is normally verified by automated tests. Locally it can be
observed only if OpenAI rejects `response_format: json_schema` for the current
model/request.

Expected if that happens:

- Logs mention schema fallback.
- The backend retries once without `response_format`.
- Backend validation still runs.
- If retry fails, normal cached/rule-based fallback is used.

## 7. Scheduled Refresh Reuse

Scheduled refresh should continue to:

- Bypass manual cooldown.
- Compute input hash before calling OpenAI.
- Skip/reuse existing `SUCCESS` or `FALLBACK_RULE_BASED` batches when the V4 input hash is unchanged.
- Continue processing later users if one user fails.

## 8. MySQL Verification Queries

Suggestion batches:

```sql
SELECT suggestion_batch_id, user_id, status, trigger_reason, prompt_version,
       input_hash, model, error_message, created_at, expires_at
FROM stock_ai_suggestion_batch
ORDER BY suggestion_batch_id DESC;
```

Suggestion items:

```sql
SELECT suggestion_item_id, suggestion_batch_id, symbol, rank_no, match_score,
       risk_level, suggestion_label, status
FROM stock_ai_suggestion_item
ORDER BY suggestion_batch_id DESC, rank_no ASC;
```

Behavior profile:

```sql
SELECT behavior_profile_id, user_id, behavior_style, behavior_confidence,
       behavior_risk_score, stock_risk_exposure_score, turnover_level,
       concentration_level, analysis_start_date, analysis_end_date, updated_at
FROM user_behavior_profile
ORDER BY user_id, updated_at DESC;
```

Paper transactions:

```sql
SELECT transaction_id, user_id, symbol, side, quantity, execution_price,
       gross_amount, cash_balance_after, executed_at
FROM paper_trade_transaction
ORDER BY transaction_id DESC;
```

## 9. Automated Verification

```powershell
cd backend
.\mvnw.cmd clean test
```

If the wrapper launcher fails locally, use the wrapper-downloaded Maven binary
workaround and report it clearly.

Expected:

- Existing V1/V2/V3 tests pass.
- V4 OpenAI client tests pass.
- V4 candidate-fit and input-hash tests pass.
