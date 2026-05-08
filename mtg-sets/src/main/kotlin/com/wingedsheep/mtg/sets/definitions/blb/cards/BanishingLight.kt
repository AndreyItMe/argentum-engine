package com.wingedsheep.mtg.sets.definitions.blb.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetPermanent

/**
 * Banishing Light
 * {2}{W}
 * Enchantment
 * When this enchantment enters, exile target nonland permanent an opponent controls
 * until this enchantment leaves the battlefield.
 */
val BanishingLight = card("Banishing Light") {
    manaCost = "{2}{W}"
    typeLine = "Enchantment"
    oracleText = "When this enchantment enters, exile target nonland permanent an opponent controls until this enchantment leaves the battlefield."

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        val permanent = target(
            "nonland permanent an opponent controls",
            TargetPermanent(filter = TargetFilter.NonlandPermanentOpponentControls)
        )
        effect = Effects.ExileUntilLeaves(permanent)
    }

    triggeredAbility {
        trigger = Triggers.LeavesBattlefield
        effect = Effects.ReturnLinkedExileUnderOwnersControl()
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "1"
        artist = "Zoltan Boros"
        flavorText = "\"If you cannot find light in the darkness, create it.\"\n—Aural, high cleric"
        imageUri = "https://cards.scryfall.io/normal/front/2/5/25a06f82-ebdb-4dd6-bfe8-958018ce557c.jpg?1721425761"
    }
}
