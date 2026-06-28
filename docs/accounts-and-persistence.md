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
- **Stats** — one row per finished game; per-account win/loss, preferred colors/sets, game modes
  played, head-to-head vs specific opponents, and a game history, all computed on demand.
- **Deck contents** of every recorded game are stored card-by-card, so we can compute the most-played
  cards and per-card win rates.
- **Tournaments** are recorded on completion (settings + final standings).
- **Admin dashboard** — global totals, games-per-day, mode/color distributions, top cards, recorded
  tournaments, and an IP-based geolocation estimate of where players connect from.
- **Guest play still works** — login is optional. Per-account stats need an account, but guest games
  (not AI) still count toward the global admin stats and geolocation.

A game is only recorded if it reached a winner **or** had more than a trivial number of actions
(`frameCount >= 10`), and only if at least one seat is a human (AI-only games are skipped).

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
| `users` | account: email (unique), display name, created_at, `is_admin` (added in `V3__admin_role.sql`) |
| `login_tokens` | single-use magic-link tokens (SHA-256 hashed, short TTL) |
| `decks` | saved decks: denormalized name/format + full `SharedDeck` JSON in `data` |
| `match_results` | one row per finished game |
| `match_participants` | a seat in a game (user_id null for guests/AI), won flag |

Flyway migration `V2__match_stats.sql` extends the stats schema:

| Table / columns | Purpose |
|-----------------|---------|
| `match_results.game_mode / frame_count / turn_count` | matchmaking mode + activity measures (the recording gate, games-per-day, mode distribution) |
| `match_participants.colors / set_codes / is_ai / client_ip` | per-seat deck color identity + sets, AI flag (distinguishes AI from guests), and raw client IP (**admin-only**, never sent to clients) |
| `match_participant_cards` | each seat's deck card-by-card (`card_name`, `copies`) — backs most-played-cards + per-card win rate |
| `tournaments` | finished tournaments + settings (format, mode, set codes, player count, rounds, winner) |
| `tournament_participants` | a seat in a tournament with final placement + W/L/D |

Flyway migration `V3__admin_role.sql` adds `users.is_admin` (boolean, default false) — the per-account
admin flag (see **Admin access** below).

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

## Admin access

The admin dashboard accepts **two** credentials, resolved by `AdminAuthService`:

1. **Bootstrap password** — the `X-Admin-Password` header matching `GAME_ADMIN_PASSWORD`. Not tied to
   an account; always works (a break-glass path), but meant only to get the first admin in. This also
   works on a server with no database at all (the replay browser is password-only).
2. **Admin account** — a normal `Authorization: Bearer …` whose account has `is_admin = true`. The
   flag is resolved against the DB **per request** (not baked into the token), so a promotion/demotion
   takes effect immediately. Promotion is done from the dashboard's **Players** view.

Bootstrapping the first admin: set `GAME_ADMIN_PASSWORD`, open `/admin`, sign in with the password,
go to **Players**, and promote an account. From then on that account reaches `/admin` with its normal
sign-in and the password can be retired. `GET /api/auth/me` now includes `isAdmin`, which the client
uses to show an **Admin dashboard** link on the profile and to skip the password prompt at `/admin`.

Every admin endpoint (`/api/admin/**` and `/api/stats/admin/**`) accepts either credential.

## Endpoints

| Method | Path | Notes |
|--------|------|-------|
| POST | `/api/auth/request-login` | `{ email }` → 200 |
| POST | `/api/auth/verify` | `{ token }` → `{ authToken, user }` |
| GET | `/api/auth/me` | Bearer → `user` (includes `isAdmin`) |
| PUT | `/api/auth/me` | Bearer + `{ displayName }` → updated `user` (1–40 chars; duplicates allowed) |
| GET | `/api/account/decks` | list summaries |
| GET | `/api/account/decks?full` | every deck in full (one round-trip; powers the unified deck browser) |
| GET | `/api/account/decks/{id}` | full deck |
| POST | `/api/account/decks` | body = `SharedDeck` JSON → created deck |
| PUT | `/api/account/decks/{id}` | replace |
| DELETE | `/api/account/decks/{id}` | |

> `/api/account/decks` is intentionally separate from the existing stateless `/api/decks`
> (validation, formats, examples).

### Stats (all under `/api/stats`)

Per-user endpoints take `Authorization: Bearer …`; admin endpoints take either admin credential (see
**Admin access**). Both groups are only mounted when accounts are enabled.

