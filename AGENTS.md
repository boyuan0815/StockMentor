# AGENTS.md - StockMentor Repository Rules

## Project Scope
- StockMentor is currently backend-only. The Spring Boot backend lives under `backend/`.
- There is no frontend implementation yet. If a frontend is added later, it must call the backend only; it must not call OpenAI or Twelve Data directly.

## Build And Verification
- Use the Maven Wrapper, not global `mvn`.
- Windows verification command:
  ```powershell
  cd backend
  .\mvnw.cmd clean compile
  ```
- Add Java dependencies in `backend/pom.xml`. Do not manually add IntelliJ libraries to make compilation work.

## Architecture
- Keep the layering clear: Controller -> Service -> Repository.
- Controllers stay thin and handle request/response concerns.
- Business logic belongs in services.
- Repositories should only handle database access.
- Avoid unrelated rewrites and avoid changing public API response fields without checking usage.
- Keep changes beginner-friendly, explainable, and scoped to the task.

## Stock Data Rules
- Twelve Data is the external stock data source.
- Intraday 1-minute data is stored in `stock_price_history_1min`.
- Daily candles are stored in `stock_price_daily`.
- Intraday data must prevent duplicate `symbol + timestamp` rows.
- If `StockPriceHistory.tradingDate` is used, populate it whenever 1-minute history rows are created.
- Daily candles should be fetched from the Twelve Data `1day` API, not recalculated every 5 minutes during market hours.
- Market-time logic must use `America/New_York`.
- Trading-day checks should go through `MarketTimeService`.

## Scheduler Rules
- During market hours, scheduled jobs fetch latest intraday data.
- Post-market full intraday repair should use:
  `stockService.backfillIntradayForDate(SYMBOLS, today)`
  because it fetches the full 390-row day and fills missing minutes.
- Weekday post-market daily candle updates should use:
  `stockService.backfillMissingDaily(SYMBOLS, today, today)`.
- Saturday catch-up should remain a safety net for missing daily candles and safe intraday cleanup.

## Analysis Snapshot Rules
- For `1D` analysis, prefer complete-enough 1-minute intraday data.
- If intraday data is incomplete, fall back to daily candle data.
- Do not simply choose `max(latestDailyDate, latestIntradayDate)` without fallback safety. Try latest intraday first only when complete enough, then candidate daily, then latest complete daily.
- Dynamic intraday completeness must consider whether the date is a trading day and the current New York market time.
- If expected intraday rows are `<= 0`, completeness must return false.
- Keep these meanings consistent:
  - `dataSource`: table/source used for the analysis data.
  - `isFallback`: true when analysis falls back from incomplete intraday data to daily candle data.
  - `missingDataCount`: estimated missing data for the selected analysis input.
  - `baselineRiskCategory`: static baseline risk from stock metadata.
  - `riskCategory`: final adjusted/dynamic risk used in the explanation.
- `snapshotHash` must include fields that affect analysis meaning, including numeric metrics, labels, `dataSource`, fallback status, `baselineRiskCategory`, and final `riskCategory`.

## Stock Market Data Viewing Rules
- US009 normal user stock viewing endpoints live under `/api/stocks`.
- Current US009 endpoints are:
  `GET /api/stocks`,
  `GET /api/stocks/{symbol}`,
  and `GET /api/stocks/{symbol}/history?timeframe=1D|7D`.
- US009 endpoints are read-only and must use Basic Auth plus `CurrentUserService`; do not accept or trust frontend-provided `userId`.
- US009 must only read stored backend data. It must not call Twelve Data, OpenAI, scheduler/backfill flows, paper-trading services, behavior profile services, AI suggestion services, or stock analysis snapshot creation.
- US009 may include current-user watchlist status as read-only context.
- US009 list/detail price fields should prefer the latest stored `stock.currentPrice` and `stock.percentChange`; use analysis snapshot prices only as fallback when the stock row is missing.
- US009 `aiExplanationAvailable` should mean a stored explanation exists for the latest displayed `7D` analysis snapshot under the same model and prompt version used by US012, without calling the US012 explanation service.
- `GET /api/stocks/{symbol}/ai-explanation` remains the US012 AI explanation endpoint and must not be broken by US009 stock detail/history routing.
- `1D` stock history should read the latest stored non-null intraday `tradingDate`; if none exists, return an empty safe response instead of backfilling or failing.
- `7D` stock history should read stored daily candles only.

## AI Explanation Rules
- Explanations must be data-driven and based only on structured stock data.
- Do not predict future prices.
- Do not give buy/sell advice.
- Do not invent external news or reasons.
- Prefer concise user-facing data in the prompt.
- Do not pass excessive debug/internal fields to OpenAI unless they improve explanation quality.
- It is okay to include a short data note only when fallback data is used.
- Reuse stored explanations for the same snapshot, model, and prompt version. If the OpenAI call fails, return the friendly unavailable response and do not insert an explanation record.

