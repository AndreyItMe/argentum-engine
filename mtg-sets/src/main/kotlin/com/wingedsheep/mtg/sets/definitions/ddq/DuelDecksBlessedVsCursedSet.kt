package com.wingedsheep.mtg.sets.definitions.ddq

import com.wingedsheep.mtg.sets.discovery.CardDiscovery
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet
import com.wingedsheep.sdk.model.Printing

/**
 * Duel Decks: Blessed vs. Cursed (2016)
 *
 * mtgish-tooling seed: only the cards relocated here as their canonical earliest printing.
 * Intentionally incomplete relative to the official set.
 *
 * Set Code: DDQ
 * Release Date: 2016-02-26
 */
object DuelDecksBlessedVsCursedSet : MtgSet {

    override val code = "DDQ"
    override val displayName = "Duel Decks: Blessed vs. Cursed"
    override val releaseDate = "2016-02-26"
    override val sealedSupported = false
    override val incomplete = true

    override val cards: List<CardDefinition> by lazy {
        CardDiscovery.findIn(CARDS_PACKAGE)
    }

    override val basicLands: List<CardDefinition> by lazy {
        CardDiscovery.findBasicLandsIn(CARDS_PACKAGE, code)
    }

    override val printings: List<Printing> by lazy {
        CardDiscovery.findPrintingsIn(CARDS_PACKAGE)
    }

    private const val CARDS_PACKAGE = "com.wingedsheep.mtg.sets.definitions.ddq.cards"
}
