# SECJ4383 Project 1 Part A Step-By-Step Guide

Use this guide to prepare screenshots, record the video, and explain the StockMentor deployment workflow. The coursework deployment is intentionally simple: Jenkins builds and tests the Spring Boot backend and Expo Web frontend, starts MySQL/backend/frontend with Docker Compose, waits for safe health endpoints, runs JMeter, then pushes Docker images to Docker Hub.

Do not show `.env`, Jenkins secret files, API keys, database passwords, admin tokens, Docker Hub tokens, or Basic Auth passwords in screenshots or logs.

## 0. Deployment Story

StockMentor has three deployable parts for Part A:

- Backend: Spring Boot `4.0.5`, Docker image `DOCKERHUB_NAMESPACE/stockmentor-backend`.
- Database: MySQL Docker Compose service with named volume `stockmentor_mysql_data`.
- Web frontend: Expo Web static export served by Nginx at `http://localhost:8081`.

MySQL has two different addresses in the demo:

- Backend container to MySQL container: `mysql:3306`.
- Windows host tools such as MySQL Workbench: `localhost:3307`, because local host port `3306` is already used.

Mobile deployment for this coursework means the Expo mobile app connects to the deployed backend URL by LAN, ngrok, or staging URL. It does not mean App Store or Play Store distribution.

Production improvements are future work: HTTPS, managed database, real secret manager, migrations with Flyway or Liquibase, cloud hosting, monitoring, and stronger rollback strategy. Do not add these to Part A unless the lecturer asks.

## 1. Preflight Checks

Run these before editing or recording.

### Command: Git status

- Location: repository root `C:\StockMentor`
- Command:

```powershell
git status --short
```

- Expected output: no unexpected files, or only files from this deployment task.
- Screenshot: capture the terminal after the command.
- Common quick fix: if unrelated files appear, do not commit them. Ask the owner or keep them out of screenshots.

### Command: Git branch

- Location: repository root `C:\StockMentor`
- Command:

```powershell
git branch --show-current
```

- Expected output: current branch name, normally `main`.
- Screenshot: capture the branch name.
- Common quick fix: if the branch is unexpected, switch only after confirming with the team.

### Command: Recent commits

- Location: repository root `C:\StockMentor`
- Command:

```powershell
git log --oneline -5
```

- Expected output: five recent commit hashes and messages.
- Screenshot: capture the terminal for repository evidence.
- Common quick fix: if no commit shows the Jira key yet, use Jira-key commit messages for the next commits, for example `G8-3 ci: add Jenkins pipeline`.

### Command: Java version

- Location: any terminal
- Command:

```powershell
java -version
```

- Expected output: Java 17 is available.
- Screenshot: capture the Java version.
- Common quick fix: install Java 17 or configure `JAVA_HOME`.

### Command: Backend build tool and Spring Boot version

- Location: `C:\StockMentor\backend`
- Command:

```powershell
Select-String -Path pom.xml -Pattern "<version>4.0.5</version>","<java.version>17</java.version>"
```

- Expected output: Spring Boot parent version `4.0.5` and Java `17`.
- Screenshot: capture the terminal result.
- Common quick fix: do not downgrade Spring Boot. The current project is confirmed on Spring Boot `4.0.5`.

### Command: Node and npm versions

- Location: any terminal
- Command:

```powershell
node -v
npm -v
```

- Expected output: Node and npm versions print successfully.
- Screenshot: capture both lines.
- Common quick fix: install Node.js LTS if either command is missing.

### Command: Docker version

- Location: any terminal
- Command:

```powershell
docker --version
docker compose version
```

- Expected output: Docker and Docker Compose versions print successfully.
- Screenshot: capture both lines.
- Common quick fix: start Docker Desktop, then rerun the command.

## 2. A1 Jira Project

Create a Jira Scrum or Kanban project named `StockMentor DevOps Part A`. Use project key `G8`.

### Jira issue list

Create at least these five issues.

