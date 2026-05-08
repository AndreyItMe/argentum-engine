package com.wingedsheep.mtg.sets.definitions.dom.cards

import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GrantCantBeBlockedToSmallCreatures

/**
 * Tetsuko Umezawa, Fugitive
 * {1}{U}
 * Legendary Creature — Human Rogue
 * 1/3
 * Creatures you control with power or toughness 1 or less can't be blocked.
 */
val TetsukoUmezawaFugitive = card("Tetsuko Umezawa, Fugitive") {
    manaCost = "{1}{U}"
    typeLine = "Legendary Creature — Human Rogue"
    power = 1
    toughness = 3
    oracleText = "Creatures you control with power or toughness 1 or less can't be blocked."

    staticAbility {
        ability = GrantCantBeBlockedToSmallCreatures(maxValue = 1)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "69"
        artist = "Randy Vargas"
        flavorText = "\"My ancestor Toshiro used to say, 'Life is a series of choices between bad and worse.' I say it's time to find a third option.\""
        imageUri = "https://cards.scryfall.io/normal/front/1/6/16185c50-f7b8-4cea-a129-dfad8e9df781.jpg?1591605108"
    }
}
