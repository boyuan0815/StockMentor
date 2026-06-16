# US009/US010 Delayed Market Data Backend Testing Guide

This guide verifies StockMentor's 15-minute delayed educational market data selector for:

* US009 stock list, detail, and `1D` history
* US010 paper-trading BUY/SELL execution
* US010 portfolio valuation metadata

The selector consumes stored backend data only. Do not run scheduler jobs, admin
backfill, cleanup, Twelve Data calls, or OpenAI calls while following this guide.

## 1. Start Backend

From PowerShell:

```powershell
cd C:\StockMentor\backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

If `.\mvnw.cmd` fails with the known wrapper launcher issue, use the cached
Maven wrapper binary:

```powershell
& "$env:USERPROFILE\.m2\wrapper\dists\apache-maven-3.9.14-bin\1cb7fhup6b5n3bed6kckbrnspv\apache-maven-3.9.14\bin\mvn.cmd" `
  spring-boot:run "-Dspring-boot.run.profiles=dev"
```

In a second PowerShell terminal:

```powershell
$base = "http://localhost:8080"
$userAuth = "demo@stockmentor.local:Demo@12345"
$symbol = "MSFT"
```

Adjust `$userAuth` and `$symbol` for your local test user and stock symbol.

For SQL checks, open MySQL Workbench or MySQL CLI and select your local dev
database:

```sql
USE
stockmentor;

SET
@symbol = 'MSFT';
```

Adjust `stockmentor` and `@symbol` for your local dev database.

## 2. Check Stored Data

Confirm the stock row, intraday rows, and daily rows exist.

Check the stock row:

```sql
SELECT stock_id,
       symbol,
       current_price,
       percent_change,
       last_updated
FROM stock
WHERE symbol = @symbol;
```

Check latest stored intraday rows:

```sql
SELECT symbol,
       trading_date,
       `timestamp`,
       close_price,
       time_interval
FROM stock_price_history_1min
WHERE symbol = @symbol
ORDER BY `timestamp` DESC LIMIT 10;
```

Check latest stored daily rows:

```sql
SELECT symbol,
       trading_date,
       open_price,
       high_price,
       low_price,
       close_price,
       updated_at
FROM stock_price_daily
WHERE symbol = @symbol
ORDER BY trading_date DESC LIMIT 10;
```

Expected:

* `stock_price_history_1min.time_interval` is `1min`
* daily rows have `close_price > 0` when used as closed-market source
* no test step requires provider fetches or backfill

## 3. Stock List Delayed Fields

```powershell
curl.exe -i -u $userAuth "$base/api/stocks"
```

```powershell
 select *
     from (select *, row_number() over(
     partition by symbol
     order by trading_date desc
     ) as rn
     from stock_price_daily
     ) t
     where rn = 1
     order by symbol;
```

Expected:

* `HTTP/1.1 200`
* legacy fields still exist: `currentPrice`, `percentChange`, `lastUpdated`
* delayed fields exist on each stock item:

```json
{
  "displayedPrice": 123.45,
  "displayedMarketTime": "2026-06-15T09:45:00",
  "targetDisplayMarketTime": "2026-06-15T09:45:00",
  "dataDelayMinutes": 15,
  "priceFreshnessStatus": "AVAILABLE",
  "isPriceAvailable": true,
  "isTradeExecutable": true,
  "priceSource": "stock_price_history_1min",
  "marketTimeZone": "America/New_York"
}
```

Valid status examples include:

* `AVAILABLE`
* `STALE`
* `NOT_READY_WITH_DAILY_FALLBACK`
* `FALLBACK_DAILY`
* `MARKET_CLOSED`
* `MARKET_CLOSED_PENDING_DAILY_CLOSE`
* `UNAVAILABLE`

## 4. Stock Detail Delayed Fields

```powershell
curl.exe -i -u $userAuth "$base/api/stocks/$symbol"
```

Expected:

* `HTTP/1.1 200`
* `currentPrice`, `percentChange`, and `lastUpdated` remain present
* delayed fields are present:

```json
{
  "symbol": "MSFT",
  "displayedPrice": 123.45,
  "displayedPercentChange": 0.42,
  "dataDelayMinutes": 15,
  "isPriceAvailable": true,
  "isTradeExecutable": true,
  "priceSource": "stock_price_history_1min",
  "analysisDataSource": "stock_price_daily",
  "marketTimeZone": "America/New_York",
  "lastBackendUpdatedAt": "2026-06-15T10:00:00"
}
```