| Issue | Title | Description | Assignee | Priority | Story points | Example commit message |
| --- | --- | --- | --- | --- | --- | --- |
| G8-1 | Add backend Docker deployment | Create a multi-stage Dockerfile for Spring Boot backend. | Lim Bo Yuan | High | 3 | `G8-1 ci: add backend Dockerfile` |
| G8-2 | Add MySQL Docker Compose service | Add MySQL service, named volume, backend env connection, and healthchecks. | Khoo Teong Lee | High | 3 | `G8-2 ci: add mysql compose deployment` |
| G8-3 | Add Jenkins CI/CD pipeline | Add Jenkinsfile with build, test, Docker build, Compose deploy, JMeter, Docker push, and archive report. | Lim Bo Yuan | High | 5 | `G8-3 ci: add Jenkins deployment pipeline` |
| G8-4 | Add JMeter performance test | Test `/api/health` and `/api/health/database` with 50 users. | Lim Yu An | Medium | 2 | `G8-4 test: add JMeter health performance plan` |
| G8-5 | Add Docker Hub teammate proof guide | Document teammate pull and run proof using `docker-compose.pull.yml`. | Loh Chee Huan | Medium | 2 | `G8-5 docs: add teammate Docker proof guide` |

### Screenshot

- Tool/page: Jira project board.
- Purpose: prove A1 Jira project exists.
- Expected visible evidence: project name, key `G8`, issue cards, assignees.

## 3. A2 Repository and Project Configuration

Show the files added for deployment.

### Command: show deployment files

- Location: repository root `C:\StockMentor`
- Command:

```powershell
git status --short
```

- Expected output: deployment files such as `Dockerfile`, `docker-compose.yml`, `Jenkinsfile`, `.env.example`, `tests/performance/stockmentor-health.jmx`, and `docs/deployment/...`.
- Screenshot: capture the status output.
- Common quick fix: if `.env` appears, stop and add it to `.gitignore`. Do not commit it.

### Command: safe Compose config validation

- Location: repository root `C:\StockMentor`
- Command:

```powershell
docker compose --env-file .env.example config --quiet
```

- Expected output: no output if the Compose file is valid.
- Screenshot: capture the command and empty success result. Use `.env.example` only.
- Common quick fix: if Docker says a variable is missing, check `.env.example` has placeholder values.

## 4. A3 Jenkins CI/CD Integration

Jenkinsfile target: Linux Jenkins agent only.

Preferred execution model:

- Jenkins runs directly on a Linux host or Linux VM with Docker CLI access.
- Docker Compose exposes backend on host port `8080`.
- JMeter target host is `localhost`.

If Jenkins runs inside a Docker container:

- `localhost` points to the Jenkins container, not the backend on the Docker host.
- Set `JMETER_TARGET_HOST` to the correct host reachable from Jenkins, or run JMeter inside the Compose network and target `backend`.
- Do not leave this ambiguous in the video. Say which model your team used.

### Jenkins credential placeholders

Create these credentials in Jenkins:

- `dockerhub-credentials`: username/password credential for Docker Hub. Username is Docker Hub username, password is Docker Hub access token.
- `stockmentor-compose-env`: secret file credential containing the real `.env` values for Compose. Do not print or archive this file.

### Command: Jenkins agent tools

- Location: Jenkins Linux agent shell.
- Command:

```bash
java -version
node -v
npm -v
docker --version
docker compose version
jmeter --version
```

- Expected output: all tools print versions.
- Screenshot: capture versions, but no secrets.
- Common quick fix: install missing tools on the Linux agent or use a prepared VM image.

### Screenshot

- Tool/page: Jenkins job configuration.
- Purpose: prove repository is linked and Jenkinsfile is selected.
- Expected visible evidence: repository URL, branch, pipeline script from SCM, credentials names only.

## 5. A4 Jira Issues Linked With Commits

Use Jira issue keys in commit messages. Do not stage or commit until the team is ready.

### Command: check commit traceability

- Location: repository root `C:\StockMentor`
- Command:

```powershell
git log --oneline --decorate -10
```

- Expected output: commit messages include keys such as `G8-3`.
- Screenshot: capture commit log and Jira issue page side by side if possible.
- Common quick fix: if a commit lacks a key, mention in the video that the next commit will use the Jira key, or amend only if the team agrees.

## 6. A5 Jenkins Pipeline Stages

The Jenkinsfile stages are:

1. Checkout
2. Backend build/test
3. Frontend lint/typecheck/export
4. Docker build backend/frontend
5. Docker Compose up mysql/backend/frontend
6. Wait for `/api/health` and `/api/health/database`
7. JMeter performance test
8. Docker push latest and commit SHA tags
9. Archive JMeter reports

