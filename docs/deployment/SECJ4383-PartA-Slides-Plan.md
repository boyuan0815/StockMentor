# SECJ4383 Part A Slides Plan

Keep slides visual and simple. Use screenshots as proof instead of long paragraphs.

| Slide | Title | Presenter | Main bullets | Screenshot placement |
| --- | --- | --- | --- | --- |
| 1 | StockMentor DevOps Part A | Lim Bo Yuan | Project name, team members, tools: Jira, Git, Jenkins, Docker, Docker Hub, JMeter | Team/project title |
| 2 | System To Deploy | Lim Bo Yuan | Spring Boot 4.0.5 backend, MySQL database, Expo Web frontend, Expo mobile connects to backend URL | Architecture diagram |
| 3 | Coursework Deployment Scope | Lim Bo Yuan | Docker Compose for backend, MySQL, web; mobile uses LAN/ngrok/staging URL; no App Store/Play Store for Part A | Deployment scope diagram |
| 4 | Jira Project | Khoo Teong Lee | Project key `G8`, issues, assignees, story points | Jira board screenshot |
| 5 | Jira Issue Examples | Khoo Teong Lee | Backend Docker, MySQL Compose, Jenkins pipeline, JMeter, teammate proof | Jira issue detail screenshot |
| 6 | Repository Configuration | Khoo Teong Lee | `Dockerfile`, `frontend/Dockerfile`, Compose files, Jenkinsfile, JMeter file, docs | Repo tree screenshot |
| 7 | Commit Traceability | Khoo Teong Lee | Commit message includes Jira key; example `G8-3 ci: add Jenkins deployment pipeline` | Git log and Jira link screenshot |
| 8 | Jenkins Pipeline | Lim Bo Yuan | Checkout, backend test, frontend export, Docker build, Compose up, wait health, JMeter, push, archive | Jenkins stage view screenshot |
| 9 | Database Deployment | Lim Bo Yuan | MySQL separate service, named volume, backend env vars, healthcheck, no secrets in image | Compose MySQL section screenshot |
| 10 | Backend Health and Security | Lim Bo Yuan | Public health endpoints, protected endpoints still require Basic Auth, safe UP/DOWN body | Health and 401 screenshots |
| 11 | Frontend Web Deployment | Loh Chee Huan | Expo Web export, Nginx static hosting, API base URL build arg, SPA fallback | Frontend page and nested route screenshot |
| 12 | JMeter Performance Test | Lim Yu An | 50 users, 10 seconds ramp-up, loop count 5, two safe endpoints, assertions | JMeter file screenshot |
| 13 | JMeter Report in Jenkins | Lim Yu An | Readiness first, JMeter second, HTML report archived | Jenkins console/report screenshot |
| 14 | Docker Hub Push | Loh Chee Huan | Backend/web images, `latest`, short SHA tag, push after tests pass | Docker Hub tag screenshots |
| 15 | Teammate Pull and Run Proof | Loh Chee Huan | `docker-compose.pull.yml`, pull, run, health verification | Teammate proof screenshots |
| 16 | Production Future Work | Lim Bo Yuan | HTTPS, cloud hosting, managed DB, migrations, secret manager, monitoring | No screenshot required |
| 17 | Summary | Lim Bo Yuan | A1 to A8 covered, simple workflow, explainable database and web deployment | Checklist screenshot |
