# Spike: mtgish as the Lesson 5 coverage-probe extractor backend

**Question.** phase-rs-lessons.md Lesson 5 wants an *oracle-text → required-capability
extractor*. Its risky tier is the heuristic "clause templates" (hand-rolled regex). Could
**mtgish**'s pre-parsed oracle IR replace that tier? The cost of doing so is a
`mtgish-tag → Argentum-capability` **mapping** we'd have to author and maintain. This spike
measures how big and how durable that mapping is, using the doc's own **calibration loop**:
every *already-implemented* card must classify as *coverable-now*; any that doesn't is a hole
in the mapping, not a real feature gap.

## What was built

- `probe.py` — three parts, all tooling, never loads a card:
  1. **Capability registry**, scanned from Kotlin source so it can't rot: 312 Effect
     `@SerialName`s + 74 `Keyword` enum entries.
  2. **Extractor** — walks a card's mtgish IR (`data/mtgish.lines.json`, 32,464 cards) and
     collects the capability-bearing tags (`_Action`, `_Rule`, `_Trigger`, `_Cost`,
     `_LayerEffect`).
  3. **Calibration** — for each implemented card, `required ∖ supported`; reports recall +
     the unmapped-tag tail.
- `mapping.json` — the hand-authored bridge (74 entries). This file *is* the maintenance cost
  being measured. `effect`/`keyword` entries are **validated against the registry** (a wrong
  guess surfaces as a MISSING gap — the mechanism catching its own error); `composed` =
  Argentum builds it from primitives (destroy = `MoveToZone→graveyard`, search, discard…);
  `ignore` = a structural mtgish envelope (`SpellActions`, `TriggerA`, the `May/Unless/If`
  gate cluster) whose capability lives in nested nodes.

## Results

**Calibration (Portal, 184 implemented cards):**

```
RECALL: 182/184 = 98.9% coverable-now
  - 0 tags mapped to a MISSING engine capability  ← every effect/keyword guess validated
  - 2 blocked cards = the 2 tags left deliberately unmapped:
      Exhaustion   (EachPermanentDoesntUntapDuringControllersNextUntap)
      False Peace  (SkipAllCombatPhasesTheirNextTurn)
```

**Vocabulary is small and Zipfian.** Portal's entire 184-card corpus uses **77 distinct
tags**; the top 30 cover 83% of occurrences. A whole classic set is mappable in ~75 lines.

**Marginal cost per new set (the real tractability number).** Reusing the Portal mapping
unchanged:

| Set | distinct tags | already covered | **new entries to author** |
|-----|---------------|-----------------|---------------------------|
| POR | 77 | 75 | 2 |
| LEA (Alpha) | 19 | 9 | 10 |
| ARN (Arabian Nights) | 68 | 32 | **36** |

ARN is the stress case (idiosyncratic early-MTG mechanics). Its 36 marginal tags decompose as
9 `_Rule` (envelopes/keywords ≈ free), 6 `_Trigger`, 3 `_Cost`, 3 `_LayerEffect`, and only
**15 genuinely new `_Action` effects** — every one mapping to a capability Argentum already has
(`AddMana`, `GainControlOfPermanentUntil`, `CreateTokens`, `RegeneratePermanent`,
`PutCountersOnPermanent`, `RemoveCreatureFromCombat`, coin-flip variants…). Zero needed a new
engine feature — correct, since ARN is fully implemented. The vocabulary saturates: each set
adds a tapering handful, converging on a global mapping.

## Verdict

**Viable, behind the calibration loop — and the loop works.** A few hundred mapping lines
cover the classic-era corpus; the per-set marginal cost is a dozen-ish entries that shrink as
the vocabulary saturates. The registry-validation half already caught nothing wrong here only
because the guesses were right — but it *is* live: mis-map an effect to a non-existent
SerialName and it reports as MISSING, exactly as designed.

**Caveats the spike confirms (not blockers):**
- The mapping is genuine, ongoing work — you trade *writing regex templates* for *writing
  mapping entries + discovering Argentum's composition for each mtgish action* (e.g. realizing
  "destroy" is `MoveToZone`, not a leaf). Comparable effort, but the mapping is **declarative
  and greppable** where regex is not, and mtgish gives 32k cards of pre-parsed structure for free.
- Triggers/costs are accepted as `supported` without validation here — Phase 1 should scan the
  `Triggers.*` / `Costs.*` facades to close that, same pattern as the effect registry.
