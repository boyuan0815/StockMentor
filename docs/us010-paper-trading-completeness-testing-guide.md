# US010 Paper-Trading Completeness Backend Testing Guide

This guide verifies the backend-only US010 paper-trading completeness module.
It focuses on PowerShell API calls, expected response fields, and SQL checks for
the current backend implementation.

US010 covers:

- virtual paper-trading account/session tracking
- BUY/SELL with a flat fee
- fee-aware average cost
- realized profit/loss on SELL
- enhanced portfolio and position response fields
- transaction filters and detail lookup
- portfolio reset with RESET marker rows
- behavior-profile recalculation after BUY/SELL only

US010 must not call OpenAI or Twelve Data from paper-trading endpoints.
BUY/SELL execution uses the latest stored `stock.current_price`.

## 1. Start Backend

From PowerShell:

```powershell
cd C:\Users\lim\.codex\worktrees\bc98\StockMentor\backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

If `.\mvnw.cmd` fails with the known wrapper launcher issue, use the wrapper
downloaded Maven binary from the local Maven wrapper cache. Example:

```powershell
& "$env:USERPROFILE\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd" `
  spring-boot:run "-Dspring-boot.run.profiles=dev"
```

In a second PowerShell terminal, set reusable variables:

```powershell
$base = "http://localhost:8080"
$beginnerAuth = "demo@stockmentor.local:Demo@12345"
$basic = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($beginnerAuth))
$headers = @{ Authorization = "Basic $basic" }
```

Create small helpers:

```powershell
function Invoke-PaperGet {
  param([string]$Path)

  Invoke-RestMethod `
    -Method Get `
    -Uri "$base$Path" `
    -Headers $headers
}

function Invoke-PaperPost {
  param([string]$Path, [object]$Body = $null)

  if ($null -eq $Body) {
    Invoke-RestMethod `
      -Method Post `
      -Uri "$base$Path" `
      -Headers $headers
  } else {
    Invoke-RestMethod `
      -Method Post `
      -Uri "$base$Path" `
      -Headers $headers `
      -ContentType "application/json" `
      -Body ($Body | ConvertTo-Json -Compress)
  }
}

function Invoke-PaperTrade {
  param([string]$Side, [string]$Symbol, [int]$Quantity)

  Invoke-PaperPost `
    -Path "/api/paper-trading/$Side" `
    -Body @{ symbol = $Symbol; quantity = $Quantity }
}
```

## 2. Confirm Demo User And Stock Prices

Use MySQL client, MySQL Workbench, or your IDE database console.

```sql
SELECT user_id, email, username, role, status
FROM app_user
WHERE email = 'demo@stockmentor.local';
```

Expected:

- one active demo user
- record the returned `user_id` as `1`

Check supported stock prices:

```sql
SELECT symbol, company_name, current_price, last_updated, updated_at
FROM stock
WHERE symbol IN ('NVDA', 'TSLA', 'AMD', 'AAPL', 'MSFT', 'GOOG', 'KO', 'JNJ')
ORDER BY symbol;
```

Expected:

- eight supported symbols can exist in the `stock` table
- the symbols you trade must have `current_price > 0`
- if `current_price` is missing or non-positive, BUY/SELL returns HTTP 400

Optional PowerShell price check through SQL is database-client specific. The
API itself does not expose a US010 supported-stocks endpoint.

## 3. Baseline Account

```powershell
$account = Invoke-PaperGet "/api/paper-trading/account"
$account | Format-List
```

Expected fields:

```text
accountId             : <number>
cashBalance           : 1000000.0000
startingCash          : 1000000.0000
currentSessionNumber  : 1
lastResetAt           :
status                : ACTIVE
createdAt             : <timestamp>
updatedAt             : <timestamp>
```

SQL check:

```sql
SELECT account_id, user_id, cash_balance, starting_cash,
       current_session_number, last_reset_at, status, created_at, updated_at
