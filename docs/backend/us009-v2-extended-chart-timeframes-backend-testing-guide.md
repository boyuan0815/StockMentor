# US009 v2 Extended Chart Timeframes Backend Testing Guide

This guide verifies US009 v2, which extends the authenticated stock history
endpoint with stock-app style chart timeframes.

US009 v2 originally kept the stock list and detail APIs unchanged. Later
delayed-market-data work added delayed metadata to those responses; this guide
still focuses on:

```text
GET /api/stocks/{symbol}/history?timeframe=...
```

Supported timeframes:

```text
1D, 7D, 1M, 3M, YTD, 1Y
```

US009 v2 is read-only. It reads stored backend rows only and does not call
Twelve Data, call OpenAI, create snapshots, generate explanations, generate AI
suggestions, recalculate behavior profiles, create paper trades, or trigger
scheduler, backfill, repair, or cleanup flows.

## 1. Start Backend

From PowerShell:

```powershell
cd C:\StockMentor\backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

If `.\mvnw.cmd` fails with the known wrapper launcher issue, use the cached
Maven wrapper distribution:

```powershell
& "$env:USERPROFILE\.m2\wrapper\dists\apache-maven-3.9.14-bin\1cb7fhup6b5n3bed6kckbrnspv\apache-maven-3.9.14\bin\mvn.cmd" `
  spring-boot:run "-Dspring-boot.run.profiles=dev"
```

In another PowerShell terminal:

```powershell
$base = "http://localhost:8080"
$beginnerAuth = "demo@stockmentor.local:Demo@12345"
```

Optional helper:

```powershell
function Invoke-Us009Get {
  param([string]$Path)
  curl.exe -i -u $beginnerAuth "$base$Path"
}
```

## 2. Authentication

US009 requires Basic Auth and resolves the current user on the backend.
Do not send `userId`.

Unauthenticated requests should return `401`:

```powershell
curl.exe -i "$base/api/stocks/MSFT/history?timeframe=1M"
```

Authenticated requests should return `200` for supported symbols and
timeframes:

```powershell
Invoke-Us009Get "/api/stocks/MSFT/history?timeframe=1M"
```

## 3. Stored Data Checks

Check latest stored intraday data for `1D`:

```sql
SELECT trading_date,
       COUNT(*)       AS rows_for_day,
       MIN(timestamp) AS first_point,
       MAX(timestamp) AS last_point
FROM stock_price_history_1min
WHERE symbol = 'MSFT'
GROUP BY trading_date
ORDER BY trading_date DESC;
```

Check stored daily candles for `7D`, `1M`, `3M`, `YTD`, and `1Y`:

```sql
SELECT MAX(trading_date) AS latest_daily_date
FROM stock_price_daily
WHERE symbol = 'MSFT';
```

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
ORDER BY trading_date DESC
LIMIT 20;
```

Longer timeframes depend on enough stored daily rows. Use existing US008
`DAILY_RANGE` or `DAILY_MISSING` admin backfill over a sufficient date range if
local data does not yet cover `YTD` or `1Y`.

## 4. Endpoint Examples

Run all supported timeframes:

```powershell
Invoke-Us009Get "/api/stocks/MSFT/history?timeframe=1D"
Invoke-Us009Get "/api/stocks/MSFT/history?timeframe=7D"
Invoke-Us009Get "/api/stocks/MSFT/history?timeframe=1M"
Invoke-Us009Get "/api/stocks/MSFT/history?timeframe=3M"
Invoke-Us009Get "/api/stocks/MSFT/history?timeframe=YTD"
Invoke-Us009Get "/api/stocks/MSFT/history?timeframe=1Y"
```

Lowercase symbol and timeframe should normalize:

```powershell
Invoke-Us009Get "/api/stocks/msft/history?timeframe=1m"
Invoke-Us009Get "/api/stocks/msft/history?timeframe=3m"
Invoke-Us009Get "/api/stocks/msft/history?timeframe=ytd"
Invoke-Us009Get "/api/stocks/msft/history?timeframe=1y"
```

## 5. Expected Response Shape

Each history response includes:

```text
symbol
timeframe
source
points
message
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

Expected source:

- `1D`: `stock_price_history_1min`
- `7D`, `1M`, `3M`, `YTD`, `1Y`: `stock_price_daily`

Daily points have `timestamp = null` and a populated `tradingDate`.

## 6. Timeframe Rules

`1D`:

- uses stored 1-minute intraday rows at or before the backend delayed display
  cutoff
