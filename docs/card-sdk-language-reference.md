# Card SDK Language Reference

A complete catalog of every building block available to card authors in the Argentum
Engine `mtg-sdk`, with a one-line description for each. Designed to be scanned and
searched. For step-by-step authoring workflow see [`api-guide.md`](api-guide.md) and
[`adding-new-cards-workflow.md`](adding-new-cards-workflow.md); for hard cases see
[`managing-complex-and-rare-abilities.md`](managing-complex-and-rare-abilities.md).

**Maintenance rule:** this document is the canonical SDK catalog. **Every change to the
SDK — new effect, trigger, condition, filter, cost, keyword, dynamic amount, modal
shape, replacement effect, etc. — must update the matching section here in the same
change.** If the entry doesn't fit cleanly in an existing section, add or rename a
section; do not let SDK additions land without a corresponding doc update.

---

## 1. Top-level card DSL

**Entry points**

- `card("Name") { ... }` — open the builder for a standard card.
- `basicLand("Plains" | "Island" | "Swamp" | "Mountain" | "Forest")` — shortcut for basic lands (sets type line,
  intrinsic mana ability, supertype).

**Card builder properties**

- `manaCost: String` — mana cost in `{X}{R}{U}` syntax. Supported pip forms: generic (`{2}`),
  colored (`{R}`), colorless (`{C}`), variable (`{X}`), hybrid (`{W/U}` — either colour),
  Phyrexian (`{W/P}` — colour or 2 life), and monocolored hybrid / "twobrid" (`{2/B}` — two
  generic **or** one mana of the colour; mana value counts the generic side per CR 202.3f).
  Gurmag Nightwatch's `{2/B}{2/G}{2/U}` is the canonical twobrid example.
- `typeLine: String` — full type line including supertypes and subtypes.
- `oracleText: String` — rules text; auto-generated from abilities if omitted.
- `power: Int?`, `toughness: Int?` — base P/T for creatures.
- `dynamicPower`, `dynamicToughness` — characteristic-defining P/T (e.g. `*/*` Tarmogoyf).
- `dynamicStats(source, powerOffset?, toughnessOffset?)` — sets both with optional `±` deltas.
- `startingLoyalty: Int?` — starting loyalty for planeswalkers.
- `colorIdentity: String?` — override (normally auto-detected). Treated as authoritative in this repo.
- `auraTarget: TargetRequirement?` — what this Aura enchants.
- `morph: String?` — morph mana cost (cast face-down).
- `morphCost: PayCost?` — non-mana morph cost.
- `morphFaceUpEffect: Effect?` — effect that fires when this morph turns face up.
- `warp: String?` — Warp alt-cost; exiles at end of turn.
- `evoke: String?` — Evoke alt-cost; sacrifices on ETB.
- `selfAlternativeCost: SelfAlternativeCost?` — generic alternative-cost slot.
- `castTimeCreatureTypeChoice: CastTimeCreatureTypeSource?` — forces a creature-type choice at cast time.
- `cantBeCountered: Boolean` — spell is uncounterable.
- `conditionalFlash: Condition?` — gains flash while condition holds.
- `layout: CardLayout` — physical layout shape (see §2).

**Ability blocks inside `card { ... }`**

- `triggeredAbility { ... }` — "when/whenever/at" abilities.
- `staticAbility { ... }` — continuous effects.
- `activatedAbility { ... }` — `cost: effect` abilities.
- `loyaltyAbility(±N) { ... }` — planeswalker loyalty abilities.
- `replacementEffect { ... }` — "instead/if … would" replacement.
- `keywords(...)` / `keywordAbility(...)` / `keywordAbilities(...)` — add keyword abilities.
- `spell { ... }` — define the spell payload for instants/sorceries and Adventure / Omen faces.

---

## 2. Card faces, layouts, printings, set metadata

**`CardLayout`**

