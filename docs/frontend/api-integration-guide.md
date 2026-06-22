# Frontend API Integration Guide

## Backend Source Of Truth

The frontend calls the Spring Boot backend only.

The frontend must not:

- call OpenAI
- call Twelve Data
- call external brokerage APIs
- generate AI suggestions
- generate AI explanations
- calculate behavior profiles
- bypass backend validation
- trust locally stored `userId` for current-user actions

## API Client Direction

The frontend foundation includes a typed API client core, backend base URL helper, Basic Auth header support, admin token
header support, request timeout/cancellation handling, JSON parsing, normalized `ApiError`, sanitized auth diagnostics,
and the landed auth/profile API modules used by Phase 2B/2.5. Later feature phases should add stock, suggestion,
watchlist, paper-trading, and admin API modules on top of that core.

Recommended later modules:

- `stocksApi`
- `suggestionsApi`
- `watchlistApi`
- `paperTradingApi`
- `adminUsersApi`
- `adminAiApi`
- `adminStocksApi`

Use `fetch` or Expo-compatible fetch. Avoid Axios unless a later implementation task gives a strong reason.

Phase 3B stock-learning modules live outside `frontend/app` and should remain the pattern for future work: route files
stay thin, API calls go through the existing Basic Auth API client, DTO types live in `frontend/types`, and stock display
helpers live in `frontend/utils`.

## Base URL

Use:

```text
EXPO_PUBLIC_API_BASE_URL=http://localhost:8080
```

Only `EXPO_PUBLIC_` variables are available in the client bundle. Never put secrets, admin tokens, OpenAI keys, Twelve
Data keys, database passwords, or write credentials in `EXPO_PUBLIC_` values.

For Expo Web/Safari local testing, the backend must allow the active web origin through
`stockmentor.cors.allowed-origins` and must permit unauthenticated `OPTIONS` preflight. This is configuration, not
frontend secret storage.

## Basic Auth MVP

The backend currently uses HTTP Basic Auth.

MVP behavior:

- Login collects email/username and password.
- Requests include `Authorization: Basic ...`.
- `POST /api/auth/login` updates `lastLoginAt`.
- `GET /api/auth/me` bootstraps the current session without updating `lastLoginAt`.
- Logout clears credentials and query cache if present.

Credential storage decision:

- Create an auth storage abstraction later.
- MVP default: in-memory/session credentials during development.
- Native demo may optionally use `expo-secure-store` later only after approval.
- Web admin should avoid persistent password storage.
- Admin token must never be hardcoded or committed.
- Basic Auth credentials remain memory-only in the Phase 2B/2.5 MVP and must not be written to localStorage,
  SecureStore, AsyncStorage, files, logs, or diagnostics without a separately approved storage design.
- Long-term improvement could be JWT/session auth, but that is not MVP scope.

## Safe Local Storage

Use `frontend/utils/safe-storage.ts` for non-sensitive same-device persistence such as search history and latest viewed
stocks. Safe storage wraps AsyncStorage and falls back to in-memory session values if the native module is unavailable
or throws. Direct AsyncStorage calls are not allowed in app flows because Expo Go/native-module issues must not
red-screen the app.

Safe storage must not hold passwords, Basic Auth values, admin tokens, `Authorization`, `X-Admin-Token`, API keys,
request bodies containing secrets, or private diagnostics.

## Admin Token UX

Admin login should ask for:

- Basic Auth credentials
- admin token

Role is verified through `/api/auth/login` or `/api/auth/me`. Admin token is required only for admin API calls. Invalid
or missing admin token should show a clear re-entry prompt.

Admin token storage:

- session-only
- never committed
- never placed in `EXPO_PUBLIC_`
- cleared on logout

## Error Normalization

Normalize frontend API errors into a shared shape:

```text
status: number
message: string
code?: string
field?: string
fields?: Record<string, string>
retryable: boolean
```

Expected handling:

- `400`: validation or invalid request, show field/form message.
- `401`: unauthenticated or wrong credentials, clear auth and route to login where appropriate.
- `403`: forbidden, show role/admin permission message.
- `404`: missing stock/user/transaction, show not-found state.
- `409`: conflict, such as duplicate account or admin self-disable.
  Duplicate registration can include `fields.email` and/or `fields.username`; map those to the matching form fields
  when present.
- Network timeout: show retry for reads, re-enable writes.
- Backend unavailable: show friendly outage state.

HCI requirements:

- Missing or invalid `EXPO_PUBLIC_API_BASE_URL`, timeout, network/CORS-like failures, and real HTTP responses should
  have distinct user-facing copy.
- Failed auth/register writes must keep the form mounted, preserve entered values, and re-enable actions after the
  request settles.
- Auth forms should mirror verified backend format rules on the client before sending a request, while keeping backend
  validation authoritative.
- When the backend can identify exact validation or conflict fields, surface those messages on the matching inputs so
  beginner users can recover without guessing.
- Server-reported field conflicts should keep the related submit action disabled until the affected field is edited and
  client-side validation passes again.
- If an onboarding submit times out or returns an account-state mismatch, show a recovery action that refreshes
  `GET /api/auth/me`; if the refreshed user no longer needs onboarding, route out of the quiz instead of trapping them.
- Dev diagnostics may show sanitized API base URL, auth method/path, status, code, and normalized message only. They
  must never show passwords, Basic Auth values, `Authorization`, `X-Admin-Token`, admin token values, request bodies,
  API keys, or secrets.

