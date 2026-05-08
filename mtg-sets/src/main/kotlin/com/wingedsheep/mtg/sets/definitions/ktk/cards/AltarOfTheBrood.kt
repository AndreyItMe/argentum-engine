package com.wingedsheep.mtg.sets.definitions.ktk.cards

import com.wingedsheep.sdk.dsl.EffectPatterns
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.ForEachPlayerEffect
import com.wingedsheep.sdk.scripting.references.Player

/**
 * Altar of the Brood
 * {1}
 * Artifact
 * Whenever another permanent you control enters, each opponent mills a card.
 */
val AltarOfTheBrood = card("Altar of the Brood") {
    manaCost = "{1}"
    typeLine = "Artifact"
    oracleText = "Whenever another permanent you control enters, each opponent mills a card."

    triggeredAbility {
        trigger = Triggers.OtherPermanentYouControlEnters
        effect = ForEachPlayerEffect(
            players = Player.EachOpponent,
            effects = EffectPatterns.mill(1).effects
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "216"
        artist = "Erica Yang"
        flavorText = "Supplicants offer flesh and silver, flowers and blood. The altar takes what it will, eyes gleaming with unspoken promises."
        imageUri = "https://cards.scryfall.io/normal/front/8/d/8d59d264-87ee-4305-bffb-110549331a82.jpg?1562790137"
    }
}
