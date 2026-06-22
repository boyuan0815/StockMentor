# StockMentor Frontend Phase Prompts

Copy one prompt into a fresh Codex chat for the matching implementation phase. Each prompt assumes no previous chat
memory. The future Codex session must inspect the current codebase before editing because frontend files may differ from
this plan.

Do not create branches or worktrees unless the user explicitly asks in that future chat. Suggested branch/worktree names
are guidance only.

Skills named in the prompts are already installed locally under `.agents/skills/`. Future Codex chats may read the
relevant `SKILL.md` and reference files as guidance, but must not install skills or edit, stage, or commit `.agents/` or
`skills-lock.json`.

## How Future Codex Chats Should Use These Prompts

When a future user pastes a phase prompt into Codex, Codex should start by inspecting the repo and reading the listed
docs/files. For named skills, read the relevant local `.agents/skills/<skill>/SKILL.md` files and directly relevant
reference files; `.agents/skills/` contains already installed local guidance files, not app source code, package
dependencies, or runtime files. Start with an implementation plan and wait for user approval before editing unless the
user explicitly says to implement immediately. Verify backend endpoints and DTOs before implementing API calls. Avoid
broad rewrites, follow existing project style, keep changes scoped to the phase, and never edit, stage, or commit
`.agents/` or `skills-lock.json`.

## Phase 1 Prompt: Frontend Foundation, Route Shell, And API Core

```text
Use `building-native-ui`, `native-data-fetching`, and `frontend-design`.

For the listed skills, read the relevant local `.agents/skills/<skill>/SKILL.md` files and any directly relevant
reference files. Use them as guidance only; do not modify, stage, or commit `.agents/` or `skills-lock.json`.

Task: Implement StockMentor frontend foundation, route shell, and API core only.

Suggested branch/worktree name: `frontend-foundation-shell`. Do not create a branch or worktree unless I
explicitly ask. Do not stage or commit.

Start in plan mode first. Read the required files, inspect the current implementation, summarize what you found, and
propose the implementation plan. Wait for my approval before editing files unless I explicitly tell you to implement
immediately.

Before editing, inspect the current codebase because frontend files may differ from the plan. Read:
- AGENTS.md
- docs/frontend/frontend-implementation-master-plan.md
- docs/frontend/frontend-phase-prompts.md
- docs/frontend/frontend-skill-usage-guide.md
- docs/frontend/frontend-design-blueprint.md
- docs/frontend/backend-api-screen-map.md
- docs/frontend/api-integration-guide.md
- docs/frontend/design-system.md
- docs/frontend/interaction-guardrails.md
- docs/frontend/frontend-environment-guide.md
- frontend/package.json
- frontend/app and existing frontend scaffold files
- relevant backend auth DTO/controller files for `/api/auth/login` and `/api/auth/me`

Before implementing API calls, verify that the relevant backend endpoints and DTOs actually exist in the current
codebase. If code differs from docs, do not invent frontend calls; implement only the verified surface or ask for
confirmation.

Implementation style:
- Make the smallest coherent changes needed for this phase.
- Follow existing frontend file naming, import style, component style, and TypeScript conventions.
- Keep route files thin; put reusable components, API functions, hooks, utilities, and types outside `frontend/app`.
- Do not silently introduce mock data into real app flows.
- Prefer clear beginner-friendly copy over trading-terminal wording.
- Temporary local `console.log` debugging is acceptable while diagnosing, but do not leave noisy logs in final code and
  never log credentials, `Authorization`, `X-Admin-Token`, passwords, API keys, or secrets.

Scope:
- Replace starter route structure with StockMentor route groups and placeholder screens.
- Add root providers, theme tokens, base layout components, auth/session provider, route guards, and API client core.
- Add typed DTO foundations and error normalization.
- Use `EXPO_PUBLIC_API_BASE_URL`.
- Keep components, API, hooks, utilities, and types outside `frontend/app`.

Non-scope:
- Do not implement full auth/onboarding screens, stock screens, paper trading, AI suggestions, or admin tables.
- Do not modify backend files, frontend package files, lock files, `.agents/`, `skills-lock.json`, or unrelated docs.
- Do not call OpenAI, Twelve Data, scheduler/backfill internals, or paper-trading internals.
- Do not create .gitignore at frontend folder, if really want to add certain file, then update the root directory's .gitignore

Dependency policy:
- Default: do not install dependencies.
- You may recommend React Query for server state if it clearly benefits this phase.
- Do not install React Query or mutate package/lock files in this phase. If it is needed, report it as a specific
  recommendation for a separate user-approved dependency task.

Verification:
- Run `git diff --check`.
- Run frontend lint/type checks available in the repo, such as `npm run lint` from `frontend`, if dependencies are
  already installed.
- Smoke-check Expo Go or Expo Web only if practical and non-destructive.
- Run `git status --short`.

Final handoff must report files changed, features completed, known limitations, manual test results, verification
commands run, skipped verification and why if any, backend endpoint/DTO mismatches found if any, next-phase notes,
whether any dependency was added or recommended, and confirmation nothing was staged or committed.
```

