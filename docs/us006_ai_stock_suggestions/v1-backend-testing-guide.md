****# StockMentor V1 Backend Testing Guide

This guide covers the current V1 backend implementation only. Do not use these steps to add JWT, behavior profiling, virtual trading, order flows, or V2 features.

## 1. Run Backend With Dev Profile

From PowerShell:

```powershell
cd C:\Users\lim\.codex\worktrees\3d96\StockMentor\backend

$env:OPENAI_API_KEY="<YOUR_OPENAI_API_KEY>"

$env:SPRING_PROFILES_ACTIVE = "dev"
.\mvnw.cmd spring-boot:run
```

If the local Maven wrapper launch fails with `Cannot index into a null array` / `Cannot start maven from wrapper`, use the wrapper-downloaded Maven binary only as a temporary verification workaround.

Default local base URL:

```powershell
$base = "http://localhost:8080"
```

## 2. Dev Credentials

Beginner user:

```text
demo@stockmentor.local:Demo@12345
```

Admin user:

```text
admin@stockmentor.local:Admin@12345
```

These users are created only by `DevDataSeeder` under the `dev` profile.

## 3. Confirm Demo And Admin Users Exist

```sql
USE stockmentor;

SELECT user_id, email, username, role, status, is_deleted, onboarding_completed, created_at, updated_at
FROM app_user
WHERE email IN ('demo@stockmentor.local', 'admin@stockmentor.local')
ORDER BY user_id;
```

Expected:

```text
demo@stockmentor.local  role=BEGINNER_INVESTOR  status=ACTIVE  is_deleted=0  onboarding_completed=1
admin@stockmentor.local role=ADMIN              status=ACTIVE  is_deleted=0  onboarding_completed=0
```

Confirm beginner profile:

```sql
SELECT profile_id, user_id, risk_tolerance, investment_goal, experience_level,
       preferred_volatility, preferred_horizon, behavior_confidence,
       profile_source, profile_version, created_at, updated_at
FROM user_investment_profile
WHERE user_id = (
  SELECT user_id FROM app_user WHERE email = 'demo@stockmentor.local'
)
ORDER BY profile_version DESC, updated_at DESC;
```

## 4. Test Unauthenticated 401

```powershell
curl.exe -i "$base/api/stocks/ai-suggestions"
```

Expected:

```text
HTTP/1.1 401 Unauthorized
WWW-Authenticate: Basic ...
```

## 5. Authenticated GET Before Refresh

```powershell
$beginnerAuth = "demo@stockmentor.local:Demo@12345"

curl.exe -s -u $beginnerAuth "$base/api/stocks/ai-suggestions" | ConvertFrom-Json
```

Expected when no stored batch exists:

```json
{
  "batchId": null,
  "batchStatus": null,
  "suggestedStocks": [],
  "refreshAllowed": true,
  "nextRefreshAllowedAt": null,
  "message": "AI stock suggestions are not available yet. Please refresh suggestions when you are ready."
}
```

`remainingStocks` should contain the supported stocks that have available stock metadata/data.

## 6. POST Refresh

```powershell
curl.exe -s -X POST -u $beginnerAuth "$base/api/stocks/ai-suggestions/refresh" | ConvertFrom-Json
```

Expected:

```text
200 OK
batchStatus=SUCCESS or FALLBACK_RULE_BASED
triggerReason=MANUAL_REFRESH
analysisTimeframe=7D
```

If OpenAI is unavailable, V1 can create a `FALLBACK_RULE_BASED` batch. This is expected for local testing.

## 7. GET After Refresh

```powershell
$afterRefresh = curl.exe -s -u $beginnerAuth "$base/api/stocks/ai-suggestions" | ConvertFrom-Json
$afterRefresh | Select-Object batchId,batchStatus,triggerReason,generatedAt,expiresAt,refreshAllowed,nextRefreshAllowedAt
$afterRefresh.suggestedStocks | Select-Object itemId,symbol,status,isWatchlisted
$afterRefresh.remainingStocks | Select-Object symbol,isSuggested,isWatchlisted
```

