package com.wingedsheep.mtg.sets.definitions.mid

import com.wingedsheep.mtg.sets.definitions.mid.cards.Consider
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet
import com.wingedsheep.mtg.sets.definitions.mid.cards.Dissipate
import com.wingedsheep.mtg.sets.definitions.mid.cards.FadingHope

/**
 * Innistrad: Midnight Hunt (2021)
 *
 * Set Code: MID
 * Release Date: September 24, 2021
 */
object InnistradMidnightHuntSet : MtgSet {

    override val code = "MID"
    override val displayName = "Innistrad: Midnight Hunt"

    override val cards: List<CardDefinition> = listOf(
        Consider,
        Dissipate,
        FadingHope,
    )
}
