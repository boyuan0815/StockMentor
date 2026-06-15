# StockMentor Design System

## Design Intent

StockMentor should feel like a calm learning workspace for beginner investors. It should avoid the adrenaline style of real-money trading apps.

Design signature: a "learning ledger" feel. Use clear rows, soft dividers, gentle status chips, and beginner-friendly explanations. The memorable element should be the combination of simple chart notes plus educational AI disclaimers, not flashy market visuals.

## Color Direction

Use a restrained light-first palette:

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
- `StockCard`
- `SuggestionCard`
- `TradeTicket`
- `PortfolioSummary`
- `AdminDataTable`

Do not install a large UI kit during planning. Add UI or chart libraries later only if implementation proves a clear need.

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

## AI Disclaimer Component

Every AI explanation/suggestion screen should show a compact disclaimer:

> This is an educational explanation based on stored StockMentor data. It is not real financial advice and does not predict future prices.

Keep it visible but not frightening.

## Forms

- Labels must be clear.
- Validation messages must be specific.
- Password fields should support show/hide.
- Quantity inputs accept whole shares only.
- Submit buttons disable while pending.
- Errors should say what happened and how to recover.

## Modals

- Destructive actions need cancel and confirm.
- Modals must not trap users without a close/cancel path.
- Confirmation text should include the affected stock/user/action.
- Primary destructive button text must be explicit, such as "Reset simulated portfolio".

## Chart Presentation

- MVP chart can be a simple educational line chart.
- Show timeframe.
- Show source/fallback notes when backend provides them.
- No advanced trading indicators.
- Candlestick chart is optional only if easy later.
- For intraday charts, label the latest visible point as delayed when backend supports delayed display metadata.
- Missing-data copy: "Some minute-level data may be missing because provider data and free-tier retrieval are delayed."

## Accessibility And HCI Basics

- Readable contrast across text, chips, and buttons.
- Mobile touch targets around 44x44 or larger.
- Clear button labels, not vague "Submit" when a better verb exists.
- Keyboard-friendly admin web controls.
- Loading states explain what is happening.
- Destructive actions always include cancel/close.
- Avoid financial-pressure wording.
