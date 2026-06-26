# StockMentor Frontend Design Blueprint

## Purpose

This document is the source of truth for the StockMentor frontend design direction before screen implementation starts.
It should be read with:

- `docs/frontend/backend-api-screen-map.md`
- `docs/frontend/mobile-user-flow.md`
- `docs/frontend/admin-web-flow.md`
- `docs/frontend/design-system.md`
- `docs/frontend/api-integration-guide.md`
- `docs/frontend/interaction-guardrails.md`
- `docs/frontend/frontend-environment-guide.md`

## Accepted Frontend Direction

- Language: TypeScript.
- Stack: React Native + Expo + Expo Router.
- Codebase: one Expo codebase for the MVP.
- Beginner investor app: mobile-first, optimized for phone-sized screens.
- Admin console: web/tablet-first using Expo Web / React Native Web.
- Admin is not a separate Vite, Next.js, or React web project for the MVP.
- The Spring Boot backend is the only source of app data.

The current `frontend/` folder has the Phase 1 StockMentor route shell and API core, the landed Phase 2B/2.5 account
experience, and the Phase 3B stock-learning UI: Watchlist, Stocks, Search, stock detail, history summary/list,
watchlist actions, safe storage for search history/latest viewed stocks, and on-demand AI explanation drawer. Future
frontend work should build on that foundation rather than returning to Expo starter routes or older card-heavy stock
screens.

## Final Use Case Map

- US001 Register Account.
- US002 Login Account.
- US003 Manage System Users.
- US004 Complete Onboarding Quiz.
- US005 View User Investment Profile.
- US006 Show AI Stock Suggestions.
- US007 Monitor and Maintain AI Stock Suggestions.
- US008 Maintain Stock Market Data.
- US009 View Stock Market Data.
- US010 Simulate Paper Trades.
- US011 Automatically Retrieve Stock Market Data.
- US012 View AI Stock Explanation.

Frontend use-case boundaries:

- US008 is admin stock market data maintenance: backfill, repair, and cleanup for stored market data.
- US009 is beginner stock market data viewing: stock list, stock detail, chart/history.
- US010 is simulated paper trading.
- US011 is backend scheduler/system behavior, not normally a direct frontend screen.
- US012 is user-facing AI stock explanation.
- There is no current admin AI explanation screen because the backend does not expose an admin endpoint for it.

## Product Character

StockMentor is a beginner-focused stock learning and paper-trading application. The UI should feel calm, educational,
trustworthy, and simple. It should not feel like a real-money trading app or pressure users into trades.

Use language such as:

- educational suggestion
- learning explanation
- simulated portfolio
- practice trade
- paper-trading practice

Avoid language such as:

- buy now
- best stock
- guaranteed profit
- real investment advice
- will rise
- sure win

## Frontend Boundaries

The frontend must not:

- call OpenAI directly
- call Twelve Data directly
- calculate AI stock suggestions
- calculate AI explanations
- calculate behavior profiles
- bypass backend validation
- send frontend-owned `userId` for current-user actions
- silently use mock responses for real app flows

The frontend may use mock data only for isolated UI previews or temporary development. Mock data must be clearly marked
and removed before final integration.

## 15-Minute Delayed Educational Market Data

StockMentor's intended stock display model is 15-minute delayed educational market data, not immediate market data.

Concept:

```text
displayedMarketTime = current New York market time - 15 minutes

9:45 AM NY time displays 9:30 AM stored candle/data
9:46 AM NY time displays 9:31 AM stored candle/data
9:47 AM NY time displays 9:32 AM stored candle/data
```

Reason:

- Twelve Data free-tier and scheduler limits mean StockMentor should not promise immediate market quotes.
- Twelve Data intraday granularity is 1-minute, not second-by-second updates.
- The backend scheduler fetches around every 5 minutes, and provider data may not be immediately available.
- A stable 15-minute display delay gives the backend time to store the relevant 1-minute candles and lets the frontend
  show a smoother educational display that can advance one displayed minute at a time when data exists.

UI language:

- "15-minute delayed educational market data"
- "Displayed market time"
- "Prices shown are delayed by about 15 minutes"
- "Updated display minute: 9:31 AM NY time"
- "Latest stored delayed price"
- "Practice trades use StockMentor's delayed stored price, not a live market quote"

Current backend contract:

- Stock list/detail/history DTOs expose delayed fields such as `displayedPrice`, `displayedAbsoluteChange`,
  `displayedPercentChange`, `previousClose`, `displayedMarketTime`, `targetDisplayMarketTime`, `dataDelayMinutes`,
  `priceFreshnessStatus`, `priceFreshnessLabel`, `isPriceAvailable`, `isTradeExecutable`, `dataNote`, `priceSource`,
  `marketTimeZone`, and where applicable `lastBackendUpdatedAt`.
