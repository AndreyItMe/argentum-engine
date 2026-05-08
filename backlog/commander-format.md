# Commander Format

Add Commander format support to the Argentum Engine. Commander is a 100-card singleton format with a designated
legendary creature commander, 40 starting life, and a 21-commander-damage loss condition. Phase 1 targets **1v1
Commander** only — multiplayer (3-4 player free-for-all) is its own project.

## Engine survey (what already exists)

- **Multiplayer:** `GameState.initial()` (`rules-engine/.../state/GameState.kt:458`) and `GameInitializer`
  (`:96`) already require `playerIds.size >= 2` — no hard 2-player cap.
- **Command zone:** `Zone.COMMAND` already exists in `mtg-sdk/.../core/Zone.kt:7-24` alongside the other zones.
- **Configurable starting life:** `PlayerConfig(startingLife: Int = 20)` in `GameInitializer.kt:27`.
- **Cost modification:** `CostCalculator.calculateEffectiveCost()` (`rules-engine/.../mechanics/mana/CostCalculator.kt:58-80`)
  evaluates `SpellCostReduction` static abilities — symmetric cost-*increase* path is needed for tax.
- **Zone-change replacement precedent:** `ExileOnLeaveBattlefieldComponent`
  (`rules-engine/.../components/battlefield/BattlefieldComponents.kt:90-94`) shows the unconditional-redirect pattern.
- **Combat damage tracking:** `HasDealtCombatDamageToPlayerComponent` (`:397-403`) is a lifetime flag,
  not (source, target) pairs — needs a new tracker.
- **Color identity:** `CardDefinition.colorIdentity` exists at `mtg-sdk/.../model/CardDefinition.kt:94-100` but is
  computed from mana cost only (does not yet scan rules text for hybrid/phyrexian/color-word symbols).
- **Format concept:** does not exist yet. No `Format` enum, no game-mode config.

## Design principle

**Commander as data, not branches.** Introduce a `Format` config object that the engine reads, rather than scattering
`if (isCommander)` across handlers. The cost is upfront discipline (one config object grows tentacles for life total,
hand size, mulligan rules, win conditions). The payoff is that Brawl, Oathbreaker, Pauper Commander, and 1v1 Commander
become trivial config variants, not new code paths.

---

## Phase 1 — 1v1 Commander

### Suggested implementation order

