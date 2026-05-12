package com.wingedsheep.engine.handlers.triggers

import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.player.CreaturesDiedThisTurnComponent
import com.wingedsheep.sdk.scripting.conditions.CreatureDiedThisTurnCondition as CreatureDiedThisTurnSdkCondition
import kotlin.reflect.KClass

/**
 * Evaluator for CreatureDiedThisTurnCondition.
 * Rule 603.4 intervening-if: "if a creature died this turn".
 * Checks CreaturesDiedThisTurnComponent on the ability controller's player entity.
 * The component is incremented by ZoneTransitionService on every creature death and
 * cleared by CleanupPhaseManager at end of turn.
 */
class CreatureDiedThisTurnConditionEvaluator {

    val conditionType: KClass<CreatureDiedThisTurnSdkCondition> = CreatureDiedThisTurnSdkCondition::class

    fun evaluate(
        state: GameState,
        @Suppress("UNUSED_PARAMETER") condition: CreatureDiedThisTurnSdkCondition,
        context: EffectContext
    ): Boolean {
        val count = state.getEntity(context.controllerId)
            ?.get<CreaturesDiedThisTurnComponent>()?.count ?: 0
        return count > 0
    }
}
