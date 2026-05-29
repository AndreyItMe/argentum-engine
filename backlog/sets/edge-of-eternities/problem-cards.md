https://scryfall.com/sets/eoe?order=name&as=grid
https://api.scryfall.com/cards/named?exact=Beyond%20the%20Quiet&set=eoe

# Problem Cards

## Status: cards still blocked on engine work (26)

The booster set is at **235 / 261**. The cards below are the only unimplemented ones; each is blocked
on a missing engine/SDK feature. The blocking clause and the engine change needed are summarized
here and detailed in [`missing-effects.md`](missing-effects.md) (section numbers in parentheses).

Everything else in this file that is still listed under a category but checked `[x]` has been
implemented; unchecked `[ ]` entries below the categories are the remaining blockers.

| Card | Blocking clause | Engine change needed (missing-effects §) |
|------|-----------------|-------------------------------------------|
| Quantum Riddler | "if you would draw one or more cards, you draw that many plus one instead" while hand ≤ 1 | Conditional draw-replacement static (§1) |
| Faller's Faithful | "if that creature wasn't dealt damage this turn, ... draws two cards" | "Was dealt damage this turn" tracking (§2) |
| Orbital Plunge | "If excess damage was dealt this way, create a Lander token" | Excess-damage detection (§3) |
| Blade of the Swarm | "Put target exiled card with warp on the bottom of its owner's library" | Targeting warp-exiled cards (§4) |
| Bioengineered Future | "Each creature you control enters with an additional +1/+1 counter ... for each land that entered ... this turn" | Continuous extra-ETB-counters + lands-entered-this-turn tracker (§5) |
| Dyadrine, Synthesis Amalgam | "enters with ... +1/+1 counters ... equal to the amount of mana spent to cast it" | Mana-spent `DynamicAmount` in ETB replacement (§6) |
| Cosmogoyf | power/toughness = "number of cards you own in exile" (+1) | CDA P/T from cards-in-exile count (§7) |
| Command Bridge | "sacrifice it unless you tap an untapped permanent you control" | ETB sacrifice-unless-pay-cost (§7b) |
| Kav Landseeker | "At the beginning of the end step on your next turn, sacrifice that token" | Delayed trigger that always fires next turn's end step (§8) |
| Territorial Bruntar | "exile cards from the top ... until you exile a nonland card. You may cast that card this turn" | Impulse-until-nonland effect (§9) |
| Zero Point Ballad | "Destroy all creatures with toughness X or less" + reanimate one destroyed this way | Dynamic-toughness mass destroy + reanimate-from-batch (§10) |
| Weapons Manufacturing | create the noncreature "Munitions" artifact token with a leaves-battlefield damage trigger | Noncreature tokens with embedded triggers (§11) |
| Lightstall Inquisitor | "each opponent exiles a card from their hand and may play that card ..." (+cost/tapped) | Opponent exile-from-hand-may-play with modifiers (§12) |
| Moonlit Meditation | "instead create that many tokens that are copies of enchanted permanent" (once/turn) | Token-creation replacement → copies (§13) |
| Sami, Wildcat Captain | "Spells you cast have affinity for artifacts" | Affinity granted to your spells (§14) |
| Thrumming Hivepool | "Affinity for Slivers" | Affinity for a subtype (§14) |
| Tannuk, Steadfast Second | "Artifact cards and red creature cards in your hand have warp {2}{R}" | Grant Warp to hand cards (§15) |
| Terminal Velocity | put a permanent from hand and grant it haste + an LTB trigger + an end-step self-sac | Grant arbitrary abilities to a put-in permanent (§16) |
| Terrasymbiosis | "Whenever you put ... +1/+1 counters on a creature ... draw that many. Do this only once each turn." | Once-per-turn gating for triggered abilities (§17) |
| Roving Actuator | "exile ... an instant or sorcery ... Copy it. You may cast the copy without paying its mana cost." | Copy a card and cast the copy (§18) |
| Mm'menon, the Right Hand | "cast artifact spells from the top of your library" + restricted mana | Play-from-top static + restricted mana (§19) |
| Weftwalking | "The first spell each player casts ... may be cast without paying its mana cost" | First-spell-free static (§20) |
| Xu-Ifit, Osteoharmonist | reanimate "It's a Skeleton ... and has no abilities" | Reanimate as typed, ability-stripped permanent (§21) |
| Mutinous Massacre | "Choose odd or even. Destroy each creature with mana value of the chosen quality. Then gain control of all creatures ..." | Odd/even MV filter + mass temporary control (§22) |
| Pull Through the Weft | return up to two nonland permanents to hand **and** up to two lands to the battlefield tapped | Dual-group graveyard return with split destinations (§23) |
| Tezzeret, Cruel Captain | −7 emblem; "it becomes a 0/0 Robot artifact creature" | Emblem creation + becomes-Robot type-set (§24) |

