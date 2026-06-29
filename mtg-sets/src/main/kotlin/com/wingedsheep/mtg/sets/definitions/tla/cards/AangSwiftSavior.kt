package com.wingedsheep.mtg.sets.definitions.tla.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.ConditionalEffect
import com.wingedsheep.sdk.scripting.effects.CounterDestination
import com.wingedsheep.sdk.scripting.effects.CounterEffect
import com.wingedsheep.sdk.scripting.effects.CounterTargetSource
import com.wingedsheep.sdk.scripting.effects.TransformEffect
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.targets.TargetObject
import com.wingedsheep.sdk.scripting.targets.TargetOther

/**
 * Aang, Swift Savior // Aang and La, Ocean's Fury
 * {1}{W}{U} — Legendary Creature — Human Avatar Ally 2/3
 * //  — Legendary Creature — Avatar Spirit Ally 5/5
 *
 * Front — Aang, Swift Savior:
 *   Flash, Flying
 *   When Aang enters, airbend up to one other target creature or spell. (Exile it. While it's
 *   exiled, its owner may cast it for {2} rather than its mana cost.)
 *   Waterbend {8}: Transform Aang.
 *
 * Back — Aang and La, Ocean's Fury:
 *   Reach, trample
 *   Whenever Aang and La attack, put a +1/+1 counter on each tapped creature you control.
 *
 * The "airbend … target creature **or spell**" is the airbend STACK branch. The single target is a
 * cross-zone union ([TargetFilter.anyOf] of a battlefield creature and a stack spell), and the effect
 * branches on whether the chosen target is a spell ([Conditions.TargetIsSpellOnStack]): a spell is
 * countered and exiled with its owner granted the {2} recast
 * ([CounterDestination.Exile]`(ownerControls, fixedAlternativeManaCost)`); a permanent takes the
 * normal [Effects.Airbend] exile path. Both grant the *owner* a fixed-{2} may-play from exile via the
 * same `PlayWithFixedAlternativeManaCostComponent`. The transform is a Waterbend-cost activated
 * ability (`hasWaterbend`).
 */
private val AangAndLaOceansFury = card("Aang and La, Ocean's Fury") {
    manaCost = ""
    colorIdentity = "WU"
    typeLine = "Legendary Creature — Avatar Spirit Ally"
    oracleText = "Reach, trample\n" +
        "Whenever Aang and La attack, put a +1/+1 counter on each tapped creature you control."
    power = 5
    toughness = 5

    keywords(Keyword.REACH, Keyword.TRAMPLE)

    triggeredAbility {
        trigger = Triggers.Attacks
        effect = Effects.ForEachInGroup(
            GroupFilter(GameObjectFilter.Creature.youControl().tapped()),
            Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 1, EffectTarget.Self)
        )
        description = "Whenever Aang and La attack, put a +1/+1 counter on each tapped creature you control."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "204"
        artist = "Tetsuko"
        imageUri = "https://cards.scryfall.io/normal/back/8/2/82866a0e-485a-4f7e-8c49-f7d9ff3f4ad4.jpg?1778914119"
    }
}

private val AangSwiftSaviorFront = card("Aang, Swift Savior") {
    manaCost = "{1}{W}{U}"
    colorIdentity = "WU"
    typeLine = "Legendary Creature — Human Avatar Ally"
    oracleText = "Flash\n" +
        "Flying\n" +
        "When Aang enters, airbend up to one other target creature or spell. (Exile it. While it's exiled, its owner may cast it for {2} rather than its mana cost.)\n" +
        "Waterbend {8}: Transform Aang."
    power = 2
    toughness = 3

    keywords(Keyword.FLASH, Keyword.FLYING)

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        target(
            "up to one other target creature or spell",
            TargetOther(
                baseRequirement = TargetObject(
                    count = 1,
                    optional = true,
                    filter = TargetFilter.anyOf(TargetFilter.Creature, TargetFilter.SpellOnStack)
                )
            )
        )
        effect = ConditionalEffect(
            condition = Conditions.TargetIsSpellOnStack(0),
            // Spell branch: counter it, exile it, owner may recast for {2}.
            effect = CounterEffect(
                targetSource = CounterTargetSource.Chosen,
                counterDestination = CounterDestination.Exile(
                    ownerControls = true,
                    fixedAlternativeManaCost = ManaCost.parse("{2}")
                )
            ),
            // Permanent branch: the normal airbend exile + {2}-recast-to-owner.
            elseEffect = Effects.Airbend()
        )
        description = "When Aang enters, airbend up to one other target creature or spell."
    }

    // Waterbend {8}: Transform Aang.
    activatedAbility {
        cost = Costs.Mana("{8}")
        hasWaterbend = true
        effect = TransformEffect(EffectTarget.Self)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "204"
        artist = "Tetsuko"
        imageUri = "https://cards.scryfall.io/normal/front/8/2/82866a0e-485a-4f7e-8c49-f7d9ff3f4ad4.jpg?1778914119"
    }
}

val AangSwiftSavior: CardDefinition = CardDefinition.doubleFacedCreature(
    frontFace = AangSwiftSaviorFront,
    backFace = AangAndLaOceansFury,
)
