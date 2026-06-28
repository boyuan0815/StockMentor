# Frontend Implementation Roadmap

This roadmap is a high-level view of the current StockMentor frontend state. Detailed fresh-chat prompts live in
`docs/frontend/frontend-phase-prompts.md`.

## Completed

### Foundation

- Expo Router shell.
- API client core.
- Session/auth providers.
- Theme tokens and shared layout primitives.
- Route guards and placeholders.

### Auth, Onboarding, Profile

- Welcome, register, login.
- Basic Auth bootstrap through `/api/auth/me`.
- Beginner/admin role routing.
- Onboarding quiz, onboarding result, profile, retake, and logout.

### Stock Learning

- Watchlist, Stocks, Search, stock detail.
- Delayed educational market data display.
- Market notice marquee strips.
- Watchlist add/remove.
- Search history and latest-viewed stocks through safe storage.
- On-demand AI stock explanation.

### Interactive Charts

- Stock detail chart timeframes shown in the frontend: `1D`, `5D`, `1M`, `3M`, `YTD`, `1Y`.
- Backend-compatible `7D` is hidden from the frontend chart selector.
- `1D` and `5D` are line-only.
- Daily timeframes can show candle mode only when backend `candlestickSupported=true`.
- Chart uses backend points exactly and does not synthesize missing rows or fake OHLC.

### Watchlist Edit

- Separate stacked `/watchlist/edit` route.
- Search, checkbox selection, select all, move-to-top, drag reorder, batch remove.
- Backend `PATCH /api/watchlist/reorder` and `POST /api/watchlist/batch-remove`.

### Portfolio And Paper Trading

- Portfolio tab with `Assets` and `History`.
- Main portfolio labels use backend fields:
  - `P/L` = `totalProfitLoss`
  - `Today's P/L` = `todayProfitLoss`
  - `Today's P/L %` = `todayProfitLossPercent`
  - `Fees Paid` = `totalFeesPaid`
- Stock-scoped buy/sell tickets.
- BUY/SELL payload remains `{ symbol, quantity }`.
- Reset-card bottom sheet.
- Paged transaction History using `GET /api/paper-trading/transactions/page` with default `size=20`.

## Partially Completed

### AI Suggestions

- Suggestions tab exists.
- Full cached suggestions list, refresh cooldown UI, dismiss, and add-to-watchlist actions are still pending.
- Next recommended feature phase: complete AI Suggestions.

## Pending

### Admin Web/Tablet Console

- Admin shell/placeholders exist.
- Full dashboard, users, AI monitoring, refresh jobs, and stock maintenance screens remain pending.

### Final Integration And Accessibility Polish

- Full manual regression, Expo mobile/web checks, accessibility review, and demo polish remain pending.

## Deferred Or Out Of Scope

- MY/HK real market data.
- Real broker trading, deposits, margin, options, news, community, competitions, and real-money behavior.
- Direct frontend OpenAI or Twelve Data calls.
- Additional dependencies unless a future task explicitly approves them.
- Separate admin web project.

## Backend Contract Reminders

- US009 stock endpoints are read-only and use stored backend data.
- History supports `1D`, `5D`, retained `7D`, `1M`, `3M`, `YTD`, and `1Y`.
- Frontend chart UI shows `5D`, not `7D`.
- AI explanation UI supports `1D`, `5D`, `1M`, and `3M`; `YTD`/`1Y` are unsupported in the drawer.
- US010 paper trading uses backend-decided delayed stored price metadata.
- Frontend paper trade writes send only `symbol` and `quantity`.
