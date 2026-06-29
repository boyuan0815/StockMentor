# StockMentor Frontend Phase Prompts

This file is the current future-work guide for StockMentor frontend sessions. It is written for fresh Codex chats with
no prior memory.

Future sessions must inspect the live repository before editing. The current implementation has moved ahead of the
original phase order: stock learning, interactive charts, watchlist edit, paper trading, portfolio history pagination,
and the Stocks portfolio card are already implemented in the working tree after backend commit
`feat(backend): complete market display and paper trading contracts`.

Do not create branches or worktrees unless the user explicitly asks. Do not stage or commit unless the user explicitly
asks. Do not modify `.agents/`, `skills-lock.json`, protected package/lock files, or `frontend/.gitignore` unless the
user explicitly scopes that work.

## Fresh Worktree Rules

Every future Codex session should start by reading:

- `AGENTS.md`
- `docs/frontend/frontend-phase-prompts.md`
- `docs/frontend/frontend-implementation-master-plan.md`
- `docs/frontend/frontend-implementation-roadmap.md`
- `docs/frontend/backend-api-screen-map.md`
- `docs/frontend/api-integration-guide.md`
- `docs/frontend/mobile-user-flow.md`
- `docs/frontend/design-system.md`
- `docs/frontend/interaction-guardrails.md`
- `docs/frontend/frontend-testing-checklist.md`
- `frontend/package.json`
- the current implementation files touched by the requested task

Before implementing API calls, verify the backend endpoints and DTO records in the current checkout. Treat code as the
source of truth if docs drift. Report manual testing gaps honestly; do not claim device/simulator verification unless it
was actually run.

## Current Implementation Snapshot

Based on the current codebase after the backend contract commit and the uncommitted frontend pass:

- Stack: Expo SDK 54, React Native 0.81, Expo Router, TypeScript.
- Auth/onboarding/profile: implemented with Basic Auth, `/api/auth/me` bootstrap, onboarding gate, onboarding quiz,
  profile/result, and logout.
- Visible beginner tabs: `Watchlist`, `Stocks`, `Suggestions`, `Portfolio`, `Profile`, `Search`.
- Hidden routes: stock detail, stock search context, stock explanation compatibility route, paper buy/sell tickets,
  paper transactions, transaction detail, and nested watchlist edit.
- Watchlist: implemented as the first tab with fixed `Watchlists` header, market notice, compact table, add/remove,
  minute-boundary refresh, and lightweight `Add Symbol` / `Edit List` actions.
- Watchlist edit: implemented as a nested stacked route under `/watchlist/edit`, using `react-native-draggable-flatlist`
  for reorder, checkboxes for batch remove, `Top`, local search, and backend reorder/batch-remove endpoints.
- Stocks: implemented with compact stock table, independent portfolio summary card above rows, placeholder-only `AI
  Picks`, `Watchlist` action, and reset-card bottom sheet.
- Stock detail: implemented with delayed quote panel, interactive `react-native-wagmi-charts` line/candle chart, visible
  chart timeframes `1D`, `5D`, `1M`, `3M`, `YTD`, `1Y`, and hidden backend-compatible `7D`.
- Chart rules: `1D` and `5D` are line-only; daily timeframes can show candle mode only when backend
  `candlestickSupported=true`; the frontend uses backend points exactly and never fakes OHLC.
- AI explanation: implemented as on-demand drawer for `1D`, `5D`, `1M`, and `3M`; `YTD` and `1Y` show unsupported copy.
- AI suggestions: beginner Suggestions tab is implemented with cached suggestion rendering, explicit manual refresh
  with cooldown handling, dismiss, add-to-watchlist actions, fallback/unavailable states, and read-only remaining-stock
  context.
- Portfolio/paper trading: implemented with `Assets`/`History`, backend P/L fields, stock-scoped buy/sell tickets,
  reset-card bottom sheet, transaction detail, paged history with `size=20`, side filter, and exact-symbol backend
  filter.
- Admin console: route shell/placeholders exist, but tablet/web admin screens are still pending.
- Major frontend dependencies now present: `react-native-wagmi-charts`, `react-native-svg`,
  `react-native-draggable-flatlist`, `react-native-reanimated`, `react-native-gesture-handler`, and
  `react-native-worklets`.
- Intentionally deferred: admin web/tablet console, MY/HK real market data, real broker trading, live streaming market
  data, margin/options/news/community/admin extras outside the documented admin scope.

