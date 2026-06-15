# Admin Web And Tablet Flow

The admin console is web/tablet-first using Expo Web / React Native Web inside the same Expo TypeScript codebase. It is an MVP admin console, not a full enterprise dashboard.

## Platform Rule

On tablet/web widths:

- Use side navigation.
- Use tables, filters, detail pages, and confirmation modals.
- Keep layouts dense but readable.

On phone-sized screens:

- Show "Admin console is best viewed on tablet or web."
- Allow logout.
- Optionally show minimal read-only status links.
- Do not show full admin tables, complex filters, or destructive maintenance actions.

## Admin Auth Flow

1. Admin enters Basic Auth credentials and admin token.
2. Frontend calls `POST /api/auth/login` or `GET /api/auth/me` using Basic Auth.
3. If `role !== ADMIN`, reject the admin route and show a permission message.
4. Store admin token in session-only state.
5. Admin API calls include `Authorization` and `X-Admin-Token`.
6. Missing or invalid admin token shows a clear re-entry prompt.

## Screen Responsibilities

### Admin Dashboard

- MVP: yes.
- Purpose: provide quick links and high-level status.
- Backend: AI usage summary, failures, refresh jobs.
- Layout: metric row plus recent activity tables.
- Empty: zero activity states.
- Guardrail: read-only by default.

### Manage Users List

- MVP: yes.
- Backend: `GET /api/admin/users`.
- Layout: table with search, role/status filters, pagination.
- Primary action: open user detail.
- Empty: no matching users.
- Error: missing token, forbidden, backend unavailable.
- Guardrail: no status changes without confirmation.

### User Detail

- MVP: yes.
- Backend: `GET /api/admin/users/{userId}`.
- Layout: account summary, latest profile summary, behavior summary, paper-trading summary.
- Primary action: disable/re-enable user.
- Empty: show missing profile/behavior/paper-trading sections as "Not available yet".
- Guardrail: do not call services that create accounts or recalculate behavior.

### Disable / Re-enable User

- MVP: yes.
- Backend: `PATCH /api/admin/users/{userId}/status`.
- Modal text should name the user and status.
- Error: show conflict for self-disable, deleted target, or last active admin.
- Guardrail: destructive/sensitive confirmation required and button disabled while pending.

### AI Suggestion Monitoring

- MVP: yes.
- Backend:
  - `GET /api/admin/ai-suggestions/batches`
  - `GET /api/admin/ai-suggestions/batches/{batchId}`
  - `GET /api/admin/ai-suggestions/failures`
  - `GET /api/admin/ai-suggestions/usage-summary`
- Layout: filters, tables, batch detail panels, token counts, status labels.
- Guardrail: monitoring read endpoints must stay read-only.

### AI Refresh Jobs

- MVP: yes.
- Backend:
  - `GET /api/admin/ai-suggestions/refresh-jobs`
  - `GET /api/admin/ai-suggestions/refresh-jobs/{jobId}`
  - `POST /api/admin/ai-suggestions/scheduled-refresh/run`
- Layout: jobs table, job detail, manual run button.
- Guardrail: manual run requires confirmation and no double submit.

### Stock Data Maintenance

- MVP: yes.
- Use case: US008 Maintain Stock Market Data.
- Backend: `POST /api/admin/stocks/backfill`.
- Layout: maintenance form with type, symbols, date or date range, result summary.
- Supported types from backend behavior:
  - `INTRADAY_DATE`
  - `DAILY_MISSING`
  - `CLEANUP_1MIN`
  - `DAILY_RANGE`
- Guardrail: confirmation required, especially cleanup.
- Purpose: improve stored market data quality for stock viewing, delayed display, charts, AI analysis inputs, and paper-trading consistency.
- This is not an immediate market control panel.

### Automatic Stock Retrieval Context

- Use case: US011 Automatically Retrieve Stock Market Data.
- Backend behavior: scheduler/system retrieval from Twelve Data.
- Frontend meaning: explain why stored market data changes over time; do not design a normal user screen for it.
- Admin may view or trigger maintenance only where backend endpoints exist.

### Admin AI Explanation Support

- MVP: no unless backend adds admin endpoints.
- Current backend only exposes user-facing `GET /api/stocks/{symbol}/ai-explanation`.
- Do not invent admin AI explanation screens or endpoints.

## Admin Copy Rules

- Use operational language: "Run scheduled refresh", "Disable user", "View batch".
- Destructive or sensitive copy must be explicit.
- Do not expose raw prompts, raw OpenAI responses, API keys, admin tokens, or secrets.