1. **1.1** — `Format` config object (foundation; no behavior change)
2. **1.2** — `CommanderComponent` + setup (commanders exist in the right zone)
3. **1.7** — Deck validator (can be done in parallel by another contributor; gates UI work)
4. **1.5** — Zone-change replacement, flag-gated always-divert (do this *before* damage tests so commanders don't vanish to graveyard during combat scenarios)
5. **1.3** — Cast from command zone
6. **1.4** — Commander tax (depends on 1.3)
7. **1.6** — Commander damage tracking + lethal SBA

### Definition of done (Phase 1 ship gate)

- [ ] Two players can start a Commander game (40 life, commander in `Zone.COMMAND`)
- [ ] Commander can be cast from command zone; tax escalates correctly across recasts
- [ ] Commander destroyed/exiled/bounced/milled returns to command zone (always-divert flag on)
- [ ] 21 cumulative combat damage from a single commander wins the game (`LossReason.COMMANDER_DAMAGE`)
- [ ] Deck validator rejects off-color, non-singleton, wrong-size, or non-legendary-creature-commander decks
- [ ] Web client renders the command zone, shows per-commander damage tallies, and allows casting from command
- [ ] One full e2e Playwright scenario: deck-build → game start → cast commander → combat damage → opponent loses by commander damage

### 1.1 `Format` config object

- New `mtg-sdk/.../model/Format.kt`:
  ```kotlin
  sealed interface Format {
      object Standard : Format
      data class Commander(
          val commanderDamageThreshold: Int = 21,
          val deckSize: Int = 100,
          val startingLife: Int = 40,
          val startingHandSize: Int = 7,
          val alwaysDivertToCommand: Boolean = true, // see 1.5
      ) : Format
  }
  ```
- `GameInitializer.GameConfig` (`:40`) — add `format: Format = Format.Standard`.
- `GameInitializer.PlayerConfig` (`:24`) — add `commanderCardName: String? = null`.
- `GameState` (`:105`) — add `val format: Format = Format.Standard`. Default keeps existing tests untouched.

**Decision A (locked): `CommanderRegistryComponent` on the player entity** holding `commanderIds: List<EntityId>`.
Rejected alternative: a `Map<EntityId, List<EntityId>>` field on `GameState`. The component approach stays
ECS-shaped, falls out of player-entity queries, serializes naturally, and Partner / Background later just append to
the list without a schema change.

**Tests:** `FormatSerializationTest`, `GameInitializerCommanderTest` (asserts life=40, format set, commanders in
`Zone.COMMAND`).

### 1.2 `CommanderComponent` + initial command-zone setup

- New `rules-engine/.../components/identity/CommanderComponent.kt`:
  ```kotlin
  data class CommanderComponent(val ownerId: EntityId, val castsFromCommandZone: Int = 0) : Component
  ```
- Sibling `CommanderRegistryComponent(val commanderIds: List<EntityId>)` (player-attached).
- Register both in `rules-engine/.../core/Serialization.kt:304` near `ExileOnLeaveBattlefieldComponent`.
- `GameInitializer.initializeGame()` step 3 (line 136) — when `config.format is Format.Commander`, find the deck card
  by `commanderCardName`, attach `CommanderComponent`, route to `ZoneKey(playerId, Zone.COMMAND)` instead of library,
  attach `CommanderRegistryComponent` to the player.

Phase 1 does **not** modify the legend rule. Commanders are *additionally* legendary; the legend rule remains
battlefield-only.

**Tests:** `CommanderSetupTest` — both commanders in `Zone.COMMAND` at game start, life totals = 40.

### 1.3 Casting from the command zone

- `rules-engine/.../legalactions/enumerators/CastSpellEnumerator.kt` — mirror the HAND (line 128) and GRAVEYARD
  (line 1428) enumeration paths for `COMMAND`; gate on `CommanderComponent` ownership.
- `CastSpellHandler.execute()` and `validate()` — accept `castFromZone == Zone.COMMAND` for cards with
  `CommanderComponent`. Verify `CastZoneResolver` (private to the handler package) also permits it — likely a hidden
  checkpoint.

**Tests:** game-server scenario test — command-zone commander appears in legal actions and successfully resolves to
the battlefield.

### 1.4 Commander tax in `CostCalculator`

- `CostCalculator.kt:58` — extend `calculateEffectiveCost()` with `fromZone: Zone? = null` (default preserves all
  existing call sites). After `calculateFilterCostIncrease` (line 90), add a commander-tax helper that reads the
  card's `CommanderComponent` and applies `+2 * castsFromCommandZone` generic mana when `fromZone == COMMAND`.
- `CastSpellHandler.kt:310, :1125` — pass `fromZone` through (already obtained via `zoneResolver`).
- `mechanics/stack/StackResolver.kt` — increment `castsFromCommandZone` **on cast commit** (after payment, before
  push to stack), not on resolution.

**Decision B (locked): increment on cast-commit** (after payment, before push to stack), not on resolution.
Per CR 903.8, the additional cost is paid *to cast*; countered commanders still owe the higher tax next time.
Surfaced here only because the engine has no precedent for "cost counter that increments on cast" — implement as
its own helper next to `castsFromCommandZone`.

**Tests:** unit test on `CostCalculator` with `CommanderComponent(castsFromCommandZone = 2)` produces effective
generic cost +4 only when `fromZone == COMMAND`. Scenario: cast commander, kill it, recast — second cast costs more.

### 1.5 Command-zone replacement on zone change ⚠ biggest decision

When a card with `CommanderComponent` would move to graveyard, exile, hand, or library (from any zone), the owner
*may* divert it to the command zone instead.

- `rules-engine/.../handlers/effects/ZoneMovementUtils.kt:363` — `checkZoneChangeRedirect()`. After the
  `ExileOnLeaveBattlefieldComponent` self-check at line 371, add a parallel branch for `CommanderComponent`.

**Decision C (locked): ship Phase 1 with `alwaysDivertToCommand = true`; defer player-choice to Phase 1.5.**
Rationale: the choice between "divert to command zone" and "stay in graveyard" only matters for graveyard-recursion
archetypes (Muldrotha, Meren, Karador). For everything else, divert is correct 100% of the time. The plumbing cost
of paused `ZoneTransitionResult` is large — `moveToZone` is called from combat dies, bounce, exile, scry-back,
mill, tucker, and stack-resolution paths, each of which would need to handle a paused outcome. Ship a playable 1v1
game first, layer the choice on once the surface is stable. The flag becomes a behavior toggle, not technical debt.

| | Always-divert (Phase 1) | Paused decision (Phase 1.5) |
|---|---|---|
| Fidelity | Right ~95% of the time; wrong for graveyard-recursion archetypes | Matches MTG rules |
| Plumbing cost | One-line `if` in `checkZoneChangeRedirect` | `CommanderZoneChoiceContinuation` + paused `ZoneTransitionResult` + audit every caller |
| New types | None | Continuation + result extension |

**Tests:** scenarios for destroy / bounce / exile / mill / tucker — each routes the commander to `Zone.COMMAND` (or
to the chosen destination, in Phase 1.5).

### 1.6 Commander damage tracking + lethal SBA

- `GameState.kt:105` — add `val commanderDamage: Map<Pair<EntityId, EntityId>, Int> = emptyMap()` keyed by
  `(commanderEntityId, defendingPlayerId)`. Helper `recordCommanderDamage(...)` near `addDelayedTrigger` (line 437).
- `rules-engine/.../mechanics/combat/CombatDamageManager.kt` — at the two `DamageDealtEvent(..., true,
  targetIsPlayer = true)` emission sites (~lines 561, 698), accumulate when source has `CommanderComponent`. Use
  `effectiveAmount` (post-prevention), not `originalAmount`. Token-copy commanders must NOT contribute (CR 903.10a
  — token copies aren't the commander) — gate on `!container.has<TokenComponent>()`.
- New SBA `rules-engine/.../mechanics/sba/player/CommanderDamageLossCheck.kt` modeled on `PlayerLifeLossCheck.kt`. Add
  `LossReason.COMMANDER_DAMAGE` at `PlayerComponents.kt:237`. Register in `PlayerSbaModule`. Add `SbaOrder.COMMANDER_DAMAGE_LOSS`
  ordinal next to `PLAYER_LIFE_LOSS`. SBA reads threshold from `state.format`; no-ops if not Commander format.

**Tests:** unit — state with `commanderDamage = (cmdr, victim) -> 21` produces `PlayerLostEvent(victim,
COMMANDER_DAMAGE)`. Scenario — 11+10 unblocked commander damage at 39 life makes the opponent lose by commander
damage, not life loss.

### 1.7 Commander deck-construction validation

Lives at the deck-construction layer, not the runtime engine. The engine stays format-agnostic about deck legality.

- `game-server/.../deck/DeckValidator.kt` — add `validateCommander(deckList, commanderName)` reusing existing
  `countsByBaseName` / `errors` infrastructure with `MIN_DECK_SIZE = 100`, `MAX_COPIES_NON_BASIC = 1`, basic-land
  exemption, and a color-identity subset check (`card.colorIdentity ⊆ commander.colorIdentity`).
- New error codes: `WRONG_COMMANDER_DECK_SIZE`, `NOT_SINGLETON`, `COLOR_IDENTITY_VIOLATION`, `INVALID_COMMANDER`
  (commander must be a legendary creature, with a future hook for "can be your commander" oracle text).

**Tests:** `DeckValidatorCommanderTest` — 99 cards fail, dup non-basic fails, dup basics pass, off-color non-basic
fails, on-color all-singleton 100 passes.

### 1.8 Web client (minimum viable)

The engine work is moot without a UI. Phase 1 frontend scope:

- **Command zone widget** — separate area near the player's hand, shows commander card face-up. Click to cast (same
  flow as casting from hand).
- **Commander damage tally** — on the opponent life-total widget, per-commander mini-counter (`5/21` style). Reads
  from `commanderDamage` map in masked client state. Threshold flashes when ≥ 18 (warning) and turns red at 21.
- **Tax indicator** — small `+4` badge on the command-zone card showing the current tax cost.
- **Deck builder** — Commander format toggle, commander picker (legendary creatures only for Phase 1), color-identity
  filtered card pool.

Phase 1.5 adds the divert-yes-no decision modal (reuses existing decision modal infrastructure).

---

## Phase 1.5 — Proper zone-change replacement (player choice)

Replace `alwaysDivertToCommand` heuristic from 1.5 with a real `YesNoDecision` flow. Add
`CommanderZoneChoiceContinuation`, extend `ZoneTransitionResult` with `pendingDecision`, audit the callers of
`ZoneTransitionService.moveToZone`. Test fixtures for graveyard-recursion strategies (commander stays in graveyard
for Muldrotha-shaped plays).

## Phase 2 — Color identity polish

- Deepen `CardDefinition.colorIdentity` to scan oracle text for mana symbols (hybrid, phyrexian, mono symbols in
  activated/triggered abilities) and color indicators / color words. Crackling Doom currently passes Phase-1
  validation when it shouldn't.
- Hook for "can be your commander" / "partner" / "friends forever" oracle-text detection so non-legendary commanders
  (planeswalkers in commander variants) can be supported by data, not by special-casing.

## Phase 3 — Multiplayer (3-4 player free-for-all)

This is its own project — large in scope and surface area.

- Audit "the opponent" assumptions across the engine: every `players.first { it != active }` and every
  auto-target-the-opponent shortcut must become "choose an opponent." Probably a few dozen call sites.
- Range of influence (multiplayer rule).
- Politics-aware UI/UX (target-an-opponent dialogs, attacker-declares-defender step).
- AI politics (group dynamics, kingmaker avoidance) — separate research project.
- Server lobby support for >2 players (likely already there for tournaments, but verify the play-flow paths).

## Phase 4 — Partner / Background / Companion / commander variants

- Plural commanders per player (already supported by `CommanderRegistryComponent.commanderIds: List<EntityId>` from
  Phase 1).
- Partner / Friends Forever / Partner With keyword detection at deck-construction time.
- Background pairing rules (`commanderName` becomes `commanderNames: List<String>` in `PlayerConfig`).
- Companion sideboard slot (Companion is technically a separate mechanic, but lives naturally near commander zone
  setup).
- Commander variants: Brawl (60 cards, standard-legal), Oathbreaker (planeswalker + signature spell, 60 cards,
  30 starting life), Pauper Commander (uncommon commander, common deck).

---

## Risks and unknowns

- **Phase 1.5 plumbing is the largest unknown.** Existing replacement effects (`ExileOnLeaveBattlefieldComponent`,
  `RedirectZoneChange`) are unconditional; `ZoneChangeRedirectResult` has no "paused" notion. The flag-gated
  always-divert in Phase 1 sidesteps this — but Phase 1.5 will need to thread a paused result through every caller of
  `ZoneTransitionService.moveToZone`.
- **`CastZoneResolver`** (private to `CastSpellHandler` package) likely gates which zones a card can be cast from;
  verify before promising 1.3 is a small change.
- **Token copies of commanders** must not contribute to commander damage and aren't themselves the commander
  (CR 903.10a). Verify `CombatDamageManager` source IDs reference the original commander permanent on the
  battlefield, not a token clone.
- **Mulligan rules** differ in Commander (free first mulligan, partial mulligans). Phase 1 leaves vanilla mulligans
  in place; flag as known gap.
- **State projector** — confirm battlefield-only continuous effects (e.g., a Goblin Lord) don't accidentally apply to
  a Goblin commander sitting in `Zone.COMMAND`. Quick smoke test before merge.
- **Serialization compatibility** — adding `format` and `commanderDamage` fields to `GameState` may break persisted
  states. Check `game-server/persistence/` (if it exists) for migration needs.
- **AI advisor coverage** — commander tax, commander damage, and the command-zone choice all need advisor logic for
  the AI to play Commander competently. Out of scope for Phase 1 (engine correctness first), but a real follow-up.
- **E2E tests** — the web client needs UI for the command zone (visible, drag-to-cast), commander-damage tracker
  (per-commander tally on the opponent's life-total widget), and the divert-yes-no decision modal (Phase 1.5).
  Significant frontend work that's not in this engine plan.