Expected:

```text
GET returns the stored batch.
GET remains cache-only and does not call OpenAI.
suggestedStocks contains only current ACTIVE or WATCHLISTED suggestion items.
remainingStocks excludes only current ACTIVE and WATCHLISTED suggested symbols.
dismissed symbols can appear in remainingStocks.
```

## 8. Verify Refresh Cooldown Fields

Immediately after a manual refresh:

```powershell
$response = curl.exe -s -u $beginnerAuth "$base/api/stocks/ai-suggestions" | ConvertFrom-Json
$response | Select-Object generatedAt,refreshAllowed,nextRefreshAllowedAt
```

Expected:

```text
refreshAllowed=False
nextRefreshAllowedAt=latest MANUAL_REFRESH createdAt + 1 hour
```

After cooldown expires:

```text
refreshAllowed=True
nextRefreshAllowedAt=null
```

MySQL check:

```sql
SELECT suggestion_batch_id, status, trigger_reason, created_at,
       DATE_ADD(created_at, INTERVAL 1 HOUR) AS expected_next_refresh_allowed_at
FROM stock_ai_suggestion_batch
WHERE user_id = (
  SELECT user_id FROM app_user WHERE email = 'demo@stockmentor.local'
)
  AND trigger_reason = 'MANUAL_REFRESH'
ORDER BY created_at DESC
LIMIT 1;
```

## 9. Test Cooldown Behavior

Call refresh twice:

```powershell
curl.exe -s -X POST -u $beginnerAuth "$base/api/stocks/ai-suggestions/refresh" | ConvertFrom-Json |
  Select-Object batchStatus,refreshAllowed,nextRefreshAllowedAt,message

curl.exe -s -X POST -u $beginnerAuth "$base/api/stocks/ai-suggestions/refresh" | ConvertFrom-Json |
  Select-Object batchStatus,refreshAllowed,nextRefreshAllowedAt,message
```

Expected second response:

```text
refreshAllowed=False
nextRefreshAllowedAt is not null
message says to wait until cooldown ends
```

## 10. Test PATCH Dismiss

```powershell
$response = curl.exe -s -u $beginnerAuth "$base/api/stocks/ai-suggestions" | ConvertFrom-Json
$itemId = $response.suggestedStocks[0].itemId

curl.exe -s -X PATCH -u $beginnerAuth "$base/api/stocks/ai-suggestions/items/$itemId/dismiss" | ConvertFrom-Json
```

Expected:

```text
Suggestion item status becomes DISMISSED.
Dismissed suggestion item is removed from suggestedStocks.
Dismissed symbol can appear in remainingStocks.
remainingStocks excludes only current ACTIVE and WATCHLISTED suggested symbols.
```

MySQL:

```sql
SELECT suggestion_item_id, user_id, symbol, status, dismissed_at, updated_at
FROM stock_ai_suggestion_item
WHERE suggestion_item_id = <ITEM_ID>;
```

Expected:

```text
status=DISMISSED
dismissed_at is not null
```

## 11. Test PATCH Watchlist

Active item:

```powershell
$response = curl.exe -s -u $beginnerAuth "$base/api/stocks/ai-suggestions" | ConvertFrom-Json
$activeItemId = $response.suggestedStocks[0].itemId

curl.exe -s -X PATCH -u $beginnerAuth "$base/api/stocks/ai-suggestions/items/$activeItemId/watchlist" | ConvertFrom-Json
```

Expected:

```text
ACTIVE item can become WATCHLISTED.
WATCHLISTED item can be watchlisted again idempotently.
user_watchlist has only one row per user_id + symbol.
```

Dismissed or expired item:

```powershell
curl.exe -s -X PATCH -u $beginnerAuth "$base/api/stocks/ai-suggestions/items/$itemId/watchlist" | ConvertFrom-Json
```

Expected:

