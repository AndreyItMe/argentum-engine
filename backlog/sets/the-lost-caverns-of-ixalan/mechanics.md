# The Lost Caverns of Ixalan (LCI) — Mechanics

Counts are over the 286 booster cards (excluding basic lands), by front-face + back-face
oracle text. "Unimpl" = not yet implemented as of this document (111 cards done). The Discover
row is freshly recounted; the other per-mechanic Unimpl counts predate the latest card batch
and are approximate.

Engine-support column reflects whether the SDK/rules-engine already models the mechanic
(so cards using *only* supported mechanics need **no backend change** — pure `add-card` work).

## Set mechanics

| Mechanic | Total | Unimpl | Engine support | Notes |
|----------|------:|-------:|----------------|-------|
| **Explore** | 25 | 24 | ✅ `ExploreEffect` | Creature reveals top card; land → hand, nonland → +1/+1 counter + may mill. |
| **Discover N** | 19 | 0 | ✅ `DiscoverEffect` | Exile from top until nonland MV ≤ N; cast free or to hand; rest bottomed random (CR 701.57). Fixed or dynamic threshold + optional discovered-card follow-up. All 19 discover cards implemented. |
| **Craft** | 19 | 19 | ✅ `Craft*` + DFC transform | Activate from battlefield (exile materials), transform to back face. All Craft cards are DFCs. |
| **Descend (4 / 8 / fathomless)** | 28 | 27 | ✅ `descended` tracking | Cares about permanent cards in your graveyard; "descend N" / "fathomless descent". |
| **Map token** | 7 | 6 | ✅ `MapToken` | Artifact token; `{1}, {T}, Sacrifice: target creature you control explores.` |
| **Transform / DFC** | 35 | 35 | ✅ `TransformEffects` | Includes Craft backs, MDFC lands (front spell // back land), god // land flips. |
| **Treasure** | 16 | 12 | ✅ Treasure token | Standard artifact token, sac for one mana of any color. |
| **Vehicle / Crew** | 6 | 5 | ✅ Crew | Standard vehicles. |
| **Cave (land subtype)** | 12 | 10 | ✅ (subtype only) | New land subtype; some cards care "you control a Cave". |
| **Finality counter** | 5 | 4 | ✅ finality counters | -1/-1-ish removal-on-death counter; used by a few cards. |

## Evergreen / returning keywords present

Flying (25), Trample (14), Vigilance (10), Ward (9), Menace (6), Lifelink (5), Deathtouch (4),
Haste (4), Reach (4), First strike (2), Double strike (1), Flash (12), Defender (1),
Indestructible (1), Hexproof (1). Plus Equip (12), Cycling / typecycling / landcycling (13),
Mill (12), Scry (11), Surveil (1), Enchant (7), Fight (1). **All engine-supported.**

## Backend-change assessment

Every headline LCI mechanic is now modelled by the engine (111 LCI cards, including a Craft DFC
— `SaheelisLattice.kt` — are implemented). Therefore **the remaining cards are pure `add-card`
authoring** unless an individual card needs a novel one-off ability. The former backend gap is
closed:

- **Discover N (19 cards)** — ✅ implemented via `DiscoverEffect` (`Effects.Discover(N)`, CR 701.57):
  exile from the top until a nonland card with mana value ≤ N; cast it free or put it into hand;
  bottom the rest at random. Supports a dynamic threshold (Hurl into History — X = the countered
  spell's mana value) and a follow-up keyed on the discovered card (Hit the Mother Lode's Treasures).
  All 19 discover cards are done, e.g. Primordial Gnawer, Geological Appraiser, Daring Discovery,
  Quintorius Kand, Hurl into History, Walk with the Ancestors.

Beyond that, individual cards may need `add-feature` for a novel one-off ability — flagged
per-card during implementation. 175 cards remain unimplemented.

Implementation order: single-faced cards using only supported mechanics first (explore / Map /
discover / standard effects), then DFCs (Craft, MDFC lands, god flips), deferring any card that
turns out to need a new SDK primitive.
