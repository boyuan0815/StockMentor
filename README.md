# StockMentor

StockMentor is an educational stock-learning mobile app for beginner investors. It combines onboarding, beginner investment profiles, selected US stock browsing, delayed market data, AI-supported learning explanations and suggestions, watchlists, paper trading, portfolio tracking, and admin maintenance tools.

## Project Overview

StockMentor helps beginner investors learn how stock market data, risk preference, watchlists, and practice trading can work together in one learning app. The app is designed for students and new investors who want to explore selected US stocks before dealing with real financial platforms.

StockMentor is not a real trading platform. It does not provide brokerage services, execute real-money trades, or give real financial advice. Paper trading and AI content are included for educational learning and practice only.

## Main Features

- Authentication and account access using Spring Security HTTP Basic Auth.
- Beginner investor registration, login, current-user bootstrap, and logout.
- Beginner onboarding quiz with backend-owned questions and scoring.
- Investment profile screen with profile and behavior summary data when available.
- Stock list and stock detail screens for the supported US stock universe.
- Delayed educational market data display using backend-selected stored prices.
- Interactive stock charts with line and candlestick support where backend history data supports it.
- On-demand AI stock explanations for supported timeframes.
- AI stock suggestions with cached loading, manual refresh, cooldown handling, dismiss, and add-to-watchlist actions.
- Watchlist list, add, remove, reorder, and batch remove flows.
- Search route and contextual stock search flow. In the current app layout, the search route exists but is hidden from the bottom tab bar.
- Paper trading with backend-priced simulated buy and sell tickets.
- Portfolio summary, open positions, reset flow, transaction history, paged history, and transaction detail.
- Web/tablet admin console inside the Expo app, including users, AI suggestion monitoring, refresh jobs, and stock data maintenance.
- Phone-sized fallback for the admin console.

Some manual demo and regression checks are still documented as final verification work. See the frontend testing and admin portal guides under `docs/frontend/`.

## Supported Stock Universe

The current app scope supports these stock symbols:

```text
NVDA, TSLA, AMD, AAPL, MSFT, GOOG, KO, JNJ
```

Unsupported stock symbols are outside the current app scope.

## Tech Stack

### Backend

- Spring Boot 4.0.5
- Java 17
- Spring MVC, Spring Security, Spring Data JPA, Bean Validation
- MySQL for local application data
- H2 for backend tests
- HTTP Basic Auth
- OpenAI integration for AI explanations and suggestions
- Twelve Data integration for stock market data retrieval
- Maven Wrapper

### Frontend

- Expo SDK 54
- React Native 0.81.5
- React 19.1
- TypeScript 5.9
- Expo Router 6
- React Native Web for web/admin support
- AsyncStorage through a safe storage wrapper for non-sensitive local persistence
- `react-native-wagmi-charts`, `react-native-svg`, Reanimated, Gesture Handler, and Worklets for charts/interactions
- `react-native-draggable-flatlist` for watchlist reorder

## System Architecture

The Expo frontend calls the Spring Boot backend only. The frontend must not call OpenAI, Twelve Data, or any brokerage service directly.

The backend handles authentication, current-user resolution, onboarding/profile rules, delayed market data selection, AI calls, paper-trading calculations, admin actions, and persistence. MySQL stores users, investment profiles, behavior profiles, stock data, analysis snapshots, AI outputs, watchlists, paper-trading accounts, positions, transactions, and refresh/maintenance records.

## Project Structure

```text
StockMentor/
  backend/
    src/main/java/        Spring Boot controllers, services, repositories, entities, DTOs
    src/main/resources/   Backend configuration files
    src/test/             Backend unit and security tests
    pom.xml               Backend Maven configuration
  frontend/
    app/                  Expo Router routes
    screens/              Feature screens outside route wrappers
    api/                  Typed backend API clients
    types/                TypeScript DTO types
    components/           Shared UI and feature components
    package.json          Frontend scripts and dependencies
  docs/
    frontend/             Frontend flows, design system, API screen map, testing guides
    backend/              Backend testing and use-case guides
  AGENTS.md               Repository rules and implementation guardrails
```

## Backend Setup

### Prerequisites

