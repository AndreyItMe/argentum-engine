package com.wingedsheep.mtg.sets.definitions.lgn.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.ProtectionScope

/**
 * White Knight
 * {W}{W}
 * Creature — Human Knight
 * 2/2
 * First strike, protection from black
 */
val WhiteKnight = card("White Knight") {
    manaCost = "{W}{W}"
    typeLine = "Creature — Human Knight"
    power = 2
    toughness = 2
    oracleText = "First strike, protection from black"

    keywords(Keyword.FIRST_STRIKE)
    keywordAbility(KeywordAbility.Protection(ProtectionScope.Color(Color.BLACK)))

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "27"
        artist = "Edward P. Beard, Jr."
        flavorText = "In the wretched depths of the Grand Coliseum, his soul shines like a single torch blazing in the night."
        imageUri = "https://cards.scryfall.io/normal/front/c/b/cb9cb8ed-7abb-4e71-b42f-5041dd0c0394.jpg?1562935895"
    }
}
