# The Lord of the Rings: Tales of Middle-earth (LTR) — Implementation Plan

> **Every card must be implemented perfectly — exactly as stated in the rules.** No
> approximations, no "close enough", no silently dropped clauses. Each card's behavior must
> match its oracle text (from `ltr_set.json`) and the Comprehensive Rules
> (`MagicCompRules_20260417.pdf`) in full, including edge cases, timing, and interactions.
> A card is not done until its scenario test proves the rules-correct behavior.

Set scaffolding is done:

- `mtg-sets/.../definitions/ltr/LordOfTheRingsSet.kt` — registered in `MtgSetCatalog.all`.
- `mtg-sets/.../definitions/ltr/cards/` — empty, ready for one file per card.
- `backlog/sets/lord-of-the-rings/cards.md` — the 291-card checklist (mark `[x]` as you go),
  split into **Draft** (261) and **Extra** (30) exactly as `scripts/card-status` partitions.
- `backlog/sets/lord-of-the-rings/ltr_set.json` — the **offline card-data source** for this set.

Verify status anytime with: `scripts/card-status --set LTR` (and `--list --set LTR`).

## Progress

- ✅ **Foundation** — set scaffolding committed.
- ✅ **`ltr-cards` (no-engine-change big PR)** — merged (PR #199). Draft cards at 126/261.
- 🚧 **The Ring** — in progress on branch `ltr-cards2`. Headline mechanic (CR 701.52),
  unlocks ~50 missing cards (the largest bucket; Amass is the next at ~24). Building the
  substrate first (one commit), then one card per commit. See
  [`MagicCompRules_20260417.pdf`] rule 701.52 for the four cumulative emblem abilities.
- ⬜ **Amass Orcs** — next mechanic (~24 cards).

## Data sources — do NOT hit the network

- **Card data** (name, mana cost, type line, oracle text, P/T, rarity, collector number,
  artist, flavor, image URI): read from `ltr_set.json`, **not** Scryfall. It is a full
  Scryfall dump of all 291 cards (`.data[]`, keyed by the usual field names). When running
  `add-card`, feed it the matching entry from this file instead of doing a Scryfall lookup.
- **Rules**: cite and verify against `MagicCompRules_20260417.pdf` (in the repo root),
  **not** yawgatog or any web source. Read the relevant pages with the `Read` tool's
  `pages` parameter. Quote rule numbers from that document.

## Workflow

Each card is implemented with the **`add-card` skill** (oracle errata, set registration,
scenario test) — but source its card data from `ltr_set.json` and its rule references from
`MagicCompRules_20260417.pdf` per the section above. Run one card per Claude invocation:

```
/add-card <Card Name>   # from set LTR; use ltr_set.json for data, the CompRules PDF for rules
```

The skill is the source of truth on whether a card needs an engine change. The buckets
below are a *provisional* triage to sequence the work — confirm during implementation.

### Git strategy

1. **Foundation commit** (this scaffolding) lands first.
2. **One big PR — "LTR: cards (no engine change)"**, branch `ltr-cards`,
   **one commit per card**. Only cards that compose from existing SDK primitives go here.
3. **One PR per engine-change feature**, each off `main` (e.g. `ltr-the-ring`,
   `ltr-amass`). When several cards share one new engine feature, that feature's PR can
   land all of them together — note it in the PR.

### Per-card procedure

For each unchecked card in `cards.md`:

1. `/add-card <name>` — implement via the DSL, no class inheritance.
2. If it composes from existing primitives → commit on `ltr-cards` (`Add <Card>`).
3. If `add-card` finds it needs a new `Effect`/keyword/replacement/SDK change →
   stop, branch off `main`, build the engine feature + the card + tests, open its own PR.
   Update `docs/card-sdk-language-reference.md` in the same PR (required for any SDK change).
4. Check the box in `cards.md` and update the `Implemented:` count.

## Provisional triage

> ⚠️ First-pass guess from oracle text — **the `add-card` skill decides for real.**
> A "Bucket B" card that turns out to compose cleanly belongs in the big PR; a "Bucket A"
> card that surprises you moves to its own PR.

### Bucket A — likely no engine change → big `ltr-cards` PR

Vanilla bodies, plain evergreen keywords (flying, first/double strike, trample, vigilance,
haste, reach, menace, deathtouch, defender, ward, flash, indestructible), stat mods, simple
targeted/activated abilities, regeneration, token-on-death, "deal N to any target",
conditional static buffs. Most commons and a large share of uncommons land here. Scry,
Mill, and basic Equip already exist as SDK primitives (`EffectPatterns.kt`), so cards whose
only "mechanic" is one of those belong here too.

### Bucket B — verify; composable but non-trivial (most land in the big PR)

Token generators (**Food**, **Treasure**, **Clue**), Cycling / Landcycling / Typecycling,
Crew, Goad, Fight, Landfall, ETB/LTB value, modal spells, sacrifice-for-value, graveyard
recursion, +1/+1 counter synergies, "legendary matters" payoffs. These compose from
existing atomic effects in most cases — confirm token types and cycling variants exist in
the SDK first; if a token predefinition or a cycling sub-keyword is missing, that is a small
engine add, not a card.

### Bucket C — likely needs engine work → own PR(s)

Group by the shared feature so one PR can clear several cards:

- **The Ring tempts you / The Ring / Ring-bearer** (≈50 cards): the set's headline
  mechanic. "The Ring" is a per-player object (not a normal permanent) that gains four
  cumulative abilities as its owner is *tempted*, and tempting designates/redesignates a
  **Ring-bearer** creature. This needs new per-player game state, the four Ring abilities
  (Ring-bearer is legendary + can't be blocked by stronger creatures; tap a non-Ring-bearer
  attacker on Ring-bearer attack; attacker damage forces a sacrifice; draw + lose life on
  Ring-bearer death/attack), and the `the Ring tempts you` action. Largest feature in the set
  — build the substrate first, then attach the ~50 cards.
- **Amass Orcs N** (27 cards, keyword): put N +1/+1 counters on an **Army** you control; if
  you don't control one, first create a 0/0 black Orc Army token. "Amass Orcs" also makes the
  Army an Orc. Needs the Army token + the amass action; check whether the engine already
  models Army/amass before building.
- **The One Ring** (artifact): ETB protection-from-everything until your next turn, a burden
  counter on upkeep, then "lose life equal to burden counters", plus tap-to-draw. The
  "protection from everything" + escalating-upkeep combo likely needs care.
- **Sauron, the Lidless Eye / Mirror-galadriel etc. — control / "amass" payoffs**: verify
  against existing control-change support.
- **Affinity / cost reduction** payoffs (e.g. artifact-count affinity) — confirm the affinity
  primitive exists.
- **"protection from everything"** (2 cards) — confirm the protection model covers the
  "everything" quality (Rule 702.16) including indestructible-like total prevention.

> The counts above are oracle-text scans, not a guarantee — re-confirm each card's bucket
> when you implement it.

## Notes

- **Implement every card faithfully**, reproducing oracle text as printed (use the Scryfall
  oracle/errata text in `ltr_set.json`).
- Verify any MTG rule number against `MagicCompRules_20260417.pdf` (repo root) before
  citing it — read the relevant pages with `Read(pages=...)`. Do not use web sources.
- Battlefield filtering must use projected state (`matchesWithProjection`).
- Basic lands (Plains/Island/Swamp/Mountain/Forest) are covered by `basicLandsFallback`;
  add LTR-art basic-land variants only if you want the distinct printings.