## Phase Status Matrix

| Phase | Original Goal | Current Status | Evidence From Code | Remaining Work | Recommended Next Action |
|---|---|---|---|---|---|
| Foundation | Route shell, API core, providers, theme, placeholders | Completed | `frontend/app`, providers, API client, theme/util files | Maintain only | Keep as baseline; avoid rewrites |
| Auth / Onboarding / Profile | Login/register/bootstrap/onboarding/profile | Completed | auth routes, onboarding screens, profile screen, `auth-routing.ts` | Manual regression only | Include in final regression pass |
| Stock learning | Watchlist, Stocks, Search, detail, delayed data, AI explanation | Completed with polish still possible | `dashboard-screen.tsx`, stock list/detail/search screens, stock API/types | Manual HCI polish as needed | Treat as implemented; do not schedule as full phase |
| Interactive charts | Real line/candle charts and `5D` support | Completed with performance/HCI sensitivity | `stock-history-view.tsx`, chart deps in `package.json` | Device performance verification | Keep as regression-polish item |
| Watchlist edit | Reorder and batch remove support | Completed with HCI sensitivity | nested `/watchlist/edit`, `watchlist-edit-screen.tsx`, watchlist API | Device drag/drop verification | Keep as regression-polish item |
| Paper trading / Portfolio | Portfolio, buy/sell, reset, history, transactions | Completed with manual polish possible | paper-trading screens/API/types | Device regression and edge cases | Treat as implemented; do not schedule as full phase |
| Stock list portfolio card | Net Assets tier card and reset entry | Completed with visual polish possible | `stock-list-screen.tsx`, reset card sheet | Screenshot-driven HCI polish | Keep as optional correction pass only |
| AI Suggestions UI | Suggestions list, refresh, cooldown, dismiss/watchlist actions | Completed | `frontend/screens/suggestions/ai-suggestions-screen.tsx`, `frontend/api/ai-suggestions.ts`, `frontend/types/ai-suggestions.ts` | Manual device/API-state verification | Keep as regression-polish item |
| Admin console | Web/tablet admin dashboard, users, AI monitoring, stock maintenance | Not started beyond shell/placeholders | admin docs/routes only | Implement admin web/tablet screens | Do after AI Suggestions or as separate phase |
| Final integration | Expo/device regression, accessibility, demo polish | Not started as a final pass | checklist docs only | Full end-to-end verification | Run after AI Suggestions/Admin |

## Reorganized Future Phase Prompts

The old phase order is no longer accurate. Use the prompts below for future work.

### Phase A Prompt: Documentation Sync And Commit Prep

```text
Use superpowers: writing-plans, executing-plans, requesting-code-review.
This is documentation-only, so test-driven-development should normally not be used.
@ponytail full
After documentation diff, run @ponytail-review.
Work in the current StockMentor repo. Do not create a branch/worktree. Do not stage or commit unless I explicitly ask.

Goal:
- Inspect the current uncommitted diff.
- Verify docs match the current code.
- Update only stale docs.
- Suggest a commit message for the whole current diff.

Read first:
- AGENTS.md
- docs/frontend/frontend-phase-prompts.md
- docs/frontend/backend-api-screen-map.md
- docs/frontend/mobile-user-flow.md
- docs/frontend/design-system.md
- docs/frontend/frontend-testing-checklist.md
- frontend/package.json
- current changed files from `git status --short`

Rules:
- Do not modify frontend runtime files, backend runtime files, package files, lock files, `.agents/`, `skills-lock.json`,
  or `frontend/.gitignore`.
- If docs and code conflict, treat current code as source of truth.
- Do not claim unverified manual testing.

Verification:
- `git diff --check`
- `git diff --cached --name-only`
- `git status --short .agents skills-lock.json frontend/.gitignore package.json package-lock.json`
- Run frontend lint/typecheck only if package state and dependencies are available.

Final report:
- files changed
- docs/code conflicts found
- verification results
- protected-file status
- suggested commit message
- confirmation nothing staged/committed
```

### Completed Phase B Reference: AI Stock Suggestions Completion

