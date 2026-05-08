package com.wingedsheep.mtg.sets.definitions.lci

import com.wingedsheep.mtg.sets.definitions.lci.cards.*
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet

/**
 * The Lost Caverns of Ixalan Set (2023)
 *
 * Set Code: LCI
 * Release Date: November 17, 2023
 */
object LostCavernsOfIxalanSet : MtgSet {

    override val code = "LCI"
    override val displayName = "The Lost Caverns of Ixalan"



    /**
     * All cards implemented from this set.
     */
    override val cards: List<CardDefinition> = listOf(
        MalcolmAlluringScoundrel,
    )
}
