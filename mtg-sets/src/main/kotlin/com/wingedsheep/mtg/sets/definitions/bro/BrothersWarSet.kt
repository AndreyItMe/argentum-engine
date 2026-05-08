package com.wingedsheep.mtg.sets.definitions.bro

import com.wingedsheep.mtg.sets.definitions.bro.cards.*
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet

/**
 * The Brothers' War (2022)
 *
 * Set Code: BRO
 * Release Date: November 18, 2022
 */
object BrothersWarSet : MtgSet {

    override val code = "BRO"
    override val displayName = "The Brothers' War"

    override val cards: List<CardDefinition> = listOf(
        FlowOfKnowledge,
        BattlefieldForge,
        Brushland,
        LlanowarWastes,
        SoulPartition,
        TeferiTemporalPilgrim,
        UndergroundRiver,
    )
}