## Phase 2 Prompt: Auth, Role Routing, Onboarding, And Profile

```text
Use `building-native-ui`, `native-data-fetching`, `frontend-design`, and `accessibility`.

For the listed skills, read the relevant local `.agents/skills/<skill>/SKILL.md` files and any directly relevant
reference files. Use them as guidance only; do not modify, stage, or commit `.agents/` or `skills-lock.json`.

Task: Implement StockMentor auth, role routing, onboarding, and profile screens.

Suggested branch/worktree name: `frontend-auth-onboarding-profile`. Do not create a branch or worktree unless I
explicitly ask. Do not stage or commit.

Start in plan mode first. Read the required files, inspect the current implementation, summarize what you found, and
propose the implementation plan. Wait for my approval before editing files unless I explicitly tell you to implement
immediately.

Before editing, inspect the current codebase because frontend files may differ from the plan. Read:
- AGENTS.md
- docs/frontend/frontend-implementation-master-plan.md
- docs/frontend/frontend-phase-prompts.md
- docs/frontend/frontend-skill-usage-guide.md
- docs/frontend/frontend-design-blueprint.md
- docs/frontend/mobile-user-flow.md
- docs/frontend/backend-api-screen-map.md
- docs/frontend/api-integration-guide.md
- docs/frontend/design-system.md
- docs/frontend/interaction-guardrails.md
- frontend/app and existing auth/session/API files from Phase 1
- backend auth and user profile controller/DTO files

Before implementing API calls, verify that the relevant backend endpoints and DTOs actually exist in the current
codebase. If code differs from docs, do not invent frontend calls; implement only the verified surface or ask for
confirmation.

Implementation style:
- Make the smallest coherent changes needed for this phase.
- Follow existing frontend file naming, import style, component style, and TypeScript conventions.
- Keep route files thin; put reusable components, API functions, hooks, utilities, and types outside `frontend/app`.
- Do not silently introduce mock data into real app flows.
- Prefer clear beginner-friendly copy over trading-terminal wording.
- Temporary local `console.log` debugging is acceptable while diagnosing, but do not leave noisy logs in final code and
  never log credentials, `Authorization`, `X-Admin-Token`, passwords, API keys, or secrets.

Scope:
- Implement welcome, register, login, auth bootstrap through `GET /api/auth/me`, role routing, onboarding quiz,
  onboarding result/profile, retake confirmation, and logout.
- Use Basic Auth for MVP.
- Route `ADMIN` users to the admin shell, beginners with `mustCompleteOnboarding=true` to onboarding, and completed
  beginners to the dashboard.
- Add loading, empty, validation, 401/403/409, backend unavailable, and duplicate-submit states.
- Keep auth forms keyboard-aware, preserve failed-submit input, avoid route-level loading unmounts during submit, and
  avoid unnecessary scroll/bounce when content fits.
- Respect iOS safe-area and Dynamic Island insets; custom auth/onboarding page identity must not sit under system UI.
- After a first invalid submit, live-validate account fields until all fields are valid, and make disabled submit
  buttons visually disabled.
- Onboarding should use a mobile-friendly one-question-at-a-time flow, validate every backend-returned question before
  submit, and avoid displaying duplicated option label/description text.
- Keep onboarding Back/Next/Finish controls fixed at the bottom safe area, use compact selected-state affordances that
  cannot overflow, and show a clear processing state during final save.
- If onboarding final submit times out or reports an account-state mismatch, offer a refresh-account recovery path and
  route out of the quiz when `GET /api/auth/me` shows onboarding is complete.
- Validate login/register field formats on the client before requests when backend rules are known.
- Use beginner-proof recovery: preserve input, keep long-form page identity visible, and map backend-known duplicate or
  validation errors to the exact field whenever possible.
- Avoid user-facing implementation disclaimers in loading or processing states when they add cognitive load without
  helping the user recover.
- Do not enable forgot-password UI unless the backend has a verified reset-token or OTP contract; otherwise keep it
  clearly future-scoped.

Backend endpoints:
- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/me`
- `GET /api/user/onboarding/questions`
- `POST /api/user/onboarding`
- `POST /api/user/onboarding/retake`
- `GET /api/user/profile`

