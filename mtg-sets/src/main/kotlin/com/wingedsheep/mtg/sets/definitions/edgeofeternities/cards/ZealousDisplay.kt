package com.wingedsheep.mtg.sets.definitions.edgeofeternities.cards

import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.conditions.IsNotYourTurn
import com.wingedsheep.sdk.scripting.effects.CompositeEffect
import com.wingedsheep.sdk.scripting.effects.ConditionalEffect
import com.wingedsheep.sdk.scripting.effects.ForEachInGroupEffect
import com.wingedsheep.sdk.scripting.effects.ModifyStatsEffect
import com.wingedsheep.sdk.scripting.effects.TapUntapEffect
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter

/**
 * Zealous Display
 * {2}{W}
 * Instant
 * Creatures you control get +2/+0 until end of turn. If it's not your turn, untap those creatures.
 */
val ZealousDisplay = card("Zealous Display") {
    manaCost = "{2}{W}"
    typeLine = "Instant"
    oracleText = "Creatures you control get +2/+0 until end of turn. If it's not your turn, untap those creatures."

    spell {
        effect = CompositeEffect(
            listOf(
                // Creatures you control get +2/+0 until end of turn
                ForEachInGroupEffect(
                    GroupFilter.AllCreaturesYouControl,
                    ModifyStatsEffect(2, 0, EffectTarget.Self)
                ),
                // If it's not your turn, untap those creatures
                ConditionalEffect(
                    condition = IsNotYourTurn,
                    effect = ForEachInGroupEffect(
                        GroupFilter.AllCreaturesYouControl,
                        TapUntapEffect(EffectTarget.Self, tap = false)
                    )
                )
            )
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "45"
        artist = "Chris Rallis"
        flavorText = "The Sunstar Free Company is not swayed by profit. Sunstar is their faith, the prism through which they cast light across Sothera."
        imageUri = "https://cards.scryfall.io/normal/front/f/5/f5973fd3-d6bc-48f1-8a44-57d2a6dda228.jpg?1752946729"
    }
}
