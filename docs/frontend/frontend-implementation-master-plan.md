# StockMentor Frontend Implementation Master Plan

This document is the detailed execution source of truth for future StockMentor frontend implementation work. It should
be used together with `docs/frontend/frontend-phase-prompts.md` and the existing frontend docs in this folder.

This document does not create branches or worktrees. Suggested branch/worktree names are guidance for future work only.
Each implementation phase is intended to run in a separate branch/worktree after its prerequisites have been merged.

## Baseline To Assume

- Frontend stack: TypeScript, React Native, Expo, Expo Router.
- Current `frontend/` app has the Phase 1 StockMentor route shell, API core, in-memory session providers, theme tokens,
  base UI primitives, and the landed Phase 2B/2.5 account flows: welcome, register, login, auth bootstrap, role
  routing, onboarding, onboarding result, profile, and admin placeholders. It also has the Phase 3B stock-learning
  surfaces: Watchlist, Stocks, Search, stock detail, history summary/list, watchlist actions, safe storage for search
  history/latest viewed stocks, and on-demand AI explanation drawer. Future chats must inspect the current codebase
  before editing because files may differ from this plan.
- The Spring Boot backend is the only app data source.
- Use `EXPO_PUBLIC_API_BASE_URL` for the backend base URL.
- Read these docs before each phase: `AGENTS.md`, `docs/frontend/frontend-design-blueprint.md`,
  `docs/frontend/backend-api-screen-map.md`, `docs/frontend/api-integration-guide.md`,
  `docs/frontend/frontend-skill-usage-guide.md`, `docs/frontend/interaction-guardrails.md`, and the phase-specific
  flow/design docs.

## Cross-Phase Consistency Rules

Every phase must preserve these rules:

- Frontend calls the Spring Boot backend only.
- Frontend must not call OpenAI, Twelve Data, scheduler/backfill internals, paper-trading internals, or brokerage APIs.
- Frontend must not send trusted `userId` for current-user normal flows.
- Frontend must not send paper-trading price. Paper-trading writes send only backend-supported fields such as `symbol`
  and `quantity`.
- Stock display must prefer delayed fields: `displayedPrice`, `displayedPercentChange`, `displayedMarketTime`,
  `targetDisplayMarketTime`, `priceFreshnessStatus`, `priceSource`, `isPriceAvailable`, and `dataNote`.
- Stock detail may use `previousClose`, `displayedAbsoluteChange`, and `displayedVolume` when the backend provides
  them. Do not derive trusted absolute change or volume in the frontend.
- Legacy stock fields such as `currentPrice`, `percentChange`, and `lastUpdated` are compatibility/fallback only.
- Use `safe-storage.ts` for non-sensitive same-device persistence. Direct AsyncStorage failures must never red-screen
  app flows.
- Preserve deterministic stock navigation by passing explicit return params instead of relying on tab history alone.
- AI wording must stay educational and must not present financial advice, price predictions, or buy/sell pressure.
- Admin token must remain session-only and must never be hardcoded, committed, or placed in `EXPO_PUBLIC_` variables.
- Real app flows must not silently use mock backend responses.
- `.agents/` and `skills-lock.json` are local agent tooling files and must not be edited, staged, or committed during
  normal frontend implementation phases.
- Do not stage or commit unless the user explicitly asks in that future phase.
- Before implementing API calls, verify that the relevant backend endpoints and DTOs actually exist in the current
  backend codebase. If code differs from docs, do not invent frontend calls; implement only the verified surface or ask
  for confirmation.

## Worktree And Merge Model

- Phase 1 and Phase 2 are sequential and should be merged before most feature phases start.
- Phase 3 should preferably merge before Phase 4 and Phase 5 because it establishes compact stock tables,
  delayed-data display, watchlist/search patterns, safe storage, and placeholder practice-trade navigation.
- Phase 4, Phase 5, and Phase 6 can run in parallel after Phase 2 if each branch avoids shared foundation rewrites.
- Phase 7 must run last after all selected feature phases are merged.
- Avoid overlapping edits to shared files such as root layouts, theme tokens, API client core, auth/session providers,
  base components, and shared DTO types. If a later phase needs shared changes, make the smallest compatible addition.
- Each worktree should branch from the latest main branch containing all prerequisite phase merges.

## Dependency Policy

- Default rule: do not install dependencies.
- If a phase explicitly allows a dependency, Codex must explain why it is needed, choose an Expo-compatible option,
  update only the correct `frontend/` package and lock files, and verify the app still runs.