Non-scope:
- Do not implement stock browsing, AI suggestions, paper trading, or admin console features beyond route placeholders.
- Do not calculate risk scores, behavior profile, AI suggestions, or AI explanations in the frontend.
- Do not modify backend files, frontend package files, lock files, `.agents/`, or `skills-lock.json`.
- Do not create .gitignore at frontend folder, if really want to add certain file, then update the root directory's .gitignore

Dependency policy:
- Do not install dependencies.

Verification:
- Run register/login/bootstrap manual checks against the backend.
- Test wrong credentials, inactive/forbidden style errors if available, onboarding gate, retake confirmation, and logout.
- Run duplicate-submit checks on register/login/onboarding finish.
- Run frontend lint/type checks available in the repo.
- Run `git diff --check` and `git status --short`.

Final handoff must report files changed, features completed, known limitations, manual test results, verification
commands run, skipped verification and why if any, backend endpoint/DTO mismatches found if any, next-phase notes,
whether any dependency was added or recommended, and confirmation nothing was staged or committed.
```

## Phase 3 Prompt: Beginner Stock Learning, Delayed Data, Chart, Watchlist, And AI Explanation

```text
Use `building-native-ui`, `native-data-fetching`, `frontend-design`, and `vercel-react-native-skills`.

For the listed skills, read the relevant local `.agents/skills/<skill>/SKILL.md` files and any directly relevant
reference files. Use them as guidance only; do not modify, stage, or commit `.agents/` or `skills-lock.json`.

Task: Implement beginner stock learning screens with delayed educational market data, watchlist, chart, and AI
explanation.

Suggested branch/worktree name: `frontend-stock-learning`. Do not create a branch or worktree unless I explicitly
ask. Do not stage or commit.

Start in plan mode first. Read the required files, inspect the current implementation, summarize what you found, and
propose the implementation plan. Wait for my approval before editing files unless I explicitly tell you to implement
immediately.

Before editing, inspect the current codebase because frontend files may differ from the plan. Read:
- AGENTS.md
- docs/frontend/frontend-implementation-master-plan.md
- docs/frontend/frontend-phase-prompts.md
- docs/frontend/frontend-skill-usage-guide.md
- docs/frontend/mobile-user-flow.md
- docs/frontend/backend-api-screen-map.md
- docs/frontend/api-integration-guide.md
- docs/frontend/design-system.md
- docs/frontend/interaction-guardrails.md
- docs/frontend/frontend-testing-checklist.md
- frontend stock/home route and component areas from earlier phases
- backend stock, history, watchlist, and AI explanation controller/DTO files

Before implementing API calls, verify that the relevant backend endpoints and DTOs actually exist in the current
codebase. If code differs from docs, do not invent frontend calls; implement only the verified surface or ask for
confirmation.

