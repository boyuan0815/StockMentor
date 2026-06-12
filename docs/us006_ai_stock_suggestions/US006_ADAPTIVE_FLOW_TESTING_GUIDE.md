# US006 V8 Adaptive Flow Testing Guide

## 1. Purpose

This guide validates adaptive personalization for US006 AI stock suggestions.

Expected story:

```text
US004 records an aggressive onboarding profile.
US006 first generates suggestions mostly from declared onboarding preference.
US010 paper-trading BUY/SELL transactions recalculate observed behavior.
US006 later reads that behavior summary and shifts weighting toward observed behavior as confidence becomes MEDIUM, then HIGH.
```

This does not prove investment profitability. It proves personalization responsiveness in an educational paper-trading context.

## 2. Start Backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

If the Windows Maven Wrapper launcher fails, use the cached Maven binary from `%USERPROFILE%\.m2\wrapper\dists\...\apache-maven-...\bin\mvn.cmd`.

## 3. Auth Variables And Helpers

```powershell
$base = "http://localhost:8080"
$demoAuth = "demo@stockmentor.local:Demo@12345"
$adminAuth = "admin@stockmentor.local:Admin@12345"
$adminToken = "<your-dev-admin-token>"
$testEmail = "us006.v8.adaptiveee@example.com"
$testAuth = "$testEmail`:Demo@12345"

function Invoke-Get($Path, $Auth = $testAuth) {
  curl.exe -s -u $Auth "$base$Path"
}

function Invoke-PostJson($Path, $Body, $Auth = $testAuth) {
  $json = $Body | ConvertTo-Json -Depth 20
  curl.exe -s -X POST -u $Auth -H "Content-Type: application/json" -d $json "$base$Path"
}

function Invoke-PaperTrade($Side, $Symbol, $Quantity) {
  Invoke-PostJson "/api/paper-trading/$Side" @{ symbol = $Symbol; quantity = $Quantity }
}
```

## 4. Fresh Local Test User

Create a local-only test user by copying the demo user's BCrypt password hash. Adjust role/status column names only if your local schema differs.

```sql
INSERT INTO app_user (email, username, password_hash, role, status, is_deleted, created_at, updated_at)
SELECT 'us006.v8.adaptiveee@example.com',
       'us006_v8_adaptive',
       password_hash,
       'BEGINNER_INVESTOR',
       'ACTIVE',
       false,
       NOW(),
       NOW()
FROM app_user
WHERE email = 'demo@stockmentor.local'
LIMIT 1;
```

Checkpoint before onboarding:

```sql
SELECT COUNT(*) AS behavior_profiles_before_onboarding
FROM user_behavior_profile
WHERE user_id = (
  SELECT user_id FROM app_user WHERE email = 'us006.v8.adaptiveee@example.com'
);
```

Expected: `0` for a fresh user.

## 5. Aggressive Onboarding

Use answers that map to aggressive growth and high volatility in the current backend scoring table. If question IDs differ locally, first inspect:

```powershell
Invoke-Get "/api/user/onboarding/questions"
```

Example request shape:

```powershell
$aggressiveOnboarding = @{
  answers = @(
    @{ questionId = 1; selectedOptionKey = "HIGH_RISK_HIGH_RETURN" },
    @{ questionId = 2; selectedOptionKey = "GROW_WEALTH" },
    @{ questionId = 3; selectedOptionKey = "BEGINNER" },
    @{ questionId = 4; selectedOptionKey = "HIGH_VOLATILITY" },
    @{ questionId = 5; selectedOptionKey = "LONG_TERM" }
  )
}

Invoke-PostJson "/api/user/onboarding/complete" $aggressiveOnboarding
```

Expected latest profile:

```text
risk_tolerance       AGGRESSIVE
investment_goal      GROWTH
preferred_volatility HIGH
```

## 6. SQL Check - Onboarding Profile

```sql
SELECT profile_id, user_id, profile_version, profile_source,
       risk_tolerance, investment_goal, experience_level,
       preferred_volatility, preferred_horizon,
       risk_score, goal_score, experience_score,
       behavior_risk_score, behavior_style, behavior_confidence,
       created_at, updated_at
FROM user_investment_profile
WHERE user_id = (
  SELECT user_id FROM app_user WHERE email = 'us006.v8.adaptiveee@example.com'
)
ORDER BY profile_version DESC, updated_at DESC;
```

Expected:

- Latest profile is aggressive/growth/high volatility.
- US004-created behavior fields are `NULL`.
- Profile belongs only to the authenticated test user.

Behavior profile count after onboarding:

```sql
SELECT COUNT(*) AS behavior_profiles_after_onboarding
FROM user_behavior_profile
WHERE user_id = (
  SELECT user_id FROM app_user WHERE email = 'us006.v8.adaptiveee@example.com'
);
```

Expected: unchanged from the before-onboarding count. US004 must not create a behavior profile.

## 7. First US006 Generation

Use an existing generation path, such as the onboarding after-commit trigger or admin scheduled refresh. Do not use GET as a trigger.

```powershell
curl.exe -s -X POST -u $adminAuth -H "X-Admin-Token: $adminToken" "$base/api/admin/ai-suggestions/scheduled-refresh/run"
```

Important rule:

```text
GET /api/stocks/ai-suggestions is read-only and must not call OpenAI, create batches, create snapshots, or create behavior profiles.
```

Behavior profile count after first US006 generation:

```sql
SELECT COUNT(*) AS behavior_profiles_after_first_us006
FROM user_behavior_profile
WHERE user_id = (
  SELECT user_id FROM app_user WHERE email = 'us006.v8.adaptiveee@example.com'
);
```

Expected: unchanged. US006 must not create or recalculate behavior profiles.

Capture the first batch:

```sql
SELECT suggestion_batch_id, user_id, profile_id, profile_version,
       status, trigger_reason, prompt_version, input_hash,
       model, error_message, created_at, expires_at
