# US004 Onboarding Profile Backend Testing Guide

This guide verifies the backend-only US004 onboarding quiz and user investment
profile API.

US004 covers:

- static backend-owned onboarding quiz questions
- deterministic backend scoring
- immutable `user_investment_profile` version rows
- first onboarding and quiz retake flows
- current-user profile response
- safe behavior summary display
- after-commit AI suggestion trigger through the existing trigger facade

US004 must not trust frontend-provided `userId`, score fields, profile enums, or
behavior fields. The backend owns scoring and profile persistence.

## 1. Start Backend

From PowerShell:

```powershell
cd C:\Users\lim\.codex\worktrees\59bb\StockMentor\backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

If `.\mvnw.cmd` fails with the known wrapper launcher issue, use the wrapper
downloaded Maven binary from the local Maven wrapper cache. Example:

```powershell
& "$env:USERPROFILE\.m2\wrapper\dists\apache-maven-3.9.14-bin\1cb7fhup6b5n3bed6kckbrnspv\apache-maven-3.9.14\bin\mvn.cmd" `
  spring-boot:run "-Dspring-boot.run.profiles=dev"
```

In a second PowerShell terminal, set reusable variables:

```powershell
$base = "http://localhost:8080"
$demoAuth = "demo@stockmentor.local:Demo@12345"
$adminAuth = "admin@stockmentor.local:Admin@12345"
```

Create small helpers:

```powershell
function Invoke-Us004Get {
  param(
    [string]$Path,
    [string]$Auth = $demoAuth
  )

  curl.exe -i -u $Auth "$base$Path"
}

function Invoke-Us004Post {
  param(
    [string]$Path,
    [string]$Body,
    [string]$Auth = $demoAuth
  )

  curl.exe -i -X POST -u $Auth "$base$Path" `
    -H "Content-Type: application/json" `
    -d $Body
}
```

## 2. Confirm Dev Users

Use MySQL Workbench, MySQL client, or your IDE database console.

```sql
SELECT user_id, email, username, role, status, is_deleted,
       onboarding_completed, created_at, updated_at
FROM app_user
WHERE email IN ('demo@stockmentor.local', 'admin@stockmentor.local')
ORDER BY user_id;
```

Expected:

- `demo@stockmentor.local` usually has `onboarding_completed = 1`.
- `admin@stockmentor.local` usually has `onboarding_completed = 0`.
- Both users should be `ACTIVE` and `is_deleted = 0`.

Check the demo user's latest investment profile:

```sql
SELECT profile_id, user_id, profile_version, profile_source,
       risk_tolerance, investment_goal, experience_level,
       preferred_volatility, preferred_horizon,
       risk_score, goal_score, experience_score,
       behavior_risk_score, behavior_style, behavior_confidence,
       created_at, updated_at
FROM user_investment_profile
WHERE user_id = (
  SELECT user_id FROM app_user WHERE email = 'demo@stockmentor.local'
)
ORDER BY profile_version DESC, updated_at DESC;
```

Expected:

- Existing dev data may already have profile version `1`.
- `behavior_risk_score`, `behavior_style`, and `behavior_confidence` may differ
  on old seed rows, but US004-created rows should leave these behavior fields
  `NULL`.

## 3. Unauthenticated Security Checks

All US004 endpoints require Basic Auth:

```powershell
curl.exe -i "$base/api/user/profile"
curl.exe -i "$base/api/user/onboarding/questions"
curl.exe -i -X POST "$base/api/user/onboarding"
curl.exe -i -X POST "$base/api/user/onboarding/retake"
```

Expected:

```text
HTTP/1.1 401
```

## 4. Get Onboarding Questions

```powershell
Invoke-Us004Get "/api/user/onboarding/questions"
```

Expected:

- HTTP `200`
- exactly 8 required questions
- stable `questionId` values:

```text
risk_reaction
volatility_comfort
investment_goal
experience_level
investment_horizon
concentration_comfort
loss_tolerance
guidance_preference
```

Expected first question shape:

```json
{
  "questionId": "risk_reaction",
  "text": "If a stock in your paper portfolio drops by 15%, what would you most likely do?",
  "required": true,
  "options": [
    {
      "optionId": "risk_reaction_sell_reduce",
      "label": "I would sell or reduce it because I want to avoid further loss.",
      "description": "I would sell or reduce it because I want to avoid further loss."
    }
  ]
}
```

