package com.wingedsheep.mtg.sets.definitions.dom.cards

import com.wingedsheep.sdk.dsl.EffectPatterns
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/**
 * Opt
 * {U}
 * Instant
 * Scry 1. Draw a card.
 */
val Opt = card("Opt") {
    manaCost = "{U}"
    typeLine = "Instant"
    oracleText = "Scry 1. (Look at the top card of your library. You may put that card on the bottom of your library.)\nDraw a card."

    spell {
        effect = EffectPatterns.scry(1).then(Effects.DrawCards(1))
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "60"
        artist = "Tyler Jacobson"
        flavorText = "The crystal pulsed with the power of Teferi's planeswalker spark. Had Jhoira given him a blessing or a curse?"
        imageUri = "https://cards.scryfall.io/normal/front/2/5/25f2e4d0-effd-4e83-b7aa-1a0d8f120951.jpg?1562732870"
    }
}