FROM paper_trading_account
WHERE user_id = 1;
```

Expected:

- `cash_balance = starting_cash`
- default `current_session_number = 1`
- `last_reset_at IS NULL` before first reset

## 4. Baseline Portfolio

```powershell
$portfolio = Invoke-PaperGet "/api/paper-trading/portfolio"
$portfolio | Format-List
```

Expected on a fresh or reset account:

```text
userId                : 1
cashBalance           : 1000000.0000
startingCash          : 1000000.0000
totalInvestedCost     : 0.0000
estimatedMarketValue  : 0.0000
totalPortfolioValue   : 1000000.0000
unrealizedProfitLoss  : 0.0000
realizedProfitLoss    : 0.0000
returnPercentage      : 0.00
totalFeesPaid         : 0.0000
currentSessionNumber  : 1
positions             : {}
```

SQL checks:

```sql
SELECT position_id, user_id, symbol, quantity, average_cost, total_cost,
       realized_pl, created_at, updated_at
FROM paper_position
WHERE user_id = 1
ORDER BY symbol;
```

```sql
SELECT COALESCE(SUM(realized_profit_loss), 0) AS current_session_realized_pl
FROM paper_trade_transaction
WHERE user_id = 1
  AND side = 'SELL'
  AND (is_current_session = TRUE OR is_current_session IS NULL);
```

```sql
SELECT COALESCE(SUM(fee), 0) AS current_session_fees
FROM paper_trade_transaction
WHERE user_id = 1
  AND side IN ('BUY', 'SELL')
  AND (is_current_session = TRUE OR is_current_session IS NULL);
```

Expected:

- open positions match `portfolio.positions`
- current-session realized P/L and fees match portfolio totals

## 5. BUY With Flat Fee

Get MSFT price from SQL first:

```sql
SELECT current_price
FROM stock
WHERE symbol = 'MSFT';
```

Assume the price is `P`.

Run a BUY:

```powershell
$buy = Invoke-PaperTrade -Side "buy" -Symbol "MSFT" -Quantity 3
$buy.account | Format-List
$buy.position | Format-List
$buy.transaction | Format-List
```

Expected formula:

```text
grossAmount = 3 * P
fee         = 1.0000
netAmount   = grossAmount + fee
cashAfter   = cashBefore - netAmount
averageCost = netAmount / 3
```

Expected transaction fields:

```text
side                 : BUY
symbol               : MSFT
quantity             : 3
executionPrice       : P
price                : P
grossAmount          : 3 * P
fee                  : 1.0000
netAmount            : (3 * P) + 1
totalAmount          : same as netAmount
realizedProfitLoss   : 0.0000
isCurrentSession     : True
sessionNumber        : current account session
transactionTime      : same as executedAt
```

SQL checks:

```sql
SELECT account_id, cash_balance, starting_cash, current_session_number
FROM paper_trading_account
WHERE user_id = 1;
```

```sql
SELECT position_id, symbol, quantity, average_cost, total_cost
FROM paper_position
WHERE user_id = 1
  AND symbol = 'MSFT';
```

```sql
SELECT transaction_id, symbol, side, quantity, execution_price,
       gross_amount, fee, net_amount, realized_profit_loss,
       cash_balance_after, is_current_session, session_number, executed_at
FROM paper_trade_transaction
WHERE user_id = 1
ORDER BY transaction_id DESC
LIMIT 5;
```

Expected:

- `paper_position.total_cost` includes the BUY fee
- BUY `realized_profit_loss = 0`
- `cash_balance_after` equals account `cash_balance`

## 6. BUY More Of Same Symbol

```powershell
$buy2 = Invoke-PaperTrade -Side "buy" -Symbol "MSFT" -Quantity 2
$buy2.position | Format-List
```

Expected:

```text
newQuantity  = oldQuantity + 2
newTotalCost = oldTotalCost + (2 * P + 1)
averageCost  = newTotalCost / newQuantity
```

SQL:

```sql
SELECT symbol, quantity, average_cost, total_cost
FROM paper_position
WHERE user_id = 1
  AND symbol = 'MSFT';