Forgot-password/reset is not part of the Basic Auth MVP. A real flow requires backend reset-token or OTP design, email
delivery, expiry, cooldown/rate limiting, and abuse protection before the frontend should expose an enabled action.

## React Query Direction

Add React Query later for server state. Do not install it during documentation tasks.

Use it for:

- query caching
- loading and error state
- request deduplication
- mutation pending state
- query invalidation after mutations
- controlled refetching

Mutation rules:

- disable related buttons while `isPending`
- do not auto-retry unsafe writes by default
- invalidate relevant read queries on success
- keep backend as final source of truth

## Request Timeout And Cancellation

Future API client should support:

- request timeout
- `AbortController` cancellation
- ignoring abort errors in UI
- safe retry for read-only requests
- no automatic retry for buy, sell, reset, status update, stock maintenance, or AI refresh without user action

## Cooldown Handling

AI suggestion response includes:

- `refreshAllowed`
- `nextRefreshAllowedAt`

Frontend must:

- disable refresh when not allowed
- show next allowed refresh time
- avoid repeated refresh calls on focus
- use backend response as source of truth

## No Mock Data Rule

Mock or demo data is allowed only for isolated UI preview or temporary development.

Rules:

- mock data must be clearly marked
- mock data must be removed before final integration
- real app flows must not silently use fake backend responses
- demos should use seeded or real backend data where possible

## 15-Minute Delayed Market Data Contract

Intended product behavior:

```text
displayedMarketTime = current New York market time - 15 minutes
```

This formula describes the active delayed display window only. During pre-open, post-close, weekend, or holiday
behavior, the backend may instead select the latest completed trading day or stored daily close metadata.

Frontend responsibilities:

- Show "15-minute delayed educational market data" wording.
- Show delayed badge/note on stock list/detail from expected backend delayed metadata.
- Use `priceFreshnessStatus`, `isPriceAvailable`, and `dataNote` to handle unavailable or null delayed data states.
- Show last backend update time separately if available.
- Never calculate trusted displayed price, displayed market time, or paper-trading execution price in the frontend.
- Never call Twelve Data directly.
- Use backend history points as returned.
- Do not silently invent or fill missing 1-minute candles.

Current backend contract:

- Stock list/detail/history and watchlist stock responses expose backend delayed display fields including `displayedPrice`,
  `displayedPercentChange`, `displayedMarketTime`, `targetDisplayMarketTime`, `dataDelayMinutes`,
  `priceFreshnessStatus`, `isPriceAvailable`, `isTradeExecutable`, `dataNote`, `priceSource`, `marketTimeZone`, and
  where applicable `lastBackendUpdatedAt`.
- Frontend display should prefer `displayedPrice`, `displayedPercentChange`, `displayedMarketTime`,
  `targetDisplayMarketTime`, and `priceFreshnessStatus`, but compact stock rows must not expose raw `priceSource`,
  backend table names, or raw freshness enum values to users.
- Legacy `currentPrice`, `percentChange`, and `lastUpdated` are backward-compatible stock table snapshot fields.
- Stock detail `dataSource` is a legacy analysis-source field. Use `analysisDataSource`, `snapshotHighPrice`,
  `snapshotLowPrice`, and `snapshotTimeframe` for latest analysis snapshot metadata.
- Stock detail `highPrice` and `lowPrice` describe the displayed/latest day range selected by the delayed market view.
- Stock detail may expose `previousClose`, `displayedAbsoluteChange`, and `displayedVolume`. Use these backend fields
  for the quote panel when present. Do not calculate trusted absolute change or volume in the frontend.
- Watchlist stock rows expose the same delayed display fields as stock list/detail rows while keeping legacy
  `currentPrice` and `percentChange` compatibility fields.
- `1D` history can return stored intraday chart rows even when quote metadata uses daily fallback during pre-open.
- Paper-trading buy/sell accepts only `symbol` and `quantity`; backend decides execution price using the delayed stored
  market price selector.

Known backend formula issue for a future backend pass:

- `displayedPercentChange` currently comes from the delayed price selector and uses same-day open or the first
  intraday open as the baseline.
- `displayedAbsoluteChange` uses `previousClose` as the baseline.
- These fields are therefore inconsistent for broker-style display. Future backend work should align displayed percent
  change to the previous-close baseline if StockMentor wants broker-style movement semantics. Do not fix this in the
  frontend by inventing a percent change.

## Phase 3B Stock Endpoint Usage

- `GET /api/stocks` is the source for the Stocks table and typed search universe.
- `GET /api/watchlist` is the source for the Watchlist table and should use delayed fields from watchlist row DTOs.
- `GET /api/stocks/{symbol}` is the source for stock detail. Clear old detail state when `symbol` changes so stale
  stock content never flashes.
- `GET /api/stocks/{symbol}/history?timeframe=1D|7D|1M|3M|YTD|1Y` powers the history summary/list. Use returned points
  exactly; do not fill, smooth, or synthesize missing candles.
- `GET /api/stocks/{symbol}/ai-explanation?timeframe=1D|7D|1M|3M` is called only after the user opens the AI drawer.
  Do not prefetch this endpoint from list/detail initial load. For `YTD` and `1Y`, show unsupported explanation copy
  and send no request.
- Watchlist add/remove uses `POST /api/watchlist/{symbol}` and `DELETE /api/watchlist/{symbol}` with duplicate-tap
  guards. On failure, preserve the previous local state and show a toast.
