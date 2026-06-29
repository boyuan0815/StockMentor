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
- `(user)/suggestions/index`: `Suggestions`
- `(user)/paper-trading/index`: `Portfolio`
- `(user)/profile`: `Profile`
- `(user)/stocks/search`: `Search`

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

- Backend: `GET /api/watchlist`, `POST /api/watchlist/{symbol}`, `DELETE /api/watchlist/{symbol}`,
  `PATCH /api/watchlist/reorder`, and `POST /api/watchlist/batch-remove`.
- Tab label: `Watchlist`; page title: `Watchlists`.
- Sections: compact title/logo row, icon-only search and refresh, market tabs `All`, `US`, `HK`, `MY`, market notice
  marquee, and a full-width watchlist table.
- Rows: `No.`, `Symbol`, `Price`, `Chg %`; no row hearts; sortable `Symbol`, `Price`, and `Chg %`.
- After the final watchlist row, show lightweight `Add Symbol` and `Edit List` actions. `Add Symbol` opens the Search
  tab. `Edit List` opens the stacked watchlist edit route.
- Empty: concise copy such as "No watchlist stocks yet. Search stocks and tap the heart to add one."
- Edit route: header title is `All`; back and Done both save the full ordered symbol list before closing. Use
  checkboxes for batch remove, `Top` for move-to-top, and drag reorder through the edit list. While search is active,
  selection/removal remains available but `Top` and drag reorder are disabled with `Clear search to reorder.` copy.
- Guardrail: refresh on focus with in-flight request protection; disable add/remove while pending; do not show raw
  backend source/status/time in rows.

### Stock List

- Backend: `GET /api/stocks`; the page also reads `/api/paper-trading/portfolio` for the paper portfolio card.
- Use case: US009 View Stock Market Data.
- Tab label: `Stocks`; page title: `Stocks`.
- Sections: fixed StockMentor-logo header row, icon-only search/refresh, market tabs `US`, `MY`, `HK`, market notice
  marquee, portfolio summary card, and a full-width supported-stock table.
- Rows: `No.`, `Symbol`, `Price`, `Chg %`, `Action`.
- Empty: no stored stock data message for `US`; `MY` and `HK` show planned-state copy only.
- Portfolio card: a centered rounded static-tier paper card shows `Net Assets · USD`, backend `todayProfitLoss`, a
  confirmation-protected `Reset` chip, `Portfolio >`, and balanced `AI Picks` / `Watchlist` actions. `AI Picks` is a
  placeholder and must not call `/api/stocks/ai-suggestions`. Stock rows still render if portfolio loading fails.
- Intended display: show backend-provided `displayedPrice`, `displayedPercentChange`, `displayedMarketTime`,
  `targetDisplayMarketTime`, `priceFreshnessStatus`, and `priceSource`.
- Legacy `currentPrice`, `percentChange`, and `lastUpdated` are compatibility fields, not preferred display fields.
- Guardrail: list is read-only and must not call Twelve Data or invent delayed prices. Do not show row-level raw
  `priceSource`, freshness enum values, or market time. The `Practice Trade`/paper action opens a guarded practice
  buy ticket and must not execute trades directly or trigger row navigation.
- Refresh: soft refresh on focus and minute-boundary refresh while focused should keep current rows visible and update
  only when the backend response returns.

### Search

- Backend: `GET /api/stocks`, plus watchlist add/remove endpoints for active-result heart toggles.
- Tab route: `(user)/stocks/search`.
- Contextual route: `(user)/stocks/search-context`, opened from Watchlist, Stocks, or Detail with origin params.
- Sections: back arrow, search input, `Search` action, search history chips, and max three `Latest Viewed Stocks`.
- The Search tab remains the rightmost real tab with a visual separator; it keeps the search row as its header.
- Empty input: do not show the full supported stock list. If no latest viewed stocks exist, show a small empty state.
- Typed search: searches supported stocks by symbol/company and shows quote rows with symbol/name plus heart toggle only.
- Guardrail: use safe storage for search history and latest viewed stocks; storage failure must not red-screen.
- Refresh: typed quote results can soft refresh while the Search screen stays focused; local input/history state must not
  reset.