- Java 17
- MySQL running locally
- PowerShell on Windows
- Internet access for Maven dependency download if dependencies are not already cached

### Create The Database

```sql
CREATE DATABASE stockmentor;
```

### Configure Backend Properties

Use `backend/src/main/resources/application-example.yaml` as the safe placeholder reference for provider, admin, CORS, and paper-trading settings. Add local datasource settings through your own non-public backend configuration. Do not commit real API keys, database passwords, admin tokens, or private credentials.

Verified backend configuration keys include:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/stockmentor
    username: your_mysql_username
    password: your_mysql_password
  jpa:
    hibernate:
      ddl-auto: update

openai:
  api:
    key: your_openai_api_key
  model: gpt-4o-mini

twelvedata:
  api:
    key: your_twelve_data_api_key

stockmentor:
  admin:
    token: your_admin_token
  cors:
    allowed-origins:
      - http://localhost:8081
      - http://127.0.0.1:8081
  paper-trading:
    initial-cash: 1000000.00
    trade-fee: 1.00
```

### Run The Backend

```powershell
cd C:\StockMentor\backend
.\mvnw.cmd spring-boot:run
```

The local backend is expected at:

```text
http://localhost:8080
```

### Backend Checks

```powershell
cd C:\StockMentor\backend
.\mvnw.cmd clean compile
.\mvnw.cmd test
```

## Frontend Setup

### Prerequisites

- Node.js and npm
- Expo-compatible Android, iOS, or web testing environment
- Backend running and reachable from the device/browser

### Install Dependencies

```powershell
cd C:\StockMentor\frontend
npm install
```

### Configure Backend Base URL

The frontend reads the backend URL from:

```text
EXPO_PUBLIC_API_BASE_URL=http://localhost:8080
```

Use the correct host for your target:

- Expo Web on the same machine: `http://localhost:8080`
- Android emulator: often `http://10.0.2.2:8080`
- Physical phone: use the computer LAN IP and ensure both devices are on the same network

Do not put secrets, OpenAI keys, Twelve Data keys, admin tokens, passwords, or database credentials in `EXPO_PUBLIC_` variables.

### Start Expo

```powershell
cd C:\StockMentor\frontend
npm.cmd run start
```

Available frontend scripts:

```powershell
npm.cmd run android
npm.cmd run ios
npm.cmd run web
npm.cmd run lint
```

`npm.cmd run ios` requires a compatible iOS development environment.

## Environment Variables / Configuration

### Backend

| Configuration key | Purpose | Secret |
| --- | --- | --- |
| `spring.datasource.url` | MySQL JDBC URL | No |
| `spring.datasource.username` | MySQL username | Usually no |
| `spring.datasource.password` | MySQL password | Yes |
| `spring.jpa.hibernate.ddl-auto` | Local schema update mode | No |
| `openai.api.key` | OpenAI API key | Yes |
| `openai.model` | OpenAI model name, defaulting to `gpt-4o-mini` in code | No |
| `twelvedata.api.key` | Twelve Data API key | Yes |
| `stockmentor.admin.token` | Admin API token sent as `X-Admin-Token` | Yes |
| `stockmentor.cors.allowed-origins` | Allowed frontend origins for API CORS | No |
| `stockmentor.paper-trading.initial-cash` | Simulated account starting cash | No |
| `stockmentor.paper-trading.trade-fee` | Simulated flat trade fee | No |

### Frontend

| Configuration key | Purpose | Secret |
| --- | --- | --- |
| `EXPO_PUBLIC_API_BASE_URL` | Spring Boot backend base URL | No, but it is visible in the client bundle |

## Running The App Locally

1. Start MySQL and make sure the `stockmentor` database exists.
2. Configure backend properties with local placeholder-safe values.
3. Start the backend from `backend/`.
4. Configure `EXPO_PUBLIC_API_BASE_URL` for the frontend runtime.
5. Start Expo from `frontend/`.
6. Register or sign in.
7. Complete onboarding if required.
8. Use Watchlist, Stocks, Suggestions, Portfolio, Profile, Search, or the `/admin` console depending on the account role.

