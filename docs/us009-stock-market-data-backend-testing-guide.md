# US009 Stock Market Data Backend Testing Guide

This guide verifies the backend-only US009 stock market data viewing API.
It focuses on authenticated read-only stock list, stock detail, and stored
chart/history responses.

US009 covers:

- stock browsing list for the supported StockMentor universe
- stock detail for one supported symbol
- stored 1-minute intraday history for `1D`
- stored daily candle history for `7D`
- read-only watchlist status for the current user
- safe links into AI explanation and paper trading screens

US009 must not call Twelve Data, OpenAI, snapshot creation, AI suggestion
generation, behavior profile logic, paper-trading logic, scheduler jobs, or
backfill/repair flows. It reads stored database rows only.

## 1. Start Backend

From PowerShell:

```powershell
cd C:\StockMentor\backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

If `.\mvnw.cmd` fails with the known wrapper launcher issue, use the wrapper
downloaded Maven binary from the local Maven wrapper cache. Example:

```powershell
& "$env:USERPROFILE\.m2\wrapper\dists\apache-maven-3.9.14-bin\1cb7fhup6b5n3bed6kckbrnspv\apache-maven-3.9.14\bin\mvn.cmd" `
  spring-boot:run "-Dspring-boot.run.profiles=dev"
```

In a second PowerShell terminal, set reusable variables:

```powershell
$base = "http://localhost:8080"
$beginnerAuth = "demo@stockmentor.local:Demo@12345"
```

Create a small GET helper:

```powershell
function Invoke-Us009Get {
  param(
    [string]$Path,
    [string]$Auth = $beginnerAuth
  )

  curl.exe -i -u $Auth "$base$Path"
}
```

## 2. Confirm Stored Data

Use MySQL Workbench, MySQL client, or your IDE database console.

Confirm the demo user:

```sql
SELECT user_id, email, username, role, status, is_deleted
FROM app_user
WHERE email = 'demo@stockmentor.local';
```

Expected:

- one active beginner investor user
- record the returned `user_id` for watchlist checks

Check supported stock rows:

```sql
SELECT stock_id,
       symbol,
       company_name,
       current_price,
       percent_change,
       last_updated,
       is_market_open,
       timezone,
       source
FROM stock
WHERE symbol IN ('NVDA', 'TSLA', 'AMD', 'AAPL', 'MSFT', 'GOOG', 'KO', 'JNJ')
ORDER BY FIELD(symbol, 'NVDA', 'TSLA', 'AMD', 'AAPL', 'MSFT', 'GOOG', 'KO', 'JNJ');
```

Expected:

- rows may exist for all eight supported symbols
- if a row is missing, US009 should still return that symbol with metadata
  fallback values where possible

Check latest analysis snapshots:

```sql
SELECT *
FROM (SELECT *,
             ROW_NUMBER() OVER (
               PARTITION BY symbol
               ORDER BY created_at DESC
             ) AS rn
      FROM stock_analysis_snapshot) t
WHERE rn = 1
ORDER BY symbol;
```

Expected:

- US009 uses the latest stored `7D` snapshot per symbol when available
- missing snapshots should not cause a 500 response
- when multiple snapshots have the same `created_at`, the higher
  `analysis_snapshot_id` is treated as the latest row

Price source cross-check:

```sql
SELECT symbol, current_price, percent_change, last_updated
FROM stock
WHERE symbol = 'MSFT';

SELECT analysis_snapshot_id,
       current_price,
       percent_change,
       data_source,
       created_at
FROM stock_analysis_snapshot
WHERE symbol = 'MSFT'
  AND timeframe = '7D'
ORDER BY created_at DESC, analysis_snapshot_id DESC LIMIT 1;
```

Expected:

- `currentPrice` and `percentChange` in US009 list/detail prefer the `stock`
  row values when that row exists
- snapshot price fields are fallback values only when the `stock` row is
  missing
- snapshot rows still provide analysis labels and metadata such as trend,
  volatility, fallback status, snapshot id, and data source

Check stored history:

