package com.wingedsheep.mtg.sets.definitions.tla.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.MayEffect
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.targets.TargetPermanent

/**
 * Beetle-Headed Merchants — Avatar: The Last Airbender #86
 * {4}{B} · Creature — Human Citizen · 5/4
 *
 * Whenever this creature attacks, you may sacrifice another creature or artifact.
 * If you do, draw a card and put a +1/+1 counter on this creature.
 *
 * The attack trigger mirrors Namazu Trader's "you may sacrifice another creature or artifact"
 * pay-then-payoff: a [MayEffect] wrapping `Effects.SacrificeTarget` over a `.other()` target
 * (creature or artifact you control, excluding this creature), followed by the payoff —
 * `Effects.DrawCards(1)` then a +1/+1 counter on this creature ([EffectTarget.Self]).
 * Sequencing the payoff after the sacrifice inside the same `MayEffect` makes "If you do"
 * conditional on actually sacrificing.
 */
val BeetleHeadedMerchants = card("Beetle-Headed Merchants") {
    manaCost = "{4}{B}"
    colorIdentity = "B"
    typeLine = "Creature — Human Citizen"
    power = 5
    toughness = 4
    oracleText = "Whenever this creature attacks, you may sacrifice another creature or artifact. " +
        "If you do, draw a card and put a +1/+1 counter on this creature."

    triggeredAbility {
        trigger = Triggers.Attacks
        val sacrificeTarget = target(
            "another creature or artifact",
            TargetPermanent(
                filter = TargetFilter(
                    GameObjectFilter.Creature.youControl().or(GameObjectFilter.Artifact.youControl())
                ).other()
            )
        )
        effect = MayEffect(
            Effects.SacrificeTarget(sacrificeTarget) then
                Effects.DrawCards(1) then
                Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 1, EffectTarget.Self)
        )
        description = "Whenever this creature attacks, you may sacrifice another creature or artifact. " +
            "If you do, draw a card and put a +1/+1 counter on this creature."
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "86"
        artist = "Norikatsu Miyoshi"
        flavorText = "To these desert-dwelling traders, there is profit to be found under every dune " +
            "of sand and in every living thing."
        imageUri = "https://cards.scryfall.io/normal/front/c/2/c2eed79f-38c1-4aac-9525-d54cb114f17f.jpg?1764120595"
    }
}
