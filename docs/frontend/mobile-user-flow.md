# Mobile Beginner Investor Flow

The beginner investor experience is mobile-first. The first complete implementation should prioritize phone-sized screens and Expo Go testing before custom native builds.

## Navigation Shape

Public and setup routes:

- `(public)/index`: welcome
- `(auth)/login`
- `(auth)/register`
- `(onboarding)/index`
- `(onboarding)/result`

Authenticated beginner tabs:

- `(user)/dashboard`: `Watchlist`
- `(user)/stocks/index`: `Stocks`
- `(user)/stocks/search`: `Search`
- `(user)/suggestions/index`: `Suggestions`
- `(user)/paper-trading/index`: `Practice`
- `(user)/profile`: `Profile`

Detail and action routes:

- `(user)/stocks/[symbol]`
- `(user)/stocks/search-context`
- `(user)/stocks/[symbol]/explanation` compatibility route with no AI auto-fetch
- `(user)/paper-trading/buy`
- `(user)/paper-trading/sell`
- `(user)/paper-trading/transactions`
- `(user)/paper-trading/transactions/[transactionId]`

Hidden routes in the tab layout should use `href: null`. Contextual routes must pass explicit return params instead of
depending on tab history.

## Screen Responsibilities

### Welcome

- Purpose: introduce StockMentor as a learning and paper-trading app.
- Primary actions: login, register.
- Secondary actions: none for MVP.
- Guardrail: do not show trading pressure or real-money wording.

### Register

- Backend: `POST /api/auth/register`.
- Sections: email, username, password, confirm password.
- Loading: disable register button and show "Creating account...".
- Error: show backend validation or duplicate account message.
- Destination: onboarding gate after successful registration or login prompt if the implementation chooses explicit login after register.

### Login

- Backend: `POST /api/auth/login`.
- Sections: email/username, password.
- Loading: disable login button.
- Error: show wrong credentials, inactive/deleted user, or backend unavailable.
- Destination: role routing through returned `AuthUserResponse`, then confirm with `/api/auth/me` on bootstrap.

### Auth Bootstrap

- Backend: `GET /api/auth/me`.
- Purpose: recover route state when app opens or refreshes.
- Loading: splash or full-screen "Checking your session".
- Error: clear credentials and route to login.

### Onboarding Quiz

- Backend: `GET /api/user/onboarding/questions`, `POST /api/user/onboarding`.
- Sections: progress indicator, question text, option cards, next/back, finish.
- Loading: question skeleton; submitting state on finish.
- Empty: "No onboarding questions are available. Try again."
- Guardrail: backend owns questions and scoring; frontend must not send risk score or profile source.

### Onboarding Result / Investment Profile

- Backend: `GET /api/user/profile`.
- Sections: profile version, risk tolerance, goal, experience, volatility, horizon, score summaries, behavior summary if available.
- Empty: route back to onboarding if no investment profile.
- Guardrail: profile is read-only except retake action.

### Watchlist

- Backend: `GET /api/watchlist`, `POST /api/watchlist/{symbol}`, `DELETE /api/watchlist/{symbol}`.
- Tab label: `Watchlist`; page title: `Watchlists`.
- Sections: compact title/logo row, icon-only search and refresh, market tabs `All`, `US`, `HK`, `MY`, market notice
  marquee, and a full-width watchlist table.
- Rows: `No.`, `Symbol`, `Price`, `Chg %`; no row hearts; sortable `Symbol`, `Price`, and `Chg %`.
- Empty: concise copy such as "No watchlist stocks yet. Search stocks and tap the heart to add one."
- Guardrail: refresh on focus with in-flight request protection; disable add/remove while pending; do not show raw
  backend source/status/time in rows.

### Stock List

- Backend: `GET /api/stocks`.
- Use case: US009 View Stock Market Data.
- Tab label: `Stocks`; current page title: `Paper Trade`.
- Sections: compact action row, icon-only search/refresh, market tabs `US`, `MY`, `HK`, market notice marquee, and a
  full-width supported-stock table.
- Rows: `No.`, `Symbol`, `Price`, `Chg %`, `Action`.
- Empty: no stored stock data message for `US`; `MY` and `HK` show planned-state copy only.
- Intended display: show backend-provided `displayedPrice`, `displayedPercentChange`, `displayedMarketTime`,
  `targetDisplayMarketTime`, `priceFreshnessStatus`, and `priceSource`.
- Legacy `currentPrice`, `percentChange`, and `lastUpdated` are compatibility fields, not preferred display fields.
- Guardrail: list is read-only and must not call Twelve Data or invent delayed prices. Do not show row-level raw
  `priceSource`, freshness enum values, or market time. The `Practice Trade`/paper action opens the placeholder route
  only and must not execute trades.

### Search

