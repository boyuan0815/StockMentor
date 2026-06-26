# Backend Market Data, Paper Trading, AI Display, Watchlist, And Transactions Testing Guide

This guide verifies the backend pass for StockMentor stored market display data, chart history, paper-trading accounting,
AI suggestion display decoration, watchlist edit support, and transaction paging.

All examples assume the backend runs locally at `http://localhost:8080` and Basic Auth users already exist.

## 1. Verification Commands

Run focused backend tests first:

```powershell
cd C:\Users\lim\.codex\worktrees\385c\StockMentor\backend
.\mvnw.cmd -Dtest=PaperTradingServiceImplTests,PaperTradingControllerSecurityTests,DelayedMarketPriceServiceTests,StockMarketDataServiceImplTests,WatchlistServiceImplTests,WatchlistControllerSecurityTests,StockAiSuggestionServiceImplTests test
```

Expected result:

- Build succeeds.
- Paper-trading service/controller tests pass.
- Delayed market-price tests pass.
- Stock market data service tests pass.
- Watchlist service/controller tests pass.
- AI suggestion service tests pass.

Run the full backend suite:

```powershell
cd C:\Users\lim\.codex\worktrees\385c\StockMentor\backend
.\mvnw.cmd test
```

Expected result:

- Build succeeds.
- No test failures or errors.

Run frontend contract sanity only after backend tests pass:

```powershell
cd C:\Users\lim\.codex\worktrees\385c\StockMentor\frontend
npm.cmd run lint
npx.cmd tsc --noEmit
```

Expected result:

- Lint passes.
- TypeScript passes.
- No frontend runtime files are needed for this backend pass.

Run repository checks:

```powershell
cd C:\Users\lim\.codex\worktrees\385c\StockMentor
git diff --check
git diff --cached --name-only
git diff --name-only backend
git status --short .agents skills-lock.json frontend/.gitignore frontend/package.json frontend/package-lock.json package.json package-lock.json
```

Expected result:

- `git diff --check` prints no whitespace errors.
- `git diff --cached --name-only` prints nothing unless you intentionally staged files later.
- `git diff --name-only backend` lists only backend code/test/docs paths relevant to this pass.
- Protected-file status prints nothing.

## 2. PowerShell API Helper

Set variables:

```powershell
$base = "http://localhost:8080"
$user = "beginner@example.com"
$password = "password"
$pair = "$user`:$password"
$auth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))
$headers = @{ Authorization = "Basic $auth" }
```

Expected result:

- `$headers.Authorization` starts with `Basic `.
- No password is printed by later commands unless you explicitly echo `$pair`.

Helper functions:

```powershell
function Invoke-StockMentorGet($Path) {
  Invoke-RestMethod -Method Get -Uri "$base$Path" -Headers $headers
}

function Invoke-StockMentorPost($Path, $Body) {
  Invoke-RestMethod -Method Post -Uri "$base$Path" -Headers $headers -ContentType "application/json" -Body ($Body | ConvertTo-Json -Depth 8)
}

