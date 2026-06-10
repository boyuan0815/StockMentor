# US006 AI Stock Suggestions Worktree Summary

This worktree completed the backend-focused US006 AI stock suggestion feature from V1 through V6. It is intended as a handoff reference for future Codex/Hermes agents and for demo/report preparation.

## What This Worktree Added

### V1 - AI Suggestion Backend Foundation

- Added Spring Security Basic Auth and current-user based backend ownership.
- Added `app_user` support with BCrypt password hashing, role/status checks, and dev demo/admin users.
- Added `user_investment_profile` as the onboarding/rule-based personalization foundation.
- Added persistent AI suggestion storage:
  - `stock_ai_suggestion_batch`
  - `stock_ai_suggestion_item`
- Added user watchlist persistence:
  - `user_watchlist`
- Added user-facing AI suggestion endpoints:
  - `GET /api/stocks/ai-suggestions`
  - `POST /api/stocks/ai-suggestions/refresh`
  - `PATCH /api/stocks/ai-suggestions/items/{itemId}/dismiss`
  - `PATCH /api/stocks/ai-suggestions/items/{itemId}/watchlist`
- Kept `GET /api/stocks/ai-suggestions` cache-only/read-only.
- Added manual refresh with 1-hour cooldown.
- Added OpenAI suggestion generation, strict backend validation, retry, cached fallback, and simple rule-based fallback.
- Ensured frontend never calls OpenAI or Twelve Data directly.

### V2 - Behavior Foundation, Scheduled Refresh, And Symbol Watchlist

- Added `user_behavior_profile` foundation and service.
- Kept behavior profile LOW/INSUFFICIENT_DATA until real paper-trading transactions existed.
- Integrated behavior summary into AI suggestion prompt/hash through `UserBehaviorProfileService`.
- Added scheduled refresh after market close, using `SCHEDULED_REFRESH`, bypassing manual cooldown and reusing unchanged `input_hash`.
- Added normal stock-symbol watchlist endpoints:
  - `GET /api/watchlist`
  - `POST /api/watchlist/{symbol}`
  - `DELETE /api/watchlist/{symbol}`
- Kept suggestion-item watchlist behavior separate and continued rejecting DISMISSED/EXPIRED suggestion items.

### V3 - Paper-Trading Foundation For Behavior Profiles

- Added backend-only paper-trading foundation:
  - `paper_trading_account`
  - `paper_position`
  - `paper_trade_transaction`
- Added paper-trading endpoints:
  - `GET /api/paper-trading/account`
  - `GET /api/paper-trading/portfolio`
  - `GET /api/paper-trading/transactions`
  - `POST /api/paper-trading/buy`
  - `POST /api/paper-trading/sell`
- Added instant market paper buy/sell using backend `stock.currentPrice`.
- Added immutable transaction history for paper buy/sell actions and user isolation.
- Realized profit/loss on sell was not completed in this worktree and should be handled in the separate paper-trading completeness task.
- Recalculated behavior profile after successful buy/sell using paper-trading data only.
- Did not add frontend UI, brokerage integration, order automation, limit/stop orders, transaction filters, portfolio reset, or complete realized P/L behavior.

### V4 - Smarter AI Suggestion Reliability

- Bumped suggestion prompt version to `stock-suggestion-v2`.
- Added backend-owned candidate fit signals for supported stocks.
- Included candidate fit signals, onboarding profile, behavior summary, snapshots, and prompt version in `input_hash`.
- Added or prepared JSON Schema `response_format` support for AI stock suggestions only, with strict backend validation still required.
- Kept AI explanation generation unchanged.
- Kept backend validation mandatory after structured output.
- Added schema fallback for schema-related OpenAI request failures.
- Hardened OpenAI response parsing for newer/unknown response fields and malformed suggestion responses.

### V5A - Behavior Scoring Refinement

- Improved transaction-backed behavior profile metrics using paper-trading data only.
- Refined:
  - `stockRiskExposureScore`
  - `favoriteRiskCategory`
  - `volatilityExposureScore`
  - `concentrationScore`
  - `averagePositionSizePercent`
  - `turnoverScore`
  - `behaviorRiskScore`
  - `behaviorSummaryText`
- Used existing stock rows and existing `StockAnalysisSnapshot` rows only.
- Did not call OpenAI, create snapshots, use watchlist/click/view/suggestion data, or implement holding-period lot pairing.
- Kept `holdingPeriodScore` null for V5A.

### V5B - AI Suggestion Trigger Integration

- Added internal trigger facade:
  - `StockAiSuggestionTriggerService`
  - `StockAiSuggestionTriggerServiceImpl`
  - `SuggestionTriggerResult`
