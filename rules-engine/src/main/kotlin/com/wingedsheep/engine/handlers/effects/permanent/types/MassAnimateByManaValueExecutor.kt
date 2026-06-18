package com.wingedsheep.engine.handlers.effects.permanent.types

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.BattlefieldFilterUtils
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.mechanics.layers.Layer
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.mechanics.layers.Sublayer
import com.wingedsheep.engine.mechanics.layers.addFloatingEffect
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.scripting.effects.MassAnimateByManaValueEffect
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import com.wingedsheep.sdk.scripting.values.EntityNumericProperty
import com.wingedsheep.sdk.scripting.values.EntityReference
import kotlin.reflect.KClass

/**
 * Executor for [MassAnimateByManaValueEffect].
 *
 * Captures every permanent matching the filter against the *current* battlefield (CR 611.2c —
 * the set of affected permanents is locked in at resolution time), then layers floating
 * continuous effects keyed to that set for [MassAnimateByManaValueEffect.duration]:
 *   - Layer 4 (TYPE): AddType("CREATURE")
 *   - Layer 6 (ABILITY): RemoveAllAbilities (when requested)
 *   - Layer 7b (POWER_TOUGHNESS, SET_VALUES): base P/T each equal to the permanent's own mana value
 *
 * Each P/T floating effect resolves its dynamic amount per affected entity
 * ([EntityReference.AffectedEntity]), so every animated permanent gets its own mana value — not
 * the source's. This is the one-shot "this effect continues until end of turn" companion to the
 * continuous group statics (GrantCardType + LoseAllAbilities + SetBasePowerToughnessDynamicStatic)
 * used while Titania's Song is on the battlefield.
 */
class MassAnimateByManaValueExecutor : EffectExecutor<MassAnimateByManaValueEffect> {

    override val effectType: KClass<MassAnimateByManaValueEffect> = MassAnimateByManaValueEffect::class

    override fun execute(
        state: GameState,
        effect: MassAnimateByManaValueEffect,
        context: EffectContext
    ): EffectResult {
        val affectedEntities = BattlefieldFilterUtils.findMatchingOnBattlefield(
            state, effect.filter, context
        ).toSet()

        if (affectedEntities.isEmpty()) {
            return EffectResult.success(state)
        }

        // Layer 4 (TYPE): becomes a creature.
        var newState = state.addFloatingEffect(
            layer = Layer.TYPE,
            modification = SerializableModification.AddType("CREATURE"),
            affectedEntities = affectedEntities,
            duration = effect.duration,
            context = context
        )

        // Layer 6 (ABILITY): loses all abilities.
        if (effect.loseAllAbilities) {
            newState = newState.addFloatingEffect(
                layer = Layer.ABILITY,
                modification = SerializableModification.RemoveAllAbilities,
                affectedEntities = affectedEntities,
                duration = effect.duration,
                context = context
            )
        }

        // Layer 7b (POWER_TOUGHNESS, SET_VALUES): base P/T = each permanent's own mana value.
        val manaValue: DynamicAmount = DynamicAmount.EntityProperty(
            entity = EntityReference.AffectedEntity,
            numericProperty = EntityNumericProperty.ManaValue
        )
        newState = newState.addFloatingEffect(
            layer = Layer.POWER_TOUGHNESS,
            sublayer = Sublayer.SET_VALUES,
            modification = SerializableModification.SetPowerToughnessDynamic(manaValue, manaValue),
            affectedEntities = affectedEntities,
            duration = effect.duration,
            context = context
        )

        return EffectResult.success(newState)
    }
}
