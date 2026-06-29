package com.wingedsheep.mtg.sets.definitions.jmp

import com.wingedsheep.mtg.sets.discovery.CardDiscovery
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet
import com.wingedsheep.sdk.model.Printing

/**
 * Jumpstart
 *
 * Set Code: JMP
 */
object JumpstartSet : MtgSet {

    override val code = "JMP"
    override val displayName = "Jumpstart"
    override val releaseDate = "2020-07-17"
    override val incomplete = true

    override val cards: List<CardDefinition> by lazy {
        CardDiscovery.findIn(CARDS_PACKAGE)
    }

    override val printings: List<Printing> by lazy {
        CardDiscovery.findPrintingsIn(CARDS_PACKAGE)
    }

    private const val CARDS_PACKAGE = "com.wingedsheep.mtg.sets.definitions.jmp.cards"
}
