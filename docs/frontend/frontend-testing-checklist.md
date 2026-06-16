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
- View home dashboard.
- View stock list.
- View stock detail.
- Change chart timeframe.
- Confirm delayed data badge/note appears from backend delayed metadata.
- Confirm unavailable/null price states use `priceFreshnessStatus`, `isPriceAvailable`, and `dataNote`.
- Confirm 9:30-9:44 AM New York opening message is clear when backend indicates current-day delayed data is not ready.
- Confirm after 4:15 PM New York wording does not imply the market is still updating.
- View empty chart state when backend returns no points.
- View AI explanation and unavailable state.
- View AI suggestions.
- Refresh suggestions when allowed.
- Cooldown display disables refresh when not allowed.
- Add stock to watchlist.
- Remove stock from watchlist.
- View paper-trading account and portfolio.
- Confirm buy.
- Confirm sell.
- Confirm reset.
- Confirm buy/sell copy says practice trades use StockMentor's delayed stored price and that frontend does not send price.
- View transactions and transaction detail.
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