Implementation style:
- Make the smallest coherent changes needed for this phase.
- Follow existing frontend file naming, import style, component style, and TypeScript conventions.
- Keep route files thin; put reusable components, API functions, hooks, utilities, and types outside `frontend/app`.
- Do not silently introduce mock data into real app flows.
- Prefer clear beginner-friendly copy over trading-terminal wording.
- Temporary local `console.log` debugging is acceptable while diagnosing, but do not leave noisy logs in final code and
  never log credentials, `Authorization`, `X-Admin-Token`, passwords, API keys, or secrets.

Scope:
- Preserve the current Phase 3B table-first stock UI standard.
- Implement or extend Watchlist, Stocks/Paper Trade, Search, stock detail, backend-returned history summary/list,
  watchlist add/remove, and user-facing AI explanation drawer.
- Use compact full-width tables instead of card-style stock rows.
- Keep visible beginner tabs as `Watchlist`, `Stocks`, `Search`, `Suggestions`, `Practice`, and `Profile`.
- Use hidden contextual search route `/stocks/search-context` and explicit return params for stock/detail/search/practice
  navigation.
- Prefer delayed fields: `displayedPrice`, `displayedPercentChange`, `displayedMarketTime`,
  `targetDisplayMarketTime`, `priceFreshnessStatus`, `isPriceAvailable`, and `dataNote`.
- Use `previousClose`, `displayedAbsoluteChange`, and `displayedVolume` only when the backend exposes them.
- Treat legacy stock fields as compatibility/fallback only.
- Use backend history points exactly as returned; do not fill or invent missing 1-minute candles.
- Show "15-minute delayed educational market data" wording and educational AI disclaimers.
- Do not show raw backend source/status/time values in compact stock rows.
- Market notice should be a compact marquee strip that preserves full copy with no string slicing or ellipsis.
- AI explanation must fetch only when the drawer is opened, use `View AI Stock Explanation` / `Close AI Stock
  Explanation`, and hide backend cache/generated status messages.
- Use `safe-storage.ts` for search history/latest viewed stocks; AsyncStorage failure must not red-screen.

Backend endpoints:
- `GET /api/stocks`
- `GET /api/stocks/{symbol}`
- `GET /api/stocks/{symbol}/history?timeframe=1D|7D|1M|3M|YTD|1Y`
- `GET /api/stocks/{symbol}/ai-explanation?timeframe=1D|7D|1M|3M`
- `GET /api/watchlist`
- `POST /api/watchlist/{symbol}`
- `DELETE /api/watchlist/{symbol}`

Non-scope:
- Do not implement AI suggestion refresh, paper-trading buy/sell, admin stock maintenance, or backend changes.
- Do not call Twelve Data or OpenAI directly.
- Do not modify backend files, frontend package files, lock files, `.agents/`, or `skills-lock.json`.
- Do not create .gitignore at frontend folder, if really want to add certain file, then update the root directory's .gitignore

Dependency policy:
- Default: do not install dependencies.
- Do not install chart dependencies by default.
- For MVP, first use existing dependencies and backend-returned points.
- If existing dependencies cannot support a simple educational chart, implement a safe history summary/list and report a
  specific chart-library recommendation for a separate user-approved dependency task.
- Do not install a chart library in Phase 3 unless I explicitly approve it in this chat.

Verification:
- Test stock list/detail/history, empty history response, timeframe changes, delayed badge/note, unavailable price
  states, watchlist add/remove duplicate-tap prevention, and AI explanation unavailable state.
- Confirm no direct Twelve Data/OpenAI calls.
- Run frontend lint/type checks available in the repo.
- Run `git diff --check` and `git status --short`.

Final handoff must report files changed, features completed, known limitations, manual test results, verification
commands run, skipped verification and why if any, backend endpoint/DTO mismatches found if any, next-phase notes,
whether any dependency was added or recommended, and confirmation nothing was staged or committed.
```

## Phase 4 Prompt: AI Stock Suggestions

```text
Use `building-native-ui`, `native-data-fetching`, and `frontend-design`.