- This is *prediction*, never a loader. Ground truth stays: authored DSL + passing scenario test.

## The tool (`just coverage`)

The probe now has three lenses, wired into the justfile beside `card-status`. It reuses
`card-status`'s Scryfall cache to pull a set's full canonical list, so it classifies *missing*
cards too — the actual backlog-triage deliverable.

```bash
just coverage --set TMP             # implemented / FREE-to-implement / blocked + leaderboard
just coverage --set TMP --free      # also list the free-to-implement (missing+coverable) cards
just coverage --set TMP --blocked   # also list blocked cards + the blocking tags
just coverage --card "Shivan Dragon"  # one card: required capabilities + verdict
just coverage --calibrate POR       # trust check: implemented cards must classify coverable
```

**Worked example — Tempest (`just coverage --set TMP`):** 16 implemented, **114 free to
implement now** (no engine work), 205 blocked. The feature leaderboard ranks the blocking
capabilities by how many cards each unlocks (`AddMana ×22`, `EnchantPermanent ×19`,
`AtUpkeep trigger ×17`, …) — a sorted worklist: author the free pile today, map/triage the
leaderboard top-down, and each entry you close reclassifies a batch as free.

Two verdict classes in the leaderboard keep it honest:
- `GAP` — tag maps to a capability **absent from the registry**: a genuine engine-feature gap.
- `??` — tag has **no mapping entry**: triage (usually a mapping hole, occasionally a new
  mechanic). With a Portal-seeded mapping most of Tempest's "blocked" is `??`, i.e. mapping
  debt, not real engine gaps — exactly what you'd expect, and what the leaderboard tells you to
  pay down.

**Keyword auto-resolve.** Any unmapped tag whose PascalCase name converts to a real `Keyword`
enum member (`FirstStrike→FIRST_STRIKE`, `Shadow→SHADOW`) is treated as covered — registry-
validated, so it never over-claims (envelopes like `Activated` stay unmapped). This collapsed
the single biggest category of mapping holes (Tempest free 88→114) with zero hand-mapping.

## Not yet done (next steps if pursued)

1. **`KeywordAbility` registry** — auto-resolve only sees the base `Keyword` enum; cost-bearing
   keywords (`Buyback`, `Echo`, …) live in `KeywordAbility` and still show as `??`. Scan that
   facade too (same pattern). Likewise `Triggers.*` / `Costs.*` to validate the `supported` kind.
2. **mtgish name-matching** — classic sets join 100% by exact front-face name; modern sets with
   DFCs / split / adventure / reprints will need fuzzier joining (mtgish keys on oracle name).
3. **License** — mtgish is "MIT-ish, talk to a lawyer." Fine for an offline `scripts/` artifact
   consuming its committed JSON; clear before any vendoring.

## Generation fidelity — can we *auto-author*, not just *cover*? (`just coverage-fidelity`)

Coverage asks "does every capability exist?" Generation asks the harder thing: "could a script
emit the correct `cardDef { }`?" To measure it honestly without a Kotlin compiler, `fidelity.py`
diffs the bridge's output against each card's **compiled golden snapshot**
(`mtg-sets/src/test/resources/snapshots/cards/POR.json`) — both sides are Argentum `@SerialName`
tags, so it's apples-to-apples. Each card is tiered:

```
just coverage-fidelity --set POR    →  184 cards (vs compiled golden)
  AUTO      138   75.0%   recall=1, every target/filter recovered -> emit whole
  SCAFFOLD   30   16.3%   right effects, but structure (pipeline/gate/unrecognized filter) needs wiring
  MISS       16    8.7%   bridge omits a capability the card uses
  mean capability recall: 93.0%
```

**AUTO is real — the emitter reproduces hand-authored DSL byte-for-byte.** `--emit "Hand of Death"`
produces, line for line, the committed `HandOfDeath.kt`:

```kotlin
spell {
    val t = target("target", TargetCreature(filter = TargetFilter.Creature.notColor(Color.BLACK)))
    effect = MoveToZoneEffect(t, Zone.GRAVEYARD, byDestruction = true)
}
```

