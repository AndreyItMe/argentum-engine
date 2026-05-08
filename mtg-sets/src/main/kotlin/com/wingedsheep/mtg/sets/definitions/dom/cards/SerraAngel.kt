package com.wingedsheep.mtg.sets.definitions.dom.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/**
 * Serra Angel
 * {3}{W}{W}
 * Creature — Angel
 * 4/4
 * Flying, vigilance
 */
val SerraAngel = card("Serra Angel") {
    manaCost = "{3}{W}{W}"
    typeLine = "Creature — Angel"
    power = 4
    toughness = 4
    oracleText = "Flying, vigilance"

    keywords(Keyword.FLYING, Keyword.VIGILANCE)

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "33"
        artist = "Donato Giancola"
        flavorText = "The angel remembers her past lives like dreams. Her song held up meadows. Her blade drove back darkness. Her wings carried her across the ages."
        imageUri = "https://cards.scryfall.io/normal/front/b/5/b56b9131-4f7e-4912-ba47-63ed82f21d1b.jpg?1562741601"
    }
}
