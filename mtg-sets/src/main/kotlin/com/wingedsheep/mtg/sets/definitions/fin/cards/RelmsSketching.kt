package com.wingedsheep.mtg.sets.definitions.fin.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.predicates.CardPredicate
import com.wingedsheep.sdk.scripting.targets.TargetPermanent

/**
 * Relm's Sketching
 * {2}{U}{U}
 * Sorcery
 * Create a token that's a copy of target artifact, creature, or land.
 *
 * The token copies the printed characteristics of the target (CR 707.2) via
 * [Effects.CreateTokenCopyOfTarget]. Target is any permanent that is an artifact, a creature,
 * or a land.
 */
val RelmsSketching = card("Relm's Sketching") {
    manaCost = "{2}{U}{U}"
    colorIdentity = "U"
    typeLine = "Sorcery"
    oracleText = "Create a token that's a copy of target artifact, creature, or land."

    spell {
        val t = target(
            "artifact, creature, or land",
            TargetPermanent(
                filter = TargetFilter(
                    GameObjectFilter(
                        cardPredicates = listOf(
                            CardPredicate.Or(
                                listOf(
                                    CardPredicate.IsArtifact,
                                    CardPredicate.IsCreature,
                                    CardPredicate.IsLand,
                                )
                            )
                        )
                    )
                )
            )
        )
        effect = Effects.CreateTokenCopyOfTarget(target = t)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "67"
        artist = "Smirtouille"
        flavorText = "In her pictures she captures everything: forests, water, light . . . " +
            "the very essence of the things she paints."
        imageUri = "https://cards.scryfall.io/normal/front/6/a/6aedac12-3714-4a81-bd4d-1d2555c66f78.jpg?1748706008"
    }
}
