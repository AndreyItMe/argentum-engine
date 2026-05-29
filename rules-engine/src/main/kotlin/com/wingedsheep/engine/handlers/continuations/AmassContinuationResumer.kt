package com.wingedsheep.engine.handlers.continuations

import com.wingedsheep.engine.core.AmassContinuation
import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.DecisionResponse
import com.wingedsheep.engine.core.EngineServices
import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.handlers.effects.player.AmassResolution
import com.wingedsheep.engine.state.GameState

/**
 * Resumes "amass [subtype] N" after the controller picks which Army to amass (CR 701.47a).
 *
 * Reached only when the controller has more than one Army. The chosen Army gets the +1/+1 counters
 * and becomes the subtype if it isn't already, via the shared [AmassResolution].
 */
class AmassContinuationResumer(
    private val services: EngineServices
) : ContinuationResumerModule {

    override fun resumers(): List<ContinuationResumer<*>> = listOf(
        resumer(AmassContinuation::class, ::resumeAmass)
    )

    private fun resumeAmass(
        state: GameState,
        continuation: AmassContinuation,
        response: DecisionResponse,
        checkForMore: CheckForMore
    ): ExecutionResult {
        if (response !is CardsSelectedResponse) {
            return ExecutionResult.error(state, "Expected an Army selection for amass")
        }

        val chosen = response.selectedCards.firstOrNull { it in continuation.candidates }
            ?: continuation.candidates.first()

        val result = AmassResolution.applyToArmy(
            state = state,
            armyId = chosen,
            controllerId = continuation.controllerId,
            opponentId = continuation.opponentId,
            subtype = continuation.subtype,
            amount = continuation.amount,
            sourceId = continuation.sourceId,
            executeEffect = services.effectExecutorRegistry::execute
        )

        return checkForMore(result.state, result.events)
    }
}
