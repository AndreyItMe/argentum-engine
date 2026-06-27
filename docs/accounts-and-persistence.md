# Accounts & persistence (PostgreSQL)

An **opt-in** accounts subsystem for the game server: passwordless magic-link sign-in, server-side
saved decks, and win/loss stats. It is off by default — with it disabled the server behaves exactly
as before (anonymous play, in-memory/Redis state, no database). All of it lives in `game-server`;
the rules engine is untouched.

> Design notes / decisions are tracked in memory `project_accounts_auth_persistence`. Persistence
> uses **Spring Data JDBC** (not JPA) — explicit queries, no lazy-loading surprises.

## What it adds

- **Accounts** keyed by email; no passwords (magic link).
- **Saved decks** stored per account (the deckbuilder's `SharedDeck` JSON), reachable from any device.
- **Stats** — one row per finished game; win/loss computed on demand.
- **Guest play still works** — login is optional and only unlocks the above.

Redis stays responsible for hot/ephemeral game/lobby/tournament state. Postgres only holds durable,
user-owned data. Different lifecycles, deliberately not merged.

## Enabling it

Set these (e.g. in `.env` for `just server`, or the deploy environment):

```bash
ACCOUNTS_ENABLED=true
ACCOUNTS_AUTOCONFIG_EXCLUDE=          # MUST be cleared (empty) — see "Gating" below
ACCOUNTS_DB_URL=jdbc:postgresql://localhost:5432/argentum
ACCOUNTS_DB_USER=argentum
ACCOUNTS_DB_PASSWORD=argentum
ACCOUNTS_AUTH_SECRET=<long-random-string>   # blank => random per restart (dev only)
APP_BASE_URL=http://localhost:5173          # origin the magic link points at
ACCOUNTS_FROM_EMAIL=no-reply@wingedsheep.com
# Mailgun SMTP — leave MAIL_USERNAME blank in dev to log the link to the console instead of sending.
MAIL_HOST=smtp.mailgun.org      # smtp.eu.mailgun.org for an EU-region Mailgun domain
MAIL_PORT=2525                   # default; see "Outbound SMTP ports" below
MAIL_USERNAME=postmaster@your-domain.mailgun.org
MAIL_PASSWORD=<mailgun-smtp-password>
```

In Docker, `docker-compose.yml` already defines a `postgres` service; set `ACCOUNTS_ENABLED=true`
and `ACCOUNTS_AUTOCONFIG_EXCLUDE=` in the deploy env to turn it on.

### Outbound SMTP ports

`MAIL_PORT` defaults to **2525**, not the usual 587. Many cloud hosts (Scaleway, GCP, …) block
outbound connections on 25 / 465 / 587 to curb spam, and Mailgun listens on **2525** for exactly this
case. A blocked port shows up as the sign-in request hanging, then a server-side
`MailConnectException: Couldn't connect to host … Operation timed out`. If your host *does* allow 587
(or you use a provider that needs it), set `MAIL_PORT=587`. Verify from the host with
`nc -zv <MAIL_HOST> 2525`.

### Gating (why the exclude env var exists)

`game-server` always has the JDBC/Flyway/Postgres jars on the classpath. Spring Boot's
`DataSourceAutoConfiguration` **fails at startup** if no datasource URL is configured, and Spring
Data JDBC probes the DB for its dialect at startup. So by default we exclude
`DataSourceAutoConfiguration` + `FlywayAutoConfiguration` (Flyway and Spring Data JDBC are both
`@ConditionalOnBean(DataSource)`, so they cascade-disable). That keeps the server — and the whole
test/e2e suite — booting with no database.

To enable, **clear** the exclude (`ACCOUNTS_AUTOCONFIG_EXCLUDE=`) so the auto-config can run, and set
`ACCOUNTS_ENABLED=true` so the account beans (`@ConditionalOnProperty`) load and Flyway runs.

## Schema

Flyway migration `V1__init.sql`:

| Table | Purpose |
|-------|---------|
| `users` | account: email (unique), display name, created_at |
| `login_tokens` | single-use magic-link tokens (SHA-256 hashed, short TTL) |
| `decks` | saved decks: denormalized name/format + full `SharedDeck` JSON in `data` |
| `match_results` | one row per finished game |
| `match_participants` | a seat in a game (user_id null for guests/AI), won flag |

## Auth flow (magic link)

1. `POST /api/auth/request-login { email }` → upsert account, email a single-use link (logged to
   console if mail isn't configured). Always returns 200 (never reveals whether the email exists).
2. The link is `${APP_BASE_URL}/login/verify?token=…`. The client page exchanges it:
   `POST /api/auth/verify { token }` → `{ authToken, user }`.
3. The client stores `authToken` in `localStorage['argentum-auth']` and sends it as
   `Authorization: Bearer …` on REST calls and on the WebSocket connect handshake.

Auth tokens are stateless HMAC-SHA256-signed (a minimal JWT shape) so REST calls don't hit the DB to
authenticate. The token also links the in-game identity to the account so finished games count toward
the account's stats.

## Endpoints

| Method | Path | Notes |
|--------|------|-------|
| POST | `/api/auth/request-login` | `{ email }` → 200 |
| POST | `/api/auth/verify` | `{ token }` → `{ authToken, user }` |
| GET | `/api/auth/me` | Bearer → `user` |
| PUT | `/api/auth/me` | Bearer + `{ displayName }` → updated `user` (1–40 chars; duplicates allowed) |
| GET | `/api/account/decks` | list summaries |
| GET | `/api/account/decks?full` | every deck in full (one round-trip; powers the unified deck browser) |
| GET | `/api/account/decks/{id}` | full deck |
| POST | `/api/account/decks` | body = `SharedDeck` JSON → created deck |
| PUT | `/api/account/decks/{id}` | replace |
| DELETE | `/api/account/decks/{id}` | |
| GET | `/api/account/stats/me` | `{ games, wins, losses, winRate }` |

> `/api/account/decks` is intentionally separate from the existing stateless `/api/decks`
> (validation, formats, examples).

## Frontend

- `authStore` (standalone Zustand) holds the signed-in user; `api/account.ts` is the REST client.
- Sign-in modal (`LoginModal`) + `/login/verify` page; a nav entry in the connection overlay.
- **Unified Save (everywhere):** every "save a deck" button routes through `useSaveDeck` — when signed
  in it saves to the account (cloud, overwriting a same-named deck via `upsertDeckByName`), otherwise
  to the browser library (localStorage). This covers the deckbuilder Save/Save-as, the tournament/draft
  "Save Deck" + deck-viewer save (previously always localStorage, even when signed in), and the lobby
  deck picker's Paste-tab save.
- **Unified deck library (`useUnifiedDecks`):** merges account decks (fetched in full via `?full`) with
  browser-only decks, each tagged `online`. Feeds (a) the deckbuilder's saved-deck **browser** overlay,
  which shows an **Online / Browser** badge per deck and routes load/rename/delete to the right store,
  and (b) the lobby deck picker's "My Decks" tab, so signed-in users can pick their cloud decks to play.
- **Display name:** editable on the profile page (`PUT /api/auth/me`); the email stays the identity.
- Profile page at `/profile` shows stats + a small **Manage my decks** launcher that opens the
  deckbuilder's deck browser (`/deckbuilder?decks=open`) — one polished list instead of a second copy.
- On sign-in, a landing-page prompt (`DeckMigrationPrompt`) offers to copy browser-only decks to the
  account.

## Tests

- `AuthTokenServiceTest` — token sign/verify/expiry/tamper (pure unit).
- `MagicLinkServiceTest` — login/verify orchestration with mocked repositories.
- `FlywayMigrationTest` — applies `V1` against a real Postgres via Testcontainers and exercises the
  account/deck/stats round-trip. Self-skips when Docker is unavailable.
