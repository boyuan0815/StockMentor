# StockMentor Design System

## Design Intent

StockMentor should feel like a calm learning workspace for beginner investors. It should avoid the adrenaline style of real-money trading apps.

Design signature: a "learning ledger" feel. Use clear rows, soft dividers, gentle status chips, and beginner-friendly explanations. The memorable element should be the combination of simple chart notes plus educational AI disclaimers, not flashy market visuals.

## Color Direction

Use a restrained light-first palette:

- Brand navy: `#052344`
- Primary trust blue: `#2563EB`
- Learning teal: `#0F766E`
- Calm background: `#F8FAFC`
- Surface: `#FFFFFF`
- Text strong: `#0F172A`
- Text muted: `#64748B`
- Border: `#E2E8F0`
- Success data: `#15803D`
- Caution: `#B45309`
- Destructive: `#B91C1C`

Green and red should indicate data movement or destructive actions only. Do not make the whole app feel like a trading terminal.

Phase 3B stock UI uses brand navy for primary practice-trade CTAs and centered toast feedback. Stock movement follows
the US convention: green for increases, red for decreases, and neutral gray for flat or unavailable values. Keep these
colors centralized so a future preference can invert movement colors without hunting hardcoded values.

## Typography

- Use system fonts for MVP.
- Use clear hierarchy: screen title, section title, body, caption/data.
- Use tabular numbers for prices, percentages, quantities, and portfolio values.
- Use sentence case for headings and buttons.
- Important data text should be selectable where useful.

## Spacing And Layout

- Mobile screens use a single-column layout.
- Admin web/tablet screens use side navigation plus content area.
- Prefer consistent gaps and padding over decorative containers.
- Cards should be light, small-radius, and not deeply nested.
- Keep first-screen content useful, not marketing-heavy.

## Component Direction

Build custom lightweight StockMentor components first:

- `Screen`
- `ScrollScreen`
- `PageHeader`
- `SectionHeader`
- `MetricCard`
- `PrimaryButton`
- `SecondaryButton`
- `DangerButton`
- `CooldownButton`
- `ConfirmActionModal`
- `ErrorBanner`
- `EmptyState`
- `SkeletonBlock`
- `RiskPill`
- `TrendPill`
- `AIDisclaimer`
- `StockTableRow`
- `SuggestionCard`
- `TradeTicket`
- `PortfolioSummary`
- `AdminDataTable`

Do not install a large UI kit during planning. Add UI or chart libraries later only if implementation proves a clear need.

Stock browsing screens should favor compact professional table rows over oversized cards when users need to compare
symbols. Phase 3B stock, watchlist, and search fallback tables are full-width table surfaces with thin dividers,
independent columns, compact row height, and no rounded card box per stock row.

Search empty states show `Search History` and at most three `Latest Viewed Stocks`. They must not show the full
supported stock list when the input is empty. Typed search may search all supported backend stocks and render quote rows
with symbol/name plus a heart toggle.

Toast/snackbar feedback should be centralized, centered, non-blocking, auto-dismissed, English-only, and styled as a
dark navy layer with white text and a visible shadow. Watchlist success/remove and refresh-cooldown messages use neutral
toast styling; errors may add a soft danger accent.

## Phase 3B Stock UI Standard

Bottom tabs:

- `Watchlist`
- `Stocks`
- `Suggestions`
- `Portfolio`
- `Profile`
- `Search`

Watchlist page:

- Title is `Watchlists`, with the StockMentor logo when the approved asset exists.
- Search and refresh actions are icon-only.
- Market tabs are `All`, `US`, `HK`, and `MY`; `HK` and `MY` are planned states, not implemented market data.
- Rows do not show heart icons. Columns are `No.`, `Symbol`, `Price`, and `Chg %`.
- `Symbol`, `Price`, and `Chg %` headers are sortable.

Stocks page:

- Title is `Stocks`, compact and aligned with the Watchlist header style.
- Market tabs are `US`, `MY`, and `HK`; only `US` shows the supported stock table.
- Row tap opens stock detail. The practice-trade action opens a guarded buy ticket and does not execute a trade
  directly.
- Table columns are `No.`, `Symbol`, `Price`, `Chg %`, and `Action`.
- The fixed area includes the StockMentor-logo header row, market tabs, market notice, and table header.

Portfolio page:

- Visible bottom tab label is `Portfolio`; internal route remains `/paper-trading`.
- Bottom-tab entry defaults to `Assets` at the top; `/paper-trading?tab=history` and `/paper-trading/transactions`
  intentionally open `History`.
- The fixed area contains the StockMentor-logo `Portfolio` header and `Assets` / `History` tabs.
- Assets summary starts collapsed with `Net Assets · USD`, Holdings Value, and Unrealized P/L only. Expanded content may
  show Cash, Fees Paid, Session, and Last reset. Do not show Today P/L unless backend exposes exact fields for that
  meaning.