- React Query was not added in Phase 1. If it is adopted for server state in a later phase, dependency installation
  must be a separate user-approved task.
- Do not add chart dependencies by default. For future charts, first use existing dependencies and backend-returned points.
  If existing dependencies cannot support a simple educational chart, implement a safe non-chart fallback or minimal
  placeholder and report a specific chart dependency recommendation for a separate user-approved dependency task. Do
  not install a chart library in Phase 3 unless the user explicitly approves it in that implementation chat.
- Do not add a large UI kit, SecureStore, native modules, CORS config, backend dependencies, or unrelated tooling unless
  a later user prompt explicitly scopes it.

## Required Phase Handoff Format

Every implementation phase must end with:

- Files changed.
- Features completed.
- Known limitations.
- Manual test results.
- Verification commands run.
- What the next phase needs to know.
- Whether any dependency was added.
- Confirmation that nothing was staged or committed unless explicitly requested.

## Skill Usage Quick Reference

The skills named below are local agent guidance under `.agents/skills/`. Future phases may read the relevant `SKILL.md`
and reference files, but these files are not frontend app source code, not package dependencies, and not runtime files;
normal implementation phases must not edit, stage, or commit `.agents/` or `skills-lock.json`.

- Use `frontend-design` for visual direction, screen UX, empty/error copy, AI disclaimers, and calm beginner wording.
- Use `building-native-ui` for Expo Router route groups, native/web layouts, tabs, stacks, safe areas, and route files.
- Use `native-data-fetching` for API client, fetch, React Query, Basic Auth, admin token headers, env vars, errors,
  retries, cancellation, and network debugging.
- Use `vercel-react-native-skills` for component implementation, list performance, mobile performance, animations, and
  React Native rendering patterns.
- Use `web-design-guidelines` during admin web/tablet UI review.
- Use `webapp-testing` for Expo Web and admin browser testing with Playwright.
- Use `accessibility` for WCAG/HCI checks, keyboard navigation, touch target, contrast, and modal accessibility.

## Debug Logging Policy

- Temporary local `console.log` debugging is acceptable while diagnosing implementation issues.
- Noisy debug logs should not remain in committed code.
- Never log passwords, Basic Auth credentials, `Authorization`, `X-Admin-Token`, admin token values, API keys, or
  secrets.
- Prefer safe centralized API error normalization and dev-only diagnostics.

## Phase 1: Frontend Foundation, Route Shell, And API Core

- Status: completed. Preserve the foundation and make compatible additions in later phases.
- Suggested branch/worktree: `codex/frontend-foundation-shell`.
- Required skills: `building-native-ui`, `native-data-fetching`, `frontend-design`.
- Purpose: replace the starter shell with StockMentor foundations that later feature phases can safely build on.
- Prerequisites: clean baseline with backend and docs already merged.
- Likely changes: `frontend/app`, shared theme/components, auth/session provider, API client core, DTO type folders,
  route guard utilities, environment config helpers.
- Must not change: backend source/tests, `.agents`, `skills-lock.json`, unrelated docs, package files, or lock files.
- Endpoints involved: `/api/auth/login`, `/api/auth/me` for bootstrap shape only; no full screen workflows yet.
- Dependency policy: do not install dependencies. Codex may recommend React Query for server state, but package changes
  require a separate user-approved dependency task.
- Scope: route groups, root providers, base layout primitives, API error normalization, Basic Auth/admin-token request
  plumbing, empty placeholder routes, and no-mock-data guardrails.
- Out of scope: finished screens, admin tables, stock chart, paper-trading tickets, AI suggestion UI.
- Acceptance: app has valid `/`, public/auth/onboarding/user/admin route groups, typed API foundations, backend-only
  fetch wrapper, route guards, and no direct OpenAI/Twelve Data references.
- Verification: `npm run lint` from `frontend` if dependencies are available, TypeScript/lint checks used by the repo,
  Expo Go smoke check if practical, and `git diff --check`.
- Handoff: document any installed dependency, route names created, auth storage behavior, and unresolved CORS/admin-web
  testing limitations.

## Phase 2: Auth, Role Routing, Onboarding, And Profile

- Status: completed through Phase 2B/2.5. Preserve the memory-only Basic Auth MVP, role routing,
  onboarding/profile flows, and HCI/accessibility polish when building later phases.
- Suggested branch/worktree: `codex/frontend-auth-onboarding-profile`.
- Required skills: `building-native-ui`, `native-data-fetching`, `frontend-design`, `accessibility`.
- Purpose: make account entry and onboarding complete enough to unlock user/admin flows.
- Prerequisites: Phase 1 merged.
- Likely changes: public/auth/onboarding/profile routes, auth API module, profile/onboarding API module, form
  components,
  validation helpers, auth/session state.
