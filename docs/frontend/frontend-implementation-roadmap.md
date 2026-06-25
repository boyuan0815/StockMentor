# Frontend Implementation Roadmap

This roadmap sequences future frontend implementation. It does not authorize coding outside a scoped implementation
task.

Detailed execution planning now lives in `docs/frontend/frontend-implementation-master-plan.md` and copy-paste phase
prompts live in `docs/frontend/frontend-phase-prompts.md`. Use those two docs as the detailed source of truth; keep this
roadmap as a high-level overview.

## Phase 1: Foundation

- Status: complete. The current `frontend/` folder has the StockMentor route shell, API core, session providers, theme
  tokens, and placeholder screens.
- Replace Expo starter routes with StockMentor route groups.
- Add theme tokens and shared layout components.
- Add `AuthProvider`.
- Add API client abstraction.
- Add error normalization.
- Add route guards for public, beginner, onboarding, and admin routes.
- Keep components, API, types, hooks, and utilities outside `app/`.

Do not add backend calls to OpenAI or Twelve Data. The frontend only calls the Spring Boot backend.

## Phase 2: Auth And Onboarding

- Status: complete through Phase 2B/2.5.
- Welcome, register, and login are implemented with memory-only Basic Auth credentials, local pending state,
  duplicate-submit guards, keyboard-aware layouts, and sanitized dev diagnostics.
- `/api/auth/me` bootstrap, role routing, onboarding required routing, logout, and admin placeholder routing are implemented.
- Onboarding is implemented as a one-question-at-a-time mobile flow with fixed bottom actions, all-question validation,
  exact `{ answers: [{ questionId, optionId }] }` submit payloads, result/profile states, confirmed retake, and
  timeout/account-state recovery.
- Profile summary, profile reload, account refresh, and retake confirmation are implemented.

Acceptance:

- New beginner is routed to onboarding.
- Completed beginner is routed to dashboard.
- Admin is routed to admin shell.
- Logout clears credentials, admin token, query cache if present, and storage abstraction state.

## Phase 3: Beginner Stock Learning

- Status: Phase 3B stock-learning UI standard is implemented in the working tree and should be preserved by later
  phases.
- Watchlist tab with `Watchlists` table.
- Stocks tab with compact `Stocks` table.
- Search tab plus hidden contextual `/stocks/search-context` route.
- Stock detail with measured dynamic header, quote panel, history summary/list, and guarded practice-trade CTA.
- Timeframe tabs and backend-returned history points.
- Watchlist add/remove with toast and duplicate-tap guards.
- AI explanation drawer opened on demand only.

Acceptance:

- Uses stored backend data only.
- History summary/list uses backend points exactly; no chart dependency is assumed.
- AI explanation has educational disclaimer and hides backend cache/generated status messages.
- Stock data is labeled as 15-minute delayed educational market data.
- UI prefers backend delayed display fields such as `displayedPrice`, `displayedPercentChange`, `displayedMarketTime`,
  `targetDisplayMarketTime`, and `priceFreshnessStatus`, while compact rows hide raw source/status/time values.
- Search empty state shows Search History and max three Latest Viewed Stocks, not the full supported stock list.
- Explicit return params keep Stocks/Watchlist/Search/Detail/Portfolio back navigation deterministic.
- Focused stock and portfolio screens use soft focus/minute-boundary refresh against stored backend data; hidden tabs do
  not poll.

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
- Portfolio with fixed `Assets` and `History` tabs.
- Stock-scoped Paper Trade ticket shared by buy/sell routes.
- Reset Portfolio bottom sheet.
- History and transaction detail.

Acceptance:

- Buy, sell, and reset require explicit confirmation.
- Quantity accepts whole shares only.
- No external brokerage, OpenAI, or Twelve Data calls.
- Frontend never sends price, amount, fee, or max quantity.
- Ticket fee/amount/max quantity values are display-only estimates.
- Buy/sell success redirects to Portfolio History.
- Backend buy/sell uses the delayed stored market price selector.
- Today P/L remains hidden until a backend field with that exact meaning exists.

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

- React Query may be recommended in Phase 1, but dependency installation requires a separate user-approved task before
  package files are changed.
- SecureStore use for native demo.
- Chart library.
- UI library.
- Ongoing local CORS origin maintenance through `stockmentor.cors.allowed-origins` when testing Expo Web from a new LAN
  or tunnel origin.
- Separate admin web project.

## Backend Contract For 15-Minute Delayed Market Data

Frontend implementation should consume the existing backend contract:

- US009 stock responses expose delayed display price/time/freshness fields.
- US009 `1D` history uses backend-returned stored intraday rows only.
- Frontend must not fill or invent missing `1D` rows.
- `1D` history may return intraday rows even when quote metadata uses daily fallback during pre-open.
- US010 paper trading uses backend-decided delayed stored price metadata; frontend sends only `symbol` and `quantity`.
- Legacy stock fields remain for compatibility, but frontend display should prefer delayed fields.