For the listed skills, read the relevant local `.agents/skills/<skill>/SKILL.md` files and any directly relevant
reference files. Use them as guidance only; do not modify, stage, or commit `.agents/` or `skills-lock.json`.

Task: Implement StockMentor AI stock suggestions screens and actions.

Suggested branch/worktree name: `frontend-ai-suggestions`. Do not create a branch or worktree unless I explicitly
ask. Do not stage or commit.

Start in plan mode first. Read the required files, inspect the current implementation, summarize what you found, and
propose the implementation plan. Wait for my approval before editing files unless I explicitly tell you to implement
immediately.

Before editing, inspect the current codebase because frontend files may differ from the plan. Read:
- AGENTS.md
- docs/frontend/frontend-implementation-master-plan.md
- docs/frontend/frontend-phase-prompts.md
- docs/frontend/frontend-skill-usage-guide.md
- docs/frontend/backend-api-screen-map.md
- docs/frontend/api-integration-guide.md
- docs/frontend/design-system.md
- docs/frontend/interaction-guardrails.md
- docs/frontend/mobile-user-flow.md
- frontend suggestions/component/API areas from earlier phases
- backend stock AI suggestion controller/DTO files

Before implementing API calls, verify that the relevant backend endpoints and DTOs actually exist in the current
codebase. If code differs from docs, do not invent frontend calls; implement only the verified surface or ask for
confirmation.

Implementation style:
- Make the smallest coherent changes needed for this phase.
- Follow existing frontend file naming, import style, component style, and TypeScript conventions.
- Keep route files thin; put reusable components, API functions, hooks, utilities, and types outside `frontend/app`.
- Do not silently introduce mock data into real app flows.
- Prefer clear beginner-friendly copy over trading-terminal wording.
- Temporary local `console.log` debugging is acceptable while diagnosing, but do not leave noisy logs in final code and
  never log credentials, `Authorization`, `X-Admin-Token`, passwords, API keys, or secrets.

Scope:
- Implement suggestion list, remaining stocks, fallback/unavailable messaging, refresh action, cooldown display,
  dismiss action, and watchlist suggestion action.
- Respect `refreshAllowed` and `nextRefreshAllowedAt`.
- Keep GET suggestions read-only/cache-only from the frontend perspective.
- Use educational wording only.

Backend endpoints:
- `GET /api/stocks/ai-suggestions`
- `POST /api/stocks/ai-suggestions/refresh`
- `PATCH /api/stocks/ai-suggestions/items/{itemId}/dismiss`
- `PATCH /api/stocks/ai-suggestions/items/{itemId}/watchlist`

Non-scope:
- Do not generate AI suggestions in the frontend.
- Do not call OpenAI directly.
- Do not calculate behavior profile in the frontend.
- Do not modify backend files, frontend package files, lock files, `.agents/`, or `skills-lock.json`.
- Do not create .gitignore at frontend folder, if really want to add certain file, then update the root directory's .gitignore

Dependency policy:
- Do not install dependencies.

Verification:
- Test cached suggestions, empty suggestions, refresh allowed, cooldown disabled, next refresh time, fallback copy,
  dismiss/watchlist actions, and rapid-tap prevention.
- Run frontend lint/type checks available in the repo.
- Run `git diff --check` and `git status --short`.

Final handoff must report files changed, features completed, known limitations, manual test results, verification
commands run, skipped verification and why if any, backend endpoint/DTO mismatches found if any, next-phase notes,
whether any dependency was added or recommended, and confirmation nothing was staged or committed.
```

## Phase 5 Prompt: Paper Trading Practice

```text
Use `building-native-ui`, `native-data-fetching`, `frontend-design`, and `vercel-react-native-skills`.

For the listed skills, read the relevant local `.agents/skills/<skill>/SKILL.md` files and any directly relevant
reference files. Use them as guidance only; do not modify, stage, or commit `.agents/` or `skills-lock.json`.

