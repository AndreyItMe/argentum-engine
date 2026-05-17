package com.wingedsheep.mtg.sets.definitions.mir

import com.wingedsheep.mtg.sets.definitions.por.PortalSet
import com.wingedsheep.mtg.sets.discovery.CardDiscovery
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet
import com.wingedsheep.sdk.model.Printing

/**
 * Mirage Set (1996)
 *
 * Mirage was the first set in the Mirage block, set on the African-inspired
 * plane of Jamuraa. It introduced Flanking and Phasing, and was the first
 * expansion designed as part of a planned block.
 *
 * Set Code: MIR
 * Release Date: October 8, 1996
 * Card Count: 350
 */
object MirageSet : MtgSet {

    override val code = "MIR"
    override val displayName = "Mirage"
    override val releaseDate = "1996-10-08"
    override val block = "Mirage"
    override val basicLandsFallback = PortalSet
    override val incomplete = true

    override val cards: List<CardDefinition> by lazy {
        CardDiscovery.findIn(CARDS_PACKAGE)
    }

    override val printings: List<Printing> by lazy {
        CardDiscovery.findPrintingsIn(CARDS_PACKAGE)
    }

    private const val CARDS_PACKAGE = "com.wingedsheep.mtg.sets.definitions.mir.cards"
}
