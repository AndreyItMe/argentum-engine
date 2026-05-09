package com.wingedsheep.engine.handlers.effects.mana

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.mechanics.mana.LandManaColorInspector
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.AddManaOfColorLandsCouldProduceEffect
import com.wingedsheep.sdk.scripting.effects.LandControllerScope
import kotlin.reflect.KClass

/**
 * Executor for [AddManaOfColorLandsCouldProduceEffect].
 * "{T}: Add one mana of any color that a land an opponent controls could produce."
 *
 * Determines the set of available colors by inspecting the mana abilities of every land
 * controlled by players in the configured scope. Activation costs are ignored (CR rulings),
 * and colorless production is excluded — only colored mana is produced.
 *
 * If no land in scope can produce any colored mana, the ability resolves with no effect.
 */
class AddManaOfColorLandsCouldProduceExecutor(
    private val cardRegistry: CardRegistry,
) : EffectExecutor<AddManaOfColorLandsCouldProduceEffect> {

    override val effectType: KClass<AddManaOfColorLandsCouldProduceEffect> =
        AddManaOfColorLandsCouldProduceEffect::class

    override fun execute(
        state: GameState,
        effect: AddManaOfColorLandsCouldProduceEffect,
        context: EffectContext,
    ): EffectResult {
        val availableColors = availableColors(state, effect.scope, context.controllerId)
        if (availableColors.isEmpty()) {
            return EffectResult.success(state)
        }

        val chosenColor = context.manaColorChoice?.takeIf { it in availableColors }
            ?: availableColors.first()

        val newState = state.updateEntity(context.controllerId) { container ->
            val pool = container.get<ManaPoolComponent>() ?: ManaPoolComponent()
            val updated = if (effect.restriction != null) {
                pool.addRestricted(chosenColor, 1, effect.restriction!!)
            } else {
                pool.add(chosenColor, 1)
            }
            container.with(updated)
        }
        return EffectResult.success(newState)
    }

    private fun availableColors(
        state: GameState,
        scope: LandControllerScope,
        controllerId: EntityId,
    ): Set<Color> {
        val projected = state.projectedState
        val targetPlayers = playersInScope(state, scope, controllerId)
        if (targetPlayers.isEmpty()) return emptySet()

        val landIds = state.getBattlefield().filter { permId ->
            val container = state.getEntity(permId) ?: return@filter false
            val card = container.get<CardComponent>() ?: return@filter false
            if (!card.typeLine.isLand) return@filter false
            projected.getController(permId) in targetPlayers
        }
        return LandManaColorInspector.colorsLandsCouldProduce(state, projected, landIds, cardRegistry)
    }

    private fun playersInScope(
        state: GameState,
        scope: LandControllerScope,
        controllerId: EntityId,
    ): Set<EntityId> = when (scope) {
        LandControllerScope.YOU -> setOf(controllerId)
        LandControllerScope.OPPONENTS -> state.turnOrder.filter { it != controllerId }.toSet()
        LandControllerScope.ANY -> state.turnOrder.toSet()
    }
}
