# US006 V6 Backend Testing Guide

V6 adds admin monitoring for AI stock suggestions and refresh-job tracking for scheduled/admin-triggered refresh runs.

V6 admin endpoints require both:

- Basic Auth as an `ADMIN` user.
- Valid `X-Admin-Token`.

Dev credentials from the existing dev seeder:

```text
admin@stockmentor.local:Admin@12345
```

Use your local admin token from `stockmentor.admin.token`.

## Start Backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

If the wrapper launcher fails locally, use the wrapper-downloaded Maven binary workaround already used in earlier US006 testing.

## PowerShell Variables

```powershell
$base = "http://localhost:8080"
$adminAuth = "admin@stockmentor.local:Admin@12345"
$adminToken = "<your-local-admin-token>"
```

## Security Checks

No Basic Auth should fail:

```powershell
curl.exe -i "$base/api/admin/ai-suggestions/batches" `
  -H "X-Admin-Token: $adminToken"
```

Admin Basic Auth without `X-Admin-Token` should fail with `401` and `Invalid admin token`:

```powershell
curl.exe -i -u $adminAuth "$base/api/admin/ai-suggestions/batches"
```

Admin Basic Auth with wrong token should fail with `401` and `Invalid admin token`:

```powershell
curl.exe -i -u $adminAuth "$base/api/admin/ai-suggestions/batches" `
  -H "X-Admin-Token: wrong"
```

Admin Basic Auth with valid token should succeed:

```powershell
curl.exe -i -u $adminAuth "$base/api/admin/ai-suggestions/batches" `
  -H "X-Admin-Token: $adminToken"
```

## Batch Monitoring

List batches:

```powershell
curl.exe -i -u $adminAuth "$base/api/admin/ai-suggestions/batches?page=0&size=20" `
  -H "X-Admin-Token: $adminToken"
```

Filter examples:

```powershell
curl.exe -i -u $adminAuth "$base/api/admin/ai-suggestions/batches?status=SUCCESS&triggerReason=MANUAL_REFRESH" `
  -H "X-Admin-Token: $adminToken"

curl.exe -i -u $adminAuth "$base/api/admin/ai-suggestions/batches?from=2026-06-01&to=2026-06-08" `
  -H "X-Admin-Token: $adminToken"
```

Expected row fields include:

```text
batchId, userId, userEmail, status, triggerReason, analysisTimeframe,
model, promptVersion, promptTokens, completionTokens, totalTokens,
finishReason, fallbackUsed, errorMessage, createdAt, expiresAt,
suggestedSymbols, itemCount
```

View one batch:

```powershell
$batchId = 1

curl.exe -i -u $adminAuth "$base/api/admin/ai-suggestions/batches/$batchId" `
  -H "X-Admin-Token: $adminToken"
```

The detail response includes item rows with:

```text
itemId, symbol, rankNo, matchScore, riskLevel, suggestionLabel,
shortReason, status, snapshotId, createdAt, updatedAt
```

The response must not include raw prompt, raw OpenAI response, API key, service tier, fingerprint, or secrets.

## Failure And Fallback Monitoring

```powershell
curl.exe -i -u $adminAuth "$base/api/admin/ai-suggestions/failures?page=0&size=20" `
  -H "X-Admin-Token: $adminToken"
```

Expected statuses:

```text
FAILED
FALLBACK_CACHED
FALLBACK_RULE_BASED
```

## Usage Summary

```powershell
curl.exe -i -u $adminAuth "$base/api/admin/ai-suggestions/usage-summary" `
  -H "X-Admin-Token: $adminToken"
```

Expected fields:

```text
totalBatches
successCount
failedCount
fallbackCachedCount
fallbackRuleBasedCount
totalPromptTokens
totalCompletionTokens
totalTokens
groupedByTriggerReason
groupedByStatus
```

Null token fields count as `0`.

## Admin Manual Scheduled Refresh

This endpoint runs the same scheduled refresh path for demo/testing. It bypasses manual refresh cooldown but still uses `input_hash` reuse.

```powershell
curl.exe -i -X POST -u $adminAuth "$base/api/admin/ai-suggestions/scheduled-refresh/run" `
  -H "X-Admin-Token: $adminToken"
```

