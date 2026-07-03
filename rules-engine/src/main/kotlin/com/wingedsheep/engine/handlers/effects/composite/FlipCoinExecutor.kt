package com.wingedsheep.engine.handlers.effects.composite

import com.wingedsheep.engine.core.CoinFlipEvent
import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.CoinFlipModifiers
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.scripting.effects.Effect
import com.wingedsheep.sdk.scripting.effects.FlipCoinEffect
import kotlin.reflect.KClass

/**
 * Executor for FlipCoinEffect.
 * Flips a coin (50/50 random) and executes the appropriate sub-effect. A [CoinFlipModifiers]
 * result replacement (e.g. Edgar's Two-Headed Coin) can force the flip to a win (CR 705.3).
 *
 * @param cardRegistry Used to look up coin-flip result replacements the flipping player controls.
 * @param effectExecutor Function to execute a sub-effect (provided by registry)
 */
class FlipCoinExecutor(
    private val cardRegistry: CardRegistry,
    private val effectExecutor: (GameState, Effect, EffectContext) -> EffectResult
) : EffectExecutor<FlipCoinEffect> {

    override val effectType: KClass<FlipCoinEffect> = FlipCoinEffect::class

    override fun execute(
        state: GameState,
        effect: FlipCoinEffect,
        context: EffectContext
    ): EffectResult {
        val forced = CoinFlipModifiers.shouldForceWin(state, cardRegistry, context.controllerId)
        val (won, afterFlip) = if (forced) true to state else state.nextRandom { nextBoolean() }
        val marked = CoinFlipModifiers.markFlipped(afterFlip, context.controllerId)

        val sourceId = context.sourceId
        val sourceName = sourceId?.let { state.getEntity(it)?.get<CardComponent>()?.name } ?: "Unknown"
        val event = CoinFlipEvent(context.controllerId, won, sourceId ?: context.controllerId, sourceName)

        val subEffect = if (won) effect.wonEffect else effect.lostEffect

        if (subEffect == null) {
            return EffectResult.success(marked, listOf(event))
        }

        val result = effectExecutor(marked, subEffect, context)
        return result.copy(events = listOf(event) + result.events)
    }
}
