# Frontend Testing Checklist

Use this checklist after frontend implementation begins. This documentation task does not run frontend tests.

## Environment

- Backend running with expected profile and local database.
- `EXPO_PUBLIC_API_BASE_URL` points to the backend from the active device/browser.
- Demo users available or registration flow works.
- Admin token configured in backend and known to the tester.
- Expo mobile/native and Expo Web tested separately.
- CORS configured later before real Expo Web admin testing.

## Mobile Beginner Flow

- Register account.
- Login with email.
- Login with username.
- Bootstrap through `GET /api/auth/me`.
- Onboarding gate routes new beginner to onboarding.
- Complete onboarding.
- View investment profile.
- Retake onboarding with confirmation.
- View Watchlist tab.
- Confirm Watchlist title is `Watchlists`, logo appears when asset exists, search/refresh are icon-only, tabs are
  `All`, `US`, `HK`, `MY`, and table rows have no hearts.
- Confirm Watchlist sorting cycles default -> ascending -> descending -> default for Symbol, Price, and Chg %, with
  small up/down icon states.
- Confirm Watchlist refreshes on focus after add/remove from detail.
- View Stocks tab.
- Confirm Stocks page title is compact `Stocks`, tabs are `US`, `MY`, `HK`, and `MY`/`HK` show planned states only.
- Confirm Stocks table columns are `No.`, `Symbol`, `Price`, `Chg %`, `Action`, and the practice-trade button does not
  trigger row navigation.
- Confirm Watchlist, Stocks, Suggestions, Portfolio, and Profile use fixed StockMentor-logo headers; Search keeps its
  search-row header and appears as the rightmost tab with a separator.
- View Search tab and contextual search from Watchlist, Stocks, and Detail.
- Confirm empty Search shows Search History and max three Latest Viewed Stocks only, not all supported stocks.
- Confirm typed Search results show symbol/name plus heart toggle only, no price/change.
- View stock detail.
- Confirm Stock Detail header stays blank at top, shows symbol/company only after the main identity block is gone, and
  shows symbol plus compact quote only after the main price block is gone.
- Confirm detail quote panel shows displayed price, direction marker, absolute change, percent change, High, Low, and
  full Volume when backend fields are present.
- Confirm Volume is not truncated with ellipsis.
- Change history timeframe.
- Confirm delayed data badge/note appears from backend delayed metadata.
- Confirm unavailable/null price states use `priceFreshnessStatus`, `isPriceAvailable`, and `dataNote`.
- Confirm 9:30-9:44 AM New York opening message is clear when backend indicates current-day delayed data is not ready.
- Confirm after 4:15 PM New York wording does not imply the market is still updating.
- View empty chart state when backend returns no points.
- Confirm market notice marquee preserves full copy, has no ellipsis, and scrolls long text.
- View AI explanation drawer and unavailable state.
- Confirm AI endpoint is not called on initial stock detail load.
- Confirm AI drawer titles are `View AI Stock Explanation` and `Close AI Stock Explanation`.
- Confirm backend cache/generated status messages are hidden while disclaimer/data window remain visible.
- Confirm `YTD` and `1Y` show unsupported AI explanation copy without sending a request.
- View AI suggestions.
- Refresh suggestions when allowed.
- Cooldown display disables refresh when not allowed.
- Add stock to watchlist.
- Remove stock from watchlist.
- View Portfolio tab.
- Confirm Portfolio bottom-tab entry opens `Assets` at the top; `/paper-trading?tab=history` and
  `/paper-trading/transactions` open `History`.
- Confirm Portfolio has fixed `Assets` and `History` top tabs.
- Confirm Assets shows `Net Assets · USD`, Holdings Value, Unrealized P/L, positions, valuation warnings, View Stocks,
  and Reset Portfolio; expanded content may show Cash, Fees Paid, Session, and Last reset. It does not show Today P/L
  without backend support.
- Confirm initial portfolio loading keeps the portfolio body in skeleton state and does not briefly show empty
  positions.
- Confirm empty positions CTA says `View Stock Page`.
- Confirm the positions table keeps `Stock` fixed, horizontally scrolls metric columns, includes P/L, `% Position`, and
  a centered Action/Sell control.
- Confirm Stock List, Stock Detail, and position-row Sell open stock-scoped guarded tickets without internal stock or
  holdings selectors.
- Confirm ticket account/quote layout shows `Net Assets · USD`, a small US flag, and the selected stock quote for both
  Buy and Sell.
