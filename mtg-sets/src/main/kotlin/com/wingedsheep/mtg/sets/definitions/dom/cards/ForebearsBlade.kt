package com.wingedsheep.mtg.sets.definitions.dom.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Filters
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/**
 * Forebear's Blade
 * {3}
 * Artifact — Equipment
 * Equipped creature gets +3/+0 and has vigilance and trample.
 * Whenever equipped creature dies, attach Forebear's Blade to target creature you control.
 * Equip {3}
 */
val ForebearsBlade = card("Forebear's Blade") {
    manaCost = "{3}"
    typeLine = "Artifact — Equipment"
    oracleText = "Equipped creature gets +3/+0 and has vigilance and trample.\n" +
        "Whenever equipped creature dies, attach Forebear's Blade to target creature you control.\n" +
        "Equip {3}"

    staticAbility {
        effect = Effects.ModifyStats(+3, +0)
        filter = Filters.EquippedCreature
    }

    staticAbility {
        effect = Effects.GrantKeyword(Keyword.VIGILANCE)
        filter = Filters.EquippedCreature
    }

    staticAbility {
        effect = Effects.GrantKeyword(Keyword.TRAMPLE)
        filter = Filters.EquippedCreature
    }

    triggeredAbility {
        trigger = Triggers.EquippedCreatureDies
        val creature = target("creature you control", Targets.CreatureYouControl)
        effect = Effects.AttachEquipment(creature)
    }

    equipAbility("{3}")

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "214"
        artist = "Scott Murphy"
        imageUri = "https://cards.scryfall.io/normal/front/5/2/52212fd5-551e-4bc1-9dac-6361e27c27ad.jpg?1681500928"
    }
}
