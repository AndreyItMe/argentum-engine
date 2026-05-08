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
 * Polluted Delta
 * Land
 * {T}, Pay 1 life, Sacrifice Polluted Delta: Search your library for an Island or Swamp card,
 * put it onto the battlefield, then shuffle.
 */
val PollutedDelta = card("Polluted Delta") {
    typeLine = "Land"
    oracleText = "{T}, Pay 1 life, Sacrifice Polluted Delta: Search your library for an Island or Swamp card, put it onto the battlefield, then shuffle."

    activatedAbility {
        cost = Costs.Composite(Costs.Tap, Costs.PayLife(1), Costs.SacrificeSelf)
        effect = EffectPatterns.searchLibrary(
            filter = GameObjectFilter(
                cardPredicates = listOf(
                    CardPredicate.IsLand,
                    CardPredicate.Or(
                        listOf(
                            CardPredicate.HasSubtype(Subtype("Island")),
                            CardPredicate.HasSubtype(Subtype("Swamp"))
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
        collectorNumber = "239"
        artist = "Vincent Proce"
        flavorText = "Where dragons once prevailed, their bones now sink."
        imageUri = "https://cards.scryfall.io/normal/front/f/f/ff2f5f58-9a95-4ca6-93a0-813738f0072f.jpg?1707235020"
    }
}
