# The Pirate Browser web app

The production web app is the Next.js application under
[`next-app`](next-app/). It runs on Cloudflare Workers through OpenNext and uses
Cloudflare D1 for accounts, sessions, saved searches, source preferences, audit
events, and cast grants.

The shared put.io OAuth token is stored only as the Worker secret
`PUTIO_OAUTH_TOKEN`. It is never compiled into browser assets or returned by an
API.

Production: <https://piratebrowser-app.2ez.club>

See [`next-app/README.md`](next-app/README.md) for local development,
verification, database migration, and deployment instructions.