Response must not include:

```text
riskScore
goalScore
experienceScore
score
weight
riskTolerance
investmentGoal
prompt
openAi
```

Quick PowerShell field check:

```powershell
$base = "http://localhost:8080"

$cred = Get-Credential

$pair = "$($cred.UserName):$($cred.GetNetworkCredential().Password)"
$encoded = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))

$headers = @{
    Authorization = "Basic $encoded"
}

$questions = Invoke-RestMethod `
    -Method Get `
    -Uri "$base/api/user/onboarding/questions" `
    -Headers $headers
```

When prompted by `Get-Credential`, enter:

```text
demo@stockmentor.local
Demo@12345
```

## 5. Get Current User Profile

```powershell
Invoke-Us004Get "/api/user/profile"
```

Expected:

- HTTP `200`
- response belongs to the authenticated user only
- includes `investmentProfile` if the user has a profile
- includes non-null `behaviorSummary`
- does not include `passwordHash`, raw prompts, raw OpenAI responses, token
  usage, API keys, or secrets

Expected fields:

```text
userId
email
username
role
onboardingCompleted
investmentProfile.profileId
investmentProfile.profileVersion
investmentProfile.profileSource
investmentProfile.riskTolerance
investmentProfile.investmentGoal
investmentProfile.experienceLevel
investmentProfile.preferredVolatility
investmentProfile.preferredHorizon
investmentProfile.riskScore
investmentProfile.goalScore
investmentProfile.experienceScore
behaviorSummary.behaviorProfileId
behaviorSummary.behaviorConfidence
behaviorSummary.behaviorStyle
behaviorSummary.behaviorRiskScore
behaviorSummary.behaviorSummaryText
behaviorSummary.sourceNote
```

SQL comparison:

```sql
SELECT user_id, email, username, role, onboarding_completed
FROM app_user
WHERE email = 'demo@stockmentor.local';

SELECT profile_id, profile_version, profile_source,
       risk_tolerance, investment_goal, experience_level,
       preferred_volatility, preferred_horizon,
       risk_score, goal_score, experience_score
FROM user_investment_profile
WHERE user_id = (
  SELECT user_id FROM app_user WHERE email = 'demo@stockmentor.local'
)
ORDER BY profile_version DESC, updated_at DESC
LIMIT 1;
```

Read-only check:

```sql
SELECT COUNT(*) AS behavior_count_before FROM user_behavior_profile;
SELECT COUNT(*) AS batch_count_before FROM stock_ai_suggestion_batch;
SELECT COUNT(*) AS snapshot_count_before FROM stock_analysis_snapshot;
```

Call:

```powershell
Invoke-Us004Get "/api/user/profile"
Invoke-Us004Get "/api/user/onboarding/questions"
```

Then re-run:

```sql
SELECT COUNT(*) AS behavior_count_after FROM user_behavior_profile;
SELECT COUNT(*) AS batch_count_after FROM stock_ai_suggestion_batch;
SELECT COUNT(*) AS snapshot_count_after FROM stock_analysis_snapshot;
```

Expected:

- counts do not change for these GET endpoints

## 6. Prepare A Fresh First-Onboarding Test User

The demo user may already be onboarded, so first-onboarding testing needs a
fresh user. For local dev testing only, copy the demo user's BCrypt password
hash so the new user can log in with `Demo@12345`.

```sql
INSERT INTO app_user (
  email, username, password_hash, role, status, is_deleted,
  onboarding_completed, created_at, updated_at
)
SELECT
  'us004.first@example.com',
  'us004first',
  password_hash,
  'BEGINNER_INVESTOR',
  'ACTIVE',
  0,
  0,
  NOW(),
  NOW()
