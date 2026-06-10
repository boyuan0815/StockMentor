# US006 V2 Backend Testing Guide

This guide tests the V2 additions on top of the committed US006 V1 backend.
V2 keeps the existing AI suggestion response fields backward-compatible and keeps
`GET /api/stocks/ai-suggestions` strictly read-only.

## 1. Run Backend With Dev Profile

From PowerShell:

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

If the wrapper launch issue occurs locally, use the same wrapper-downloaded
Maven binary workaround used during V1 verification.

Default local base URL:

```powershell
$base = "http://localhost:8080"
```

Dev credentials:

```powershell
$beginnerAuth = "demo@stockmentor.local:Demo@12345"
$adminAuth = "admin@stockmentor.local:Admin@12345"
```

## 2. Behavior Profile Foundation

V2 behavior profiles are stored separately from onboarding profiles in
`user_behavior_profile`.

Because no paper-trading transaction module exists yet, V2 should create or
reuse only a LOW confidence / INSUFFICIENT_DATA behavior profile during
generation-related flows.

Important checks:

- `GET /api/stocks/ai-suggestions` must not create behavior rows.
- `POST /api/stocks/ai-suggestions/refresh` may create one LOW behavior row if missing.
- Repeating refresh should reuse the existing LOW behavior row, not create one every time.
- Behavior must not be calculated from page views, clicks, or watchlist rows.

MySQL check before and after GET:

```sql
SELECT behavior_profile_id, user_id, behavior_style, behavior_confidence, created_at, updated_at
FROM user_behavior_profile
ORDER BY behavior_profile_id DESC;
```

## 3. AI Suggestions GET Remains Cache-Only

Unauthenticated request should fail:

```powershell
curl.exe -i "$base/api/stocks/ai-suggestions"
```

Expected:

```text
HTTP/1.1 401
```

Authenticated GET:

```powershell
curl.exe -i -u $beginnerAuth "$base/api/stocks/ai-suggestions"
```

Expected:

- `HTTP/1.1 200`
- Does not call OpenAI.
- Does not create a behavior profile.
- Returns stored `ACTIVE` / `WATCHLISTED` suggestions if a valid batch exists.
- Returns `remainingStocks` without duplicating current suggested symbols.
- If no batch exists, returns `suggestedStocks: []` and a friendly message.

## 4. Manual Refresh

Manual refresh still enforces the V1 one-hour cooldown:

```powershell
curl.exe -i -X POST -u $beginnerAuth "$base/api/stocks/ai-suggestions/refresh"
```

Expected success when allowed:

- `HTTP/1.1 200`
- Creates/reuses a LOW behavior profile if no transaction-backed profile exists.
- Computes `input_hash` using onboarding profile, behavior summary, and snapshots.
- Reuses an existing `SUCCESS` or `FALLBACK_RULE_BASED` batch when the input hash is unchanged.
- Calls OpenAI only if generation is needed.
- Falls back to `FALLBACK_RULE_BASED` if OpenAI is unavailable and no cached batch is usable.

Cooldown check:

```powershell
curl.exe -i -X POST -u $beginnerAuth "$base/api/stocks/ai-suggestions/refresh"
curl.exe -i -u $beginnerAuth "$base/api/stocks/ai-suggestions"
```

Expected while blocked:

- `refreshAllowed: false`
- `nextRefreshAllowedAt` equals latest `MANUAL_REFRESH` batch `created_at + 1 hour`

Expected when allowed:

- `refreshAllowed: true`
- `nextRefreshAllowedAt: null`

## 5. Scheduled Refresh

V2 adds a scheduled refresh around 19:30 `America/New_York` on weekdays.

Expected behavior:

- Trading days only.
- Processes active, non-deleted users with `onboarding_completed = true`.
- Skips users without an active investment profile.
- Uses the same internal generation path as manual refresh.
- Bypasses manual cooldown.
- Still computes `input_hash` before OpenAI.
- Skips/reuses unchanged `SUCCESS` or `FALLBACK_RULE_BASED` batches.
- One user failure does not stop the whole scheduled run.

There is no public endpoint for manually triggering the scheduler in V2.
Verify through logs and automated tests.

Expected log themes:

```text
Scheduled AI suggestion refresh completed processedUsers=...
AI suggestion generation skipped ... input_hash is unchanged
AI suggestion batch generated ...
AI suggestion rule-based fallback saved ...
```

## 6. Stock-Symbol Watchlist Endpoints

These endpoints use the authenticated current user. They do not accept a
frontend-provided `userId`.

Supported symbols:

```text
NVDA, TSLA, AMD, AAPL, MSFT, GOOG, KO, JNJ
```

Get current user's watchlist:

```powershell
curl.exe -i -u $beginnerAuth "$base/api/watchlist"
```

Expected shape:

