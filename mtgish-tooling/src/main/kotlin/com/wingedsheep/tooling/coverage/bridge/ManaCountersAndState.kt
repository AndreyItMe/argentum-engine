package com.wingedsheep.tooling.coverage.bridge

/** Mana, counters, control, and combat/untap-state effects. Mostly the "universal" verbs that Portal
 *  never exercised but every later set does — each line lifts recall on every set at once. */
internal fun BridgeBuilder.manaCountersAndState() {
    // AddMana's exact Effect depends on the produced symbol — colorless ({C}) serialises as
    // AddColorlessMana, "any color" as AddManaOfChoice, a fixed colour as AddMana — and the capability
    // scorer can't see the symbol arg, so name the whole mana family the action can lower to.
    composed("AddMana", UNIVERSAL, composes = listOf("AddMana", "AddColorlessMana", "AddManaOfChoice"))
    composed("AddManaRepeated", UNIVERSAL, composes = listOf("AddMana", "AddColorlessMana", "AddManaOfChoice"))
    effect("AddColorlessMana", "AddColorlessMana", UNIVERSAL)

    // CreateTokens lowers to either the generic CreateToken (creature tokens) or a predefined-token
    // facade (Treasure -> CreatePredefinedToken); the capability scorer can't see which from the tag
    // alone, so name both — mirroring the AddMana family above.
    composed("CreateTokens", UNIVERSAL, composes = listOf("CreateToken", "CreatePredefinedToken"))
    // "becomes the creature type of your choice" — a ChooseACreatureType + an AddCreatureTypeVariable
    // layer effect collapse to one BecomeCreatureTypeEffect (Mistform cycle, Imagecrafter).
    effect("AddCreatureTypeVariable", "BecomeCreatureType", UNIVERSAL)
    effects("PutACounterOfTypeOnPermanent", "PutNumberCountersOfTypeOnPermanent", tag = "AddCounters", note = UNIVERSAL)

    effect("RegeneratePermanent", "Regenerate", UNIVERSAL)
    effects("GainControlOfPermanent", "GainControlOfPermanentUntil", tag = "GainControl", note = UNIVERSAL)
    effect("RemoveCreatureFromCombat", "RemoveFromCombat", UNIVERSAL)
    // Goad (CR 701.15): targeted GoadCreature renders; mass GoadEachCreature carries the same
    // capability but its group filter often scaffolds in the emitter.
    effects("GoadCreature", "GoadEachCreature", tag = "Goad", note = UNIVERSAL)

    effect("EachPermanentDoesntUntapDuringControllersNextUntap", "SkipUntap",
        "Exhaustion: target player's creatures+lands don't untap next untap step")
    effect("SkipAllCombatPhasesTheirNextTurn", "SkipCombatPhases",
        "False Peace: target skips all combat phases of their next turn")
}