FROM stock_ai_suggestion_batch
WHERE user_id = (
  SELECT user_id FROM app_user WHERE email = 'us006.v8.adaptiveee@example.com'
)
ORDER BY suggestion_batch_id DESC;
```

Inspect first items:

```sql
SELECT i.rank_no, i.symbol, i.match_score, i.risk_level,
       i.suggestion_label, i.short_reason, i.detail_reason,
       s.risk_category, s.volatility_label, s.trend,
       s.price_consistency, s.is_fallback, s.missing_data_count
FROM stock_ai_suggestion_item i
JOIN stock_ai_suggestion_batch b
  ON b.suggestion_batch_id = i.suggestion_batch_id
LEFT JOIN stock_analysis_snapshot s
  ON s.analysis_snapshot_id = i.analysis_snapshot_id
WHERE b.suggestion_batch_id = <FIRST_BATCH_ID>
ORDER BY i.rank_no;
```

Expected Stage 1:

```text
Quiz profile: AGGRESSIVE
Behavior: missing or LOW
Expected weight: onboarding 80 / behavior 20
Suggestion pattern: onboarding-dominant and more tolerant of aggressive/high-volatility educational examples
```

Save the first `input_hash` for the final evidence table.

## 8. Select Lower-Risk Symbols

Inspect current snapshot risk categories before choosing symbols:

```sql
SELECT symbol, risk_category, volatility_label, trend, price_consistency,
       is_fallback, missing_data_count, created_at
FROM stock_analysis_snapshot
WHERE timeframe = '7D'
ORDER BY symbol, created_at DESC;
```

Recommended if available:

```text
KO, JNJ, and one moderate/lower-risk symbol such as MSFT, AAPL, or GOOG.
```

Do not rely on KO/JNJ alone for HIGH confidence because current behavior thresholds require at least 3 symbols for HIGH.

## 9. First Trade Set To Reach MEDIUM

Current behavior thresholds:

```text
MEDIUM = at least 3 valid BUY/SELL transactions across at least 2 symbols.
HIGH   = at least 10 valid BUY/SELL transactions across at least 3 symbols.
```

Example:

```powershell
Invoke-PaperTrade -Side "buy" -Symbol "KO" -Quantity 1
Invoke-PaperTrade -Side "buy" -Symbol "JNJ" -Quantity 1
Invoke-PaperTrade -Side "buy" -Symbol "KO" -Quantity 1
```

US010 BUY/SELL may create or recalculate `user_behavior_profile`. RESET, invalid trades, watchlist actions, suggestion actions, clicks, views, and browsing must not be behavior sources.

Behavior profile count after US010 trades:

```sql
SELECT COUNT(*) AS behavior_profiles_after_us010_trades
FROM user_behavior_profile
WHERE user_id = (
  SELECT user_id FROM app_user WHERE email = 'us006.v8.adaptiveee@example.com'
);
```

Expected: count may become `1` because US010 BUY/SELL recalculates real paper-trading behavior.

## 10. Behavior SQL Check After MEDIUM Trades

```sql
SELECT behavior_profile_id, behavior_confidence, behavior_style,
       behavior_risk_score, favorite_risk_category,
       most_traded_symbols, high_volatility_exposure,
       average_position_size_percent,
       stock_risk_exposure_score, concentration_score,
       turnover_score, holding_period_score,
       volatility_exposure_score, updated_at
