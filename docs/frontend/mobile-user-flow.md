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

- `(user)/index`: home dashboard
- `(user)/stocks/index`
- `(user)/suggestions/index`
- `(user)/paper-trading/index`
- `(user)/profile`

Detail and action routes:

- `(user)/stocks/[symbol]`
- `(user)/stocks/[symbol]/explanation`
- `(user)/paper-trading/buy`
- `(user)/paper-trading/sell`
- `(user)/paper-trading/transactions`
- `(user)/paper-trading/transactions/[transactionId]`

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

### Home Dashboard

- Backend: read-only summaries from profile, suggestions, portfolio, and watchlist/stock surfaces.
- Sections: learning greeting, profile card, suggestion preview, simulated portfolio preview, watchlist shortcut.
- Empty: show starter prompts.
- Guardrail: do not trigger AI refresh or paper-trading actions automatically.

### Stock List

- Backend: `GET /api/stocks`.
- Use case: US009 View Stock Market Data.
- Sections: supported stock list, search/filter, risk/trend/volatility labels, watchlist marker, delayed data badge/note.
- Empty: no stored stock data message.
- Intended display: show backend-provided `displayedPrice`, `displayedPercentChange`, `displayedMarketTime`,
  `targetDisplayMarketTime`, `priceFreshnessStatus`, and `priceSource`.
- Legacy `currentPrice`, `percentChange`, and `lastUpdated` are compatibility fields, not preferred display fields.
- Guardrail: list is read-only and must not call Twelve Data or invent delayed prices.

### Stock Detail

- Backend: `GET /api/stocks/{symbol}`, `GET /api/stocks/{symbol}/history`.
- Use case: US009 View Stock Market Data.
- Sections: delayed price summary, displayed market time from backend delayed metadata, last backend update time when
  available, simple chart, timeframe tabs, risk/trend labels, data/fallback note, watchlist action, AI explanation
  action, practice trade action.
- Use `priceFreshnessStatus`, `isPriceAvailable`, and `dataNote` for unavailable or fallback delayed data states.
- Empty: empty chart state if backend returns no points.
- Guardrail: chart must show timeframe and data/fallback notes when available. The UI may refetch about once per displayed minute during market display hours, but it must not imply immediate market data.
- Detail `highPrice`/`lowPrice` describe the displayed/latest day range. `analysisDataSource`, `snapshotHighPrice`,
  `snapshotLowPrice`, and `snapshotTimeframe` describe the latest analysis snapshot.
- `1D` history can return stored intraday chart rows even when quote metadata uses daily fallback during pre-open.
- Opening copy: "Today's delayed market display starts around 9:45 AM New York time."
- Missing data copy: "Some minute-level data may be missing because provider data and free-tier retrieval are delayed."

### AI Explanation

- Backend: `GET /api/stocks/{symbol}/ai-explanation?timeframe=7D`.
- Use case: US012 View AI Stock Explanation.
- Sections: educational disclaimer, explanation, data note, cached/available status.
- Empty: unavailable state if `available=false`.
- Guardrail: never frame output as financial advice.

### AI Suggestions

- Backend: `GET /api/stocks/ai-suggestions`, `POST /api/stocks/ai-suggestions/refresh`.
- Sections: batch summary, cooldown, suggestion cards, remaining stocks, fallback note.
- Empty: no cached suggestions, show refresh if allowed.
- Guardrail: disable refresh when `refreshAllowed=false`; show `nextRefreshAllowedAt`.

### Watchlist

- Backend: `GET /api/watchlist`, `POST /api/watchlist/{symbol}`, `DELETE /api/watchlist/{symbol}`.
- Sections: watchlisted stocks, price/risk labels, remove action.
- Empty: encourage adding stocks from stock detail.
- Guardrail: disable add/remove while pending.

### Paper Trading

- Backend: account, portfolio, buy, sell, reset, transactions endpoints under `/api/paper-trading`.
- Use case: US010 Simulate Paper Trades.
- Sections: simulated cash, total portfolio value, realized/unrealized P/L, fees, positions, recent transactions.
- Empty: no positions yet, show browse stocks CTA.
- Guardrail: every buy, sell, and reset requires confirmation.
- Price concept: practice trades must use the backend-decided price. The frontend must not send or invent price.
- Backend buy/sell uses the same delayed stored market-data selector concept used by stock display.
- Required copy: "Practice trade uses StockMentor's delayed stored price, not a live market quote."

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