---

## Warp, and related

- [x] Bygone Colossus
- [ ] Astelli Reclaimer
- [ ] Blade of the Swarm
- [ ] Exalted Sunborn
- [ ] Haliya, Guided by Light
- [ ] Rayblade Trooper
- [ ] Codecracker Hound
- [ ] Quantum Riddler
- [ ] Starfield Vocalist
- [x] Starwinder
- [ ] Perigee Beckoner
- [ ] Full Bore
- [ ] Possibility Technician
- [ ] Tannuk, Steadfast Second
- [ ] Broodguard Elite
- [ ] Close Encounter
- [x] Drix Fatemaker
- [ ] Loading Zone
- [ ] Susurian Voidborn
- [ ] Timeline Culler


## Void:

- [x] Plasma Bolt
- [ ] Roving Actuator

## Differently named lands:
- [x] All-Fates Scroll
- [ ] Fungal Colossus
- [ ] Survey Mechan

## NthSpell static ability
- [x] Brightspear Zealot

## Number of cards you own in exile
- [ ] Cosmogoyf

## Drone token:
- [x] Desculpting Blast
- [x] Station Monitor
- [ ] Pinnacle Emissary

## Complex:
- [x] Consult the Star Charts
- [ ] Dyadrine, Synthesis Amalgam
- [ ] Pull Through the Weft
- [ ] Requiem Monolith
- [ ] Tezzeret, Cruel Captain
- [ ] Thrumming Hivepool
- [ ] Ragost, Deft Gastronaut


## 4 damage to each creature:
- [x] Extinguisher Battleship

## Additional land, land from graveyard - New mechanics needed
- [ ] Icetill Explorer

## Sacrifice, unless tap an untapped - New mechanics needed
- [ ] Command Bridge

## Sacrifce, token, next endstep
- [ ] Kav Landseeker

## May sacrifice, if you do
- [x] Larval Scoutlander

## Enters with counters, not trigger new counters, (Enters with dynamic counters)
- [x] Luxknight Breacher

## You may cast artifact spells from the top of your library.
- [ ] Mm'menon, the Right Hand

## enchanted creature deals damage equal to its power to any other target
- [ ] Pain for All

## Spells you cast have affinity for artifacts
- [ ] Sami, Wildcat Captain

## you may sacrifice an artifact. When you do
- [ ] Selfcraft Mechan

## Target player mills cards equal to your life total.
- [ ] Space-Time Anomaly

## This ability costs {2} less to activate if you control a creature with a +1/+1 counter on it.
- [ ] Starport Security

## up to one other target artifact you control becomes an artifact creature with base power and toughness 2/2 and gains flying until end of turn
- [ ] Synthesizer Labship

## If the amount of mana spent to cast that spell was less than its mana value, you draw a card.
- [ ] Unravel

## Already implemented, in other set
- [ ] Banishing Light

## Each creature you control enters with an additional +1/+1 counter on it for each land that entered the battlefield under your control this turn
- [ ] Bioengineered Future

# if you control two or more tapped creatures
- [x] Dawnstrike Vanguard
- [ ] Flight-Deck Coordinator
- [x] Frontline War-Rager
- [x] Sami, Ship's Engineer
- [x] Sunstar Chaplain
- [ ] Vaultguard Trooper

## Create X tokens that are copies of target artifact or creature you control. Those tokens gain haste until end of turn. Sacrifice them at the beginning of the next end step.
- [ ] Devastating Onslaught

## Counter target spell unless its controller pays {2}. If they do, you create a Lander token
- [ ] Divert Disaster

## each player creates a Lander token
- [ ] Edge Rover

## each opponent discards a card. Each opponent who can’t loses 3 life.
- [ ] Entropic Battlecruiser

## If that creature wasn’t dealt damage this turn, its controller draws two cards.
- [ ] Faller's Faithful

## Devour land 3
- [ ] Famished Worldsire

## X is 2 plus the number of creatures and/or Spacecraft you control.
- [ ] Focus Fire

## target creature has base power and toughness 3/3 until end of turn. If you control six or more lands, that creature has base power and toughness 6/6 until end of turn instead
- [x] Genemorph Imago

## This spell costs {3} less to cast if you’ve cast another spell this turn.
- [ ] Gigastorm Titan

## Whenever one or more creatures you control with +1/+1 counters on them deal combat damage to a player, draw a card. (Only keyword by counter exists)
- [ ] Haliya, Ascendant Cadet

## create a tapped 2/2 colorless Robot artifact creature token for each multicolored permanent you control.
- [x] Infinite Guideline Station

## Conditional activated ability:
- [ ] Kavaron, Memorial World
- [x] Evendo, Waking Haven

## This spell costs {2} less to cast if your opponents control three or more creatures.
- [ ] Lashwhip Predator