- Must not change: backend, package files unless user separately approves, stock/paper/admin feature screens beyond
  navigation placeholders.
- Endpoints involved: `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me`,
  `GET /api/user/onboarding/questions`, `POST /api/user/onboarding`, `POST /api/user/onboarding/retake`,
  `GET /api/user/profile`.
- Dependency policy: do not install dependencies.
- Scope: welcome, register, login, bootstrap routing, onboarding quiz, onboarding result/profile, retake confirmation,
  settings/logout basics, role-based redirection.
- Out of scope: stock browsing, AI suggestions, paper trading, admin console implementation.
- Acceptance: no credentials route to public screens; admin routes to admin shell; beginner with
  `mustCompleteOnboarding=true` routes to onboarding; completed beginner routes to dashboard; logout clears credentials,
  admin token, query cache if present, and auth storage abstraction state.
- Verification: auth/onboarding manual smoke tests against backend, invalid credential test, route refresh/bootstrap
  test, duplicate-submit test, lint/type checks, `git diff --check`.
- Handoff: report auth storage choice, exact route names, validation behavior, and any backend/CORS limitation.

## Phase 3: Beginner Stock Learning, Delayed Data, Chart, Watchlist, And AI Explanation

- Status: Phase 3B working standard is compact table-first stock learning. Preserve its UI/navigation conventions in
  later phases.
- Suggested branch/worktree: `codex/frontend-stock-learning`.
- Required skills: `building-native-ui`, `native-data-fetching`, `frontend-design`, `vercel-react-native-skills`.
- Purpose: implement the stock-learning experience that demonstrates delayed educational market data.
- Prerequisites: Phase 2 merged.
- Likely changes: user Watchlist/Stocks/Search routes, stocks API module, watchlist API module, AI explanation API
  module, compact table components, market notice marquee, safe storage, delayed-data helpers, and watchlist controls.
- Must not change: admin console, real paper-trading writes beyond placeholder navigation links, package files, or lock
  files unless the user explicitly scopes that work.
- Endpoints involved: `GET /api/stocks`, `GET /api/stocks/{symbol}`,
  `GET /api/stocks/{symbol}/history?timeframe=1D|7D|1M|3M|YTD|1Y`,
  `GET /api/stocks/{symbol}/ai-explanation?timeframe=1D|7D|1M|3M`, `GET /api/watchlist`, `POST /api/watchlist/{symbol}`,
  `DELETE /api/watchlist/{symbol}`.
- Dependency policy: do not install chart dependencies by default. First use existing dependencies and backend-returned
  points. If existing dependencies cannot support a simple educational chart, implement a safe non-chart fallback or
  minimal placeholder and report a specific chart-library recommendation for a separate user-approved dependency task.
- Scope: Watchlist table, Stocks/Paper Trade table, Search tab/contextual search, stock detail/history summary,
  delayed metadata display, unavailable data states, watchlist add/remove, AI explanation drawer with disclaimer, safe
  storage for search history/latest viewed stocks, and safe pull-to-refresh for reads.
- Out of scope: AI suggestion refresh, paper-trading execution, admin maintenance actions.
- Acceptance: UI prefers delayed fields, hides raw backend source/status/time from compact rows, shows market notice
  marquee with full copy, never invents missing candles, labels stock data as delayed educational data, never calls
  Twelve Data/OpenAI directly, and practice-trade CTAs navigate only to the placeholder route until Phase 5.
- Verification: Watchlist/Stocks/Search/detail manual checks, deterministic back navigation, empty history state, 1D
  pre-open/closed wording, AI explanation on-demand/no-prefetch behavior, watchlist duplicate-tap prevention, safe
  storage fallback, lint/type checks.
- Handoff: report chart/history-summary choice, any dependency added, delayed-field rendering behavior, route return
  params, and reusable stock components available to later phases.

## Phase 4: AI Stock Suggestions

- Suggested branch/worktree: `codex/frontend-ai-suggestions`.
- Required skills: `building-native-ui`, `native-data-fetching`, `frontend-design`.
- Purpose: expose US006 suggestion results and refresh guardrails without generating AI in the frontend.
- Prerequisites: Phase 2 merged; Phase 3 preferred for shared stock/watchlist components.
- Likely changes: suggestions route, suggestions API module/hooks, suggestion cards, cooldown button, fallback states.
- Must not change: backend, OpenAI integration, stock market data logic, paper-trading execution, package files.
- Endpoints involved: `GET /api/stocks/ai-suggestions`, `POST /api/stocks/ai-suggestions/refresh`,
  `PATCH /api/stocks/ai-suggestions/items/{itemId}/dismiss`,
  `PATCH /api/stocks/ai-suggestions/items/{itemId}/watchlist`.
