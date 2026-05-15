package com.wingedsheep.mtg.sets.definitions.por

import com.wingedsheep.mtg.sets.discovery.CardDiscovery
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet

/**
 * Portal Set (1997)
 *
 * Portal was an introductory set designed to teach new players
 * the basics of Magic: The Gathering with simplified rules.
 *
 * Set Code: POR
 * Release Date: June 1997
 * Card Count: 222
 */
object PortalSet : MtgSet {

    override val code = "POR"
    override val displayName = "Portal"
    override val releaseDate = "1997-05-01"
    override val sealedSupported = true

    override val cards: List<CardDefinition> by lazy {
        CardDiscovery.findIn(CARDS_PACKAGE)
    }

    override val basicLands: List<CardDefinition> by lazy {
        CardDiscovery.findBasicLandsIn(CARDS_PACKAGE).map { it.copy(setCode = code) }
    }

    private const val CARDS_PACKAGE = "com.wingedsheep.mtg.sets.definitions.por.cards"
}
