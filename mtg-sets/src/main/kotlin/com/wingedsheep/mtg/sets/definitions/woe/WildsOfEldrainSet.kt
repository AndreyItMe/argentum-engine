package com.wingedsheep.mtg.sets.definitions.woe

import com.wingedsheep.mtg.sets.definitions.woe.cards.*
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet

/**
 * Wilds of Eldraine (2023)
 *
 * Set Code: WOE
 * Release Date: September 8, 2023
 */
object WildsOfEldrainSet : MtgSet {

    override val code = "WOE"
    override val displayName = "Wilds of Eldraine"

    override val cards: List<CardDefinition> = listOf(
        SpellbookVendor,
        SleepCursedFaerie,
    )
}
