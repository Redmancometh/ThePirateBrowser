# The Pirate Browser web app

The web product is a Spring Boot 4.1 service with an embedded React 19 interface.
It provides individual accounts, per-user saved searches and source preferences,
shared put.io transfer/file controls, protected media streaming, and an
administrator audit trail.

The put.io OAuth token is a server secret. It is read only from
`PUTIO_OAUTH_TOKEN`; it is never compiled into the React bundle or returned by an
API. Because all members use the same put.io account, public sign-up is not
supported. Registration is disabled when `WEB_REGISTRATION_INVITE_CODE` is
blank. Administrators can also create accounts directly.

## Local development

Requirements: Java 21, Maven, and Node 22.

```powershell
cd frontend
npm install
npm run dev
```

In a second terminal:

```powershell
cd backend
$env:WEB_ADMIN_USERNAME = "owner"
$env:WEB_ADMIN_PASSWORD = "replace-with-at-least-12-characters"
$env:PUTIO_OAUTH_TOKEN = "your-token"
mvn spring-boot:run
```

Vite proxies `/api` to Spring Boot. The default local database is an H2 file
under `backend/data`.

## Production container

Copy `.env.example` to `.env`, replace every placeholder, set
`COOKIE_SECURE=true` behind HTTPS, and run:

```powershell
docker compose up --build -d
```

The image builds and tests the React app, embeds it in the Spring Boot JAR,
applies Flyway migrations to PostgreSQL, and exposes health at
`/actuator/health`.

Put the service behind an HTTPS reverse proxy. Do not publish port 8080 directly
to the internet. Back up the `pirate-postgres` volume before upgrades.

## Verification

```powershell
cd frontend
npm test
npm run build

cd ../backend
mvn verify
```