- Empty positions use the `View Stock Page` CTA. Loading keeps the portfolio body in skeleton state instead of flashing
  empty positions.
- Positions use a fixed `Stock` column plus one horizontally scrollable metric table: `Latest Value/QTY`,
  `Current Price/Avg Cost`, `P/L`, `% Position`, and centered `Action` / Sell.
- History uses `Action`, `Stock`, `Price/Qty`, and `P/L`, with current-session toggle, local loaded-row search, and
  transaction detail navigation. Reset/null-symbol rows render as portfolio reset records.
- Trade tickets are stock-scoped and must not include internal all-stock pickers, generic holdings selectors, bid/ask,
  editable price, order type, time-in-force, session selector, max buying power, margin/options, or frontend-sent price,
  fee, amount, or max quantity.
- Reset uses a dimmed slide-up/down reset-card bottom sheet; the sheet action calls the reset endpoint while pending
  controls are disabled.

Stock detail page:

- Fixed header has three states: blank at top, symbol/company only after the main identity block is covered, then symbol
  plus compact quote after the main price block is covered.
- Header thresholds must be based on measured block bottoms, not block starts.
- Compact quote uses close price, a tight `▲` or `▼` marker, absolute change, and percent change. Values use movement
  color.
- Main quote panel shows symbol/company, market status, displayed price, direction marker, absolute change, percent
  change, High, Low, and full Volume. Volume must not be truncated with ellipsis.
- Footer wrapper is transparent. The CTA button is brand navy `#052344` and labeled `Practice Trade`.

AI explanation drawer:

- Closed title is `View AI Stock Explanation`; open title is `Close AI Stock Explanation`.
- Hide backend cache/generated status messages such as `Returned cached AI explanation`.
- Keep the educational disclaimer and data window compact.
- Loading uses a simple spinner plus short copy; error state provides retry for the same on-demand request.

Sorting icons:

- Default unsorted state shows both small arrows.
- Ascending shows only the up arrow.
- Descending shows only the down arrow.
- Use `IconSymbol` mappings or a local icon component; do not show raw text `^`, `v`, `<`, or `>`.

## Status Labels

Risk labels:

- Low risk: calm teal/green chip.
- Medium risk: amber chip.
- High risk: red chip, but avoid alarmist wording.

Trend labels:

- Uptrend
- Downtrend
- Sideways
- Choppy
- Not enough data

AI labels:

- Educational suggestion
- Learning explanation
- Data note
- Fallback used
- Currently unavailable

Market data labels:

- Delayed
- 15-minute delayed educational market data
- Displayed market time
- Latest stored delayed price
- Updated display minute
- Data not ready yet

Market notice banners are compact shallow-red marquee strips on Watchlist, Stocks, and Stock Detail. Preserve the full
backend-derived copy, do not slice strings, and do not show ellipsis. Long text should scroll as a marquee. Map backend
freshness metadata to user-facing states such as delayed data available, delayed prices not ready yet, latest stored
market data, and stale or unavailable data. Do not duplicate full exchange holiday logic in the frontend.

## AI Disclaimer Component

Every AI explanation/suggestion screen should show a compact disclaimer:

> This is an educational explanation based on stored StockMentor data. It is not real financial advice and does not predict future prices.

Keep it visible but not frightening.

## Forms

- Labels must be clear.
- Validation messages must be specific.
- Password fields should use an accessible eye/eye-off visibility affordance when existing icon support is available.
- Quantity inputs accept whole shares only.
- Submit buttons disable while pending.
- Errors should say what happened and how to recover.
- Keyboard-aware auth forms must keep focused inputs visible above the software keyboard and outside the iOS safe area/Dynamic Island.
- Field-level validation and backend-known conflicts should identify the exact field whenever possible.

## Modals

- Destructive actions need cancel and confirm.
- Modals must not trap users without a close/cancel path.
- Confirmation text should include the affected stock/user/action.
- Primary destructive button text must be explicit, such as "Reset simulated portfolio".

## Chart Presentation

- Phase 3B does not ship a real chart dependency. Use a compact history summary/list until a chart package is approved.
- Show timeframe.
- Show source/fallback notes when backend provides them.
- No advanced trading indicators.
- Candlestick chart is optional only if easy later.
- For intraday charts, label the latest visible point as delayed using backend delayed display metadata.
- Missing-data copy: "Some minute-level data may be missing because provider data and free-tier retrieval are delayed."

## Accessibility And HCI Basics

- Readable contrast across text, chips, and buttons.
- Mobile touch targets around 44x44 or larger.
- Clear button labels, not vague "Submit" when a better verb exists.
- Keyboard-friendly admin web controls.
- Loading states explain what is happening.
- Destructive actions always include cancel/close.
- Avoid financial-pressure wording.
