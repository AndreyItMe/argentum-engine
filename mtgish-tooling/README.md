# mtgish Tooling

This module turns the external [mtgish](https://github.com/i5jb/mtgish) oracle IR into actionable Argentum data.
It is analysis and generation tooling, not a runtime dependency and not a card loader.

## Commands

Run through `just` from the repo root:

```bash
just coverage-dashboard          # interactive TUI over everything below
just coverage --set POR
just coverage-fidelity --set POR
just coverage-verify POR
just coverage-generate --set TMP
just coverage-refresh-set POR
```

The installed CLI has four subcommands:

```bash
mtgish-tooling probe      # coverage: can the SDK/engine express a card?
mtgish-tooling fidelity   # calibration: does generated output match implemented cards?
mtgish-tooling autogen    # write generated Kotlin drafts
mtgish-tooling dashboard  # interactive TUI (probe + autogen + a cross-set capability index)
```

## Dashboard (TUI)

`just coverage-dashboard` is a navigable, two-pane terminal UI over the same analysis the CLIs print
— no flags. It composes the existing per-card primitives (`Probe.analyze`, `Emitter.renderCard`)
rather than re-implementing coverage logic, and memoizes per-set results so a keypress never recomputes.

- **Title** — corpus totals: implemented / total, and `+N auto-gen ready` (cards the generator can
  render whole). The figure sums only the sets analyzed so far and grows as you browse; `f` (or
  launching with `--scan`) analyzes every set up front so it's complete.
- **Left pane** — every set (implemented ∪ Scryfall-cached) with its full name and release year,
  sorted newest-first by default (`s` toggles alphabetical), showing `implemented/total` and `gN`
  auto-gen coverage (cards the generator renders whole, `g?` until that set is analyzed). `/` searches
  sets by name or code.
- **Right pane** — the selected set's `implemented / auto-gen / free-to-add / blocked` breakdown.
  **Auto-gen** spans *all* cards (implemented included) so a 100%-implemented set still shows how much
  the generator could reproduce; **free-to-add** is the missing-card backlog. Plus the feature
  leaderboard of engine work ranked by how many blocked cards it would unlock, and (for snapshot sets)
  the golden-fidelity tiers.
- **Drill in** (`→`/`enter`) — the set's card list, coloured by the generator's verdict (green WHOLE,
  yellow SCAFFOLD, red BLOCKED) with a marker for implemented (`✓`) vs new (`+`/`~`/`✗`); a red `✓` is
  an implemented card the generator *can't* reproduce. Filter with `/`.
- **Card detail** — two tabs (`tab` to switch): **Kotlin** shows the emitter's generated `cardDef`
  DSL, syntax-highlighted, above a `⚠ missing mappings` block listing the still-unmapped capabilities;
  **capabilities** lists every required capability with its verdict + which sets implement the card.
- **`c`** — the cross-set capability index: every set's leaderboard rolled into one ranking of "what
  engine work unlocks the most cards everywhere" (computed on demand, with a progress bar).
- **`f` / `--scan`** — full scan: analyze every set up front (fills all `gN` counts + the global
  total; also makes `c` instant).
- Keys: `↑↓`/`jk` move · `→`/`enter` drill in · `←`/`esc` back · `tab` Kotlin/capabilities · `/`
  search/filter · `s` sort · `f` scan-all · `r` fetch a set from Scryfall · `c` cross-set · `q` quit.
  Needs an interactive terminal (drives `/dev/tty` via `stty`).

The dashboard adds no runtime dependency — it uses raw ANSI escapes and `stty` raw mode
(`dashboard/Tui.kt`), keeping the module dependency-light. `dashboard --render` prints static frames
to stdout (no raw mode) for smoke-testing outside a live terminal.

## Data Flow

1. `Mtgish.kt` loads mtgish card IR from `mtgish-tooling/data/mtgish.lines.json` (auto-downloaded, gitignored).
2. `Cards.kt` joins mtgish names to Scryfall cached set data and existing Argentum card names.
3. `bridge/*` maps mtgish tags to Argentum capabilities for coverage scoring.
4. `emitter/*` renders Kotlin `card { ... }` DSL from mtgish rules.
5. `mtg-sets:verifyGeneratedCards` compiles generated drafts and serializes them with the same exporter used for golden snapshots.
6. `fidelity --gate SET` compares generated gameplay trees to golden snapshots, with a small allowlist of known-equivalent representations and Scryfall-oracle drift reporting.

## Two dictionaries

A mtgish tag is mapped in two independent places, each readable as a flat list of entries:

| Dictionary | Question it answers | Lives in |
|------------|---------------------|----------|
| **Capability** (`bridge/`) | *Can* Argentum express this tag? | `coverage/bridge/*` |
| **Rendering** (`emitter/`) | *What Kotlin DSL* does it emit?  | `coverage/emitter/*Handlers.kt` |

The probe uses only the capability dictionary; the emitter uses both. The capability dictionary can
say "yes" for a tag the emitter still declines to render exactly — that's the `SCAFFOLD` tier.

### Capability dictionary (`bridge/`)

Add mappings by editing the themed bridge files. Each entry is one line:

```kotlin
effect("DrawNumberCards", "DrawCards")
composed("DestroyPermanent", "MoveToZone -> graveyard", composes = listOf("MoveToZone"))
envelope("Targeted", "structural wrapper")
```

Mapping kinds:

- `keyword`: mtgish tag maps to an SDK `Keyword` enum value.
- `effect`: mtgish tag maps to one SDK `Effect` `@SerialName`.
- `composed`: mtgish action is expressible by existing Argentum primitives.
- `envelope`: structural IR node whose nested children carry the real capability.
- `supported`: accepted non-effect capability, such as trigger/cost vocabulary.

`effect`/`keyword` tags are validated against a live scan of the SDK source, so a typo or a renamed
SerialName surfaces as a coverage gap rather than rotting silently.

### Rendering dictionary (`emitter/`)

Each mtgish `_Action` tag maps to the Argentum Effect DSL string it emits. Entries are split across
themed `*Handlers.kt` files and registered with one of two forms:

```kotlin
simple("Shuffle", "ShuffleLibraryEffect()")              // a constant, argument-free effect
on("DrawNumberCards") { node, args, tvar -> ... }        // needs amount/target/filter recovery
```

Handlers do **not** track imports: `Shells.importsFor` derives the file's import block by scanning the
emitted code for SDK symbols, so a handler is a pure `tag → DSL string` mapping. Return `null` whenever
exact rendering isn't possible — the card downgrades to `SCAFFOLD` rather than emit something wrong.

Important files:

- `ActionHandlers.kt`: the `actionHandlers { }` builder + the merged `_Action → handler` registry.
- `DamageDrawLifeHandlers.kt`, `ZoneHandlers.kt`, `TapLayerStateHandlers.kt`, `PlayerContinuousHandlers.kt`: themed action handlers.
- `TargetRecovery.kt`: target and filter reconstruction (the target/filter sub-dictionary).
- `CardStructure.kt`: spell, triggered ability, and activated ability envelopes.
- `SpellShortcuts.kt`: whole-card shapes recognised as one named `EffectPatterns.*`.
- `StaticAbilities.kt`: `PermanentRuleEffect → flags()/staticAbility { }`.
- `Shells.kt`: mana cost, type line, metadata, KDoc, and the import auto-derivation.
- `Emitter.kt`: whole-card assembly.

To add support for a new mtgish action:

1. Find the mtgish `_Action` value in a failing `fidelity --gate` or scaffold reason.
2. Add a `simple(...)` or `on(...) { }` entry in the closest themed `*Handlers.kt` file.
3. If the handler needs target/filter support, add it to `TargetRecovery.kt` rather than widening filters.
4. Return `null` when exact rendering is not possible. This deliberately downgrades the card to `SCAFFOLD`.
5. Run `just coverage-verify POR` or another calibrated set before trusting the change.

## Complete renders vs scaffolds

Every emitted card is one of two tiers, and the file's header banner says which:

- **Complete render** — the emitter rendered the whole card; the header says *"GENERATED by
  mtgish-tooling … Complete render — no manual wiring needed"* (no "draft" wording). It's still
  predictive, so review the rules text and add a scenario test, but there are no stubs to fill in.
- **Scaffold** (incomplete render) — some structure couldn't be recovered. The header leads with a
  `// TODO:` and the body carries `// STRUCTURE needs human wiring: …`. These must be finished by hand.

### Emitting only complete renders (skip scaffolds)

To get *only* the cards the emitter can render whole — skipping anything blocked or scaffolded:

```bash
just coverage-generate --set TMP                 # missing cards only -> mtgish-tooling/generated/<set>/
just coverage-refresh-set TMP --complete-only    # whole set into definitions, scaffolds skipped
```

`coverage-generate` (`autogen --write`) is **always** complete-only: it writes only coverable,
whole-renderable missing cards into the staging dir. For a full-set refresh, plain
`coverage-refresh-set` includes scaffolds; add `--complete-only` to drop them.

## Fidelity Policy

The generator must be conservative:

- A confidently wrong generated card is worse than no generated card.
- If a target, filter, amount, cost, gate, or choice cannot be rendered exactly, return `null` and scaffold.
- `coverage-verify` is the regression gate: generated cards must compile and gameplay-tree match calibrated snapshots.
- When golden snapshots disagree with current Scryfall oracle text, the gate reports `GOLDEN DRIFT SUSPECTED` instead of treating the generated output as wrong.

## Source Refreshes

`autogen --write-all --set CODE` replaces real set card source files with mtgish-generated files:

```bash
just coverage-refresh-set POR                  # complete renders + scaffolds (TODO-flagged)
just coverage-refresh-set POR --complete-only  # only complete renders; scaffolds skipped
```

By default this writes complete generated cards and scaffold files for unsupported structures (pass
`--complete-only` to skip the scaffolds). Existing source files that use `basicLand(...)` are
preserved, and mtgish basic-land entries are skipped, because sets can contain multiple basic-land
printings that Scryfall/mtgish collapse by name. Use it only when intentionally converting a set to
mtgish-authored source, then compile and refresh snapshots.

## Canonical vs reprint placement

The write paths (`--write` / `--write-all`) verify each card's **canonical home** against Scryfall's
cross-set printing list — the same rule `scripts/check-card-printing.py` enforces: a card's
`CardDefinition` must live in its *earliest real-expansion printing*, and every later set contributes
only a `Printing(...)` row, never a second colliding `card(...)` (which `CardRegistry` would resolve
last-registration-wins). So a card whose earliest printing is a different set is emitted as a
`Printing(...)` row instead of a full definition — but only when the canonical is **actually
implemented** in that earlier set. If it isn't (the canonical doesn't exist yet, or its earliest set
isn't scaffolded), the card is kept as a full `card(...)` under a `// TODO(mtgish)` banner so it's
flagged rather than silently misplaced. Pass `--skip-reprints` to drop reprints entirely instead.

To resolve those TODOs, `--relocate` backfills the missing canonicals:

```bash
just coverage-relocate POR     # emit the canonical for every POR card whose earliest set is elsewhere
                               # into that earlier set's cards/ package (with the earlier set's metadata)
```

After relocating, scaffold any brand-new earlier sets (an `MtgSet` object + a `MtgSetCatalog` entry),
re-run `coverage-refresh-set` so the later set becomes `Printing(...)` rows, then compile + refresh
snapshots. Network: `--relocate` and the write paths do one Scryfall `unique=prints` lookup per card,
cached under `~/.cache/scryfall/printings/` (shared with `check-card-printing.py`).