- Dependency policy: do not install dependencies.
- Scope: suggestion list, remaining stocks, refresh cooldown, next allowed refresh time, dismiss/watchlist actions,
  fallback/unavailable messaging, educational AI disclaimer.
- Out of scope: prompt construction, AI generation logic, behavior profile calculation, direct OpenAI calls.
- Acceptance: GET is read-only/cache-only in UI behavior, POST refresh is one-click guarded, cooldown disables refresh,
  no advice wording, and item actions re-enable after recoverable failure.
- Verification: cached suggestions display, no suggestions state, refresh allowed/disallowed paths, rapid-tap tests,
  fallback copy, lint/type checks.
- Handoff: report query keys/invalidation, suggestion card reuse, cooldown handling, and any backend data assumptions.

## Phase 5: Paper Trading Practice

- Suggested branch/worktree: `codex/frontend-paper-trading`.
- Required skills: `building-native-ui`, `native-data-fetching`, `frontend-design`, `vercel-react-native-skills`.
- Purpose: implement the simulated portfolio and trade flows while preserving backend-owned execution price.
- Prerequisites: Phase 2 merged; Phase 3 preferred for stock symbol/detail reuse.
- Likely changes: paper-trading routes, paper-trading API module/hooks, trade ticket, portfolio summary, positions list,
  transaction list/detail, confirmation modals.
- Must not change: backend, stock delayed selector, package files, AI suggestion logic.
- Endpoints involved: `GET /api/paper-trading/account`, `GET /api/paper-trading/portfolio`,
  `POST /api/paper-trading/portfolio/reset`, `POST /api/paper-trading/buy`, `POST /api/paper-trading/sell`,
  `GET /api/paper-trading/transactions`, `GET /api/paper-trading/transactions/{transactionId}`.
- Dependency policy: do not install dependencies.
- Scope: account/portfolio, positions, buy/sell with whole-share quantity validation, reset confirmation, transaction
  history/detail, delayed execution metadata display, insufficient cash/holding errors.
- Out of scope: advanced orders, brokerage integration, frontend behavior profile calculation, frontend price selection.
- Acceptance: buy/sell requests send only `symbol` and `quantity`; frontend never sends price; copy says practice trades
  use StockMentor's delayed stored price, not a live market quote; reset/buy/sell require confirmation and block
  duplicate submits.
- Verification: buy/sell/reset manual tests, fractional quantity validation, insufficient cash, sell exceeds holding,
  transaction filters, no-price payload inspection, lint/type checks.
- Handoff: report payload shape, delayed metadata display behavior, invalidation after trades, and any account bootstrap
  assumptions.

## Phase 6: Admin Web/Tablet Console

- Suggested branch/worktree: `codex/frontend-admin-console`.
- Required skills: `building-native-ui`, `native-data-fetching`, `web-design-guidelines`, `frontend-design`.
- Purpose: provide the MVP admin console in the same Expo codebase using Expo Web / React Native Web.
- Prerequisites: Phase 2 merged; can run in parallel with beginner feature phases if shared files stay stable.
- Likely changes: admin routes/layout, admin token prompt, admin users API, admin AI API, admin stock maintenance API,
  admin table/detail components, confirmation modals.
- Must not change: backend CORS, backend admin logic, normal user flows except shared auth/token handling, package
  files.
- Endpoints involved: `/api/admin/users`, `/api/admin/users/{userId}`, `/api/admin/users/{userId}/status`,
  `/api/admin/ai-suggestions/batches`, `/api/admin/ai-suggestions/batches/{batchId}`,
  `/api/admin/ai-suggestions/failures`, `/api/admin/ai-suggestions/usage-summary`,
  `/api/admin/ai-suggestions/scheduled-refresh/run`, `/api/admin/ai-suggestions/refresh-jobs`,
  `/api/admin/ai-suggestions/refresh-jobs/{jobId}`, `/api/admin/stocks/backfill`.
- Endpoint verification: inspect backend admin controllers and DTOs first. Do not assume admin AI/user/stock endpoints
  exist only from docs; if an endpoint is missing or differs, report the mismatch and implement only the verified
  available surface or ask for confirmation.
