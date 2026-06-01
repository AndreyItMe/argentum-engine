package com.wingedsheep.mtg.sets.definitions.lci.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.EffectPatterns
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/**
 * Ruin-Lurker Bat
 * {W}
 * Creature — Bat
 * 1/1
 *
 * Flying, lifelink
 * At the beginning of your end step, if you descended this turn, scry 1.
 *
 * "Descended this turn" is CR 700.11: at least one nontoken permanent card was
 * put into your graveyard from any zone this turn. The condition is the
 * `Conditions.YouDescendedThisTurn()` SDK gate (defaults to `atLeast = 1`),
 * which reads the per-player descend counter maintained by `ZoneTransitionService`
 * and cleared each turn by `CleanupPhaseManager`.
 *
 * Per the Scryfall ruling, the ability fires only once per end step regardless
 * of how many times you descended that turn — the intervening-if gate is a
 * boolean check on the counter, not a multiplier.
 */
val RuinLurkerBat = card("Ruin-Lurker Bat") {
    manaCost = "{W}"
    colorIdentity = "W"
    typeLine = "Creature — Bat"
    power = 1
    toughness = 1
    oracleText = "Flying, lifelink\n" +
        "At the beginning of your end step, if you descended this turn, scry 1. " +
        "(You descended if a permanent card was put into your graveyard from anywhere.)"

    keywords(Keyword.FLYING, Keyword.LIFELINK)

    triggeredAbility {
        trigger = Triggers.YourEndStep
        triggerCondition = Conditions.YouDescendedThisTurn()
        effect = EffectPatterns.scry(1)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "33"
        artist = "Camille Alquier"
        flavorText = "\"Look! Aclazotz sends his children to watch over our pilgrimage.\"\n" +
            "—Clavileño, First of the Blessed"
        imageUri = "https://cards.scryfall.io/normal/front/d/6/d6bedf13-c2bc-4e5d-aba3-3c0d5495a9bb.jpg?1699043372"
        ruling("2023-11-10", "You descended if a permanent card was put into your graveyard from anywhere this turn. Tokens are not cards, and putting a token into the graveyard doesn't count.")
        ruling("2023-11-10", "The ability triggers only once during your end step, no matter how many times you descended that turn. If you haven't descended by the time your end step begins, it won't trigger at all.")
    }
}