| Method | Path | Notes |
|--------|------|-------|
| GET | `/api/stats/me` | `{ games, wins, losses, winRate }` |
| GET | `/api/stats/me/colors` · `/sets` · `/modes` | `[{ label, count }]` breakdowns |
| GET | `/api/stats/me/opponents` | head-to-head `[{ opponent, isAi, wins, losses }]` |
| GET | `/api/stats/me/history?limit&offset` | recent games |
| GET | `/api/stats/me/cards?limit` | most-played cards |
| GET | `/api/stats/me/tournaments?limit` | tournament finishes with placement |
| GET | `/api/stats/admin/overview` | global totals |
| GET | `/api/stats/admin/games-per-day?days` | daily game counts |
| GET | `/api/stats/admin/modes` · `/colors` | global distributions |
| GET | `/api/stats/admin/cards` · `/cards/win-rates?minDecks` | most-played + per-card win rate |
| GET | `/api/stats/admin/tournaments?limit` | recorded tournaments |
| GET | `/api/stats/admin/geo` | IP → coarse location, aggregated by location (raw IPs never returned) |

### Admin — players (`/api/admin/users`, either admin credential)

| Method | Path | Notes |
|--------|------|-------|
| GET | `/api/admin/users` | roster: every account + lifetime games/wins, `isAdmin`, last played |
| GET | `/api/admin/users/{id}` | one account's full stats (overview, colors, modes, head-to-head, top cards, tournaments, recent games) |
| POST | `/api/admin/users/{id}/admin` | `{ isAdmin }` → grant/revoke admin access |

The replay browser lives at `/api/admin/games` and `/api/admin/games/{id}/replay` (also either
credential; password-only on a DB-less server).

Aggregate queries live in `StatsQueryService` (plain SQL via `JdbcTemplate`). Geolocation
(`GeoIpService`) resolves IPs via the free ip-api.com batch endpoint, cached in-process; it's only
called from the admin `geo` endpoint, never the hot recording path.

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
- Profile page at `/profile` shows the win/loss summary plus colors played (a Recharts bar chart),
  sets, game modes, head-to-head, most-played cards, tournament finishes, and a recent-games list — all
  from `/api/stats/me/*` via `api/account.ts`. It also has a small **Manage my decks** launcher that
  opens the deckbuilder's deck browser (`/deckbuilder?decks=open`).
- Admin page at `/admin` is a **hub** (`AdminPage` → `AdminHub`) that routes to three areas, each its
  own self-scrolling screen (`AdminScreen` in `adminUi.tsx` — the whole app runs in
  `#root { overflow: hidden }`, so admin screens scroll themselves rather than the document):
  - **Stats** (`AdminDashboard`, `api/adminStats.ts`) — headline totals, games-per-day line chart,
    mode/color distributions, most-played + highest-win-rate cards, recorded tournaments, geolocation.
  - **Replays** (`ReplayViewer`) — browse and play back every completed game.
  - **Players** (`AdminPlayers`, `api/adminUsers.ts`) — the account roster, a per-account drill-down
    (full stats + recent games), and the **Make admin / Revoke admin** control.
  Admin auth is the shared `AdminAuth` (`api/adminAuth.ts`): the bootstrap password (kept in
  sessionStorage) or, for an admin account, its Bearer token. A signed-in admin skips the password
  prompt entirely; the profile page shows an **Admin dashboard** link when `user.isAdmin`.
- On sign-in, a landing-page prompt (`DeckMigrationPrompt`) offers to copy browser-only decks to the
  account.

## Tests

- `AuthTokenServiceTest` — token sign/verify/expiry/tamper (pure unit).
- `MagicLinkServiceTest` — login/verify orchestration with mocked repositories.
- `FlywayMigrationTest` — applies `V1` + `V2` against a real Postgres via Testcontainers and exercises
  the account/deck/stats round-trip plus the V2 stats schema and its Postgres-specific aggregate SQL
  (set-code unnest, card win-rate `FILTER`, games-per-day interval, tournament round-trip, cascade
  deletes). Self-skips when Docker is unavailable.
- `DeckProfilerTest` — deck color-identity (WUBRG order) + set derivation, colorless/fallback/pin cases.
- `MatchResultSinkTest` — the recording guard: AI-only games skipped, human/guest games recorded with
  their deck cards; same for the tournament sink.
