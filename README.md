# StockMentor

StockMentor is an educational stock-learning app for beginner investors. It combines account onboarding, beginner investment profiles, delayed stock market data, AI-supported explanations and suggestions, watchlists, paper trading, portfolio tracking, and admin monitoring tools.

StockMentor is not a brokerage or financial-advice product. Paper trades and AI content are for learning and practice only.

## Features

- Spring Boot backend with HTTP Basic Auth.
- Beginner registration, login, current-user bootstrap, and onboarding.
- Investment profile and behavior summary support.
- Supported US stock browsing, stock detail, delayed quote display, and history charts.
- AI stock explanations and AI stock suggestions through backend-only OpenAI integration.
- Watchlist add, remove, reorder, and batch remove flows.
- Paper trading with backend-priced buy/sell tickets, portfolio reset, transaction history, and transaction detail.
- Expo mobile app plus Expo Web admin console.
- Admin user management, AI suggestion monitoring, refresh job monitoring, and stock maintenance endpoints.
- Docker, Docker Compose, and Jenkins CI/CD support.

## Supported Stock Symbols

```text
NVDA, TSLA, AMD, AAPL, MSFT, GOOG, KO, JNJ
```

## Tech Stack

### Backend

- Java 17
- Spring Boot 4.0.5
- Spring MVC, Spring Security, Spring Data JPA, Bean Validation
- MySQL for local/runtime data
- H2 for tests
- Maven Wrapper
- OpenAI and Twelve Data integrations through backend services only

### Frontend

- Expo SDK 54
- React Native 0.81.5
- React 19.1
- TypeScript 5.9
- Expo Router 6
- React Native Web for web/admin support
- AsyncStorage, Reanimated, Gesture Handler, SVG, and Wagmi Charts

## Repository Layout

```text
StockMentor/
  backend/                 Spring Boot backend source and tests
  frontend/                Expo / React Native app source
  Dockerfile               Backend production image build
  docker-compose.yml       Local build-and-run Compose stack
  docker-compose.pull.yml  Pull prebuilt images and run the stack
  Jenkinsfile              CI/CD pipeline for build, test, Docker, and JMeter report archival
  .env.example             Safe root environment template
```

Local-only project notes, coursework evidence, generated outputs, local secrets, and performance test plans are intentionally ignored and not part of the public repository.

## Configuration

Copy `.env.example` for Docker/Compose usage and keep real values in a local `.env` file. Do not commit `.env`, backend `application.yaml`, frontend `.env`, API keys, admin tokens, database passwords, or provider credentials.

Important backend configuration keys:

| Key | Purpose | Secret |
| --- | --- | --- |
| `spring.datasource.url` | MySQL JDBC URL | No |
| `spring.datasource.username` | MySQL username | Usually no |
| `spring.datasource.password` | MySQL password | Yes |
| `openai.api.key` | OpenAI API key | Yes |
| `openai.model` | OpenAI model name | No |
| `twelvedata.api.key` | Twelve Data API key | Yes |
| `stockmentor.admin.token` | Admin API token sent as `X-Admin-Token` | Yes |
| `stockmentor.cors.allowed-origins` | Allowed frontend origins | No |
| `stockmentor.paper-trading.initial-cash` | Simulated starting cash | No |
| `stockmentor.paper-trading.trade-fee` | Simulated flat trade fee | No |

Frontend configuration:

| Key | Purpose | Secret |
| --- | --- | --- |
| `EXPO_PUBLIC_API_BASE_URL` | Spring Boot backend base URL | No, but it is visible in the client bundle |

Never place OpenAI keys, Twelve Data keys, admin tokens, database passwords, or private credentials in `EXPO_PUBLIC_` variables.

## Backend Setup

Prerequisites:

- Java 17
- MySQL
- PowerShell on Windows

Create the database:

```sql
CREATE DATABASE stockmentor;
```

Run the backend:

```powershell
cd C:\StockMentor\backend
.\mvnw.cmd spring-boot:run
```

The backend defaults to:

```text
http://localhost:8080
```

Useful backend checks:

```powershell
cd C:\StockMentor\backend
.\mvnw.cmd clean compile
.\mvnw.cmd test
```

## Frontend Setup

Prerequisites:

- Node.js and npm
- Expo-compatible Android, iOS, or web environment
- Running backend reachable from the target device/browser

Install dependencies:

```powershell
cd C:\StockMentor\frontend
npm install
```

Set the backend URL:

```text
EXPO_PUBLIC_API_BASE_URL=http://localhost:8080
```

Start Expo:

```powershell
cd C:\StockMentor\frontend
npm.cmd run start
```

Useful frontend commands:

```powershell
npm.cmd run android
npm.cmd run ios
npm.cmd run web
npm.cmd run lint
npx.cmd tsc --noEmit
```

## Docker

Build and run the local stack from source:

```powershell
cd C:\StockMentor
docker compose --env-file .env up -d --build
```

Run using prebuilt images:

```powershell
cd C:\StockMentor
docker compose -f docker-compose.pull.yml --env-file .env up -d
```

Default local ports:

```text
Backend:  http://localhost:8080
Frontend: http://localhost:8081
MySQL:    localhost:3307 -> container 3306
```

## Jenkins

`Jenkinsfile` keeps the CI/CD pipeline in the public repo because it is part of the deployable project surface. It builds and tests the backend, lints/typechecks/exports the frontend, builds Docker images, starts the Compose stack, checks health endpoints, runs the JMeter plan when present in the Jenkins workspace, archives reports, and pushes Docker images through Jenkins credentials.

Required Jenkins credentials:

| Credential ID | Type | Purpose |
| --- | --- | --- |
| `stockmentor-compose-env` | Secret file | Compose environment file with real local values |
| `dockerhub-credentials` | Username/password | Docker Hub push credentials |

## API Overview

| Area | Main routes |
| --- | --- |
| Auth | `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me` |
| Onboarding/Profile | `GET /api/user/onboarding/questions`, `POST /api/user/onboarding`, `POST /api/user/onboarding/retake`, `GET /api/user/profile` |
| Stocks | `GET /api/stocks`, `GET /api/stocks/{symbol}`, `GET /api/stocks/{symbol}/history` |
| AI Explanation | `GET /api/stocks/{symbol}/ai-explanation` |
| AI Suggestions | `GET /api/stocks/ai-suggestions`, `POST /api/stocks/ai-suggestions/refresh` |
| Watchlist | `GET /api/watchlist`, `POST /api/watchlist/{symbol}`, `DELETE /api/watchlist/{symbol}`, `PATCH /api/watchlist/reorder`, `POST /api/watchlist/batch-remove` |
| Paper Trading | `/api/paper-trading/account`, `/portfolio`, `/buy`, `/sell`, `/portfolio/reset`, `/transactions`, `/transactions/page`, `/transactions/{transactionId}` |
| Admin Users | `GET /api/admin/users`, `GET /api/admin/users/{userId}`, `PATCH /api/admin/users/{userId}/status` |
| Admin AI | `/api/admin/ai-suggestions/batches`, `/failures`, `/usage-summary`, `/scheduled-refresh/run`, `/refresh-jobs` |
| Admin Stock Maintenance | `POST /api/admin/stocks/backfill` |
| Health | `GET /api/health`, `GET /api/health/database` |

## Security Notes

- The frontend calls the Spring Boot backend only.
- The frontend must not call OpenAI, Twelve Data, Docker Hub, or database services directly.
- Real API keys, admin tokens, database credentials, provider credentials, local application config, build outputs, logs, and private documents should stay ignored.
- Public examples use placeholder values only.

## License / Academic Context

Developed as a Final Year Project for the Bachelor of Computer Science (Software Engineering), Faculty of Computing, Universiti Teknologi Malaysia.