`Monstrous Growth` (`ModifyStatsEffect(powerModifier = 4, toughnessModifier = 4, target = t)`) and
`Anaconda` (`keywords(Keyword.SWAMPWALK)`) likewise match exactly. The recovery layer reads mtgish's
target/filter/amount operands the coverage map discards — `TargetPermanent{And(IsNonColor Black,
IsCardtype Creature)}` → `TargetCreature(filter = ...notColor(Color.BLACK))`.

**This run is post-improvement.** The first cut scored AUTO 57% / SCAFFOLD 21% / MISS 22%. Two
changes, both driven by the diff, moved it to **75 / 16 / 9**:

1. **Structure recovery from the mtgish side.** "Can *we* reconstruct the targets/filters?" replaces
   "is the card complex?" mtgish fully encodes the common target vocabulary (`IsCardtype ×72`,
   `And ×40`, player filters), so filtered-single-target cards (`destroy target nonblack creature`)
   are AUTO, not SCAFFOLD. +18 points of AUTO.
2. **Lowering-rule tags surfaced by calibration.** The MISS pile taught the bridge how Argentum
   *lowers* verbs: single-card move → `MoveToZone`, mass → `MoveCollection`, tap-all → `TapUntap`
   over a group, unless-gate → `PayOrSuffer`. Encoding those (general rules, not per-card fits)
   cut MISS from 41 to 16. This is the calibration loop working as designed: implemented cards
   reveal the correct lowering, you encode it once, it generalizes.

**Where it still breaks — and the diff names it precisely:**

- **MISS (9%)** — `MoveCollection ×7` (the genuinely ambiguous single-vs-many discard/look/mill
  lowering) plus a one-each tail of real per-card capabilities (`SkipCombatPhases`, `PlayAdditionalLands`,
  `MustBeBlocked`, `Taunt`…). These are honest residue, not low-hanging fruit.
- **SCAFFOLD (16%)** — a ranked worklist: `SearchLibrary ×7`, `DestroyEachPermanent ×6`, the
  `Unless/MayCost` gate cluster ×6, each-player iteration, multi-target counts. Each is a known
  structure a converter *could* render with more work — the report ranks them so you'd extend the
  emitter highest-value-first.
- **mtgish is approximate — even AUTO needs review.** `Lava Axe` ("deal 5 to target player") emits
  `AnyTarget()` because mtgish parsed it as the over-broad `TargetPlayerOrPermanent`. It still scores
  AUTO and still compiles — but it's *subtly wrong*. Proof that AUTO is a *ceiling*, not a guarantee:
  only a compile + scenario test catches this, which is why the gate stays human-reviewed.

**Verdict on auto-authoring.** Even after improvement, ~**75%** of 100%-*coverage* cards are
cleanly emittable, ~16% are review-drafts, ~9% the bridge gets wrong — and the AUTO slice skews
toward cards that are *already cheap to hand-author*. So the product is still a **reviewed draft
scaffolder**, never an unattended loader; the fidelity tier is the routing signal (AUTO → near-final
draft, SCAFFOLD → head-start with structure flagged, MISS → `add-card` by hand), and a compile +
scenario test remains the only real correctness gate.

## Cross-set generalization — the decisive finding (`just coverage-fidelity --all`)

The Portal numbers above were tuned *on Portal*. The honest question is whether the bridge
generalizes. Applying the **same bridge, unchanged**, to other fully-implemented sets that have a
golden snapshot:

```
  SET   matched   AUTO  SCAFFOLD   MISS  recall
  POR       184  75.0%     16.3%   8.7%   93.0%   <- the set the mapping was built on
  INV       312  48.1%      5.4%  46.5%   70.0%
  ONS       327  38.2%     13.5%  48.3%   65.3%
  KTK       242  46.7%      7.9%  45.5%   70.1%
  DOM       242  46.7%      7.9%  45.5%   69.2%
  LGN       145  53.1%      6.2%  40.7%   69.3%
  SCG       143  46.2%     11.2%  42.7%   68.2%
  ARN        58  44.8%     15.5%  39.7%   70.3%
```

**AUTO halves off the home set (75% → ~45%); the 75% was substantially a Portal artifact.** This is
the single most important result in the spike — measured, not asserted.