`lastBackendUpdatedAt` should come from the selected delayed source where
available: daily `updatedAt`, intraday `createdAt`, or stock-row update time only
as a last fallback.

Stock detail day-range fields:

```json
{
  "highPrice": 126.50,
  "lowPrice": 121.20,
  "snapshotHighPrice": 130.00,
  "snapshotLowPrice": 118.00,
  "snapshotTimeframe": "7D"
}
```

Expected:

* `highPrice` and `lowPrice` mean displayed/latest day range for the selected
  delayed market view.
* During active delayed intraday display, this range is derived from stored
  `1min` rows at or before `displayedMarketTime`.
* During closed daily, pre-open, weekend, or holiday daily-source display, this
  range comes from the selected `stock_price_daily` row.
* If no safe day-range data exists, `highPrice` and `lowPrice` may be `null`.
* `snapshotHighPrice`, `snapshotLowPrice`, and `snapshotTimeframe` are the
  explicit latest `7D` analysis snapshot range fields.
* Stock list/card responses do not need day-range fields.
* `priceSource` is the authoritative displayed quote source.
* `dataSource` is a legacy analysis-source field; `analysisDataSource` is the
  explicit latest analysis snapshot source.

## 5. `1D` History Cutoff

The `1D` history response always uses `source = stock_price_history_1min`.
Chart row selection is independent from quote `priceSource`.

During the active delayed selector window, `09:45-16:14 America/New_York`, the
latest returned `1D` point must be at or before `targetDisplayMarketTime`.

During `PRE_DELAYED_OPEN`, quote metadata may use daily fallback, but `1D`
history should still return latest completed trading-day stored `1min` rows at
or before `16:00` when those rows exist.

During `POST_DELAYED_CLOSE`, `1D` history should return current trading-day
stored `1min` rows at or before `16:00`. During weekends/holidays, it should
return latest completed trading-day rows at or before `16:00` when available.

```powershell
$historyJson = curl.exe -s -u $userAuth "$base/api/stocks/$symbol/history?timeframe=1D" | ConvertFrom-Json
$historyJson.targetDisplayMarketTime
$historyJson.points | Select-Object -Last 5
```

Expected:

* `HTTP/1.1 200` when using `curl.exe -i`
* no point has `timestamp` later than `targetDisplayMarketTime`
* missing 1-minute candles are not filled or invented
* metadata includes `dataDelayMinutes = 15`

Example:

```text
Current ET = 10:00
targetDisplayMarketTime = 09:45

If the database has 09:46, 09:47, 09:48, 09:49,
those rows must not appear in the 1D response.
```

## 6. Active Delayed BUY

During `09:45-16:14 America/New_York`, BUY uses the exact delayed intraday
candle or the closest same-day prior candle within the staleness threshold.

```powershell
$buy = @{
  symbol = $symbol
  quantity = 1
} | ConvertTo-Json -Compress

Set-Content -Path .\buy-request.json -Value $buy -Encoding utf8

curl.exe -i -u $userAuth `
  -H "Content-Type: application/json" `
  --data-binary "@buy-request.json" `
  "$base/api/paper-trading/buy"
```

Expected:

* `HTTP/1.1 200`
* `transaction.executionPrice` equals the backend selected delayed stored price
* response includes delayed metadata:

```json
{
  "transaction": {
    "side": "BUY",
    "executionPrice": 123.45
  },
  "delayedPriceMetadata": {
    "priceFreshnessStatus": "AVAILABLE",
    "priceSource": "stock_price_history_1min",
    "isTradeExecutable": true
  }
}
```

## 7. Active Missing Intraday Rejection

During `09:45-16:14 America/New_York`, if no acceptable same-day delayed
intraday candle exists, BUY/SELL must reject. Daily fallback may be display-only
for stock browsing, but it must not execute trades.

```powershell
$buy = @{
  symbol = $symbol
  quantity = 1
} | ConvertTo-Json -Compress

Set-Content -Path .\buy-request.json -Value $buy -Encoding utf8

