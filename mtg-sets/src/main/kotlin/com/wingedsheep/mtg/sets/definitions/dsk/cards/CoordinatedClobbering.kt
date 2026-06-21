package com.wingedsheep.mtg.sets.definitions.dsk.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.CollectionFilter
import com.wingedsheep.sdk.scripting.effects.FilterCollectionEffect
import com.wingedsheep.sdk.scripting.effects.ForEachInCollectionEffect
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.targets.TargetCreature
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import com.wingedsheep.sdk.scripting.values.EntityNumericProperty
import com.wingedsheep.sdk.scripting.values.EntityReference

/**
 * Coordinated Clobbering
 * {G}
 * Sorcery
 *
 * Tap one or two target untapped creatures you control. They each deal damage equal to their
 * power to target creature an opponent controls.
 *
 * Two target slots: the single opponent's creature (declared first, so it stays addressable as
 * `ContextTarget(0)` across the per-attacker loop) and one or two untapped creatures you control.
 * At resolution we gather the chosen targets, filter to the creatures you control (excludes the
 * victim), then tap them all and have each deal damage equal to its own power — read per-iteration
 * via [EntityReference.IterationEntity] — to the opponent's creature.
 */
val CoordinatedClobbering = card("Coordinated Clobbering") {
    manaCost = "{G}"
    colorIdentity = "G"
    typeLine = "Sorcery"
    oracleText = "Tap one or two target untapped creatures you control. They each deal damage " +
        "equal to their power to target creature an opponent controls."

    spell {
        // Declared first so the victim is a stable target across the per-creature loop.
        target("creature an opponent controls", Targets.CreatureOpponentControls)
        target(
            "untapped creatures you control",
            TargetCreature(
                count = 2,
                minCount = 1,
                filter = TargetFilter(GameObjectFilter.Creature.untapped().youControl()),
            ),
        )

        effect = Effects.Composite(
            // Gather every chosen target, then keep only the creatures you control (the victim is
            // an opponent's creature, so it drops out).
            GatherCardsEffect(
                source = CardSource.ChosenTargets,
                storeAs = "allTargets",
            ),
            FilterCollectionEffect(
                from = "allTargets",
                filter = CollectionFilter.MatchesFilter(GameObjectFilter.Creature.youControl()),
                storeMatching = "clobberers",
            ),
            // Tap all chosen creatures first ("Tap one or two target untapped creatures you control").
            ForEachInCollectionEffect(
                collection = "clobberers",
                effect = Effects.Tap(EffectTarget.Self),
            ),
            // Then each deals damage equal to its power to the opponent's creature.
            ForEachInCollectionEffect(
                collection = "clobberers",
                effect = Effects.DealDamage(
                    amount = DynamicAmount.EntityProperty(
                        EntityReference.IterationEntity,
                        EntityNumericProperty.Power,
                    ),
                    target = EffectTarget.ContextTarget(0),
                    damageSource = EffectTarget.Self,
                ),
            ),
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "173"
        artist = "Fajareka Setiawan"
        flavorText = "Zimone's theory was that the fractalization of atmospheric aether would " +
            "increase kinetic energy. Tyvar's theory was that if you hit cultists in the face " +
            "really hard, they would fall down. They were both right."
        imageUri = "https://cards.scryfall.io/normal/front/d/4/d498cd5d-5807-4297-bc8a-c0941f2f5ce2.jpg?1726286504"
    }
}
