package com.wingedsheep.mtg.sets.definitions.ktk.cards

import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.predicates.CardPredicate
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.SearchDestination
import com.wingedsheep.sdk.dsl.EffectPatterns

/**
 * Flooded Strand
 * Land
 * {T}, Pay 1 life, Sacrifice Flooded Strand: Search your library for a Plains or Island card,
 * put it onto the battlefield, then shuffle.
 */
val FloodedStrand = card("Flooded Strand") {
    typeLine = "Land"
    oracleText = "{T}, Pay 1 life, Sacrifice Flooded Strand: Search your library for a Plains or Island card, put it onto the battlefield, then shuffle."

    activatedAbility {
        cost = Costs.Composite(Costs.Tap, Costs.PayLife(1), Costs.SacrificeSelf)
        effect = EffectPatterns.searchLibrary(
            filter = GameObjectFilter(
                cardPredicates = listOf(
                    CardPredicate.IsLand,
                    CardPredicate.Or(
                        listOf(
                            CardPredicate.HasSubtype(Subtype("Plains")),
                            CardPredicate.HasSubtype(Subtype("Island"))
                        )
                    )
                )
            ),
            destination = SearchDestination.BATTLEFIELD,
            entersTapped = false,
            shuffleAfter = true
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "233"
        artist = "Andreas Rocha"
        flavorText = "Where dragons once slept, their bones now rest."
        imageUri = "https://cards.scryfall.io/normal/front/8/c/8c2996d9-3287-4480-8c04-7a378e37e3cf.jpg?1707237513"
    }
}
