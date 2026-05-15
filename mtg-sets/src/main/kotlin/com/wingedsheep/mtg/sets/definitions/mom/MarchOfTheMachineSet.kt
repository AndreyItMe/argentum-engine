package com.wingedsheep.mtg.sets.definitions.mom

import com.wingedsheep.mtg.sets.discovery.CardDiscovery
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet
import com.wingedsheep.sdk.model.Printing

/**
 * March of the Machine (2023)
 *
 * Set Code: MOM
 * Release Date: April 21, 2023
 */
object MarchOfTheMachineSet : MtgSet {

    override val code = "MOM"
    override val displayName = "March of the Machine"
    override val releaseDate = "2023-04-21"

    override val cards: List<CardDefinition> by lazy {
        CardDiscovery.findIn(CARDS_PACKAGE)
    }

    override val printings: List<Printing> by lazy {
        CardDiscovery.findPrintingsIn(CARDS_PACKAGE)
    }

    private const val CARDS_PACKAGE = "com.wingedsheep.mtg.sets.definitions.mom.cards"
}