function Invoke-StockMentorPatch($Path, $Body) {
  Invoke-RestMethod -Method Patch -Uri "$base$Path" -Headers $headers -ContentType "application/json" -Body ($Body | ConvertTo-Json -Depth 8)
}
```

Expected result:

- Functions are created without output.

## 3. Delayed Display Statuses And Previous-Close Formula

Run stock list:

```powershell
$stocks = Invoke-StockMentorGet "/api/stocks"
$stocks.stocks | Select-Object symbol, displayedPrice, previousClose, displayedAbsoluteChange, displayedPercentChange, priceFreshnessStatus, priceFreshnessLabel | Format-Table
```

Expected result:

- Every priced row uses a backend `displayedPrice`.
- `priceFreshnessStatus` is one of:
  - `DELAYED_15_MINUTES`
  - `MARKET_CLOSED_LAST_CLOSE`
  - `LATEST_STORED_PRICE`
  - `UNAVAILABLE`
- `priceFreshnessLabel` is one of:
  - `Delayed 15 min`
  - `Market Closed · Last Close`
  - `Latest Stored Price`
  - `Unavailable`
- When `displayedPrice` and positive `previousClose` exist:
  - `displayedAbsoluteChange = displayedPrice - previousClose`
  - `displayedPercentChange = displayedAbsoluteChange / previousClose * 100`

Run a stock detail check:

```powershell
$detail = Invoke-StockMentorGet "/api/stocks/MSFT"
$detail | Select-Object symbol, displayedPrice, previousClose, displayedAbsoluteChange, displayedPercentChange, displayedMarketTime, targetDisplayMarketTime, priceFreshnessStatus, priceFreshnessLabel, priceSource
```

Expected result:

- Detail values use the same delayed display contract as stock list.
- Frontend does not need to recalculate market status or percent movement.

## 4. After-Market Daily Close Rule

Check whether a same-day daily candle exists:

```sql
SELECT symbol, trading_date, close_price, source, updated_at
FROM stock_price_daily
WHERE symbol = 'MSFT'
ORDER BY trading_date DESC
LIMIT 5;
```

Expected result:

- If the latest row is today's New York trading date and has a valid `close_price` after market close, stock list/detail should display that daily close with `MARKET_CLOSED_LAST_CLOSE`.
- If today's daily candle is missing after market close, the backend may fall back to latest same-day intraday close with `LATEST_STORED_PRICE`.
- Daily close must not be synthesized from the final 1-minute candle.

Check intraday fallback source if daily is missing:

```sql
SELECT symbol, trading_date, timestamp, close_price
FROM stock_price_history_1min
WHERE symbol = 'MSFT'
ORDER BY timestamp DESC
LIMIT 5;
```

Expected result:

- Fallback rows are real stored intraday rows.
- No synthetic daily row is created by a read endpoint.

## 5. Stock History And Chart Data

Check `1D`:

```powershell
$h1d = Invoke-StockMentorGet "/api/stocks/MSFT/history?timeframe=1D"
$h1d | Select-Object symbol, timeframe, granularity, expectedPointCount, actualPointCount, missingDataCount, candlestickSupported, completenessNote
$h1d.points | Select-Object -First 3 timestamp, tradingDate, open, high, low, close, price, volume
```

Expected result:

- `timeframe` is `1D`.
- `granularity` is `INTRADAY_1MIN`.
- Normal full-day `expectedPointCount` is `390`.
- Points are ordered oldest to newest.
- Points represent stored data only.
- If OHLC is missing on any point, `candlestickSupported=false`.

Check preferred `5D`:

```powershell
$h5d = Invoke-StockMentorGet "/api/stocks/MSFT/history?timeframe=5D"
$h5d | Select-Object symbol, timeframe, granularity, expectedPointCount, actualPointCount, missingDataCount, includedTradingDays, requestedTradingDays, candlestickSupported, completenessNote
```

Expected result:

- `timeframe` is `5D`.
- `granularity` is `INTRADAY_1MIN`.
- Full five-day expected count is `1950`.
- `actualPointCount` equals returned point count.
- Missing points are counted in `missingDataCount`; missing rows are not synthesized.
- During market hours, current day can be partial.
- Before market open, the backend may use the previous five completed trading days.

Check retained `7D`:

```powershell
$h7d = Invoke-StockMentorGet "/api/stocks/MSFT/history?timeframe=7D"
$h7d | Select-Object symbol, timeframe, granularity, source, dataSource, expectedPointCount, actualPointCount
```

Expected result:

- `7D` stays backward-compatible.
- In the current backend, `7D` remains daily history.
- Future chart UI should prefer `5D` for multi-day intraday charts.

Check daily ranges:

```powershell
foreach ($tf in @("1M", "3M", "YTD", "1Y")) {
  $r = Invoke-StockMentorGet "/api/stocks/MSFT/history?timeframe=$tf"
  [PSCustomObject]@{
    timeframe = $r.timeframe
    granularity = $r.granularity
    actualPointCount = $r.actualPointCount
    candlestickSupported = $r.candlestickSupported
  }
}
```

Expected result:

- Each response uses `DAILY` granularity.
- Points are oldest to newest.
- Daily OHLC is real stored OHLC.

## 6. Candlestick Rule

SQL spot check:

```sql
SELECT symbol, timestamp, open_price, high_price, low_price, close_price
FROM stock_price_history_1min
WHERE symbol = 'MSFT'
ORDER BY timestamp DESC
LIMIT 10;
```

Expected result:

- If every returned API point has non-null real stored `open`, `high`, `low`, and `close`, `candlestickSupported=true`.
- If any returned API point lacks real OHLC, `candlestickSupported=false`.
- Backend must never fake candles by copying close into open/high/low.

## 7. Paper-Trading Accounting Truth Tables

Reset first:

```powershell
$reset = Invoke-StockMentorPost "/api/paper-trading/portfolio/reset" @{}
$portfolio = Invoke-StockMentorGet "/api/paper-trading/portfolio"
$portfolio | Select-Object cashBalance, startingCash, realizedProfitLossAfterFees, unrealizedProfitLoss, totalProfitLoss, todayProfitLoss, totalFeesPaid, currentSessionNumber
```

Expected result:

- New session starts.
- Open positions are cleared.
- `realizedProfitLossAfterFees = 0`.
- `unrealizedProfitLoss = 0` when there are no positions.
- `totalProfitLoss = 0`.
- `todayOpenPositionProfitLoss = 0`.
- `todayRealizedProfitLossAfterFees = 0`.
- `todayProfitLoss = 0`.
- `totalFeesPaid = 0`.

Buy one share:

```powershell
$buy = Invoke-StockMentorPost "/api/paper-trading/buy" @{ symbol = "MSFT"; quantity = 1 }
$portfolio = Invoke-StockMentorGet "/api/paper-trading/portfolio"
$portfolio | Select-Object cashBalance, estimatedMarketValue, realizedProfitLossAfterFees, unrealizedProfitLoss, totalProfitLoss, totalProfitLossPercent, totalFeesPaid
```

Expected result:

- Request body contains only `symbol` and `quantity`.
- Backend execution price comes from delayed stored display selector.
- Cash decreases by `executionPrice * quantity + fee`.
- Cost basis includes buy fee.
- If current valuation price equals buy execution price and fee is `1.00`, open unrealized P/L for one share is `-1.00`.
- `realizedProfitLossAfterFees = 0`.
- `totalProfitLoss = realizedProfitLossAfterFees + unrealizedProfitLoss`.

Sell the same share:

```powershell
$sell = Invoke-StockMentorPost "/api/paper-trading/sell" @{ symbol = "MSFT"; quantity = 1 }
$portfolio = Invoke-StockMentorGet "/api/paper-trading/portfolio"
$portfolio | Select-Object realizedProfitLossAfterFees, unrealizedProfitLoss, totalProfitLoss, totalFeesPaid
```

Expected result:

- Request body contains only `symbol` and `quantity`.
- Backend ignores any frontend `price`, `fee`, `amount`, `maxQuantity`, or `userId` if sent.
- If buy price and sell price are both `100.00` with a `1.00` fee on each side:
  - unrealized P/L is `0`.
  - realized P/L after fees is `-2.00`.
  - total P/L is `-2.00`.

Accounting truth table:

| Scenario | Unrealized P/L | Realized P/L after fees | Total P/L |
|---|---:|---:|---:|
| Buy 1 at 100, buy fee 1, current price 100 | -1 | 0 | -1 |
| Sell same 1 at 100, sell fee 1 | 0 | -2 | -2 |
| After full sell | 0 | -2 | -2 |
| After reset | 0 | 0 | 0 |

## 8. Today P/L

Read portfolio:

```powershell
$portfolio = Invoke-StockMentorGet "/api/paper-trading/portfolio"
$portfolio | Select-Object todayOpenPositionProfitLoss, todayRealizedProfitLossAfterFees, todayProfitLoss, todayProfitLossPercent, todayProfitLossComplete, todayProfitLossNote
```

Expected result:

- `todayOpenPositionProfitLoss = sum(openQuantity * (displayedPrice - previousClose))` for safely priced open positions.
- `todayRealizedProfitLossAfterFees = sum(today SELL realized P/L after fees in current session)`.
- `todayProfitLoss = todayOpenPositionProfitLoss + todayRealizedProfitLossAfterFees`.
- `todayProfitLossPercent = todayProfitLoss / currentSessionStartingCash * 100`.
- `todayProfitLossNote` is `Percentage is based on current session starting cash.` when complete.
- If any open position lacks displayed price or previous close, calculated subset is returned, `todayProfitLossComplete=false`, and the note explains missing open-position pricing.

Today P/L truth table:

| Scenario | Today Open Position P/L | Today Realized P/L | Today P/L |
|---|---:|---:|---:|
| Hold 1 share, previous close 100, displayed price 105 | 5 | 0 | 5 |
| Sell today with realized after-fee P/L -2 and no open position | 0 | -2 | -2 |
| Hold 1 share up 5 and sell another share today realized -2 | 5 | -2 | 3 |

## 9. Reset And Current Session History

After reset, check current session:

```powershell
$current = Invoke-StockMentorGet "/api/paper-trading/transactions?currentSessionOnly=true"
$all = Invoke-StockMentorGet "/api/paper-trading/transactions?currentSessionOnly=false"
[PSCustomObject]@{
  currentCount = $current.Count
  allCount = $all.Count
}
```

Expected result:

- Current-session transactions show only transactions after the latest reset.
- All-session transactions include older sessions.
- Old transactions and old fees remain visible when `currentSessionOnly=false`.

SQL spot check:

```sql
SELECT transaction_id, user_id, symbol, side, is_current_session, session_number, fee, net_amount, realized_profit_loss, executed_at
FROM paper_trade_transaction
ORDER BY executed_at DESC, transaction_id DESC
LIMIT 20;
```

Expected result:

- RESET rows have `symbol = NULL`.
- Current-session rows have `is_current_session = true`.
- Old rows remain persisted with `is_current_session = false`.

## 10. Plain Transaction List

Fetch latest list:

```powershell
$tx = Invoke-StockMentorGet "/api/paper-trading/transactions?size=50&currentSessionOnly=true"
$tx | Select-Object -First 5 transactionId, symbol, side, quantity, price, fee, grossAmount, netAmount, realizedProfitLoss, realizedProfitLossAfterFees, cashBalanceAfter, isCurrentSession, sessionNumber
```

Expected result:

- Response is a plain list, not a paged wrapper.
- Newest rows come first.
- SELL rows include after-fee realized P/L.
- BUY and RESET rows safely show zero or null realized P/L according to DTO conventions.
- RESET rows are safe with null symbol.

## 11. Paged Transaction Endpoint

Fetch first page:

```powershell
$page = Invoke-StockMentorGet "/api/paper-trading/transactions/page?page=0&size=50&currentSessionOnly=true"
$page | Select-Object page, size, totalElements, totalPages
$page.transactions | Select-Object -First 5 transactionId, symbol, side, executedAt
```

Expected result:

- Response has `transactions`, `page`, `size`, `totalElements`, and `totalPages`.
- `size` defaults to 50 if omitted.
- `size` is capped at 100.
- Sort is `executedAt DESC`, then `transactionId DESC`.

Filter by exact symbol:

```powershell
Invoke-StockMentorGet "/api/paper-trading/transactions/page?symbol=MSFT&currentSessionOnly=false" |
  Select-Object -ExpandProperty transactions |
  Select-Object transactionId, symbol, side