```

Expected:

- one MSFT row only
- quantity increased
- average cost changed based on fee-inclusive cost basis

## 7. Partial SELL And Realized P/L

Get current average cost before selling:

```sql
SELECT quantity, average_cost, total_cost
FROM paper_position
WHERE user_id = 1
  AND symbol = 'MSFT';
```

Assume average cost is `A` and current MSFT price is `P`.

Run a partial sell:

```powershell
$sell = Invoke-PaperTrade -Side "sell" -Symbol "MSFT" -Quantity 1
$sell.account | Format-List
$sell.position | Format-List
$sell.transaction | Format-List
```

Expected formula:

```text
grossAmount        = 1 * P
fee                = 1.0000
netAmount          = grossAmount - fee
costBasisSold      = 1 * A
realizedProfitLoss = netAmount - costBasisSold
cashAfter          = cashBefore + netAmount
newQuantity        = oldQuantity - 1
newTotalCost       = oldTotalCost - costBasisSold
```

Expected:

- SELL transaction stores non-null `realizedProfitLoss`
- remaining position keeps the same average cost except rounding
- account cash increases by SELL `netAmount`

SQL:

```sql
SELECT transaction_id, symbol, side, quantity, execution_price,
       gross_amount, fee, net_amount, realized_profit_loss,
       cash_balance_after, is_current_session, session_number, executed_at
FROM paper_trade_transaction
WHERE user_id = 1
  AND side = 'SELL'
ORDER BY transaction_id DESC
LIMIT 5;
```

```sql
SELECT symbol, quantity, average_cost, total_cost
FROM paper_position
WHERE user_id = 1
  AND symbol = 'MSFT';
```

## 8. Full SELL Removes Position

Find remaining MSFT quantity:

```sql
SELECT quantity
FROM paper_position
WHERE user_id = 1
  AND symbol = 'MSFT';
```

Sell that remaining quantity:

```powershell
$fullSell = Invoke-PaperTrade -Side "sell" -Symbol "MSFT" -Quantity <REMAINING_QTY>
$fullSell.position
$fullSell.transaction | Format-List
```

Expected:

- `position` in the API response is empty/null
- SELL transaction is saved
- `paper_position` no longer has an MSFT row for this user

SQL:

```sql
SELECT *
FROM paper_position
WHERE user_id = 1
  AND symbol = 'MSFT';
```

Expected:

- no rows returned

## 9. Enhanced Portfolio After Trades

```powershell
$portfolio = Invoke-PaperGet "/api/paper-trading/portfolio"
$portfolio | Format-List
$portfolio.positions | Format-Table symbol, quantity, averageCost, investedCost, currentPrice, marketValue, unrealizedProfitLoss, unrealizedProfitLossPercent, portfolioWeightPercent, riskCategory
```

Expected portfolio formulas:

```text
totalInvestedCost     = sum(open position totalCost)
estimatedMarketValue  = sum(open position quantity * currentPrice)
unrealizedProfitLoss  = estimatedMarketValue - totalInvestedCost
realizedProfitLoss    = sum current-session SELL realizedProfitLoss
totalPortfolioValue   = cashBalance + estimatedMarketValue
returnPercentage      = (totalPortfolioValue - startingCash) / startingCash * 100
totalFeesPaid         = sum current-session BUY/SELL fees
```

Expected position formulas:

```text
investedCost                 = position totalCost
unrealizedProfitLoss         = marketValue - investedCost
unrealizedProfitLossPercent  = unrealizedProfitLoss / investedCost * 100
portfolioWeightPercent       = marketValue / totalPortfolioValue * 100
riskCategory                 = StockMetadata risk category for symbol
lastUpdated                  = stock.lastUpdated, fallback stock.updatedAt
```

SQL cross-check:

```sql
SELECT p.symbol,
       p.quantity,
       p.average_cost,
       p.total_cost AS invested_cost,
       s.current_price,
       p.quantity * s.current_price AS market_value,
       (p.quantity * s.current_price) - p.total_cost AS unrealized_pl