FROM app_user
WHERE email = 'demo@stockmentor.local';
```

Confirm:

```sql
SELECT user_id, email, username, onboarding_completed
FROM app_user
WHERE email = 'us004.first@example.com';
```

PowerShell auth variable:

```powershell
$firstAuth = "us004.first@example.com:Demo@12345"
```

## 7. First Onboarding Success

Request body:

```powershell
$firstOnboardingBody = @'
{
  "userId": 999999,
  "riskScore": 1,
  "profileSource": "BEHAVIOR_ADJUSTED",
  "answers": [
    { "questionId": "risk_reaction", "optionId": "risk_reaction_wait_review" },
    { "questionId": "volatility_comfort", "optionId": "volatility_medium" },
    { "questionId": "investment_goal", "optionId": "goal_balanced" },
    { "questionId": "experience_level", "optionId": "experience_basic" },
    { "questionId": "investment_horizon", "optionId": "horizon_medium" },
    { "questionId": "concentration_comfort", "optionId": "concentration_balanced" },
    { "questionId": "loss_tolerance", "optionId": "loss_tolerance_medium" },
    { "questionId": "guidance_preference", "optionId": "guidance_balanced_compare" }
  ]
}
'@

$pair = "us004.first@example.com:Demo@12345"
$basic = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))
$headers = @{ Authorization = "Basic $basic" }

Invoke-RestMethod `
  -Method Post `
  -Uri "$base/api/user/onboarding" `
  -Headers $headers `
  -ContentType "application/json" `
  -Body $firstOnboardingBody
```

Expected:

- HTTP `200`
- `onboardingCompleted = true`
- `investmentProfile.profileSource = ONBOARDING`
- `investmentProfile.profileVersion = 1` for a clean user
- backend-calculated values:

```text
riskTolerance       MODERATE
investmentGoal      BALANCED
experienceLevel     BASIC
preferredVolatility MEDIUM
preferredHorizon    MEDIUM_TERM
riskScore           55
goalScore           55
experienceScore     45
```

The submitted `userId`, `riskScore`, and `profileSource` fields must not affect
stored data.

SQL checks:

```sql
SELECT user_id, email, onboarding_completed, updated_at
FROM app_user
WHERE email = 'us004.first@example.com';

SELECT profile_id, user_id, profile_version, profile_source,
       risk_tolerance, investment_goal, experience_level,
       preferred_volatility, preferred_horizon,
       risk_score, goal_score, experience_score,
       behavior_risk_score, behavior_style, behavior_confidence,
       created_at, updated_at
FROM user_investment_profile
WHERE user_id = (
  SELECT user_id FROM app_user WHERE email = 'us004.first@example.com'
)
ORDER BY profile_version DESC;
```

Expected:

- one new profile row
- `profile_source = 'ONBOARDING'`
- behavior fields are `NULL`
- `user_id` is the authenticated user's id, not the submitted `999999`

After-commit trigger check:

```sql
SELECT suggestion_batch_id, user_id, profile_id, profile_version,
       status, trigger_reason, prompt_version, input_hash,
       model, error_message, created_at, expires_at
FROM stock_ai_suggestion_batch
WHERE user_id = (
  SELECT user_id FROM app_user WHERE email = 'us004.first@example.com'
)
ORDER BY suggestion_batch_id DESC;
```

Expected:

- if OpenAI/snapshot prerequisites are available, a suggestion batch may be
  created with `trigger_reason = 'ONBOARDING_COMPLETED'`
- if generation fails, the onboarding API should still succeed because trigger
  failure is caught after commit
- if no usable stock analysis snapshots exist yet, no batch row is created and
  backend logs should include:
  `US004 after-commit AI suggestion trigger completed without batch`

If no batch row appears, check whether stock analysis snapshots are available:

```sql
SELECT symbol, timeframe, risk_category, volatility_label,
       data_source, is_fallback, missing_data_count,
       snapshot_hash, created_at
FROM stock_analysis_snapshot
WHERE timeframe = '7D'
ORDER BY symbol, created_at DESC;
```

Expected:

- suggestion generation needs usable `7D` snapshots for supported symbols
- empty snapshot data can make the trigger complete successfully without saving
  a `stock_ai_suggestion_batch` row

## 8. Repeat First Onboarding Conflict

```powershell
Invoke-Us004Post "/api/user/onboarding" $firstOnboardingBody $firstAuth
```

Expected:

```text
HTTP/1.1 409
```

Body:

```json
{
  "status": 409,
  "message": "Onboarding has already been completed. Use retake instead."
}
```

SQL check:

```sql
SELECT COUNT(*) AS profile_count
FROM user_investment_profile
WHERE user_id = (
  SELECT user_id FROM app_user WHERE email = 'us004.first@example.com'
);
```

