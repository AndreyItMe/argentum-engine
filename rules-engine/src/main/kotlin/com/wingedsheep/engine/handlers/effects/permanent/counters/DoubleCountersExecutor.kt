package com.wingedsheep.engine.handlers.effects.permanent.counters

import com.wingedsheep.engine.core.CountersAddedEvent
import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.DamageUtils
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.handlers.effects.ReplacementEffectUtils
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.AbilityFlag
import com.wingedsheep.sdk.scripting.effects.DoubleCountersEffect
import kotlin.reflect.KClass

/**
 * Executor for [DoubleCountersEffect].
 * "Double the number of +1/+1 counters on that creature."
 *
 * Reads the current count of the named counter kind on the target and puts that
 * many more on it, so the total doubles. The added counters go through the normal
 * counter-placement replacement path (e.g., Hardened Scales), mirroring the rules
 * treatment of doubling as additional counter placement. No-op when the target has
 * no counters of that kind or can't receive counters.
 */
class DoubleCountersExecutor : EffectExecutor<DoubleCountersEffect> {

    override val effectType: KClass<DoubleCountersEffect> = DoubleCountersEffect::class

    override fun execute(
        state: GameState,
        effect: DoubleCountersEffect,
        context: EffectContext
    ): EffectResult {
        val targetId = context.resolveTarget(effect.target, state)
            ?: return EffectResult.error(state, "No valid target to double counters on")

        if (state.projectedState.hasKeyword(targetId, AbilityFlag.CANT_RECEIVE_COUNTERS)) {
            return EffectResult.success(state, emptyList())
        }

        val counterType = resolveCounterType(effect.counterType)
        val current = state.getEntity(targetId)?.get<CountersComponent>() ?: CountersComponent()
        val existing = current.getCount(counterType)
        if (existing <= 0) {
            return EffectResult.success(state, emptyList())
        }

        // Doubling places `existing` additional counters; honor placement replacements.
        val added = ReplacementEffectUtils.applyCounterPlacementModifiers(
            state, targetId, counterType, existing, placerId = context.controllerId
        )

        val newState = state.updateEntity(targetId) { container ->
            container.with(current.withAdded(counterType, added))
        }.let { DamageUtils.markCounterPlacedOnCreature(it, context.controllerId, targetId) }

        val entityName = state.getEntity(targetId)?.get<CardComponent>()?.name ?: ""

        return EffectResult.success(
            newState,
            listOf(CountersAddedEvent(targetId, effect.counterType, added, entityName))
        )
    }
}