AI features require valid OpenAI configuration. Market data retrieval and admin stock maintenance require valid Twelve Data configuration. Without those provider settings, related features may return fallback or unavailable states.

## Testing

Useful backend checks:

```powershell
cd C:\StockMentor\backend
.\mvnw.cmd clean compile
.\mvnw.cmd test
```

Useful frontend checks:

```powershell
cd C:\StockMentor\frontend
npm.cmd run lint
npx.cmd tsc --noEmit
npx.cmd expo config --type public
```

Useful repository checks before handoff:

```powershell
cd C:\StockMentor
git diff --check
git diff --cached --name-only
git status --short
```

Manual testing guidance is documented in:

- `docs/frontend/frontend-testing-checklist.md`
- `docs/frontend/admin-portal-testing-guide.md`
- `docs/frontend/ai-suggestions-manual-testing-guide.md`

## API Overview

| Area | Main backend routes |
| --- | --- |
| Auth | `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me` |
| Onboarding/Profile | `GET /api/user/onboarding/questions`, `POST /api/user/onboarding`, `POST /api/user/onboarding/retake`, `GET /api/user/profile` |
| Stocks | `GET /api/stocks`, `GET /api/stocks/{symbol}`, `GET /api/stocks/{symbol}/history` |
| AI Explanation | `GET /api/stocks/{symbol}/ai-explanation` |
| AI Suggestions | `GET /api/stocks/ai-suggestions`, `POST /api/stocks/ai-suggestions/refresh`, suggestion item `PATCH` actions |
| Watchlist | `GET /api/watchlist`, `POST /api/watchlist/{symbol}`, `DELETE /api/watchlist/{symbol}`, `PATCH /api/watchlist/reorder`, `POST /api/watchlist/batch-remove` |
| Paper Trading | `/api/paper-trading/account`, `/portfolio`, `/buy`, `/sell`, `/portfolio/reset`, `/transactions`, `/transactions/page`, `/transactions/{transactionId}` |
| Admin Users | `GET /api/admin/users`, `GET /api/admin/users/{userId}`, `PATCH /api/admin/users/{userId}/status` |
| Admin AI | `/api/admin/ai-suggestions/batches`, `/failures`, `/usage-summary`, `/scheduled-refresh/run`, `/refresh-jobs` |
| Admin Stock Maintenance | `POST /api/admin/stocks/backfill` |

See `docs/frontend/backend-api-screen-map.md` for the screen-to-endpoint map and DTO notes.

## Educational / Safety Notice

StockMentor is for educational learning and paper-trading practice only. It does not provide real financial advice, brokerage services, or real-money trading.

## Known Limitations

- Only the selected US stock universe is supported.
- Market data is delayed educational data selected from stored backend data.
- MY and HK market data are deferred/planned states in the current frontend docs.
- No real-money trading, brokerage integration, margin, options, short selling, or fractional shares.
- AI explanations and suggestions depend on configured OpenAI availability and backend validation.
- External market data ingestion and admin stock maintenance depend on configured Twelve Data availability.
- The admin console is web/tablet focused, with a phone-sized fallback instead of full mobile admin tables.
- Final end-to-end demo polish and manual regression testing are tracked in the frontend testing docs.

## Documentation Links

- `docs/frontend/frontend-phase-prompts.md`
- `docs/frontend/frontend-implementation-master-plan.md`
- `docs/frontend/frontend-implementation-roadmap.md`
- `docs/frontend/backend-api-screen-map.md`
- `docs/frontend/api-integration-guide.md`
- `docs/frontend/mobile-user-flow.md`
- `docs/frontend/design-system.md`
- `docs/frontend/frontend-environment-guide.md`
- `docs/frontend/frontend-testing-checklist.md`
- `docs/frontend/admin-web-flow.md`
- `docs/frontend/admin-portal-testing-guide.md`
- `docs/backend/backend-market-paper-watchlist-testing-guide.md`
- `docs/backend/us010-paper-trading-completeness-testing-guide.md`
- `docs/backend/us009-v2-extended-chart-timeframes-backend-testing-guide.md`

## Author / Academic Context

Developed as a Final Year Project for the Bachelor of Computer Science (Software Engineering), Faculty of Computing, Universiti Teknologi Malaysia.