- does not expose current-day rows newer than `targetDisplayMarketTime`
- returns stored 1-minute rows oldest-to-newest by timestamp
- returns empty `points` safely when no intraday data exists

`7D`:

- uses latest 7 stored daily candles
- returns points oldest-to-newest

`1M`:

- finds latest stored daily candle date
- start date is latest date minus 1 month
- returns stored daily candles between start and latest date inclusive

`3M`:

- start date is latest daily date minus 3 months
- returns stored daily candles between start and latest date inclusive

`YTD`:

- start date is January 1 of the latest daily candle year
- returns stored daily candles between start and latest date inclusive

`1Y`:

- start date is latest daily date minus 1 year
- returns stored daily candles between start and latest date inclusive

All returned chart points must be ordered oldest-to-newest.

## 7. Empty Data Behavior

Use a supported symbol with no daily rows in a clean local database, or inspect
one first:

```sql
SELECT COUNT(*) AS daily_rows
FROM stock_price_daily
WHERE symbol = 'JNJ';
```

Then:

```powershell
Invoke-Us009Get "/api/stocks/JNJ/history?timeframe=1M"
```

Expected:

- HTTP 200
- `source = stock_price_daily`
- `points = []`
- friendly message saying no stored daily history is available
- no backfill is triggered

## 8. Validation Failures

Unsupported symbol:

```powershell
Invoke-Us009Get "/api/stocks/META/history?timeframe=1M"
```

Expected:

```text
HTTP/1.1 400
```

Unsupported timeframe:

```powershell
Invoke-Us009Get "/api/stocks/MSFT/history?timeframe=30D"
```

Expected:

```text
HTTP/1.1 400
```

US009 v2 supports exactly `1D`, `7D`, `1M`, `3M`, `YTD`, and `1Y`.

## 9. Route Coexistence

Run:

```powershell
Invoke-Us009Get "/api/stocks/MSFT"
Invoke-Us009Get "/api/stocks/MSFT/history?timeframe=1M"
Invoke-Us009Get "/api/stocks/MSFT/ai-explanation?timeframe=7D"
```

Expected:

- stock detail returns detail fields
- history returns chart `points`
- AI explanation returns US012 explanation fields
- no route conflict occurs

`/api/stocks/{symbol}/ai-explanation` belongs to US012. It may create/reuse a
snapshot and call OpenAI when no cached explanation exists. Do not include that
endpoint in US009 read-only mutation checks.

## 10. Read-Only Count Checks

Capture counts before:

```sql
SELECT COUNT(*) AS stock_count FROM stock;
SELECT COUNT(*) AS snapshot_count FROM stock_analysis_snapshot;
SELECT COUNT(*) AS intraday_count FROM stock_price_history_1min;
SELECT COUNT(*) AS daily_count FROM stock_price_daily;
SELECT COUNT(*) AS explanation_count FROM stock_ai_explanation;
SELECT COUNT(*) AS suggestion_batch_count FROM stock_ai_suggestion_batch;
SELECT COUNT(*) AS suggestion_item_count FROM stock_ai_suggestion_item;
SELECT COUNT(*) AS behavior_profile_count FROM user_behavior_profile;
SELECT COUNT(*) AS paper_trade_count FROM paper_trade_transaction;
```

Run only US009 endpoints:

```powershell
Invoke-Us009Get "/api/stocks"
Invoke-Us009Get "/api/stocks/MSFT"
Invoke-Us009Get "/api/stocks/MSFT/history?timeframe=1D"
Invoke-Us009Get "/api/stocks/MSFT/history?timeframe=7D"
Invoke-Us009Get "/api/stocks/MSFT/history?timeframe=1M"
Invoke-Us009Get "/api/stocks/MSFT/history?timeframe=3M"
Invoke-Us009Get "/api/stocks/MSFT/history?timeframe=YTD"
Invoke-Us009Get "/api/stocks/MSFT/history?timeframe=1Y"
```

Run the SQL counts again.

Expected:

- counts are unchanged
- no stock, intraday, daily, snapshot, explanation, suggestion, behavior, or
  paper-trading rows are inserted, updated, or deleted
- no Twelve Data or OpenAI call occurs

## 11. Automated Verification

Run from `backend`:

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd clean compile
```

If `.\mvnw.cmd` fails with the local wrapper issue, use the cached Maven wrapper
distribution and record that fallback.

Run from repository root:

```powershell
git diff --check
git status --short
```

Expected:

- targeted US009 tests pass
- full backend test suite passes
- compile passes
- `git diff --check` has no whitespace errors
- `git status --short` shows only expected US009 v2 files