- `NORMAL` — standard single face (default).
- `SPLIT` — two or more halves on one card; combined characteristics apply off-battlefield (CR 709.4c). Used for Rooms,
  Fuse, Aftermath, and the classic Invasion split cards (Pain // Suffering, Stand // Deliver, Wax // Wane). Each half is
  cast independently via `CastSpell.faceIndex`; only the chosen half goes on the stack (CR 709.4). A non-permanent half
  carries its effect in a `face("Name") { spell { … } }` block (with its own `target(...)` requirements); a permanent
  half (Room) carries triggered/activated/static abilities instead.
- `ADVENTURE` — primary face is a creature, `cardFaces[0]` is an instant/sorcery Adventure (CR 715). Resolving the
  Adventure exiles the card and grants permission to cast the creature from exile.
- `OMEN` — primary face is a permanent (creature), `cardFaces[0]` is an instant/sorcery Omen (Tarkir: Dragonstorm).
  Casts exactly like an Adventure (creature face, or Omen via `CastSpell.faceIndex = 0`), but resolving the Omen
  **shuffles the card into its owner's library** instead of exiling it — no cast-from-exile linkage. DSL:
  `card { omen("Name") { spell { … } } }`.
- `MODAL_DFC` — primary characteristics are the front face, `cardFaces[0]` is the back face (CR 712). Cast **one**
  face from hand (front via primary characteristics, back via `CastSpell.faceIndex = 0`), never both. Unlike
  ADVENTURE there is no exile-then-recast linkage — a spell back resolves as an ordinary spell (graveyard, or exile
  when its script sets `selfExileOnResolve` via `spell { selfExile() }`). DSL: `card { modalBack("Name") { spell { … } } }`.

**`CardFace` (SPLIT / ADVENTURE / OMEN / MODAL_DFC)**

- `name` — face name.
- `manaCost` — face mana cost.
- `typeLine` — face type line.
- `script { ... }` — that face's abilities; for instant/sorcery SPLIT halves, Adventures, and modal DFC spell
  faces this includes a `spell { effect = …; target(...) }` block holding the face's effect and target
  requirements (plus `selfExile()` for faces that exile themselves on resolution).
- `keywords` — face-local keywords.
- `imageUri` — face art when it differs from the front (MODAL_DFC backs have their own Scryfall image).

**`metadata { ... }`**

- `rarity: Rarity` — `COMMON | UNCOMMON | RARE | MYTHIC | SPECIAL | BONUS`.
- `collectorNumber: String` — Scryfall collector number.
- `artist: String` — illustrator credit.
- `flavorText: String` — italicized flavor.
- `imageUri: String?` — art URL; auto-fetched from Scryfall if omitted.
- `scryfallId: String?` — Scryfall UUID.
- `releaseDate: String?` — `YYYY-MM-DD`.
- `inBooster: Boolean` — appears in draft boosters (default `true`; `false` for Special Guests / starter exclusives).
- `oracleTextOverride: String?` — bypass auto-generated oracle text.

**Reprints** — add a `Printing` row in the new set's `Reprints.kt` and wire it into `MtgSet.printings`. Never duplicate
the `CardDefinition`.

---

## 3. Costs (`Costs.*`)

- `Costs.Free` — costs nothing (`{0}`).
- `Costs.Tap` — `{T}`; tap this permanent.
- `Costs.Untap` — `{Q}`; untap this permanent.
- `Costs.Mana("{2}{U}")` — pay the given mana cost (string or `ManaCost`).
- `Costs.PayLife(amount)` — pay N life.
- `Costs.PayXLife` — pay X life, where X is the value chosen for the ability's `{X}` mana cost
  (e.g. "{X}{B}, {T}, Pay X life: …" on Krumar Initiate). The X-linked counterpart to
  `Costs.PayLife`; `calculateMaxAffordableX` caps X by the controller's life total.
- `Costs.Sacrifice(filter)` — sacrifice a permanent matching the filter (may include self).
- `Costs.SacrificeAnother(filter)` — sacrifice a *different* permanent matching the filter.
- `Costs.DiscardCard` — discard a card you choose (any card).
- `Costs.Discard(filter, count = 1, atRandom = false)` — discard `count` cards matching the filter.
  When `atRandom` is true the engine picks the cards (no player selection); otherwise the player
  chooses which cards to discard.
- `Costs.DiscardAtRandom(count, filter)` — discard `count` cards chosen at random (Meteor Storm:
  "Discard two cards at random").
- `Costs.DiscardHand` — discard your entire hand.
- `Costs.DiscardSelf` — discard this card (cycling-style).
- `Costs.Composite(c1, c2, ...)` — multiple costs paid together.

**Spell-level alternatives**

- `selfAlternativeCost` — generic "cast instead for" alt-cost.
- `evoke` — pay evoke cost; creature is sacrificed at ETB.
- `morph` — cast face-down for `{3}`-ish.
- `warp` — cast from anywhere; exiled at end of turn.
- `conditionalFlash` — flash while condition holds.
- `cantBeCountered` — spell is uncounterable.
- `xManaRestriction = setOf(Color.BLACK, Color.RED)` — "spend only [colors] on X." Restricts which
  mana may pay the `{X}` portion of the cost (the fixed colored/generic portion is unaffected).
  Available in both `spell { }` and `activatedAbility { }` blocks; honored by the mana solver and the
  payment path. Per-color amount spent on X is then readable via `DynamicAmount.ManaSpentOnX(color)`.
  Soul Burn (`spell { xManaRestriction = setOf(Color.BLACK, Color.RED) }`) and Atalya, Samite Master
  (`activatedAbility { xManaRestriction = setOf(Color.WHITE) }`) are the first users.

**`AdditionalCost`** — extra costs paid alongside the mana cost.

- `AdditionalCost.BlightVariable` — "as you cast, you may pay X life" (Blight X); X exposed via
  `DynamicAmount.AdditionalCostBlightAmount`.
- `AdditionalCost.PayLifePerTarget(amountPerTarget)` — "this spell costs N life more to cast for
  each target." Pair with an unbounded `TargetCreature(unlimited = true)` etc.; the engine
  auto-pays `amountPerTarget × action.targets.size` at cast resolution (Phyrexian Purge).

**`PayCost`** — payable costs used by [`PayOrSufferEffect`](#15-replacement-effects) ("do X
unless you Y") and by `morphCost` (non-mana face-up cost). Distinct from `AbilityCost` / `Costs.*`
which model an ability's activation cost; `PayCost` models a single cost the engine prompts the
player to pay against an alternative consequence.

- `PayCost.Mana(ManaCost)` — pay mana (auto-taps lands via the solver). "...unless you pay {U}{U}"
  (Vaporous Djinn).
- `PayCost.OwnManaCost` — pay the mana cost of the permanent the cost applies to (its *own* mana
  cost, read from `CardComponent.manaCost` at payment time). Use for granted abilities like
  Essence Leak ("...sacrifice this permanent unless you pay its mana cost"), where the affected
  permanent — not a fixed cost — owns the mana cost. The engine resolves it into a concrete
  `PayCost.Mana` against that permanent before prompting.
- `PayCost.PayLife(amount)` — pay N life; offered only when the player has more than N life.
  "...unless you pay 3 life."
- `PayCost.Discard(filter = Any, count = 1, random = false)` — discard cards matching `filter`.
  Random variant prompts a yes/no and the engine picks the discards (Pillaging Horde).
- `PayCost.Sacrifice(filter = Any, count = 1)` — sacrifice permanents you control matching
  `filter`. Source is auto-excluded. "...unless you sacrifice three Forests" (Primeval Force).
- `PayCost.Exile(filter = Any, zone = HAND, count = 1)` — exile cards from `zone` matching
  `filter`. "...unless you exile a blue card from your hand."
- `PayCost.Tap(filter = Any, count = 1)` — tap untapped permanents you control matching `filter`.
  Source is auto-excluded. Tapping each emits a `TappedEvent` so "becomes tapped" triggers fire.
  "...unless you tap an untapped permanent you control" (Command Bridge).
- `PayCost.Choice(options)` — present several `PayCost`s; player picks one (or the suffer effect).
  Unaffordable options are hidden. "...unless they sacrifice a nonland permanent or discard a card."
- `PayCost.ReturnToHand(filter, count = 1)` — return permanents you control to their owner's hand.
  Currently only consumed by `morphCost`; not yet wired into `PayOrSufferEffect`.
- `PayCost.RevealCard(filter, count = 1)` — reveal a card from hand matching `filter`. Currently
  only consumed by `morphCost`; not yet wired into `PayOrSufferEffect`.

---

## 4. Effects (`Effects.*`)

Atomic effect factories. For library/zone manipulation, prefer the pipelines in §5.

### Damage

- `DealDamage(amount, target)` — deal fixed/dynamic damage.
- `DealXDamage(target)` — deal X damage (spell's X).
- `Fight(target1, target2)` — two creatures each deal damage equal to their power to each other (CR 701.12).

### Life

- `GainLife(amount, target?)` — target gains life (default: controller).
- `LoseLife(amount, target)` — target loses life.
- `SetLifeTotal(amount, target)` — set target's life total to N.
- `ExchangeLifeAndPower(target)` — swap target's power with controller's life total.
- `LoseHalfLife(roundUp, target, lifePlayer?)` — lose half of life total (round up/down).
- `LoseGame(target, message?)` — target loses the game.
- `WinGame(target, message?)` — target wins the game.
- `ForceExileMultiZone(count, target)` — exile from hand/battlefield/graveyard combined (Lich's Mastery shape).

### Cards (draw / discard)

- `DrawCards(count, target?)` — draw N (default: controller).
- `DrawUpTo(max, target)` — draw up to N (player picks 0–N).
- `DrawRevealDiscardUnless(filter, target?)` — draw a card, reveal it, and discard it unless it matches `filter` (e.g. Sindbad: "draw a card and reveal it; if it isn't a land card, discard it"). Matches the drawn card in hand against `filter`.
- `Discard(count, target)` — controller-of-target chooses; mandatory.
- `EachOpponentDiscards(count)` — each opponent discards N.
- `EachPlayerReturnPermanentToHand()` — each player bounces a permanent.
- `EachPlayerDrawsForDamageDealtToSource()` — each player draws equal to damage source took this turn.
- `ReadTheRunes()` — draw N, then discard N (or sacrifice permanents).
- `ReplaceNextDraw(effect)` — replaces controller's next draw with the given effect.

### Destruction & exile

- `Destroy(target)` — destroy target (respects indestructible).
- `DestroyAll(filter, noRegenerate?, storeDestroyedAs?, excludeTriggering?)` — destroy all matching; optionally
  save the ID list for follow-up. `excludeTriggering = true` spares the triggering entity, for "destroy all
  *other* … with it" triggers (Spreading Plague).
- `DestroyAllAndAttached(filter, noRegenerate?)` — also destroys auras/equipment on the matching permanents.
- `DestroyAllEquipmentOnTarget(target)` — wreck the gear attached to a creature.
- `Exile(target)` — exile target.
- `ExileAndGrantOwnerPlayPermission(target, until?)` — exile + owner may play it (Garth-style).
- `ExileOpponentsGraveyards()` — exile every card in each opponent's graveyard.
- `ExileUntilLeaves(target)` — linked exile; returns when source leaves the battlefield.
- `ExileGroupAndLink(filter, storeAs?)` — exile all matching permanents into source's linked exile pile.
- `ExileFromTopRepeating(count, repeatCondition)` — keep exiling top cards while a condition holds.
- `ExileLibraryUntilManaValue(manaValue)` — exile from library until mana value ≤ N.

### Return / placement

- `ReturnToHand(target)` — bounce to hand.
- `PutOnTopOfLibrary(target)` — place target on top of its owner's library.
- `PutOnTopOrBottomOfLibrary(target)` — player chooses top or bottom.
- `PutSecondFromTopOrBottomOfLibrary(target)` — second-from-top or bottom.
- `ShuffleIntoLibrary(target)` — shuffle target into owner's library.
- `PutIntoLibraryNthFromTop(target, positionFromTop)` — place N from the top.
- `PutOntoBattlefield(target, tapped?)` — put target on the battlefield.
- `PutOntoBattlefieldUnderYourControl(target)` — under controller's control.
- `PutOntoBattlefieldFaceDown(count, target?)` — enter face-down (2/2 morph shape).
- `ReturnSelfToBattlefieldAttached(target)` — return source attached to target (Aura recursion).
- `ReturnCreaturesPutInGraveyardThisTurn(player)` — Patriarch's Bidding shape.

### Hand reveal

- `Effects.MayRevealCardFromHand(filter, otherwise?)` — atomic "you may reveal a `filter`
  card from your hand" choice. Computes eligible hand cards; if none, runs `otherwise`
  silently; otherwise prompts the controller with a `SelectCardsDecision` (min=0, max=1).
  Revealing emits a `CardsRevealedEvent` and stops; declining (or empty selection) runs
  `otherwise`. Compose with `Effects.Tap`/`Effects.Sacrifice`/etc. via `otherwise` to
  express "if you don't, X" riders — e.g. SOI shadow lands wrap this in
  `OnEnterRunEffect(...)` with `otherwise = Effects.Tap(EffectTarget.Self)` for the
  "this land enters tapped" branch.

### Library reveal & free cast

- `Effects.Cascade` — CR 702.85a (`CascadeEffect`). Exile from the top of the controller's library
  until a nonland card with mana value **strictly less than** the triggering spell's is exiled,
  offer to cast it for free, bottom-randomize every exiled card that isn't cast.
- `RevealAndMayCastFromLibraryEffect(count, maxManaValue, player?)` — Sunbird's Invocation
  shape. Reveal top `count` cards of `player`'s library, present a `SELECT_CARDS` prompt over
  the revealed nonland cards with mana value ≤ `maxManaValue` (player picks 0 or 1), free-cast
  the chosen card if any, bottom-randomize the rest. Pair with `DynamicAmounts.triggeringManaValue()`
  (= `EntityProperty(Triggering, ManaValue)`) when both bounds come from the triggering spell.

### Linked exile & play-from-exile permissions

- `ReturnLinkedExile()` — return all from source's linked exile, under controller.
- `ReturnLinkedExileUnderOwnersControl()` — return under each card's owner.
- `ReturnLinkedExileToHand()` — return all from linked exile to hand.
- `ReturnOneFromLinkedExile()` — return one chosen card.
- `GrantMayPlayFromExile(from, restriction?)` — owner may play matching cards from exile.
- `GrantPlayWithoutPayingCost(from)` — same, without paying mana costs.
- `GrantFreeCastTargetFromExile(target)` — cast specific exiled card for free.

### Stats & keywords

- `ModifyStats(power, toughness, target?)` — `±P/±T` until end of turn (default scope).
- `GrantKeyword(keyword, target, duration)` — grant a keyword for a duration.
- `RemoveKeyword(keyword, target, duration)` — strip a keyword.
- `RemoveAllAbilities(target, duration)` — wipe all abilities (including granted keywords).
- `LoseAllCreatureTypes(target, duration)` — remove all creature subtypes.
- `SetCreatureSubtypes(subtypes, target, duration)` — replace subtypes outright.
- `AddCreatureType(subtype, target, duration)` — additive subtype.
- `GrantHexproof(target, duration)` — temporary hexproof.
- `GrantExileOnLeave(target)` — "if it would leave, exile instead".
- `GrantKeywordToAttackersBlockedBy(keyword, target)` — grant keyword to creatures this blocks.

### Counters

- `AddCounters(type, count, target)` — add N counters of `type`.
- `AddDynamicCounters(type, amount, target)` — count is computed at resolution.
- `DoubleCounters(type?, target?)` — one-shot doubling of the `type` counters (default `+1/+1`) already on the
  target: reads the current count and places that many more (so the total doubles). Distinct from the
  `DoubleCounterPlacement` replacement (which doubles *future* placements); the added counters still trigger
  placement replacements like Hardened Scales. No-op with zero counters. Sage of the Fang.
- `RemoveCounters(type, count, target)` — remove N counters.
- `RemoveAnyNumberOfCounters(target)` — player removes 0 or more.
- `RemoveAllCounters(target)` — wipe every counter.
- `RemoveAllCountersOfType(type, target)` — wipe one kind.
- `MoveAllLastKnownCounters(target)` — Hooded Hydra / Essence Channeler — move every counter kind from source's
  last-known state.
- `Counters.ANY` — wildcard counter-type string for "counters of any type" triggers/events (e.g.
  `Triggers.countersPlacedOn`); not a real placeable counter, only a matcher sentinel.
- `DistributeCountersFromSelf(type?, count?)` — split source's counters among creatures you control.
- `DistributeCountersAmongTargets(total, type?, minPerTarget?)` — divvy N counters among chosen targets.
- `Proliferate()` — add one counter of each kind already present on chosen permanents/players (CR 701.27).
- `AddCountersToCollection(name, type, count)` — add counters to cards held in a pipeline collection.

### Color & type

- `AddCardType(type, target, duration)` — add a card type (e.g. become an artifact).
- `AddSubtype(subtype, target, duration)` — add a subtype temporarily.
- `SetLandType(landType, target, duration, fromChosenValueKey)` — target land *becomes* the basic land type, **replacing** its existing land subtypes (Rule 305.7); pass `fromChosenValueKey` to read the type from a preceding `ChooseOption(OptionType.BASIC_LAND_TYPE)`. One-shot counterpart to the `SetEnchantedLandType` aura static ability. (Dream Thrush)
- `ChooseColorForTarget(target)` — target picks a color; stored in context.
- `BecomeChosenManaColor(target)` — adopt the previously chosen color.
- `ChangeColor(colors, target, duration)` — replace colors with the given set.
- `BecomeAllColors(target, duration)` — five-color until end of turn.
- `ChangeColorToChosen(target, duration)` — replace the target's colors with the single color picked
  by a preceding `ChooseColorThen` (read from `EffectContext.chosenColor`). The target may be a
  **spell on the stack** or a permanent — the color projection reads the recolored entry in both
  zones, so a recolored spell's new color drives color-matching checks (e.g. protection) during
  resolution. Compose as `ChooseColorThen(then = ChangeColorToChosen(target))` for "target ...
  becomes the color of your choice" (Blind Seer).
- `ChangeWordInText(target, duration)` — Layer-3 text change: the player picks one **color word**
  or **basic land type** on the target and a replacement of the same category, recorded as a
  `TextReplacement` on the target. A basic-land-type swap flows through the projected type line, so
  the land's mana (via `IntrinsicManaAbilities`), landwalk relevance, and type checks all follow
  automatically (Forest→Island taps for `{U}`); a color-word swap rewrites protection-from-color and
  `HasColor`/`NotColor` filters. `duration = EndOfTurn` is stripped at cleanup; `Permanent` is the
  Artificial-Evolution-style indefinite change. The player picks the FROM and TO words on **one
  screen** (a `ChooseReplacementDecision`), with words **present on the target** surfaced first
  (labeled "On <card>") so a no-op pick is discouraged, and a live `from → to` preview. (Crystal Spray)

### Mana

- `AddMana(color, amount, restriction?)` — add N of one color.
- `AddColorlessMana(amount, restriction?)` — add colorless.
- `AddManaOfChoice(colorSet, amount?, restriction?, riders?)` — **unified primitive.** Add N mana of one color the controller picks from a resolved [ManaColorSet](#manacolorset). All "any-color from a constrained pool" cards (any color, commander identity, among permanents, lands could produce, source-chosen color) are expressed as this effect plus a different `ManaColorSet`. `riders` is a `Set<ManaSpellRider>` consumed when the mana pays for a spell (e.g. Path of Ancestry tags its mana with `ScryOnSharedTypeWithCommander`); when riders are set without a `restriction`, the engine stores the entries under `ManaRestriction.AnySpend` to preserve the rider through the pool.
- `AddAnyColorMana(amount?, restriction?)` — sugar for `AddManaOfChoice(ManaColorSet.AnyColor, amount)`.
- `AddManaOfChosenColor(amount?)` — sugar for `AddManaOfChoice(ManaColorSet.SourceChosenColor, amount)`.
- `AddManaOfColorAmong(filter)` — sugar for `AddManaOfChoice(ManaColorSet.AmongPermanents(filter))`.
- `AddManaOfColorLandsCouldProduce(scope)` — sugar for `AddManaOfChoice(ManaColorSet.LandsCouldProduce(scope))`. Fellwar Stone / Exotic Orchard / Reflecting Pool shape.
- `AddManaOfColorInCommanderColorIdentity()` — sugar for `AddManaOfChoice(ManaColorSet.CommanderIdentity)`. Arcane Signet / Command Tower shape.
- `AddAnyColorManaSpendOnChosenType(typeName)` — mana that can only pay for a specific card type (kept separate because it derives a runtime [ManaRestriction] from the source's chosen subtype).
- `AddDynamicMana(amount, allowedColors, restriction?)` — split X across a fixed color set, distinct from `AddManaOfChoice` because it distributes the full X total across multiple colors rather than producing X copies of one chosen color.
- `AddManaInAnyCombination(colors, amount)` — split N across colors (alias for `AddDynamicMana`).
- `AddOneManaOfEachColorAmong(filter)` — one mana of *each* color found among matching permanents (Bloom Tender shape).

### Tokens & emblems

- `CreateToken(name, p, t, colors?, subtypes?, keywords?, count?, tapped?)` — make N tokens. `count` accepts an
  `Int` or a `DynamicAmount` (the latter for "create X tokens" wording — e.g. Verdeloth the Ancient passes
  `count = DynamicAmount.XValue` to make X Saprolings when kicked). Publishes the created token entity IDs to the
  `CREATED_TOKENS` pipeline collection, so a sibling effect in a `CompositeEffect` can address each token via
  `EffectTarget.PipelineTarget(CREATED_TOKENS, index)` — e.g. Mardu Monument grants menace and haste until end of
  turn to each of its three freshly-created Warriors with one `GrantKeyword` per token.
- `CreateDynamicToken(dynamicPower, dynamicToughness, colors?, creatureTypes, keywords?, count?, controller?, imageUri?)` —
  tokens whose P/T is computed at resolution (e.g. Pure Reflection's X/X Reflection where X = the cast spell's mana
  value, via `DynamicAmounts.triggeringManaValue()`). `controller` directs who gets the token (e.g.
  `EffectTarget.PlayerRef(Player.TriggeringPlayer)` for "that player creates …"); `imageUri` sets custom token art.
- `CreateTokenCopyOfSelf(count?, tapped?)` — token copies of source.
- `CreateTokenCopyOfTarget(target, count?, overridePower?, overrideToughness?, tapped?, attacking?, triggeredAbilities?, addedKeywords?, addedSupertypes?, removedSupertypes?, overrideColors?, overrideSubtypes?)` —
  token copy of another permanent (or a card in any zone — the executor copies the target's `CardComponent`,
  so a graveyard/exile card works). `overrideColors`/`overrideSubtypes` replace the copy's colors/subtypes
  outright for "a token that's a copy … except it's a 5/5 black Demon" wording (Ardyn, the Usurper).
- `CreateTokenCopyOfEquippedCreature(count?, tapped?)` — equipment-specific copy.
- `CreateTreasure(count?, tapped?)` — Treasure tokens.
- `CreateFood(count?, controller?)` — Food tokens.
- `CreateLander(count?, controller?)` — Lander land tokens.
- `CreateMutavault(count?, tapped?, controller?)` — Mutavault tokens.
- `CreateRoleToken(roleName, target)` — attach a Role aura token.
- `CreateMapToken(count?)` — Map artifact tokens.
- `CreateDroneToken(count?)` — Drone tokens.
- `CreatePermanentEmblem(name, abilities)` — planeswalker emblem with static abilities.

### Ability granting

- `GrantTriggeredAbilityEffect(ability)` — permanently grant a triggered ability.
- `CreateGlobalTriggeredAbility(ability, duration = Duration.Permanent, descriptionOverride? = null)` — engine-wide triggered ability with no source permanent. `duration` is a plain parameter, so the one method covers every lifetime: `Duration.EndOfTurn` (False Cure, Death Frenzy), `Duration.UntilYourNextTurn` (Season of the Bold), `Duration.EndOfCombat`, `Duration.Permanent` (Dimensional Breach, planeswalker emblems), etc. `descriptionOverride` sets emblem display text.
- `GrantSpellKeywordEffect` — grant a keyword to a spell on the stack.
- `GrantSpellsCantBeCountered(target, filter, duration)` — target's matching spells become uncounterable (Domri shape).

### Control & combat

- `GainControlEffect(target, duration)` — gain control until end of turn (default).
- `ExchangeControlEffect(target1, target2)` — swap control of two permanents.
- `GainControlByMostEffect(metric, target?)` — the player with strictly the most of a `PlayerRankMetric` takes it (tie = no change). Metrics: `PlayerRankMetric.LifeTotal` (Ghazbán Ogre), `PlayerRankMetric.CreaturesOfSubtype(subtype)` (Thoughtbound Primoc). Facades: `Effects.GainControlByMostLife()`, `Effects.GainControlByMostOfSubtype(subtype)`.
- `GiftGivenEffect(target)` — "gift" temporary control.
- `CantAttackEffect(target, unless?)` — target can't attack.
- `CantBlockEffect(target, unless?)` — target can't block.
- `CantAttackGroupEffect(filter, condition?)` — group-scoped can't-attack.
- `CantBlockGroupEffect(filter, condition?)` — group-scoped can't-block.
- `Effects.Suspect(target)` — target becomes Suspected (MKM keyword). Composite: `SetSuspectedEffect` (named status, CR 701.60d dedup) + `GrantKeywordEffect(MENACE)` + `CantBlockEffect`.
- `RemoveFromCombatEffect(target)` — yank target out of combat.
- `Effects.CanAttackDespiteDefenderThisTurn(target = Self)` (`CanAttackDespiteDefenderThisTurnEffect`) — target can attack this
  turn as though it didn't have defender. Adds a transient `CanAttackDespiteDefenderThisTurnComponent`
  honored by the defender attack-restriction rule and cleaned up at end of turn. The
  activated/temporary counterpart to the static `CanAttackDespiteDefender` ability (Krotiq Nestguard).
- `SkipNextTurnEffect(target)` — target skips their next turn.
- `Effects.SkipNextDrawStep(target = Controller)` (`SkipNextDrawStepEffect`) — target skips their next draw step. Adds a one-shot `SkipDrawStepComponent` marker consumed by `DrawPhaseManager.performDrawStep` (Elfhame Sanctuary's "you skip your draw step this turn").
- `HijackNextTurnEffect(target)` — you control target's next turn.
- `GrantCantBeBlockedByChosenColorEffect(target, duration)` — unblockable except by chosen color.
- `CantCastSpellsEffect(target, until?)` — target can't cast spells. Facade: `Effects.CantCastSpells(target, duration)`.
- `Effects.CantPlayLandsThisTurn(target = Controller)` (`PreventLandPlaysThisTurnEffect`) — the target player can't
  play lands for the rest of this turn (sets remaining land drops to 0). Defaults to the controller (Rock Jockey);
  pass `EffectTarget.ContextTarget(n)` for "target player can't play lands this turn" cards like Turf Wound.
- `CantActivateLoyaltyAbilitiesEffect(target, duration)` — target can't activate planeswalkers' loyalty abilities.
  Facade: `Effects.CantActivateLoyaltyAbilities(target, duration)`. Sibling of `CantCastSpells`; compose the two for
  cards that forbid both (e.g. Revel in Silence).

### Forced sacrifice / discard

- `SacrificeTargetEffect(target, sacrificedByItsController = false)` — sacrifice a specific permanent. By
  default only fires if the resolving player controls it; set `sacrificedByItsController = true` for
  "[that creature]'s controller sacrifices it" (e.g. The Ring's Ring-bearer ability).
- `ForceSacrificeEffect(target, count)` — edict; target sacrifices N creatures.
- `ForceReturnOwnPermanentEffect(target)` — target bounces one of their own.

### Stack manipulation

- `CounterEffect(target, condition?, destination?)` — counter a spell/ability; optionally send elsewhere.
  - `target = CounterTarget.Spell` / `Ability` / `SpellOrAbility` — `SpellOrAbility` dispatches at resolution by inspecting whether the stack entity has a `SpellOnStackComponent`. Used by Teferi's Response.
  - `condition = CounterCondition.UnlessPaysMana(cost, onPaid?)` / `UnlessPaysDynamic(amount, onPaid?)` — "unless its controller pays …" with an optional `onPaid: Effect` rider that fires **only** when the spell's controller pays (Divert Disaster's "If they do, you create a Lander token"). The rider executes with the counter's controller as `controllerId`, so "you" in the rider resolves to the caster of the counter. The rider does not fire when the spell is countered. Facade: `Effects.CounterUnlessPays(cost, onPaid)` / `Effects.CounterUnlessDynamicPays(amount, exileOnCounter, onPaid)`.
- `CounterAllOnStackEffect(filter?, destination?)` — counter everything matching.
- `OpenLifeBid(onWin, participant = Player.Opponent)` — open life-bidding auction between you and `participant` (resolved against the effect context). You open at a bid of 1; the two bidders alternate topping the high bid (yes/no to top, then a number for the amount, capped at the bidder's life) until one passes. The high bidder loses that much life; `onWin` runs **only if you win**, with the original targets in context. If `participant` resolves to you (or to nobody), you're the sole bidder and win at the opening bid. For Mages' Contest, bid against the targeted spell's controller and counter it: `Effects.OpenLifeBid(Effects.CounterSpell(), Player.ControllerOf("target spell"))` — pair with a `TargetSpell` requirement.
- `DestroySourceOfTargetedAbilityEffect` — when the targeted stack object is a permanent's activated/triggered ability, destroy that source permanent. Compose *before* the counter step so the ability component is still readable (Teferi's Response).
- `CopyTargetSpellEffect(target)` — copy a spell on the stack.
- `CopyTargetTriggeredAbilityEffect(target)` — copy a triggered ability on the stack.
- `CopyNextSpellCastEffect(copies = 1, spellFilter = InstantOrSorcery)` (facade `Effects.CopyNextSpellCast(copies, spellFilter)`) — when its controller next casts a spell matching `spellFilter` this turn, create `copies` copies of it. `spellFilter` is a `GameObjectFilter` matched against the spell as it's cast, so the default "instant or sorcery" (Howl of the Horde) can be widened — e.g. `GameObjectFilter.Creature` for "copy the next creature spell." Consumed after one matching cast. Non-matching casts leave the entry waiting.
- `CopyEachSpellCastEffect(copies = 1, spellFilter = InstantOrSorcery)` (facade `Effects.CopyEachSpellCast(copies, spellFilter)`) — the persistent sibling: copies **every** spell matching `spellFilter` the controller casts for the rest of the turn (The Mirari Conjecture Ch. III). Same `spellFilter` parameterization as above.
- `CopyCardIntoCollectionEffect(source, storeAs)` (facade `Effects.CopyCardIntoCollection(source, storeAs)`) — copy a **card in a zone** (not a spell on the stack), publishing the copy's entity id to pipeline collection `storeAs`. Per Rule 707.12 the copy is created in the card's current zone under the effect's controller and tagged as a stack-style copy, so once cast it becomes a token if it's a permanent spell and ceases to exist if it's an instant/sorcery (Rule 707.10). Pair with `CastFromCollectionWithoutPayingCostEffect(from)` (facade `Effects.CastFromCollectionWithoutPayingCost(from)`, wrap in `MayEffect` for "you may cast") to express "copy a card, then cast the copy" — e.g. **Shiko, Paragon of the Way**: `Composite(MoveToZoneEffect(target, Zone.EXILE), Effects.CopyCardIntoCollection(target, "copy"), MayEffect(Effects.CastFromCollectionWithoutPayingCost("copy")))`. A copy that is never cast is swept up by the Rule 707.10a state-based action (`PhantomCardCopiesCheck`), so no explicit cleanup step is needed.
- `CastAnyNumberFromCollectionWithoutPayingCostEffect(from)` (facade `Effects.CastAnyNumberFromCollectionWithoutPayingCost(from)`) — the multi-cast sibling of `CastFromCollectionWithoutPayingCostEffect`. **During this effect's resolution**, the controller is offered the cards in pipeline collection `from` (filtered to those still in exile) one at a time and may cast each for free until they decline; each cast's targets / X / modes flow through the normal cast machinery. Because the casts go through the synthesized-cast path (like Cascade), card-type **timing restrictions are ignored** and no lingering "you may play it later" permission is granted — cards left uncast just stay where they are (the controller can't wait until later in the turn). Hand it the eligible set: filter the collection upstream (e.g. nonland + `FilterCollection(ManaValueAtMost(...))`). Models "you may cast any number of spells with mana value X or less from among them without paying their mana costs" — e.g. **Kotis, the Fangkeeper**: `GatherCards(TopOfLibrary(damage, TriggeringPlayer)) → MoveCollection(→ exile) → FilterCollection(Nonland) → FilterCollection(ManaValueAtMost(damage)) → CastAnyNumberFromCollectionWithoutPayingCostEffect("castable")`. Also used by **Villainous Wealth** (the same chain off an {X} sorcery) and **Etali, Primal Storm** (exile the top card of each library, no MV cap).
- `ChangeTargetEffect(spell, newTarget)` — change a spell's target.
- `ChangeSpellTargetEffect(spell, filter)` — same, filtered.
- `ReselectTargetRandomlyEffect(spell)` — re-choose targets at random.
- `Effects.ChangeTriggeringObjectTargets(chooser = RetargetChooser.Controller)` — the player named by `chooser` may change the target or targets of the triggering spell/ability (`context.triggeringEntityId`); the player-chosen, multi-target counterpart of `ReselectTargetRandomly`. `RetargetChooser.Controller` = the effect's controller; `RetargetChooser.OwnerOfStored(name)` = the owner of the single card in pipeline collection `name` (≠1 card → no chooser → no-op). Reselection is offered slot-by-slot among the original object's legal targets (legality judged from *its* controller, current target kept as a "keep" option, no target chosen twice). **Psychic Battle** composes from atoms: `Composite(GatherCards(TopOfLibrary(1, Player.Each), revealed=true, storeAs="revealed"), FilterCollection("revealed", GreatestManaValue, storeMatching="w"), ChangeTriggeringObjectTargets(RetargetChooser.OwnerOfStored("w")))` — a tie keeps several greatest cards so `OwnerOfStored` finds no unique owner and the targets stay put.
- `ReturnSpellToOwnersHandEffect(spell)` — return a spell from the stack to hand.

### Combat-shape & misc

- `PreventDamageEffect(amount, direction, scope, sourceFilter, onPrevented, gainLifeFromColors, duration)` — prevention shield. `amount = null` prevents all. `sourceFilter` can be `ChosenSource` (player picks any source on resolution) or `ChosenColoredSource` (player picks a source on resolution, but only colored sources are offered — "a source of your choice that shares a color with the mana spent"; a colorless source qualifies for nothing, so it's never offered — Protective Sphere). `onPrevented: Effect?` is an **arbitrary follow-up effect** run when a single-instance `ChosenSource` shield prevents an instance of damage (see below). `gainLifeFromColors: Set<Color>` makes the shield's controller gain that much life whenever it prevents damage from a source of one of those colors (Samite Ministration). Facades: `Effects.PreventNextDamage`, `Effects.PreventNextDamageFromChosenSource(amount, target)`, `Effects.PreventNextDamageFromChosenSource(onPrevented)`, `Effects.PreventAllDamageFromChosenSource(target, gainLifeFromColors)`, `Effects.PreventAllDamageFromChosenColoredSource(target)`, `Effects.DeflectNextDamageFromChosenSource()`.
  - **Prevent-and-react (`onPrevented`)** — instead of a bespoke reaction type, the chosen-source shield runs **any composed effect** when it fires, as a real triggered ability on the stack ("When damage is prevented this way, …", CR-faithful — opponents get priority and can respond). Mechanically: on resolution the shield is created **and** a linked event-based delayed triggered ability (`CreateDelayedTriggerEffect`-style) whose `effect` is `onPrevented`; when the shield prevents an instance it emits an internal `DamagePreventedEvent` that fires only that delayed trigger (matched by id). Inside the trigger the prevented amount is `DynamicAmounts.preventedDamage()` ("that much"/"that many") and the prevented source's controller is `EffectTarget.ControllerOfTriggeringEntity` ("that source's controller") — the same pair Tephraderm uses. So Deflecting Palm's `onPrevented` = `DealDamage(ControllerOfTriggeringEntity, preventedDamage())`; New Way Forward's = `Composite(DealDamage(ControllerOfTriggeringEntity, preventedDamage()), DrawCards(preventedDamage()))`. Because the payoff is a normal stack ability, it may be interactive (targets, replacements) like any other.
- `BecomeCreatureEffect(target, p, t, subtypes, keywords, duration)` — animate non-creature (lands, artifacts).
- `EachPermanentBecomesCopyOfTargetEffect(filter, target)` — Cytoshape-style mass copy.
- `AnimateLandEffect(target, subtypes, keywords, duration)` — land becomes a creature.
- `ExploreEffect(target)` — Explore mechanic (reveal top; land → battlefield, else hand + counter).
- `AttachEquipmentEffect(equip, target)` — attach an Equipment.
- `TapUntapEffect(target, isTap)` — tap or untap. Facade: `Effects.Tap` / `Effects.Untap`.
- `Effects.TapEachTarget()` — "tap up to N target creatures": taps every object chosen as a target.
  Composes `ForEachTargetEffect` over `Effects.Tap(ContextTarget(0))`, so the count lives only on the
  spell's `TargetCreature`/`TargetPermanent` (`count`, `unlimited`, or `dynamicMaxCount`) — never
  duplicated on the effect. For "tap X target creatures" use `dynamicMaxCount = DynamicAmount.XValue`
  on the target (Icy Blast); for a fixed cap use `count = N` (Tidal Surge, Choking Tethers, Eddymurk
  Crab). Do **not** pass a magic `count = 20` to mean "any number" — use `unlimited`/`dynamicMaxCount`.
- `PhaseOutEffect(target = Self)` — phase the target permanent out (Rule 702.26); facade `Effects.PhaseOut(target)`. While phased out it's treated as though it doesn't exist (excluded from `getBattlefield`, so from projection, triggers, combat, targeting, and SBAs) and phases back in before its controller's next untap step. Indirect phasing (attached Auras/Equipment) is handled automatically. Used as the `suffer` branch of a pay-or-phase trigger (Vaporous Djinn: "phases out unless you pay {U}{U}" = `PayOrSufferEffect(PayCost.Mana(...), Effects.PhaseOut())`).
- `MarkExileOnDeathEffect(target)` — replace next "to graveyard" with "to exile".
- `OptionalCostEffect(cost, effect)` — pay cost to trigger an effect.
- `Effects.AnyPlayerMayPay(cost, consequence)` / `Effects.UnlessAnyPlayerPays(cost, effect)` —
  back the single `AnyPlayerMayPayEffect(cost, consequence?, consequenceIfNonePaid?)`, which asks
  each player in APNAP order whether to pay `cost`. The first to pay runs `consequence` and stops
  the loop; if no one pays, `consequenceIfNonePaid` runs. `AnyPlayerMayPay` reads the
  "if a player does, X" direction (Prowling Pangolin); `UnlessAnyPlayerPays` reads the inverse
  "X unless any player pays" direction (Aether Rift: "return it… unless any player pays 5 life").
  Supported costs: `PayCost.Sacrifice` (card selection) and `PayCost.PayLife` (yes/no). The
  surrounding pipeline's stored collections are carried into whichever consequence fires, so the
  consequence can reference cards gathered earlier in the same resolution (e.g. the discarded card,
  via `MoveCollection(from = "discarded", …)`).
- `StoreResultEffect(effect, as)` — stash an effect's result for later reference.
- `StoreCountEffect(effect, as)` — stash a count for later reference.
- `RepeatWhileEffect(condition, effect, maxIterations?)` — run effect repeatedly while condition holds.

### Sequencing & conditional

- `CompositeEffect(effects)` / `Composite(e1, e2, ...)` — run effects in order.
- `ConditionalEffect(condition, ifTrue, ifFalse?)` / `Branch(...)` — conditional branch.
- `IfYouDoEffect(action, reflexive, optional)` — if optional action is taken, run reflexive effect.
- `ReflexiveTriggerEffect(action, reflexive, optional)` — same shape but the reflexive effect goes on the stack.

### Modal & choice

- `ModalEffect.chooseOne { mode(...) }` / `ModalEffect.chooseN(n) { ... }` — modal effect block.
- `ChooseActionEffect(choices)` — player picks from a list of effects.
- `GrantProtectionFromColor(color, target, duration)` — grant protection from a **fixed** color to a target (no player choice); a thin recipe over `GrantKeyword("PROTECTION_FROM_<COLOR>")`. "{W}: Target creature gains protection from red until end of turn." (Crimson Acolyte).
- `ChooseColorThenEffect(whenChosen)` — pick a color, then run a function of that color.
- `Effects.ChooseNumberThen(then, minValue=0, maxValue=16, prompt)` — pick a number in `[minValue, maxValue]`,
  then run `then` once with the chosen number exposed via the effect context as **X**. Atomic effects and filters
  under `then` read it through `ManaValueEqualsX` (`.manaValueEqualsX()`). Compose with `CompositeEffect` for
  multi-step cards (Void: destroy all artifacts/creatures with that mana value, then a target player reveals their
  hand and discards all nonland cards with that mana value).
- `GrantHexproofFromChosenColorEffect(target)` — hexproof from chosen color.
- `GrantProtectionFromChosenColorEffect(target)` — protection from chosen color. Must run inside `ChooseColorThen`; wrap in `ForEachInGroup` for the group case (Akroma's Blessing: "Creatures you control gain protection from the chosen color").
- `ChooseCreatureTypeEffect(...)` — pause for creature-type pick.
- `SelectTargetEffect(...)` — have a player pick from a valid set.
- `SeparatePermanentsIntoPilesEffect(filter, piles)` — divvy into piles (Fact-or-Fiction shape).

> **Authoring rule:** prefer composing primitives over adding parameters to an existing effect. Use `CompositeEffect`
> and the gather/select/move pipeline before writing a new executor.

---

## 5. Effect patterns (`EffectPatterns.*`)

Composed pipelines (`GatherCards → SelectFromCollection → MoveCollection` shapes and similar).

**Library search & reveal**

- `searchLibrary(filter, destination?, tapped?, shuffle?)` — search library, pick matching, move, shuffle.
- `searchMultipleZones(filters, ...)` — search multiple zones in one effect.

**Top-deck manipulation**

- `scry(count)` — look at top N, bottom any, rest on top.
- `surveil(count)` — look at top N, any to graveyard, rest on top.
- `mill(count)` — top N cards into graveyard.
- `lookAtTopAndKeep(count, keepCount)` — Ancestral Memories — keep exactly K to hand.
- `lookAtTopAndReorder(count)` — reorder top N.

**Reveal patterns**

- `revealUntilNonlandDealDamage(target)` — Bonecrusher Giant shape.
- `wheelEffect(players)` — each player shuffles hand into library, draws that many.
- `factOrFiction(...)` — reveal 5, opponent splits into two piles, you choose one.

**Hand manipulation**

- `discardCards(count, target)` — controller-of-target chooses (mandatory).
- `discardRandom(count, target)` — random discards.
- `discardHand(target)` — discard entire hand.
- `eachOpponentDiscards(count, controllerDrawsPerDiscard?)` — Mind Twist-style.
- `eachPlayerDiscardsDraws(controllerBonusDraw?)` — Windfall / Wheel of Fortune.
- `eachPlayerDrawsX(includeController?, includeOpponents?)` — Howling Mine shape.
- `eachPlayerMayDraw(maxCards, lifePerCardNotDrawn?)` — optional group draw with a tax.
- `exileFromHand(count?, target)` — exile N from hand.

**Sacrifice / destroy**

- `sacrifice(filter, count, then)` — sacrifice N, then run effect.
- `sacrificeFor(filter, countName, thenEffect)` — sacrifice variable count, store, then effect.
- `destroyAllPipeline(filter, noRegenerate?, storeDestroyedAs?)` — wrath pipeline with storage.
- `destroyAllAndAttachedPipeline(filter, noRegenerate?)` — wrath + attached.
- `destroyAllSharingTypeWithSacrificed(noRegenerate?)` — destroy all creatures sharing type with a sacrificed creature.

**Creature-type choice**

- `chooseCreatureTypeRevealTop()` — pick a type, reveal until matching.
- `chooseCreatureTypeReturnFromGraveyard(count)` — pick a type, return N from graveyard.
- `chooseCreatureTypeModifyStats(...)` — pick a type, buff matching.
- `chooseCreatureTypeUntap()` — pick a type, untap your matching.
- `chooseCreatureTypeGainControl(duration?)` — pick a type, control matching.
- `becomeChosenTypeAllCreatures(...)` — all creatures become the chosen type.

**Misc mechanic shapes**

- `mayPay(cost, effect)` — optionally pay cost to trigger an effect.
- `mayPayOrElse(cost, ifPaid, ifNotPaid)` — pay-or-else fork.
- `blight(amount, player?)` — Blight X additional cost glue.
- `bolster(amount)` — Bolster N (CR 701.36): controller chooses a creature with the least toughness among
  creatures they control and puts N +1/+1 counters on it. Non-targeting; no-op with no creatures. Composes
  Gather → `FilterCollection(CollectionFilter.LeastToughness)` → `SelectFromCollection(ChooseExactly 1)` →
  `AddCountersToCollection(+1/+1)`. Toughness is read from projected state for the least-toughness comparison.
- `forage(afterEffect?)` — Forage cost; choose card-from-hand to play.
- `loot(draw?, discard?)` — "draw N, discard M" loop.
- `rummage(count?)` — discard then draw.
- `connive(target?)` — draw 1, discard 1, then put a +1/+1 counter on `target` if the discard was a nonland (CR 702.166). Also exposed as `Effects.Connive(target)`.
- `readTheRunes()` — "draw X cards; for each, discard a card unless you sacrifice a permanent." Composes `RepeatDynamicTimesEffect(XValue, ChooseActionEffect(...))` with feasibility guards. Exposed as `Effects.ReadTheRunes()`.
- `eachOpponentMayPutFromHand(filter?)` — each opponent may dump a matching card.
- `putFromHand(filter?, count?, entersTapped?)` — you may put N from hand onto battlefield.
- `incubate(n)` — make an Incubator token with N counters.
- `returnLinkedExile(underOwnersControl?)` — bring back linked exile pile.
- `takeFromLinkedExile()` — pull one card from linked exile.
- `shuffleGraveyardIntoLibrary(target?)` — Elixir of Immortality shape.
- `reflexiveTrigger(action, whenYouDo, optional?)` — optional action; if taken, queue a reflexive trigger.

**Group bulk operations** (one effect applied to every permanent matching a `GroupFilter`)

- `modifyStatsForAll(power, toughness, filter, duration?)` — give every match +X/+Y (`Int` or `DynamicAmount`).
- `doublePowerAndToughnessForAll(filter, duration?)` — double each match's power and toughness. Resolves to a fixed +P/+T modification read per-entity from projected state via `DynamicAmount.EntityProperty(EntityReference.IterationEntity, …)`, so the bonus locks in at resolution (no re-doubling) and negative power doubles correctly. Roar of Endless Song, Unnatural Growth.
- `grantKeywordToAll(keyword, filter, duration?)` / `removeKeywordFromAll(...)`; `tapAll(filter)` / `untapGroup(filter?)`; `dealDamageToAll(amount, filter)`; `destroyAll(filter, noRegenerate?)`; `gainControlOfGroup(filter?, duration?)`.

---

## 6. Targets

### Resolution-time (`EffectTarget`)

- `EffectTarget.ContextTarget(i)` — i-th cast-time target.
- `EffectTarget.Controller` — controller of the source ability.
- `EffectTarget.Self` — the source permanent.
- `EffectTarget.TriggeringEntity` — the entity that caused the trigger to fire.
- `EffectTarget.PlayerRef(...)` — a player slot: `You`, `Each`, `Opponent`, etc.
- `EffectTarget.ContextProperty(key)` — value plumbed into `EffectContext` (damage amount, life gained, blight
  amount, …).
- `EnchantedCreature` / `EquippedCreature` — resolve via `AttachedToComponent`; requires the state-aware
  `resolveTarget(state, target)` overload.
- `EnchantedPermanent` — same `AttachedToComponent` resolution as `EnchantedCreature`, but type-agnostic; use for
  Auras that enchant non-creature permanents (e.g. Wellspring enchants a land: "gain control of enchanted land").

### Cast-time (`Targets.*` / `TargetRequirement`)

- `Targets.Any` — any creature, player, or planeswalker.
- `Targets.AnyOtherThanEnchantedCreature` — any target except the creature the source Aura/Equipment
  is attached to. Desugars to `TargetOther(AnyTarget(), excludeAttachedCreature = true)`; for Aura/Equipment
  abilities worded "enchanted/equipped creature deals damage … to **any other target**" (e.g. Pain for All),
  where the dealer is the attached creature, not the ability's source permanent.
- `Targets.Creature` — any creature.
- `Targets.Player` — any player.
- `Targets.Planeswalker` — any planeswalker.
- `Targets.Permanent` — any permanent.
- `Targets.NonlandPermanent` — any nonland permanent.
- `Targets.Artifact` — any artifact.
- `Targets.Enchantment` — any enchantment.
- `Targets.Land` — any land.
- `Targets.BasicLand` — any basic land.
- `Targets.Spell` — any spell on the stack.
- `Targets.Card` — any card in any zone (e.g. graveyard).
- `Targets.CreatureOrPlaneswalker` — combined.
- `Targets.TappedCreature` / `UntappedCreature` — state-restricted.
- `Targets.InstantOrSorcery` — instant-or-sorcery card.

**Chained predicates** — `.youControl()`, `.controlledByOpponent()`, `.opponent()`, `.withSubtype(...)`,
`.withKeyword(...)`, `.ofColor(...)`, `.tapped()`, `.untapped()`, `.power(n)`, `.minPower(n)`, `.maxPower(n)`; plus
`TargetFilter.excludeSelf` to exclude the source.

### Named multi-target binding

```kotlin
spell {
    val creature = target("creature", Targets.Creature)
    val player = target("player", Targets.Player)
    effect = Effects.Composite(
        Effects.Destroy(creature),
        Effects.DealDamage(3, player),
    )
}
```

For modal spells, prefer the explicit `targetPlayerControls(target)` DSL form; per-mode targets route via
`modeTargetsOrdered`.

### Target count

Every `TargetRequirement` carries count semantics (defaults shown):

- `count = 1` — maximum number of targets.
- `minCount = count` — minimum; set below `count` for "one or two target creatures".
- `optional = false` — when `true`, minimum becomes 0 ("up to N target ...").
- `unlimited = false` — when `true`, **"any number of target ..."** — no upper cap. The practical
  maximum is the number of legal targets, which the engine sends to the client; validation imposes
  no limit and the minimum is 0. Use this instead of a large placeholder `count` (Phyrexian Purge,
  Kaboom, Weaver of Lies). For "**X** target creatures" use `dynamicMaxCount = DynamicAmount.XValue`
  instead — that clamps the count to the chosen X.
- `dynamicMaxCount: DynamicAmount?` — evaluated when the spell/ability hits the stack; the resolved
  value becomes the max ("up to X target creatures", X = board state or chosen X).
- `sameController = false` — on `TargetObject` / `TargetCreature(...)`; when `true` and the requirement
  picks more than one target, every chosen target must share a controller ("**two target creatures
  controlled by the same player**"). Enforced cross-target by `TargetValidator` at cast time using
  projected control; a no-op for single-target requirements. E.g.
  `TargetCreature(count = 2, sameController = true)` (Barrin's Spite).
- `sameOwner = false` — on `TargetObject`; when `true` and the requirement picks more than one target,
  every chosen **card** target must share an owner ("**exile up to two target cards from a single
  graveyard**"). Enforced cross-target both at cast time (`TargetValidator`) and on triggered-ability
  target decisions (`DecisionValidators` reads each card's `OwnerComponent`); a no-op for single-target
  requirements and for non-card targets. E.g.
  `TargetObject(count = 2, optional = true, filter = TargetFilter.CardInGraveyard, sameOwner = true)`
  (Arashin Sunshield).

---

## 7. Filters & predicates

### `GameObjectFilter` — for searches, sacrifice, group effects

- `Filters.AnyCard` — any card.
- `Filters.Creature` — any creature card.
- `Filters.Land` — any land card.
- `Filters.BasicLand` — any basic land.
- `Filters.PlainsCard` / `IslandCard` / `SwampCard` / `MountainCard` / `ForestCard` — specific basics.
- `Filters.Instant` — instant card.
- `Filters.Sorcery` — sorcery card.
- `Filters.Permanent` — permanent card.
- `Filters.NonlandPermanent` — nonland permanent.
- `Filters.WithSubtype(subtype)` — card of a given subtype.
- `GameObjectFilter.Multicolored` — multicolored card (two or more colors; `CardPredicate.IsMulticolored`).

**Chained predicates**

- `.youControl()` / `.controlledByOpponent()` — control predicate.
- `.controlledByActivePlayer()` — controlled by the player whose turn it is (`ControllerPredicate.ControlledByActivePlayer`).
  Pairs with `Triggers.EachUpkeep` for "at the beginning of each player's upkeep, do X to permanents that player
  controls" (the upkeep player is the active player — Temporal Distortion).
- `.targetPlayerControls(target)` — controlled by a referenced player. Resolves `EffectTarget`
  bindings/context targets, plus `EffectTarget.ControllerOfTriggeringEntity` (controller of the
  entity that fired the trigger — e.g. Tectonic Instability "tap all lands its controller controls").
- `.withSubtype(s)` / `.withKeyword(k)` — type/ability predicate.
- `.ofColor(c)` / `.ofColors(set)` — color predicate.
- `.withColor(c)` / `.withAnyColor(c…)` / `.notColor(c)` — fixed-color predicates (`CardPredicate.HasColor`/`NotColor`).
- `.withChosenColor()` — `CardPredicate.HasChosenColor`: matches the color chosen during the current
  effect's resolution (read from `EffectContext.chosenColor`, set by `Effects.ChooseColorThen`). Use with
  `AggregateBattlefield(Player.Each, …)` for "for each permanent of that color" (Coalition Dragon cycle).
- `.sharingCreatureTypeWith(entity)` — `CardPredicate.SharesCreatureTypeWith(entity)`: shares ≥1 (projected)
  creature subtype with a referenced entity. `entity` may be `EntityReference.AffectedEntity`, which resolves
  to the creature a continuous effect is being applied to during projection — combine with
  `AggregateBattlefield(Player.Each, GameObjectFilter.Creature.sharingCreatureTypeWith(EntityReference.AffectedEntity), excludeSelf = true)`
  for "+X/+X for each OTHER creature that shares a creature type with it" (Alpha Status). In a granted
  context `excludeSelf` excludes the affected (enchanted) creature, not the granting source.
- `.sharingColorWith(entity)` — `CardPredicate.SharesColorWith(entity)`: shares ≥1 (projected) color with
  a referenced entity (e.g. `EntityReference.Triggering`). Mirror of `.sharingCreatureTypeWith(entity)`.
  Colorless entities share no color (never match). Used by Spreading Plague ("destroy all other creatures
  that share a color with it") — pair with `Effects.DestroyAll(filter, excludeTriggering = true)` so the
  triggering creature itself is spared.
- `.named(name)` — `CardPredicate.NameEquals`: matches a fixed card name.
- `.namedFromVariable(variableName)` — `CardPredicate.NameEqualsChosen`: matches the card name stored in
  `chosenValues[variableName]` (case-insensitive). Set the name with `Effects.ChooseCardName` (player names it)
  or `Effects.StoreCardName` (captured from a chosen card). Fails closed in static/projection contexts. Used by
  the "name a card … cards with that name" family (Desperate Research, Lobotomy).
- `.power(n)` / `.minPower(n)` / `.maxPower(n)` — P/T comparator.
- `.manaValue(n)` / `.manaValueAtMost(n)` / `.manaValueAtLeast(n)` — mana-value comparator.
- `.manaValueAtMostX()` — mana value ≤ the X chosen for the source spell/ability.
- `.manaValueEqualsX()` — mana value **exactly equal** to the number chosen for the source spell/ability
  (set by `Effects.ChooseNumberThen`; resolution-time only — matches nothing without a chosen number). Used by Void.
- `.manaValueAtMostEntity(ref)` — mana value ≤ a referenced entity's mana value (e.g. Kodama of the East Tree).
- `.manaValueAtMostEntityManaSpent(ref)` — mana value ≤ the mana **actually spent** to cast a referenced
  entity. Reads the live `SpellOnStackComponent` buckets while the entity is still a spell, or the
  `CastRecordComponent` snapshot once it has resolved onto the battlefield (0 if it was never cast).
  Used by Edge of Eternities warp payoffs like Astelli Reclaimer ("…mana value X or less…, where X is the
  amount of mana spent to cast this creature") — X is 5 for `{3}{W}{W}`, 3 for warp `{2}{W}`, 0 for free.
- `.manaValueIsOdd()` / `.manaValueIsEven()` — mana-value parity (zero is even). Pair with modal
  spells whose modes ask the caster to choose a parity (e.g. *Mutinous Massacre*).
- `.tapped()` / `.untapped()` — tap state.
- `.nontoken()` / `.token()` — token vs printed.
- `.faceDown()` — face-down state.
- `.card(filter)` — defer to a card-shape filter for off-battlefield checks.

**Explicit constructor**:
`GameObjectFilter(cardPredicates, controllerPredicate, colorPredicate, keywordPredicate, powerToughnessPredicate, subtypePredicate)`.

### `GroupFilter` — static-ability scope

- `GroupFilter.CreaturesYouControl` — your creatures.
- `GroupFilter.CreaturesOpponentControls` — their creatures.
- `GroupFilter.AllCreatures` — every creature on the battlefield.
- `GroupFilter.All(filter)` — custom group.
- Chained: `.withColor`, `.withoutColor`, `.withKeyword`, `.withoutKeyword`, `.withSubtype`, `.withoutSubtype`,
  `.minPower`, `.maxPower`, `.power`.

### Stack-object predicates

These `CardPredicate`s evaluate against entities in the `Zone.STACK` (spells and activated/triggered
abilities on the stack). They are handled in the evaluator before the `CardComponent` check, so they
work for abilities-on-stack (which carry no `CardComponent`).

- `CardPredicate.IsActivatedOrTriggeredAbility` — true for activated/triggered abilities on the stack
  (Stifle).
- `CardPredicate.IsTriggeredAbility` — triggered abilities only (excludes activated abilities and
  spells).
- `CardPredicate.IsActivatedAbility` — activated abilities only (excludes triggered abilities and
  spells). Mana abilities never use the stack, so they're never matched. Exposed as the
  `Targets.ActivatedAbility` target requirement (Bind: "Counter target activated ability").
- `CardPredicate.TargetsMatching(subfilter)` — true when the stack object's `TargetsComponent`
  includes at least one chosen target matching `subfilter`. Player targets are skipped. The
  subfilter inherits the outer `PredicateContext`, so `Land.youControl()` inside the subfilter
  resolves against the outer chooser. Used by Teferi's Response.
- `CardPredicate.HasNonManaActivatedAbility` — matches a permanent whose printed activated abilities
  include at least one that isn't a mana ability and isn't a loyalty ability (battlefield-activatable).
  Backed by the precomputed `CardComponent.hasNonManaActivatedAbility` flag (set at entity creation from
  `CardDefinition.hasNonManaActivatedAbility`), so abilities granted by other continuous effects are not
  counted. Used by Tsabo's Web ("each land with an activated ability that isn't a mana ability …").

### `StatePredicate` — battlefield state checks

- `IsTapped` — currently tapped.
- `IsUntapped` — currently untapped.
- `IsAttacking` — declared as attacker this combat.
- `IsBlocking` — declared as blocker this combat.
- `InSameBandAsSource` (filter builder `inSameBandAsSource()`) — source-relative (CR 702.22):
  matches the effect's source creature itself and any creature sharing its combat band id.
  Resolves against `PredicateContext.sourceId`, so it only matches while that source is attacking
  (band membership exists only during combat). Used as the recipient filter of Camel's
  "prevent all damage Deserts would deal to this creature and to creatures banded with this
  creature". Note: it's only evaluated where the context carries a source entity — currently the
  recipient filter of a `PreventDamage` replacement (see §15); it's inert in group/projection,
  untap, and trigger-gating contexts.
- `IsFaceDown` — currently face-down.
- `HasCounter(type)` — has at least one counter of `type`.
- `IsWarpExiled` (filter builder `warpExiled()`) — card in exile via warp's
  end-of-turn delayed trigger (CR 702.185b).
- `WasCastForWarp` (filter builder `castForWarp()`) — battlefield permanent that
  was cast for its warp cost (CR 702.185). Pair with
  `Conditions.TargetMatchesFilter(GameObjectFilter.Creature.castForWarp(), …)` to
  branch on whether a target was warp-cast (e.g., Full Bore).

### `AffectsFilter` — static-ability target shapes

- `OtherCreaturesWithSubtype` — lord scope (other creatures of subtype).
- `CreaturesWithCounter` — creatures with at least one counter (Aurification).

> **Load-bearing rule:** filtering battlefield permanents by type/subtype/color/keyword/P-T MUST use
`predicateEvaluator.matchesWithProjection(state, projected, ...)`. Use `projected.isCreature(entityId)` rather than
`cardComponent.typeLine.isCreature`. Non-battlefield zones may read base state.

---

## 8. Triggered abilities (`Triggers.*`)

`triggeredAbility { trigger; effect; target?; triggerCondition?; optional?; checkOnNextState?; dealsDamageBeforeResolve?; controlledByTriggeringEntityController? }`.

### Zone change

Named sugar for the common cases; reach for `entersBattlefield(...)` / `leavesBattlefield(...)`
for any other (filter, binding, to/excludeTo) combination.

**Enters the battlefield**

- `EntersBattlefield` — SELF, no filter. ("When this permanent enters.")
- `OtherCreatureEnters` — OTHER binding, filter = `Creature.youControl()`.
- `LandYouControlEnters` — landfall: OTHER binding, filter = `Land.youControl()`.
- `entersBattlefield(filter, binding)` — factory. Covers face-down filters,
  ANY-binding tribal scopes, permanent-you-control scopes, enchantment-enters scopes (Eerie), etc.

**Leaves / dies**

- `LeavesBattlefield` — SELF, any destination.
- `Dies` — SELF, battlefield → graveyard.
- `AnyCreatureDies` — ANY binding, filter = `Creature`.
- `YourCreatureDies` — ANY binding, filter = `Creature.youControl()`.
- `PutIntoGraveyardFromBattlefield` — SELF, same event shape as `Dies`; rename
  clarifies non-creature intent (artifact / enchantment going to yard).
- `leavesBattlefield(filter, to?, excludeTo?, binding)` — factory. `to = GRAVEYARD`
  gives a "dies" variant scoped beyond the named constants (other tribal deaths,
  any-controller deaths); `excludeTo = GRAVEYARD` gives "leaves without dying"
  (Three Tree Scribe shape); leaving both null gives "leaves to any zone."

### Combat

Named sugar for the common cases; reach for `attacks(...)` / `blocks(...)` /
`becomesBlocked(...)` for any other combination, and use the [AttackPredicate]
sealed set for attack-time facts beyond the basics.

**Attacks (per-attacker `AttackEvent`)**

- `Attacks` — SELF, no filter. ("When this creature attacks.")
- `attacks(filter?, requires?, binding?)` — factory. Covers ANY-binding scopes,
  type-filtered scopes (creature-you-control, nontoken-creature-you-control),
  and attack-time predicates (alone, future Battalion-style count gates).

**Attacks (player-level)**

- `YouAttack` — when you declare attackers (player-level, ANY binding).
- `YouAttackWithFilter(filter)` — when you attack with ≥1 matching attacker.
- `CreaturesAttackYou` — defender side; fires once per `AttackersDeclaredEvent`,
  not per attacker. Excludes creatures attacking a planeswalker you control
  (CR 509.1b). Pair with `DynamicAmounts.creaturesAttackingYou()` for
  attacker-count payoffs (e.g., Orim's Prayer).

**Blocks**

- `Blocks` — SELF, no filter.
- `BecomesBlocked` — SELF, no filter.
- `blocks(filter?, binding?, attackerFilter?)` — factory. `filter` constrains the
  blocker (ANY binding). `attackerFilter` constrains the blocked attacker — requires
  SELF binding for "whenever this creature blocks a [filter]" (Skystinger);
  combining it with ANY is rejected (the ANY detector branch ignores `attackerFilter`).
  `triggeringEntityId` is set to the blocked attacker in that case.
- `becomesBlocked(filter?, binding?)` — factory. Replaces the old
  `CreatureYouControlBecomesBlocked` and `FilteredBecomesBlocked(filter)`.
- `BlocksOrBecomesBlockedBy(filter)` — either direction, partner-filtered;
  sole consumer of `BlocksOrBecomesBlockedByEvent`. Prefer `blocks(attackerFilter=...)`
  when only the blocking direction should fire.

**`AttackPredicate`** — extensible "facts about an attack declaration."
Adding a new attack-time mechanic is one new sealed-case + one matcher branch
— `AttackEvent` does not grow a new field per axis.

- `AttackPredicate.Alone` — the attacker is the only declared attacker this
  combat (`attacker count == 1`). Replaces the old `alone: Boolean` axis.
- `AttackPredicate.AttackerCountAtLeast(n)` — at least N creatures total were
  declared as attackers (counting the trigger's attacker). Battalion shape:
  `attacks(requires = setOf(AttackerCountAtLeast(3)))` on a `SELF` binding.

Examples:

```kotlin
// "Whenever this creature attacks alone"
Triggers.attacks(requires = setOf(AttackPredicate.Alone))

// "Whenever a nontoken creature you control attacks"
Triggers.attacks(
    filter = GameObjectFilter.Creature.youControl().nontoken(),
    binding = TriggerBinding.ANY,
)

// "Whenever a Beast becomes blocked"
Triggers.becomesBlocked(
    filter = GameObjectFilter.Creature.withSubtype("Beast"),
    binding = TriggerBinding.ANY,
)

// "Whenever this creature blocks a creature with flying" (Skystinger)
Triggers.blocks(attackerFilter = GameObjectFilter.Creature.withKeyword(Keyword.FLYING))
```

`Triggers.BecomesBlocked` (SELF, **unfiltered**) fires **once** when the creature becomes
blocked, regardless of how many creatures block it, with `triggeringEntityId` = the source —
so `DynamicAmounts.numberOfBlockers()` reads this creature's blocker count (Rampage). The
**filtered** SELF form `becomesBlocked(filter = …)` instead fires once per matching blocker,
with `triggeringEntityId` = that blocker (Flanking gives each blocker -1/-1).

### Damage

Named sugar for the common cases; reach for the factories for any other combination of axes.

- `DealsDamage` — source deals any damage (SELF binding).
- `DealsCombatDamageToPlayer` — source deals combat damage to a player (SELF binding).
- `DealsCombatDamageToCreature` — source deals combat damage to a creature (SELF binding).
- `TakesDamage` — source is dealt damage by any source (SELF binding).
- `CreatureDealtDamageByThisDies` — Etali / Sengir / Soul Collector shape; only consumer of `CreatureDealtDamageBySourceDiesEvent`.

**Factories** (axes: `damageType` × `recipient` × `sourceFilter` × `binding` for outgoing; `source` × `binding` for incoming):

- `dealsDamage(damageType?, recipient?, sourceFilter?, binding?)` — outgoing-damage trigger. Pick `DamageType.{Any,Combat,NonCombat}`, `RecipientFilter.{Any,AnyPlayer,AnyPlayerOrPlaneswalker,AnyCreature,…}`, an optional source `GameObjectFilter`, and `TriggerBinding.{SELF,ANY,ATTACHED}`. Covers "deals combat damage to a player or planeswalker", "creature you control deals combat damage to a player" (`binding = ANY` + `sourceFilter = Creature.youControl()`), "nontoken creature you control deals…" (`.nontoken()`), and "enchanted creature deals damage" (`binding = ATTACHED`).
- `takesDamage(source?, binding?)` — incoming-damage trigger. Pick `SourceFilter.{Any,Creature,Spell,Combat,NonCombat,HasColor(c),…}` and `TriggerBinding.{SELF,ATTACHED}`. Covers "damaged by a creature/spell" and "enchanted creature is dealt damage" (`binding = ATTACHED`, Aurification / Frozen Solid shape).
- `becomesTapped(binding?, filter?)` — "becomes tapped" trigger. `BecomesTapped` is the SELF constant; pass `binding = TriggerBinding.ANY` with an optional `filter: GameObjectFilter` for "whenever a [filter] becomes tapped" (e.g. `GameObjectFilter.CreatureOrLand` — Temporal Distortion). The filter is matched against the tapped permanent via projected state.

### Phase & turn

Named sugar for the common `(step, player)` cases; reach for `phase(step, player?, binding?)`
for anything else (the ATTACHED-binding aura shapes, custom step/player combinations).

- `YourUpkeep` — start of your upkeep.
- `YourDrawStep` — start of your draw step.
- `EachUpkeep` — every upkeep.
- `EachOpponentUpkeep` — at each opponent's upkeep.
- `YourEndStep` — beginning of your end step.
- `EachEndStep` — beginning of each end step.
- `BeginCombat` — start of combat on your turn.
- `EachCombat` — beginning of each combat (any player's turn).
- `FirstMainPhase` — start of pre-combat main.
- `YourPostcombatMain` — start of post-combat main.

**Factory** — `phase(step, player = Player.You, binding = TriggerBinding.ANY)`.

### Aura / equipment

No named constants for the "enchanted/equipped creature does X" shapes — they all collapse to
the existing event factories with `binding = TriggerBinding.ATTACHED`. Examples (all card uses
in the repo today):

- *Enchanted creature dies* (Demonic Vigor):
  `Triggers.leavesBattlefield(to = Zone.GRAVEYARD, binding = TriggerBinding.ATTACHED)`
- *Enchanted/equipped creature leaves the battlefield* (Curator's Ward):
  `Triggers.leavesBattlefield(binding = TriggerBinding.ATTACHED)`
- *Enchanted/equipped creature attacks* (Extra Arms, Heart-Piercer Bow, Ordeal of Nylea,
  Chorale of the Void, Atomic Microsizer, Sorcerer Role token):
  `Triggers.attacks(binding = TriggerBinding.ATTACHED)`
- *Enchanted permanent becomes tapped* (Uncontrolled Infestation, Cryoshatter):
  `Triggers.becomesTapped(binding = TriggerBinding.ATTACHED)`
- *Enchanted creature is turned face up* (Fatal Mutation):
  `Triggers.turnedFaceUp(binding = TriggerBinding.ATTACHED)`
- *At the beginning of enchanted creature's controller's `<step>`* (Custody Battle,
  Lingering Death): `Triggers.phase(step, binding = TriggerBinding.ATTACHED)`
- *Enchanted-creature damage triggers* — damage factories already support binding:
  `Triggers.dealsDamage(binding = TriggerBinding.ATTACHED)` (any damage),
  `Triggers.dealsDamage(damageType = Combat, recipient = AnyPlayer, binding = TriggerBinding.ATTACHED)`,
  `Triggers.takesDamage(binding = TriggerBinding.ATTACHED)` (Aurification / Frozen Solid).

### Cards & draws

- `YouDraw` — when you draw a card.
- `RevealCreatureFromDraw` — Hatching Plans-style top-card reveal.
- `RevealCardFromDraw` — generic reveal-from-draw trigger.
- `CardsPutIntoYourGraveyard(filter?)` — when matching cards enter your yard.
- `PermanentCardsPutIntoYourGraveyard` — only permanent cards.
- `CreaturesPutIntoGraveyardFromLibrary` — mill-trigger shape.
- `CardsLeaveYourGraveyard(filter?)` — batching trigger; fires once per event batch when one
  or more matching cards **leave** your graveyard (cast/exiled/reanimated/returned to hand,
  etc.), regardless of how many or where they went. For the common "leave your graveyard
  **during your turn**" wording, add `triggerCondition = Conditions.IsYourTurn`; for "this
  ability triggers only once each turn", add `oncePerTurn = true`. (Attuned Hunter, Kishla
  Skimmer, Kheru Goldkeeper.)

### Discard

Fires once per card discarded — a single resolution that discards N cards fires the
trigger N times (mirrors how `YouDraw` handles multi-card draws). The engine emits
one aggregate `CardsDiscardedEvent` per resolution and fans it out in the detector.
`Player.TriggeringPlayer` resolves to the discarding player inside the effect.

- `AnyOpponentDiscards` — whenever an opponent discards a card. (Entropic Battlecruiser.)
- `YouDiscard` — whenever you discard a card.

**Factory** — `discards(player?, cardFilter?)` — generic shape. `player = Player.Each`
matches any player; `cardFilter` narrows the fan-out to matching cards, so a batch that
discards a creature and two lands fires a `cardFilter = Creature` trigger once, not three
times. The cardFilter is evaluated against the **post-discard zone** (the cards are already
in the graveyard when the trigger matches) — safe for type/subtype/color predicates,
but a filter that depends on hand-specific state would read the wrong zone.

### Spell casting

Named sugar for the common type-primitive cases; reach for `youCastSpell(...)` plus a
`SpellCastPredicate` set for anything from-zone / kicked / mana-source-tagged.

- `YouCastSpell` — any spell you cast.
- `YouCastCreature` — any creature spell you cast.
- `YouCastNoncreature` — non-creature spells you cast.
- `YouCastInstantOrSorcery` — instant/sorcery you cast.
- `YouCastEnchantment` — any enchantment you cast.
- `YouCastHistoric` — artifact / legendary / Saga.
- `YouCastSubtype(subtype)` — tribal helper: spell with matching subtype.
- `AnySpellOrAbilityOnStack` — any object hits the stack.
- `OpponentActivatesAbility` — an opponent activates an ability that **isn't a mana ability** (CR 605/606). Mana
  abilities don't use the stack, so they never fire this; loyalty abilities (which are activated abilities) do. Pair
  with `Effects.DealDamage(n, EffectTarget.PlayerRef(Player.TriggeringPlayer))` to punish the activator (Flamescroll
  Celebrant). Backed by `GameEvent.AbilityActivatedEvent(player)`.

**Other casters.** The same shape, scoped to a different caster via the runtime
`Player.Each` / `Player.Opponent` matching on `SpellCastEvent`. Bind the payoff to the
caster with `EffectTarget.PlayerRef(Player.TriggeringPlayer)`.

- `AnyPlayerCastsSpell` — any player (including you) casts a spell.
- `OpponentCastsSpell` — an opponent casts a spell.
- `AnyPlayerChoosesTargets` — any player casts a spell, activates an ability, or puts a triggered ability on the stack with ≥1 target (fires once per object via `GameEvent.TargetsChosenEvent`). The triggering entity is that spell/ability, so the payoff can read/change its targets (Psychic Battle).
- `anyPlayerCasts(spellFilter?, requires?)` — factory; e.g. `anyPlayerCasts(GameObjectFilter.Creature)`
  for "whenever a player casts a creature spell" (Pure Reflection).
- `opponentCasts(spellFilter?, requires?)` — factory; e.g. `opponentCasts(GameObjectFilter.Multicolored)`
  for "whenever an opponent casts a multicolored spell" (Rewards of Diversity).

**Factory** — `youCastSpell(spellFilter?, requires: Set<SpellCastPredicate>)`. The
`requires` set is conjunctive — every predicate must hold for the trigger to fire.

**`SpellCastPredicate`** — extensible "facts about a cast." Adding a new cast-time mechanic
(was-copied, was-overloaded, paid-additional-life-cost, …) is one new sealed-case plus one
matcher branch — `SpellCastEvent` does not grow a new field per axis.

- `SpellCastPredicate.CastFromZone(zone)` — spell was cast from this zone. Used for Sunbird's
  Invocation (`Zone.HAND`), Goliath Daydreamer's instant/sorcery-from-hand trigger,
  Wildsear's enchantment-from-hand cascade.
- `SpellCastPredicate.WasKicked` — spell was cast with kicker (CR 702.32). Used for
  Hallar / Bloodstone Goblin.
- `SpellCastPredicate.PaidWithManaFromSubtype(subtype)` — mana from a permanent of this
  subtype was spent on the cast. Resolves Treasure today (Rain of Riches, Alchemist's
  Talent); engine matcher accepts other token subtypes as the shape, but only Treasure
  actually fires until the mana-pool tracker generalizes beyond its current Treasure-only
  boolean.
- `SpellCastPredicate.IsModal` — spell was cast with at least one chosen mode (rules
  700.2). Matches `SpellCastEvent.chosenModesCount > 0`, where the count is the size of
  `SpellOnStackComponent.chosenModes` (so Spree picking the same mode twice counts as
  two). Used by Riku of Many Paths: "Whenever you cast a modal spell, …".

Examples:

```kotlin
// "Whenever you cast a spell from your hand"
Triggers.youCastSpell(requires = setOf(SpellCastPredicate.CastFromZone(Zone.HAND)))

// "Whenever you cast an instant or sorcery from your hand"
Triggers.youCastSpell(
    spellFilter = GameObjectFilter.InstantOrSorcery,
    requires = setOf(SpellCastPredicate.CastFromZone(Zone.HAND)),
)

// "Whenever you cast a kicked spell"
Triggers.youCastSpell(requires = setOf(SpellCastPredicate.WasKicked))

// "Whenever you cast a spell using mana from a Treasure"
Triggers.youCastSpell(
    requires = setOf(SpellCastPredicate.PaidWithManaFromSubtype(Subtype.TREASURE)),
)

// "Whenever you cast a modal spell" (Riku of Many Paths)
Triggers.youCastSpell(requires = setOf(SpellCastPredicate.IsModal))

// "Whenever you cast a noncreature or Otter spell"
Triggers.youCastSpell(
    spellFilter = GameObjectFilter.Noncreature or
                  GameObjectFilter.Any.withSubtype(Subtype("Otter")),
)
```

### State change & misc

- `TurnedFaceUp` — source turns face up. Use `turnedFaceUp(binding)` for the ATTACHED-binding aura variant (Fatal Mutation).
- `CreatureTurnedFaceUp(player?)` — when a creature you control turns face up.
- `GainControlOfSelf` — you gain control of source.
- `BecomesTarget(filter?)` — source becomes target of spell/ability.
- `CreatureYouControlBecomesTargetByOpponent(filter?)` — your creature gets targeted by opponent.
- `Transforms` — source transforms (either direction).
- `TransformsToFront` — to front face.
- `TransformsToBack` — to back face.
- `YouCycleThis` — you cycle source.
- `AnyPlayerCycles` — anyone cycles.
- `AnyPlayerTapsLandForMana` — whenever any player taps a land for mana. Use
  `landTappedForMana(player, landFilter, binding)` for "an opponent"/"you" variants or a land-type
  restriction. Fires on the manual mana-ability path only (auto-pay adds mana via the solver without
  emitting the event). Backs the "whenever a player taps a land for mana" family (Mana Flare, Heartbeat
  of Spring); the inline-static cards (Overabundance, Pulse) use the mana statics in §9 instead.
- `YouCommitCrime` — MKM crime mechanic.
- `YouGiveAGift` — Gift mechanic.
- `Valiant` — Bloomburrow Valiant trigger.
- `RoomFullyUnlocked` — Rooms — both doors unlocked.
- `OnDoorUnlocked` — single Room door unlocked.

### Life

- `YouGainLife` — you gain any life.
- `AnyPlayerGainsLife` — anyone gains life.
- `YouLoseLife` — you lose any life.
- `AnyPlayerLosesLife` — anyone loses life.
- `YouGainOrLoseLife` — combined life-change.

### The Ring

- `RingTemptsYou` — whenever the Ring tempts you (CR 701.52d). Paired with `Effects.TheRingTemptsYou()`.

### Scry

- `WheneverYouScry` — fires once per scry resolution (CR 701.18), after the cards have
  been placed on top/bottom. Pair with `DynamicAmount.ContextProperty(ContextPropertyKey.TRIGGER_SCRY_COUNT)`
  for "for each card looked at" payoffs (Celeborn the Wise, Elrond Master of Healing).
  Automatically emitted by `EffectPatterns.scry(N)`; no card has to opt in.

### Sacrifice & counters

- `YouSacrificeOneOrMore(filter?)` — you sac ≥1 matching.
- `Sacrificed` — source is sacrificed.
- `PlusOneCountersPlacedOnYourCreature` — Hardened Scales shape (+1/+1 only).
- `countersPlacedOn(filter = Creature.youControl(), counterType = Counters.ANY, firstTimeEachTurn = true)`
  — fires when counters of any type (`Counters.ANY` wildcard) land on a matching permanent;
  `firstTimeEachTurn` gates it to the first counter placement on *that* permanent this turn
  (engine-tracked via `ReceivedCountersThisTurnComponent`). Triggering permanent is
  `EffectTarget.TriggeringEntity`. Stalwart Successor shape.
- `OneOrMorePermanentsEnter(filter?)` — batched ETB trigger.
- `OneOrMoreLeaveWithoutDying(...)` — batched LTB-without-dying.

### Conditional

- `NthSpellCast(n, player?)` — fires on the Nth spell cast.
- `Expend(threshold)` — Expend N (CLB mechanic).

### Delayed & granted triggers

- `DelayedTriggeredAbility` — registered now, fires at a specific future step (Astral Slide).
- `Effects.GrantTriggeredAbilityEffect` — grant a triggered ability for a duration; `GrantTriggeredAbilityExecutor` uses
  projected state and supports leaves-battlefield-to-zone triggers.
- `CreateDelayedTriggerEffect(step, effect, fireOnlyOnControllersTurn, timing, …)` —
  the data-side facade. Two orthogonal axes control *when* the trigger may first fire:
  - `fireOnlyOnControllersTurn` — gates *whose* turn: only matches when the active player equals
    the controller.
  - `timing: DelayedTriggerTiming` — gates *which* turn is the earliest eligible one:
    - `CURRENT_TURN_OR_LATER` (default) — no turn floor; the next upcoming occurrence of `step`,
      which may be the current turn. (Astral Slide exile-until-end-step.)
    - `NEXT_END_STEP` — "at the beginning of your next end step": defers to next turn only if the
      controller's current-turn end step has already begun (END/CLEANUP); otherwise the current
      turn's end step qualifies. (Dragonhawk, Fate's Tempest.)
    - `NEXT_TURN` — stricter "on your next turn"-style timing: the current turn never qualifies
      regardless of step. Pair with `fireOnlyOnControllersTurn = true` to land on the controller's
      upcoming own turn rather than an intervening opponent turn. (Kav Landseeker.)
- **Event-based delayed triggers** — pass `trigger = <TriggerSpec>` (instead of `step`) and the
  delayed ability fires whenever a matching *event* occurs, staying resident until `expiry`
  (`DelayedTriggerExpiry.EndOfTurn`) removes it. `watchedTarget` scopes it to one entity (e.g.
  "when **that** creature deals combat damage / dies this turn" — Long River Lurker, Deflecting
  Palm). Matching delegates to the same `TriggerMatcher` the battlefield triggers use, so supported
  events include `DealsDamageEvent`, `ZoneChangeEvent`, the internal `DamagePreventedEvent`, and the
  attack-declaration events `YouAttackEvent` / `AttackEvent`.
  - `fireOnce = true` makes it a **one-shot**: it's consumed the first time it fires, then gone —
    "when you **next** [event] this turn". Combine with `trigger = Triggers.YouAttack` for the
    common "when you next attack this turn, …" template (All-Out Assault: untap each creature you
    control on your next attack). With `fireOnce = false` (default) it fires on every matching event
    until expiry (double-strike combat damage). One-shot consumption happens when the trigger goes
    on the stack (`TriggerProcessor`), so a second matching event the same turn won't re-fire it.

---

## 9. Static abilities

```kotlin
staticAbility {
    ability = Modification.GrantKeyword(Keyword.FLYING)
    filter = GroupFilter.CreaturesYouControl.withSubtype("Soldier")
    duration = Duration.Permanent
    layer = Layer.PT_POWER_TOUGHNESS    // optional; usually inferred
    condition = Conditions.YouControl(Filters.Swamp)
}
```

**`Modification` options**

- `AddSubtype(subtype)` — add a subtype to matching creatures.
- `RemoveSubtype(subtype)` — strip a subtype.
- `ReplaceSubtypes(subtypes)` — set the subtype list outright.
- `ModifyStats(p, t)` — `±P/±T`.
- `SetPower(p)` — overwrite power.
- `SetToughness(t)` — overwrite toughness.
- `SetStats(p, t)` — overwrite both.
- `GrantKeyword(keyword)` — grant a keyword.
- `RemoveKeyword(keyword)` — remove a keyword.
- `GrantProtection(color)` — grant protection from a color.
- `Custom(...)` — escape hatch for one-off modifications.

**Composite static abilities**

- `ModifyStatsForCreatureGroup` — lord-style P/T booster targeting a group.
- `GrantKeywordByCounter` — Aurification — keyword based on counters present.
- `AddCreatureTypeByCounter` — subtype based on counters present.
- `SetEnchantedLandType(landType)` — "Enchanted land is an Island" — replaces the enchanted
  land's basic land types with a fixed type (Rule 305.7). (Sea's Claim)
- `SetEnchantedLandTypeFromChosen` — "Enchanted land is the chosen type" — same, but reads the
  type from the source's `ChosenLandTypeComponent` (paired with
  `EntersWithChoice(ChoiceType.BASIC_LAND_TYPE)`). Chosen-value counterpart to
  `SetEnchantedLandType`, mirroring `GrantChosenColor`/`GrantColor`. (Phantasmal Terrain)
- `GrantLandwalkOfChosenType(filter = attachedCreature())` — "Enchanted creature has landwalk of
  the chosen type" — grants the landwalk keyword matching the source's `ChosenLandTypeComponent`
  (Plains→Plainswalk, Island→Islandwalk, …) at projection time. Chosen-value counterpart to
  `GrantKeyword`; pair with `EntersWithChoice(ChoiceType.BASIC_LAND_TYPE)`. (Traveler's Cloak)
- `GrantProtectionFromControlledColors(filter = attachedCreature())` — "[filter] have protection from
  the colors of permanents you control" — grants the affected creature(s) protection from every color
  among the permanents the source's controller controls, recomputed at projection (Layer 6, after
  Layer 5 colors) so it tracks the board in real time. Colorless permanents add no color. (Pledge of
  Loyalty)
- `GrantHexproofFromMonocoloredToGroup(filter = attachedCreature())` — "[filter] have hexproof from
  monocolored" — adds the projected keyword `HEXPROOF_FROM_MONOCOLORED`, which blocks targeting by
  monocolored (exactly one color, CR 105.2) spells and abilities opponents control. Colorless and
  multicolored sources are unaffected; the controller can still target their own creatures. (Dragonfire
  Blade)
- `GrantCardType(cardType, filter)` / `RemoveCardType(cardType, filter)` — Layer 4 type-changing statics that add or
  remove a card type (e.g. `"CREATURE"`). `RemoveCardType` backs Impending's "isn't a creature while it has a time
  counter" (wrapped in a `ConditionalStaticAbility`); reuse it for any "it's no longer a [type]" effect.
- `ConditionalStaticAbility` — static gated by a runtime `Condition`.
- `CantBlockCreaturesWithGreaterPower(filter = source())` — blocker-side evasion (Spitfire Handler): this
  creature can't block creatures whose projected power exceeds its own.
- `CantBeBlockedByCreaturesWithLessPower(filter = source())` — attacker-side dual (Formation Breaker): this
  creature can't be blocked by creatures whose projected power is less than its own. Resolved by
  `CantBeBlockedByCreaturesWithLessPowerRule`; both sides use projected power, so a P/T buff raises the
  threshold.
- `Effects.CreatePermanentEmblem(...)` — emblem with static abilities (planeswalker ultimates).
- `AttackTax(amountPerAttacker: DynamicAmount)` — Propaganda / Ghostly Prison / Windborn Muse /
  Collective Restraint. Per-attacker generic-mana tax for attacking the source's controller; the
  amount is a `DynamicAmount` so it can scale with state (e.g., `DynamicAmounts.domain()` for
  "{X} where X is your domain"). Evaluated with the source permanent's controller as "you".
  When `totalTax > 0`, the engine pauses `DeclareAttackers` for a `YesNoDecision` *before* tapping
  any mana — declining is a clean no-op that leaves the player in `DECLARE_ATTACKERS` to re-declare.
  The same prompt/cancel pattern applies to block-tax floating effects (e.g. Whipgrass Entangler)
  via `AttackBlockTaxPerCreatureType`.
- `CantBeAttackedWithout(keyword, attackerFilter = null)` — Form of the Dragon-style "Creatures
  without flying can't attack you." defender-side restriction. Optional `attackerFilter` narrows
  which attackers are restricted (evaluated with the source permanent as predicate source, so
  chosen-color/subtype predicates resolve against it) — e.g. Teferi's Moat:
  `CantBeAttackedWithout(Keyword.FLYING, GameObjectFilter.Creature.sharingChosenColorWithSource())`.
- `CantAttackUnlessCoAttacker(coAttackerFilter, filter = source)` — "This creature can't attack
  unless [a creature matching coAttackerFilter] also attacks" (Scarred Puma). Unlike
  `CantAttackUnless` (which is defender-relative), this depends on the whole proposed attacker
  group, so it's validated against the other declared attackers at declaration time (projected
  state; self never counts as its own co-attacker).
- `AttackerCountLimit(maxAttackers)` / `BlockerCountLimit(maxBlockers)` — global combat caps
  (Dueling Grounds — "No more than one creature can attack/block each combat"). Constrain the
  *total* declared attacker/blocker set across all players, not a single creature, so they are
  enforced as a whole-declaration check in `AttackPhaseManager`/`BlockPhaseManager` rather than a
  per-creature rule. While any permanent with the ability is on the battlefield, declaring more
  than the smallest cap is rejected. (`BlockerCountLimit` counts distinct blocking creatures.)

**Spell cost statics — `ModifySpellCost`**

Replaces the per-shape cost classes. Use directly as the `ability` of a `staticAbility { }` block.

```kotlin
staticAbility {
    ability = ModifySpellCost(
        target = SpellCostTarget.YouCast(GameObjectFilter.Any),
        modification = CostModification.ReduceGeneric(2),
        gating = CostGating.NthOfTypePerTurn(2),
    )
}
```

- `target: SpellCostTarget` — `SelfCast`, `YouCast(filter)`, `AnyCaster(filter)`,
  `OpponentsCastTargeting(GroupFilter)`, `FaceDownYouCast`, `MorphActivation`.
- `modification: CostModification` — `ReduceGeneric(amount)`, `ReduceGenericBy(source)`,
  `ReduceColored(symbols)`, `ReduceColoredPerUnit(symbols, source)`, `IncreaseGeneric(amount)`,
  `IncreaseColored(symbols)` (colored tax — adds colored pips, e.g. the Invasion Leeches'
  "White spells you cast cost {W} more"), `IncreaseGenericPerOtherSpellThisTurn(amountPerSpell)`,
  `IncreaseLife(amount)`.
  Reduction `source: CostReductionSource` covers fixed amounts, counts of permanents/cards in
  zones, target/condition gates, and a few mechanic-specific shapes — see
  `CostStaticAbilities.kt` for the full list.
- `gating: CostGating` — restricts how often the modifier fires:
  - `None` (default) — applies to every matching cast.
  - `NthOfTypePerTurn(n)` — only when this is the Nth matching spell each turn (1-indexed; counts the
    spell currently being cast). Use `n = 1` for "the first ... each turn" (Eluge); use
    `NthOfTypePerTurn(2)` with `target = YouCast(GameObjectFilter.Any)` for Uthros Psionicist's "the
    second spell you cast each turn costs {2} less".

`NthOfTypePerTurn` requires a filter-bearing target (`YouCast` / `AnyCaster`) — it needs a notion
of "type" to count.

**Global denial statics** (no `filter`/`duration` block — they're singleton-style)

- `PreventCycling` — "Players can't cycle cards." (Stabilizer)
- `PreventActivatedAbilities(filter)` — activated abilities (mana + non-mana) of matching
  permanents can't be activated; loyalty abilities and animation costs that haven't yet
  produced a creature are unaffected. (Cursed Totem → `GameObjectFilter.Creature`)
- `PreventManaPoolEmptying` — mana pools don't empty between steps/phases. (Upwelling)
- `NoMaximumHandSize` — controller has no hand-size limit. (Thought Vessel)
- `DampLandManaProduction` — a land tapped for 2+ mana produces `{C}` instead. (Damping Sphere)
- `RestrictSpellsCastPerTurn(maxPerTurn)` — the controller can't cast more than `maxPerTurn`
  spell(s) each turn. Per-controller; the most restrictive applies when several are in play.
  Already-cast spells count, even those cast before this permanent entered. (Yawgmoth's Agenda)
- `CantCastSpellsSharingColorWithLastCast` — *global* (all players): can't cast a spell that shares a
  color with the spell most recently cast this turn. Backed by `GameState.lastCastSpellColors` (the
  colors of the last spell cast, cleared each turn). Never blocks the first spell of the turn; a
  colorless spell shares no color, so it is always castable and casting one lifts the restriction
  until the next colored spell. (Mana Maze)
- `PlayersCantCastSpells(affected = Player.EachOpponent, spellFilter = GameObjectFilter.Any, condition = null)`
  — continuous cast *prohibition* parameterized along three independent axes, each a reused
  primitive: **who** (`affected`, a `Player` reference *relative to the source's controller* —
  `EachOpponent`/`Opponent`, `You`, `Each`), **which** (`spellFilter`, matched against the card being
  cast), and **when** (`condition`, evaluated in the controller's context, so `IsYourTurn` = "during
  your turn", `IsNotYourTurn` = "during an opponent's turn"; `null` = always). Read at cast-legality
  time through the single `CastPermissionUtils.reasonCannotCast` chokepoint, so it covers every
  casting zone (hand, flashback/harmonize, exile, top of library) uniformly; control is read from
  projected state. Examples: Voice of Victory = `PlayersCantCastSpells(Player.EachOpponent, condition
  = IsYourTurn)`; Grand Abolisher's cast clause = `PlayersCantCastSpells(Player.EachOpponent)`; Void
  Winnower = `PlayersCantCastSpells(Player.EachOpponent, spellFilter = GameObjectFilter(cardPredicates
  = listOf(CardPredicate.ManaValueIsEven)))`.

**Tapped-for-mana mana statics** (extra mana / replaced mana when a land is tapped for mana — resolve
inline as triggered mana abilities, off the stack per CR 605). These fire on the *manual* mana-ability
path; automatic cost payment adds the extra/replacement *mana* via the solver but skips non-mana
riders, matching how the engine already treats e.g. City of Brass's damage during auto-pay.

- `AdditionalManaOnTap(color, amount, anyColor = false)` — aura: "Whenever enchanted land is tapped
  for mana, its controller adds additional mana." `color = null` reads the aura's `ChosenColorComponent`;
  `anyColor = true` makes it one mana of **any color the controller chooses** each tap (prompts on a
  manual tap; flexible for the solver). (Elvish Guidance = fixed `{G}`; **Fertile Ground** = `anyColor`)
- `AdditionalManaOnSourceTap(sourceFilter, color = null, amount = 1, rider = null)` — global: "Whenever
  a `<sourceFilter>` is tapped for mana, that player adds …". `color = null` mirrors the produced color.
  `rider` is an optional non-mana `Effect` resolved inline, controlled by the tapping player
  (`EffectTarget.Controller` = tapper, `EffectTarget.Self` = the static's source). (Lavaleaper = basic-land
  mirror; Badgermole Cub = `+{G}`; **Overabundance** = `GameObjectFilter.Land` mirror + `DealDamage(1,
  Controller)` rider)
- `ReplaceLandManaColor(filter)` — global: lands matching `filter` produce one mana of a color of their
  controller's choice instead of their normal mana. Implemented by swapping the land's base mana effect
  for "add one mana of any color", so the choice flows through the normal any-color machinery (manual tap
  prompts; solver treats a matched basic as a five-color source). (**Pulse of Llanowar** =
  `GameObjectFilter.BasicLand.youControl()`)
- `OverrideEnchantedLandManaColor(color)` — aura: replaces the enchanted land's *own* produced color with
  a fixed/aura-chosen `color` (vs. `ReplaceLandManaColor`'s filter-based, free-choice form). (Shimmerwilds Growth)

**Alternative play / cast permissions** (let a player play or cast cards from non-hand zones)

- `MayPlayLandsFromGraveyard` — play lands from your graveyard (no per-turn cap). (Icetill Explorer)
- `MayPlayPermanentsFromGraveyard` — Muldrotha: play a land + cast one permanent spell of each
  permanent type from your graveyard each turn (per-type-per-turn cap).
- `MayCastFromGraveyard(filter, lifeCost = 0, duringYourTurnOnly = false)` — cast spells matching
  `filter` from your graveyard following normal timing, optionally paying `lifeCost` life. Free for
  Yawgmoth's Agenda (`MayCastFromGraveyard(Nonland)`); `lifeCost = 1, duringYourTurnOnly = true` for
  Festival of Embers. Pair with `MayPlayLandsFromGraveyard` for "play lands and cast spells from
  your graveyard". Lands are *played*, not cast, so they need the lands permission separately.

**Top-of-library reveal & play** (reveal the top card of a library, optionally with permission to
play it from there). Visibility (public reveal to all players) and play permission are separate
concerns — the `ClientStateTransformer` reveals the top card for `PlayFromTopOfLibrary` *or*
`RevealTopOfLibrary`, while the cast/play-from-top paths key only on the play-granting variants.

- `RevealTopOfLibrary` — *public reveal only*, no play permission: the controller's top card is
  shown to all players, but can only be played once drawn. (**Goblin Spy**)
- `PlayFromTopOfLibrary` — public reveal **and** "play lands and cast spells from the top of your
  library" (all card types). (Future Sight)
- `PlayLandsAndCastFilteredFromTopOfLibrary(spellFilter)` — like `PlayFromTopOfLibrary` but only
  spells matching `spellFilter` are castable (lands always playable). (Glarb, Calamity's Augur =
  `GameObjectFilter.Any.manaValueAtLeast(4)`)
- `CastSpellTypesFromTopOfLibrary(filter)` — cast only matching spell types from the top; no land
  play, no full public reveal. (Precognition Field = instants/sorceries)
- `LookAtTopOfLibrary` — *private*: the controller may look at their own top card any time (revealed
  only to them, not opponents). (Lens of Clarity, Vizier of the Menagerie)
- `OpponentsPlayWithHandsRevealed` — visibility-only, the opponent-facing sibling of
  `RevealTopOfLibrary`: each opponent of the controller plays with their hand publicly visible to
  that controller (no other game effect). Handled entirely by the client state transformer's
  hand-masking seam. (**Seer's Vision**)

> Multiple lord effects on one card → multiple `staticAbility { }` blocks.

---

## 10. Activated abilities

```kotlin
activatedAbility {
    cost = Costs.Tap
    effect = Effects.DrawCards(1)
    target = Targets.Creature
    optional = false
    timing = TimingRule.Normal
    isManaAbility = false
    restriction = ActivationRestriction.MaxPerTurn(1)
}
```

**`TimingRule`**

- `Normal` — at instant speed (default for most abilities).
- `ManaAbility` — resolves immediately, doesn't use the stack (CR 605).
- `SorcerySpeed` — only during your main phase, empty stack.
- `OnlyIfCondition(c)` — guarded by a runtime condition.

**`ActivationRestriction`**

- `MaxPerTurn(n)` — at most N activations per turn.
- `OnlyOnce` — once per game.
- `OnlyIfCondition(c)` — condition gate.

**Loyalty abilities**

- `loyaltyAbility(+N) { ... }` — add loyalty + effect.
- `loyaltyAbility(-N) { ... }` — remove loyalty + effect.
- `loyaltyAbility(0) { ... }` — 0-loyalty ability.

---

## 11. Keywords

**`Keyword` enum (display-level)**

Flying, Menace, Intimidate, Fear, Shadow, Horsemanship, all basic landwalks (Plainswalk … Forestwalk), Desertwalk
(nonbasic landwalk variant — `Keyword.DESERTWALK`, keyed off `Subtype.DESERT`), First Strike, Double
Strike, Trample, Deathtouch, Lifelink, Vigilance, Reach, Provoke, Flanking, Defender, Indestructible, Hexproof, Shroud, Haste,
Flash, Prowess, Flurry, Changeling, Convoke, Delve, Affinity, Storm, Flashback, Harmonize, Evoke, Impending, Conspire, Hideaway, Cascade, Plot,
Offspring, Persist, Ascend, Wither, Toxic, Eerie, Vivid, Fateful Bite, … (display-only — engine effect lives in handlers or
composite abilities).

**Parameterized `KeywordAbility.*`**

- `Ward(amount)` — opponent pays cost to target this.
- `Protection(color)` — protection from a single color.
- `ProtectionFrom(set)` — protection from a set of colors/types.
- `Protection(ProtectionScope.Supertype("Legendary"))` / `KeywordAbility.protectionFromSupertype("Legendary")` — protection from a supertype, e.g. "protection from legendary creatures" (Tsabo Tavoc). Enforced across targeting, blocking, and combat damage via projected `PROTECTION_FROM_SUPERTYPE_<X>` keywords.
- `Affinity(filter)` — cost reduction per matching permanent.
- `Amplify(n)` — ETB reveal-creatures-for-counters.
- `Devour(multiplier, sacrificeFilter, variant)` — "As this enters, you may sacrifice any number of [sacrificeFilter]. It enters with [multiplier] × that many +1/+1 counters." Plain Devour uses `sacrificeFilter = Creature` and `variant = ""`; the Edge of Eternities variant "Devour land N" uses `KeywordAbility.devourLand(n)` (`sacrificeFilter = Land`, `variant = "land"`). The keyword surfaces the rules text; pair with [`EntersWithDevour`](#15-replacement-effects) for the mechanical behavior.
- `Annihilator(n)` — attacker forces sacrifices.
- `Absorb(n)` — prevent N damage each time it would be dealt to this.
- `Bushido(n)` — +N/+N when blocking or blocked.
- `Rampage(n)` — +N/+N for each blocker past the first. Display-only; wire the behavior with the
  `card { rampage(n) }` builder helper, which adds this keyword ability plus a "becomes blocked"
  triggered ability granting `+n/+n × (blockers − 1)` until end of turn (mirrors `prowess()`).
- `Flurry` (Tarkir: Dragonstorm, Jeskai) — "Flurry — Whenever you cast your second spell each turn,
  [effect]." Display-only `Keyword.FLURRY`; wire the behavior with the `card { flurry { … } }` builder
  helper. Author the effect/target/optional inside the block exactly like `triggeredAbility { }` — the
  helper forces the `Triggers.NthSpellCast(2, Player.You)` trigger, adds the FLURRY tag, and prefixes the
  rendered text with "Flurry — Whenever you cast your second spell each turn," (mirrors `prowess()` /
  `rampage()`). The second-spell-cast event is matched by `GameEvent.NthSpellCastEvent`; no new engine
  subsystem is involved. Example: `flurry { effect = Effects.DealDamage(1, EffectTarget.PlayerRef(Player.EachOpponent), damageSource = EffectTarget.Self) }`.
- `Afflict(n)` — defender loses N when this becomes blocked.
- `Crew(n)` — tap N power worth to animate a Vehicle.
- `Modular(n)` — ETB with +1/+1 counters, transfer on death.
- `Fading(n)` — ETB with N fade counters; removes one each upkeep, sacrifice if can't.
- `Vanishing(n)` — same idea with time counters.
- `Renown(n)` — first combat damage to a player grants renown counters.
- `Fabricate(n)` — ETB choose +1/+1 counters or Servo tokens.
- `Tribute(n)` — opponent chooses ETB bonus.
- `Mobilize(n)` — +N tapped-and-attacking 1/1 red Warrior tokens on attack (Tarkir: Dragonstorm, Mardu).
  Display-only; wire the behavior with the `card { mobilize(n) }` builder helper, which adds this keyword
  ability plus a "whenever this attacks" triggered `CreateTokenEffect` (`tapped = true`, `attacking = true`)
  whose `sacrificeAtStep = Step.END` schedules one delayed `SacrificeTargetEffect` per created token at the
  next end step (mirrors `rampage()`). `n` may be any fixed value (Mobilize 1/2/3, …).
- `Decayed` — "This creature can't block, and when it attacks, sacrifice it at end of combat" (CR 702.147,
  Innistrad: Midnight Hunt). Display-only; wire the behavior with the `card { decayed() }` builder helper, which adds
  the keyword plus a `CantBlock(GroupFilter.source())` static ability and a "whenever this attacks" triggered
  `CreateDelayedTriggerEffect(step = Step.END_COMBAT, effect = Effects.SacrificeTarget(EffectTarget.Self))` (mirrors
  Mardu Blazebringer's end-of-combat self-sacrifice). No parameter.
- `Toxic(n)` — adds poison counters on combat damage.
- `Cycling(cost)` — pay cost, discard, draw a card.
- `BasicLandcycling(cost)` — cycling that fetches a basic land type.
- `Typecycling(type, cost)` — cycling that fetches a card type.
- `Plot(cost)` — `KeywordAbility.plot(cost)`. Special action available during your main phase while the stack is empty: pay [cost] and exile the card from your hand. It becomes plotted (stamped with a `PlottedComponent`). On a later turn you may cast it from exile without paying its mana cost, as a sorcery (CR 718). Cast permission is granted via the engine's standard `MayPlayPermission` + `PlayWithoutPayingCostComponent`, gated by `Conditions.SourcePlottedOnPriorTurn`. No card-side wiring needed — declare the keyword ability on the card and the engine handles the rest.
- `Hideaway(n)` — `KeywordAbility.hideaway(n)`; display tag rendered "Hideaway N". Mechanic is composed manually via `MoveCollectionEffect(faceDown = true, linkToSource = true)` + `CardSource.FromLinkedExile()` — the keyword itself carries no engine behavior.
- `Harmonize(cost)` — `KeywordAbility.harmonize(cost)` (Tarkir: Dragonstorm). An alternative cost to cast an instant/sorcery **from your graveyard**, like Flashback, then exile it as it resolves. As you cast it you may tap **a single** untapped creature you control to reduce the **generic** portion of the harmonize cost by that creature's (projected) power — a Convoke-style reduction, but one creature paying generic-equal-to-power instead of one mana per creature. No card-side wiring: declare the keyword ability and the engine handles graveyard-cast enumeration (`CastWithHarmonize`), the per-creature reduction (routed through `AlternativePaymentChoice.harmonizeCreature`), and the exile-on-resolution. The chosen creature and its power are surfaced to the client via `LegalAction.harmonizeCreatures` / `hasHarmonize`; the client offers an on-battlefield single-creature tap step (the `harmonize` pipeline phase + `HarmonizeSelector` HUD, mirroring Convoke). **Harmonize {X}** (e.g. Nature's Rhythm `{X}{G}{G}{G}{G}`): the `CastWithHarmonize` action surfaces `hasXCost`/`maxAffordableX` (max X folds in the best single-creature tap reduction) so the client prompts for X. {X} is generic mana, so the tap reduces the mana paid *for X* — `CastSpellHandler.harmonizePaymentXValue` lowers the X mana once `reduceGeneric` has consumed any printed generic — while the chosen X stamped onto `SpellOnStackComponent.xValue` (and read by the effect, e.g. "mana value X or less") is unchanged. Colored pips are never reduced.
- `OptionalAdditionalCost(manaCost?, additionalCost?, multi, displayPrefix, branchesEffect, grantsFlashTiming)` — generalised "pay an optional extra cost while casting" primitive. Backs printed Kicker / Multikicker / Offspring **and** the pre-kicker "pay {N} more to cast as though it had flash" pattern (Ghitu Fire). When `branchesEffect = true` (default) paying the cost marks the spell so `WasKicked` fires for the card's own effect/triggers; when `false` the payment is invisible to `WasKicked` (used by `flashKicker`). When `grantsFlashTiming = true` paying the cost unlocks instant-speed casting in addition to whatever else it does — the optional cost may be mana (Ghitu Fire: `KeywordAbility.flashKicker("{2}")`) **or** a non-mana `additionalCost` such as Behold (Molten Exhale: "you may cast this as though it had flash if you behold a Dragon", `KeywordAbility.flashKicker(AdditionalCost.Behold(filter = Filters.WithSubtype("Dragon")))`). Prefer the factories: `KeywordAbility.kicker(cost)`, `KeywordAbility.kicker(additionalCost)`, `KeywordAbility.multikicker(cost)`, `KeywordAbility.offspring(cost)`, `KeywordAbility.flashKicker(cost)`, `KeywordAbility.flashKicker(additionalCost)`. Serial name is `Kicker` for wire compatibility. **Kicker {X}** (variable kicker, e.g. `KeywordAbility.kicker("{X}")` on Verdeloth the Ancient): the kicked cast surfaces `hasXCost`/`maxAffordableX` so the client prompts for X exactly like a base-cost X spell; the chosen X is paid as part of the kicker and stamped onto `SpellOnStackComponent.xValue`, so the card's ETB trigger reads it via `DynamicAmount.XValue` ("create X tokens").
- `Impending(time, cost)` — `card { impending(n, cost) }` builder helper (CR 702.175, Duskmourn). A self-alternative
  cost: pay [cost] instead of the mana cost and the permanent enters with N **time counters**, isn't a creature until
  the last is removed, and loses one at the beginning of your end step. The helper wires everything from one call — the
  `KeywordAbility.Impending` alt-cost (display + cast enumeration), a `ConditionalStaticAbility(RemoveCardType("CREATURE"),
  Conditions.SourceHasCounter(TIME))` "isn't a creature while it has a time counter" static, and a `YourEndStep`
  triggered ability (gated by the same intervening-if) that removes a time counter. The engine places the N TIME counters
  when a spell cast for its impending cost resolves; casting for the normal mana cost adds no counters, so neither wiring
  fires (mirrors `prowess()` / `rampage()`).
- `Suspend` (CR 702.62) — an **exile-zone** mechanic, unlike Impending/Vanishing which live on the battlefield.
  A suspended card sits in exile with **time counters**; at the beginning of its **owner's** upkeep one is removed,
  and when the last is gone its owner **may play it for free**, with **haste** if it's a creature. The lifecycle is
  **component-driven**, not definition-driven: the engine grants `Suspend.countdownAbility` (a synthesized
  `activeZone = EXILE` upkeep trigger — remove a counter, then a `MayEffect` that gathers the card via
  `CardSource.Self` and casts it with `CastFromCollectionWithoutPayingCostEffect`) to **any** exiled card carrying the
  `SuspendedComponent` marker. So an arbitrary card with no printed suspend can be suspended.
  - **Putting a card into suspend** is a chain you compose; `Effects.Suspend(target, timeCounters)` is the reusable
    two-step tail (`AddCounters(TIME, n)` + `GrantSuspendEffect` — the latter sets the marker **and** arms a dormant
    haste effect on the card with duration `WhileControlledByController`, so the haste ends the moment the player who
    played it loses control of the permanent — CR 702.62g). The caller supplies the exile step first, because it differs by source zone:
    a spell on the stack uses `CounterSpellToExile` / `CounterEffect(counterDestination = Exile())` (it can't be lifted
    off the stack with a zone-move); a printed `suspend N—[cost]` exiles from hand as its cast cost.
  - **Taigam, Master Opportunist** is the first user: `Composite(CopyTargetSpell(TriggeringEntity),
    CounterEffect(TriggeringEntity → Exile), Suspend(TriggeringEntity, 4))`.
- `Renew(cost)` — `card { renew(cost) { effect = … } }` builder helper (Tarkir: Dragonstorm, Sultai clan keyword).
  A graveyard-activated ability: "Renew — [cost], Exile this card from your graveyard: [effect]. Activate only as a
  sorcery." The helper composes it entirely from existing primitives — `AbilityCost.Composite(Mana(cost), ExileSelf)`,
  `activateFromZone = Zone.GRAVEYARD`, and `timing = TimingRule.SorcerySpeed` — so no new engine subsystem is involved.
  The `renew { }` lambda configures the effect (and any targets via `target(name, requirement)`) exactly like
  `activatedAbility { }`; its `cost`/`timing`/`activateFromZone` fields are ignored (fixed by Renew). The
  `GraveyardAbilityEnumerator` surfaces the ability while the card is in the graveyard and only at sorcery speed; the
  `ActivateAbilityHandler` pays the mana and exiles the card from the graveyard. Declares `Keyword.RENEW` for display.
- `Morph(cost)` — cast face-down for `{3}`, flip for cost.
- `Unmorph(cost, effect)` — turn-face-up cost + bonus effect.
- `Equip(cost)` — Equipment attach cost. The `equipAbility(cost, genericCostReduction = …)` DSL
  form optionally reduces the generic portion of the equip cost by a `DynamicAmount` evaluated at
  activation. Reductions that read the chosen equip target (e.g. `DynamicAmounts.targetColorCount()`
  for "costs {1} less to activate for each color of the creature it targets" — Dragonfire Blade)
  resolve against the picked target. Backed by `ActivatedAbility.genericCostReduction`: the
  `ActivateAbilityHandler` locks the per-target reduction in before paying; the legal-action
  enumerator gates affordability on the cheapest reachable cost (largest reduction over the
  currently-legal targets) since the target isn't chosen until activation.
- `Fortify(cost)` — Aura-like attach cost on lands.

```kotlin
keywords(Keyword.FLYING, Keyword.VIGILANCE)
keywordAbility(KeywordAbility.Ward(2))
keywordAbilities(KeywordAbility.Protection(Color.BLUE), KeywordAbility.Annihilator(2))
```

---

## 12. Conditions (`Conditions.*`)

### Battlefield state

- `YouControl(filter)` — you control ≥1 matching permanent.
- `ControlCreature` — you control any creature.
- `ControlMoreCreatures` — you control more creatures than each opponent.
- `OpponentControlsCreature` — at least one opponent has a creature.
- `OpponentControlsMoreCreatures` — an opponent outpaces you.
- `OpponentControlsMoreLands` — an opponent has more lands.
- `OpponentControlsLandType(type)` — opponent controls land of a type.
- `TargetControlsCreature(target)` — target player has a creature.
- `TargetControlsLand(target)` — target player has a land.
- `TargetMatchesFilter(filter, targetIndex = 0)` — the context target matches a `GameObjectFilter`.
- `IfTargetTookExcessDamage(targetIndex = 0)` — true post-damage when the target creature's marked
  damage strictly exceeds its (projected) toughness. Chain after `Effects.DealDamage` in a composite
  so the marked-damage update applies before the condition reads it. Used by Orbital Plunge ("If
  excess damage was dealt this way, create a Lander token"). Semantics caveat: the read is
  `marked > toughness` regardless of which preceding step dealt the damage — Composite doesn't
  interleave SBA or fire triggers mid-chain, so for the canonical "deal N, then check" pipeline
  this is equivalent to "did the preceding step deal excess". A chain that deals damage in
  multiple steps within the same composite would see cumulative damage; reach for a different
  condition there. Defensive guards return false for non-creature targets and targets no longer
  on the battlefield (unreachable under `Targets.Creature` + Composite, retained for future
  callers).
- `TargetSharesMostCommonColor(targetIndex = 0)` — the context target shares a color with the
  most common color among all permanents, or a color tied for most common. Tallies each of the
  five colors across every battlefield permanent (multicolored permanents count once per color,
  using projected colors), takes the highest tally, and checks whether the target has any color
  in that (possibly tied) most-common set. A board with no colored permanents is `false`. Used by
  Tsabo's Assassin.
- `ColorIsMostCommon(color)` — the self-gating sibling of the above: true when `color` is the most
  common color among all permanents, or tied for most common (same tally rules). Board-derived
  only — no targets/triggering/kicker — so it evaluates identically in resolution and in
  projection, which lets it gate a `ConditionalStaticAbility`. Used by the Invasion djinn cycle
  ("as long as [color] is the most common color among all permanents…" — Goham/Halam/Ruham/Sulam/Zanam).
- `AnotherPermanentWithSameNameAsTarget(targetIndex = 0)` — true when at least one *other*
  battlefield permanent shares the exact card name of the context target at `targetIndex`. The
  target itself is excluded, so a lone copy never satisfies its own check; tokens compare by name
  like any other permanent. Resolution-only (reads a chosen target). Used by Winnow ("Destroy
  target nonland permanent if another permanent with the same name is on the battlefield").
- `EnchantedPermanentMatches(filter)` — true when the permanent the source Aura is attached to
  matches a `GameObjectFilter` (color, type, etc.), evaluated in projected state via the Aura's
  `AttachedToComponent`. General-purpose counterpart to the narrow `EnchantedCreatureIsLegendary` /
  `EnchantedCreatureHasSubtype` conditions. Works as a `ConditionalStaticAbility` gate (also in the
  trigger resolver for conditionally-granted abilities). Used by Essence Leak ("as long as enchanted
  permanent is red or green…", `GameObjectFilter.Permanent.withAnyColor(Color.RED, Color.GREEN)`).
- `YouHaveCitysBlessing` — you have City's Blessing (10+ permanents).
- `SourceIsRingBearer` — the source permanent is your Ring-bearer (CR 701.52e).

### Life & damage

- `LifeAtLeast(n, player?)` — player has ≥N life.
- `LifeAtMost(n, player?)` — player has ≤N life.
- `YouLostLife` — you lost life this turn.
- `OpponentLostLife` — an opponent lost life this turn.

### Cast / cost

- `WasCast` — source was cast (not put onto the stack).
- `WasCastFromHand` — cast specifically from hand.
- `WasCastFromZone(zone)` — cast from a specific zone.
- `WasKicked` — cast with kicker / multikicker / offspring (i.e. an `OptionalAdditionalCost` with `branchesEffect = true` whose extra cost was paid). FlashKicker payments are intentionally invisible to this condition.
- `BlightWasPaid(amount)` — the Blight X additional cost was paid.

### Source state

All "source matches X" conditions desugar to `SourceMatches(filter)` — a generic predicate
check against the source entity that works in both resolution and static-ability (projection)
contexts.

- `SourceMatches(filter)` — primitive: source entity matches a `GameObjectFilter`.
- `SourceIsAttacking` — source is attacking.
- `SourceIsBlocking` — source is blocking.
- `SourceIsTapped` — source is tapped.
- `SourceIsUntapped` — source is untapped.
- `SourceEnteredThisTurn` — source entered the battlefield this turn.
- `SourceHasDealtDamage` — source has dealt damage since entering the battlefield.
- `SourceHasDealtCombatDamageToPlayer` — saboteur-style payoff gate.
- `SourceIsModified` — has counters, attached Equipment, or controller-owned Aura
  attached (CR 700.4). Kept as a dedicated condition because the controller-of-Aura
  match isn't expressible via the generic `SourceMatches` machinery.
- `SourceHasSubtype(subtype)` — `SourceMatches(GameObjectFilter.Any.withSubtype(...))`;
  Changeling is honored.
- `SourceHasKeyword(keyword)` — `SourceMatches(GameObjectFilter.Any.withKeyword(...))`.
- `SourceHasCounter(counterType)` — `SourceMatches(GameObjectFilter.Any` with the
  corresponding `StatePredicate.HasCounter` / `HasAnyCounter`).

### Turn / phase

- `IsYourTurn` — it's your turn.
- `IsNotYourTurn` — it's an opponent's turn.
- `IsInPhase(phase)` — currently in `BEGINNING | MAIN | COMBAT | …`.

### Per-turn counts

All three are parameterised by a `Player` reference (default `Player.You`), so they
work in both resolution and static-ability (projection) contexts. The DSL helpers
default to "you" so card authors don't need to pass it explicitly.

- `YouAttackedWithCreaturesThisTurn(filter, atLeast)` — Raid/Battalion shape. Backed by
  `PlayerAttackedWithCreaturesThisTurn(Player.You, filter, atLeast)`.
- `YouCastSpellsThisTurn(atLeast, filter)` — Prowess/Magecraft shape. Backed by
  `PlayerCastSpellsThisTurn(Player.You, filter, atLeast)`.
- `TriggeringSpellMatches(filter)` — intervening-if guard: the spell that triggered this ability
  matches `filter`. Reads the triggering entity's static card characteristics (so it stays correct
  after the spell leaves the stack). General "whenever you cast a spell, if it's a/an X ..." gate.
  Backed by `TriggeringSpellMatchesFilter(filter)`.
- `YouCastFirstSpellOfTypeThisTurn(filter)` — true when the triggering spell is the *first* spell
  matching `filter` you've cast this turn. Pure composition, no bespoke counting:
  `All(TriggeringSpellMatches(filter), Not(YouCastSpellsThisTurn(atLeast = 2, filter)))`. The
  `TriggeringSpellMatches` half is load-bearing — it stops a later non-matching cast from satisfying
  the count once one matching spell exists. Used by Alania, Divergent Storm (first instant / first
  sorcery / first Otter).
- `YouHaveCitysBlessing` — Ascend gate. Backed by `PlayerHasCitysBlessing(Player.You)`.
- `IsFirstSpellPaidWithTreasureManaCastThisTurn` — gates a triggered ability to fire only
  on the first spell each turn that mana from a Treasure was spent to cast (Rain of
  Riches). Reads `CastSpellRecord.paidWithTreasureMana` on the per-player spell history.
- `PermanentTypeEnteredBattlefieldThisTurn(cardType, player = Player.You)` — true if a
  permanent of `cardType` entered the battlefield under `player`'s control at any point
  this turn. Pure ETB tracker: the permanent need not still be on the battlefield, still
  be of that type, or still be under the same controller — only the entry event matters
  (so Mechan Shieldmate's "as long as an artifact entered ... this turn" stays satisfied
  even if the artifact is destroyed before combat). Captured types are read from the
  *projected* state at the moment of entry, so a permanent that's an artifact via a
  continuous effect at ETB (Mycosynth Lattice, etc.) also counts. Backed by the per-player
  `PermanentTypesEnteredBattlefieldThisTurnComponent`, cleared by `CleanupPhaseManager` at
  end of turn. Every battlefield entry must go through `BattlefieldEntry.place` for this
  tracker to stay in sync. Shortcut: `Conditions.ArtifactEnteredBattlefieldThisTurn`.
- `YouDescendedThisTurn(atLeast = 1)` — CR 700.11 gate: at least `atLeast` nontoken
  permanent cards were put into your graveyard from *any* zone this turn (battlefield,
  hand, library, stack, exile). Tokens do not count, even though they briefly enter the
  graveyard before ceasing to exist; instants and sorceries do not count. The cards
  themselves need not still be in the graveyard when the gate evaluates — the count is a
  pure event tracker. Composes through `Compare(DynamicAmount.TurnTracking(Player.You,
  TurnTracker.DESCENDED), GTE, Fixed(atLeast))`, so the same plumbing supports the bare
  descend gate (`atLeast = 1`, Ruin-Lurker Bat: "At the beginning of your end step, if
  you descended this turn, scry 1") and the descend N / fathomless descent ability words
  (`atLeast = 4`, `atLeast = 8`). Backed by the per-player
  `PlayerDescendedThisTurnComponent`, incremented in `ZoneTransitionService` whenever a
  permanent (nontoken) card lands in a player's graveyard, and cleared by
  `CleanupPhaseManager` at end of turn.

### Composition

- `All(c1, c2, ...)` — AND.
- `Any(c1, c2, ...)` — OR.
- `Not(c)` — negate.
- `Compare(v1, op, v2)` — numeric comparison between `DynamicAmount`s.
- `Exists(player, zone, filter)` — at least one matching object exists.
- `FixedIfCondition(...)` — bake a condition into a static-ability gate.

### Static-ability vs resolution-time evaluation

Every `Condition` works in both contexts: at spell/trigger resolution (full
`EffectContext` — targets, kicker, triggering entity, etc.) and during state projection
inside a `ConditionalStaticAbility` (only the source entity and projected values are
known). The engine dispatches via a `ConditionEvaluationContext.Resolution` /
`Projection` sealed type — there is **no** separate `SourceProjectionCondition` arm.

Conditions that need resolution-only facts (e.g. `TargetMatchesFilter`, `TargetSharesMostCommonColor`, `TriggeringEntity*`,
`WasKicked`, `ManaSpentToCastIncludes`, `CollectionContainsMatch`) silently evaluate to
`false` under projection — a static-ability gate is never "in the middle of casting a spell".

Other gates available in both contexts:

- `ColorIsMostCommon(color)` — board-derived, so it gates a `ConditionalStaticAbility` directly
  (the Invasion djinns rely on this).
- `SourceChosenModeIs("id")` — gate on the chosen mode (Sieges / `EntersWithChoice`).
  Currently resolution-only; can be extended to projection if needed.

---

## 13. Dynamic amounts (`DynamicAmount.*`)

Numbers computed at resolution time.

### Math

- `Fixed(n)` — literal constant.
- `XValue` — the X chosen for the spell/ability.
- `TotalManaSpent` — total mana paid from the pool to cast the current spell (sum of every per-color
  bucket; for X spells the X portion is included). E.g. Memory Deluge "where X is the mana spent."
- `ManaSpentOnX(color)` — the amount of `{color}` mana spent on the `{X}` portion specifically, broken
  down by color. Used by payoffs that scale with how much of a color went into X — Soul Burn ("you gain
  life equal to the amount of black mana spent on X"). Pair with `xManaRestriction` (see below) so the X
  can only be paid with the relevant colors.
- `Add(a, b)` — `a + b`.
- `Subtract(a, b)` — `a − b`.
- `Multiply(a, b)` — `a × b`.
- `Divide(a, b, roundUp?)` — division with rounding rule.
- `Min(a, b)` — minimum.
- `Max(a, b)` — maximum.
- `Absolute(a)` — `|a|`.

### Battlefield aggregation

- `AggregateBattlefield(player, filter)` — count matching permanents.
- `AggregateZone(player, zone, filter?, aggregation?)` — count cards in a zone.
- `CountPermanentsOfType(player, subtype)` — count by creature type.
- `CountCreaturesYouControl` — shorthand for "your creatures".

### Player & game

- `LifeTotal(player)` — current life total.
- `HandSize(player)` — cards in hand.
- `TurnCount(player)` — turn number for that player.
- `TurnTracking(player, TurnTracker)` — value of a per-turn counter (see below).

### Counters

- `CountersOnSource(type)` — counters of `type` on the source permanent.
- `LastKnownCountersOnSource(type)` — counters when source last existed (for dies-triggers).
- `CountersOnTarget(target, type)` — counters on a target permanent.
- `CountersOnContext(path, type)` — counters stored in an `EffectContext` path.

### Card properties

- `TargetPower(target)` — target's current power.
- `TargetToughness(target)` — target's current toughness.
- `TargetManaValue(target)` — target's mana value.
- `DynamicAmounts.targetManaSpent(index)` — sum of all `manaSpent{Color}` buckets on
  the targeted spell's `SpellOnStackComponent` (i.e. what was actually paid, after
  cost reductions/increases). Pair with `targetManaValue()` for "if the amount of
  mana spent to cast that spell was less than its mana value" gates (Unravel).
  Desugars to `EntityProperty(EntityReference.Target(index), EntityNumericProperty.ManaSpent)`.
  Returns 0 if the target isn't a spell on the stack.
- `DynamicAmounts.targetColorCount(index)` / `DynamicAmounts.colorCountOf(entity)` — number of
  distinct colors of the indexed cast-time target / any `EntityReference`. Desugars to
  `EntityProperty(entity, EntityNumericProperty.ColorCount)`. Read from projected state for
  battlefield permanents (honors layer-5 color-changing — a creature turned colorless counts 0).
  Powers "for each color of [it]" amounts, e.g. Dragonfire Blade's equip cost reduction.
- `CardNumericProperty(card, property)` — generic numeric property accessor.

### Triggering-entity shortcuts (`DynamicAmounts.*` facades)

For triggered abilities whose effect reads a property of the entity that caused the trigger
(rather than the source of the ability):

- `DynamicAmounts.triggeringPower()` — power of the triggering entity (e.g. Warstorm Surge:
  "it deals damage equal to its power").
- `DynamicAmounts.triggeringToughness()` — toughness of the triggering entity.
- `DynamicAmounts.triggeringManaValue()` — mana value of the triggering entity.

All three desugar to `EntityProperty(EntityReference.Triggering, …)`.

### Attached-creature shortcut (`DynamicAmounts.*` facade)

For Aura/Equipment abilities that read a property of the creature the source is attached to (rather
than the source permanent itself — for an Aura, `EntityReference.Source` is the Aura, not the creature):

- `DynamicAmounts.enchantedCreaturePower()` — power of the attached creature (e.g. Pain for All:
  "enchanted creature deals damage equal to its power"). Desugars to
  `EntityProperty(EntityReference.EnchantedCreature, EntityNumericProperty.Power)`. The
  `EnchantedCreature` reference resolves through the source's `AttachedToComponent` (state-aware), so it
  needs an effect context with a `sourceId`; it returns 0 in predicate/filter-only contexts that don't
  thread state. When read in a **triggered ability** and the attached creature has already left the
  battlefield by resolution (e.g. removed in response to the aura's ETB trigger), it falls back to the
  creature's last-known power — captured when the trigger fired — per CR 608.2g, rather than 0.

### Context-plumbed

- `ContextProperty(key)` — value plumbed via `EffectContext`. Keys include:
  - `TRIGGER_DAMAGE_AMOUNT` — damage in the current trigger payload (Tephraderm).
  - `TRIGGER_LIFE_GAINED` / `TRIGGER_LIFE_LOST` — life delta from a `LifeChangedEvent`.
  - `TRIGGER_COUNTERS_PLACED_AMOUNT` — counters placed in the triggering event (Simic Ascendancy).
  - `LAST_KNOWN_PLUS_ONE_COUNTER_COUNT` / `LAST_KNOWN_TOTAL_COUNTER_COUNT` — counters on the
    source as it last existed on the battlefield (Hooded Hydra / Shadow Urchin).
  - `ADDITIONAL_COST_EXILED_COUNT` / `ADDITIONAL_COST_BLIGHT_AMOUNT` — cost-step accumulators.
  - `TARGET_COUNT` — still-legal targets in the current effect context.
  - `LINKED_EXILE_CARD_COUNT` / `LINKED_EXILE_DISTINCT_CARD_TYPE_COUNT` — cards / distinct
    types in the source's linked exile pile (Veteran Survivor / Keen-Eyed Curator).
  - `MODES_CHOSEN_ON_TRIGGERING_SPELL` — number of mode picks recorded on the cast that fired
    the trigger (Riku of Many Paths). Counts selections, not distinct modes, so Spree with
    the same mode twice reads as `2`.
  - `TRIGGER_SCRY_COUNT` — cards looked at by the scry that fired the trigger (Celeborn the
    Wise, Elrond Master of Healing). Equals the scry N parameter.
- `AdditionalCostBlightAmount` — X paid via the Blight additional cost.
- `ChosenNumber` — number a player chose via a Choose action.
- `VariableReference(name)` — named variable stored earlier by `StoreResult`/`StoreCount`.
- `ColorsAmongPermanents(player)` — count of distinct colors among player's permanents.

### `ManaColorSet`<a id="manacolorset"></a>

Color analogue of `DynamicAmount` — pure data resolved at the moment a mana effect fires.
Used by `AddManaOfChoice(colorSet, amount)`; the engine's `ManaColorSetResolver` materializes
a `Set<Color>` from the source/controller/projected state, the player picks one (or the
solver picks if there's only one), and that color is added to the pool.

- `ManaColorSet.AnyColor` — all five colors. The "any-color" default.
- `ManaColorSet.Specific(colors)` — hand-authored fixed set (e.g., `{R, G}` for a Gruul producer).
- `ManaColorSet.CommanderIdentity` — union of color identities of every commander the controller has registered. Empty (no mana produced) in non-Commander formats.
- `ManaColorSet.AmongPermanents(filter)` — colors of permanents matching `filter`, read via projected state so type/color-changing effects are honored. Mox Amber shape.
- `ManaColorSet.LandsCouldProduce(scope)` — colors any land in `scope` could produce; tapped state and activation costs are ignored (CR 106.7). `scope` is `LandControllerScope.{YOU, OPPONENTS, ANY}`. Fellwar Stone / Exotic Orchard / Reflecting Pool shape.
- `ManaColorSet.SourceChosenColor` — the single color stored on the source's `ChosenColorComponent` (set via `EntersWithChoice(ChoiceType.COLOR)`). Uncharted Haven / Ashling Rekindled shape.

### `ManaRestriction`

Spending restrictions attached to a unit of mana when it is added to the pool. Used by
`AddMana`, `AddColorlessMana`, and `AddManaOfChoice` (via the `restriction` parameter).
When the engine pays a spell's cost, restricted mana is consumed preferentially when its
restriction matches the spell context.

- `ManaRestriction.AnySpend` — no restriction; satisfies any spend. Used internally when
  `AddManaOfChoice(riders = ...)` is provided without an explicit restriction, so the rider
  set survives in the pool without limiting where the mana can be spent (Path of Ancestry).
- `ManaRestriction.InstantOrSorceryOnly` — only instants and sorceries.
- `ManaRestriction.KickedSpellsOnly` — only kicked spells.
- `ManaRestriction.CreatureSpellsOnly` / `CreatureMV4OrXCost` / `SpellsMV4OrGreater` —
  creature- or mana-value-gated.
- `ManaRestriction.SubtypeSpellsOrAbilitiesOnly(subtype, creatureOnly?)` — Cavern of Souls /
  Unclaimed Territory: only spells of a baked subtype, optionally creature-only.
- `ManaRestriction.CastFromExileOnly` — only spells cast from exile.
- `ManaRestriction.CardTypeSpellsOrAbilitiesOnly(cardType, allowSpells?, allowAbilities?)` —
  Steelswarm Operator shape.

### `ManaSpellRider`

Side-effects attached to mana that fire when the mana is spent on a spell. Orthogonal to
`ManaRestriction`: the restriction controls *where* the mana may be spent; the rider
controls *what happens to the spell* when it is spent. The cast pipeline either mutates the
spell directly (e.g. stamps a component) or queues a triggered ability onto the stack above
the spell when the rider needs the stack (typically because it requires a player decision).

- `ManaSpellRider.MakesSpellUncounterable` — Cavern of Souls: stamps `CantBeCounteredComponent`
  on the spell at cast time.
- `ManaSpellRider.ScryOnSharedTypeWithCommander(amount)` — Path of Ancestry: if the spell is
  a creature spell that shares a creature type with any of the controller's commanders,
  queues a `scry amount` triggered ability above the spell.

### `TurnTracker` keys (used with `TurnTracking`)

- `CREATURES_DIED` — creatures that died this turn.
- `NONTOKEN_CREATURES_DIED` — nontoken creatures that died this turn.
- `OPPONENT_CREATURES_EXILED` — opponent creatures you exiled.
- `OPPONENTS_WHO_LOST_LIFE` — count of opponents who lost life.
- `DAMAGE_RECEIVED` — damage received by player.
- `LIFE_GAINED` — life gained this turn (Bre of Clan Stoutarm).
- `LIFE_LOST` — life lost this turn.
- `PLAYER_ATTACKED` — whether/how many times you attacked.
- `DEALT_COMBAT_DAMAGE` — combat damage dealt.
- `COUNTERS_PUT_ON_CREATURE` — counters placed.
- `LANDS_PLAYED` — lands played this turn.
- `FOOD_SACRIFICED` — Food tokens sacrificed.
- `CARDS_LEFT_GRAVEYARD` — cards leaving your graveyard.
- `DESCENDED` — number of times a player has descended this turn (CR 700.11) — i.e.
  count of nontoken permanent cards put into that player's graveyard from any zone.
  Backs `Conditions.YouDescendedThisTurn(atLeast)` and `DynamicAmounts.descendedThisTurn`
  (descend N / fathomless descent ability words).

---

## 14. Modal & choice

### Modal spells

```kotlin
spell {
    modal(chooseCount = 1) {
        mode("Destroy a creature") {
            val c = target("creature", Targets.Creature)
            effect = Effects.Destroy(c)
        }
        mode("Draw a card") {
            effect = Effects.DrawCards(1)
        }
    }
}
```

- `modal(chooseCount = N) { ... }` — N modes picked at cast time (or resolution for Commands).
- `mode(description) { ... }` — one option with its own targets/effect.
- `.requiresTarget(filter)` — mode needs a target matching filter.
- `.optional()` — mode can be skipped.
- `Mode.noTarget(...)` — explicit target-less mode (outer targets are preserved).

`ModalEffect.chooseOne { mode(...) }` and `ModalEffect.chooseN(n) { ... }` for explicit modal effects.

**Dynamic "choose up to X"** — `ModalEffect.chooseUpToDynamic(dynamicMax, *modes, allowRepeat = false)`
caps the pick count by a `DynamicAmount` evaluated at resolution time. `minChooseCount` is
forced to `0` (the player may always decline); `chooseCount` becomes `min(eval, modes.size)`.
If the evaluated cap is `0` the effect resolves as a no-op. Used by Riku of Many Paths,
where the cap is `ContextProperty(MODES_CHOSEN_ON_TRIGGERING_SPELL)`. Equivalent raw shape:
`ModalEffect(modes, chooseCount = modes.size, minChooseCount = 0, dynamicChooseCount = …)`.

### Permanent enters-with-choice (Sieges)

```kotlin
EntersWithChoice(
    ChoiceType.MODE,
    modeOptions = listOf(
        ModeOption(id = "khans", label = "Khans", description = "...", iconKey = "khans"),
        ModeOption(id = "dragons", label = "Dragons", description = "...", iconKey = "dragons"),
    ),
)
```

- Writes `ChosenModeComponent(modeId)` on the permanent.
- Downstream triggers/conditions gate via `SourceChosenModeIs("khans")`.
- Icons live in `web-client/src/assets/icons/options/`.

**Other `ChoiceType`s** — `ChoiceType.COLOR` writes `ChosenColorComponent` (read by
`GrantChosenColor`), `ChoiceType.CREATURE_TYPE` writes `ChosenCreatureTypeComponent`,
`ChoiceType.CREATURE_ON_BATTLEFIELD` writes `ChosenCreatureComponent`, and
`ChoiceType.BASIC_LAND_TYPE` writes `ChosenLandTypeComponent` (read by
`SetEnchantedLandTypeFromChosen` and `GrantLandwalkOfChosenType`). Example — Phantasmal Terrain
("As this Aura enters, choose a basic land type. Enchanted land is the chosen type."):

```kotlin
auraTarget = Targets.Land
replacementEffect(EntersWithChoice(ChoiceType.BASIC_LAND_TYPE))
staticAbility { ability = SetEnchantedLandTypeFromChosen }
```

Traveler's Cloak grants landwalk of the chosen type to the enchanted creature instead:

```kotlin
auraTarget = Targets.Creature
replacementEffect(EntersWithChoice(ChoiceType.BASIC_LAND_TYPE))
staticAbility { ability = GrantLandwalkOfChosenType() }
```

### Other choice effects

- `ChooseActionEffect(choices)` — pick one effect from a list.
- `ChooseColorThenEffect(whenChosen)` — pick a color, then apply a function of the color.
- `GrantHexproofFromChosenColorEffect(target)` / `GrantProtectionFromChosenColorEffect(target)` — atoms that run inside `ChooseColorThen` and read the chosen color from context (hexproof / protection from that color). Wrap in `ForEachInGroup` for "creatures you control gain protection from the chosen color" (Akroma's Blessing).
- `ChooseCreatureTypeEffect(...)` — pause for creature-type selection.
- `Effects.ChooseCardName(storeAs, prompt?, excludeBasicLandNames?)` — name a card (`ChooseOptionEffect(OptionType.CARD_NAME)`); the chosen name is stored in `chosenValues[storeAs]`. Options are every registry card name (searchable list, not free text); `excludeBasicLandNames` drops the five basics. Match cards by it with `GameObjectFilter.namedFromVariable(storeAs)`. (Desperate Research)
- `Effects.StoreCardName(from, storeAs)` — capture the name of the first card in collection `from` into `chosenValues[storeAs]`. The "choose a card, then act on cards of that name" counterpart to `ChooseCardName`. (Lobotomy)
- `SelectTargetEffect(...)` — pick from a valid target set.
- `SeparatePermanentsIntoPilesEffect(filter, piles)` — divvy permanents into piles (Fact-or-Fiction shape).

---

## 15. Replacement effects

```kotlin
replacementEffect {
    condition = Conditions.YouControl(Filters.Swamp)
    effect = ReplacementEffect.PreventDamage(1)
}
```

- `ReplacementEffect.PreventDamage(amount?, restrictions?, appliesTo)` — prevent damage matching the
  `GameEvent.DamageEvent` shape. `amount = null` prevents all; a number prevents up to that much.
  `restrictions: List<Condition>` (default empty) gates the prevention on extra conditions evaluated
  against the source's controller — the same pattern as `ModifyLifeLoss.restrictions`. Use it for
  "as long as …, prevent …" statics (Spirit of Resistance: a five-distinct-colors `Compare` gate).
- `CapDamage(maxAmount, appliesTo)` — clamp matching damage to `maxAmount` (a *replacement* distinct
  from prevent/modify; applied after all amplification). Divine Presence: `CapDamage(3, DamageEvent(recipient = Any))`.
- `RedirectDamage(redirectTo, appliesTo)` — redirect matching damage to another recipient. Now wired
  as a continuous static replacement (each source applies at most once per damage event). `redirectTo`
  supports `EffectTarget.ControllerOfDamageSource` (the controller of the damaging source),
  `Controller`/`Self` (the replacement's owner/controller), and `TargetController`. Harsh Judgment:
  redirect chosen-color instant/sorcery damage dealt to you back to the spell's controller.
- **DamageEvent filters (gap #7):** `GameEvent.DamageEvent(recipient, source, damageType, amount)`.
  `amount: AmountFilter` (`Any` / `AtMost(n)` / `AtLeast(n)` / `Exactly(n)`) gates on the would-be
  amount (Callous Giant: `AtMost(3)`). `source = SourceFilter.Matching(filter)` can carry relational
  predicates: `GameObjectFilter.sharingColorWithRecipient()` (`CardPredicate.SharesColorWithRecipient`,
  Well-Laid Plans — "another creature that shares a color") and `sharingChosenColorWithSource()`
  (`CardPredicate.SharesChosenColorWithSource`, reads the replacement source's `ChosenColorComponent`).
- `ReplacementEffect.EntersBattlefieldTappedUnless(condition)` — ETB tapped unless condition met.
- `ReplacementEffect.IfYouDoBranchEffect(...)` — branch on "if you do" replacement.
- `OnEnterRunEffect(effect)` — generic "as ~ enters the battlefield, run [effect]". The wrapped effect
  executes via the normal effect-executor pipeline at entry time (so `EffectTarget.Self` resolves to
  the entering permanent) and may pause for player input. Compose with atomic pausable effects like
  `Effects.MayRevealCardFromHand` to build SOI shadow lands or other "as ~ enters" choices.
  **Scope today:** only wired into the land-play path (`PlayLandHandler`). When the first non-land
  permanent needs this, also wire it into `StackResolver.enterPermanentOnBattlefield`.
- `EntersWithDevour(multiplier, sacrificeFilter, counterType, variant)` — Devour (CR 702.82) and its
  printed variants. As the permanent resolves from the stack, the controller is prompted to pick any
  number of their own permanents matching `sacrificeFilter`. Those permanents are sacrificed and the
  entering permanent gains `multiplier × count` counters of `counterType` (default `+1/+1`). Pair
  with `KeywordAbility.Devour(multiplier, sacrificeFilter, variant)` so the rules text renders. The
  `variant` parameter is a textual tag only — `""` for plain Devour, `"land"` for the EOE
  "Devour land N" wording. **Scope today:** only the stack-spell entry path is wired; reanimation and
  token entries skip Devour (which is fine for printed cards — Devour creatures all cost real mana to
  cast).
- `EntersAsCopy(optional, copyFilter, copyFromZone, filterByTotalManaSpent, additionalSubtypes, additionalKeywords, nameOverride, powerOverride, toughnessOverride, exileCopiedCard)` —
  "enter as a copy of …". As the permanent resolves, the controller picks an object matching
  `copyFilter` and the permanent enters as a copy (Rule 707 copiable values), with any overrides
  applied. `copyFromZone` selects the candidate pool: `Zone.BATTLEFIELD` (default — Clone, Clever
  Impersonator, Mockingbird) copies a permanent in play; `Zone.GRAVEYARD` copies a creature *card*
  from any graveyard (Superior Spider-Man) via the modal card-list overlay. `additionalSubtypes` /
  `additionalKeywords` are added "in addition to its other types"; `nameOverride` keeps a fixed name;
  `powerOverride` / `toughnessOverride` force base P/T; `exileCopiedCard` exiles the copied card after
  the copy ("When you do, exile that card"). `filterByTotalManaSpent` restricts copy targets to mana
  value ≤ total mana spent (Mockingbird). The copy snapshots a `CopyOfComponent` so it reverts to its
  printed identity when it leaves the battlefield (CR 400.7 / 707.2).
- `ModifyDrawAmount(modifier, restrictions, appliesTo)` — modify the number of cards a draw
  instruction announces by a fixed amount, optionally gated by extra `restrictions: List<Condition>`
  evaluated against the drawing player as controller. Applied **once** per draw instruction at the
  announcement site — `DrawCardsExecutor.execute` for spell/ability draws and
  `DrawPhaseManager.performDrawStep` for the draw step (CR 121.2a: "An instruction to draw multiple
  cards can be modified by replacement effects that refer to the number of cards drawn. This
  modification occurs before considering any of the individual card draws.") — so a paused-and-
  resumed per-card loop doesn't double-modify. Note that "you" in restriction text reads as the
  drawing player, not the source's controller; for `DrawEvent(player = Player.You)` they coincide,
  but `DrawEvent(player = Player.Opponent)` cards needing "you" = source controller would have to
  use a source-relative condition instead. Use for "if you would draw one or more cards, you draw
  that many cards plus N instead" (Quantum Riddler:
  `ModifyDrawAmount(modifier = 1, restrictions = listOf(Conditions.CardsInHandAtMost(1)), appliesTo = DrawEvent(player = Player.You))`).
- Custom — implement the `ReplacementEffect` interface directly.

Amount-modifying replacements expose **both** `multiplier` (×) and `modifier` (±) on the same type — do not split into
`DoubleX` + `ModifyXAmount`.

---

## 16. Counters

String-keyed counter types — resolve via the central `resolveCounterType` helper rather than per-executor character
substitution.

- `+1/+1`, `-1/-1` — power/toughness counters.
- `loyalty` — planeswalker loyalty.
- `charge`, `time`, `level`, `quest`, `shield`, `fade`, `vanishing`, `experience`, `age`, `velocity`, `awakening`,
  `blood`, `cage`, `doom`, `storage`, `divinity`, `charm`, `music`, `crumble`, `corpse`, `germ`, `ink`, `growth`,
  `hour`, `energy`, `scry`, `aura`, `chapter`, `citation`, `rune`, `scar`, `crux`, `omen`, `secret`, `feather`,
  `hourglass` — assorted printed counter kinds. (`hourglass`: Temporal Distortion — a permanent with one doesn't untap
  during its controller's untap step; model the restriction with `GrantKeyword(AbilityFlag.DOESNT_UNTAP.name,
  GroupFilter(... .withCounter(Counters.HOURGLASS)))` so it stays projection-scoped.)
- **Keyword counters** (Rule 122.1b) — `flying`, `first strike`, `lifelink`, `indestructible`, `deathtouch`,
  `trample`, `hexproof`, `reach`. `StateProjector` grants the matching `Keyword` to any permanent carrying one (mapped in
  `KEYWORD_COUNTER_MAP`, re-applied after Layer 6 so "loses all abilities" can't wipe a counter-granted keyword).
  Add via `AddCounters(Counters.DEATHTOUCH, ...)` etc.; no static ability needed. (`reach`: Sagu Pummeler's renew
  payoff puts a reach counter on a creature.)

Counter effects live in §4 (`AddCounters`, `RemoveCounters`, `Proliferate`, `MoveAllLastKnownCounters`, etc.).

---

## 17. Zones & movement

**Zones** — `BATTLEFIELD`, `HAND`, `LIBRARY`, `GRAVEYARD`, `EXILE`, `STACK`.

**Primitives**

- `MoveToZoneEffect(target, zone, faceDown?, byDestruction?, linked?)` — single-target move.
- `MoveCollectionEffect(collectionName, zone, faceDown?, linkToSource?, asOwner?, likelyPosition?)` — pipeline move of a
  stored collection.
- `GatherCardsEffect(source, filter, into)` — pipeline gather from a zone into a named collection. `CardSource`
  variants include zones (`FromZone`, `FromMultipleZones`), battlefield queries (`BattlefieldMatching`,
  `ControlledPermanents`), linked exile (`FromLinkedExile`), tapped-as-cost (`TappedAsCost`), and the resolved
  spell/ability targets (`ChosenTargets`). The zone/library sources (`FromZone`, `FromMultipleZones`,
  `TopOfLibrary`) accept a multi-player `player` reference (`Player.Each`, `Player.ActivePlayerFirst`,
  `Player.EachOpponent`) and fan out across every relevant player's copy of the zone in a single gather —
  e.g. "all creature cards in each player's graveyard" (Bringer of the Last Gift). Pair with
  `MoveCollectionEffect(underOwnersControl = true)` to return each card to its owner.
- `CaptureControllersEffect(from, storeAs)` — snapshot each entity's current controller into a parallel
  `List<EntityId>` under `storedCollections[storeAs]`. Required when a later step needs "who controlled
  this card before it left the battlefield" — `ControllerComponent` is stripped on move-out.
- `ForEachCapturedControllerEffect(collection, originalCollection, controllerSnapshot, countVariable?, effects)` —
  cross-references a post-move `collection` against an `originalCollection` + parallel `controllerSnapshot` to
  build per-controller tallies, then runs `effects` once per controller (turn order from the active player). Each
  iteration sets `context.controllerId` to the controller (so `Player.You` / `EffectTarget.Controller` resolve to
  them) and writes the tally into `storedNumbers[countVariable]` (default `"iterationCount"`) for
  `DynamicAmount.VariableReference` to read. Outer `storedCollections` are preserved (unlike
  `ForEachPlayerEffect`). Used by Builder's Bane via the
  `GatherCards(ChosenTargets) → CaptureControllers → MoveCollection(Destroy, storeMovedAs) → ForEachCapturedController`
  shape.
- `ForEachInCollectionEffect(collection, effect)` — run `effect` once per entity in a named pipeline collection
  (snapshotted at resolution), with `pipeline.iterationTarget` set to that entity. Collection-based sibling of
  `ForEachInGroupEffect` (which iterates a battlefield filter): use it to apply a per-entity effect to a *chosen*
  set rather than a re-evaluated filter. Pair with a single-target effect on `EffectTarget.Self` — e.g.
  `ForEachInCollection(nonChosenPile, Effects.CantAttack(EffectTarget.Self))` gives each creature in a chosen pile
  its own snapshot can't-attack floating effect (Fight or Flight / Stand or Fall; creatures entering after the
  split are unaffected).
- `SelectFromCollectionEffect(from, into, selectCount?, allowZero?, alwaysPrompt?, restrictions?)` — let a player pick
  from a collection. `restrictions` (`List<SelectionRestriction>`) cap and trim the picks server-side: `OnePerCardType`,
  `OnePerColor(matchControllerPermanentColors?)`, `OnePerCardName`, `TotalManaValueAtMost(max)`, and
  `OnePerBasicLandType`. `OnePerBasicLandType` keeps at most one land of each basic land type (a kept land claims
  *every* basic type it has) and — unlike `OnePerColor`, where a colourless card is unconstrained — a land with no
  basic land type can't be kept at all (Global Ruin: "chooses a land of each basic land type, then sacrifices the
  rest"). Each restriction also exposes a boolean flag on `SelectCardsDecision` (`onePerBasicLandType`, …) so the UI
  can disable redundant picks.
  - `chooser` (`Chooser`, default `Controller`) — who makes the selection: `Controller`, `Opponent`, `TargetPlayer`
    (`context.targets[0]`), `TriggeringPlayer`, `SourceController` (the source's controller, ignoring per-iteration
    swaps), or `ControllerOfSelection` (the controller of the cards in `from` — resolved from the first card's
    projected controller). Use `ControllerOfSelection` for "their controller chooses…" where the deciding player is
    whoever controls the gathered cards and may be you or an opponent (Barrin's Spite: gather the two targeted
    creatures, their controller sacrifices one, the other is returned to hand). The same `chooser` set is accepted by
    `ChoosePileEffect`.

**Linked exile**

- `Effects.ExileGroupAndLink(filter, storeAs?)` — exile matching permanents linked to source.
- `Effects.ReturnLinkedExile` — return all to controller.
- `ReturnLinkedExileUnderOwnersControl` — return to owners.
- `ReturnLinkedExileToHand` — return to hand.
- `ReturnOneFromLinkedExile` — return one chosen card.
- `CardSource.FromLinkedExile()` — play permission targeting linked-exile pile.
- `CardSource.FromExile(name)` — play permission for a named exile zone.

**Face-down**

- `PutOntoBattlefieldFaceDown(count, target?)` — enter face-down (morph shape).
- `Triggers.TurnedFaceUp` — fires when source flips face-up.
- UI label: `"Turn Face-Up"` (used by E2E `selectAction("Turn Face-Up")`).

---

## 18. Components (set indirectly by effects)

### Permanent

- `ChosenModeComponent` — chosen entry mode (Sieges, modal permanents).
- `TypeLineOverrideComponent` — temporary type-line edits.
- `CountersComponent` — all counters on the permanent.
- `EnchantedCreatureComponent` — reference to attached creature (Auras).
- `EquippedCreatureComponent` — reference to equipped creature.
- `LinkedExileComponent` — linked exile pile attached to source.
- `ExileOnLeaveComponent` — replace next zone change with exile.
- `MayPlayFromExileComponent` — owner may play this from exile.
- `TappedStateComponent` — tap state.
- `FaceDownComponent` — face-down state.
- `ControllerComponent` — current controller.
- `ProtectionComponent` — protection from colors/types.
- `CantAttackComponent` / `CantBlockComponent` — combat restrictions.

### Player

- `PlayerCitysBlessingComponent` — you have City's Blessing.
- `TheRingComponent` — you have the Ring emblem; `temptCount` gates its four abilities (CR 701.52).
- `RingBearerComponent` — designates a creature as a player's Ring-bearer (on the creature, not the player).
- `SpellsCantBeCounteredComponent` — your matching spells can't be countered.
- `LifeGainedAmountThisTurnComponent` — accumulator for life gained.
- `LifeLostThisTurnComponent` — marker that you've lost life this turn.
- `PlayerAttackedThisTurnComponent` — marker that you've attacked this turn.
- `PlayerAttackersThisTurnComponent` — list of attackers declared this turn.
- `LandDropsComponent` — lands played this turn.
- `FoodSacrificeThisTurnComponent` — marker that you sacrificed a Food this turn.
- `SpellsCastThisTurnByPlayer` — count of spells you cast this turn.

Card authors rarely reference these directly; they are created/updated by the matching effect or trigger.

---

## 19. Named-mechanic composites

- **Cycling / Typecycling / Basic landcycling** — `KeywordAbility.Cycling(cost)`, `Typecycling(type, cost)`,
  `BasicLandcycling(cost)`; unified via `TypecyclingVariant(cost, searchFilter, description)` in `TypecycleCardHandler`.
- **Plot (CR 718)** — `KeywordAbility.plot(cost)`. Engine wires a sorcery-speed `PlotEnumerator` + `PlotCardHandler`
  that pays the plot cost, exiles the card face-up from hand, stamps `PlottedComponent(controllerId, turnPlotted)` +
  `PlayWithoutPayingCostComponent`, and adds a permanent `MayPlayPermission` gated by `SourcePlottedOnPriorTurn`.
  The cast-from-exile path is the standard `MayPlayPermission` flow in `CastFromZoneEnumerator` — `permanent = true`
  keeps the grant alive across end-of-turn cleanup. Emits `CardPlottedEvent` / `ClientEvent.CardPlotted`.
- **Adventure (CR 715)** — `layout = ADVENTURE` + `cardFaces[0]` Adventure spell; DSL:
  `card { adventure("Name") { spell { … } } }`.
- **Omen (Tarkir: Dragonstorm)** — `layout = OMEN` + `cardFaces[0]` Omen spell; DSL:
  `card { omen("Name") { spell { … } } }`. Reuses the Adventure cast/enumeration path (`enumerateSecondaryFace`,
  cast via `CastSpell.faceIndex = 0`), but `StackResolver` routes the resolving Omen to `Zone.LIBRARY` and shuffles
  the owner's library (`shuffleOwnerLibrary` + `LibraryShuffledEvent`) instead of exiling with a `MayPlayPermission`.
  No new effect/component — the layout enum drives the resolution fork. First user: Dirgur Island Dragon //
  Skimming Strike.
- **Modal DFC (CR 712)** — `layout = MODAL_DFC` + `cardFaces[0]` back face; DSL:
  `card { modalBack("Name") { imageUri = …; spell { selfExile(); … } } }`. Cast either face from hand (back via
  `CastSpell.faceIndex = 0`); reuses the Adventure cast/enumeration path (`enumerateSecondaryFace`) but with no
  exile-then-recast linkage at resolution. `StackResolver` reads the cast face's `selfExileOnResolve`, and the back
  art rides on `CardFace.imageUri` → `CardComponent.backFaceImageUri`. First user: Flamescroll Celebrant.
- **Hideaway N** — `KeywordAbility.hideaway(n)` (display, "Hideaway N") + `MoveCollectionEffect(faceDown = true,
  linkToSource = true)` + `CardSource.FromLinkedExile()`; no special engine plumbing needed.
- **Ascend / City's Blessing** — `Keyword.ASCEND` + `Effects.GainCitysBlessing()` + `Conditions.YouHaveCitysBlessing` /
  `SourceProjectionCondition.ControllerHasCitysBlessing` + `PlayerCitysBlessingComponent`.
- **Siege (named-mode entry)** — `EntersWithChoice(ChoiceType.MODE, modeOptions = ...)` + `SourceChosenModeIs("id")`.
- **Morph** — `morph = "{2}{U}"` (top-level) + `morphFaceUpEffect` for "as it turns face up".
- **Warp** — `warp = "{1}{R}"`; alt-cost that exiles end of turn.
- **Evoke** — `evoke = "{U}"`; pay alt cost, sacrifice on ETB.
- **Earthbend** — `Effects.Earthbend` composes AnimateLand + GrantKeyword + AddCounters + granted self-triggers (no fake
  keyword).
- **Endure N** — `Effects.Endure(amount, target = EffectTarget.Self)` composes a `ModalEffect.chooseOne` of
  AddDynamicCounters (N +1/+1 counters on the enduring permanent) and a single N/N white Spirit `CreateTokenEffect`
  (no fake keyword — endure is always the effect of a triggered/activated ability, resolved at resolution time). `amount`
  is `DynamicAmount.Fixed` for "endure 2" or any dynamic value for "endure X" (e.g. Warden of the Grove reads
  `EntityProperty(Source, CounterCount(...))`); `target` defaults to `Self` ("it endures") but takes
  `EffectTarget.TriggeringEntity` when a card endures the creature that triggered it.
- **Forage** — `EffectPatterns.forage`; cast-from-graveyard permissions need a branch in `CastSpellHandler.validate`.
- **Blight X** — `AdditionalCost.BlightVariable` + `DynamicAmount.AdditionalCostBlightAmount` +
  `Conditions.BlightWasPaid(n)`.
- **Divvy (Fact-or-Fiction)** — `EffectPatterns.factOrFiction(...)`; `SplitPilesDecision` stays dormant until N > 2.
- **Astral Slide / delayed return** — `ExileUntilEndStepEffect` + `DelayedTriggeredAbility`.
- **Lord effects** — multiple `staticAbility { }` blocks + `ModifyStatsForCreatureGroup` /
  `AffectsFilter.OtherCreaturesWithSubtype`.
- **Player-scoped uncounterable grant** — `Effects.GrantSpellsCantBeCountered(target, filter, duration)` +
  `SpellsCantBeCounteredComponent`.
- **Static emblems** — `Effects.CreatePermanentEmblem(...)` for planeswalker emblems with static abilities.
- **The Ring / the Ring tempts you (CR 701.52)** — `Effects.TheRingTemptsYou(target = Controller)`: the player gets
  the Ring emblem (`TheRingComponent`, tempt-count tracked) and chooses a creature they control to become their
  Ring-bearer (`RingBearerComponent` designation). The emblem's four cumulative abilities are resolved by the engine,
  not card data: the bearer is made legendary in `StateProjector` and can't be blocked by greater power via
  `RingBearerCantBeBlockedByGreaterPowerRule`; the ≥2/≥3/≥4 triggered abilities are appended to the bearer by
  `TriggerAbilityResolver` (see `TheRingAbilities`). For card triggers/checks use `Triggers.RingTemptsYou`
  ("Whenever the Ring tempts you") and `Conditions.SourceIsRingBearer` ("if this is your Ring-bearer").
- **Amass [subtype] N (CR 701.47)** — `Effects.Amass(count, subtype)` (fixed) or
  `Effects.Amass(amount, subtype)` (a `DynamicAmount`, for "amass Orcs X"). `subtype` is required (no default) —
  the amassed Army's type is printed on each card (Orcs for the LTR cards). If the controller controls no Army
  creature, a 0/0 black `[subtype]` Army token is created first (composing `CreateTokenEffect`); then they put N
  +1/+1 counters on an Army they control (a `SelectCardsDecision` resolved by `AmassContinuation` picks which one
  when they control several) and that Army becomes the subtype if it isn't already. The counter/subtype back half
  lives in `AmassResolution`; counters route through `AddCountersEffect`, so placement replacements still apply.

## 20. Miscellaneous author-facing knobs

- `triggeredAbility { controlledByTriggeringEntityController = true }` — the triggered ability is controlled by the
  triggering entity's controller (not source's). Useful for ETB-on-creature triggers and Death Match-style shapes.
- `metadata.oracleTextOverride` — bypass auto-generated oracle text when needed.
- `metadata.inBooster = false` — Special Guests, starter exclusives, bonus sheets.
- `colorIdentity` override is authoritative — never run `:mtg-sets:syncColorIdentityFromDump`.
- Layer dependencies (CR 613.8) — same-layer effects sort by dependency (trial application) before falling back to
  timestamp.
- Server is authoritative; never compute legal actions in the client. Every state change emits a `GameEvent` so triggers
  and animations can react.

---

## Authoritative source files

| Area               | Path                                                            |
|--------------------|-----------------------------------------------------------------|
| Card DSL           | `mtg-sdk/src/main/kotlin/.../dsl/CardBuilder.kt`                |
| Effects            | `mtg-sdk/src/main/kotlin/.../dsl/Effects.kt`                    |
| Effect patterns    | `mtg-sdk/src/main/kotlin/.../dsl/EffectPatterns.kt`             |
| Triggers           | `mtg-sdk/src/main/kotlin/.../dsl/Triggers.kt`                   |
| Costs              | `mtg-sdk/src/main/kotlin/.../dsl/Costs.kt`                      |
| Conditions         | `mtg-sdk/src/main/kotlin/.../dsl/Conditions.kt`                 |
| Filters            | `mtg-sdk/src/main/kotlin/.../dsl/Filters.kt`                    |
| Targets            | `mtg-sdk/src/main/kotlin/.../dsl/Targets.kt`                    |
| Keywords           | `mtg-sdk/src/main/kotlin/.../core/Keyword.kt`                   |
| Card model         | `mtg-sdk/src/main/kotlin/.../model/CardDefinition.kt`           |
| Dynamic amounts    | `mtg-sdk/src/main/kotlin/.../scripting/values/DynamicAmount.kt` |
| Real card examples | `mtg-sets/src/main/kotlin/.../definitions/blb/cards/`           |

For step-by-step authoring workflow see [`api-guide.md`](api-guide.md) and
[`adding-new-cards-workflow.md`](adding-new-cards-workflow.md);
for hard cases see [`managing-complex-and-rare-abilities.md`](managing-complex-and-rare-abilities.md).