- Backend: `GET /api/stocks`, plus watchlist add/remove endpoints for active-result heart toggles.
- Tab route: `(user)/stocks/search`.
- Contextual route: `(user)/stocks/search-context`, opened from Watchlist, Stocks, or Detail with origin params.
- Sections: back arrow, search input, `Search` action, search history chips, and max three `Latest Viewed Stocks`.
- Empty input: do not show the full supported stock list. If no latest viewed stocks exist, show a small empty state.
- Typed search: searches supported stocks by symbol/company and shows quote rows with symbol/name plus heart toggle only.
- Guardrail: use safe storage for search history and latest viewed stocks; storage failure must not red-screen.

### Stock Detail

- Backend: `GET /api/stocks/{symbol}`, `GET /api/stocks/{symbol}/history`.
- Use case: US009 View Stock Market Data.
- Sections: fixed dynamic header, market notice marquee, compact quote panel, history summary/list, AI explanation
  drawer, and practice trade footer.
- Fixed header states:
  1. blank title area at top;
  2. symbol/company only after the main identity block is fully covered;
  3. symbol plus compact quote only after the main price block is fully covered.
- Header thresholds should be based on measured block bottoms, not block starts.
- Quote panel: symbol/company, market status, displayed price, tight `▲`/`▼` direction marker, absolute change, percent
  change, High, Low, and full Volume when backend fields are present.
- Use `priceFreshnessStatus`, `isPriceAvailable`, and `dataNote` for unavailable or fallback delayed data states.
- Empty: empty chart state if backend returns no points.
- Guardrail: chart must show timeframe and data/fallback notes when available. The UI may refetch about once per displayed minute during market display hours, but it must not imply immediate market data.
- Detail `highPrice`/`lowPrice` describe the displayed/latest day range. `analysisDataSource`, `snapshotHighPrice`,
  `snapshotLowPrice`, and `snapshotTimeframe` describe the latest analysis snapshot.
- `1D` history can return stored intraday chart rows even when quote metadata uses daily fallback during pre-open.
- Footer: transparent wrapper, brand navy `#052344` button labeled `Practice Trade`, placeholder route only.
- Guardrail: clear old detail/history/AI state on symbol change so previous-stock content never flashes.

### AI Explanation

- Backend: `GET /api/stocks/{symbol}/ai-explanation?timeframe=1D|7D|1M|3M`.
- Use case: US012 View AI Stock Explanation.
- Sections: drawer row titled `View AI Stock Explanation` or `Close AI Stock Explanation`, educational disclaimer,
  explanation, and data window.
- Empty: unavailable state if `available=false`.
- Guardrail: never frame output as financial advice. Fetch only when the drawer is opened. Do not request `YTD` or
  `1Y`; show unsupported copy instead. Hide backend cache/generated status messages such as "Returned cached AI
  explanation".

### AI Suggestions

- Backend: `GET /api/stocks/ai-suggestions`, `POST /api/stocks/ai-suggestions/refresh`.
- Sections: batch summary, cooldown, suggestion cards, remaining stocks, fallback note.
- Empty: no cached suggestions, show refresh if allowed.
- Guardrail: disable refresh when `refreshAllowed=false`; show `nextRefreshAllowedAt`.

### Paper Trading

- Backend: account, portfolio, buy, sell, reset, transactions endpoints under `/api/paper-trading`.
- Use case: US010 Simulate Paper Trades.
- Sections: simulated cash, total portfolio value, realized/unrealized P/L, fees, positions, recent transactions.
- Empty: no positions yet, show browse stocks CTA.
- Guardrail: every buy, sell, and reset requires confirmation.
- Price concept: practice trades must use the backend-decided price. The frontend must not send or invent price.
- Backend buy/sell uses the same delayed stored market-data selector concept used by stock display.
- Required copy: "Practice trade uses StockMentor's delayed stored price, not a live market quote."
- Phase 3B placeholder: practice-trade CTAs may navigate to the placeholder route only. They must not call buy/sell APIs
  or imply execution is available.

### Profile / Settings / Logout

- Backend: `GET /api/user/profile`, local logout.
- Sections: account summary, investment profile, behavior summary, retake onboarding, logout.
- Guardrail: logout clears credentials, admin token, query cache, and storage abstraction state.

## Mobile Interaction Rules

- Use readable type and spacing.
- Touch targets should be around 44x44 or larger.
- Buttons must describe the action, such as "Start practice buy" or "Reset simulated portfolio".
- Pull-to-refresh only on safe read-only screens.
- Pending mutations disable related buttons and inputs.
- Stock/context navigation must pass explicit return params so back actions are deterministic:
  Stocks -> Detail -> Stocks, Watchlist -> Detail -> Watchlist, Search tab -> Detail -> Search tab, and contextual
  search -> Detail -> contextual search.