FROM paper_position p
JOIN stock s ON s.symbol = p.symbol
WHERE p.user_id = 1
ORDER BY p.symbol;
```

```sql
SELECT a.cash_balance,
       COALESCE(SUM(p.quantity * s.current_price), 0) AS estimated_market_value,
       a.cash_balance + COALESCE(SUM(p.quantity * s.current_price), 0) AS total_portfolio_value
FROM paper_trading_account a
LEFT JOIN paper_position p ON p.user_id = a.user_id
LEFT JOIN stock s ON s.symbol = p.symbol
WHERE a.user_id = 1
GROUP BY a.account_id, a.cash_balance;
```

## 10. Transaction History Filters

Default latest 50:

```powershell
$tx = Invoke-PaperGet "/api/paper-trading/transactions"
$tx | Select-Object -First 5 | Format-Table transactionId, symbol, side, quantity, fee, netAmount, realizedProfitLoss, isCurrentSession, sessionNumber
```

Expected:

- newest first
- includes both current-session and old-session transactions
- returns a list, not a paged wrapper

Filter by symbol:

```powershell
Invoke-PaperGet "/api/paper-trading/transactions?symbol=MSFT" |
  Format-Table transactionId, symbol, side, quantity, netAmount
```

Filter by side:

```powershell
Invoke-PaperGet "/api/paper-trading/transactions?side=SELL" |
  Format-Table transactionId, symbol, side, realizedProfitLoss
```

Filter by date-only range:

```powershell
$today = Get-Date -Format "yyyy-MM-dd"
Invoke-PaperGet "/api/paper-trading/transactions?from=$today&to=$today" |
  Format-Table transactionId, symbol, side, transactionTime
```

Filter with paging:

```powershell
Invoke-PaperGet "/api/paper-trading/transactions?page=0&size=2" |
  Format-Table transactionId, symbol, side, transactionTime

Invoke-PaperGet "/api/paper-trading/transactions?page=1&size=2" |
  Format-Table transactionId, symbol, side, transactionTime
```

Expected:

- `page=0&size=2` returns the newest two matching rows
- `page=1&size=2` returns the next two matching rows
- `size` must be between 1 and 100

SQL comparison:

```sql
SELECT transaction_id, symbol, side, quantity, fee, net_amount,
       realized_profit_loss, is_current_session, session_number, executed_at
FROM paper_trade_transaction
WHERE user_id = 1
ORDER BY executed_at DESC
LIMIT 50;
```

## 11. Transaction Detail Endpoint

Pick a transaction ID:

```powershell
$tx = Invoke-PaperGet "/api/paper-trading/transactions"
$transactionId = $tx[0].transactionId
$detail = Invoke-PaperGet "/api/paper-trading/transactions/$transactionId"
$detail | Format-List
```

Expected:

```text
transactionId        : <selected id>
price                : same as executionPrice
grossAmount          : pre-fee amount
fee                  : flat fee for BUY/SELL, 0 for RESET
netAmount            : actual cash impact after fee
totalAmount          : same as netAmount
realizedProfitLoss   : SELL P/L, 0 for BUY/RESET
isCurrentSession     : True or False
sessionNumber        : account session number for that row
transactionTime      : same as executedAt
```

Missing or other-user transactions should return 404:

```powershell
try {
  Invoke-PaperGet "/api/paper-trading/transactions/999999999"
} catch {
  $_.Exception.Response.StatusCode.value__
  $_.ErrorDetails.Message
}
```

Expected:

```text
404
{"message":"Paper trade transaction not found","status":404}
```

## 12. Portfolio Reset

Run reset:

```powershell
$reset = Invoke-PaperPost "/api/paper-trading/portfolio/reset"
$reset | Format-List
```

Expected:

```text
cashBalance          : 1000000.0000
startingCash         : 1000000.0000
currentSessionNumber : previous session + 1
lastResetAt          : <timestamp>
positions            : {}
totalInvestedCost    : 0.0000
estimatedMarketValue : 0.0000
totalPortfolioValue  : 1000000.0000
returnPercentage     : 0.00
```

Check old and new transactions:

```powershell
Invoke-PaperGet "/api/paper-trading/transactions" |
  Select-Object -First 10 |
  Format-Table transactionId, symbol, side, quantity, fee, netAmount, isCurrentSession, sessionNumber