## each opponent exiles a card from their hand and may play that card for as long as it remains exiled
- [ ] Lightstall Inquisitor

## Then you may sacrifice an artifact. When you do, Lithobraking deals 2
- [ ] Lithobraking

## As long as an artifact entered the battlefield under your control this turn, this creature can attack as though it didn’t have defender.
- [ ] Mechan Shieldmate

## Whenever a creature you control with a +1/+1 counter on it dies, draw a card.
- [x] Meltstrider Eulogist

## Exile the top X cards of your library, where X is one plus the mana value of the sacrificed artifact. You may play those cards this turn.
- [ ] Memorial Vault

## This spell costs {1} less to cast during your turn.
- [ ] Mental Modulation

## The first time you would create one or more tokens each turn, you may instead create that many tokens that are copies of enchanted permanent.
- [ ] Moonlit Meditation

## Choose odd or even. Destroy each creature with mana value of the chosen quality. Then gain control of all creatures until end of turn. Untap them. They gain haste until end of turn
- [ ] Mutinous Massacre

## If excess damage was dealt this way, create a Lander token
- [ ] Orbital Plunge

## X +1/+1 counters on each creature you control, where X is this creature’s power.
- [ ] Ouroboroid

## Put each card exiled with this artifact into its owner’s graveyard, then create a 2/2 colorless Robot artifact creature token for each card put into a graveyard this way. Sacrifice this artifact.
- [ ] Pinnacle Starcage

## return target creature or enchantment card from your graveyard to the battlefield
- [x] Rescue Skiff

## Return up to three target creature cards with total mana value 3 or less from your graveyard to the battlefield. Put a +1/+1 counter on each of them
- [ ] Scout for Survivors

## Whenever this creature blocks a creature with flying, this creature gets +5/+0 until end of turn
- [ ] Skystinger

## Whenever a creature you control dies, each opponent chooses a creature they control and exiles it.
- [ ] Sothera, the Supervoid

## Spend this mana only to cast an artifact spell
- [ ] Steelswarm Operator

## Planet, Draw cards equal to the sacrificed creature’s power
- [ ] Susur Secundi, Void Altar

## That permanent gains haste, "When this permanent leaves the battlefield, it deals damage equal to its mana value to each creature," and "At the beginning of your end step, sacrifice this permanent."
- [ ] Terminal Velocity

## target opponent may have you create two Lander tokens. If they don’t, put two +1/+1 counters on this creature
- [ ] Terrapact Intimidator

## Whenever you put one or more +1/+1 counters on a creature you control, you may draw that many cards. Do this only once each turn.
- [ ] Terrasymbiosis

## Whenever a land you control enters, exile cards from the top of your library until you exile a nonland card. You may cast that card this turn.
- [ ] Territorial Bruntar

## This ability costs {3} less to activate if you attacked with a Spacecraft this turn.
- [ ] Thaumaton Torpedo

## Equipped creature gets +1/+1 and has “{15}, Exile The Dominion Bracelet: You control target opponent during their next turn. This ability costs {X} less to activate, where X is this creature’s power. Activate only as a sorcery
- [ ] The Dominion Bracelet

## Whenever you play a land or cast a spell, draw a card. At the beginning of your end step, your life total becomes half your starting life total, rounded up.
- [ ] The Endstone

## Add X mana of any one color, where X is the number of charge counters on The Eternity Elevator.
- [ ] The Eternity Elevator

## When The Seriema enters, search your library for a legendary creature card, reveal it, put it into your hand, then shuffle. Other tapped legendary creatures you control have indestructible
- [ ] The Seriema

## You control enchanted permanent
- [x] Tractor Beam

## The second spell you cast each turn costs {2} less to cast.
- [ ] Uthros Psionicist

## Add {U} for each artifact you control.
- [ ] Uthros, Titanic Godcore

## Whenever a nontoken artifact creature you control deals combat damage to a player, that player gets two poison counters
- [ ] Virulent Silencer

## When this Spacecraft enters, it deals damage equal to the number of artifacts you control to target creature an opponent controls.
- [x] Warmaker Gunship

## create a colorless artifact token named Munitions with “When this token leaves the battlefield, it deals 2 damage to any target.”
- [ ] Weapons Manufacturing

## When this enchantment enters, if you cast it, shuffle your hand and graveyard into your library, then draw seven cards. The first spell each player casts during each of their turns may be cast without paying its mana cost.
- [ ] Weftwalking

## Return target creature card from your graveyard to the battlefield. It’s a Skeleton in addition to its other types and has no abilities. Activate only as a sorcery
- [ ] Xu-Ifit, Osteoharmonist

## Destroy all creatures with toughness X or less. You lose X life. If X is 6 or more, return a creature card put into a graveyard this way to the battlefield under your control
- [ ] Zero Point Ballad