- Confirm buy/sell validate whole-share quantity, show modal confirmation, and send only `symbol` plus numeric
  `quantity`.
- Confirm fee, amount, and max quantity are display-only estimates and are not sent in the buy/sell payload.
- Confirm sell supports Partial and All; pasted quantity greater than or equal to holding switches to All. If holding
  quantity is `1`, All is forced, Partial is disabled, quantity stays `1`, and plus/minus controls look disabled.
- Confirm successful buy/sell redirects to `/paper-trading?tab=history`.
- Confirm reset uses a dimmed slide-up/down reset-card sheet saying simulated cash returns to starting balance, open
  positions are cleared, a new session starts, and the action cannot be undone.
- Confirm Stock List and Stock Detail `Practice Trade` CTAs open the guarded buy ticket and do not execute directly.
- View History tab and transaction detail.
- Confirm History defaults to current session, supports side filters and local symbol/company search over loaded rows,
  displays `Action`, `Stock`, `Price/Qty`, and `P/L`, and displays RESET/null-symbol rows as session reset rows without
  stock links or price assumptions.
- Logout clears session.

## Admin Web Flow

- Login with admin Basic Auth credentials.
- Enter admin token.
- Reject normal user from admin shell.
- Missing admin token shows re-entry prompt.
- Wrong admin token shows re-entry prompt.
- View admin dashboard.
- View users list with filters.
- View user detail.
- Disable user with confirmation.
- Re-enable user with confirmation.
- Verify self-disable/last-admin conflicts show clear message.
- View AI batches.
- View AI batch detail.
- View AI failures.
- View usage summary.
- View refresh jobs and job detail.
- Run scheduled refresh with confirmation.
- Run stock maintenance/backfill with confirmation.
- Logout clears admin token.

## API Smoke Tests By Screen

Use `backend-api-screen-map.md` as the endpoint checklist. Every screen should verify:

- correct endpoint
- correct HTTP method
- correct Basic Auth
- correct admin token where needed
- correct request body
- expected response DTO fields rendered
- loading state
- empty state
- error state
- pending-state guardrails

## Failure And Recovery Tests

- `400` validation errors.
- `401` unauthenticated/wrong credentials.
- `403` normal user opening admin route.
- `404` missing stock/user/transaction.
- `409` conflict such as duplicate account or admin self-disable.
- Backend unavailable.
- Network timeout.
- AsyncStorage/native module unavailable; search history and latest viewed stocks must fall back safely without a red
  screen.
- AI explanation unavailable.
- AI suggestions fallback used.
- Insufficient cash.
- Sell quantity exceeds holding.
- Unsupported stock symbol.
- Fractional quantity rejected.

## Duplicate Submit Tests

- Tap register rapidly.
- Tap login rapidly.
- Tap onboarding finish rapidly.
- Tap AI refresh rapidly.
- Tap buy/sell/reset rapidly.
- Tap admin disable/re-enable rapidly.
- Tap admin scheduled refresh rapidly.
- Tap stock backfill/cleanup rapidly.

Each case should send at most one write request while pending.

## Navigation Regression Tests

- Stocks -> Detail -> Back returns Stocks.
- Watchlist -> Detail -> Back returns Watchlist.
- Search tab -> Detail -> Back returns Search tab.
- Stocks -> Search Context -> Back returns Stocks.
- Watchlist -> Search Context -> Back returns Watchlist.
- Detail -> Search Context -> Back returns the same Detail.
- Search Context -> Detail -> Back returns Search Context with original params.
- Stock list -> Practice Trade buy ticket -> Back returns Stock list.
- Detail -> Practice Trade buy ticket -> Back returns same Detail.
- Portfolio -> History -> Transaction Detail -> Back returns History.
- Random-second focus refresh on Watchlist, Stocks, Search, Stock Detail, Portfolio, and History keeps current values
  visible, then updates again at the next minute boundary without clearing inputs, filters, tabs, or scroll state.

## Phase 3B Protected-File Checks

- `frontend/.gitignore` is absent unless explicitly approved.
- `.agents` and `skills-lock.json` are not modified.
- Package/lock files change only when a dependency was explicitly approved.

## Demo Script

1. Register or login.
2. Complete onboarding.
3. View investment profile.
4. View stock list and stock detail.
5. View AI stock explanation.
6. View AI stock suggestions.
7. Add watchlist or perform a practice trade.
8. View portfolio and transactions.
9. Login as admin.
10. Manage users.
11. Monitor or maintain stock and AI data.