Task: Implement StockMentor paper-trading account, portfolio, buy/sell, reset, and transactions.

Suggested branch/worktree name: `frontend-paper-trading`. Do not create a branch or worktree unless I explicitly
ask. Do not stage or commit.

Start in plan mode first. Read the required files, inspect the current implementation, summarize what you found, and
propose the implementation plan. Wait for my approval before editing files unless I explicitly tell you to implement
immediately.

Before editing, inspect the current codebase because frontend files may differ from the plan. Read:
- AGENTS.md
- docs/frontend/frontend-implementation-master-plan.md
- docs/frontend/frontend-phase-prompts.md
- docs/frontend/frontend-skill-usage-guide.md
- docs/frontend/mobile-user-flow.md
- docs/frontend/backend-api-screen-map.md
- docs/frontend/api-integration-guide.md
- docs/frontend/design-system.md
- docs/frontend/interaction-guardrails.md
- frontend paper-trading/API/component areas from earlier phases
- backend paper-trading controller/DTO files

Before implementing API calls, verify that the relevant backend endpoints and DTOs actually exist in the current
codebase. If code differs from docs, do not invent frontend calls; implement only the verified surface or ask for
confirmation.

Implementation style:
- Make the smallest coherent changes needed for this phase.
- Follow existing frontend file naming, import style, component style, and TypeScript conventions.
- Keep route files thin; put reusable components, API functions, hooks, utilities, and types outside `frontend/app`.
- Do not silently introduce mock data into real app flows.
- Prefer clear beginner-friendly copy over trading-terminal wording.
- Temporary local `console.log` debugging is acceptable while diagnosing, but do not leave noisy logs in final code and
  never log credentials, `Authorization`, `X-Admin-Token`, passwords, API keys, or secrets.

Scope:
- Implement paper account, portfolio, positions, buy ticket, sell ticket, reset confirmation, transaction history, and
  transaction detail.
- Validate whole-share positive quantities on the frontend, while keeping backend validation authoritative.
- Show delayed execution metadata when backend returns it.
- Confirm buy, sell, and reset.
- Ensure buy/sell payloads send only `symbol` and `quantity`.

Backend endpoints:
- `GET /api/paper-trading/account`
- `GET /api/paper-trading/portfolio`
- `POST /api/paper-trading/portfolio/reset`
- `POST /api/paper-trading/buy`
- `POST /api/paper-trading/sell`
- `GET /api/paper-trading/transactions`
- `GET /api/paper-trading/transactions/{transactionId}`

Non-scope:
- Do not send price from the frontend.
- Do not implement advanced orders, brokerage integration, frontend behavior profile calculation, or backend changes.
- Do not modify backend files, frontend package files, lock files, `.agents/`, or `skills-lock.json`.
- Do not create .gitignore at frontend folder, if really want to add certain file, then update the root directory's .gitignore

Dependency policy:
- Do not install dependencies.

Verification:
- Test account/portfolio, buy success, insufficient cash, sell success, sell exceeds holding, reset confirmation,
  transaction filters/detail, fractional quantity rejection, and rapid-tap prevention.
- Inspect request payloads to confirm no paper-trading price is sent.
- Run frontend lint/type checks available in the repo.
- Run `git diff --check` and `git status --short`.

Final handoff must report files changed, features completed, known limitations, manual test results, verification
commands run, skipped verification and why if any, backend endpoint/DTO mismatches found if any, next-phase notes,
whether any dependency was added or recommended, and confirmation nothing was staged or committed.
```

## Phase 6 Prompt: Admin Web/Tablet Console

```text
Use `building-native-ui`, `native-data-fetching`, `web-design-guidelines`, and `frontend-design`.

For the listed skills, read the relevant local `.agents/skills/<skill>/SKILL.md` files and any directly relevant
reference files. Use them as guidance only; do not modify, stage, or commit `.agents/` or `skills-lock.json`.

Task: Implement the StockMentor admin web/tablet console in the same Expo codebase.