Expected:

- profile count does not increase from the rejected repeat first-onboarding call

## 9. Retake Success

Request body:

```powershell
$retakeBody = @'
{
  "answers": [
    { "questionId": "risk_reaction", "optionId": "risk_reaction_buy_more" },
    { "questionId": "volatility_comfort", "optionId": "volatility_high" },
    { "questionId": "investment_goal", "optionId": "goal_growth" },
    { "questionId": "experience_level", "optionId": "experience_intermediate" },
    { "questionId": "investment_horizon", "optionId": "horizon_long" },
    { "questionId": "concentration_comfort", "optionId": "concentration_focused" },
    { "questionId": "loss_tolerance", "optionId": "loss_tolerance_high" },
    { "questionId": "guidance_preference", "optionId": "guidance_growth_opportunities" }
  ]
}
'@

Invoke-Us004Post "/api/user/onboarding/retake" $retakeBody $firstAuth
```

Expected:

- HTTP `200`
- `onboardingCompleted = true`
- `investmentProfile.profileSource = RETAKE_QUIZ`
- `profileVersion = previous max + 1`
- backend-calculated values:

```text
riskTolerance       AGGRESSIVE
investmentGoal      GROWTH
experienceLevel     INTERMEDIATE
preferredVolatility HIGH
preferredHorizon    LONG_TERM
riskScore           85
goalScore           75
experienceScore     70
```

SQL check:

```sql
SELECT profile_id, profile_version, profile_source,
       risk_tolerance, investment_goal, experience_level,
       preferred_volatility, preferred_horizon,
       risk_score, goal_score, experience_score,
       behavior_risk_score, behavior_style, behavior_confidence,
       created_at, updated_at
FROM user_investment_profile
WHERE user_id = (
  SELECT user_id FROM app_user WHERE email = 'us004.first@example.com'
)
ORDER BY profile_version DESC;
```

Expected:

- older profile rows still exist
- latest row has `profile_source = 'RETAKE_QUIZ'`
- latest row has `profile_version = 2` if this is the first retake
- behavior fields on the latest US004-created row are `NULL`

After-commit retake trigger check:

```sql
SELECT suggestion_batch_id, user_id, profile_id, profile_version,
       status, trigger_reason, input_hash, created_at
FROM stock_ai_suggestion_batch
WHERE user_id = (
  SELECT user_id FROM app_user WHERE email = 'us004.first@example.com'
)
ORDER BY suggestion_batch_id DESC;
```

Expected:

- a new/reused batch may appear with `trigger_reason = 'RETAKE_QUIZ'`
- if generation fails, the retake API should still succeed
- this backend enum uses `RETAKE_QUIZ`, not `PROFILE_RETAKEN`
- if no usable stock analysis snapshots exist, logs should clearly say the
  trigger completed without a batch

## 10. Retake Before First Onboarding Conflict

Create a second fresh user:

```sql
INSERT INTO app_user (
  email, username, password_hash, role, status, is_deleted,
  onboarding_completed, created_at, updated_at
)
SELECT
  'us004.retake.blocked@example.com',
  'us004retakeblocked',
  password_hash,
  'BEGINNER_INVESTOR',
  'ACTIVE',
  0,
  0,
  NOW(),
  NOW()
FROM app_user
WHERE email = 'demo@stockmentor.local';
```

PowerShell:

```powershell
$blockedRetakeAuth = "us004.retake.blocked@example.com:Demo@12345"
Invoke-Us004Post "/api/user/onboarding/retake" $retakeBody $blockedRetakeAuth
```

Expected:

```text
HTTP/1.1 409
```

Body:

```json
{
  "status": 409,
  "message": "Complete onboarding before retaking the quiz."
}
```

## 11. Validation Checks

Null or missing answers:

```powershell
Invoke-Us004Post "/api/user/onboarding" "{}" $blockedRetakeAuth
```

Expected:

```text
HTTP/1.1 400
```

Message:

```text
Onboarding answers are required
```

Empty answers:

```powershell
Invoke-Us004Post "/api/user/onboarding" '{ "answers": [] }' $blockedRetakeAuth
```

Expected message:

```text
Onboarding answers must not be empty
```

Missing required question:

```powershell
$missingAnswerBody = @'
{
  "answers": [
    { "questionId": "volatility_comfort", "optionId": "volatility_medium" },
    { "questionId": "investment_goal", "optionId": "goal_balanced" },
    { "questionId": "experience_level", "optionId": "experience_basic" },
    { "questionId": "investment_horizon", "optionId": "horizon_medium" },
    { "questionId": "concentration_comfort", "optionId": "concentration_balanced" },
    { "questionId": "loss_tolerance", "optionId": "loss_tolerance_medium" },
    { "questionId": "guidance_preference", "optionId": "guidance_balanced_compare" }
  ]
}
'@

Invoke-Us004Post "/api/user/onboarding" $missingAnswerBody $blockedRetakeAuth
```

Expected message:

```text
Missing onboarding answer for question: risk_reaction
```

Duplicate question:

```powershell
$duplicateAnswerBody = @'
{
  "answers": [
    { "questionId": "risk_reaction", "optionId": "risk_reaction_wait_review" },
    { "questionId": "risk_reaction", "optionId": "risk_reaction_buy_more" },
    { "questionId": "volatility_comfort", "optionId": "volatility_medium" },
    { "questionId": "investment_goal", "optionId": "goal_balanced" },
    { "questionId": "experience_level", "optionId": "experience_basic" },
    { "questionId": "investment_horizon", "optionId": "horizon_medium" },
    { "questionId": "concentration_comfort", "optionId": "concentration_balanced" },
    { "questionId": "loss_tolerance", "optionId": "loss_tolerance_medium" },
    { "questionId": "guidance_preference", "optionId": "guidance_balanced_compare" }
  ]
}
'@

Invoke-Us004Post "/api/user/onboarding" $duplicateAnswerBody $blockedRetakeAuth
```

Expected message:

```text
Duplicate onboarding answer for question: risk_reaction
```

Unknown question:

```powershell
$unknownQuestionBody = $firstOnboardingBody.Replace('"risk_reaction"', '"bad_question"')
Invoke-Us004Post "/api/user/onboarding" $unknownQuestionBody $blockedRetakeAuth
```

Expected message:

```text
Unknown onboarding question: bad_question
```

Unknown option:

```powershell
$unknownOptionBody = $firstOnboardingBody.Replace('"risk_reaction_wait_review"', '"bad_option"')
Invoke-Us004Post "/api/user/onboarding" $unknownOptionBody $blockedRetakeAuth
```

Expected message:

```text
Unknown onboarding option: bad_option
```

Wrong option for question:

```powershell
$wrongOptionBody = $firstOnboardingBody.Replace('"risk_reaction_wait_review"', '"volatility_medium"')
Invoke-Us004Post "/api/user/onboarding" $wrongOptionBody $blockedRetakeAuth
```

Expected message:

```text
Option volatility_medium does not belong to question: risk_reaction
```

SQL check after rejected requests:

```sql
SELECT COUNT(*) AS profile_count
FROM user_investment_profile
WHERE user_id = (
  SELECT user_id FROM app_user WHERE email = 'us004.retake.blocked@example.com'
);
```

Expected:

- rejected validation and retake requests do not create profile rows

## 12. Current-User Isolation Check

Call profile as the first test user:

```powershell
Invoke-Us004Get "/api/user/profile" $firstAuth
```

Call profile as the demo user:

```powershell
Invoke-Us004Get "/api/user/profile" $demoAuth
```

Expected:

- each response returns only the authenticated user
- no request accepts `userId` from the client

SQL comparison:

```sql
SELECT u.user_id, u.email, p.profile_id, p.profile_version, p.profile_source
FROM app_user u
LEFT JOIN user_investment_profile p ON p.user_id = u.user_id
WHERE u.email IN ('demo@stockmentor.local', 'us004.first@example.com')
ORDER BY u.email, p.profile_version;
```

## 13. AI Suggestion Compatibility Checks

US004 should not rewrite US006. The existing AI suggestion generation should
continue to use the latest declared profile and behavior summary.

Check latest suggestion batches for the US004 test user:

```sql
SELECT suggestion_batch_id, user_id, profile_id, profile_version,
       status, trigger_reason, prompt_version, input_hash,
       model, fallback_used, error_message, created_at, expires_at
FROM stock_ai_suggestion_batch
WHERE user_id = (
  SELECT user_id FROM app_user WHERE email = 'us004.first@example.com'
)
ORDER BY suggestion_batch_id DESC;
```

