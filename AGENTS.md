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

## AI Explanation Rules
- Explanations must be data-driven and based only on structured stock data.
- Do not predict future prices.
- Do not give buy/sell advice.
- Do not invent external news or reasons.
- Prefer concise user-facing data in the prompt.
- Do not pass excessive debug/internal fields to OpenAI unless they improve explanation quality.
- It is okay to include a short data note only when fallback data is used.
- Reuse stored explanations for the same snapshot, model, and prompt version. If the OpenAI call fails, return the friendly unavailable response and do not insert an explanation record.

## Cleanup Rules
- Old intraday cleanup must delete 1-minute rows only when a matching daily candle exists.
- Do not delete intraday rows when there is no daily backup.
- Report cleanup deletions through `deletedRows`, not `savedRows`.
- Keep cleanup logic safe before optimizing it.
- Avoid MySQL-specific `DELETE JOIN` unless explicitly requested, because tests may need H2 compatibility.

## Configuration And Secrets
- Never hardcode API keys or admin tokens.
- Keep the namespaced admin token property as `stockmentor.admin.token`.
- Keep `backend/src/main/resources/application-example.yaml` updated when configuration fields are added.