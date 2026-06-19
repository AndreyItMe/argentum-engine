package com.wingedsheep.engine.handlers.effects.composite

import com.wingedsheep.engine.core.CoinFlipEvent
import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.scripting.effects.FlipCoinsEffect
import kotlin.reflect.KClass

/**
 * Executor for [FlipCoinsEffect].
 *
 * Flips [FlipCoinsEffect.count] coins, emitting one [CoinFlipEvent] per flip, and publishes the
 * number that came up heads into the pipeline (`storedNumbers[storeHeadsAs]`) via
 * [EffectResult.updatedStoredNumbers] so a later sub-effect in the same composite can scale off it
 * with [com.wingedsheep.sdk.scripting.values.DynamicAmount.VariableReference] — exactly how
 * [StoreNumberExecutor][com.wingedsheep.engine.handlers.effects.library.StoreNumberExecutor]
 * surfaces a value.
 *
 * "Heads" is modelled as a won flip, matching the rest of the coin-flip plumbing
 * ([FlipCoinExecutor]).
 */
class FlipCoinsExecutor : EffectExecutor<FlipCoinsEffect> {

    override val effectType: KClass<FlipCoinsEffect> = FlipCoinsEffect::class

    override fun execute(
        state: GameState,
        effect: FlipCoinsEffect,
        context: EffectContext
    ): EffectResult {
        val sourceId = context.sourceId
        val sourceName = sourceId?.let { state.getEntity(it)?.get<CardComponent>()?.name } ?: "Unknown"

        var currentState = state
        val events = mutableListOf<GameEvent>()
        var heads = 0
        repeat(effect.count.coerceAtLeast(0)) {
            val (won, advanced) = currentState.nextRandom { nextBoolean() }
            currentState = advanced
            if (won) heads++
            events.add(CoinFlipEvent(context.controllerId, won, sourceId ?: context.controllerId, sourceName))
        }

        return EffectResult(
            state = currentState,
            events = events,
            updatedStoredNumbers = mapOf(effect.storeHeadsAs to heads)
        )
    }
}