Suggested branch/worktree name: `frontend-admin-console`. Do not create a branch or worktree unless I explicitly
ask. Do not stage or commit.

Start in plan mode first. Read the required files, inspect the current implementation, summarize what you found, and
propose the implementation plan. Wait for my approval before editing files unless I explicitly tell you to implement
immediately.

Before editing, inspect the current codebase because frontend files may differ from the plan. Read:
- AGENTS.md
- docs/frontend/frontend-implementation-master-plan.md
- docs/frontend/frontend-phase-prompts.md
- docs/frontend/frontend-skill-usage-guide.md
- docs/frontend/admin-web-flow.md
- docs/frontend/backend-api-screen-map.md
- docs/frontend/api-integration-guide.md
- docs/frontend/design-system.md
- docs/frontend/interaction-guardrails.md
- docs/frontend/frontend-environment-guide.md
- frontend admin/auth/API/layout areas from earlier phases
- backend admin users, admin AI suggestions, and admin stock controller/DTO files

Before implementing API calls, verify that the relevant backend endpoints and DTOs actually exist in the current
codebase. Do not assume admin AI/user/stock endpoints exist only from the docs. If an endpoint is missing or differs
from docs, do not invent frontend calls; report the mismatch and implement only the verified available surface, or ask
for confirmation.

Implementation style:
- Make the smallest coherent changes needed for this phase.
- Follow existing frontend file naming, import style, component style, and TypeScript conventions.
- Keep route files thin; put reusable components, API functions, hooks, utilities, and types outside `frontend/app`.
- Do not silently introduce mock data into real app flows.
- Prefer clear beginner-friendly copy over trading-terminal wording.
- Temporary local `console.log` debugging is acceptable while diagnosing, but do not leave noisy logs in final code and
  never log credentials, `Authorization`, `X-Admin-Token`, passwords, API keys, or secrets.

Scope:
- Implement admin login/token UX, admin dashboard, users list/detail, disable/re-enable confirmation, AI suggestion
  monitoring, refresh job monitoring/detail, manual scheduled refresh, and stock maintenance form/results.
- Use Expo Web / React Native Web friendly layouts on web/tablet widths.
- On phone-sized screens, show "Admin console is best viewed on tablet or web", allow logout, and avoid full tables or
  destructive maintenance actions.
- Include `Authorization` and `X-Admin-Token` on admin API calls.

Backend endpoints:
- `GET /api/admin/users`
- `GET /api/admin/users/{userId}`
- `PATCH /api/admin/users/{userId}/status`
- `GET /api/admin/ai-suggestions/batches`
- `GET /api/admin/ai-suggestions/batches/{batchId}`
- `GET /api/admin/ai-suggestions/failures`
- `GET /api/admin/ai-suggestions/usage-summary`
- `POST /api/admin/ai-suggestions/scheduled-refresh/run`
- `GET /api/admin/ai-suggestions/refresh-jobs`
- `GET /api/admin/ai-suggestions/refresh-jobs/{jobId}`
- `POST /api/admin/stocks/backfill`

Non-scope:
- Do not create a separate Vite/Next admin app.
- Do not implement admin AI explanation maintenance screens unless backend endpoints exist.
- Do not implement backend CORS.
- Do not modify backend files, frontend package files, lock files, `.agents/`, or `skills-lock.json`.
- Do not create .gitignore at frontend folder, if really want to add certain file, then update the root directory's .gitignore

Dependency policy:
- Do not install dependencies.

Verification:
- Test admin login/token entry, normal-user rejection, missing/wrong token re-entry, user list/detail, disable/re-enable
  conflicts, AI monitoring tables, scheduled refresh confirmation, stock maintenance confirmation, and phone fallback.
- Expo Web admin browser testing may require backend CORS follow-up.
- Run frontend lint/type checks available in the repo.
- Run `git diff --check` and `git status --short`.

