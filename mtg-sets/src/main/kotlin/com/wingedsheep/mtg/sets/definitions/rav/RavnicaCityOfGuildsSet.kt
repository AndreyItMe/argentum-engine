package com.wingedsheep.mtg.sets.definitions.rav

import com.wingedsheep.mtg.sets.discovery.CardDiscovery
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet
import com.wingedsheep.sdk.model.Printing

/**
 * Ravnica: City of Guilds (2005)
 *
 * Set Code: RAV
 * Release Date: October 7, 2005
 */
object RavnicaCityOfGuildsSet : MtgSet {

    override val code = "RAV"
    override val displayName = "Ravnica: City of Guilds"
    override val releaseDate = "2005-10-07"
    override val block = "Ravnica"
    override val incomplete = true

    override val cards: List<CardDefinition> by lazy {
        CardDiscovery.findIn(CARDS_PACKAGE)
    }

    override val basicLands: List<CardDefinition> by lazy {
        CardDiscovery.findBasicLandsIn(CARDS_PACKAGE).map { it.copy(setCode = code) }
    }

    override val printings: List<Printing> by lazy {
        CardDiscovery.findPrintingsIn(CARDS_PACKAGE)
    }

    private const val CARDS_PACKAGE = "com.wingedsheep.mtg.sets.definitions.rav.cards"
}