Expected:

- `profile_id` and `profile_version` match a saved profile row when a batch is
  created
- `trigger_reason` is normally `ONBOARDING_COMPLETED` or `RETAKE_QUIZ`
- unchanged inputs can reuse an existing successful/fallback batch by
  `input_hash`
- no batch row is expected when generation exits early because no usable
  snapshots exist; in that case use backend logs to confirm the trigger ran

Check profile fields that affect the AI input:

```sql
SELECT profile_id, profile_version, profile_source,
       risk_tolerance, investment_goal, experience_level,
       preferred_volatility, preferred_horizon,
       risk_score, goal_score, experience_score
FROM user_investment_profile
WHERE user_id = (
  SELECT user_id FROM app_user WHERE email = 'us004.first@example.com'
)
ORDER BY profile_version DESC;
```

Important:

- `profile_version`, risk/goal/experience fields, volatility, horizon, scores,
  and `profile_source` are meaningful AI-suggestion inputs.
- Do not add `profile.updated_at` to the input hash just for US004 testing.
- US004 runs trigger work after commit inside a new transaction so suggestion
  rows can commit independently from the already-finished onboarding
  transaction.

## 14. Automated Verification

Run the focused US004 tests:

```powershell
cd C:\Users\lim\.codex\worktrees\59bb\StockMentor\backend
.\mvnw.cmd "-Dtest=UserProfileServiceImplTests,UserProfileControllerSecurityTests" test
```

Run the full backend tests and compile:

```powershell
cd C:\Users\lim\.codex\worktrees\59bb\StockMentor\backend
.\mvnw.cmd clean test
.\mvnw.cmd clean compile
```

If `.\mvnw.cmd` fails with the known wrapper issue, use the wrapper-downloaded
Maven binary:

```powershell
& "$env:USERPROFILE\.m2\wrapper\dists\apache-maven-3.9.14-bin\1cb7fhup6b5n3bed6kckbrnspv\apache-maven-3.9.14\bin\mvn.cmd" `
  "-Dtest=UserProfileServiceImplTests,UserProfileControllerSecurityTests" test

& "$env:USERPROFILE\.m2\wrapper\dists\apache-maven-3.9.14-bin\1cb7fhup6b5n3bed6kckbrnspv\apache-maven-3.9.14\bin\mvn.cmd" `
  clean test

& "$env:USERPROFILE\.m2\wrapper\dists\apache-maven-3.9.14-bin\1cb7fhup6b5n3bed6kckbrnspv\apache-maven-3.9.14\bin\mvn.cmd" `
  clean compile
```

From repo root:

```powershell
cd C:\Users\lim\.codex\worktrees\59bb\StockMentor
git diff --check
```

Expected:

- focused tests pass
- full test suite passes
- compile passes
- `git diff --check` reports no whitespace errors

## 15. Cleanup For Local Dev Test Users

If you want to remove the local-only test users, delete specific rows in a safe
order. Delete only exact rows for the test emails.

```sql
DELETE FROM stock_ai_suggestion_item
WHERE suggestion_batch_id IN (
  SELECT suggestion_batch_id
  FROM stock_ai_suggestion_batch
  WHERE user_id IN (
    SELECT user_id
    FROM app_user
    WHERE email IN ('us004.first@example.com', 'us004.retake.blocked@example.com')
  )
);

DELETE FROM stock_ai_suggestion_batch
WHERE user_id IN (
  SELECT user_id
  FROM app_user
  WHERE email IN ('us004.first@example.com', 'us004.retake.blocked@example.com')
);

DELETE FROM user_behavior_profile
WHERE user_id IN (
  SELECT user_id
  FROM app_user
  WHERE email IN ('us004.first@example.com', 'us004.retake.blocked@example.com')
);

DELETE FROM user_investment_profile
WHERE user_id IN (
  SELECT user_id
  FROM app_user
  WHERE email IN ('us004.first@example.com', 'us004.retake.blocked@example.com')
);

DELETE FROM app_user
WHERE email IN ('us004.first@example.com', 'us004.retake.blocked@example.com');
```

These SQL statements are optional and intended only for local dev data cleanup.
