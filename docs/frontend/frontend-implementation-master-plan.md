# StockMentor Frontend Implementation Master Plan

This document is the current master plan for future StockMentor frontend work. It should be used with
`docs/frontend/frontend-phase-prompts.md`, which contains copy-paste prompts for fresh Codex sessions.

The original phase order is no longer the actual implementation order. Stock learning, interactive charts, watchlist
edit, paper trading, portfolio history pagination, and the Stocks portfolio card were implemented before the full AI
Suggestions UI and admin console.

## Current Baseline

- Frontend stack: Expo SDK 54, React Native 0.81, Expo Router, TypeScript.
- The Spring Boot backend is the only app data source.
- `EXPO_PUBLIC_API_BASE_URL` configures the backend base URL.
- Auth/onboarding/profile are implemented.
- Visible beginner tabs are `Watchlist`, `Stocks`, `Suggestions`, `Portfolio`, `Profile`, and `Search`.
- Watchlist, Stocks, Suggestions, Portfolio, and Profile use fixed StockMentor-logo headers where applicable; Search
  remains the rightmost real tab with its search-row header.
- Watchlist is implemented with compact rows, add/remove, focus/minute-boundary refresh, and a separate stacked edit
  route for reorder/batch remove.
- Stocks is implemented with compact rows, delayed educational market data, a paper portfolio tier card, and guarded
  paper-trade entry.
- Stock detail is implemented with delayed quote metadata, interactive line/candle charts, and on-demand AI explanation.
- Portfolio/Paper Trading is implemented with `Assets`/`History`, backend P/L fields, stock-scoped buy/sell tickets,
  reset bottom sheet, paged transactions, and transaction detail.
- Full AI Suggestions UI is still pending.
- Admin web/tablet console is still pending beyond shell/placeholders.

## Cross-Phase Rules

Every future frontend phase must preserve these rules:

- Frontend calls the Spring Boot backend only.
- Frontend must not call OpenAI, Twelve Data, scheduler/backfill internals, paper-trading internals, broker APIs, or
  real-money trading services.
- Frontend must not send trusted `userId` for current-user normal flows.
- Paper-trading BUY/SELL request bodies must stay exactly `{ symbol, quantity }`. UI price, fee, amount, and max
  quantity remain display-only estimates.
- Stock display must prefer backend delayed display fields and backend freshness labels.
- The frontend must not synthesize missing stock history rows or fake candlesticks by copying close into OHLC.
- Use `frontend/utils/safe-storage.ts` for non-sensitive same-device persistence; never store Basic Auth credentials,
  admin tokens, authorization headers, API keys, or request bodies with secrets.
- Keep route files thin; put feature logic, components, API calls, DTO types, and helpers outside `frontend/app`.
- Pass explicit return params for tab/context flows instead of relying on tab history alone.
- AI wording must stay educational and must not present financial advice, price predictions, or buy/sell pressure.
- `.agents/`, `skills-lock.json`, protected package/lock files, and `frontend/.gitignore` must not be modified unless
  the user explicitly scopes that work.
- Do not stage or commit unless the user explicitly asks.
- Verify backend endpoints and DTOs from code before implementing or documenting API calls.

## Dependency State

Current real frontend dependencies include:

- `react-native-wagmi-charts`
- `react-native-svg`
- `react-native-draggable-flatlist`
- `react-native-reanimated`
- `react-native-gesture-handler`
- `react-native-worklets`

Do not add more dependencies by default. If a future task needs one, inspect existing packages first, explain why the
current stack is insufficient, and get approval before changing package/lock files.

## Completed Work To Preserve

### Foundation

- Expo Router route shell.
- API client core with Basic Auth/admin-token support.
- Theme and shared primitives.
- Auth/session provider and route guards.

### Auth, Onboarding, Profile

- Register/login.
- `/api/auth/me` bootstrap.
- Role routing.
- Onboarding quiz/result/profile.
- Retake flow and logout.

### Stock Learning

- Watchlist, Stocks, Search, stock detail.
- Delayed market display metadata.
- Safe search/latest-viewed persistence.
- Market notice marquee strips.
- Stock detail AI explanation drawer.
- Interactive chart with frontend timeframes `1D`, `5D`, `1M`, `3M`, `YTD`, `1Y`; backend-compatible `7D` hidden.

### Watchlist Edit

- Nested `/watchlist/edit` stacked route.
- Local search.
- Checkbox selection and select all.
- `Top` move-to-top.
- Drag reorder through `react-native-draggable-flatlist`.
- Backend reorder and batch remove.

### Paper Trading / Portfolio

- Portfolio Assets/History tabs.
- Backend `totalProfitLoss`, `todayProfitLoss`, `todayProfitLossPercent`, and current-session fee/P&L fields.
- Stock-scoped buy/sell tickets.
- Reset-card bottom sheet.
- Paged transaction history with default `size=20`.
- Transaction detail.

## Remaining Feature Phases

### 1. AI Stock Suggestions Completion

This is the recommended next feature phase.

Implement the full Suggestions tab using:

- `GET /api/stocks/ai-suggestions`
- `POST /api/stocks/ai-suggestions/refresh`
- `PATCH /api/stocks/ai-suggestions/items/{itemId}/dismiss`
- `PATCH /api/stocks/ai-suggestions/items/{itemId}/watchlist`

Requirements:

- GET remains cache-only/read-only in UI behavior.
- Refresh respects backend cooldown.
- Item actions prevent duplicate submits and recover on failure.
- Delayed display fields on suggestion items are rendered where present.
- No frontend OpenAI call.

### 2. Admin Web/Tablet Console

Implement after or separately from AI Suggestions:

- Admin token entry/re-entry.
- Dashboard.
- Users list/detail/status update.
- AI suggestion monitoring.
- Refresh job monitoring.
- Stock maintenance/backfill form.
- Phone-sized fallback message.

Admin API calls require Basic Auth plus `X-Admin-Token`.

### 3. Final Integration, Demo, Accessibility, And Regression Polish

Run after selected feature phases:

- Execute `frontend-testing-checklist.md`.
- Verify Expo mobile and Expo Web where practical.
- Review accessibility, contrast, touch targets, modal behavior, keyboard behavior, and loading/error states.
- Fix only verified regressions.

### 4. Optional Manual UI/HCI Correction Pass

Use only when screenshots/device testing identify concrete issues. Keep patches narrow and do not redesign completed
flows broadly.

## Required Handoff Format

Every implementation phase should end with:

- Files changed.
- Features completed.
- Known limitations.
- Manual test results, or explicit note that manual testing was not run.
- Verification commands run.
- Backend endpoint/DTO mismatches found.
- Dependency changes, if any.
- Protected-file status.
- Confirmation that nothing was staged or committed unless explicitly requested.
