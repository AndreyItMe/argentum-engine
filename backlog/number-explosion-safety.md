# Number / Token Explosion Safety — Hardening Plan

Some cards can drive *exponential or unbounded* growth in counts: doubling tokens (Doubling
Season + a token maker), doubling counters, "twice the number of X" dynamic amounts, and trigger
loops. Today the engine can be made to **hang (OOM) or silently corrupt state (Int overflow)**
before any rule stops it. This doc captures the failure modes, what other engines / the MTG rules
do, and a tiered plan to fix it.

**Status:** Option A implemented (see [`GameLimits`](../rules-engine/src/main/kotlin/com/wingedsheep/engine/core/GameLimits.kt),
`GameLimitsTest`, `NumberExplosionSafetyTest`, and architecture-principles §2.8). Options B and C
remain open. Items ordered by impact ÷ effort.

## Failure modes (current state)

Two genuinely different problems, plus one already partly handled.

### 1. Memory explosion — tokens are materialized one entity at a time (CRASH)

Each token is a full ECS entity. Creation is a linear loop, doubling is multiplicative, and there
is **no ceiling** before allocation.

- `rules-engine/.../handlers/effects/token/CreateTokenExecutor.kt:137` —
  `repeat(count) { val (tokenId, stateWithId) = newState.newEntity() ... }`, one entity per token,
  full component set each. Count is only checked for `<= 0` (line ~106).
- `rules-engine/.../handlers/effects/token/TokenCreationReplacementHelper.kt:98` —
  `repeat(doublings) { count *= 2 }` with no upper bound (Doubling Season stack → 2^N).

Effect: a few doublers + one token maker asks the engine to allocate 2^N entities sequentially →
multi-second freeze, then JVM OOM. This is a hard crash, not a wrong number.

### 2. Arithmetic overflow — counters / dynamic amounts are bare `Int` (SILENT CORRUPTION)

- `rules-engine/.../state/components/battlefield/BattlefieldComponents.kt:278` —
  `CountersComponent(counters: Map<CounterType, Int>)`; `withAdded` does `current + amount` with no
  overflow check.
- `rules-engine/.../handlers/effects/permanent/counters/DoubleCountersExecutor.kt:43` — reads the
  count as `Int` and doubles it.
- `rules-engine/.../handlers/DynamicAmountEvaluator.kt:79` — `evaluate(...)` returns **`Int`**;
  `Add`/`Subtract`/`Multiply` (lines ~165–171) use bare `+ - *`.

Effect: doubling a billion-counter permanent ~once silently **wraps to a negative value** — game
state corrupts instead of crashing, which is arguably worse (undetectable, non-deterministic
downstream).

### 3. Trigger / SBA loops — already partly handled

- `rules-engine/.../mechanics/StateBasedActionChecker.kt:123` — `MAX_SBA_ITERATIONS = 1000`; if
  SBAs don't stabilize the game ends as a draw (CR 104.4 family). This catches infinite *SBA/trigger*
  cycles only — **not** linear memory growth or arithmetic overflow above.

## Prior art — how this is handled elsewhere

The decisive insight: **no engine should ever materialize the huge number.** In the MTG rules
themselves, gigantic quantities are a *shortcut*, not a literal action:

- **Mandatory loop → the game is a draw** (Magic Tournament Rules 4.4; CR 720 loop rules).
- **Optional loop → the player names a finite number** of iterations and the loop is resolved in one
  step. The actions must be identical and deterministic each iteration.
- Forge / XMage are mostly a cautionary tale here: both have shipped bugs where AI-driven token
  loops hang the client precisely because they materialize tokens rather than reason about the loop.

Standard engineering toolkit:

- **Saturating / checked arithmetic** — clamp at a defined sane max (or `Math.multiplyExact` to fail
  loudly) instead of silently wrapping.
- **Token aggregation ("stacked tokens")** — represent N identical tokens as *one* entity with a
  `quantity`, splitting only when one diverges (gains a counter, aura, damage, taps). Makes a
  legitimately huge board cheap.
- **Resolution depth / iteration guards** — the same pattern as the existing SBA cap, extended to
  effect resolution and entity creation.
- **Reject BigInteger.** It removes overflow but does nothing for the memory blow-up (you still can't
  allocate 2^60 entities) and taxes every arithmetic op forever. Wrong tool.

Reference: [Loop (MTG Wiki)](https://mtg.wiki/page/Loop) ·
[MTR 4.4 Loops](https://blogs.magicjudges.org/rules/mtr4-4/) ·
[Shortcut (MTG Wiki)](https://mtg.fandom.com/wiki/Shortcut).

## Plan

### Option A — Safety net (recommended first; small)

Goal: **guarantee no crash and no silent overflow.** Lossy at the absurd extreme, but no real game
distinguishes "10k tokens" from "a trillion tokens" — both just win.

1. **Saturating arithmetic.** Introduce a single shared helper (e.g. `SafeMath` /
   `Long`-internal-then-clamp) and route counter math and `DynamicAmountEvaluator`'s
   `Add`/`Subtract`/`Multiply` through it. Define `MAX_SANE_QUANTITY` (e.g. `1_000_000_000`, well
   below `Int.MAX_VALUE`) and clamp to `[..MAX_SANE_QUANTITY]`. Counters: clamp in
   `CountersComponent.withAdded` and in `DoubleCountersExecutor`.
2. **Entity-creation budget.** Cap any single token-creation effect (post-doubling) at a configured
   ceiling (e.g. 10k entities). When the budget is hit, create up to the cap, **log it and surface a
   game event** so it's visible (never silent). Apply the cap *before* the `repeat` loop in
   `CreateTokenExecutor` / `TokenCreationReplacementHelper` so we never enter a 2^N allocation.
3. **Resolution-depth guard.** Mirror the SBA cap for nested effect/continuation resolution depth so
   a runaway non-SBA chain ends in a defined way (draw / abort the action) instead of stack
   overflow.

Touch points: `CountersComponent`, `DoubleCountersExecutor`, `DynamicAmountEvaluator`,
`CreateTokenExecutor`, `TokenCreationReplacementHelper`, plus a new shared math/limits constant.
This is an `add-feature` change (new SDK/engine guards + a clamp event). Update
`docs/card-sdk-language-reference.md` if any SDK surface (e.g. a saturating dynamic amount) changes.

Tests: doubling a near-cap counter clamps (no negative); stacked Doubling Seasons cap token output
and emit the cap event; a synthetic deep resolution chain terminates cleanly.

### Option B — Token quantity aggregation (medium-large; only if huge boards must *function*)

If displaying/playing genuinely massive boards is a product goal (not just "don't crash"), add a
`quantity` to the token/permanent representation so identical tokens collapse into one entity and
**split on divergence** (counter, aura, equipment, damage, tap state, summoning sickness boundary).

This is squarely `add-feature` and cross-layer: projection, combat (declaring N attackers from a
stack), SBAs, server DTO, the client board renderer (already renders "stacks" visually — see
battlefield slot sizing notes), and serialization all need to understand quantity. Significant; do
only after Option A proves insufficient.

### Option C — Full loop-shortcut detection (research-grade; likely out of scope)

Detect a repeating game-state delta and resolve the whole loop as a draw (mandatory) or a
player-named iteration count (optional), per CR 720. Correct and powerful but hard to get right;
probably not worth it for the current player base. Listed for completeness.

## Recommendation

Do **Option A** now — cheap, removes the crash and the silent-corruption risk entirely. Reach for
**B** only if massive boards must actually play out, and treat **C** as out of scope unless a
tournament-correctness need appears.
