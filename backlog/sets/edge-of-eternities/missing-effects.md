# Edge of Eternities (EOE) - Missing Effects & Implementation Plan

This document lists engine features required to implement EOE cards that do not yet exist in the
Argentum Engine, ordered by priority (number of cards unblocked).

---

## 5. Poison Counters

**Cards affected:** 1 (Virulent Silencer), but also Thrumming Hivepool (Slivers) could enable
future poison cards.

**Rules:** "That player gets two poison counters. A player with ten or more poison counters loses
the game."

**Implementation plan:**

1. **rules-engine:** Add `PoisonCounterComponent` to player entities tracking poison count.
2. **rules-engine:** Add `AddPoisonCountersEffect` and corresponding executor.
3. **rules-engine:** Add state-based action: "A player with 10+ poison counters loses the game."
4. **game-server:** Expose poison counter count in `MaskedGameState` / client DTOs.
5. **web-client:** Display poison counter count in player info panel.

**Dependencies:** None.

---

## 7. Token Doubling (Replacement Effect)

**Cards affected:** 1 (Exalted Sunborn).

**Rules:** "If one or more tokens would be created under your control, twice that many of those
tokens are created instead."

**Implementation plan:**

1. **rules-engine:** Add a replacement effect that intercepts `CreateTokenEffect` execution.
   When the controller has a token-doubling continuous effect, double the token count.
2. **mtg-sdk:** Add a static ability type: `DoubleTokenCreation`.

**Dependencies:** None, but replacement effect infrastructure for token creation may need work.

---

## 8. Devour (Land Variant)

**Cards affected:** 1 (Famished Worldsire).

**Rules:** "Devour land 3 — As this enters, you may sacrifice any number of lands. It enters with
three times that many +1/+1 counters."

**Implementation plan:**

1. **mtg-sdk:** Extend existing `Devour` keyword (if creature-only) to support a filter parameter
   (lands vs creatures).
2. **rules-engine:** Modify devour handler to accept a `GameObjectFilter` for what can be
   sacrificed, rather than hardcoding creatures.

**Dependencies:** Check if standard Devour (creatures) is already implemented.

---

## 12. "Mana Spent to Cast" Tracking

**Cards affected:** 2+ (Dyadrine enters with counters equal to mana spent; Astelli Reclaimer uses
X = mana spent).

**Rules:** Track the total mana spent to cast a spell (not just X value).

**Implementation plan:**

1. **rules-engine:** Store `manaSpentToCast` on spells/permanents when cast. This may already be
   partially tracked for X spells.
2. **mtg-sdk:** Add `DynamicAmount.ManaSpentToCast` for use in ETB effects.

**Dependencies:** None.

---

## Implementation Order (Recommended)

| Phase | Feature                    | Cards Unblocked | Effort |
|-------|----------------------------|-----------------|--------|
| 7     | Mana spent tracking        | ~2              | Small  |
| 8     | Poison counters            | ~1              | Medium |
| 9     | Stun counters              | ~1              | Small  |
| 10    | Token doubling             | ~1              | Medium |
| 11    | Devour (land variant)      | ~1              | Small  |
| 12    | Mindslaver                 | ~1              | Huge   |

After phases 1-5, the majority of EOE cards become implementable using existing engine effects
(ETB triggers, +1/+1 counters, destroy, exile, draw, mill, surveil, kicker, convoke, equip,
landfall, combat damage triggers, etc.).
