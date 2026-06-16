# US003 Admin User Management Backend Testing Guide

This guide verifies the backend-only admin user-management flow for StockMentor:

- list users
- filter/search users
- view safe user detail
- disable a user
- verify disabled login returns `401`
- re-enable a user

US003 does not hard-delete users, restore deleted users, update roles, create
users, reset passwords, or add JWT/session/token behavior. Status changes never
modify `isDeleted`.

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
$adminAuth = "admin@stockmentor.local:Admin@12345"
$demoAuth = "demo@stockmentor.local:Demo@12345"
$adminToken = "replace-with-your-stockmentor.admin.token"
```

## 2. List Users

```powershell
curl.exe -i -u $adminAuth `
  -H "X-Admin-Token: $adminToken" `
  "$base/api/admin/users"
```

Expected:

- `HTTP/1.1 200`
- paged response with `content`, `page`, `size`, `totalElements`, `totalPages`
- no `passwordHash`
- non-deleted users only by default

## 3. Filter Users

Search matches email or username case-insensitively:

```powershell
curl.exe -i -u $adminAuth `
  -H "X-Admin-Token: $adminToken" `
  "$base/api/admin/users?search=demo&page=0&size=20"
```

Filters combine with `AND`:

```powershell
curl.exe -i -u $adminAuth `
  -H "X-Admin-Token: $adminToken" `
  "$base/api/admin/users?email=demo&username=demo&role=BEGINNER_INVESTOR&status=ACTIVE"
```

Expected:

- `HTTP/1.1 200`
- only users matching every provided filter are returned
- invalid `role` or `status` returns `400`
- requested `size` above `100` is capped to `100`

## 4. Get User Detail

Use a `userId` from the list response:

```powershell
$userId = 1

curl.exe -i -u $adminAuth `
  -H "X-Admin-Token: $adminToken" `
  "$base/api/admin/users/$userId"
```

Expected:

- `HTTP/1.1 200`
- response sections: `user`, `latestInvestmentProfile`, `behaviorSummary`, `paperTradingSummary`
- missing summaries may be `null`
- `paperTradingSummary` is `null` when the user has no paper-trading account
- no paper-trading account is created by viewing detail
- no `passwordHash`

## 5. Disable User

```powershell
$disableBody = @{
  status = "INACTIVE"
} | ConvertTo-Json

$disableBody | Set-Content request.json

curl.exe -i -X PATCH -u $adminAuth `
  -H "Content-Type: application/json" `
  -H "X-Admin-Token: $adminToken" `
  --data-binary "@request.json" `
  "$base/api/admin/users/$userId/status"
```

Expected:
- `HTTP/1.1 200`
- `user.status = INACTIVE`
```powershell
curl.exe -i -X PATCH -u $adminAuth `
  -H "Content-Type: application/json" `
  -H "X-Admin-Token: $adminToken" `
  --data-binary "@request.json" `
  "$base/api/admin/users/$userId/status"
```
Expected:
- repeating the same request is successful and leaves the status unchanged

```
select user_id from app_user
where role = 'ADMIN';
```
```powershell
$adminId = <YOUR_ADMIN_ID>

$disableBody = @{
  status = "INACTIVE"
} | ConvertTo-Json

$disableBody | Set-Content request.json

curl.exe -i -X PATCH -u $adminAuth `
  -H "Content-Type: application/json" `
  -H "X-Admin-Token: $adminToken" `
  --data-binary "@request.json" `
  "$base/api/admin/users/$adminId/status"
```
- disabling your own admin account returns `409`
- disabling the last active non-deleted admin returns `409`

```powershell
$suspendedBody = @{
  status = "SUSPENDED"
} | ConvertTo-Json

$suspendedBody | Set-Content request.json

curl.exe -i -X PATCH -u $adminAuth `
  -H "Content-Type: application/json" `
  -H "X-Admin-Token: $adminToken" `
  --data-binary "@request.json" `
  "$base/api/admin/users/$userId/status"
```
- status `SUSPENDED` returns `400`

```
update app_user
set is_deleted = True
where user_id = 4;
```
```powershell
$deletedAccountId = 4

$disableBody = @{
  status = "INACTIVE"
} | ConvertTo-Json

$disableBody | Set-Content request.json

curl.exe -i -X PATCH -u $adminAuth `
  -H "Content-Type: application/json" `
  -H "X-Admin-Token: $adminToken" `
  --data-binary "@request.json" `
  "$base/api/admin/users/$deletedAccountId/status"
```
- status update on a deleted user returns `409`

## 6. Verify Disabled Login Fails

For the disabled user:

```powershell
$disabledUserAuth = "demo@stockmentor.local:Demo@12345"

curl.exe -i -X POST -u $disabledUserAuth "$base/api/auth/login"
```

Expected:

- `HTTP/1.1 401`

## 7. Re-enable User

```powershell
$enableBody = @{
  status = "ACTIVE"
} | ConvertTo-Json

$enableBody | Set-Content request.json

curl.exe -i -X PATCH -u $adminAuth `
  -H "Content-Type: application/json" `
  -H "X-Admin-Token: $adminToken" `
  --data-binary "@request.json" `
  "$base/api/admin/users/$userId/status"
```

Expected:

- `HTTP/1.1 200`
- `user.status = ACTIVE`
- the user can authenticate again if `isDeleted = false`

```powershell
$enabledUserAuth = "demo@stockmentor.local:Demo@12345"

curl.exe -i -X POST -u $enabledUserAuth "$base/api/auth/login"
```

## 8. Security Checks

Missing Basic Auth:

```powershell
curl.exe -i -H "X-Admin-Token: $adminToken" "$base/api/admin/users"
```

Expected: `401`

Beginner user with admin token:

```powershell
curl.exe -i -u $demoAuth `
  -H "X-Admin-Token: $adminToken" `
  "$base/api/admin/users"
```

Expected: `403`

Admin without token:

```powershell
curl.exe -i -u $adminAuth "$base/api/admin/users"
```

Expected:

- `401`
- message: `Invalid admin token`