```

Expected result:

- All returned rows with a symbol use exact uppercase `MSFT`.
- Unsupported symbols return HTTP 400.

Filter by side:

```powershell
Invoke-StockMentorGet "/api/paper-trading/transactions/page?side=SELL&currentSessionOnly=false" |
  Select-Object -ExpandProperty transactions |
  Select-Object transactionId, symbol, side, realizedProfitLossAfterFees
```

Expected result:

- All returned rows have `side = SELL`.
- `side=ALL` behaves like no side filter.
- Invalid side returns HTTP 400.

Filter by inclusive date/time:

```powershell
$from = "2026-01-01T00:00:00"
$to = "2026-12-31T23:59:59"
Invoke-StockMentorGet "/api/paper-trading/transactions/page?from=$from&to=$to&currentSessionOnly=false" |
  Select-Object page, size, totalElements
```

Expected result:

- Returned rows have `executedAt >= from` and `executedAt <= to`.
- Invalid date strings return HTTP 400.

Route-conflict check:

```powershell
Invoke-StockMentorGet "/api/paper-trading/transactions/page" | Select-Object page, size
```

Expected result:

- `/transactions/page` returns the paged response.
- It is not captured by `/transactions/{transactionId}`.

## 12. Buy/Sell Unknown JSON Fields

Send extra fields:

```powershell
$body = @{
  symbol = "MSFT"
  quantity = 1
  price = 1
  fee = 0
  amount = 1
  maxQuantity = 999999
  userId = 999999
}
Invoke-StockMentorPost "/api/paper-trading/buy" $body
```

Expected result:

- Backend either ignores extra fields or rejects them safely.
- Extra `price` does not affect execution price.
- Extra `userId` does not affect the authenticated user.
- Execution price always comes from backend delayed display selector.

## 13. AI Suggestion Delayed Display Fields

Fetch cached suggestions:

```powershell
$suggestions = Invoke-StockMentorGet "/api/stocks/ai-suggestions"
$suggestions.suggestedStocks | Select-Object symbol, displayedPrice, displayedAbsoluteChange, displayedPercentChange, previousClose, priceFreshnessStatus, priceFreshnessLabel, displayDataSource
```

Expected result:

- GET is cache-only/read-only.
- GET does not call OpenAI.
- Existing AI suggestion analysis timeframe remains `7D`.
- Delayed display fields decorate responses only.
- Missing delayed data for one symbol does not fail the whole response.
- Display fields do not change `inputHash`, prompt version, or trigger regeneration.

## 14. Watchlist Reorder

Create a watchlist:

```powershell
Invoke-StockMentorPost "/api/watchlist/MSFT" @{}
Invoke-StockMentorPost "/api/watchlist/AAPL" @{}
Invoke-StockMentorPost "/api/watchlist/KO" @{}
$watchlist = Invoke-StockMentorGet "/api/watchlist"
$watchlist.watchlistedStocks | Select-Object symbol
```

Expected result:

- Added symbols append to the end.
- GET returns persisted order.

Reorder full list:

```powershell
$reordered = Invoke-StockMentorPatch "/api/watchlist/reorder" @{ symbols = @("KO", "MSFT", "AAPL") }
$reordered.watchlistedStocks | Select-Object symbol
```

Expected result:

- Response order is `KO`, `MSFT`, `AAPL`.
- Backend saves `displayOrder = 0..n-1`.
- Request does not accept or trust `userId`.
- Duplicate, unsupported, unowned, or missing owned symbols return HTTP 400 and change nothing.

SQL spot check:

```sql
SELECT watchlist_id, user_id, symbol, display_order, created_at
FROM user_watchlist
ORDER BY user_id, display_order, created_at, watchlist_id;
```

Expected result:

- Current user's rows have contiguous `display_order` values from 0.
- Other users' rows are unchanged.

## 15. Watchlist Batch Remove

Run batch remove:

```powershell
$removed = Invoke-StockMentorPost "/api/watchlist/batch-remove" @{ symbols = @("MSFT", "JNJ") }
$removed | Select-Object removedSymbols, notFoundSymbols
$removed.remainingWatchlistedStocks | Select-Object symbol
```

Expected result:

- Supported present symbols appear in `removedSymbols`.
- Supported but absent symbols appear in `notFoundSymbols`.
- Remaining watchlist is returned in normalized order.
- Unsupported symbols return HTTP 400 and remove nothing.

## 16. Optional MySQL Compatibility SQL For display_order

Hibernate `ddl-auto: update` can add `display_order` in local development. Use this optional SQL only for existing MySQL
databases where the column/index must be created manually:

```sql
ALTER TABLE user_watchlist ADD COLUMN display_order INT NULL;

