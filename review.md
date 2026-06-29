# Review: Add Yue, the Moon Spirit (TLA)

## Verdict
The SDK shape is **right** — the card composes existing pipeline primitives with zero new
types, exactly the SDK-elegance goal. The one **blocking correctness bug** was that the
implementation gave Yue **Flash**, which the real card does not have. Verified against both
the local `bolav/tla_full.json` and the live Scryfall API (`set=tla`): keywords are only
`Flying, Vigilance, Waterbend`. *(Fixed — see Resolution below.)*

## What's good (kept)
- **Pure primitive composition, no new SDK types.** `GatherCardsEffect →
  SelectFromCollectionEffect(ChooseUpTo 1) → Effects.CastFromCollectionWithoutPayingCost`
  mirrors `KellanTheKid` exactly. Nothing card-specific leaked into the engine.
- **Reuses the existing waterbend carrier** (`hasWaterbend = true`) rather than inventing a
  cost type.
- **Filter models the oracle precisely.** `GameObjectFilter.Nonland.notCreature()` →
  `IsNonland ∧ ¬IsCreature` correctly captures "noncreature spell" (lands aren't spells;
  artifact-creatures are still creature spells and excluded).
- **Tests are well-designed.** The exact-`{5}`-blue-mana trick makes the free cast observable
  (the `{2}{U}` enchantment can only resolve onto the battlefield if it was genuinely cast for
  free), and the second test covers the decline path (`emptyList()` selection → stays in hand).
- Mana cost, subtype (`Spirit Ally`), color identity, P/T, rarity, collector number, artist,
  flavor, and image all match Scryfall.
- Registration is correct — TLA uses `CardDiscovery.findIn(CARDS_PACKAGE)` reflection, so
  dropping the file in `cards/` is sufficient; no manual set-file edit needed.

## Issues

### Blocking
- **`YueTheMoonSpirit.kt:43` — `Keyword.FLASH`.** The real card has no Flash; this materially
  changes it (lets you cast Yue at instant speed). Remove from `keywords(...)`.
- **`YueTheMoonSpirit.kt:38` — `"Flash\n"` oracle prefix.** Scryfall oracle starts at
  `"Flying, vigilance\n…"`. Also remove the doc-comment line at `:22`.
- **Re-bless the snapshot** (`mtg-sets/.../snapshots/cards/TLA.json`) after the fix — it pinned
  the wrong `"FLASH"` keyword and oracle text.

### Minor
- **Backlog not updated.** `backlog/sets/avatar-the-last-airbender/cards.md` showed
  `- [ ] Yue, the Moon Spirit` and the header count still read `231 / 286`.
- The two tests exercise the pipeline but not the keyword set, so they would *not* have caught
  the Flash bug — the snapshot is the only net there.

## Test result
Both scenario tests pass (`BUILD SUCCESSFUL`):
- `Waterbend ability casts a noncreature spell from hand for free` — PASSED
- `declining the optional cast leaves the spell in hand` — PASSED

As predicted, the green tests don't catch the Flash bug (they don't assert on keywords).

## Resolution (applied)
1. Removed `Keyword.FLASH` (`:43`), the `"Flash\n"` oracle prefix (`:38`), and the comment
   line (`:22`).
2. Re-blessed `TLA.json` (FLASH keyword + oracle text corrected).
3. Checked Yue off in the backlog and bumped the count to `232 / 286` (and `55 → 54` remaining).

No engine/SDK changes needed — the composition is correct and reusable as-is. The branch
already had `origin/main` merged (was up to date — no merge commit). Review ran in place; no
worktree created.
