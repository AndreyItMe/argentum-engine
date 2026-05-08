package com.wingedsheep.mtg.sets.definitions.blb.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/**
 * Run Away Together
 * {1}{U}
 * Instant
 *
 * Choose two target creatures controlled by different players.
 * Return those creatures to their owners' hands.
 *
 * In a 2-player game, this is equivalent to targeting one creature
 * you control and one creature an opponent controls.
 */
val RunAwayTogether = card("Run Away Together") {
    manaCost = "{1}{U}"
    typeLine = "Instant"
    oracleText = "Choose two target creatures controlled by different players. Return those creatures to their owners' hands."

    spell {
        val t1 = target("creature you control", Targets.CreatureYouControl)
        val t2 = target("creature an opponent controls", Targets.CreatureOpponentControls)
        effect = Effects.ReturnToHand(t1)
            .then(Effects.ReturnToHand(t2))
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "67"
        artist = "Omar Rayyan"
        flavorText = "Nobody bothered to look for Libo and Pulla, because everyone knew they didn't want to be found."
        imageUri = "https://cards.scryfall.io/normal/front/7/c/7cb7ec70-a5a4-4188-ba1a-e88b81bdbad0.jpg?1721426215"
    }
}
