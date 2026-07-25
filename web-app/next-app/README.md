# The Pirate Browser on Cloudflare Workers

This is the Cloudflare-native replacement for the Spring Boot service. Next.js
route handlers run in a Worker through the OpenNext adapter. Accounts, sessions,
saved searches, source preferences, audit events, and cast grants are stored in
the dedicated D1 database bound as `DB`. The shared put.io OAuth token is a
Worker secret and is never sent to the browser.

## Runtime

- Node 22.12 or newer for local builds
- Next.js 16.2 and React 19
- `@opennextjs/cloudflare` for the Worker bundle
- Wrangler 4
- Worker name: `pirate-browser-web`
- D1: `pirate-browser-web`
- D1 binding: `DB`
- Static asset binding: `ASSETS`
- `nodejs_compat` with compatibility date `2026-07-24`
- Worker invocation logs and observability enabled
- Production URL: <https://piratebrowser-app.2ez.club>
- Current release canary: `WEB-CF-1.1.1`

Cloudflare does not offer a product called D3. D1 is the SQL store used here;
no Durable Object, KV, R2, or external database is required.

## Local development

Install dependencies and generate binding types:

```powershell
npm ci
npm run cf:typegen
Copy-Item .dev.vars.example .dev.vars
```

Replace every placeholder in `.dev.vars`, then create the local D1 schema and
run the Worker-accurate preview:

```powershell
npm run db:migrate:local
npm run preview
```

`npm run dev` uses the Node.js Next development server and is quicker for UI
work. `npm run preview` uses `workerd` and is the required pre-deployment check.

## Production secrets

Authenticate Wrangler against the Cloudflare account that owns the Worker:

```powershell
npx wrangler login
npx wrangler whoami
```

Set the three required runtime secrets. Wrangler prompts for each value and
does not write it to the repository:

```powershell
npx wrangler secret put PUTIO_OAUTH_TOKEN
npx wrangler secret put REGISTRATION_INVITE_CODE
npx wrangler secret put ADMIN_BOOTSTRAP_PASSWORD
```

`ADMIN_BOOTSTRAP_USERNAME` defaults to `owner` in `wrangler.jsonc`.
`COOKIE_SECURE` is `true` in production. Change the non-secret username in the
Wrangler config before first deployment if desired.

The bootstrap password is used only to create the first administrator if that
username does not exist. Remove the bootstrap secret after confirming the
administrator can sign in:

```powershell
npx wrangler secret delete ADMIN_BOOTSTRAP_PASSWORD
```

If that secret is removed, also remove it from `secrets.required` so local and
CI validation do not warn.

## Database and deployment

The production database has already been provisioned and its non-secret ID is
checked into `wrangler.jsonc`. Apply migrations before deploying:

```powershell
npm run typecheck
npm run cf:build
npm run db:migrate:remote
npm run deploy
```

Never run `wrangler d1 execute ... --remote` with ad hoc destructive SQL. Add a
forward-only SQL migration under `migrations/`, validate it locally, and then
apply it remotely.

For live logs:

```powershell
npm run tail
```

## GitHub deployment

The `Cloudflare web app` workflow verifies pull requests and pushes to `main`.
Automated production deployment is opt-in: set the repository variable
`CLOUDFLARE_CI_DEPLOY` to `true`, then add these GitHub
production-environment secrets:

- `CLOUDFLARE_ACCOUNT_ID`
- `CLOUDFLARE_API_TOKEN`

The API token must be scoped to the intended account and have Workers Scripts
edit plus D1 edit permissions. Runtime application secrets remain in
Cloudflare; they must not be copied into GitHub.

The workflow cannot deploy until those two GitHub secrets exist and the runtime
secrets above have been installed on the Worker. A Cloudflare custom domain can
be attached after the first successful Worker deployment.
