package com.wingedsheep.engine.handlers.effects.drawing

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.scripting.effects.EachPlayerDrawsForDamageDealtToSourceEffect
import kotlin.reflect.KClass

/**
 * Executor for [EachPlayerDrawsForDamageDealtToSourceEffect].
 *
 * Reads [EffectContext.triggerLastKnownDamageDealtByPlayers] (captured on the trigger's
 * source when it left the battlefield) and has each tracked player draw that many cards.
 * Players that dealt no damage to the source this turn are not included in the map and
 * therefore draw nothing. Used for Grothama, All-Devouring's LTB ability.
 */
class EachPlayerDrawsForDamageDealtToSourceExecutor(
    private val drawCardsExecutor: DrawCardsExecutor
) : EffectExecutor<EachPlayerDrawsForDamageDealtToSourceEffect> {

    override val effectType: KClass<EachPlayerDrawsForDamageDealtToSourceEffect> =
        EachPlayerDrawsForDamageDealtToSourceEffect::class

    override fun execute(
        state: GameState,
        effect: EachPlayerDrawsForDamageDealtToSourceEffect,
        context: EffectContext
    ): EffectResult {
        val perPlayer = context.triggerLastKnownDamageDealtByPlayers ?: emptyMap()
        if (perPlayer.isEmpty()) return EffectResult.success(state, emptyList())

        // Resolve in turn order so APNAP-ish ordering is deterministic.
        val ordered = state.turnOrder.filter { perPlayer.containsKey(it) && (perPlayer[it] ?: 0) > 0 }

        var currentState = state
        val events = mutableListOf<GameEvent>()
        for (playerId in ordered) {
            val baseCount = perPlayer[playerId] ?: continue
            if (baseCount <= 0) continue
            // Each player's draw is its own announcement (CR 121.2c — each player's draws
            // resolve in turn order as separate instructions), so ModifyDrawAmount (CR 121.2a)
            // applies per-player. Without this, e.g. Quantum Riddler on a player's side
            // wouldn't add +1 to their Grothama-LTB draw while their hand-size restriction held.
            val count = drawCardsExecutor.applyDrawAmountModifier(currentState, playerId, baseCount)
            if (count <= 0) continue
            val result = drawCardsExecutor.executeDraws(currentState, playerId, count)
            currentState = result.state
            events.addAll(result.events)
            if (result.pendingDecision != null) {
                return EffectResult.paused(currentState, result.pendingDecision, events)
            }
        }
        return EffectResult.success(currentState, events)
    }
}
