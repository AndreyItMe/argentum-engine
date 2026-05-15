package com.wingedsheep.mtg.sets.definitions.tla

import com.wingedsheep.mtg.sets.discovery.CardDiscovery
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet

/**
 * Avatar: The Last Airbender (2025)
 *
 * Set Code: TLA
 * Release Date: November 21, 2025
 */
object AvatarTheLastAirbenderSet : MtgSet {

    override val code = "TLA"
    override val displayName = "Avatar: The Last Airbender"
    override val releaseDate = "2025-11-21"

    override val cards: List<CardDefinition> by lazy {
        CardDiscovery.findIn(CARDS_PACKAGE)
    }

    private const val CARDS_PACKAGE = "com.wingedsheep.mtg.sets.definitions.tla.cards"
}
