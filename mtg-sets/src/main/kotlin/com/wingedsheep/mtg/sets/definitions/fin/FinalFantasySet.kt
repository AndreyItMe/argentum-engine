package com.wingedsheep.mtg.sets.definitions.fin

import com.wingedsheep.mtg.sets.discovery.CardDiscovery
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet

/**
 * Final Fantasy (2025)
 *
 * Set Code: FIN
 * Release Date: June 13, 2025
 */
object FinalFantasySet : MtgSet {

    override val code = "FIN"
    override val displayName = "Final Fantasy"

    override val cards: List<CardDefinition> by lazy {
        CardDiscovery.findIn(CARDS_PACKAGE)
    }

    private const val CARDS_PACKAGE = "com.wingedsheep.mtg.sets.definitions.fin.cards"
}