UPDATE user_watchlist uw
JOIN (
  SELECT watchlist_id,
         ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY created_at, watchlist_id) - 1 AS next_order
  FROM user_watchlist
) ranked ON ranked.watchlist_id = uw.watchlist_id
SET uw.display_order = ranked.next_order
WHERE uw.display_order IS NULL;

CREATE INDEX idx_watchlist_user_display_order
  ON user_watchlist(user_id, display_order);
```

Expected result:

- Existing rows receive deterministic order per user.
- The index helps ordered watchlist reads.
- Do not add Flyway or Liquibase unless the project later adopts a migration tool.

## 17. Security Checks

Unauthenticated requests:

```powershell
Invoke-RestMethod -Method Get -Uri "$base/api/paper-trading/transactions/page"
Invoke-RestMethod -Method Patch -Uri "$base/api/watchlist/reorder" -ContentType "application/json" -Body '{"symbols":["MSFT"]}'
Invoke-RestMethod -Method Post -Uri "$base/api/watchlist/batch-remove" -ContentType "application/json" -Body '{"symbols":["MSFT"]}'
```

Expected result:

- Each request returns HTTP 401.

Authenticated normal user:

```powershell
Invoke-StockMentorGet "/api/paper-trading/transactions/page"
Invoke-StockMentorPatch "/api/watchlist/reorder" @{ symbols = @("KO", "AAPL") }
Invoke-StockMentorPost "/api/watchlist/batch-remove" @{ symbols = @("KO") }
```

Expected result:

- No `X-Admin-Token` is required.
- Endpoints operate only on the authenticated user's data.
- Client `userId` query/body values are ignored or rejected safely.

## 18. Frontend Sanity Checks

Use the app after backend verification:

1. Open Watchlist, Stocks, Search, Stock Detail, Portfolio, and History.
2. Confirm rows use backend freshness labels and delayed display values.
3. Confirm Portfolio can show current-session `totalProfitLoss`, today P/L fields, and current-session `totalFeesPaid`
   when frontend wiring chooses to surface them.
4. Confirm paper-trading ticket still sends only `symbol` and `quantity`.
5. Confirm History can keep using latest 50 plain-list rows; future load-more can use `/transactions/page`.

Expected result:

- No frontend direct OpenAI, Twelve Data, broker, or market-data calls.
- No frontend-calculated trusted price, fee, amount, max quantity, or P/L is submitted to backend.
