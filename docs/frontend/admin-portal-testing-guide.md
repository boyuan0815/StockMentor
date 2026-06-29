# StockMentor Admin Portal Testing Guide

## 1. Purpose

This guide covers manual testing for the StockMentor admin portal implemented inside the existing Expo Router frontend. It verifies:

- admin access and beginner-user rejection
- session-only `X-Admin-Token` entry and recovery
- users list, detail, and status management
- AI suggestion monitoring, batch detail, failure list, usage summary, refresh jobs, and manual scheduled refresh
- stock maintenance/backfill form behavior
- responsive web/tablet layout and phone-sized fallback
- local database evidence for admin actions

The admin portal is a frontend for verified Spring Boot admin endpoints only. It must not call OpenAI or Twelve Data directly from the browser.

## 2. How To Open The Admin Portal

### Start The Backend

From PowerShell:

```powershell
cd C:\StockMentor\backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

If the local Maven wrapper launcher fails, use the wrapper-downloaded Maven binary under `%USERPROFILE%\.m2\wrapper\dists\...` and run the same `spring-boot:run` goal from `C:\StockMentor\backend`.

The backend must expose:

- Basic Auth endpoints on `http://localhost:8080`
- admin endpoints under `/api/admin/**`
- CORS allowed origin for Expo Web, usually `http://localhost:8081`

The CORS origin is configured by `stockmentor.cors.allowed-origins`. Do not put secrets in tracked config.

### Start Expo Web

From another PowerShell window:

```powershell
cd C:\StockMentor\frontend
$env:EXPO_PUBLIC_API_BASE_URL="http://localhost:8080"
npm.cmd run web
```

Open the printed Expo Web URL. The default local URL is usually:

```text
http://localhost:8081/admin
```

If Expo uses a different port, keep the same path and use the printed host:

```text
http://localhost:<expo-port>/admin
```

### Sign In And Enter Admin Token

1. Go to `/admin`.
2. If not signed in, the app redirects to `/login`.
3. Sign in with a local admin Basic Auth account.
4. The admin shell asks for `X-Admin-Token`.
5. Paste the local admin token for this session.
6. The dashboard should load with the sidebar navigation.

The token is stored only in React state. Refreshing the page or clearing the admin token should require token entry again.

### Beginner User Expected Behavior

If a normal beginner investor signs in and opens `/admin`, the UI should show an access-denied screen and provide logout/session controls. No admin data tables or maintenance actions should be visible.

### CORS Troubleshooting

If the browser console or UI reports a network/CORS failure:

- confirm the backend is running on the URL in `EXPO_PUBLIC_API_BASE_URL`
- confirm the Expo Web origin is listed in `stockmentor.cors.allowed-origins`
- confirm unauthenticated `OPTIONS /**` preflight is still permitted
- do not change backend CORS during frontend testing unless that change is explicitly approved

## 3. Required Test Accounts / Tokens

Use local-dev values only. Do not write real passwords or tokens into this document or screenshots.

Required:

- admin Basic Auth account: `<admin-email-or-username>` / `<admin-password>`
- beginner Basic Auth account: `<beginner-email-or-username>` / `<beginner-password>`
- admin token: configured as `stockmentor.admin.token` in local backend configuration

The tracked example config shows only the placeholder key:

```yaml
stockmentor:
  admin:
    token: your-admin-token
```

Confirm admin and beginner roles with SQL:

```sql
SELECT user_id, email, username, role, status, is_deleted
FROM app_user
WHERE email IN ('<admin-email>', '<beginner-email>')
   OR username IN ('<admin-username>', '<beginner-username>');
```

Expected:

- admin row has `role = 'ADMIN'`, `status = 'ACTIVE'`, `is_deleted = 0`
- beginner row has `role = 'BEGINNER_INVESTOR'`, `status = 'ACTIVE'`, `is_deleted = 0`

## 4. Full Manual Test Checklist

### Access / Auth

