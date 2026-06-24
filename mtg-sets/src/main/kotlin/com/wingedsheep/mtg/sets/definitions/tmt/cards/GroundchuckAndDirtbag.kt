package com.wingedsheep.mtg.sets.definitions.tmt.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.references.Player

/**
 * Groundchuck & Dirtbag
 * {4}{G}{G}
 * Legendary Creature — Ox Mole Mutant
 * 8/8
 *
 * Trample
 * Whenever you tap a land for mana, add {G}.
 */
val GroundchuckAndDirtbag = card("Groundchuck & Dirtbag") {
    manaCost = "{4}{G}{G}"
    colorIdentity = "G"
    typeLine = "Legendary Creature — Ox Mole Mutant"
    oracleText = "Trample\nWhenever you tap a land for mana, add {G}."
    power = 8
    toughness = 8

    keywords(Keyword.TRAMPLE)

    triggeredAbility {
        trigger = Triggers.landTappedForMana(player = Player.You)
        effect = Effects.AddMana(Color.GREEN, 1)
        description = "Whenever you tap a land for mana, add {G}."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "115"
        artist = "Nicholas Gregory"
        flavorText = "\"Whatever my lil' man D.B. here can't dig under, I can just about bust through. Ain't no one caging us ever again.\""
        imageUri = "https://cards.scryfall.io/normal/front/1/5/1563592e-0f21-4488-a3ec-ca766386e423.jpg?1769006182"
    }
}
