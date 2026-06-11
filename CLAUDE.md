# CLAUDE.md

Guidance for Claude Code working in this repository.

## Collaboration rules

- **Focus on your own work.** If a change you didn't make breaks the build, report it to the user and stop. Don't
  revert, stash, or discard others' changes — that's likely another agent's in-flight work. Pause until the user
  confirms it is safe to continue.
- **Implementing a card from a backlog file** (e.g., `backlog/sets/scourge/cards.md`) → always use the `add-card` skill.
- **Adding an engine/SDK/server/client feature** (a new effect, trigger, condition, keyword, decision flow, or any
  capability that isn't a single card) → always use the `add-feature` skill. It enforces composition-over-monoliths,
  designing each new SDK type for reuse, full cross-layer tracing, and performance + UX review.
- **Verify MTG rule numbers before citing them.** Rule numbers are easy to misremember (613.8 vs 613.7, 704.5 vs
  704.6, etc.). Whenever you reference a specific rule number in code comments, commit messages, PR descriptions,
  or chat, look it up first via the official WotC rules page <https://magic.wizards.com/en/rules>. The linked
  plain-text Comprehensive Rules `.txt` is too large to fetch into context — download it (e.g. `curl -o`) and
  `grep` it locally for the rule number/text. If you can't verify, describe the rule by name instead of guessing a number.

## Project Overview

Argentum Engine — Magic: The Gathering rules engine + online play platform in Kotlin. Pure ECS, immutable `GameState`,
pure functional `(GameState, GameAction) -> ExecutionResult(GameState, List<GameEvent>)`.

**Stack:** Kotlin 2.3.20 / JDK 21 / Gradle 8.14 / Kotest 6.1.5 / Spring Boot 4.0.4 (backend); React 19.2 / TS 5.9 /
Zustand 5.0 / Vite 8.0 (frontend).

## Build Commands

```bash
just build                          # Build entire project
just test | test-rules | test-server
just test-class CreatureStatsTest   # Specific test class
just server | client                # Run game server / web client dev
```

Direct gradle: `./gradlew :rules-engine:test --tests "CreatureStatsTest"` ·
Web client: `cd web-client && npm run dev | build | typecheck` (dev at localhost:5173).

## Card status script

Reports which cards in a set are implemented vs missing by diffing Kotlin source against Scryfall's
canonical list. Cards are partitioned into Draft (Scryfall `booster: true`) and Extra (starter-deck
exclusives, Special Guests, bonus sheets) so booster-relevant progress is distinguishable from completionist work.

```bash
scripts/card-status --set BLB              # summary table renders, extras column populated
scripts/card-status --list --set BLB       # missing cards grouped under Extra:
scripts/card-status --cards BLB            # full listing split into Draft: / Extra: sections
```

## mtgish coverage + auto-gen tooling (`:mtgish-tooling` module)

A **predictive, non-authoritative** toolchain that maps the [mtgish](https://github.com/i5jb/mtgish)
oracle-IR corpus onto our SDK capabilities, to triage the backlog and draft easy cards. It is a
`scripts/`-style analyzer, **never a card loader** — ground truth stays a human-authored `cardDef`
whose scenario test passes. The mtgish→Argentum mapping is typed Kotlin in two dictionaries — capability
(`mtgish-tooling/src/main/kotlin/com/wingedsheep/tooling/coverage/bridge/`) and rendering
(`.../coverage/emitter/`); see [`mtgish-tooling/README.md`](mtgish-tooling/README.md).
First run auto-downloads the 29 MB mtgish IR into `mtgish-tooling/data/` (gitignored).

```bash
# DASHBOARD — interactive TUI over all the analysis below (no flags; q to quit).
just coverage-dashboard                 # navigate sets, drill into cards, c = cross-set capability index

# COVERAGE — which missing cards need no engine work, and which feature unlocks the most.
just coverage --set TMP                 # implemented / FREE-to-implement / blocked + feature leaderboard
just coverage --set TMP --free          # also list the implementable-today cards
just coverage --card "Shivan Dragon"    # one card: required capabilities + verdict
just coverage --calibrate POR           # trust check: implemented cards must classify coverable (POR 100%)

# FIDELITY — could we AUTO-AUTHOR a card? Diffs the bridge vs each card's compiled golden snapshot.
just coverage-fidelity --set POR        # tiers cards AUTO / SCAFFOLD / MISS (AUTO ⟺ emitter renders whole)
just coverage-fidelity --all            # cross-set table (renders-whole AUTO: POR ~96%, unseen ~15-20%)
just coverage-fidelity --emit "Lava Axe"  # print the generated cardDef DSL (complete metadata, no TODO)

# AUTO-GEN — turn the bridge on a set's UNIMPLEMENTED cards.
just coverage-gaps --set TMP            # AUTOGEN / SCAFFOLD / BLOCKED counts + scaffold-gap & blocked-capability leaderboards
just coverage-generate --set TMP        # draft .kt for the AUTOGEN cards -> mtgish-tooling/generated/<set>/
just coverage-relocate TMP              # backfill canonicals of cards whose earliest printing is another set

# VERIFY — the real gate: COMPILE the emitted cards + diff serialized caps vs golden.
just coverage-verify --set POR          # POR: 184/184 emitted & compile-verified, 0 capability mismatch
just coverage-verify-all                # fast hermetic golden test + the compile/gameplay-tree gate for every 0-mismatch set

# REGRESSION — the FAST in-suite net (runs in `just test`): re-emit each calibrated set from a committed
# slice and diff a committed golden. EmitterGoldenTest fails with the exact card on any emitter drift.
just coverage-fixtures POR              # (re)generate the vendored fixtures from real data (needs IR + Scryfall cache)
just coverage-fixtures --rebless        # re-bless the golden from the committed slice after an intentional emitter change
```

**When to use.** Spoiler-season/backlog triage (`coverage` leaderboard = which feature unlocks the
most cards); deciding whether a missing card is pure authoring vs. needs `add-feature`
(`coverage-gaps`); getting a blank-page head-start on simple cards (`coverage-generate`);
`coverage-verify` proves the emitted drafts actually compile and capability-match before you trust them.

**Hard rules.** Generated `.kt` are **DRAFTS in a staging dir** — they must compile, get a scenario
test, and be human-reviewed before moving into a set's `cards/` package. `coverage-verify` proves
*compile + capabilities*, NOT behaviour (a filter/count can be subtly wrong — scenario test is the
real gate). The emitter is Portal-tuned: it renders 100% of Portal (compile-verified) but only ~15-20% of unseen
sets, converging as you add emitter handlers / bridge entries (one helps all sets). Keep using
the `add-card` skill for real implementation.

**Reprint placement.** The write paths verify each card's canonical home against Scryfall's cross-set
printing list (the `check-card-printing` rule): only a card's *earliest real printing* gets a full
`card(...)`; later sets get a `Printing(...)` row, never a duplicate canonical. A card whose earliest
set lacks the canonical is left as a TODO-flagged `card(...)`; `just coverage-relocate <SET>` backfills
those canonicals into their correct earlier set (scaffold any brand-new set's `MtgSet` + `MtgSetCatalog`
entry afterward).

## Module Layout

| Module | Purpose | Deps |
|--------|---------|------|
| `mtg-sdk` | DSLs, data models, primitives — pure data, no logic | — |
| `mtg-sets` | Card definitions (Portal, Alpha, Onslaught, ...) | sdk |
| `rules-engine` | Core MTG rules (zero server deps) | sdk |
| `gym` / `gym-server` / `gym-trainer` | RL/MCTS env + HTTP transport + self-play SPI | engine, sdk |
| `game-server` | Spring Boot orchestration, WebSocket, state masking | engine, sdk |
| `web-client` | React UI (dumb terminal — no game logic) | — |

**Key principle:** engine is pure (no card-specific code), content is data-driven (no execution logic), API is an
anti-corruption layer between engine and clients.

## Load-bearing rules

- **Immutability:** never mutate components in place — always return new state.
- **Projected state for battlefield filters:** filtering battlefield permanents by type/subtype/color/keywords/P/T
  MUST use `predicateEvaluator.matchesWithProjection(state, projected, ...)`, not `.matches(...)`. Same for
  `cardComponent.typeLine.isCreature` → use `projected.isCreature(entityId)`. Non-battlefield zones (hand, library,
  graveyard, stack) can use base state.
- **Layer dependencies (Rule 613.8):** sort effects in the same layer by trial application before falling back to
  timestamp.
- **Events, not silent mutations:** every state change emits a `GameEvent` so triggers and animations can react.
- **Server is authoritative:** never compute legal actions in the client — the server sends them.

## Card / effect authoring

- **Cards are data:** define via `cardDef { }` DSL, not class inheritance. Register in the set file
  (`definitions/{set}/{Set}Set.kt`) — the engine auto-loads via `ServiceLoader`.
- **Use the `Effects.*` facade** (e.g., `Effects.DrawCards(1)`, `Effects.Destroy()`), not raw constructors.
- **Prefer atomic pipeline effects** (Gather → Select → Move via the domain pattern objects) over monolithic executors
  for library/zone mechanics. `Effects.kt` holds foundational atomic facades; the `*Patterns.kt` objects
  hold compositions like Scry, Mill, SearchLibrary, reached through the single `Patterns` index
  (`Patterns.Library`, `Patterns.Hand`, `Patterns.Group`, `Patterns.Exile`, `Patterns.CreatureType`, `Patterns.Mechanic`).
- **Adding a card** → use the `add-card` skill (handles Scryfall lookup, oracle errata, set registration,
  scenario test).
- **Adding a mechanic** → prefer composing in the relevant `*Patterns.kt` object first; only add a new `Effect` type + executor in
  `rules-engine/handlers/effects/` when atomic primitives don't suffice.
- **Adding an engine/SDK/server/client feature** (a new primitive, mechanic, decision flow, or capability that isn't a
  single card) → use the `add-feature` skill. It enforces composition-over-monoliths, designing each new SDK type for
  the *next* card (not just the one in front of you), full cross-layer tracing (SDK → engine → projection/triggers →
  continuations → server DTO → client), and performance + UX/UI review.

Detailed DSL reference: [`docs/card-sdk-language-reference.md`](docs/card-sdk-language-reference.md) — a complete
catalog of every building block (effects, triggers, conditions, filters, costs, keywords, dynamic amounts, etc.).
**When you add or change anything in the SDK — a new effect, trigger, condition, keyword, dynamic amount, modal
shape, replacement effect, etc. — update this document in the same change.** Architectural reasoning (ECS,
continuations, layer system, mana, priority): [`docs/architecture-principles.md`](docs/architecture-principles.md).

## Testing

- **Unit / integration / scenario tests** — Kotest in `rules-engine` and `game-server`.
- **Card snapshot net** — `CardDefinitionSnapshotTest` (in `mtg-sets`) pins every registered card's
  compiled JSON tree against a committed golden per set, so any SDK change shows up as a reviewable
  per-card diff across the whole corpus. After an *intentional* change, re-bless with
  `./gradlew :mtg-sets:test --tests "*CardDefinitionSnapshotTest" -DupdateSnapshots=true`.
- **Card lint net** — `CardLintTest` (in `mtg-sets`) runs `CardLinter` over every registered card:
  pipeline-variable reads must have writers in scope, `ContextTarget`/`BoundVariable` must resolve
  against the owning ability's requirements, choice-slot reads need declarations. A new SDK type
  that reads/writes a named pipeline variable must be classified in `CardLinter.dataflowFields`
  (the hygiene check fails otherwise). See `card-sdk-language-reference.md` §21.
- **E2E tests** — Playwright in `e2e-scenarios/`, run against full stack. Patterns, scenario config, and `GamePage`
  helper reference: [`docs/e2e-test-patterns.md`](docs/e2e-test-patterns.md).
- **Manual self-play** — drive a full game over the gym server's HTTP step loop to shake out new-set cards that
  don't behave as printed: [`docs/gym-self-play-testing.md`](docs/gym-self-play-testing.md).
- Run: `just test` · `just test-rules` · `just test-class <Name>`.

## Documentation index

| Doc | Topic |
|-----|-------|
| [`architecture-principles.md`](docs/architecture-principles.md) | Core design (ECS, continuations, layer system, mana, priority) |
| [`api-guide.md`](docs/api-guide.md) | Adding cards/mechanics step-by-step |
| [`card-sdk-language-reference.md`](docs/card-sdk-language-reference.md) | Full card SDK / DSL reference — update on any SDK change |
| [`continuous-effect-dependency-system.md`](docs/continuous-effect-dependency-system.md) | Rule 613.8 dependency resolution |
| [`managing-complex-and-rare-abilities.md`](docs/managing-complex-and-rare-abilities.md) | Patterns for complex abilities |
| [`engine-server-interface.md`](docs/engine-server-interface.md) | Engine ↔ API contract |
| [`player-input.md`](docs/player-input.md) | Async I/O and decision protocol |
| [`data-contracts.md`](docs/data-contracts.md) | Client/server JSON payloads |
| [`web-client-architecture.md`](docs/web-client-architecture.md) | Frontend architecture, WebSocket API |
| [`e2e-test-patterns.md`](docs/e2e-test-patterns.md) | Playwright fixtures, GamePage helpers, scenario config |
| [`gym-deckbuild-env.md`](docs/gym-deckbuild-env.md) | Sealed deckbuild gym env (build → play pipeline) + how to supply your own win-rate reward |
| [`gym-self-play-testing.md`](docs/gym-self-play-testing.md) | Driving the gym server over HTTP to manually self-play and surface broken cards |
