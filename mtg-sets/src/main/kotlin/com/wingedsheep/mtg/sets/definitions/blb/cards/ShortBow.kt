package com.wingedsheep.mtg.sets.definitions.blb.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Filters
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/**
 * Short Bow
 * {2}
 * Artifact — Equipment
 *
 * Equipped creature gets +1/+1 and has vigilance and reach.
 * Equip {1}
 */
val ShortBow = card("Short Bow") {
    manaCost = "{2}"
    typeLine = "Artifact — Equipment"
    oracleText = "Equipped creature gets +1/+1 and has vigilance and reach.\nEquip {1}"

    staticAbility {
        effect = Effects.ModifyStats(+1, +1)
        filter = Filters.EquippedCreature
    }

    staticAbility {
        effect = Effects.GrantKeyword(Keyword.VIGILANCE)
        filter = Filters.EquippedCreature
    }

    staticAbility {
        effect = Effects.GrantKeyword(Keyword.REACH)
        filter = Filters.EquippedCreature
    }

    equipAbility("{1}")

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "248"
        artist = "Zara Alfonso"
        flavorText = "Take heart. Take aim. Take them down."
        imageUri = "https://cards.scryfall.io/normal/front/5/1/51d8b72b-fa8f-48d3-bddc-d3ce9b8ba2ea.jpg?1721427294"
    }
}
