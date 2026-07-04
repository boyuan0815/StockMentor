# SECJ4383 Part A Screenshot Checklist

Use this checklist while preparing slides and the video. Do not screenshot secrets or `.env` file contents.

| Area | Tool or page | Screenshot purpose | Expected visible evidence |
| --- | --- | --- | --- |
| A1 Jira | Jira project board | Prove Jira project exists | Project name, key `G8`, issue cards |
| A1 Jira | Jira issue detail | Prove issue planning | Title, description, assignee, priority, story points |
| A2 Repo | Git terminal | Prove project configuration files | `Dockerfile`, `docker-compose.yml`, `Jenkinsfile`, JMeter file, docs |
| A2 Repo | GitHub or repository browser | Prove repository structure | `backend/`, `frontend/`, `docs/deployment/`, `tests/performance/` |
| A2 Repo | `pom.xml` or terminal search | Prove Spring Boot version | Spring Boot `4.0.5`, Java `17` |
| A3 Jenkins | Jenkins job config | Prove CI/CD integration | Repository URL, Jenkinsfile from SCM |
| A3 Jenkins | Jenkins credentials list | Prove safe credential setup | Credential IDs `dockerhub-credentials`, `stockmentor-compose-env`; no values |
| A4 Jira commits | Git log and Jira issue | Prove traceability | Commit message with `G8-*` and linked Jira issue |
| A5 Pipeline | Jenkins stage view | Prove pipeline stages | Checkout, backend test, frontend export, Docker build, Compose up, wait health, JMeter, Docker push, archive |
| A5 Backend build | Jenkins console | Prove backend build/test | Maven `BUILD SUCCESS` |
| A5 Frontend check | Jenkins console | Prove frontend lint/typecheck/export | `npm run lint`, `tsc --noEmit`, `expo export --platform web` success |
| A5 Deploy | Docker Desktop or `docker compose ps` | Prove app deployment | `stockmentor-mysql`, `stockmentor-backend`, `stockmentor-frontend` running or healthy |
| A5 Health | Browser/Postman/terminal | Prove backend is ready | `/api/health` returns `{"status":"UP"}` |
| A5 Database health | Browser/Postman/terminal | Prove DB deployment | `/api/health/database` returns `{"status":"UP"}` |
| A5 MySQL port mapping | Docker Desktop, `docker compose ps`, or MySQL Workbench | Prove database access path is clear | Backend uses `mysql:3306`; Windows host or MySQL Workbench uses `localhost:3307` |
| A5 Security | Browser/Postman/terminal | Prove protected endpoints remain protected | `/api/auth/me` without Basic Auth returns `401` |
| A5 CORS | Terminal response headers | Prove Expo Web origin is allowed | `access-control-allow-origin: http://localhost:8081` |
| A5 Web | Browser | Prove frontend web deployment | `http://localhost:8081` loads StockMentor |
| A5 Web route | Browser refresh | Prove Nginx SPA fallback | Nested route refresh returns page, not Nginx 404 |
| A6 JMeter | Jenkins console | Prove performance test ran | JMeter command, no assertion failures |
| A6 JMeter report | Jenkins archived artifacts or HTML report | Prove report exists | `index.html`, summary charts, request statistics |
| A7 Docker build | Jenkins console | Prove Docker images built | Backend and web build logs complete |
| A7 Docker Hub | Docker Hub repository page | Prove image push | `stockmentor-backend` and `stockmentor-web` with `latest` and short SHA tags |
| A8 Team proof | Teammate terminal | Prove pull/run | `docker compose -f docker-compose.pull.yml pull` and `up -d` |
| A8 Team proof | Teammate browser/Postman | Prove app works on teammate machine | Health endpoints return `UP`, frontend loads |
| Submission | Slides title page | Prove group identity | Lim Bo Yuan, Khoo Teong Lee, Loh Chee Huan, Lim Yu An |