| Test | Precondition | Steps | Expected UI | Expected backend/API | Cleanup |
| --- | --- | --- | --- | --- | --- |
| Unauthenticated admin route | No active app session | Open `/admin` | Redirects to login | No admin API call succeeds | None |
| Beginner user tries admin route | Beginner account exists | Log in as beginner, open `/admin` | Access-denied screen with logout | Admin endpoints are not called by the shell | Log out |
| Admin missing token | Admin signed in, no token entered | Open `/admin` | Token prompt appears | No admin data loads without token | None |
| Admin wrong token | Admin signed in | Enter a wrong token | Admin request fails and token prompt returns | Admin endpoint returns `401` | Enter correct token |
| Admin correct token | Admin signed in | Enter local token | Dashboard loads | Admin GET requests include Basic Auth plus `X-Admin-Token` | None |
| Clear admin token | Dashboard loaded | Click `Clear admin token` | Token prompt returns | Basic Auth session remains | Re-enter token |
| Logout | Any admin screen | Click `Log out` | App returns to unauthenticated flow | Session credentials and admin token are cleared | None |
| Refresh page behavior | Admin dashboard loaded | Browser refresh | Token must be entered again if React state is reset | No token is persisted | Re-enter token |
| Phone fallback | Browser devtools width below 768px | Open `/admin` | `Admin console is best viewed on tablet or web`; token/logout controls only | No full admin tables or maintenance actions visible | Restore width |

### Users

| Test | Precondition | Steps | Expected UI | Expected backend/API | Database evidence | Cleanup |
| --- | --- | --- | --- | --- | --- | --- |
| Users list load | Admin signed in with token | Open `/admin/users` | Table loads users | `GET /api/admin/users` | `SELECT COUNT(*) FROM app_user WHERE is_deleted = 0;` | None |
| Users list search | Known user exists | Search email or username | Matching row appears | `search` query filters backend list | Confirm row in `app_user` | Clear search |
| Role filter | Admin and beginner rows exist | Select role chips | Rows match role | `role` query filters list | `SELECT role, COUNT(*) FROM app_user GROUP BY role;` | Select all |
| Status filter | Active/inactive rows exist | Select status chips | Rows match status | `status` query filters list | `SELECT status, COUNT(*) FROM app_user GROUP BY status;` | Select all |
| Pagination | More than one page exists | Click Next/Previous | Page count changes | `page` and `size` query params change | None | Return to page 1 |
| User detail load | User row exists | Click a user row | Account/profile/behavior/paper sections render | `GET /api/admin/users/{userId}` | Select same `user_id` in related tables | None |
| Missing summaries | User lacks profile/behavior/paper account | Open detail | Sections say `Not available yet` | Detail DTO returns null sections | Confirm no related rows | None |
| Disable user | Target user is active and not protected | Click `Disable user`, confirm | Status changes to inactive or conflict appears | `PATCH /api/admin/users/{userId}/status` body `{ "status": "INACTIVE" }` | `SELECT status FROM app_user WHERE user_id = <user-id>;` | Re-enable user |
| Re-enable user | Target user is inactive | Click `Re-enable user`, confirm | Status changes to active | `PATCH` body `{ "status": "ACTIVE" }` | `SELECT status FROM app_user WHERE user_id = <user-id>;` | None |
| Duplicate status action prevention | Status modal open | Double-click confirm | Only one pending action is allowed | One effective PATCH should be accepted | Check final status only | None |
| Conflict/error state | Try disabling self or last active admin | Confirm disable | Conflict/error banner appears | Backend returns `409` or `403` | Admin row remains active | None |

### AI Monitoring