curl.exe -i -u $userAuth `
  -H "Content-Type: application/json" `
  --data-binary "@buy-request.json" `
  "$base/api/paper-trading/buy"
```

Expected when delayed intraday is unavailable:

* `HTTP/1.1 400`
* message similar to:

```json
{
  "message": "Delayed market price is not available yet. Please try again later."
}
```

Do not use `stock.currentPrice`, previous daily close, or a frontend price for
active-window execution.

## 8. Frontend Price Cannot Influence Execution

Unknown JSON fields may be ignored by Jackson, but the backend must not use them.

```powershell
$buyWithPrice = @{
  symbol = $symbol
  quantity = 1
  price = 9999.99
} | ConvertTo-Json -Compress

Set-Content -Path .\buy-with-price.json -Value $buyWithPrice -Encoding utf8

curl.exe -i -u $userAuth `
  -H "Content-Type: application/json" `
  --data-binary "@buy-with-price.json" `
  "$base/api/paper-trading/buy"
```

Expected:

* `HTTP/1.1 200`, or `HTTP/1.1 400` if delayed data is unavailable
* if the trade succeeds, `transaction.executionPrice` is not `9999.99`
* `transaction.executionPrice` matches stored delayed selector data

## 9. SELL Uses Delayed Selector Price

After a successful BUY:

```powershell
$sell = @{
  symbol = $symbol
  quantity = 1
} | ConvertTo-Json -Compress

Set-Content -Path .\sell-request.json -Value $sell -Encoding utf8

curl.exe -i -u $userAuth `
  -H "Content-Type: application/json" `
  --data-binary "@sell-request.json" `
  "$base/api/paper-trading/sell"
```

Expected:

* `HTTP/1.1 200`
* `transaction.side = SELL`
* `transaction.executionPrice` comes from delayed selector metadata
* realized P/L and fees still follow the US010 rules

## 10. After-Close Pending Daily Close

StockMentor has an opportunistic early daily close attempt around
`16:14 America/New_York`. The existing `19:00` daily scheduler remains the
stable fallback and should still be able to update the same day's daily row if
the early provider value changes.

Run this after `16:15 America/New_York` on a trading day when today's daily row
does not exist yet but same-day intraday rows exist.

In MySQL Workbench or MySQL CLI, set the trading date manually to the current
New York trading date:

```sql
SET
@trading_date = '2026-06-15';
```

Check whether today's daily row exists:

```sql
SELECT symbol,
       trading_date,
       close_price
FROM stock_price_daily
WHERE symbol = @symbol
  AND trading_date = @trading_date;
```

Check same-day intraday rows:

```sql
SELECT symbol,
       trading_date,
       `timestamp`,
       close_price
FROM stock_price_history_1min
WHERE symbol = @symbol
  AND trading_date = @trading_date
  AND time_interval = '1min'
ORDER BY `timestamp` DESC LIMIT 5;
```

Expected HTTP metadata:

```json
{
  "priceFreshnessStatus": "MARKET_CLOSED_PENDING_DAILY_CLOSE",
  "priceSource": "stock_price_history_1min",
  "targetDisplayMarketTime": "2026-06-15T16:00:00",
  "displayedMarketTime": "2026-06-15T15:59:00",
  "isTradeExecutable": true
}
```

```json
{
  "priceFreshnessStatus": "MARKET_CLOSED",
  "priceSource": "stock_price_daily",
  "targetDisplayMarketTime": "2026-06-15T16:00:00",
  "displayedMarketTime": "2026-06-15T16:00:00",
  "isTradeExecutable": true
}
```

For `MARKET_CLOSED_PENDING_DAILY_CLOSE`, `displayedMarketTime` is the actual
stored intraday candle timestamp. It may be `15:59` when no `16:00` candle
exists. Do not require or invent a `16:00` 1-minute candle.

For `MARKET_CLOSED`, `displayedMarketTime` represents the selected daily close
at the regular market close timestamp, and `priceSource` is `stock_price_daily`.

## 11. After Daily Close Is Available

When today's valid daily row exists, set the trading date manually to the current
New York trading date:

```sql
SET
@trading_date = '2026-06-15';
```

Then check today's daily row:

```sql
SELECT symbol,
       trading_date,
       open_price,
       close_price,
       updated_at
FROM stock_price_daily
WHERE symbol = @symbol
  AND trading_date = @trading_date;
```

