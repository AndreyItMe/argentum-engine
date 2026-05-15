package com.wingedsheep.mtg.sets.definitions.inr

import com.wingedsheep.mtg.sets.discovery.CardDiscovery
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet

/**
 * Innistrad Remastered (2025)
 *
 * Set Code: INR
 * Release Date: January 24, 2025
 */
object InnistradRemasteredSet : MtgSet {

    override val code = "INR"
    override val displayName = "Innistrad Remastered"
    override val releaseDate = "2025-01-24"

    override val cards: List<CardDefinition> by lazy {
        CardDiscovery.findIn(CARDS_PACKAGE)
    }

    private const val CARDS_PACKAGE = "com.wingedsheep.mtg.sets.definitions.inr.cards"
}