- Dependency policy: do not install dependencies.
- Scope: admin login/token UX, dashboard, users list/detail, disable/re-enable confirmations, AI monitoring, refresh job
  monitoring/manual run, stock maintenance form/results, phone best-viewed-on-tablet/web fallback.
- Out of scope: separate Vite/Next admin app, mobile admin table cram, admin AI explanation screens, backend CORS.
- Acceptance: admin APIs include Basic Auth and `X-Admin-Token`, normal users are rejected, invalid token prompts for
  re-entry, destructive actions require confirmation, and phone-sized admin does not expose full destructive UI.
- Verification: Expo Web admin manual checks after CORS is available, missing/wrong token paths, table empty/error
  states, disable/re-enable conflict display, stock maintenance confirmation, lint/type checks.
- Handoff: report admin route layout, token storage behavior, CORS status, and any endpoints left as optional.

## Phase 7: Final Integration, Demo, Testing, Design, And Accessibility Polish

- Suggested branch/worktree: `codex/frontend-integration-polish`.
- Required skills: `webapp-testing`, `accessibility`, `web-design-guidelines`, `frontend-design`,
  `native-data-fetching`.
- Purpose: integrate merged feature phases into a polished FYP demo build.
- Prerequisites: all selected implementation phases merged.
- Likely changes: small UI polish, copy fixes, loading/empty/error improvements, accessibility fixes, test checklist
  notes, demo script updates.
- Must not change: backend source/tests, API contracts, package files unless a user explicitly scopes a testing
  dependency, `.agents`, `skills-lock.json`.
- Endpoints involved: all implemented frontend endpoints through normal user/admin flows.
- Dependency policy: do not install dependencies by default.
- Scope: run `docs/frontend/frontend-testing-checklist.md`, Expo Go checks, Expo Web admin checks,
  keyboard/accessibility
  review, visual consistency review, delayed-data and no-price payload checks, final demo story.
- Out of scope: large redesigns, new features, separate admin project, backend CORS implementation.
- Acceptance: demo story works end to end, route guards hold, duplicate-submit guards hold, delayed market data wording
  is correct, AI disclaimers are visible, admin token remains session-only, and web/mobile layouts pass HCI basics.
- Verification: frontend lint/type checks, Expo Go smoke test, Expo Web smoke test, Playwright checks where practical,
  manual accessibility checklist, final `git diff --check`.
- Handoff: report demo readiness, remaining risks, browser/device coverage, and any backend/environment blockers.

## Frontend Quality Gate Checklist

- Expo Router routes match documented route groups and every route file exports a screen component.
- TypeScript remains strict with no `any` added unless justified.
- API modules use `EXPO_PUBLIC_API_BASE_URL`, normalize errors, and keep backend as source of truth.
- Basic Auth credentials and admin token are cleared on logout; admin token is session-only.
- Stock UI prefers delayed fields and labels data as delayed educational market data.
- `1D` history uses backend points only; no frontend candle filling.
- Paper-trading buy/sell payloads never include price.
- AI screens use educational wording and disclaimers.
- Loading, empty, error, cooldown, confirmation, and duplicate-submit states are implemented.
- Mobile touch targets are around 44x44 or larger; admin web supports keyboard-friendly navigation.
- Mobile forms keep focused inputs visible above the keyboard and preserve entered values after recoverable errors.
- Mobile page headers respect safe-area and Dynamic Island insets; page identity is never hidden under system UI.
- Fit-to-screen mobile pages do not casually scroll or bounce; scrolling is reserved for overflow and keyboard avoidance.
- Onboarding-style mobile quizzes use one-question-at-a-time progress and hide duplicated option label/description text.
- Onboarding quiz actions stay fixed at the bottom safe area, selected answers avoid overflow-prone text badges, and
  final submit shows a clear processing state with a non-exact saving indicator.
- Auth screens validate client-side field formats that mirror backend constraints before sending API requests.
- Account forms use no-guesswork recovery: keep long-form page context visible and attach backend-known duplicate or
  validation errors to the exact field whenever possible.
- After an invalid submit, account forms live-validate until all fields are valid; disabled submit buttons must be
  visually disabled rather than active-looking.
- Final processing states use user-friendly status copy and avoid implementation disclaimers that add cognitive load.
- Onboarding submit responses must not be blocked by post-onboarding AI suggestion generation; long-running follow-up
  work should run in the backend as background best-effort work, and frontend timeout/state-mismatch recovery should refresh
  account state before asking the user to retry.
- Forgot-password affordances remain disabled or future-scoped until a backend reset-token or OTP flow is explicitly
  designed.
- Expo Go and Expo Web checks are run where the phase requires them.