### Stock Detail

- Backend: `GET /api/stocks/{symbol}`, `GET /api/stocks/{symbol}/history`.
- Use case: US009 View Stock Market Data.
- Sections: fixed dynamic header, market notice marquee, compact quote panel, interactive history chart, AI explanation
  drawer, and practice trade footer.
- Fixed header states:
  1. blank title area at top;
  2. symbol/company only after the main identity block is fully covered;
  3. symbol plus compact quote only after the main price block is fully covered.
- Header thresholds should be based on measured block bottoms, not block starts.
- Quote panel: symbol/company, market status, displayed price, tight `▲`/`▼` direction marker, absolute change, percent
  change, High, Low, and full Volume when backend fields are present.
- Use `priceFreshnessStatus`, `isPriceAvailable`, and `dataNote` for unavailable or fallback delayed data states.
- Chart: visible timeframes are `1D`, `5D`, `1M`, `3M`, `YTD`, and `1Y`; `7D` is hidden from the frontend selector.
  `1D` and `5D` are line-only. Daily timeframes show a line/candle toggle only when backend
  `candlestickSupported=true`.
- Empty: empty chart state if backend returns no points.
- Guardrail: chart must use backend points exactly, never synthesize missing rows, and never fake candlesticks by copying
  close into open/high/low. Normal page scroll takes priority; selected point details appear only during intentional
  chart focus/long-press and update the chart overlay only, not the main quote header.
- Detail `highPrice`/`lowPrice` describe the displayed/latest day range. `analysisDataSource`, `snapshotHighPrice`,
  `snapshotLowPrice`, and `snapshotTimeframe` describe the latest analysis snapshot.
- `1D` history can return stored intraday chart rows even when quote metadata uses daily fallback during pre-open.
- Footer: transparent wrapper, brand navy `#052344` button labeled `Practice Trade`; it opens the guarded practice buy
  ticket and does not execute directly.
- Guardrail: clear old detail/history/AI state on symbol change so previous-stock content never flashes.
- Refresh: stock detail soft refresh updates quote/detail and visible history/recent returned points together. Do not
  auto-refresh AI explanation drawer content.

### AI Explanation

- Backend: `GET /api/stocks/{symbol}/ai-explanation?timeframe=1D|5D|1M|3M` for the current frontend drawer.
  Backend-compatible `7D` may still exist, but the visible chart/explanation UI uses `5D`.
- Use case: US012 View AI Stock Explanation.
- Sections: drawer row titled `View AI Stock Explanation` or `Close AI Stock Explanation`, educational disclaimer,
  explanation, and data window.
- Empty: unavailable state if `available=false`.
- Guardrail: never frame output as financial advice. Fetch only when the drawer is opened. Do not request `YTD` or
  `1Y`; show unsupported copy instead. Hide backend cache/generated status messages such as "Returned cached AI
  explanation".

### AI Suggestions

- Backend: `GET /api/stocks/ai-suggestions`, `POST /api/stocks/ai-suggestions/refresh`,
  `PATCH /api/stocks/ai-suggestions/items/{itemId}/dismiss`, and
  `PATCH /api/stocks/ai-suggestions/items/{itemId}/watchlist`.
- Sections: batch summary, cooldown, suggestion cards, remaining stocks, fallback note.
- Empty: no cached suggestions, show refresh if allowed.
- Guardrail: disable refresh when `refreshAllowed=false`; show `nextRefreshAllowedAt`.

### Portfolio / Paper Trading

- Backend: account, portfolio, buy, sell, reset, transactions endpoints under `/api/paper-trading`.
- Use case: US010 Simulate Paper Trades.
- Visible tab label: `Portfolio`; internal route remains `/paper-trading`.
- Bottom-tab entry always opens `Assets` at the top. `/paper-trading?tab=history` and `/paper-trading/transactions`
  intentionally open `History`.
