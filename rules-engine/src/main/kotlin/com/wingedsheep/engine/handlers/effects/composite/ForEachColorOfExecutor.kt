package com.wingedsheep.engine.handlers.effects.composite

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.handlers.effects.TargetResolutionUtils
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.Effect
import com.wingedsheep.sdk.scripting.effects.ForEachColorOfEffect
import kotlin.reflect.KClass

/**
 * Executor for [ForEachColorOfEffect] — the non-interactive sibling of `ChooseColorThen`.
 *
 * Resolves the source entity from its [EntityReference], reads its colors, then runs the inner
 * effect once per color with that color placed in [EffectContext.chosenColor] (the same channel
 * `ChooseColorThen` feeds), so per-color atoms like `GrantProtectionFromChosenColor` compose
 * inside it. Iterates in canonical WUBRG order for deterministic events.
 *
 * Source colors are read from projected state while the source is on the battlefield (so Layer-5
 * "becomes colorless"/Devoid and color-adding effects are honored, including an authoritative
 * empty set), otherwise from its base printed colors as last-known information (CR 113.7a). A
 * colorless source runs the inner effect zero times (colorless is not a color, CR 105.2).
 */
class ForEachColorOfExecutor(
    private val effectExecutor: (GameState, Effect, EffectContext) -> EffectResult
) : EffectExecutor<ForEachColorOfEffect> {

    override val effectType: KClass<ForEachColorOfEffect> = ForEachColorOfEffect::class

    override fun execute(
        state: GameState,
        effect: ForEachColorOfEffect,
        context: EffectContext
    ): EffectResult {
        val sourceId = TargetResolutionUtils.resolveEntityReference(effect.source, context, state)
            ?: return EffectResult.success(state)

        val colorNames = readSourceColors(state, sourceId)
        // Canonical WUBRG order for deterministic event sequencing; skips colors not present.
        val colors = Color.entries.filter { it.name in colorNames }
        if (colors.isEmpty()) return EffectResult.success(state)

        var currentState = state
        val events = mutableListOf<GameEvent>()
        for (color in colors) {
            val innerContext = context.copy(chosenColor = color)
            val result = effectExecutor(currentState, effect.effect, innerContext)
            if (result.isPaused) {
                return EffectResult.paused(result.state, result.pendingDecision!!, events + result.events)
            }
            currentState = result.newState
            events.addAll(result.events)
        }
        return EffectResult.success(currentState, events)
    }

    private fun readSourceColors(state: GameState, entityId: EntityId): Set<String> {
        // On battlefield → projection is authoritative, including an empty set (the source is
        // legitimately colorless via Devoid or a Layer-5 "becomes colorless" effect) and any
        // colors added by a Layer-5 effect. Off battlefield → no projectedValues entry exists,
        // so fall back to base printed colors as last-known-information.
        if (state.getBattlefield().contains(entityId)) {
            return state.projectedState.getColors(entityId)
        }
        val card = state.getEntity(entityId)?.get<CardComponent>() ?: return emptySet()
        return card.colors.map { it.name }.toSet()
    }
}