Final handoff must report files changed, features completed, known limitations, manual test results, verification
commands run, skipped verification and why if any, backend endpoint/DTO mismatches found if any, next-phase notes,
whether any dependency was added or recommended, and confirmation nothing was staged or committed.
```

## Phase 7 Prompt: Final Integration, Demo, Testing, Design, And Accessibility Polish

```text
Use `webapp-testing`, `accessibility`, `web-design-guidelines`, `frontend-design`, and `native-data-fetching`.

For the listed skills, read the relevant local `.agents/skills/<skill>/SKILL.md` files and any directly relevant
reference files. Use them as guidance only; do not modify, stage, or commit `.agents/` or `skills-lock.json`.

Task: Run final frontend integration, demo, testing, design, and accessibility polish for StockMentor.

Suggested branch/worktree name: `frontend-integration-polish`. Do not create a branch or worktree unless I
explicitly ask. Do not stage or commit.

Start in plan mode first. Read the required files, inspect the current implementation, summarize what you found, and
propose the implementation plan. Wait for my approval before editing files unless I explicitly tell you to implement
immediately.

Before editing, inspect the current codebase because frontend files may differ from the plan. Read:
- AGENTS.md
- docs/frontend/frontend-implementation-master-plan.md
- docs/frontend/frontend-phase-prompts.md
- docs/frontend/frontend-skill-usage-guide.md
- docs/frontend/frontend-testing-checklist.md
- docs/frontend/frontend-design-blueprint.md
- docs/frontend/design-system.md
- docs/frontend/interaction-guardrails.md
- docs/frontend/api-integration-guide.md
- all implemented frontend routes/components/API areas

Before changing API behavior or test assumptions, verify that the relevant backend endpoints and DTOs actually exist in
the current codebase. If code differs from docs, do not invent frontend calls; implement only the verified surface or
ask for confirmation.

Implementation style:
- Make the smallest coherent changes needed for this phase.
- Follow existing frontend file naming, import style, component style, and TypeScript conventions.
- Keep route files thin; put reusable components, API functions, hooks, utilities, and types outside `frontend/app`.
- Do not silently introduce mock data into real app flows.
- Prefer clear beginner-friendly copy over trading-terminal wording.
- Temporary local `console.log` debugging is acceptable while diagnosing, but do not leave noisy logs in final code and
  never log credentials, `Authorization`, `X-Admin-Token`, passwords, API keys, or secrets.

Scope:
- Run the frontend testing checklist and FYP demo story.
- Polish loading, empty, error, cooldown, confirmation, and unavailable states.
- Review mobile HCI, web/tablet admin UI, keyboard behavior, touch targets, contrast, and AI disclaimer wording.
- Confirm fit-to-screen pages do not casually scroll/bounce, mobile forms keep focused inputs visible above the
  keyboard, recoverable errors preserve input, password visibility controls are accessible, and diagnostics do not
  expose secrets.
- Confirm mobile quiz actions stay fixed, question transitions are subtle, selected option UI does not overflow, and
  final-submit processing copy does not imply an exact backend analysis percentage.
- Verify delayed market data display correctness and paper-trading no-price payload rule.
- Use Playwright/web testing for Expo Web where practical.

Non-scope:
- Do not add new product features.
- Do not perform a large redesign.
- Do not implement backend CORS or backend changes.
- Do not modify backend files, `.agents/`, or `skills-lock.json`.
- Do not modify frontend package files or lock files unless I explicitly approve a testing dependency.
- Do not create .gitignore at frontend folder, if really want to add certain file, then update the root directory's .gitignore

Dependency policy:
- Do not install dependencies by default.

Verification:
- Run frontend lint/type checks available in the repo.
- Run Expo Go mobile smoke checks.
- Run Expo Web admin checks after CORS is available.
- Use Playwright checks where practical.
- Run manual accessibility checks for keyboard navigation, focus visibility, target size, contrast, and modal escape.
- Run `git diff --check` and `git status --short`.

Final handoff must report files changed, features completed, known limitations, manual test results, verification
commands run, skipped verification and why if any, backend endpoint/DTO mismatches found if any, next-phase notes,
whether any dependency was added or recommended, and confirmation nothing was staged or committed.
```
