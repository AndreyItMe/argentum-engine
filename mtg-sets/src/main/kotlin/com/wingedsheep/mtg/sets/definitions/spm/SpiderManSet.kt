package com.wingedsheep.mtg.sets.definitions.spm

import com.wingedsheep.mtg.sets.definitions.spm.cards.*
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet

/**
 * Marvel's Spider-Man (2025)
 *
 * Set Code: SPM
 * Release Date: September 26, 2025
 */
object SpiderManSet : MtgSet {

    override val code = "SPM"
    override val displayName = "Marvel's Spider-Man"

    override val cards: List<CardDefinition> = listOf(
        OriginOfSpiderMan,
        SkywardSpider,
    )
}