- Future onboarding/profile modules can call:
  - `handleOnboardingCompleted(...)`
  - `handleProfileRetaken(...)`
  - `handleInvestmentProfileChanged(...)`
- Trigger facade calls the existing shared generation path with manual cooldown disabled.
- Trigger facade validates saved user/profile ownership and catches generation failures without breaking future profile save flows.
- Expanded `input_hash` to include meaningful investment profile fields such as risk tolerance, goal, experience, volatility, horizon, scores, and profile source.

### V6 - Admin Monitoring And Scheduler Management

- Added admin AI suggestion monitoring endpoints:
  - `GET /api/admin/ai-suggestions/batches`
  - `GET /api/admin/ai-suggestions/batches/{batchId}`
  - `GET /api/admin/ai-suggestions/failures`
  - `GET /api/admin/ai-suggestions/usage-summary`
- Added admin scheduler management endpoints:
  - `POST /api/admin/ai-suggestions/scheduled-refresh/run`
  - `GET /api/admin/ai-suggestions/refresh-jobs`
  - `GET /api/admin/ai-suggestions/refresh-jobs/{jobId}`
- Added refresh job tracking:
  - `ai_suggestion_refresh_job`
  - `AiSuggestionRefreshJobStatus`
  - `AiSuggestionRefreshTriggeredBy`
- Added shared scheduled refresh service used by both real scheduler and admin manual scheduled refresh.
- Admin monitoring endpoints require both ADMIN Basic Auth role access and valid `X-Admin-Token`.
- Admin read endpoints are read-only and do not call OpenAI, generate suggestions, create snapshots, mutate batches, or create jobs.
- Only `POST /api/admin/ai-suggestions/scheduled-refresh/run` creates refresh job rows.

## Important Current Behavior

- Normal user endpoints require Basic Auth.
- Admin AI endpoints require ADMIN role plus valid `X-Admin-Token`.
- No endpoint should trust frontend-provided `userId`.
- `GET /api/stocks/ai-suggestions` must remain strictly cache-only/read-only.
- Manual refresh enforces cooldown.
- Scheduled/admin/profile triggers bypass manual cooldown but still reuse `input_hash`.
- Behavior personalization must flow through `UserBehaviorProfileService`; AI suggestion service must not query paper-trading tables directly.
- Behavior must not be calculated from watchlist, dismissals, page views, clicks, or browsing.
- OpenAI output is never returned raw to normal users.
- Raw prompts, raw OpenAI responses, API keys, service tier, fingerprints, and secrets must not be exposed through normal or admin endpoints.

## Verification Results

Latest V6 verification used the wrapper-downloaded Maven binary because `.\mvnw.cmd` hit the known local wrapper launcher issue:

```text
.\mvnw.cmd clean test
-> failed locally with Cannot index into a null array / Cannot start maven from wrapper

wrapper-downloaded Maven clean test
-> BUILD SUCCESS, 102 tests, 0 failures, 0 errors

.\mvnw.cmd clean compile
-> failed locally with Cannot index into a null array / Cannot start maven from wrapper

wrapper-downloaded Maven clean compile
-> BUILD SUCCESS
```

Additional focused tests were added across the worktree for:

- OpenAI success/failure/fallback behavior.
- Manual cooldown behavior.
- GET read-only regression.
- Watchlist idempotency and dismissed/expired guardrails.
- Paper-trading buy/sell/account/portfolio/transaction behavior.
- Behavior profile scoring and input hash updates.
- V5B trigger facade.
- V6 admin endpoint security, monitoring, usage summary, and refresh job counters.

## Known Limitations And Future Work

- Full onboarding and quiz-retake write APIs are not implemented yet. Future modules should save user/profile changes first, then call the V5B trigger facade.
- JWT is not implemented. If added later, business logic should still resolve users through `CurrentUserService`.
- Paper trading is still a foundation, not a complete trading product:
  - no portfolio reset
  - no transaction filters
  - no transaction detail endpoint
  - no complete realized P/L calculation on sell
  - no enhanced portfolio summary with total invested cost, estimated market value, unrealized P/L, realized P/L, and return percentage
  - no order book or pending orders
  - no limit/stop/stop-limit orders
  - no automation
  - no margin, short selling, fees, taxes, dividends, splits, or fractional shares
- Behavior scoring can improve after portfolio and transaction logic matures.
- V6 refresh job tracking stores summary rows only, not per-user job detail rows.
- V6 job counters are best-effort for demo/report and intentionally do not change public `StockAiSuggestionResponse`.