```json
{
  "status": 400,
  "message": "Only active or already watchlisted suggestion items can be watchlisted from this endpoint. Use a stock watchlist action for dismissed or expired suggestions."
}
```

This endpoint must not change `DISMISSED` or `EXPIRED` items back to `WATCHLISTED`.

MySQL:

```sql
SELECT suggestion_item_id, symbol, status, updated_at
FROM stock_ai_suggestion_item
WHERE suggestion_item_id = <ITEM_ID>;

SELECT watchlist_id, user_id, symbol, source, created_at, updated_at
FROM user_watchlist
WHERE user_id = (
  SELECT user_id FROM app_user WHERE email = 'demo@stockmentor.local'
)
ORDER BY updated_at DESC;

SELECT user_id, symbol, COUNT(*) AS row_count
FROM user_watchlist
GROUP BY user_id, symbol
HAVING COUNT(*) > 1;
```

Expected duplicate query:

```text
No rows.
```

## 12. Test Existing AI Explanation After Basic Auth

Unauthenticated:

```powershell
curl.exe -i "$base/api/stocks/MSFT/ai-explanation?timeframe=7D"
```

Expected:

```text
401 Unauthorized
```

Authenticated:

```powershell
curl.exe -s -u $beginnerAuth "$base/api/stocks/MSFT/ai-explanation?timeframe=7D" | ConvertFrom-Json
```

Expected fields:

```text
symbol
timeframe
explanation
cached
available
analysisSnapshotId
dataSource
isFallback
baselineRiskCategory
riskCategory
message
```

If OpenAI is unavailable, `available` may be false and the response should use the friendly unavailable message.

## 13. Test Admin Backfill With Basic Auth And X-Admin-Token

On Windows PowerShell, `curl.exe --data-raw $body` or similar variable-based curl body calls can cause JSON body parsing issues for admin backfill requests, even when the backend is correct. For PowerShell admin backfill success testing, prefer `Invoke-RestMethod` because it sends the JSON body and content type more predictably.

If PowerShell `curl.exe` returns `400 Bad Request` but Postman or `Invoke-RestMethod` succeeds with the same JSON fields, treat it as a local command formatting issue, not a backend failure.

Set variables:

```powershell
$adminAuth = "admin@stockmentor.local:Admin@12345"
$adminToken = "<your-local-admin-token>"
```

No Basic Auth:

```powershell
curl.exe -i -X POST "$base/api/admin/stocks/backfill" `
  -H "Content-Type: application/json" `
  -H "X-Admin-Token: $adminToken" `
  -d '{ "type": "DAILY_RANGE", "symbols": ["MSFT"], "startDate": "2026-06-01", "endDate": "2026-06-01" }'
```

Expected:

```text
401 Unauthorized or 403 Forbidden, depending on the security entry point.
```

Beginner user with valid token:

```powershell
curl.exe -i -X POST -u $beginnerAuth "$base/api/admin/stocks/backfill" `
  -H "Content-Type: application/json" `
  -H "X-Admin-Token: $adminToken" `
  -d '{ "type": "DAILY_RANGE", "symbols": ["MSFT"], "startDate": "2026-06-01", "endDate": "2026-06-01" }'
```

Expected:

```text
403 Forbidden
```

Admin user with missing or wrong token:

```powershell
curl.exe -i -X POST -u $adminAuth "$base/api/admin/stocks/backfill" `
  -H "Content-Type: application/json" `
  -H "X-Admin-Token: wrong-token" `
  -d '{ "type": "DAILY_RANGE", "symbols": ["MSFT"], "startDate": "2026-06-01", "endDate": "2026-06-01" }'
```

Expected:

```text
Failure based on existing admin token validation.
Usually 401 Unauthorized with message Invalid admin token.
```

Admin user with valid token:

```powershell
$base = "http://localhost:8080"
$adminAuth = "admin@stockmentor.local:Admin@12345"
$adminToken = "9f83b2a164e5f8a9b3c1d7e2f6a4b"