- Frontend display should prefer delayed fields over legacy `currentPrice`, `percentChange`, and `lastUpdated`.
- Stock detail `dataSource` is a legacy analysis-source field; `analysisDataSource`, `snapshotHighPrice`,
  `snapshotLowPrice`, and `snapshotTimeframe` describe the latest analysis snapshot.
- `highPrice` and `lowPrice` describe the displayed/latest day range selected by the delayed market view.
- Stock detail may expose `previousClose`, `displayedAbsoluteChange`, and `displayedVolume`; show these only when the
  backend returns them.
- Frontend implementation must not invent trusted prices, displayed market times, absolute changes, or percent changes.
- Backend movement is previous-close based. Use backend `displayedAbsoluteChange` and `displayedPercentChange` directly.

Opening and closing behavior:

- From 9:30 to 9:44 AM New York time, current-day delayed intraday display is not ready because
  `current time - 15 minutes` is before market open. Show: "Today's delayed market display starts around 9:45 AM New
  York time."
- After 4:15 PM New York time, the delayed display can show the 4:00 PM market close data if available, then remain as
  latest delayed/stored market data until the next trading day.

## Role Routing

Auth bootstrap is owned by the frontend shell:

1. No credentials: show welcome, login, and register routes.
2. Credentials available: call `GET /api/auth/me`.
3. `role = ADMIN`: route to the admin web shell/dashboard, then require admin token before admin API calls.
4. `role = BEGINNER_INVESTOR` and `mustCompleteOnboarding = true`: route to onboarding.
5. `role = BEGINNER_INVESTOR` and onboarding complete: route to the beginner dashboard.
6. Logout clears Basic Auth credentials, admin token, query cache, and auth storage abstraction state.

Beginner routes must not expose admin-only actions. Admin routes must reject normal users.

## Route Group Direction

Use Expo Router route groups so native and web can share code while keeping layouts clear:

```text
frontend/app/
  _layout.tsx
  (public)/
    index.tsx
  (auth)/
    login.tsx
    register.tsx
  (onboarding)/
    onboarding/index.tsx
    onboarding/result.tsx
  (user)/
    _layout.tsx
    dashboard.tsx
    stocks/index.tsx
    stocks/[symbol].tsx
    stocks/[symbol]/explanation.tsx
    suggestions/index.tsx
    paper-trading/index.tsx
    paper-trading/buy.tsx
    paper-trading/sell.tsx
    paper-trading/transactions.tsx
    profile.tsx
  (admin)/
    _layout.tsx
    admin.tsx
    admin/users/index.tsx
    admin/users/[userId].tsx
    admin/stocks/maintenance.tsx
    admin/ai-suggestions/index.tsx
    admin/ai-suggestions/batches/[batchId].tsx
    admin/ai-suggestions/jobs/[jobId].tsx
```

Routes belong in `app/`. Components, types, utilities, API functions, hooks, and theme tokens should live outside
`app/`.

## Beginner Mobile Experience

After login and onboarding, beginner users should see a simple tab-based shell:

- Watchlist
- Stocks
- Suggestions
- Portfolio
- Profile
- Search

Stacks inside tabs should open detail screens for stock detail, AI explanation, buy/sell tickets, transaction detail,
onboarding retake, and settings.

Phase 3B stock UI conventions:

- Watchlist page title is `Watchlists`; it uses the StockMentor logo, icon-only search/refresh, market tabs
  `All`, `US`, `HK`, `MY`, and a full-width table with `No.`, `Symbol`, `Price`, `Chg %`.
- Stocks page title is compact `Stocks`; it uses market tabs `US`, `MY`, `HK` and a table with
  `No.`, `Symbol`, `Price`, `Chg %`, `Action`.
- Watchlist, Stocks, Suggestions, Portfolio, and Profile use fixed StockMentor-logo headers; Search keeps the search-row
  header and is visually separated as the rightmost tab.
- Search is both a visible bottom tab and a hidden contextual route `/stocks/search-context`. Empty search shows Search
  History and max three Latest Viewed Stocks, never the full supported stock list.
- Stock detail uses a dynamic fixed header: blank at top, identity only after the main identity block is covered, and
  symbol plus compact quote only after the main price block is covered.
- Market notice copy is a shallow-red marquee strip that preserves the full backend-derived sentence with no string
  slicing or ellipsis.
