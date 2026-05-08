package com.wingedsheep.mtg.sets.definitions.dom.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.conditions.NotCondition
import com.wingedsheep.sdk.scripting.conditions.WasKicked
import com.wingedsheep.sdk.scripting.effects.SacrificeSelfEffect

/**
 * Skizzik
 * {3}{R}
 * Creature — Elemental
 * 5/3
 * Kicker {R}
 * Trample, haste
 * At the beginning of the end step, if this creature wasn't kicked, sacrifice it.
 */
val Skizzik = card("Skizzik") {
    manaCost = "{3}{R}"
    typeLine = "Creature — Elemental"
    power = 5
    toughness = 3
    oracleText = "Kicker {R}\nTrample, haste\nAt the beginning of the end step, if this creature wasn't kicked, sacrifice it."

    keywordAbility(KeywordAbility.Kicker(ManaCost.parse("{R}")))
    keywords(Keyword.TRAMPLE, Keyword.HASTE)

    triggeredAbility {
        trigger = Triggers.EachEndStep
        triggerCondition = NotCondition(WasKicked)
        effect = SacrificeSelfEffect
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "145"
        artist = "Tomasz Jedruszek"
        flavorText = "\"It skitters across Shiv, each tendril hitting the ground with a sharp snap.\""
        imageUri = "https://cards.scryfall.io/normal/front/7/7/77af9d28-1639-47bd-b925-7f3d2eefd352.jpg?1615334491"
    }
}