```sql
SELECT symbol,
       trading_date,
       COUNT(*)       AS rows_for_day,
       MIN(timestamp) AS first_point,
       MAX(timestamp) AS last_point
FROM stock_price_history_1min
WHERE symbol = 'MSFT'
GROUP BY symbol, trading_date
ORDER BY trading_date DESC;
```

```sql
SELECT symbol,
       trading_date,
       open_price,
       high_price,
       low_price,
       close_price,
       volume,
       source
FROM stock_price_daily
WHERE symbol = 'MSFT'
ORDER BY trading_date DESC LIMIT 10;
```

Expected:

- `1D` reads the latest non-null intraday `trading_date`
- `7D` reads the latest stored daily candle rows
- neither endpoint backfills missing rows

## 3. Authentication Checks

US009 requires Basic Auth:

```powershell
curl.exe -i "$base/api/stocks"
curl.exe -i "$base/api/stocks/MSFT"
curl.exe -i "$base/api/stocks/MSFT/history?timeframe=1D"
```

Expected:

```text
HTTP/1.1 401
```

Authenticated requests should succeed:

```powershell
Invoke-Us009Get "/api/stocks"
Invoke-Us009Get "/api/stocks/MSFT"
Invoke-Us009Get "/api/stocks/MSFT/history?timeframe=1D"
```

Expected:

- HTTP 200
- responses are for the authenticated current user
- no request accepts `userId`

## 4. Stock List

```powershell
$base = "http://localhost:8080"
$beginnerAuth = "demo@stockmentor.local:Demo@12345"

$basicToken = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($beginnerAuth))
$headers = @{ Authorization = "Basic $basicToken" }

$list = Invoke-RestMethod -Method Get -Uri "$base/api/stocks" -Headers $headers

$list.stocks | Format-Table symbol, companyName, currentPrice, percentChange, riskCategory, trend, isWatchlisted
```

Alternative with `curl.exe`:

```powershell
Invoke-Us009Get "/api/stocks"
```

Expected fields per stock:

```text
stockId
symbol
companyName
currentPrice
percentChange
lastUpdated
isMarketOpen
timezone
source
riskCategory
baselineRiskCategory
trend
volatilityLabel
volumeTrend
priceConsistency
isFallback
missingDataCount
latestAnalysisSnapshotId
isWatchlisted
```

Expected order:

```text
NVDA, TSLA, AMD, AAPL, MSFT, GOOG, KO, JNJ
```

Important checks:

- only the eight supported symbols are returned
- DB row order must not affect response order
- missing `stock` rows do not crash the endpoint
- missing snapshots do not crash the endpoint
- `isWatchlisted` reflects only the authenticated user

Watchlist SQL cross-check:

```sql
SELECT symbol, source, created_at
FROM user_watchlist
WHERE user_id = 1
ORDER BY symbol;
```

Expected:

- list response `isWatchlisted = true` only for matching rows

## 5. Stock Detail

```powershell
Invoke-Us009Get "/api/stocks/MSFT"
Invoke-Us009Get "/api/stocks/msft"
```

Expected:

- both return HTTP 200
- response `symbol = "MSFT"`
- lowercase path symbols are normalized to uppercase

Expected fields:

```text
stockId
symbol
companyName
currentPrice
percentChange
lastUpdated
isMarketOpen
timezone
source
riskCategory
baselineRiskCategory
trend
volatilityLabel
volumeTrend
priceConsistency
highPrice
lowPrice
dataSource
isFallback
missingDataCount
latestAnalysisSnapshotId
snapshotHash
isWatchlisted
aiExplanationAvailable
aiExplanationEndpoint
tradeSupported
```

Expected:

- `tradeSupported = true` for supported symbols
- `aiExplanationEndpoint = /api/stocks/MSFT/ai-explanation?timeframe=7D`
- `currentPrice`, `percentChange`, `lastUpdated`, `isMarketOpen`, `timezone`,
  and `source` come from the stored `stock` row when it exists
- `highPrice` and `lowPrice` represent the latest `7D` analysis range when a
  snapshot exists; they fall back to stock day high/low only when no snapshot
  exists
- `aiExplanationAvailable` is based on stored explanation rows only for the
  latest displayed `7D` snapshot, configured OpenAI model, and
  `stock-explanation-v1` prompt version
