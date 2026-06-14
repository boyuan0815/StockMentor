# US001-US002 Auth Backend Testing Guide

This guide verifies the backend-only account entry flow for StockMentor:

- US001 Register Account
- US002 Login Account
- US002 current-user bootstrap

The backend keeps the existing Spring Security HTTP Basic Auth design for the
FYP MVP. These endpoints do not issue JWTs, sessions, refresh tokens, OTPs, or
email verification links.

US001-US002 must not call OpenAI, Twelve Data, stock backfill/retrieval flows,
AI suggestion generation, AI explanation generation, behavior recalculation, or
paper-trading service methods that auto-create accounts.

## 1. Start Backend

From PowerShell:

```powershell
cd C:\StockMentor\backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

If `.\mvnw.cmd` fails with the known wrapper launcher issue, use the wrapper
downloaded Maven binary from the local Maven wrapper cache. Example:

```powershell
& "$env:USERPROFILE\.m2\wrapper\dists\apache-maven-3.9.14-bin\1cb7fhup6b5n3bed6kckbrnspv\apache-maven-3.9.14\bin\mvn.cmd" `
  spring-boot:run "-Dspring-boot.run.profiles=dev"
```

In a second PowerShell terminal:

```powershell
$base = "http://localhost:8080"
$demoAuth = "demo@stockmentor.local:Demo@12345"
$adminAuth = "admin@stockmentor.local:Admin@12345"
```

## 2. Register A New Beginner Account

Username rules:

- 3-30 characters
- letters, numbers, dot, underscore, and hyphen only
- `@`, spaces, and other special characters are rejected

```powershell
$registerBody = @{
  email = "new.beginner_2@example.com"
  username = "new.beginner_2"
  password = "Beginner@12345"
  confirmPassword = "Beginner@12345"
} | ConvertTo-Json

$registerBody | Set-Content request.json

curl.exe -i -X POST "$base/api/auth/register" `
  -H "Content-Type: application/json" `
  --data-binary "@request.json"
```

PowerShell note: avoid passing pretty multi-line JSON directly with
`--data $body` to `curl.exe`; it may split the body and cause
`Invalid JSON request body`. In this guide, write JSON to `request.json` and
send it with `--data-binary "@request.json"`.

Expected:

- `HTTP/1.1 201`
- `role = BEGINNER_INVESTOR`
- `status = ACTIVE`
- `onboardingCompleted = false`
- `hasInvestmentProfile = false`
- `mustCompleteOnboarding = true`
- no `passwordHash`
- no `token` or `jwt`

Database checks:

```sql
SELECT user_id,
       email,
       username,
       role,
       status,
       is_deleted,
       onboarding_completed,
       password_hash,
       last_login_at
FROM app_user
WHERE email = 'new.beginner_2@example.com';
```

Expected:

- email is stored lowercase
- password hash is BCrypt, not the raw password
- `is_deleted = 0`
- `onboarding_completed = 0`
- `last_login_at` is still null

## 3. Duplicate Registration Checks

Duplicate email should return `409 Conflict`:

```powershell
curl.exe -i -X POST "$base/api/auth/register" `
  -H "Content-Type: application/json" `
  --data-binary "@request.json"
```

Case-variant duplicate email should also return `409 Conflict`:

```powershell
$duplicateEmailBody = @{
  email = "new.beginner_2@example.com"
  username = "anotherbeginner"
  password = "Beginner@12345"
  confirmPassword = "Beginner@12345"
} | ConvertTo-Json

$duplicateEmailBody | Set-Content request.json

curl.exe -i -X POST "$base/api/auth/register" `
  -H "Content-Type: application/json" `
  --data-binary "@request.json"
```

Duplicate username should return `409 Conflict`:

```powershell
$duplicateUsernameBody = @{
  email = "new.beginner.2@example.com"
  username = "NEW.BEGINNER_2"
  password = "Beginner@12345"
  confirmPassword = "Beginner@12345"
} | ConvertTo-Json

$duplicateUsernameBody | Set-Content request.json

curl.exe -i -X POST "$base/api/auth/register" `
  -H "Content-Type: application/json" `
  --data-binary "@request.json"
```

## 4. Registration Validation Checks

Invalid email should return `400 Bad Request`:

```powershell
$invalidEmailBody = @{
  email = "not-an-email"
  username = "validuser1"
  password = "Beginner@12345"
  confirmPassword = "Beginner@12345"
} | ConvertTo-Json

$invalidEmailBody | Set-Content request.json

curl.exe -i -X POST "$base/api/auth/register" `
  -H "Content-Type: application/json" `
  --data-binary "@request.json"
```

Password mismatch should return `400 Bad Request`:

```powershell
$mismatchBody = @{
  email = "valid.mismatch@example.com"
  username = "validuser2"
  password = "Beginner@12345"
  confirmPassword = "Different@12345"
} | ConvertTo-Json

$mismatchBody | Set-Content request.json

curl.exe -i -X POST "$base/api/auth/register" `
  -H "Content-Type: application/json" `
  --data-binary "@request.json"
```

Password shorter than 8 characters or longer than 72 characters should return
`400 Bad Request`.

