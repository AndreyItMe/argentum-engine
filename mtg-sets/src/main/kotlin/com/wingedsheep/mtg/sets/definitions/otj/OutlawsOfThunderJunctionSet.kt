package com.wingedsheep.mtg.sets.definitions.otj

import com.wingedsheep.mtg.sets.definitions.otj.cards.*
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet

/**
 * Outlaws of Thunder Junction (2024)
 *
 * Set Code: OTJ
 * Release Date: April 19, 2024
 */
object OutlawsOfThunderJunctionSet : MtgSet {

    override val code = "OTJ"
    override val displayName = "Outlaws of Thunder Junction"

    override val cards: List<CardDefinition> = listOf(
        BristlyBillSpineSower,
    )
}