```

Expected:

- one newest RESET row
- RESET has `symbol` empty/null, `quantity = 0`, `fee = 0`, `netAmount = 0`
- previous current-session rows are now `isCurrentSession = False`
- old history remains visible

Check current-session-only:

```powershell
Invoke-PaperGet "/api/paper-trading/transactions?currentSessionOnly=true" |
  Format-Table transactionId, symbol, side, isCurrentSession, sessionNumber
```

Expected:

- only current-session rows
- immediately after reset this should include the RESET marker

Check RESET rows:

```powershell
Invoke-PaperGet "/api/paper-trading/transactions?side=RESET" |
  Format-Table transactionId, symbol, side, quantity, fee, netAmount, isCurrentSession, sessionNumber
```

SQL:

```sql
SELECT account_id, cash_balance, starting_cash,
       current_session_number, last_reset_at, updated_at
FROM paper_trading_account
WHERE user_id = 1;
```

```sql
SELECT transaction_id, symbol, side, quantity,
       execution_price, gross_amount, fee, net_amount,
       realized_profit_loss, cash_balance_after,
       is_current_session, session_number, executed_at
FROM paper_trade_transaction
WHERE user_id = 1
ORDER BY transaction_id DESC
LIMIT 20;
```

```sql
SELECT *
FROM paper_position
WHERE user_id = 1;
```

Expected:

- account session incremented
- RESET marker is current session
- pre-reset rows are not current session
- no open positions remain for the current user

Optional troubleshooting for older local MySQL databases:

```sql
ALTER TABLE paper_trade_transaction
MODIFY symbol VARCHAR(10) NULL;
```

Use this only if an existing local database was created before RESET supported
`symbol = NULL` and reset fails with a symbol nullability error. Do not add
Flyway/Liquibase for US010, and do not switch the dev profile to `create`.

## 13. Behavior Profile Checks

Before a reset or invalid call, capture latest behavior timestamp:

```sql
SELECT behavior_profile_id, behavior_confidence, behavior_style,
       behavior_risk_score, most_traded_symbols, updated_at
FROM user_behavior_profile
WHERE user_id = 1
ORDER BY updated_at DESC
LIMIT 1;
```

BUY/SELL expected behavior:

- successful BUY/SELL triggers behavior recalculation after the trade commits
- behavior recalculation uses current-session valid BUY/SELL rows
- RESET rows are ignored
- null-symbol rows are ignored
- quantity `<= 0` rows are ignored
- behavior is not calculated from watchlist, views, clicks, or dismissed suggestions

After reset, query the latest profile again:

```sql
SELECT behavior_profile_id, behavior_confidence, behavior_style,
       behavior_risk_score, most_traded_symbols, updated_at
FROM user_behavior_profile
WHERE user_id = 1
ORDER BY updated_at DESC
LIMIT 1;
```

Expected:

- reset does not delete the behavior profile
- reset does not create a new profile by itself
- reset does not downgrade or overwrite behavior fields
- future BUY/SELL after reset can recalculate using current-session trades

## 14. Negative Tests

Unsupported symbol:

```powershell
try {
  Invoke-PaperTrade -Side "buy" -Symbol "META" -Quantity 1
} catch {
  $_.Exception.Response.StatusCode.value__
  $_.ErrorDetails.Message
}
```

Expected:

```text
400
{"message":"Unsupported paper-trading symbol: META","status":400}
```

Invalid quantity:

```powershell
try {
  Invoke-PaperTrade -Side "buy" -Symbol "MSFT" -Quantity 0
} catch {
  $_.Exception.Response.StatusCode.value__
  $_.ErrorDetails.Message
}
```

Expected:

```text
400
{"message":"Quantity must be positive","status":400}
```

Fractional quantity should fail JSON validation/deserialization:

```powershell
try {
  Invoke-PaperPost `
    -Path "/api/paper-trading/buy" `
    -Body @{ symbol = "MSFT"; quantity = 1.5 }
} catch {
  $_.Exception.Response.StatusCode.value__
  $_.ErrorDetails.Message
}
```

Expected:

- HTTP 400
- no account, position, transaction, or behavior update for that request

Oversell:

```powershell
try {
  Invoke-PaperTrade -Side "sell" -Symbol "MSFT" -Quantity 999999
} catch {
  $_.Exception.Response.StatusCode.value__
  $_.ErrorDetails.Message
}
```

Expected:

```text
400
{"message":"Sell quantity exceeds held paper shares","status":400}
```

Invalid transaction filters:

```powershell
try {
  Invoke-PaperGet "/api/paper-trading/transactions?side=BAD"
} catch {
  $_.Exception.Response.StatusCode.value__
  $_.ErrorDetails.Message
}

