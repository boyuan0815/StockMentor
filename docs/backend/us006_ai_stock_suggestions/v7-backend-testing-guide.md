# US006 V7 Backend Testing Guide

Note: V7 is historical. The current V8/V3 flow supersedes the fallback-cache behavior below: `FALLBACK_CACHED` and `FALLBACK_RULE_BASED` rows can still be readable, but same-input fallback rows must not permanently block a future OpenAI retry, and current generated batches use `prompt_version = stock-suggestion-v3`.

V7 completes the US006 follow-up for explicit onboarding-vs-behavior personalization.

US006 now uses:

- Declared onboarding preference from the latest `UserInvestmentProfile`.
- Observed paper-trading behavior from read-only `BehaviorSummaryForSuggestion`.
- Stored stock analysis snapshots and deterministic candidate-fit signals.
- GPT `gpt-4o-mini` when available.
- Safe deterministic fallback when AI generation is unavailable.

US006 must not create, insert, or recalculate `user_behavior_profile`.

## Personalization Weights

The backend applies these internal weights before prompt generation and fallback ranking:

```text
LOW or missing behavior profile:
  onboardingWeight = 80
  behaviorWeight = 20

MEDIUM behavior confidence:
  onboardingWeight = 40
  behaviorWeight = 60

HIGH behavior confidence:
  onboardingWeight = 10
  behaviorWeight = 90
```

The prompt input separates:

```text
declaredOnboardingProfile
observedPaperTradingBehavior
personalizationWeight
candidateFitSignals
supportedStockUniverse
beginnerSafetyRules
```

## API Checks

Start the backend in dev mode:

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

If the Maven wrapper launcher fails locally, use the wrapper-downloaded Maven binary from `%USERPROFILE%\.m2\wrapper\dists`.

Set variables:

```powershell
$base = "http://localhost:8080"
$beginnerAuth = "demo@stockmentor.local:Demo@12345"
```

Read cached suggestions:

```powershell
curl.exe -i -u $beginnerAuth "$base/api/stocks/ai-suggestions"
```

Expected:

- No OpenAI call.
- No behavior profile creation.
- No snapshot creation.
- No suggestion batch creation.
- Existing `SUCCESS`, `FALLBACK_CACHED`, or `FALLBACK_RULE_BASED` batches may be returned.

Manual refresh:

```powershell
curl.exe -i -X POST -u $beginnerAuth "$base/api/stocks/ai-suggestions/refresh"
```

Expected:

- Manual cooldown is enforced.
- If inputs are unchanged, existing matching suggestions are reused.
- If OpenAI succeeds, batch status is `SUCCESS`.
- If OpenAI fails and a previous successful batch exists, response status is `FALLBACK_CACHED` and `fallbackUsed = true`.
- If OpenAI fails with no previous success, response status is `FALLBACK_RULE_BASED` and `fallbackUsed = true`.

## SQL Checks

Confirm US006 does not create behavior profiles during suggestion refresh:

```sql
SELECT COUNT(*) AS behavior_profiles_before
FROM user_behavior_profile
WHERE user_id = 1;
```

Run a US006 refresh, then check again:

```sql
SELECT COUNT(*) AS behavior_profiles_after
FROM user_behavior_profile
WHERE user_id = 1;
```

Expected:

- Counts should be unchanged by US006.
- New behavior profiles should only appear after successful paper-trading BUY/SELL flows.

Review suggestion batches:

```sql
SELECT suggestion_batch_id, user_id, status, trigger_reason,
       input_hash, model, prompt_version, error_message,
       created_at, expires_at
FROM stock_ai_suggestion_batch
ORDER BY suggestion_batch_id DESC;
```

Expected:

- `FALLBACK_CACHED` rows preserve an internal `error_message` when OpenAI failed.
- `FALLBACK_CACHED` is not treated as `SUCCESS`.
- `input_hash` changes only when meaningful onboarding, behavior, candidate, snapshot, model, prompt, timeframe, or supported-symbol inputs change.

## Regression Checks

US004 onboarding/profile:

- First onboarding and retake save immutable investment profile rows.
- US004-created `UserInvestmentProfile` behavior fields remain `NULL`.
- US004 does not create or recalculate behavior profiles directly.

US010 paper trading:

- Successful BUY/SELL recalculates behavior profile after commit.
- RESET and invalid trades do not recalculate behavior.
- Paper trading does not mutate onboarding profile rows.

## Automated Verification

Run from `backend`:

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd clean compile
```

Then run from repository root:

```powershell
git diff --check
git status --short
```

If `.\mvnw.cmd` fails with the known Windows launcher issue, use the cached Maven binary and document that in the implementation summary.
