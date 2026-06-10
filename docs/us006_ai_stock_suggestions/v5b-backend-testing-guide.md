# US006 V5B Backend Testing Guide

V5B adds internal AI suggestion trigger entry points for future onboarding and quiz-retake modules. It does not add public onboarding endpoints.

## Run Backend Tests

```powershell
cd backend
.\mvnw.cmd clean test
.\mvnw.cmd clean compile
```

If the Maven wrapper launcher fails locally, use the wrapper-downloaded Maven binary workaround already used in earlier US006 verification.

## GET Suggestions Stays Read-Only

```powershell
$base = "http://localhost:8080"
$beginnerAuth = "demo@stockmentor.local:Demo@12345"

curl.exe -i -u $beginnerAuth "$base/api/stocks/ai-suggestions"
```

Expected:

- No OpenAI call.
- No behavior profile creation or recalculation.
- No suggestion batch creation.
- No `NO_ACTIVE_SUGGESTION` trigger.
- If no batch exists, `suggestedStocks = []`, supported stocks appear in `remainingStocks`, and a friendly message is returned.

## Service-Level Trigger Entry Points

V5B adds `StockAiSuggestionTriggerService` for future backend modules to call after saving user/profile data:

- `handleOnboardingCompleted(AppUser user)`
- `handleProfileRetaken(AppUser user, UserInvestmentProfile profile)`
- `handleInvestmentProfileChanged(AppUser user, UserInvestmentProfile profile, StockAiSuggestionTriggerReason reason)`

Important:

- Future onboarding/retake code must save `AppUser` and `UserInvestmentProfile` first.
- Trigger methods reload the latest profile through the existing generation path.
- Trigger generation bypasses manual refresh cooldown.
- Trigger failures are logged and returned as `SuggestionTriggerResult`; they should not rollback profile saves.

## Trigger Reason Expectations

Allowed through the trigger facade:

- `ONBOARDING_COMPLETED`
- `RETAKE_QUIZ`
- `NO_ACTIVE_SUGGESTION` for explicit backend/system use only

Rejected through the trigger facade:

- `MANUAL_REFRESH`
- `SCHEDULED_REFRESH`

Manual refresh and scheduled refresh keep their existing dedicated flows.

## Input Hash Checks

Suggestion `input_hash` now includes these investment profile fields:

- `profileId`
- `profileVersion`
- `riskTolerance`
- `investmentGoal`
- `experienceLevel`
- `preferredVolatility`
- `preferredHorizon`
- `riskScore`
- `goalScore`
- `experienceScore`
- `profileSource`

It also keeps existing inputs:

- model
- prompt version
- timeframe
- snapshot hashes
- candidate fit signals
- behavior summary fields, including behavior `updatedAt`

Expected:

- Changing a meaningful profile field changes `input_hash`.
- Changing profile version changes `input_hash`.
- Changing behavior profile summary or `updatedAt` changes `input_hash`.
- Same `input_hash` still reuses an existing successful or fallback batch.

## Behavior Profile Integration

Paper-trading buy/sell still recalculates behavior through `UserBehaviorProfileService`.

AI suggestion generation consumes behavior only through `UserBehaviorProfileService.getBehaviorSummaryForSuggestion(...)`.

It must not query paper-trading repositories directly and must not use watchlist, dismissals, views, or clicks as behavior signals.

## Known Limitations

- Full onboarding and quiz-retake write APIs are not implemented in V5B.
- V5B provides backend service hooks for those future modules.
- No public response DTO fields were added for generated-versus-reused detection; trigger results use the existing suggestion response status/message where available.