Expected response fields:

```text
jobId
status
triggeredBy
triggeredByUserId
startedAt
finishedAt
processedUsers
skippedUsers
successCount
reusedCount
fallbackCount
failedCount
message
```

Expected `triggeredBy`:

```text
ADMIN_MANUAL
```

If all users are skipped or reused and no failures occur, `status` should still be:

```text
SUCCESS
```

## Refresh Job Monitoring

List jobs:

```powershell
curl.exe -i -u $adminAuth "$base/api/admin/ai-suggestions/refresh-jobs?page=0&size=20" `
  -H "X-Admin-Token: $adminToken"
```

Filter examples:

```powershell
curl.exe -i -u $adminAuth "$base/api/admin/ai-suggestions/refresh-jobs?status=SUCCESS" `
  -H "X-Admin-Token: $adminToken"

curl.exe -i -u $adminAuth "$base/api/admin/ai-suggestions/refresh-jobs?triggeredBy=ADMIN_MANUAL" `
  -H "X-Admin-Token: $adminToken"
```

View one job:

```powershell
$jobId = 1

curl.exe -i -u $adminAuth "$base/api/admin/ai-suggestions/refresh-jobs/$jobId" `
  -H "X-Admin-Token: $adminToken"
```

## MySQL Checks

Suggestion batches:

```sql
SELECT suggestion_batch_id, user_id, status, trigger_reason, prompt_version,
       input_hash, model, prompt_tokens, completion_tokens, total_tokens,
       error_message, created_at, expires_at
FROM stock_ai_suggestion_batch
ORDER BY suggestion_batch_id DESC;
```

Suggestion items:

```sql
SELECT suggestion_item_id, suggestion_batch_id, symbol, rank_no, match_score,
       risk_level, suggestion_label, status, analysis_snapshot_id
FROM stock_ai_suggestion_item
ORDER BY suggestion_batch_id DESC, rank_no ASC;
```

Refresh jobs:

```sql
SELECT job_id, triggered_by, triggered_by_user_id, status, started_at, finished_at,
       processed_users, skipped_users, success_count, reused_count,
       fallback_count, failed_count, message
FROM ai_suggestion_refresh_job
ORDER BY job_id DESC;
```

## Postman Structure

Suggested collection:

```text
US006 V6 Admin
- Security
  - no auth batches
  - admin no token
  - admin wrong token
  - admin valid token
- Batch Monitoring
  - list batches
  - batch detail
  - failures
  - usage summary
- Scheduler Management
  - run scheduled refresh manually
  - list refresh jobs
  - refresh job detail
```

Postman setup:

- Authorization: Basic Auth
  - username: `admin@stockmentor.local`
  - password: `Admin@12345`
- Header:
  - `X-Admin-Token: <your-local-admin-token>`

## Regression Checks

Normal user GET remains read-only:

```powershell
$beginnerAuth = "demo@stockmentor.local:Demo@12345"

curl.exe -i -u $beginnerAuth "$base/api/stocks/ai-suggestions"
```

This GET must not:

- call OpenAI
- generate suggestions
- create snapshots
- create refresh jobs
- mutate suggestion batches

Manual refresh cooldown should still work:

```powershell
curl.exe -i -X POST -u $beginnerAuth "$base/api/stocks/ai-suggestions/refresh"
curl.exe -i -X POST -u $beginnerAuth "$base/api/stocks/ai-suggestions/refresh"
```

## Verification

```powershell
cd backend
.\mvnw.cmd clean test
.\mvnw.cmd clean compile
```

If `mvnw.cmd` fails with the known local launcher issue, use the wrapper-downloaded Maven binary workaround and document it.
