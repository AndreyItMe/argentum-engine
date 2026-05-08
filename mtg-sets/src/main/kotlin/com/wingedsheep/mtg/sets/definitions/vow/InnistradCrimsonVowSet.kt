package com.wingedsheep.mtg.sets.definitions.vow

import com.wingedsheep.mtg.sets.discovery.CardDiscovery
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet

/**
 * Innistrad: Crimson Vow (2021)
 *
 * Set Code: VOW
 * Release Date: November 19, 2021
 */
object InnistradCrimsonVowSet : MtgSet {

    override val code = "VOW"
    override val displayName = "Innistrad: Crimson Vow"

    override val cards: List<CardDefinition> by lazy {
        CardDiscovery.findIn(CARDS_PACKAGE)
    }

    private const val CARDS_PACKAGE = "com.wingedsheep.mtg.sets.definitions.vow.cards"
}
