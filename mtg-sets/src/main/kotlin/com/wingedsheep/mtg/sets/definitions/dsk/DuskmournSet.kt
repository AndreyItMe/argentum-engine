package com.wingedsheep.mtg.sets.definitions.dsk

import com.wingedsheep.mtg.sets.definitions.dsk.cards.*
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet

/**
 * Duskmourn: House of Horror (2024)
 *
 * Set Code: DSK
 * Release Date: September 27, 2024
 */
object DuskmournSet : MtgSet {

    override val code = "DSK"
    override val displayName = "Duskmourn: House of Horror"

    override val cards: List<CardDefinition> = listOf(
        BlazemireVerge,
        EtherealArmor,
        FloodfarmVerge,
        GloomlakeVerge,
        HushwoodVerge,
        OptimisticScavenger,
        ShardmagesRescue,
        ShelteredByGhosts,
        ThornspireVerge,
        VeteranSurvivor,
    )
}
