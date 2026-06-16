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

Implementation should later create a typed API layer. Do not add code during documentation-only tasks.

Recommended modules:

- `authApi`
- `profileApi`
- `stocksApi`
- `suggestionsApi`
- `watchlistApi`
- `paperTradingApi`
- `adminUsersApi`
- `adminAiApi`
- `adminStocksApi`

Use `fetch` or Expo-compatible fetch. Avoid Axios unless a later implementation task gives a strong reason.

## Base URL

Use:

```text
EXPO_PUBLIC_API_BASE_URL=http://localhost:8080
```

Only `EXPO_PUBLIC_` variables are available in the client bundle. Never put secrets, admin tokens, OpenAI keys, Twelve
Data keys, database passwords, or write credentials in `EXPO_PUBLIC_` values.

## Basic Auth MVP

The backend currently uses HTTP Basic Auth.

MVP behavior:

- Login collects email/username and password.
- Requests include `Authorization: Basic ...`.
- `POST /api/auth/login` updates `lastLoginAt`.
- `GET /api/auth/me` bootstraps the current session without updating `lastLoginAt`.
- Logout clears credentials and query cache.

Credential storage decision:

- Create an auth storage abstraction later.
- MVP default: in-memory/session credentials during development.
- Native demo may optionally use `expo-secure-store` later only after approval.
- Web admin should avoid persistent password storage.
- Admin token must never be hardcoded or committed.
- Long-term improvement could be JWT/session auth, but that is not MVP scope.

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
retryable: boolean
```

Expected handling:

- `400`: validation or invalid request, show field/form message.
- `401`: unauthenticated or wrong credentials, clear auth and route to login where appropriate.
- `403`: forbidden, show role/admin permission message.
- `404`: missing stock/user/transaction, show not-found state.
- `409`: conflict, such as duplicate account or admin self-disable.
- Network timeout: show retry for reads, re-enable writes.
- Backend unavailable: show friendly outage state.

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
- Show delayed badge/note on stock list/detail when backend provides delayed data.
- Show displayed market time if backend exposes it.
- Show last backend update time separately if available.
- Never calculate trusted displayed price, displayed market time, or paper-trading execution price in the frontend.
- Never call Twelve Data directly.
- Use backend history points as returned.
- Do not silently invent or fill missing 1-minute candles.

Current backend contract:

- Stock list/detail/history responses expose backend delayed display fields including `displayedPrice`,
  `displayedPercentChange`, `displayedMarketTime`, `targetDisplayMarketTime`, `dataDelayMinutes`,
  `priceFreshnessStatus`, `isPriceAvailable`, `isTradeExecutable`, `dataNote`, `priceSource`, `marketTimeZone`, and
  where applicable `lastBackendUpdatedAt`.
- Frontend display should prefer `displayedPrice`, `displayedPercentChange`, `priceSource`, `displayedMarketTime`,
  `targetDisplayMarketTime`, and `priceFreshnessStatus`.
- Legacy `currentPrice`, `percentChange`, and `lastUpdated` are backward-compatible stock table snapshot fields.
- Stock detail `dataSource` is a legacy analysis-source field. Use `analysisDataSource`, `snapshotHighPrice`,
  `snapshotLowPrice`, and `snapshotTimeframe` for latest analysis snapshot metadata.
- Stock detail `highPrice` and `lowPrice` describe the displayed/latest day range selected by the delayed market view.
- `1D` history can return stored intraday chart rows even when quote metadata uses daily fallback during pre-open.
- Paper-trading buy/sell accepts only `symbol` and `quantity`; backend decides execution price using the delayed stored
  market price selector.