## AI Stock Suggestion Rules
- `GET /api/stocks/ai-suggestions` must stay cache-only/read-only: no OpenAI call, no behavior profile creation/recalculation, no suggestion batch creation, no snapshot creation, and no refresh job creation.
- `POST /api/stocks/ai-suggestions/refresh` is the normal user manual refresh path and must enforce the manual cooldown.
- Scheduled refresh, admin scheduled refresh, and profile/onboarding trigger flows bypass manual cooldown but must still compute/reuse `input_hash` before calling OpenAI unnecessarily.
- Normal user endpoints require Basic Auth and must resolve the user through `CurrentUserService`; no endpoint should accept or trust frontend-provided `userId`.
- AI suggestions may use onboarding profile, behavior summary, stock snapshots, and backend candidate-fit signals, but must not expose raw prompts, raw OpenAI responses, API keys, service tier, fingerprints, or secrets.
- Behavior personalization must come through `UserBehaviorProfileService`; do not calculate behavior from watchlist rows, suggestion dismissals, page views, clicks, or browsing.
- US006 suggestion generation must not create, insert, or recalculate `user_behavior_profile`; it may only read `BehaviorSummaryForSuggestion`.
- US006 must combine declared onboarding preference and observed paper-trading behavior with explicit confidence-based weighting:
  LOW/missing = onboarding 80 / behavior 20, MEDIUM = onboarding 40 / behavior 60, HIGH = onboarding 10 / behavior 90.
- Current US006 suggestion prompt version is `stock-suggestion-v3`; bump it when prompt meaning, schema expectations, ranking semantics, or validation behavior materially change.
- AI suggestion calls should use OpenAI JSON Schema `response_format` with strict backend validation still authoritative. If schema mode is rejected for schema/response-format reasons, retry once without `response_format`.
- Same-input reuse should skip OpenAI only for reusable `SUCCESS` batches. `FALLBACK_CACHED` and `FALLBACK_RULE_BASED` may be readable, but they must not permanently block a future same-input OpenAI retry.
- Same-input `FALLBACK_CACHED`, `FAILED`, or rule-based rows should be updated in place when possible to avoid duplicate `user/model/prompt/input_hash` batches.
- AI suggestion explanations should be normalized before validation: sanitize awkward repeated trend wording and raw backend field names, allow natural wording with semantic factor coverage, and still reject advice, predictions, unsupported external claims, and wording that contradicts volatile/choppy snapshot data.
- Reusable suggestion batches must render at most one visible `ACTIVE`/`WATCHLISTED` item per rank, prefer `WATCHLISTED` then newest rows, expire duplicate visible rows, and reactivate the freshest expired intended rows when visible ranks are missing.
- Behavior recalculation belongs to US010 after successful paper-trading BUY/SELL commits, not to US006 suggestion generation.
- Paper-trading endpoints must not call OpenAI. They may update behavior profiles after successful trades through the behavior service.

## User Onboarding/Profile Rules
- US004 onboarding/profile is backend-only under `/api/user/**`.
- Normal user endpoints require Basic Auth and must resolve the current user through `CurrentUserService`.
- Do not accept or trust frontend-provided `userId`, `riskScore`, `profileSource`, profile version, or behavior fields.
- Onboarding questions are backend-owned and stable.
- First onboarding uses `profileSource = ONBOARDING`; retake uses `profileSource = RETAKE_QUIZ`.
- Investment profile versioning is immutable and uses max existing version + 1.
- US004-created `UserInvestmentProfile` rows must leave behavior fields null.
- US004 must not create, recalculate, reset, or downgrade behavior profiles.
- Profile response may include behavior summary through read-only behavior summary retrieval.
- First onboarding triggers AI suggestions after commit using `ONBOARDING_COMPLETED`; retake triggers after commit using `RETAKE_QUIZ`.
- AI suggestion trigger failures must be caught/logged and must not roll back onboarding/profile saves.
- `GET /api/user/profile` and `GET /api/user/onboarding/questions` must be read-only and must not trigger AI generation.
- Registration is not implemented yet; do not add or expose `/api/auth/register` unless explicitly scoped later.

## Paper-Trading Rules
- Paper-trading backend lives under `backend/src/main/java/net/boyuan/stockmentor/papertrading` and uses Controller -> Service -> Repository layering.
- Current paper-trading endpoints are:
  `GET /api/paper-trading/account`,
  `GET /api/paper-trading/portfolio`,
  `POST /api/paper-trading/portfolio/reset`,
  `POST /api/paper-trading/buy`,
  `POST /api/paper-trading/sell`,
  `GET /api/paper-trading/transactions`,
  and `GET /api/paper-trading/transactions/{transactionId}`.