$pair = $adminAuth
$basic = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))

$headers = @{
  Authorization = "Basic $basic"
  "X-Admin-Token" = $adminToken
}

$body = @{
  type = "DAILY_RANGE"
  symbols = @("MSFT")
  startDate = "2026-06-01"
  endDate = "2026-06-01"
} | ConvertTo-Json -Compress

Invoke-RestMethod `
  -Method Post `
  -Uri "$base/api/admin/stocks/backfill" `
  -Headers $headers `
  -ContentType "application/json" `
  -Body $body
```

Expected:

```text
Success if request body is valid and the data source is available.
```

Expected success response example:

```text
jobType     : daily-range
symbols     : {MSFT}
startDate   : 2026-06-01
endDate     : 2026-06-01
savedRows   : 1
skippedRows : 0
deletedRows : 0
messages    : {Daily range backfill completed}
```

Postman also works for this endpoint when Authorization is Basic Auth using `admin@stockmentor.local` / `Admin@12345`. The request must also include the `X-Admin-Token` header.

## 14. Suggestion Persistence Queries

Batches:

```sql
SELECT suggestion_batch_id, user_id, profile_id, profile_version,
       model, prompt_version, status, trigger_reason, input_hash,
       batch_summary, analysis_timeframe, prompt_tokens, completion_tokens,
       total_tokens, finish_reason, error_message,
       created_at, updated_at, expires_at
FROM stock_ai_suggestion_batch
ORDER BY created_at DESC;
```

Items:

```sql
SELECT suggestion_item_id, suggestion_batch_id, user_id, symbol, rank_no,
       match_score, risk_level, suggestion_label, short_reason,
       status, analysis_snapshot_id, dismissed_at, created_at, updated_at
FROM stock_ai_suggestion_item
ORDER BY suggestion_batch_id DESC, rank_no ASC;
```

Watchlist:

```sql
SELECT watchlist_id, user_id, symbol, source, created_at, updated_at
FROM user_watchlist
ORDER BY user_id, symbol;
```

## 15. Suggested Postman Collection

Collection: `StockMentor V1 Backend`

Variables:

```text
baseUrl=http://localhost:8080
beginnerAuth=demo@stockmentor.local
beginnerPassword=Demo@12345
adminAuth=admin@stockmentor.local
adminPassword=Admin@12345
adminToken=<your-local-admin-token>
symbol=MSFT
itemId=
```

Folders:

```text
Auth Smoke
- GET suggestions unauthenticated - expect 401
- GET suggestions beginner auth - expect 200

US006 AI Suggestions
- GET before refresh
- POST refresh
- GET after refresh
- POST refresh cooldown
- PATCH watchlist active item
- PATCH watchlist watchlisted item
- PATCH watchlist dismissed item - expect 400
- PATCH watchlist expired item - expect 400
- PATCH dismiss item

Existing AI Explanation
- GET explanation unauthenticated - expect 401
- GET explanation beginner auth - expect 200

Admin
- POST backfill no auth - expect 401/403
- POST backfill beginner + valid token - expect 403
- POST backfill admin + wrong token - expect failure
- POST backfill admin + valid token - expect success
```

Useful Postman test snippets:

```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));

const body = pm.response.json();
pm.expect(body).to.have.property("refreshAllowed");
pm.expect(body).to.have.property("suggestedStocks");
pm.expect(body).to.have.property("remainingStocks");
```

Store first suggested item:

```javascript
const body = pm.response.json();
if (body.suggestedStocks && body.suggestedStocks.length > 0) {
  pm.collectionVariables.set("itemId", body.suggestedStocks[0].itemId);
}
```

Dismissed watchlist rejection:

```javascript
pm.test("Rejected dismissed or expired suggestion item", () => {
  pm.response.to.have.status(400);
  const body = pm.response.json();
  pm.expect(body.message).to.include("Only active or already watchlisted suggestion items");
});
```