- Sections: fixed Portfolio header, fixed `Assets` and `History` top tabs, account/portfolio summary, valuation
  warnings, positions table, stock-scoped guarded buy/sell ticket, reset bottom sheet, transaction list, and
  transaction detail.
- Assets: show `Net Assets · USD`, Holdings Value, Today's P/L, Today's P/L %, optional Remaining Cash, Fees Paid,
  total `P/L`, Session/Last reset, `View Stocks`, and `Reset Portfolio`. Main Portfolio `P/L` uses backend
  `totalProfitLoss`; Today's P/L uses backend `todayProfitLoss`.
- Empty: no positions yet, show `View Stock Page`; buy entry comes from Stock rows/detail, not a generic portfolio buy
  selector.
- Guardrail: every buy, sell, and reset requires confirmation. BUY/SELL request bodies must contain only `symbol` and
  numeric `quantity`; the frontend must not send or invent price, amount, fee, or max quantity.
- Quantity: trim, require `/^[1-9]\d*$/`, reject decimals/scientific notation/zero/negative values, convert with
  `Number(trimmedQuantity)` only after validation, and do not use `parseInt` coercion.
- Trade ticket: always stock-scoped. Buy and Sell share one selected-stock ticket layout with `Net Assets · USD`, value,
  and small US flag. If no route symbol is provided, show an invalid-entry state instead of an all-stock picker or
  generic holdings selector.
- Sell ticket: enabled only when the selected stock is held. It supports Partial and All modes and clamps pasted values
  greater than or equal to the holding into All mode. If holding quantity is `1`, All is forced, Partial is disabled,
  quantity is locked at `1`, and plus/minus controls render disabled.
- Estimates: fee, amount, and max quantity are display-only UI estimates. Confirmation may show fee-aware estimate rows,
  but backend remains authoritative and the request body is still only `symbol` and `quantity`.
- Submit success: successful buy/sell redirects to `/paper-trading?tab=history`.
- Reset: use the reset-card bottom sheet with dim backdrop and slide-up/down behavior. The sheet says simulated cash
  returns to starting balance, open positions are cleared, a new session starts, and the action cannot be undone.
- History: `/paper-trading/transactions` opens/reuses the `History` tab. Load paged transactions with default
  `page=0`, `size=20`, and `currentSessionOnly=true`; `Load more` appends later pages. Side `ALL` omits the backend
  side filter, exact supported symbols may be sent to the backend, and partial search remains local over loaded rows.
  Show `Action`, `Stock`, `Price/Qty`, and `P/L`. Handle `RESET` / `symbol=null` rows as portfolio/session reset
  records without stock links or price assumptions.
- Price concept: practice trades must use the backend-decided delayed stored price. The frontend must not send or invent
  execution price.
- Refresh: Portfolio and History soft refresh on focus and minute boundary without clearing visible data, scroll/input
  state, selected tab, filters, or confirmation state.

### Profile / Settings / Logout

- Backend: `GET /api/user/profile`, local logout.
- Sections: account summary, investment profile, behavior summary, retake onboarding, logout.
- Guardrail: logout clears credentials, admin token, query cache, and storage abstraction state.

## Mobile Interaction Rules

- Use readable type and spacing.
- Touch targets should be around 44x44 or larger.
- Buttons must describe the action, such as "Start practice buy" or "Reset simulated portfolio".
- Pull-to-refresh only on safe read-only screens.
- Auto-refresh may run only for the focused visible screen. It targets the latest stored delayed backend snapshot, not
  live quotes, and must keep existing values visible unless there is no usable first-load data.
- Pending mutations disable related buttons and inputs.
- Stock/context navigation must pass explicit return params so back actions are deterministic:
  Stocks -> Detail -> Stocks, Watchlist -> Detail -> Watchlist, Search tab -> Detail -> Search tab, and contextual
  search -> Detail -> contextual search.