```text
Use superpowers: writing-plans, executing-plans, requesting-code-review.
Use test-driven-development only for pure helper/logic changes where an existing test pattern exists.
@ponytail full
After implementation diff, run @ponytail-review.

Use `building-native-ui`, `native-data-fetching`, and `frontend-design`.

Task:
Implement the full beginner AI Suggestions UI using the existing backend US006 endpoints. This phase is implemented;
keep this prompt only as a regression/reference handoff if the Suggestions UI needs targeted follow-up work.

Start by inspecting:
- AGENTS.md
- docs/frontend/frontend-phase-prompts.md
- docs/frontend/backend-api-screen-map.md
- docs/frontend/api-integration-guide.md
- docs/frontend/mobile-user-flow.md
- docs/frontend/design-system.md
- frontend/app/(user)/suggestions/index.tsx
- frontend/api, frontend/types, frontend/screens, frontend/components
- backend `StockAiSuggestionController` and AI suggestion DTO records

Verified endpoints:
- `GET /api/stocks/ai-suggestions`
- `POST /api/stocks/ai-suggestions/refresh`
- `PATCH /api/stocks/ai-suggestions/items/{itemId}/dismiss`
- `PATCH /api/stocks/ai-suggestions/items/{itemId}/watchlist`

Scope:
- Replace placeholder Suggestions tab with a real list.
- Render cached/read-only suggestions from GET without triggering OpenAI.
- Add refresh action with backend cooldown handling.
- Add dismiss and add-to-watchlist actions with duplicate-submit protection.
- Use backend delayed display fields on suggestion items.
- Show fallback/unavailable state and educational no-advice copy.
- Keep behavior beginner-friendly and compact.

Non-scope:
- Do not call OpenAI or Twelve Data from the frontend.
- Do not implement admin AI monitoring.
- Do not change backend behavior unless a verified contract bug blocks the UI and I approve it.
- Do not add dependencies unless approved.

Verification:
- frontend lint/typecheck/expo config
- manual flow: no cached suggestions, cached suggestions, refresh allowed, refresh cooldown, dismiss, add to watchlist,
  backend unavailable
- repo checks and protected-file checks

Final report:
- files changed
- endpoint/DTO assumptions
- manual testing status
- verification results
- confirmation nothing staged/committed
```
### Phase C Prompt: Admin Web/Tablet Console

```text
Use superpowers: writing-plans, executing-plans, requesting-code-review.
Use test-driven-development only for pure helper/logic changes where an existing test pattern exists.
@ponytail full
After implementation diff, run @ponytail-review.

Use `building-native-ui`, `native-data-fetching`, `frontend-design`, `web-design-guidelines`, and `accessibility`.

Task:
Implement the StockMentor admin web/tablet console inside the existing Expo app.

Start by inspecting:
- AGENTS.md
- docs/frontend/admin-web-flow.md
- docs/frontend/backend-api-screen-map.md
- docs/frontend/api-integration-guide.md
- docs/frontend/design-system.md
- backend admin controllers/DTOs
- existing admin route shell

Scope:
- Admin login/token entry and token re-entry states.
- Admin dashboard.
- Users list and user detail.
- Disable/re-enable user with confirmation.
- AI suggestion monitoring pages: batches, batch detail, failures, usage summary, refresh jobs, job detail.
- Manual scheduled refresh action with confirmation.
- Stock maintenance/backfill form with confirmation.
- Phone-sized fallback: "Admin console is best viewed on tablet or web" plus logout.

Rules:
- Admin API calls require Basic Auth plus `X-Admin-Token`.
- Admin token is session-only and must not be stored in `EXPO_PUBLIC_`, AsyncStorage, logs, or diagnostics.
- Monitoring pages are read-only unless the endpoint is an explicit POST/PATCH action.
- Do not expose prompts, secrets, auth headers, or admin token values.
- Do not add a separate web project.

Verification:
- Expo Web admin smoke tests if practical.
- normal user rejected from admin shell
- missing/wrong admin token recovery
- users list/detail/status update
- AI monitoring pages
- stock maintenance form validation
- lint/typecheck/expo config and repo checks
```

### Phase D Prompt: Final Integration, Demo, Accessibility, And Regression Polish