| Test | Precondition | Steps | Expected UI | Expected backend/API | Database evidence | Cleanup |
| --- | --- | --- | --- | --- | --- | --- |
| Usage summary load | Admin signed in with token | Open `/admin/ai-suggestions`, Usage tab | Metrics and grouped counts render | `GET /api/admin/ai-suggestions/usage-summary` | Count rows in `stock_ai_suggestion_batch` | None |
| Batches list load | Batches exist or empty DB | Open Batches tab | Table or empty state | `GET /api/admin/ai-suggestions/batches` | `SELECT COUNT(*) FROM stock_ai_suggestion_batch;` | None |
| Batch detail load | Batch exists | Click a batch | Batch metadata and items render | `GET /api/admin/ai-suggestions/batches/{batchId}` | Select batch/items by id | None |
| Failures list load | Failed batches may exist | Open Failures tab | Failure table or empty state | `GET /api/admin/ai-suggestions/failures` | `WHERE status = 'FAILED'` | None |
| Refresh jobs list load | Jobs may exist | Open Refresh jobs tab | Jobs table or empty state | `GET /api/admin/ai-suggestions/refresh-jobs` | Count `ai_suggestion_refresh_job` | None |
| Refresh job detail load | Job exists | Click job row | Job counters and message render | `GET /api/admin/ai-suggestions/refresh-jobs/{jobId}` | Select job by id | None |
| Manual scheduled refresh confirmation | Admin signed in with token | Click `Run scheduled refresh` | Confirmation modal appears | No POST until confirmed | None | Cancel if not testing |
| Manual scheduled refresh success | Backend configured for AI refresh | Confirm modal | Success message and jobs tab | `POST /api/admin/ai-suggestions/scheduled-refresh/run` | New/updated row in `ai_suggestion_refresh_job` | None |
| Duplicate manual refresh prevention | Refresh pending | Try repeated confirms | Button stays pending/disabled | No accidental repeated submits from UI | Check no unexpected extra jobs | None |
| Backend unavailable/error recovery | Stop backend | Refresh tab | Recoverable error banner | Request fails gracefully | None | Restart backend and refresh |

### Stock Maintenance

| Test | Precondition | Steps | Expected UI | Expected backend/API | Database evidence | Cleanup |
| --- | --- | --- | --- | --- | --- | --- |
| Invalid form states | Admin signed in with token | Submit missing required dates | Validation banner appears | No POST | No data change | Fix form |
| `INTRADAY_DATE` success | Provider/backend configured | Select date and symbols, confirm | Result summary with saved/skipped rows | `POST /api/admin/stocks/backfill` body includes `type`, `date`, optional `symbols` | Count `stock_price_history_1min` rows | None |
| `DAILY_RANGE` success | Provider/backend configured | Enter start/end dates, confirm | Saved/skipped summary | Body includes `startDate` and `endDate` | Count `stock_price_daily` rows | None |
| `DAILY_MISSING` success | Provider/backend configured | Leave dates empty or enter both | Result summary | Body includes optional date range | Count missing/daily rows | None |
| `CLEANUP_1MIN` success | Old intraday rows exist with daily backup | Select cleanup, confirm | Deleted-row summary | Body is `{ "type": "CLEANUP_1MIN" }` | Intraday count decreases only where daily backup exists | None |
| Partial success/failure result | Some symbols unavailable | Run subset | Messages visible in result summary | Backend returns messages/counters | Compare per-symbol counts | None |
| Repeated-submit prevention | Backfill pending | Double-click confirm | Pending state blocks repeats | One effective POST from UI | Check no unexpected duplicate rows | None |
| Unsupported/empty symbols | Subset mode selected | Deselect all and submit | Validation says choose a symbol | No POST | No data change | Select symbols |

## 5. SQL Verification Guide

These SQL examples are for local development only. Do not run destructive SQL on production or real user data. Verify table and column names against the current JPA entities or the actual schema first:

```sql
DESCRIBE app_user;
DESCRIBE stock_ai_suggestion_batch;
DESCRIBE stock_ai_suggestion_item;
DESCRIBE ai_suggestion_refresh_job;
DESCRIBE stock_price_daily;
DESCRIBE stock_price_history_1min;
```

Use `SELECT` checks first. If you intentionally create or remove local test data, wrap it in a transaction and prefer rollback until you are sure:

```sql
START TRANSACTION;
-- local test setup here
ROLLBACK;
```

### User/Admin Checks

Find admin user:

```sql
SELECT user_id, email, username, role, status, is_deleted, onboarding_completed, created_at, last_login_at
FROM app_user
WHERE role = 'ADMIN'
ORDER BY created_at DESC;
```

Find beginner user:

```sql
SELECT user_id, email, username, role, status, is_deleted, onboarding_completed, created_at, last_login_at
FROM app_user
WHERE role = 'BEGINNER_INVESTOR'
ORDER BY created_at DESC
LIMIT 20;
```

Check disabled/re-enabled status:

