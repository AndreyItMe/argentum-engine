package com.wingedsheep.mtg.sets.definitions.ecl.cards

import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.EntersTapped

/**
 * Overgrown Tomb
 * Land — Swamp Forest
 *
 * ({T}: Add {B} or {G}.)
 * As this land enters, you may pay 2 life. If you don't, it enters tapped.
 */
val OvergrownTomb = card("Overgrown Tomb") {
    typeLine = "Land — Swamp Forest"
    oracleText = "({T}: Add {B} or {G}.)\nAs this land enters, you may pay 2 life. If you don't, it enters tapped."

    // Mana abilities are intrinsic from basic land types (Swamp → {B}, Forest → {G})

    replacementEffect(EntersTapped(payLifeCost = 2))

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "266"
        artist = "Adam Paquette"
        flavorText = "The elf was disgusted by the bramble before him when the skies darkened, then he stepped closer to observe the wild beauty."
        imageUri = "https://cards.scryfall.io/normal/front/4/5/45b92924-baa1-4c9b-9932-9a5eda8f3446.jpg?1759144847"
    }
}
