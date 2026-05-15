package com.wingedsheep.mtg.sets.definitions.ecl

import com.wingedsheep.mtg.sets.definitions.ons.OnslaughtSet
import com.wingedsheep.mtg.sets.discovery.CardDiscovery
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet

/**
 * Lorwyn Eclipsed Set (2026)
 *
 * A return to the Lorwyn/Shadowmoor plane featuring tribal themes
 * with Elves, Kithkin, Merfolk, Goblins, and Elementals.
 *
 * Set Code: ECL
 * Release Date: January 23, 2026
 * Card Count: 273
 */
object LorwynEclipsedSet : MtgSet {

    override val code = "ECL"
    override val displayName = "Lorwyn Eclipsed"
    override val releaseDate = "2026-01-23"
    override val sealedSupported = true

    override val cards: List<CardDefinition> by lazy {
        CardDiscovery.findIn(CARDS_PACKAGE)
    }

    override val basicLands: List<CardDefinition> by lazy {
        CardDiscovery.findBasicLandsIn(CARDS_PACKAGE).map { it.copy(setCode = OnslaughtSet.code) }
    }

    private const val CARDS_PACKAGE = "com.wingedsheep.mtg.sets.definitions.ecl.cards"
}