```json
{
  "userId": 1,
  "watchlistedStocks": [
    {
      "stockId": 5,
      "symbol": "MSFT",
      "companyName": "Microsoft",
      "currentPrice": 428.049988,
      "percentChange": 3.3513,
      "trend": "strong uptrend",
      "volatilityLabel": "low",
      "riskCategory": "moderate",
      "isWatchlisted": true
    }
  ],
  "message": "Watchlist returned successfully"
}
```

Add a supported symbol:

```powershell
curl.exe -i -X POST -u $beginnerAuth "$base/api/watchlist/MSFT"
```

Expected first add:

```json
{
  "message": "Stock added to watchlist",
  "changed": true,
  "stock": {
    "symbol": "MSFT",
    "isWatchlisted": true
  }
}
```

Expected duplicate add:

```json
{
  "message": "Stock is already in watchlist",
  "changed": false,
  "stock": {
    "symbol": "MSFT",
    "isWatchlisted": true
  }
}
```

Remove a symbol:

```powershell
curl.exe -i -X DELETE -u $beginnerAuth "$base/api/watchlist/MSFT"
```

Expected existing remove:

```json
{
  "message": "Stock removed from watchlist",
  "changed": true,
  "stock": {
    "symbol": "MSFT",
    "isWatchlisted": false
  }
}
```

Expected missing remove:

```json
{
  "message": "Stock was not in watchlist",
  "changed": false,
  "stock": {
    "symbol": "MSFT",
    "isWatchlisted": false
  }
}
```

Unsupported symbol should fail:

```powershell
curl.exe -i -X POST -u $beginnerAuth "$base/api/watchlist/NFLX"
```

Expected:

```text
HTTP/1.1 400
```

## 7. Suggestion-Item Watchlist Regression

The existing suggestion-item watchlist endpoint remains unchanged in purpose:

```powershell
curl.exe -i -X PATCH -u $beginnerAuth "$base/api/stocks/ai-suggestions/items/${itemId}/watchlist"
```

Expected:

- `ACTIVE` item can become `WATCHLISTED`.
- `WATCHLISTED` item is idempotent.
- `DISMISSED` item is rejected.
- `EXPIRED` item is rejected.

If a user later wants to watchlist the same symbol from normal stock browsing,
use `POST /api/watchlist/{symbol}`, not the dismissed suggestion item ID.

## 8. MySQL Verification Queries

Users:

```sql
SELECT user_id, email, username, role, status, is_deleted, onboarding_completed
FROM app_user
ORDER BY user_id;
```

Onboarding profile:

```sql
SELECT profile_id, user_id, risk_tolerance, investment_goal, experience_level,
       preferred_volatility, preferred_horizon, profile_source, profile_version,
       created_at, updated_at
FROM user_investment_profile
ORDER BY user_id, profile_version DESC;
```

Behavior profile:

```sql
SELECT behavior_profile_id, user_id, behavior_style, behavior_confidence,
       behavior_risk_score, analysis_start_date, analysis_end_date,
       created_at, updated_at
FROM user_behavior_profile
ORDER BY user_id, updated_at DESC;
```

Suggestion batches:

```sql
SELECT suggestion_batch_id, user_id, profile_id, profile_version, status,
       trigger_reason, input_hash, analysis_timeframe, created_at, updated_at, expires_at
FROM stock_ai_suggestion_batch
ORDER BY suggestion_batch_id DESC;
```

Suggestion items:

```sql
SELECT suggestion_item_id, suggestion_batch_id, user_id, symbol, rank_no,
       match_score, risk_level, status, analysis_snapshot_id, created_at, updated_at
FROM stock_ai_suggestion_item
ORDER BY suggestion_item_id DESC;
```

Watchlist:

```sql
SELECT watchlist_id, user_id, symbol, source, created_at, updated_at
FROM user_watchlist
ORDER BY user_id, symbol;
```

## 9. Suggested Postman Collection Structure

- Auth
  - AI suggestions GET without auth
  - AI suggestions GET as beginner
  - AI suggestions refresh as beginner
- Watchlist
  - GET watchlist
  - POST watchlist supported symbol
  - POST watchlist duplicate
  - POST watchlist unsupported symbol
  - DELETE watchlist existing symbol
  - DELETE watchlist missing symbol
- Suggestion Items
  - PATCH dismiss active suggestion
  - PATCH watchlist active suggestion
  - PATCH watchlist dismissed suggestion
- Admin Regression
  - POST admin backfill with admin Basic Auth and `X-Admin-Token`

For admin backfill, Postman should use Basic Auth
`admin@stockmentor.local / Admin@12345` and include `X-Admin-Token`.

## 10. Known V2 Limits

- No transaction-backed behavior scoring exists yet.
- LOW / INSUFFICIENT_DATA behavior is a foundation only, not a recommendation engine.
- Scheduled refresh has no public trigger endpoint in V2.
- OpenAI `response_format: json_schema` was left as a future improvement unless it can be added without destabilizing `OpenAiClient`; backend validation remains mandatory.