- Toasts are centered dark-navy layers with white text and auto-dismiss behavior.
- Practice-trade CTAs open guarded stock-scoped US010 tickets and do not execute directly. Tickets have no internal
  stock picker or generic holdings selector, and buy/sell requests send only `symbol` and numeric `quantity`.

Mobile HCI rules for account and quiz flows:

- Screens should avoid casual scroll/bounce when content fits the viewport. Use scroll only when content exceeds the
  screen or when the software keyboard requires it.
- Mobile page identity must respect the iOS safe area and Dynamic Island. Fixed or sticky headers belong below the top
  inset and must not overlap the page content.
- Forms must be keyboard-aware so the active input remains visible, selectable, editable, and paste-friendly.
- Auth form headers should share the keyboard-aware scroll context or otherwise prove they cannot overlap iOS system
  UI, safe areas, or focused fields.
- After the first invalid submit, forms should live-validate edited fields until every field is valid and the submit
  button can clearly re-enable.
- Recoverable errors must stay visible on the same screen, preserve user input, and explain the next action.
- Design account recovery states for beginner users who should not need to guess what went wrong. Prefer field-level
  errors for the exact field when the backend can identify it, keep page context visible in longer forms, and remove
  explanations that add cognitive load without helping the user recover.
- Disabled submit buttons must look disabled, not like active primary actions with only reduced opacity.
- Password visibility controls should use a clear accessible eye/eye-off affordance when the project already has icon
  support.
- Onboarding-style quizzes should show one question at a time on mobile, with visible progress, Back/Next controls, and
  all-answer validation before submit.
- Quiz action buttons should stay fixed at the bottom safe area while the question content scrolls only when needed.
- Question changes may use a subtle quick slide/fade transition; avoid slow or distracting motion.
- During final quiz submit, replace the quiz with a clear processing state and a non-exact saving indicator rather than
  relying only on button text.
- Onboarding completion should treat timeout or account-state mismatch as recoverable: refresh the authenticated user
  state and leave the quiz when the backend has already completed onboarding.
- Selected option cards should use background, border, or a compact check affordance. Avoid text badges that can
  overflow on small screens.
- Do not display duplicated option text twice. If an option description repeats the label, show only the label.
- Dev diagnostics must stay development-only and must never expose passwords, Basic Auth values, authorization headers,
  admin tokens, request bodies, API keys, or secrets.
- Forgot-password UI must stay future-scoped until the backend has a reset-token or OTP design with email delivery,
  expiry, cooldown/rate limiting, and abuse protection.

## Admin Web/Tablet Experience

Admin uses the same Expo codebase but a different layout:

- side navigation on tablet/web widths
- table/list views
- filters
- detail panels/pages
- confirmation modals
- maintenance action forms

On phone-sized screens, show: "Admin console is best viewed on tablet or web." Allow logout and optionally show minimal
read-only status links. Do not cram full admin tables, filters, or destructive maintenance actions into phone UI.

## State Ownership

Use this ownership model during implementation:

- React Query later for server state, caching, loading state, invalidation, and request deduplication.
- `AuthSessionProvider` for memory-only Basic Auth/session state, role routing, onboarding mode, admin token state, and logout cleanup.
- `ThemeProvider` for theme mode and design tokens.
- Local component state for forms, modals, quantity inputs, filters, and confirmation dialogs.
- No Redux, Zustand, or broad global store unless a clear implementation need appears later.

## History And Chart Direction

Phase 3B ships a compact history summary/list because no chart dependency is part of the approved baseline. A real line
or candlestick chart requires a separate dependency task.

- Show selected timeframe.
- Show data source and fallback notes when available.
- Do not add advanced trading indicators.
- Candlestick charts are optional only if easy later.
- The history display should help beginners understand movement, not simulate a professional trading terminal.
- During the active delayed display window, the latest visible intraday point should normally be no later than the
  backend-decided displayed market time, about 15 minutes behind current New York market time.
- The frontend must not silently invent or fill missing 1-minute candles.

## FYP Demo Story

Use this sequence for the main demonstration:

1. Register or login.
2. Complete onboarding.
3. View investment profile.
4. View stock list and stock detail.
5. View AI stock explanation.
6. View AI stock suggestions.
7. Add a stock to watchlist or perform a practice trade.
8. View portfolio and transactions.
9. Login as admin.
10. Manage users.
11. Monitor or maintain stock and AI data.

## UI Library Decision

Use custom lightweight StockMentor components first. Do not install a large UI kit during planning. Add UI or chart
libraries later only if implementation proves a clear need.

## Documentation Rule

Future Codex chats should read the docs in `docs/frontend/` before frontend work. For skill usage, see
`docs/frontend/frontend-skill-usage-guide.md`.
