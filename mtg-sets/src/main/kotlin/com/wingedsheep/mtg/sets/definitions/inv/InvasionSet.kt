package com.wingedsheep.mtg.sets.definitions.inv

import com.wingedsheep.mtg.sets.discovery.CardDiscovery
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet

/**
 * Invasion (2000)
 *
 * Set Code: INV
 * Release Date: October 2, 2000
 *
 * First set in the Invasion block. Heavy multicolor theme; home of the
 * canonical "divvy" mechanic exemplar Fact or Fiction (CR 700.3 piles).
 */
object InvasionSet : MtgSet {

    override val code = "INV"
    override val displayName = "Invasion"
    override val releaseDate = "2000-10-02"
    override val block = "Invasion"
    override val incomplete = true

    override val cards: List<CardDefinition> by lazy {
        CardDiscovery.findIn(CARDS_PACKAGE)
    }

    private const val CARDS_PACKAGE = "com.wingedsheep.mtg.sets.definitions.inv.cards"
}
