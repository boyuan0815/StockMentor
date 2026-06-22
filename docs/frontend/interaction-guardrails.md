# Interaction Guardrails

These rules apply across beginner mobile and admin web/tablet screens.

## Submit And Tap Safety

- Disable submit buttons while a request is in progress.
- Prevent double tap and double submit at both button and mutation handler level.
- Disable relevant inputs while a write is pending.
- Show a visible loading state during requests.
- Re-enable actions after recoverable validation or network failure.

## Confirmation Rules

Confirmation is required for:

- paper-trading buy
- paper-trading sell
- portfolio reset
- admin user disable/re-enable
- admin scheduled AI refresh run
- admin stock backfill or cleanup
- removing a stock from watchlist when done from the watchlist screen

Every confirmation modal must:

- name the action
- name the affected stock/user/job when possible
- include cancel
- include close or safe dismissal
- disable confirm while pending

## AI Guardrails

- AI content must be framed as educational.
- Never present AI output as real financial advice.
- Do not predict future prices in UI wording.
- Do not say "best stock" or "buy now".
- Show fallback/unavailable notes when backend provides them.

## Market Data Guardrails

- Use "15-minute delayed educational market data" wording for stock display.
- Do not use wording that implies immediate market data.
- Frontend must not invent displayed price, displayed market time, or missing 1-minute candles.
- If backend returns empty history points or a message, show the backend message.
- Use backend delayed display metadata as the preferred display source.
- Handle unavailable delayed data states through backend fields such as `priceFreshnessStatus`, `isPriceAvailable`, and
  `dataNote`.
- Show displayed market time and last backend update time only where they help the user. Do not repeat raw
  `priceSource`, raw freshness values, displayed market time, or backend table names in every stock row.
- Practice trade confirmation must say: "Practice trade uses StockMentor's delayed stored price, not a live market quote."
- Compact stock and watchlist tables should use price and movement data only unless the backend exposes a reliable
  displayed/latest volume field for that row. Stock detail may show `displayedVolume` when the backend provides it.
- Market notice copy belongs in one compact marquee strip near the table/detail header. Preserve the full notice string,
  do not slice it, and do not use ellipsis.
- If a displayed/stored price is already available, do not tell users delayed prices are not ready. Use backend metadata
  to distinguish open delayed data, closed/latest stored data, pre-market stored data, truly unavailable data, and stale
  data.

## Toast Feedback

- Toast/snackbar feedback should be centralized, centered, lightweight, non-blocking, auto-dismissed, and English-only.
- Current stock UI toasts use dark navy `#052344`, white text, and visible shadow/elevation so they read as a separate
  layer without blocking the screen.
- Use toast feedback for watchlist success, watchlist failure, refresh cooldown, and unavailable placeholder actions.
- Retry after a failed AI explanation load may repeat the same on-demand request, but it must not be labeled as refresh
  or regenerate unless a backend refresh endpoint exists.

## Local Persistence Guardrails

- Use `frontend/utils/safe-storage.ts` for non-sensitive same-device persistence such as search history and latest
  viewed stocks.
- Safe storage must catch AsyncStorage/native-module failures and fall back to in-memory session values so Expo Go or
  native-module availability issues never red-screen the app.
- Never store passwords, Basic Auth values, admin tokens, `Authorization`, `X-Admin-Token`, API keys, request bodies
  containing secrets, or private diagnostics in AsyncStorage or safe storage.

## Cooldown Rules

For AI suggestions:

- Use `refreshAllowed`.
- Show `nextRefreshAllowedAt`.
- Disable refresh while cooldown is active.
- Do not poll or refetch repeatedly to bypass cooldown.

## Safe Refresh Rules

Pull-to-refresh is allowed for read-only screens:

- profile
- stock list/detail/history
- AI suggestions GET
- watchlist
- portfolio
- transactions
- admin monitoring lists

Do not pull-to-refresh write actions.

- Refresh icons on stock data screens should use the refresh cooldown helper where applicable.
- Pull-to-refresh and timer refresh must not spam backend read endpoints or imply real-time market data.
- History-only loading should not trigger whole-page refresh states or scroll jumps.

## Form Validation UX

- Validate obvious client-side fields before request.
- Show backend validation as authoritative.
- Keep messages specific, such as "Quantity must be a whole positive number."
- Preserve entered values after validation errors except passwords if security requires clearing them.
- After the first invalid account-form submit, live-validate edited fields until all fields are valid and make disabled submit buttons visibly disabled.
- Map backend-known registration conflicts to exact fields, such as duplicated email or username, instead of relying only on a global banner.
- Auth forms should be keyboard-aware and must not hide focused inputs under iOS safe areas, Dynamic Island, or fixed
  headers.

## HCI And Accessibility

- Maintain readable contrast.
- Touch targets should be around 44x44 or larger.
- Use clear button labels.
- Modals must not trap users.
- Web admin should support keyboard-friendly focus order.
- Loading states must explain what is happening.
- Empty states should tell the user what to do next.

## Onboarding Recovery Guardrails

- Onboarding submit sends only `{ answers: [{ questionId, optionId }] }` and never sends frontend-calculated risk/profile
  data.
- If onboarding submit times out or reports an account-state mismatch, provide a refresh-account-state action; if
  `GET /api/auth/me` shows onboarding is complete, route out of the quiz.
- Post-onboarding AI suggestion generation is backend background work and must not block the user-facing onboarding
  response.

## Route Guardrails

- Beginner routes must not show admin-only actions.
- Admin routes must reject normal users.
- Admin API calls require Basic Auth plus `X-Admin-Token`.
- Frontend must not send `userId` for current-user normal user actions.
- Route params are allowed only where backend expects them, such as stock symbol, transaction id, user id for admin, batch id, and job id.
- For tab/context flows, pass explicit return params instead of relying on `router.canGoBack()` alone.
- Stock detail routes should receive `returnTo=stocks`, `returnTo=watchlist`, `returnTo=search-context`, or
  `returnTo=search-tab` depending on origin.
- Contextual search uses hidden route `/stocks/search-context`; the bottom `Search` tab remains a stable tab route.
- Hidden tab routes should use `href: null`.
- Expected navigation:
  - Stocks -> Detail -> Back returns Stocks.
  - Watchlist -> Detail -> Back returns Watchlist.
  - Search tab -> Detail -> Back returns Search tab.
  - Stocks/Watchlist/Detail -> Search Context -> Back returns the correct origin.
  - Detail -> Practice Trade -> Back returns the same detail context.

## Placeholder Trade Guardrails

- Phase 3B practice-trade actions may navigate to the placeholder route only.
- Placeholder practice-trade UI must not call buy/sell APIs, must not submit quantities, and must not pretend real or
  paper execution is implemented.
- Real US010 frontend buy/sell tickets require a separately scoped implementation phase with confirmation and backend
  payload checks.