Username containing `@` should return `400 Bad Request`:

```powershell
$invalidUsernameBody = @{
  email = "valid.username@example.com"
  username = "bad@example"
  password = "Beginner@12345"
  confirmPassword = "Beginner@12345"
} | ConvertTo-Json

$invalidUsernameBody | Set-Content request.json

curl.exe -i -X POST "$base/api/auth/register" `
  -H "Content-Type: application/json" `
  --data-binary "@request.json"
```

Username containing spaces should also return `400 Bad Request`:

```powershell
$spaceUsernameBody = @{
  email = "space.username@example.com"
  username = "bad user"
  password = "Beginner@12345"
  confirmPassword = "Beginner@12345"
} | ConvertTo-Json

$spaceUsernameBody | Set-Content request.json

curl.exe -i -X POST "$base/api/auth/register" `
  -H "Content-Type: application/json" `
  --data-binary "@request.json"
```

## 5. Login With Basic Auth

`POST /api/auth/login` validates the Basic Auth header and returns the safe user
summary. It does not issue a token.

```powershell
$newAuth = "new.beginner_2@example.com:Beginner@12345"

curl.exe -i -X POST -u $newAuth "$base/api/auth/login"
```

Expected:

- `HTTP/1.1 200`
- safe user summary
- `role = BEGINNER_INVESTOR`
- `lastLoginAt` is populated
- no `passwordHash`
- no `token` or `jwt`

Database check:

```sql
SELECT email, last_login_at
FROM app_user
WHERE email = 'new.beginner_2@example.com';
```

Expected:

- `last_login_at` is no longer null after explicit login

## 6. Current User Bootstrap

`GET /api/auth/me` lets the Expo frontend restore current-user state with the
same Basic Auth credentials. It must not mutate `lastLoginAt`.

```powershell
curl.exe -i -u $newAuth "$base/api/auth/me"
```

Expected:

- `HTTP/1.1 200`
- same safe user summary shape as login
- no `passwordHash`
- no `token` or `jwt`
- `last_login_at` in the database stays unchanged from the previous login

## 7. Invalid And Disabled Login Checks

Invalid credentials should return `401 Unauthorized`:

```powershell
curl.exe -i -X POST -u "new.beginner_2@example.com:WrongPassword" "$base/api/auth/login"
```

Disable the user manually for local testing:

```sql
UPDATE app_user
SET status = 'INACTIVE'
WHERE email = 'new.beginner_2@example.com';
```

Then try login again:

```powershell
curl.exe -i -X POST -u $newAuth "$base/api/auth/login"
```

Expected:

- `401 Unauthorized`

Re-enable the user for later testing:

```sql
UPDATE app_user
SET status = 'ACTIVE'
WHERE email = 'new.beginner_2@example.com';
```

## 8. Admin And Demo User Login

Demo beginner login:

```powershell
curl.exe -i -X POST -u $demoAuth "$base/api/auth/login"
```

Expected:

- `role = BEGINNER_INVESTOR`

Admin login:

```powershell
curl.exe -i -X POST -u $adminAuth "$base/api/auth/login"
```

Expected:

- `role = ADMIN`
- no admin token is returned
- admin-only endpoints still require `X-Admin-Token` separately

## 9. Confirm No Side Effects

After registration/login/me, check that no unrelated records were created for
the new user:

```sql
SELECT COUNT(*) FROM user_investment_profile
WHERE user_id = (SELECT user_id FROM app_user WHERE email = 'new.beginner_2@example.com');

SELECT COUNT(*) FROM user_behavior_profile
WHERE user_id = (SELECT user_id FROM app_user WHERE email = 'new.beginner_2@example.com');

SELECT COUNT(*) FROM paper_trading_account
WHERE user_id = (SELECT user_id FROM app_user WHERE email = 'new.beginner_2@example.com');

SELECT COUNT(*) FROM paper_position
WHERE user_id = (SELECT user_id FROM app_user WHERE email = 'new.beginner_2@example.com');

SELECT COUNT(*) FROM paper_trade_transaction
WHERE user_id = (SELECT user_id FROM app_user WHERE email = 'new.beginner_2@example.com');

SELECT COUNT(*) FROM user_watchlist
WHERE user_id = (SELECT user_id FROM app_user WHERE email = 'new.beginner_2@example.com');

SELECT COUNT(*) FROM stock_ai_suggestion_batch
WHERE user_id = (SELECT user_id FROM app_user WHERE email = 'new.beginner_2@example.com');

SELECT COUNT(*) FROM stock_ai_suggestion_item
WHERE user_id = (SELECT user_id FROM app_user WHERE email = 'new.beginner_2@example.com');
```

Expected:

- all counts are `0`

## 10. Frontend MVP Meaning

For the FYP MVP, the Expo frontend should:

- call `POST /api/auth/register` without Basic Auth
- call `POST /api/auth/login` with Basic Auth credentials
- store/reuse the Basic Auth credentials only for the MVP/demo flow
- call protected backend APIs with the same Basic Auth credentials
- use `GET /api/auth/me` when reopening or refreshing app state
- route to onboarding when `mustCompleteOnboarding = true`

The backend does not issue JWTs in this scope.
