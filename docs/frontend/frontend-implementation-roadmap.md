# Frontend Implementation Roadmap

This roadmap sequences future frontend implementation. It does not authorize coding outside a scoped implementation task.

## Phase 1: Foundation

- Replace Expo starter routes with StockMentor route groups.
- Add theme tokens and shared layout components.
- Add `AuthProvider`.
- Add API client abstraction.
- Add error normalization.
- Add route guards for public, beginner, onboarding, and admin routes.
- Keep components, API, types, hooks, and utilities outside `app/`.

Do not add backend calls to OpenAI or Twelve Data. The frontend only calls the Spring Boot backend.

## Phase 2: Auth And Onboarding

- Welcome.
- Register.
- Login.
- `/api/auth/me` bootstrap.
- Onboarding required routing.
- Onboarding quiz.
- Onboarding result / profile summary.

Acceptance:

- New beginner is routed to onboarding.
- Completed beginner is routed to dashboard.
- Admin is routed to admin shell.
- Logout clears credentials, admin token, query cache, and storage abstraction state.

## Phase 3: Beginner Stock Learning

- Home dashboard.
- Stock list.
- Stock detail.
- Simple educational line chart.
- Timeframe tabs.
- Watchlist add/remove.
- AI explanation.

Acceptance:

- Uses stored backend data only.
- Chart shows timeframe and data/fallback notes.
- AI explanation has educational disclaimer.
- Stock data is labeled as 15-minute delayed educational market data when backend supports delayed display metadata.
- Current backend lacks dedicated delayed display fields, so full delayed display is a backend follow-up.

## Phase 4: AI Suggestions

- Suggestion list.
- Remaining stocks.
- Refresh action.
- Cooldown display.
- Dismiss/watchlist suggestion actions.
- Fallback/unavailable messaging.

Acceptance:

- GET stays read-only/cache-only.
- Refresh button respects `refreshAllowed` and `nextRefreshAllowedAt`.

## Phase 5: Paper Trading

- Paper account.
- Portfolio.
- Buy.
- Sell.
- Reset.
- Transactions.
- Transaction detail.

Acceptance:

- Buy, sell, and reset require confirmation.
- Quantity accepts whole shares only.
- No external brokerage, OpenAI, or Twelve Data calls.
- Frontend never sends price.
- Copy states that practice trades use StockMentor's delayed stored price, not a live market quote.
- Full delayed execution-price consistency is a backend follow-up while backend buy/sell still uses stock `currentPrice`.

## Phase 6: Admin Web/Tablet

- Admin login/token entry.
- Admin dashboard.
- Users list.
- User detail.
- Disable/re-enable confirmation.
- AI suggestion monitoring.
- Refresh job monitoring.
- Manual scheduled refresh.
- Stock maintenance.

Acceptance:

- Admin pages require ADMIN role and admin token.
- Phone-sized admin shows best-viewed-on-tablet/web message.
- Destructive/sensitive actions require confirmation.

## Phase 7: Testing And Polish

- Run frontend testing checklist.
- Validate Expo mobile.
- Validate Expo Web admin after backend CORS follow-up.
- Review HCI/accessibility.
- Review empty/loading/error states.
- Prepare FYP demo script.

## Deferred Decisions

- React Query dependency installation.
- SecureStore use for native demo.
- Chart library.
- UI library.
- Backend CORS implementation.
- Separate admin web project.

## Backend Follow-Ups For 15-Minute Delayed Market Data

Future backend implementation will likely need changes in:

- `StockMarketDataController`, `StockMarketDataServiceImpl`, and stock response DTOs: expose delayed display price/time/freshness fields for US009.
- `StockPriceHistoryRepository`: query closest stored 1-minute candle at or before displayed market time.
- `StockPriceDailyRepository`: support fallback display when intraday delayed data is unavailable.
- `MarketTimeService`: centralize New York displayed-market-time rules, including before 9:45 AM and after 4:15 PM behavior.
- `StockScheduler` and stock backfill service paths: preserve current retrieval cadence while supporting delayed-display quality.
- `AdminStockController` and `StockService`: keep US008 maintenance/backfill/cleanup as tools that improve stored data quality.
- `PaperTradingServiceImpl` and paper-trading DTOs: select and expose backend-decided delayed execution price/time for US010.
- Stock and paper-trading tests: cover delayed stock display, missing delayed candles, opening/closing behavior, and delayed paper-trade execution.
