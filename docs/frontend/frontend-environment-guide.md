# Frontend Environment Guide

## Backend Base URL

Frontend implementation should read the backend base URL from:

```text
EXPO_PUBLIC_API_BASE_URL=http://localhost:8080
```

Do not hardcode local backend URLs in screen code.

Example `.env.example` content for future use:

```text
EXPO_PUBLIC_API_BASE_URL=http://localhost:8080
```

This value is not a secret, but it is client-visible. Do not put OpenAI keys, Twelve Data keys, admin tokens, database credentials, or passwords in `EXPO_PUBLIC_` variables.

## Local URL Differences

The correct base URL depends on where the app runs:

- Expo Web on the same machine as backend: `http://localhost:8080`.
- Android emulator: often `http://10.0.2.2:8080`.
- iOS simulator: often `http://localhost:8080`.
- Physical phone: use the computer LAN IP, for example `http://192.168.x.x:8080`, and ensure phone and computer are on the same network.

Always verify the phone/browser can reach the backend before debugging app code.

## Admin Token Handling

- Admin token is entered by the admin during admin login/token prompt.
- Admin token is sent as `X-Admin-Token` only for admin API calls.
- Admin token is session-only.
- Admin token is cleared on logout.
- Admin token must never be committed, hardcoded, or stored in `EXPO_PUBLIC_`.

## Basic Auth Credential Handling

- MVP default: in-memory/session credentials during development.
- Create an auth storage abstraction later.
- Native demo may optionally use `expo-secure-store` later only after approval.
- Web admin should avoid persistent password storage.
- Do not use AsyncStorage for sensitive credentials.

## CORS Prerequisite

Do not implement CORS in this frontend documentation task.

Documented backend follow-up:

- Expo mobile/native may not hit browser CORS restrictions the same way.
- Expo Web admin needs backend CORS allowing the Expo Web origin.
- Required allowed headers include `Authorization` and `X-Admin-Token`.
- CORS is required before real Expo Web admin testing in a browser.

## Delayed Market Data Environment Notes

- StockMentor should describe stock data as 15-minute delayed educational market data.
- Expo Web or mobile refetch timing must not imply immediate market data.
- During 9:30-9:44 AM New York time, current-day delayed intraday display is not ready yet because the displayed market time is before market open.
- After 4:15 PM New York time, the delayed display can show 4:00 PM close data if backend has it, then remain as latest delayed/stored market data until the next trading day.

## Expo Web Notes

The current Expo config uses static web output. Use client-side API fetching for authenticated app data. Expo Router loaders are web-only and should not be the first choice for this MVP because the native app also needs the same authenticated data flow.

## No Committed Secrets

Never commit:

- admin token
- Basic Auth password
- OpenAI API key
- Twelve Data API key
- database password
- production endpoint with embedded credentials

Use local environment files and keep secret values out of source control.
