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
import com.wingedsheep.sdk.scripting.effects.FlipTwoCoinsEffect
import kotlin.reflect.KClass

/**
 * Executor for FlipTwoCoinsEffect.
 * Flips two coins and executes the appropriate sub-effect based on the combined outcome. A
 * [CoinFlipModifiers] result replacement (e.g. Edgar's Two-Headed Coin) forces both coins to heads
 * (CR 705.3) — the two coins are one flip event, so a first-flip-each-turn replacement covers both.
 */
class FlipTwoCoinsExecutor(
    private val cardRegistry: CardRegistry,
    private val effectExecutor: (GameState, Effect, EffectContext) -> EffectResult
) : EffectExecutor<FlipTwoCoinsEffect> {

    override val effectType: KClass<FlipTwoCoinsEffect> = FlipTwoCoinsEffect::class

    override fun execute(
        state: GameState,
        effect: FlipTwoCoinsEffect,
        context: EffectContext
    ): EffectResult {
        val forced = CoinFlipModifiers.shouldForceWin(state, cardRegistry, context.controllerId)
        val (coin1, s1) = if (forced) true to state else state.nextRandom { nextBoolean() }
        val (coin2, s2) = if (forced) true to s1 else s1.nextRandom { nextBoolean() }
        val marked = CoinFlipModifiers.markFlipped(s2, context.controllerId)

        val sourceId = context.sourceId
        val sourceName = sourceId?.let { state.getEntity(it)?.get<CardComponent>()?.name } ?: "Unknown"

        val events = listOf(
            CoinFlipEvent(context.controllerId, coin1, sourceId ?: context.controllerId, sourceName),
            CoinFlipEvent(context.controllerId, coin2, sourceId ?: context.controllerId, sourceName)
        )

        val subEffect = when {
            coin1 && coin2 -> effect.bothHeadsEffect
            !coin1 && !coin2 -> effect.bothTailsEffect
            else -> effect.mixedEffect
        }

        if (subEffect == null) {
            return EffectResult.success(marked, events)
        }

        val result = effectExecutor(marked, subEffect, context)
        return result.copy(events = events + result.events)
    }
}