- Paper-trading endpoints must resolve the authenticated user through `CurrentUserService`; never accept or trust frontend-provided `userId`.
- BUY/SELL execution must use the latest stored `stock.currentPrice` from backend stock rows. Do not call Twelve Data, OpenAI, or any external brokerage/trading service during BUY, SELL, or RESET.
- Supported symbols remain the StockMentor supported stock universe: `NVDA`, `TSLA`, `AMD`, `AAPL`, `MSFT`, `GOOG`, `KO`, and `JNJ`.
- Quantities are whole integer shares only. Fractional JSON numbers such as `1.5` must be rejected instead of coerced to `1`; keep `ObjectMapperConfig` strict integer deserialization in place.
- BUY applies the configured flat fee (`stockmentor.paper-trading.trade-fee`, default `1.00`) and includes that fee in position cost basis: `netAmount = quantity * price + fee`.
- SELL applies the flat fee once, records realized P/L, and deletes the position on a full sell: `netAmount = quantity * price - fee`; `realizedProfitLoss = netAmount - costBasisSold`.
- For full sells, use the position's full remaining `totalCost` as `costBasisSold` to avoid rounding drift. For partial sells, use `quantity * averageCost`.
- Portfolio responses should expose current-session performance fields such as `totalPortfolioValue`, `realizedProfitLoss`, `returnPercentage`, `totalFeesPaid`, `currentSessionNumber`, and `lastResetAt`. Do not reintroduce duplicate aliases such as `estimatedPortfolioValue` or `initialCash` unless an API compatibility task explicitly requires it.
- Direct BUY/SELL execution responses may return `portfolioWeightPercent = null` for the affected position because full portfolio value is not recalculated for that lightweight response. Full portfolio responses should calculate portfolio weights.
- Transaction history supports current-user filters for `symbol`, `side`, `from`, `to`, `page`, `size`, and `currentSessionOnly`; it must still return a plain list, not a paged wrapper.
- RESET is a backend-created transaction side only. RESET rows use `symbol = null`, zero quantity/amounts/fee/P&L, and mark the new current session. RESET must not trigger stock lookup, price lookup, OpenAI, Twelve Data, or behavior recalculation.
- Portfolio reset increments `PaperTradingAccount.currentSessionNumber`, sets `lastResetAt`, resets cash and starting cash to configured initial cash, deletes only the current user's open positions, and marks only that user's previous current/null-session transactions as old.
- Transaction columns added for US010 are intentionally nullable for local DB compatibility: `isCurrentSession`, `sessionNumber`, `fee`, `netAmount`, and `realizedProfitLoss`. Service/DTO logic must normalize old null values safely.
- `PaperTradeTransaction.symbol` is nullable because RESET is not stock-specific. If an older local MySQL schema rejects RESET rows, the manual compatibility fix is `ALTER TABLE paper_trade_transaction MODIFY symbol VARCHAR(10) NULL;`; do not add Flyway/Liquibase only for this.
- Behavior profile recalculation should run only after successful BUY/SELL commits, should be best-effort, and must not roll back valid trade persistence if behavior analytics fails. RESET, null-symbol rows, non BUY/SELL rows, and quantity `<= 0` rows must be ignored by behavior scoring.
- Advanced order features remain out of scope unless explicitly requested: limit orders, stop orders, pending orders, automation, brokerage integration, margin, short selling, fractional shares, taxes, dividends, stock splits, FX conversion, lot tables, and closed-position tables.

## Admin AI Suggestion Rules
- Admin AI endpoints live under `/api/admin/ai-suggestions/**` and require both ADMIN role and a valid `X-Admin-Token`.
- Current admin AI endpoints are:
  `GET /batches`, `GET /batches/{batchId}`, `GET /failures`, `GET /usage-summary`,
  `POST /scheduled-refresh/run`, `GET /refresh-jobs`, and `GET /refresh-jobs/{jobId}`.
- Admin monitoring read endpoints must be read-only: no OpenAI call, no suggestion generation, no snapshot creation, no batch mutation, and no refresh job creation.
- Only `POST /api/admin/ai-suggestions/scheduled-refresh/run` should create an `ai_suggestion_refresh_job` row.
- The scheduled AI suggestion refresh runs after market close and should use the shared scheduled refresh service; one failed user must not stop the job.
- Do not modify existing admin backfill behavior unless explicitly scoped; it also uses `X-Admin-Token`.

## Cleanup Rules
- Old intraday cleanup must delete 1-minute rows only when a matching daily candle exists.
- Do not delete intraday rows when there is no daily backup.
- Report cleanup deletions through `deletedRows`, not `savedRows`.
- Keep cleanup logic safe before optimizing it.
- Avoid MySQL-specific `DELETE JOIN` unless explicitly requested, because tests may need H2 compatibility.

## Configuration And Secrets
- Never hardcode API keys or admin tokens.
- Keep the namespaced admin token property as `stockmentor.admin.token`.
- Keep paper-trading config under `stockmentor.paper-trading`, currently including `initial-cash` and `trade-fee`.
- Keep `backend/src/main/resources/application-example.yaml` updated when configuration fields are added.

## Known Future Work
- Paper-trading completeness belongs in a separate scope: portfolio reset, transaction filters, complete realized P/L, and advanced order features are not part of the current AI suggestion worktree.
- Advanced paper-trading order features such as limit orders, stop orders, stop-limit orders, automation, margin, short selling, and brokerage integration are out of scope unless explicitly requested.
- JWT may be added later, but backend business logic should continue to rely on `CurrentUserService` for current-user resolution.