- no OpenAI call happens from this detail endpoint

SQL cross-check for stored AI explanation availability:

```sql
SELECT e.explanation_id,
       e.analysis_snapshot_id,
       e.symbol,
       e.timeframe,
       e.model,
       e.prompt_version,
       e.created_at
FROM stock_ai_explanation e
JOIN stock_analysis_snapshot s
  ON s.analysis_snapshot_id = e.analysis_snapshot_id
WHERE e.symbol = 'MSFT'
  AND e.timeframe = '7D'
ORDER BY e.created_at DESC;
```

Expected:

- if a row exists for the latest displayed snapshot id, configured model, and
  `stock-explanation-v1`, detail may show `aiExplanationAvailable = true`
- explanations for older snapshots do not make the latest detail response show
  available
- if no matching row exists, detail should still return HTTP 200 with
  `aiExplanationAvailable = false`

## 6. History: 1D Intraday

```powershell
Invoke-Us009Get "/api/stocks/MSFT/history?timeframe=1D"
```

Expected response shape:

```text
symbol    : MSFT
timeframe : 1D
source    : stock_price_history_1min
points    : [stored 1-minute rows]
message   : <friendly message>
```

Each point includes:

```text
timestamp
tradingDate
openPrice
highPrice
lowPrice
closePrice
volume
source
```

Expected:

- points are ordered by `timestamp` ascending
- rows come from the latest non-null `trading_date` for the symbol
- no backfill or Twelve Data call occurs

SQL comparison:

```sql
SELECT trading_date
FROM stock_price_history_1min
WHERE symbol = 'MSFT'
  AND trading_date IS NOT NULL
ORDER BY trading_date DESC LIMIT 1;
```

Then:

```sql
SELECT timestamp, trading_date, open_price, high_price, low_price, close_price, volume, source
FROM stock_price_history_1min
WHERE symbol = 'MSFT'
  AND trading_date = '<LATEST_TRADING_DATE>'
ORDER BY timestamp ASC;
```

## 7. History: Empty 1D Case

Use a supported symbol that has no non-null intraday `trading_date`, or test in
a clean local database before intraday data is loaded:

```powershell
Invoke-Us009Get "/api/stocks/JNJ/history?timeframe=1D"
```

Expected:

- HTTP 200
- `points` is an empty array
- `message` says no stored intraday history is available
- no backfill is triggered

SQL should confirm no new rows were created:

```sql
SELECT COUNT(*) AS jnj_intraday_rows
FROM stock_price_history_1min
WHERE symbol = 'JNJ';
```

Run the API call, then run the count again.

Expected:

- count is unchanged

## 8. History: 7D Daily Candles

```powershell
Invoke-Us009Get "/api/stocks/MSFT/history?timeframe=7D"
```

Expected:

- HTTP 200
- `source = stock_price_daily`
- up to 7 points
- points are ordered oldest-to-newest in the response
- `timestamp` is empty/null for daily points
- `tradingDate` is populated

SQL comparison:

```sql
SELECT trading_date,
       open_price,
       high_price,
       low_price,
       close_price,
       volume,
       source
FROM stock_price_daily
WHERE symbol = 'MSFT'
ORDER BY trading_date DESC LIMIT 7;
```

Expected:

- API returns the same rows, reversed into ascending date order

## 9. Validation Failures

Unsupported symbol:

```powershell
Invoke-Us009Get "/api/stocks/META"
```

Expected:

```text
HTTP/1.1 400
{"message":"Unsupported stock symbol: META","status":400}
```

Unsupported timeframe:

```powershell
Invoke-Us009Get "/api/stocks/MSFT/history?timeframe=30D"
```

Expected:

```text
HTTP/1.1 400
{"message":"Unsupported stock history timeframe: 30D","status":400}
```

## 10. Route Conflict Checks

US009 adds:

```text
GET /api/stocks/{symbol}
GET /api/stocks/{symbol}/history
```

US012 already owns:

```text
GET /api/stocks/{symbol}/ai-explanation
```

Run all three:

```powershell
Invoke-Us009Get "/api/stocks/MSFT"
Invoke-Us009Get "/api/stocks/MSFT/history?timeframe=1D"
Invoke-Us009Get "/api/stocks/MSFT/ai-explanation?timeframe=7D"
```

Expected:

- stock detail returns stock detail fields
- history returns `points`
- AI explanation returns explanation fields such as `explanation`, `available`,
  `analysisSnapshotId`, and `message`
- no route conflict occurs

Note:

- the AI explanation endpoint may create/reuse a snapshot and call OpenAI when
  no cached explanation exists; that behavior belongs to US012, not US009
- US009 detail/history endpoints do not call the AI explanation service

## 11. Read-Only Checks

Capture key table counts:

```sql
SELECT COUNT(*) AS stock_count
FROM stock;
SELECT COUNT(*) AS snapshot_count
FROM stock_analysis_snapshot;
SELECT COUNT(*) AS intraday_count
FROM stock_price_history_1min;
SELECT COUNT(*) AS daily_count
FROM stock_price_daily;
SELECT COUNT(*) AS explanation_count
FROM stock_ai_explanation;
SELECT COUNT(*) AS suggestion_batch_count
FROM stock_ai_suggestion_batch;
SELECT COUNT(*) AS suggestion_item_count
FROM stock_ai_suggestion_item;
SELECT COUNT(*) AS behavior_profile_count
FROM user_behavior_profile;
SELECT COUNT(*) AS paper_trade_count
FROM paper_trade_transaction;
```

Run only US009 endpoints:

```powershell
Invoke-Us009Get "/api/stocks"
Invoke-Us009Get "/api/stocks/MSFT"
Invoke-Us009Get "/api/stocks/MSFT/history?timeframe=1D"
Invoke-Us009Get "/api/stocks/MSFT/history?timeframe=7D"
```

Run the SQL counts again.

Expected:

- counts are unchanged
- no stock rows are created or updated
- no history/daily rows are created
- no snapshots are created
- no AI explanations or suggestion batches/items are created
- no behavior profiles are created or recalculated
- no paper trades are created

## 12. Frontend Notes

For Expo integration:

- call all US009 endpoints with Basic Auth until JWT exists
- do not send `userId`
- use `GET /api/stocks` for the browse screen
- use `GET /api/stocks/{symbol}` for the detail header and action states
- use `GET /api/stocks/{symbol}/history?timeframe=1D` for intraday chart points
- use `GET /api/stocks/{symbol}/history?timeframe=7D` for daily chart points
- use `aiExplanationEndpoint` as the entry point to the AI explanation screen
- use `tradeSupported` to enable paper-trading buy/sell entry points for the
  supported universe
- treat empty `points` as "no stored chart data yet", not as an error

## 13. Automated Verification

Run from `backend`:

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd clean compile
```

If `.\mvnw.cmd` fails with the local wrapper issue, use the wrapper-downloaded
Maven binary and record the fallback in your verification notes.

Run from repository root:

```powershell
git diff --check
git status --short
```

Expected:

```text
BUILD SUCCESS
Tests run: <number>, Failures: 0, Errors: 0, Skipped: 0
```

## 14. Acceptance Checklist

- `GET /api/stocks` requires authentication.
- `GET /api/stocks` returns exactly the supported symbols.
- Stock list order is `NVDA, TSLA, AMD, AAPL, MSFT, GOOG, KO, JNJ`.
- Stock list/detail include stored stock price fields when available.
- Stock list/detail include latest stored `7D` snapshot labels when available.
- Missing stock rows or snapshots do not cause HTTP 500.
- Watchlist status is scoped to the authenticated current user.
- `GET /api/stocks/{symbol}` normalizes lowercase symbols.
- Unsupported symbols return HTTP 400.
- `1D` history reads stored intraday rows or returns an empty safe response.
- `7D` history reads stored daily candle rows.
- Unsupported timeframes return HTTP 400.
- US009 endpoints do not mutate stock, snapshot, history, AI, behavior, or paper-trading tables.
- `/api/stocks/{symbol}/ai-explanation` still works after adding US009 routes.