The frontend export appears twice in the workflow for a clear coursework story:

- First export in CI verifies frontend correctness.
- Docker build exports again to create the deployable Nginx web image.

### Screenshot

- Tool/page: Jenkins build stages.
- Purpose: prove build, test, lint/export, deploy, JMeter, and Docker push stages exist.
- Expected visible evidence: all stages green.

## 7. A6 JMeter Performance Test Through Jenkins

The JMeter file is `tests/performance/stockmentor-health.jmx`.

Configuration:

- Virtual users: 50
- Ramp-up: 10 seconds
- Loop count: 5
- Connect timeout: 5000 ms
- Response timeout: 10000 ms
- Endpoints: `GET /api/health` and `GET /api/health/database`
- Assertions: response code equals `200`, response text contains `"status"`, response text contains `"UP"`

JMeter runs only after Jenkins readiness polling confirms both health endpoints return HTTP 200 and status `UP`. If the database is down, `/api/health/database` returns status `DOWN` with non-200 status, and JMeter must not start.

In Jenkins, the HTML report output is written to a fresh per-build folder: `build/jmeter-${BUILD_NUMBER}/html`. This keeps the report folder clean without deleting a shared directory. Jenkins also archives `build/jmeter-*/**` in `post { always { ... } }` with `allowEmptyArchive: true`, so partial reports can still be collected after a failed run.

### Command: local JMeter dry run

- Location: repository root `C:\StockMentor`, after backend is running at `localhost:8080`
- Command:

```powershell
jmeter -n -t tests/performance/stockmentor-health.jmx -JtargetHost=localhost -JtargetPort=8080 -JconnectTimeout=5000 -JresponseTimeout=10000 -l out\jmeter-results.jtl -e -o out\jmeter-html
```

- Expected output: JMeter finishes without assertion failures and creates HTML report files.
- Screenshot: capture terminal success and the HTML dashboard.
- Common quick fix: if it cannot connect, check whether backend is reachable from that machine and confirm the correct `targetHost`.

## 8. A7 Docker Image Build and Push to Docker Hub

Images:

- `${DOCKERHUB_NAMESPACE}/stockmentor-backend:latest`
- `${DOCKERHUB_NAMESPACE}/stockmentor-backend:<short-commit-sha>`
- `${DOCKERHUB_NAMESPACE}/stockmentor-web:latest`
- `${DOCKERHUB_NAMESPACE}/stockmentor-web:<short-commit-sha>`

Jenkins pushes only after build, Compose startup, readiness checks, and JMeter pass.

### Command: manual backend image build

- Location: repository root `C:\StockMentor`
- Command:

```powershell
docker build -t your-dockerhub-username/stockmentor-backend:latest .
```

- Expected output: Docker build completes successfully.
- Screenshot: capture final build success lines.
- Common quick fix: if Maven dependencies fail, check network access from Docker.

### Command: manual frontend image build

- Location: repository root `C:\StockMentor`
- Command:

```powershell
docker build --build-arg EXPO_PUBLIC_API_BASE_URL=http://localhost:8080 -t your-dockerhub-username/stockmentor-web:latest -f frontend/Dockerfile frontend
```

- Expected output: Expo Web export completes and Nginx image is created.
- Screenshot: capture final build success lines.
- Common quick fix: if Expo cannot find the API URL, verify the build argument name is `EXPO_PUBLIC_API_BASE_URL`.

## 9. Local Docker Compose Demo

Create a real `.env` from `.env.example`, then edit placeholder values. Do not show it in screenshots.

### Command: start deployment

- Location: repository root `C:\StockMentor`
- Command:

```powershell
docker compose --env-file .env up -d --build
```

- Expected output: MySQL, backend, and frontend containers start.
- Screenshot: capture the service startup result, not the `.env` file.
- Common quick fix: if MySQL is unhealthy, wait 30 to 60 seconds and check Docker Desktop resources.

### Command: check containers

- Location: repository root `C:\StockMentor`
- Command:

```powershell
docker compose --env-file .env ps
```

- Expected output: project name `stockmentor`, services `mysql`, `backend`, `frontend`, and healthy status.
- Screenshot: capture container names and health state.
- Common quick fix: if container names are confusing, confirm `COMPOSE_PROJECT_NAME=stockmentor`.

### MySQL port mapping for Workbench

- Location: Windows host, MySQL Workbench or another SQL client
- Connection values:

```text
Host: localhost
Port: 3307
Database: stockmentor
Username: value from MYSQL_USER in your private .env
Password: value from MYSQL_PASSWORD in your private .env
```

- Expected output: MySQL Workbench connects to the Docker MySQL database.
- Screenshot: capture the successful connection screen or schema list, but do not show the password.
- Common quick fix: if connection fails, confirm Docker Compose shows `mysql` healthy and confirm the port mapping is `3307:3306`.

Important explanation for the video: the backend does not use `localhost:3307`. Inside Docker Compose, the backend connects to MySQL using the service DNS name `mysql` and container port `3306`.

### Command: backend health

- Location: any terminal
- Command:

```powershell
curl http://localhost:8080/api/health
curl http://localhost:8080/api/health/database
```

- Expected output:

```json
{"status":"UP"}
{"status":"UP"}
```

- Screenshot: capture both responses.
- Common quick fix: if database health is `DOWN`, run `docker compose --env-file .env ps` and wait for MySQL healthy.

### Command: protected endpoint remains protected

- Location: any terminal
- Command:

```powershell
curl -i http://localhost:8080/api/auth/me
```

- Expected output: HTTP `401` JSON response.
- Screenshot: capture the 401 response.
- Common quick fix: if it returns 200 without Basic Auth, stop the demo and re-check Spring Security rules.

### Command: CORS preflight for Expo Web

- Location: any terminal
- Command:

```powershell
curl -i -X OPTIONS http://localhost:8080/api/auth/register -H "Origin: http://localhost:8081" -H "Access-Control-Request-Method: POST" -H "Access-Control-Request-Headers: authorization,content-type"
```

- Expected output: `access-control-allow-origin: http://localhost:8081` and no wildcard credentialed CORS.
- Screenshot: capture headers.
- Common quick fix: add the frontend URL to `STOCKMENTOR_CORS_ALLOWED_ORIGINS` and restart backend.

### Command: frontend web and route refresh

- Location: browser or terminal
- Command:

```powershell
curl -I http://localhost:8081
curl -I http://localhost:8081/admin/users
```

- Expected output: HTTP `200`, not Nginx `404`. Nginx uses `try_files $uri /index.html;` for Expo Router client-side routing without redirecting nested routes to a URL that drops port `8081`.
- Screenshot: capture browser at `http://localhost:8081` and a nested route refresh.
- Common quick fix: if nested route refresh is 404, check `frontend/nginx.conf`.

## 10. A8 Teammate Docker Hub Pull and Run Proof

Teammates should use `docker-compose.pull.yml` so they pull Docker Hub images instead of rebuilding. For Docker Hub pull/run proof, they only need two files in a folder: `docker-compose.pull.yml` and their private `.env` file. They do not need the full StockMentor source code.

### Command: pull images

- Location: teammate machine, folder containing only `docker-compose.pull.yml` and a private `.env`
- Command:

```powershell
docker compose -f docker-compose.pull.yml --env-file .env pull
```

- Expected output: Docker pulls `stockmentor-backend` and `stockmentor-web`.
- Screenshot: capture pull output and Docker Hub image names.
- Common quick fix: if access is denied, verify Docker Hub namespace and login.

### Command: run pulled images

- Location: teammate machine
- Command:

```powershell
docker compose -f docker-compose.pull.yml --env-file .env up -d
```

- Expected output: MySQL, backend, and frontend containers run.
- Screenshot: capture successful start.
- Common quick fix: if port is occupied, stop the conflicting local service or change the port mapping for the screenshot demo.

### Command: teammate verification

- Location: teammate machine
- Command:

```powershell
curl http://localhost:8080/api/health
curl http://localhost:8080/api/health/database
```

- Expected output: both return `{"status":"UP"}`.
- Screenshot: each teammate captures their terminal and browser/Postman verification.
- Common quick fix: if database is `DOWN`, wait for MySQL health or recreate the local named volume after backing up any needed data.

## 11. Submission Evidence

Submit:

- Slides based on `SECJ4383-PartA-Slides-Plan.md`.
- Maximum 20-minute video based on `SECJ4383-PartA-Video-Runbook.md`.
- Screenshots from `SECJ4383-PartA-Screenshot-Checklist.md`.
- Repository link.
- Jenkins link.
- Docker Hub image links.
- Jira board link.