Then call the stock detail endpoint:

```powershell
curl.exe -i -u $userAuth "$base/api/stocks/$symbol"
```

Expected:

```json
{
  "priceFreshnessStatus": "MARKET_CLOSED",
  "priceSource": "stock_price_daily",
  "isPriceAvailable": true,
  "isTradeExecutable": true,
  "dataNote": "Market is closed. This practice trade uses the latest stored daily close, not a live quote."
}
```

The `1D` history response should still return actual stored intraday candles for
the completed trading day. It should not replace the chart with a synthetic
daily-close point.

Daily close and final 1-minute close may differ. This is expected provider
behavior, not a bug. Do not synthesize or overwrite `StockPriceDaily.closePrice`
from the latest 1-minute candle.

## 12. Weekend Or Holiday

On Saturday, Sunday, or a NYSE holiday, the selector walks backward to the
latest completed trading day.

```powershell
curl.exe -i -u $userAuth "$base/api/stocks/$symbol"
```

Expected when latest completed daily close exists:

```json
{
  "priceFreshnessStatus": "MARKET_CLOSED",
  "priceSource": "stock_price_daily",
  "isPriceAvailable": true,
  "isTradeExecutable": true,
  "dataNote": "Market is closed. Prices shown are based on the latest completed trading day."
}
```

Expected when that daily close is missing or invalid:

* stock display may show unavailable metadata
* paper trading returns `400`
* paper trading must not use random older intraday data

## 13. Portfolio Valuation Metadata

```powershell
curl.exe -i -u $userAuth "$base/api/paper-trading/portfolio"
```

Expected when all positions are priced:

```json
{
  "portfolioValuationComplete": true,
  "pricedPositionCount": 1,
  "unpricedPositionCount": 0,
  "positions": [
    {
      "valuationPrice": 123.45,
      "valuationMarketValue": 123.45,
      "delayedPriceMetadata": {
        "isTradeExecutable": true
      }
    }
  ]
}
```

Expected when some positions are unpriced:

```json
{
  "portfolioValuationComplete": false,
  "pricedPositionCount": 1,
  "unpricedPositionCount": 1,
  "portfolioDataNote": "Portfolio valuation is partial because some delayed stored prices are unavailable. Unpriced positions are excluded from market value, unrealized P/L, and return calculations."
}
```

Unpriced positions should not silently fall back to `stock.currentPrice`.

## 14. Admin Daily Backfill Safety

Admin historical `DAILY_RANGE` and `DAILY_MISSING` backfill remain
insert-missing-only by default.

Expected behavior for wide historical requests:

* missing `stock_price_daily` rows are inserted
* existing historical daily rows are skipped
* existing historical rows count as `skippedRows`, not `savedRows`
* weekend catch-up remains missing-only

Only scheduler/current-day refresh paths, including the opportunistic `16:14`
attempt and stable `19:00` job, may refresh an existing same-day daily row.
This lets the `19:00` job correct a same-day daily candle fetched too early.
`refreshDailyForDate(...)` should reject non-current New York dates without
calling Twelve Data or updating existing daily rows.

The daily close may differ from the final stored 1-minute candle close. This is
acceptable provider behavior. Do not synthesize or overwrite daily candles from
intraday rows.

## 15. Regression Checks

Run backend tests:

```powershell
cd C:\StockMentor\backend
.\mvnw.cmd clean test
.\mvnw.cmd clean compile
```

If the Maven wrapper fails locally, use:

```powershell
& "$env:USERPROFILE\.m2\wrapper\dists\apache-maven-3.9.14-bin\1cb7fhup6b5n3bed6kckbrnspv\apache-maven-3.9.14\bin\mvn.cmd" clean test
& "$env:USERPROFILE\.m2\wrapper\dists\apache-maven-3.9.14-bin\1cb7fhup6b5n3bed6kckbrnspv\apache-maven-3.9.14\bin\mvn.cmd" clean compile
```

Then:

```powershell
cd C:\StockMentor
git diff --check
git status --short
```

Expected:

* US001-US008 and US011-US012 tests still pass
* US009 responses are enhanced, not renamed
* US010 BUY/SELL and portfolio valuation use delayed stored selector metadata
* no frontend implementation files are modified
