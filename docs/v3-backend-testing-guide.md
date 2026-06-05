# US006 V3 Backend Testing Guide

This guide verifies the backend-only paper-trading foundation added in US006 V3.
V3 keeps AI suggestions read-only on GET and uses paper trades only to update
`user_behavior_profile` after successful buy/sell actions.

## 1. Run Backend With Dev Profile

From PowerShell:

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

If the wrapper launch issue occurs locally, use the wrapper-downloaded Maven
binary workaround used during V2 verification.

```powershell
$base = "http://localhost:8080"
$beginnerAuth = "demo@stockmentor.local:Demo@12345"
```

## 2. Account

```powershell
curl.exe -i -u $beginnerAuth "$base/api/paper-trading/account"
```

Expected:

- `HTTP/1.1 200`
- `cashBalance` and `startingCash` use `stockmentor.paper-trading.initial-cash`
- default is `10000.0000`
- `status` is `ACTIVE`

## 3. Portfolio

```powershell
curl.exe -i -u $beginnerAuth "$base/api/paper-trading/portfolio"
```

Expected:

- `HTTP/1.1 200`
- cash fields are present
- `positions` is an array
- first access auto-creates the paper account if missing

## 4. Buy

```powershell
curl.exe -i -u $beginnerAuth `
  -H "Content-Type: application/json" `
  -d "{\"symbol\":\"MSFT\",\"quantity\":3}" `
  "$base/api/paper-trading/buy"
```

Expected:

- Uses backend `stock.currentPrice`
- Reduces `cashBalance`
- Creates or updates `paper_position`
- Creates immutable `paper_trade_transaction`
- Recalculates `user_behavior_profile`

## 5. Sell

```powershell
curl.exe -i -u $beginnerAuth `
  -H "Content-Type: application/json" `
  -d "{\"symbol\":\"MSFT\",\"quantity\":1}" `
  "$base/api/paper-trading/sell"
```

Expected:

- Uses backend `stock.currentPrice`
- Increases `cashBalance`
- Reduces position quantity
- Full sell removes the `paper_position` row
- Creates immutable SELL transaction
- Recalculates `user_behavior_profile`

## 6. Transactions

```powershell
curl.exe -i -u $beginnerAuth "$base/api/paper-trading/transactions"
```

Expected:

- `HTTP/1.1 200`
- latest 50 current-user transactions
- sorted newest first
- no transaction update/delete endpoint exists

## 7. Invalid Trade Checks

Unsupported symbol:

```powershell
curl.exe -i -u $beginnerAuth `
  -H "Content-Type: application/json" `
  -d "{\"symbol\":\"META\",\"quantity\":1}" `
  "$base/api/paper-trading/buy"
```

Invalid quantity:

```powershell
curl.exe -i -u $beginnerAuth `
  -H "Content-Type: application/json" `
  -d "{\"symbol\":\"MSFT\",\"quantity\":0}" `
  "$base/api/paper-trading/buy"
```

Expected:

- invalid requests return 400
- unsupported symbols are rejected
- no partial account, position, transaction, or behavior update is saved

## 8. AI Suggestions Regression

GET remains strictly read-only:

```powershell
curl.exe -i -u $beginnerAuth "$base/api/stocks/ai-suggestions"
```

Expected:

- no paper account creation
- no paper trade creation
- no behavior recalculation
- no suggestion batch creation
- no OpenAI call

Refresh can react to changed behavior profile:

```powershell
curl.exe -i -u $beginnerAuth -X POST "$base/api/stocks/ai-suggestions/refresh"
```

Expected:

- manual cooldown still applies
- unchanged input hashes are reused
- changed behavior profile timestamp/summary is included through the existing V2 behavior path

## 9. MySQL Checks

Account:

```sql
SELECT account_id, user_id, cash_balance, starting_cash, status, created_at, updated_at
FROM paper_trading_account
ORDER BY account_id DESC;
```

Positions:

```sql
SELECT position_id, user_id, symbol, quantity, average_cost, total_cost, realized_pl, created_at, updated_at
FROM paper_position
ORDER BY user_id, symbol;
```

Transactions:

```sql
SELECT transaction_id, user_id, symbol, side, quantity, execution_price, gross_amount,
       cash_balance_after, executed_at
FROM paper_trade_transaction
ORDER BY transaction_id DESC;
```

Behavior:

```sql
SELECT behavior_profile_id, user_id, behavior_style, behavior_confidence,
       behavior_risk_score, stock_risk_exposure_score, turnover_level,
       concentration_level, analysis_start_date, analysis_end_date, updated_at
FROM user_behavior_profile
ORDER BY user_id, updated_at DESC;
```

## 10. Test Command

```powershell
cd backend
.\mvnw.cmd clean test
```

If the local wrapper launch issue occurs, use the wrapper-downloaded Maven
binary workaround and document that in verification notes.
