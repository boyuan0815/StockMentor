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
- If backend later exposes delayed display metadata, show displayed market time and last backend update time separately.
- Practice trade confirmation must say: "Practice trade uses StockMentor's delayed stored price, not a live market quote."

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

## Form Validation UX

- Validate obvious client-side fields before request.
- Show backend validation as authoritative.
- Keep messages specific, such as "Quantity must be a whole positive number."
- Preserve entered values after validation errors except passwords if security requires clearing them.

## HCI And Accessibility

- Maintain readable contrast.
- Touch targets should be around 44x44 or larger.
- Use clear button labels.
- Modals must not trap users.
- Web admin should support keyboard-friendly focus order.
- Loading states must explain what is happening.
- Empty states should tell the user what to do next.

## Route Guardrails

- Beginner routes must not show admin-only actions.
- Admin routes must reject normal users.
- Admin API calls require Basic Auth plus `X-Admin-Token`.
- Frontend must not send `userId` for current-user normal user actions.
- Route params are allowed only where backend expects them, such as stock symbol, transaction id, user id for admin, batch id, and job id.