```sql
SELECT user_id, email, username, role, status, is_deleted, updated_at
FROM app_user
WHERE user_id = <user-id>;
```

Check user detail related data:

```sql
SELECT profile_id, user_id, profile_version, profile_source, risk_tolerance, investment_goal, created_at, updated_at
FROM user_investment_profile
WHERE user_id = <user-id>
ORDER BY profile_version DESC, updated_at DESC;

SELECT behavior_profile_id, user_id, behavior_confidence, behavior_style, behavior_summary_text, updated_at
FROM user_behavior_profile
WHERE user_id = <user-id>
ORDER BY updated_at DESC;

SELECT account_id, user_id, cash_balance, status, current_session_number, last_reset_at, created_at, updated_at
FROM paper_trading_account
WHERE user_id = <user-id>;

SELECT COUNT(*) AS position_count
FROM paper_position
WHERE user_id = <user-id>;

SELECT COUNT(*) AS transaction_count
FROM paper_trade_transaction
WHERE user_id = <user-id>;
```

### AI Suggestion Checks

Check batches:

```sql
SELECT suggestion_batch_id, user_id, status, trigger_reason, model, prompt_version,
       analysis_timeframe, total_tokens, created_at, expires_at
FROM stock_ai_suggestion_batch
ORDER BY created_at DESC
LIMIT 20;
```

Check batch items:

```sql
SELECT suggestion_item_id, suggestion_batch_id, user_id, symbol, rank_no,
       match_score, risk_level, suggestion_label, status, analysis_snapshot_id, created_at
FROM stock_ai_suggestion_item
WHERE suggestion_batch_id = <batch-id>
ORDER BY rank_no ASC;
```

Check failures:

```sql
SELECT suggestion_batch_id, user_id, trigger_reason, error_message, created_at
FROM stock_ai_suggestion_batch
WHERE status = 'FAILED'
ORDER BY created_at DESC
LIMIT 20;
```

Check refresh jobs:

```sql
SELECT job_id, triggered_by, triggered_by_user_id, status, started_at, finished_at,
       processed_users, skipped_users, success_count, reused_count, fallback_count, failed_count, message
FROM ai_suggestion_refresh_job
ORDER BY started_at DESC
LIMIT 20;
```

Verify manual scheduled refresh created or updated a job:

```sql
SELECT job_id, triggered_by, status, started_at, finished_at, processed_users, success_count, failed_count
FROM ai_suggestion_refresh_job
WHERE triggered_by = 'ADMIN_MANUAL'
ORDER BY started_at DESC
LIMIT 5;
```

Check dismissed/watchlisted suggestion item status:

```sql
SELECT suggestion_item_id, user_id, symbol, rank_no, status, dismissed_at, updated_at
FROM stock_ai_suggestion_item
WHERE user_id = <user-id>
ORDER BY updated_at DESC
LIMIT 20;

SELECT watchlist_id, user_id, symbol, display_order, source, created_at, updated_at
FROM user_watchlist
WHERE user_id = <user-id>
ORDER BY display_order ASC, created_at ASC;
```

### Stock Maintenance Checks

Daily rows before and after `DAILY_RANGE`:

```sql
SELECT symbol, COUNT(*) AS daily_rows, MIN(trading_date) AS first_date, MAX(trading_date) AS last_date
FROM stock_price_daily
WHERE symbol = '<SYMBOL>'
  AND trading_date BETWEEN '<start-date>' AND '<end-date>'
GROUP BY symbol;
```

Find missing daily dates approximately by comparing stored rows in the date range:

```sql
SELECT trading_date, COUNT(*) AS row_count
FROM stock_price_daily
WHERE symbol = '<SYMBOL>'
  AND trading_date BETWEEN '<start-date>' AND '<end-date>'
GROUP BY trading_date
ORDER BY trading_date;
```

Intraday 1-minute rows before and after `INTRADAY_DATE`:

```sql
SELECT symbol, trading_date, COUNT(*) AS minute_rows,
       MIN(timestamp) AS first_timestamp, MAX(timestamp) AS last_timestamp
FROM stock_price_history_1min
WHERE symbol = '<SYMBOL>'
  AND trading_date = '<date>'
GROUP BY symbol, trading_date;
```