```text
Use superpowers: writing-plans, executing-plans, requesting-code-review.
Use test-driven-development only for pure helper/logic changes where an existing test pattern exists.
@ponytail full
After implementation diff, run @ponytail-review.

Use `accessibility`, `web-design-guidelines`, `webapp-testing`, and `frontend-design`.

Task:
Run final StockMentor frontend integration and demo polish after AI Suggestions and Admin Console are implemented.

Scope:
- Execute `docs/frontend/frontend-testing-checklist.md`.
- Verify Expo mobile and Expo Web where available.
- Review auth/onboarding, Watchlist, Stocks, chart, AI explanation, Suggestions, Portfolio, paper trade tickets,
  History, Profile, and Admin.
- Fix only verified regressions.
- Improve accessibility, contrast, touch targets, modal behavior, keyboard behavior, and loading/error states.
- Prepare demo notes if requested.

Rules:
- Do not add features.
- Do not call OpenAI/Twelve Data directly from frontend.
- Do not change backend unless a blocking integration bug is verified and approved.
- Report manual testing gaps honestly.

Verification:
- frontend lint/typecheck/expo config
- repo protected-file checks
- device/simulator/browser manual notes
```

### Phase E Prompt: Optional UI/HCI Correction Pass

```text
Use superpowers: writing-plans, executing-plans, requesting-code-review.
Use test-driven-development only for pure helper/logic changes where an existing test pattern exists.
@ponytail full
After implementation diff, run @ponytail-review.

Use ponytail mode and `frontend-design`.

Task:
Fix only the listed manual UI/HCI regressions from screenshots/device testing.

Rules:
- Start by inspecting the dirty diff and relevant current files.
- Patch minimally.
- Do not redesign broad flows unless the issue requires it.
- Do not add dependencies without approval.
- Do not stage or commit.
- Do not modify backend unless the prompt explicitly allows a narrowly scoped backend fix.
- Do not claim manual verification unless it was actually run.

Verification:
- frontend lint/typecheck/expo config
- `git diff --check`
- protected-file checks
- targeted manual checklist from the prompt
```

## Recommended Next Phase From Current State

Do **Admin Web/Tablet Console** next.

Why:

- The current frontend already has the beginner shell, stock list/detail/search, interactive charts, watchlist edit,
  portfolio, paper-trading tickets, reset card, transaction history, and beginner AI Suggestions UI.
- Backend admin monitoring, user management, and stock maintenance endpoints already exist.
- Admin console remains the largest documented frontend feature gap.

Prompt to paste into a fresh Codex chat:

```text
Use superpowers: writing-plans, executing-plans, requesting-code-review.
Use test-driven-development only for pure helper/logic changes where an existing test pattern exists.
@ponytail full
After implementation diff, run @ponytail-review.

Use `building-native-ui`, `native-data-fetching`, `frontend-design`, `web-design-guidelines`, and `accessibility`.

Work in the current StockMentor repo. Start in plan mode first. Do not stage or commit.

Task:
Implement the StockMentor admin web/tablet console inside the existing Expo app.

Read first:
- AGENTS.md
- docs/frontend/admin-web-flow.md
- docs/frontend/frontend-phase-prompts.md
- docs/frontend/backend-api-screen-map.md
- docs/frontend/api-integration-guide.md
- docs/frontend/design-system.md
- backend admin controllers/DTOs
- existing admin route shell

Rules:
- Do not modify `.agents/`, `skills-lock.json`, protected package/lock files, or `frontend/.gitignore`.
- Admin API calls require Basic Auth plus `X-Admin-Token`.
- Admin token is session-only and must not be stored in `EXPO_PUBLIC_`, AsyncStorage, logs, or diagnostics.
- Monitoring pages are read-only unless the endpoint is an explicit POST/PATCH action.
- Do not expose prompts, secrets, auth headers, or admin token values.
- Do not add a separate web project.
- Do not stage or commit.
- Report manual testing gaps honestly.

Implementation:
- Admin login/token entry and token re-entry states.
- Admin dashboard.
- Users list and user detail.
- Disable/re-enable user with confirmation.
- AI suggestion monitoring pages.
- Manual scheduled refresh action with confirmation.
- Stock maintenance/backfill form with confirmation.
- Phone-sized fallback for admin console.

Verification:
- npm.cmd run lint
- npx.cmd tsc --noEmit
- npx.cmd expo config --type public
- git diff --check
- git diff --cached --name-only
- protected-file checks
- admin manual checks for auth/token recovery, users, AI monitoring, scheduled refresh, and stock maintenance

Final report:
- files changed
- endpoint/DTO assumptions
- manual testing status
- verification results
- confirmation nothing staged/committed
```