FROM user_behavior_profile
WHERE user_id = (
  SELECT user_id FROM app_user WHERE email = 'us006.v8.adaptiveee@example.com'
)
ORDER BY updated_at DESC
LIMIT 1;
```

Expected:

```text
behavior_confidence = MEDIUM, if threshold is met
favorite_risk_category / most_traded_symbols / volatility exposure reflect lower-risk trades
```

## 11. Second US006 Generation After MEDIUM Behavior

Use the same cooldown-bypassing generation path:

```powershell
curl.exe -s -X POST -u $adminAuth -H "X-Admin-Token: $adminToken" "$base/api/admin/ai-suggestions/scheduled-refresh/run"
```

Capture the second batch:

```sql
SELECT suggestion_batch_id, status, trigger_reason, input_hash, created_at
FROM stock_ai_suggestion_batch
WHERE user_id = (
  SELECT user_id FROM app_user WHERE email = 'us006.v8.adaptiveee@example.com'
)
ORDER BY suggestion_batch_id DESC;
```

Expected Stage 2:

```text
Quiz profile: AGGRESSIVE
Behavior confidence: MEDIUM
Expected weight: onboarding 40 / behavior 60
input_hash: different from Stage 1
Suggestion pattern: mixed; lower-risk behavior should partly influence ranking/reasons
```

Behavior profile count after later US006 generation:

```sql
SELECT COUNT(*) AS behavior_profiles_after_medium_us006
FROM user_behavior_profile
WHERE user_id = (
  SELECT user_id FROM app_user WHERE email = 'us006.v8.adaptiveee@example.com'
);
```

Expected: unchanged from after US010 trades. Later US006 should only read the existing behavior profile.

## 12. Second Larger Trade Set To Reach HIGH

Add enough valid trades to reach at least 10 BUY/SELL transactions across at least 3 supported symbols.

Example shape:

```powershell
Invoke-PaperTrade -Side "buy" -Symbol "MSFT" -Quantity 1
Invoke-PaperTrade -Side "buy" -Symbol "KO" -Quantity 1
Invoke-PaperTrade -Side "sell" -Symbol "KO" -Quantity 1
Invoke-PaperTrade -Side "buy" -Symbol "JNJ" -Quantity 1
Invoke-PaperTrade -Side "buy" -Symbol "MSFT" -Quantity 1
Invoke-PaperTrade -Side "buy" -Symbol "KO" -Quantity 1
Invoke-PaperTrade -Side "buy" -Symbol "JNJ" -Quantity 1
```

Adjust quantities only if a sell would exceed current holdings. The final evidence should include at least three symbols, for example KO, JNJ, and MSFT.

## 13. Behavior SQL Check After HIGH Trades

Re-run the behavior SQL from section 10.

Expected:

```text
behavior_confidence = HIGH
behavior_style and favorite risk category reflect lower-risk behavior
most_traded_symbols includes at least three selected symbols
```

## 14. Third US006 Generation After HIGH Behavior

Run the generation path again:

```powershell
curl.exe -s -X POST -u $adminAuth -H "X-Admin-Token: $adminToken" "$base/api/admin/ai-suggestions/scheduled-refresh/run"
```

Capture the third batch and items using the SQL from sections 7 and 11.

Expected Stage 3:

```text
Quiz profile: AGGRESSIVE
Behavior confidence: HIGH
Expected weight: onboarding 10 / behavior 90
input_hash: different from Stage 1 and Stage 2
Suggestion pattern: behavior-dominant; lower-risk/conservative/moderate symbols should be more prominent if suitable snapshots exist
Reason wording: should explain observed paper-trading behavior when it conflicts with aggressive onboarding
```

Behavior profile count after final US006:

```sql
SELECT COUNT(*) AS behavior_profiles_after_high_us006
FROM user_behavior_profile
WHERE user_id = (
  SELECT user_id FROM app_user WHERE email = 'us006.v8.adaptiveee@example.com'
);
```

Expected: unchanged from after US010 trades.

## 15. Final Evidence Table

| Stage | Quiz risk tolerance | Behavior confidence | Favorite risk category | Expected onboarding weight | Expected behavior weight | Suggested symbols | Risk levels | Input hash |
| --- | --- | --- | --- | ---: | ---: | --- | --- | --- |
| After onboarding | AGGRESSIVE | LOW / missing | N/A | 80 | 20 | `<symbols>` | `<risk levels>` | `<hash 1>` |
| After first lower-risk trades | AGGRESSIVE | MEDIUM | conservative/lower-risk | 40 | 60 | `<symbols>` | `<risk levels>` | `<hash 2>` |
| After more lower-risk trades | AGGRESSIVE | HIGH | conservative/lower-risk | 10 | 90 | `<symbols>` | `<risk levels>` | `<hash 3>` |

Expected demo story:

```text
After aggressive onboarding:
  Suggestions are mostly based on declared aggressive profile.

After some lower-risk paper trades:
  Behavior confidence becomes MEDIUM.
  Suggestions become mixed because behavior now has meaningful influence.

After many lower-risk paper trades:
  Behavior confidence becomes HIGH.
  Suggestions become more behavior-dominant and should align more with observed lower-risk trading behavior.
```

## 16. Safe Cleanup SQL

Run only for the V8 test email in local development.

```sql
SET @v8_user_id = (
  SELECT user_id FROM app_user WHERE email = 'us006.v8.adaptiveee@example.com'
);

DELETE FROM stock_ai_suggestion_item
WHERE user_id = @v8_user_id;

DELETE FROM stock_ai_suggestion_batch
WHERE user_id = @v8_user_id;

DELETE FROM user_behavior_profile
WHERE user_id = @v8_user_id;

DELETE FROM paper_trade_transaction
WHERE user_id = @v8_user_id;

DELETE FROM paper_position
WHERE user_id = @v8_user_id;

DELETE FROM paper_trading_account
WHERE user_id = @v8_user_id;

DELETE FROM user_investment_profile
WHERE user_id = @v8_user_id;

DELETE FROM app_user
WHERE user_id = @v8_user_id;
```