Intraday cleanup check:

```sql
SELECT h.symbol, h.trading_date, COUNT(*) AS minute_rows
FROM stock_price_history_1min h
WHERE h.trading_date < '<cutoff-date>'
GROUP BY h.symbol, h.trading_date
ORDER BY h.trading_date DESC, h.symbol ASC
LIMIT 50;
```

Verify cleanup only targets days with daily backup:

```sql
SELECT h.symbol, h.trading_date, COUNT(*) AS minute_rows,
       CASE WHEN d.daily_id IS NULL THEN 'NO_DAILY_BACKUP' ELSE 'HAS_DAILY_BACKUP' END AS backup_status
FROM stock_price_history_1min h
LEFT JOIN stock_price_daily d
  ON d.symbol = h.symbol
 AND d.trading_date = h.trading_date
WHERE h.trading_date < '<cutoff-date>'
GROUP BY h.symbol, h.trading_date, backup_status
ORDER BY h.trading_date DESC, h.symbol ASC
LIMIT 50;
```

Optional local-only setup to simulate missing daily data. Roll back unless you intentionally want to keep the local change:

```sql
START TRANSACTION;

SELECT *
FROM stock_price_daily
WHERE symbol = '<SYMBOL>'
  AND trading_date = '<date>';

DELETE FROM stock_price_daily
WHERE symbol = '<SYMBOL>'
  AND trading_date = '<date>';

-- Run DAILY_MISSING from the admin portal in another session, then inspect.
SELECT *
FROM stock_price_daily
WHERE symbol = '<SYMBOL>'
  AND trading_date = '<date>';

ROLLBACK;
```

## 6. Expected Outputs

- Successful users list: table rows with user id, email, username, role, status, last login, and created date.
- Empty users list: `No users found` empty state.
- Access denied: `Admin console unavailable` with logout.
- Missing token: `Enter admin token` prompt.
- Wrong token: error banner explains token was missing or not accepted, and token prompt returns.
- Successful token: admin dashboard loads.
- Status changed: user detail status pill changes to Active or Inactive.
- Conflict: error banner explains backend conflict, such as self-disable or last-admin protection.
- Usage summary loaded: batch and token metrics render.
- Batch detail loaded: batch metadata and item table render, without prompts or input hashes.
- Manual refresh job started/completed: jobs table contains a new or updated job with counters.
- Backfill result: saved, skipped, and deleted row metrics plus backend messages render.
- Backend unavailable: recoverable error banner asks tester to check backend URL/CORS.

## 7. Troubleshooting

- Backend not running: start Spring Boot and refresh the active tab.
- Frontend cannot reach backend: check `EXPO_PUBLIC_API_BASE_URL`.
- CORS blocked on Expo Web: add the Expo Web origin to local backend config only after approval.
- Wrong Basic Auth credentials: login fails before the admin shell.
- Wrong `X-Admin-Token`: admin request returns `401`; the UI clears the token and asks again.
- Token cleared after `401`: expected behavior for admin-token recovery.
- Empty database: admin tables show empty states; seed or create local users/batches if needed.
- No AI suggestion batches yet: complete onboarding or use backend-supported refresh flows to create safe local data.
- Twelve Data/backfill provider issues: stock maintenance may return partial success or provider errors from backend messages.
- Expo Web route not found: confirm the URL path is `/admin`, not a separate admin project route.
- MySQL connection checks: confirm local credentials, database name, and that the running backend points to the same schema you inspect.

## 8. Demo Script

1. Open `http://localhost:8081/admin`.
2. Log in as the local admin user.
3. Enter the local admin token.
4. View dashboard metrics and recent refresh jobs.
5. Open Users, search for a known user, and clear filters.
6. Open that user detail and show profile/behavior/paper summary sections.
7. Open AI monitoring and show Usage.
8. Open Batches and then a batch detail.
9. Open Stock maintenance, choose `DAILY_MISSING` with a safe date range or backend defaults, and review the confirmation.
10. Confirm only in a local test environment, then show saved/skipped/deleted result counters.
11. Narrow the browser below 768px and show the phone fallback message.
