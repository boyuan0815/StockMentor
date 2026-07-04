# SECJ4383 Part A Video Runbook

Target length: 18 to 20 minutes. Keep the video practical: show the tools, explain the deployment story, and avoid reading long text.

## Presenter 1: Khoo Teong Lee, 4 minutes

Screen:

- Jira board
- Jira issue details
- Git log showing Jira keys

Presenter notes:

- "We created a Jira project called StockMentor DevOps Part A with key G8."
- "Each DevOps task is tracked as an issue with assignee, priority, story points, and an example commit message."
- "The commit messages use the Jira key, for example `G8-3 ci: add Jenkins deployment pipeline`, so the repository work can be traced back to Jira."

Screenshots to insert:

- Jira board
- One Jira issue detail
- Git log with Jira key

## Presenter 2: Lim Bo Yuan, 7 minutes

Screen:

- Repository deployment files
- Jenkins pipeline stages
- Docker Compose containers
- Health endpoints

Presenter notes:

- "StockMentor uses Spring Boot 4.0.5 for the backend, MySQL for the database, and Expo Web for the admin/web demo."
- "For coursework deployment, MySQL is a separate Docker Compose service, not baked into the backend image."
- "The backend waits for MySQL readiness through Compose healthchecks. Jenkins waits again using `/api/health` and `/api/health/database` before running JMeter."
- "Secrets are not hardcoded. Jenkins uses `dockerhub-credentials` and `stockmentor-compose-env` credentials."
- "The health endpoints are public and safe. `/api/health` returns only status UP. `/api/health/database` returns only UP or DOWN, with no database URL, username, stack trace, API key, or password."
- "The Compose stack remains running after a successful deployment so screenshots and demo verification can be captured."

Screenshots to insert:

- `Dockerfile`
- `docker-compose.yml`
- Jenkins green stages
- Health endpoint responses

## Presenter 3: Lim Yu An, 4 minutes

Screen:

- JMeter test plan
- Jenkins JMeter stage
- Archived JMeter HTML report

Presenter notes:

- "The performance test uses JMeter through Jenkins."
- "It tests safe read-only health endpoints only, so it does not call OpenAI or Twelve Data."
- "The configuration is 50 virtual users, 10 seconds ramp-up, loop count 5, connect timeout 5000 ms, and response timeout 10000 ms."
- "Each request asserts response code 200 and response body contains status and UP."
- "JMeter runs only after Jenkins readiness polling confirms the backend and database health are UP."

Screenshots to insert:

- JMeter `.jmx` file
- Jenkins JMeter console output
- JMeter report dashboard

## Presenter 4: Loh Chee Huan, 4 minutes

Screen:

- Docker Hub image pages
- Teammate pull/run commands
- Frontend web demo
- Mobile explanation slide

Presenter notes:

- "After Jenkins tests and JMeter pass, Jenkins pushes two image tags: `latest` and the short commit SHA."
- "Teammates use `docker-compose.pull.yml` to pull images from Docker Hub and run the app without rebuilding."
- "Each teammate captures proof: Docker pull output, running containers, backend health, database health, and frontend page."
- "For the mobile app, coursework deployment means Expo mobile connects to the deployed backend URL through LAN, ngrok, or staging URL. App Store and Play Store distribution are not required for this Part A."

Screenshots to insert:

- Docker Hub backend image tags
- Docker Hub web image tags
- Teammate terminal proof
- Frontend at `http://localhost:8081`

## Closing, 1 minute

Presenter: Lim Bo Yuan

Presenter notes:

- "This deployment is intentionally simple and explainable for coursework."
- "Real production improvements would include HTTPS, cloud hosting, managed database, real secret manager, migrations, and monitoring."
- "For Part A evidence, we have Jira, repository config, Jenkins pipeline, Jira-linked commits, JMeter through Jenkins, Docker Hub push, and teammate pull/run proof."