**But the gap is *tractable, converging debt*, not a wall.** The MISS pile on unseen sets is
overwhelmingly **effect verbs Argentum already has but Portal never exercised** — `AddMana`,
`CreateTokens`, `AddCounters`, `Regenerate`, `GainControl`, `ExilePermanent`, the `May`-gate family.
Each is one mapping line, validated against the registry. Adding **26 such universal lines lifted
recall ~10–14 points on every unseen set at once** (KTK 56→70%, INV 58→70%, ONS 55→65%) — the
convergence property: the bridge is shared infrastructure, so one fix helps all sets, and the
vocabulary saturates as you go. Reaching Portal's 93%/75% on a new set means paying down its share
of the long tail (~160 rarer verbs remain across these six sets).

**What this means for the verdict.** Auto-authoring is *even less* of a free lunch than the Portal
slice implied:
- A first-time set lands around **45% AUTO / 45% MISS** with today's bridge.
- Closing the gap is real, ongoing mapping work — but it *accumulates* (every verb mapped is mapped
  forever, for all sets) rather than resetting per set. The bridge is a **build-and-converge asset**,
  not build-once and not throwaway.
- It still only produces *review drafts*: even at Portal's 93% recall, `Lava Axe` emits a wrong
  target from a clean parse. The scenario test stays the gate.

So the recommendation is unchanged but now quantified: a **reviewed draft scaffolder** is worth it
*if* you commit to growing the mapping across the corpus (the `--all` recall column is the KPI to
watch); it is not a push-button "implement every coverable card" tool.

## Auto-gen gap detector + draft generator (`autogen.py`)

The end-to-end payoff: turn the bridge on a set's *unimplemented* cards and (1) predict which it
could draft today, (2) emit those drafts.

```
just coverage-gaps --set TMP            # of 319 unimplemented Tempest cards:
  AUTOGEN     36   bridge could draft a whole card now
  SCAFFOLD   113   covered, but structure needs hand-wiring
  BLOCKED    170   capability gap (needs mapping or engine work)
  BLOCKED leaderboard: EnchantPermanent ×19 (auras), AtUpkeep-trigger ×17, ...

just coverage-generate --set TMP        # writes 36 draft .kt -> generated/tmp/  (staging, gitignored)
```

A generated draft (`generated/tmp/LightningBlast.kt`), complete with package + exact imports:

```kotlin
// === GENERATED DRAFT — do NOT merge as-is. ===
val LightningBlast = card("Lightning Blast") {
    manaCost = "{3}{R}"
    typeLine = "Instant"
    spell {
        val t = target("target", AnyTarget())
        effect = DealDamageEffect(4, t)
    }
    metadata { rarity = Rarity.COMMON /* TODO verify rarity + add Scryfall fields */ }
}
```

**Prediction without a snapshot is harder, and the tool is honest about it.** Missing cards have no
golden tree, so AUTOGEN = `coverable` (probe) ∧ `structure recoverable` (fidelity) ∧ `emitter renders
the whole card`. The completeness gate is strict: when target recovery can't faithfully render a
filter (e.g. Disenchant's *artifact-or-enchantment* target), `target_dsl` returns `None` and the card
drops to SCAFFOLD rather than emitting a confidently-wrong `TargetPermanent()`. That one fix moved 5
Tempest cards out of AUTOGEN — silent-wrong avoided by construction.

**Two deliberate safety choices**, consistent with the whole spike:
- Drafts go to a **staging dir, never the live set.** They're predictions from approximate IR; the
  gate stays compile + scenario test + human review, then drop into `cards/` (which auto-registers
  via classpath scan). This is a scaffolder, not a card loader.
- The generator emits **only what it can render whole** (36/319 for Tempest: 19 keyword creatures,
  6 damage spells, 11 vanilla) — exactly the cheap-to-author slice. It is a blank-page eliminator
  for the easy pile, freeing human time for the SCAFFOLD/BLOCKED cards where it actually goes.

## Files

- `probe.py` — registry scan + extractor + classifier + the three coverage lenses.
- `fidelity.py` — generation-fidelity scorer (vs golden snapshot), `--all` cross-set, DSL emitter.
- `autogen.py` — auto-gen gap detector (`--gaps`) + draft generator (`--write`) for missing cards.
- `generated/<set>/` — staging output of draft `.kt` files (gitignored; review before use).
- `mapping.json` — the hand-authored mtgish→Argentum bridge (the measured maintenance cost);
  `composed` entries carry `tags` naming the concrete primitives they'd compile to (for fidelity).
- `data/mtgish.lines.json` — 32k-card mtgish IR (gitignored; auto-downloaded on first run).
- `registry.effects.txt` — generated dump of effect SerialNames (gitignored).
