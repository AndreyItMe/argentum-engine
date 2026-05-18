package com.wingedsheep.mtg.sets.definitions.eoe.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.AbilityCost
import com.wingedsheep.sdk.scripting.ActivationRestriction
import com.wingedsheep.sdk.scripting.EntersTapped
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.conditions.Compare
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator
import com.wingedsheep.sdk.scripting.events.CounterTypeFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import com.wingedsheep.sdk.scripting.values.EntityNumericProperty
import com.wingedsheep.sdk.scripting.values.EntityReference

/**
 * Uthros, Titanic Godcore
 * Land — Planet
 * This land enters tapped.
 * {T}: Add {U}.
 * Station (Tap another creature you control: Put charge counters equal to its power on this Planet. Station only as a sorcery.)
 * 12+ | {U}, {T}: Add {U} for each artifact you control.
 */
val UthrosTitanicGodcore = card("Uthros, Titanic Godcore") {
    typeLine = "Land — Planet"
    colorIdentity = "U"
    oracleText = "This land enters tapped.\n" +
        "{T}: Add {U}.\n" +
        "Station (Tap another creature you control: Put charge counters equal to its power on this Planet. Station only as a sorcery.)\n" +
        "12+ | {U}, {T}: Add {U} for each artifact you control."

    // This land enters tapped
    replacementEffect(EntersTapped())

    // Basic mana ability: {T}: Add {U}
    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddMana(Color.BLUE)
        manaAbility = true
    }

    // Station activated ability: tap another creature → add charge counters equal to its power
    activatedAbility {
        cost = AbilityCost.TapPermanents(
            count = 1,
            filter = GameObjectFilter.Creature,
            excludeSelf = true
        )
        effect = Effects.AddDynamicCounters(
            counterType = Counters.CHARGE,
            amount = DynamicAmount.EntityProperty(
                entity = EntityReference.TappedAsCost(),
                numericProperty = EntityNumericProperty.Power
            ),
            target = EffectTarget.Self
        )
        timing = TimingRule.SorcerySpeed
    }

    // Conditional mana ability: {U}, {T}: Add {U} for each artifact you control at 12+ charge counters
    activatedAbility {
        cost = Costs.Composite(
            Costs.Mana("{U}"),
            Costs.Tap
        )
        effect = Effects.AddMana(
            Color.BLUE,
            DynamicAmounts.battlefield(Player.You, GameObjectFilter.Artifact).count()
        )
        restrictions = listOf(
            ActivationRestriction.OnlyIfCondition(
                Compare(
                    left = DynamicAmount.EntityProperty(
                        entity = EntityReference.Source,
                        numericProperty = EntityNumericProperty.CounterCount(CounterTypeFilter.Named(Counters.CHARGE))
                    ),
                    operator = ComparisonOperator.GTE,
                    right = DynamicAmount.Fixed(12)
                )
            )
        )
        manaAbility = true
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "260"
        artist = "Adam Paquette"
        imageUri = "https://cards.scryfall.io/normal/front/1/1/11da39d6-cfa6-498d-91b1-11454cc7e5a3.jpg?1755341453"
    }
}