try {
  Invoke-PaperGet "/api/paper-trading/transactions?from=not-a-date"
} catch {
  $_.Exception.Response.StatusCode.value__
  $_.ErrorDetails.Message
}

try {
  Invoke-PaperGet "/api/paper-trading/transactions?size=101"
} catch {
  $_.Exception.Response.StatusCode.value__
  $_.ErrorDetails.Message
}
```

Expected:

- HTTP 400
- clear validation message

Unauthenticated request:

```powershell
try {
  Invoke-RestMethod -Method Get -Uri "$base/api/paper-trading/portfolio"
} catch {
  $_.Exception.Response.StatusCode.value__
}
```

Expected:

```text
401
```

## 15. No OpenAI Or Twelve Data Calls From Paper Trading

Paper-trading endpoints should only use stored backend rows. They must not call
OpenAI or Twelve Data directly.

Practical checks:

- BUY/SELL fail if `stock.current_price` is missing instead of fetching a new price
- reset succeeds without stock lookup
- no `stock_ai_suggestion_batch` row is created by paper-trading calls
- no OpenAI response/prompt row is created by paper-trading calls

SQL:

```sql
SELECT COUNT(*) AS suggestion_batches_for_user
FROM stock_ai_suggestion_batch
WHERE user_id = 1;
```

Run a BUY/SELL/RESET, then run the count again.

Expected:

- count should not increase because of paper-trading endpoints

## 16. Acceptance Checklist

- Account auto-creates for current user.
- New accounts start at `current_session_number = 1`.
- BUY subtracts `grossAmount + fee`.
- BUY includes fee in position cost basis.
- SELL adds `grossAmount - fee`.
- SELL stores realized P/L.
- Full SELL deletes the open position.
- Portfolio shows invested cost, market value, unrealized P/L, realized P/L, fees, total value, and return percentage.
- Transaction history filters by symbol, side, date range, page, size, and current session.
- Transaction detail is current-user scoped.
- Reset clears only current user's positions.
- Reset marks old current-session rows false.
- Reset creates a current-session RESET marker with `symbol = NULL`.
- Reset does not recalculate or overwrite behavior profile.
- BUY/SELL behavior recalculation is best-effort after commit.
- Invalid requests do not partially persist account/position/transaction changes.
- No OpenAI or Twelve Data call is introduced by paper-trading endpoints.

## 17. Automated Verification

Run from `backend`:

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd clean compile
git diff --check
```

If `.\mvnw.cmd` fails with the local wrapper issue, use the wrapper-downloaded
Maven binary and record the fallback in your verification notes.

Expected:

```text
BUILD SUCCESS
Tests run: <number>, Failures: 0, Errors: 0, Skipped: 0
```


## 18. Additional Notes
Jackson float-to-integer coercion is disabled through ObjectMapperConfig, so fractional quantities such as 1.5 are rejected instead of being converted into integer quantity values.

RESET transactions intentionally store symbol as null because a portfolio reset is a session marker rather than a stock-specific BUY or SELL transaction.