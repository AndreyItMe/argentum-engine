package com.wingedsheep.mtg.sets.definitions.inv.cards

import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.CantBlock
import com.wingedsheep.sdk.scripting.ModifyStats
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter

/**
 * Maniacal Rage
 * {1}{R}
 * Enchantment — Aura
 * Enchant creature
 * Enchanted creature gets +2/+2 and can't block.
 */
val ManiacalRage = card("Maniacal Rage") {
    manaCost = "{1}{R}"
    colorIdentity = "R"
    typeLine = "Enchantment — Aura"
    oracleText = "Enchant creature\nEnchanted creature gets +2/+2 and can't block."

    auraTarget = Targets.Creature

    staticAbility {
        ability = ModifyStats(2, 2)
    }

    staticAbility {
        ability = CantBlock(filter = GroupFilter.attachedCreature())
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "155"
        artist = "Matt Cavotta"
        imageUri = "https://cards.scryfall.io/normal/front/3/d/3d17886c-fffd-4f0d-b4da-4b5fba18b811.jpg?1595075978"
    }
}
