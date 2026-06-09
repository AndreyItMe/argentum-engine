package com.wingedsheep.engine.handlers.continuations

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.mechanics.layers.Layer
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.mechanics.layers.addFloatingEffect
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.NotedCreatureTypesComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.handlers.effects.library.ChooseCreatureTypePipelineExecutor

class CreatureTypeChoiceContinuationResumer(
    private val services: com.wingedsheep.engine.core.EngineServices
) : ContinuationResumerModule {

    override fun resumers(): List<ContinuationResumer<*>> = listOf(
        resumer(ChooseOptionPipelineContinuation::class, ::resumeChooseOptionPipeline),
        resumer(NoteCreatureTypePipelineContinuation::class, ::resumeNoteCreatureType),
        resumer(BecomeCreatureTypeContinuation::class, ::resumeBecomeCreatureType),
        resumer(EachPlayerChoosesCreatureTypeContinuation::class, ::resumeEachPlayerChoosesCreatureType)
    )

    /**
     * Resume after player chose a generic option in a pipeline context.
     *
     * Injects the chosen value into every EffectContinuation on the stack
     * (via chosenValues map) so any downstream pipeline effect — including
     * ones in outer composites that wrap the choice (e.g., a ChooseOption
     * nested inside a MayEffect inside an outer CompositeEffect) — can
     * read it via EffectContext.chosenValues[storeAs].
     *
     * Special case: when storeAs == "chosenCreatureType", additionally
     * injects into the dedicated chosenCreatureType field for backward
     * compatibility with SelectFromCollectionExecutor and the
     * HasSubtypeFromVariable predicate.
     */
    fun resumeChooseOptionPipeline(
        state: GameState,
        continuation: ChooseOptionPipelineContinuation,
        response: DecisionResponse,
        checkForMore: CheckForMore
    ): ExecutionResult {
        if (response !is OptionChosenResponse) {
            return ExecutionResult.error(state, "Expected option choice response for generic option selection")
        }

        val chosenValue = continuation.options.getOrNull(response.optionIndex)
            ?: return ExecutionResult.error(state, "Invalid option index: ${response.optionIndex}")

        val isChosenCreatureType = continuation.storeAs ==
            ChooseCreatureTypePipelineExecutor.CHOSEN_CREATURE_TYPE_KEY

        val updatedStack = state.continuationStack.map { frame ->
            if (frame is EffectContinuation) {
                val newContext = if (isChosenCreatureType) {
                    frame.effectContext.copy(
                        chosenCreatureType = chosenValue,
                        pipeline = frame.effectContext.pipeline.copy(
                            chosenValues = frame.effectContext.pipeline.chosenValues +
                                (continuation.storeAs to chosenValue)
                        )
                    )
                } else {
                    frame.effectContext.copy(
                        pipeline = frame.effectContext.pipeline.copy(
                            chosenValues = frame.effectContext.pipeline.chosenValues +
                                (continuation.storeAs to chosenValue)
                        )
                    )
                }
                frame.copy(effectContext = newContext)
            } else {
                frame
            }
        }

        val newState = state.copy(continuationStack = updatedStack)

        return checkForMore(newState, emptyList())
    }

    /**
     * Resume after the player picked a creature type for a [com.wingedsheep.sdk.scripting.effects.NoteCreatureTypeEffect].
     * Appends the chosen type to the source's [NotedCreatureTypesComponent] (creating the
     * component on demand) AND injects the chosen value into every `EffectContinuation` on the
     * stack via `chosenValues[storeAs]` — same pattern as [resumeChooseOptionPipeline] so a
     * downstream pipeline step (e.g., the delayed-trigger spawn that fires "when you next cast
     * a creature spell of that type this turn") reads it the same way it would read any
     * `ChooseOption` result.
     */
    fun resumeNoteCreatureType(
        state: GameState,
        continuation: NoteCreatureTypePipelineContinuation,
        response: DecisionResponse,
        checkForMore: CheckForMore
    ): ExecutionResult {
        if (response !is OptionChosenResponse) {
            return ExecutionResult.error(state, "Expected option choice response for note-creature-type selection")
        }

        val chosenValue = continuation.options.getOrNull(response.optionIndex)
            ?: return ExecutionResult.error(state, "Invalid option index: ${response.optionIndex}")

        // Source may have left play between pause and resume — guard before updating.
        val stateWithComponent = if (state.getEntity(continuation.sourceId) != null) {
            state.updateEntity(continuation.sourceId) { container ->
                val existing = container.get<NotedCreatureTypesComponent>() ?: NotedCreatureTypesComponent()
                container.with(existing.withAdded(chosenValue))
            }
        } else {
            state
        }

        // Inject chosen value into every EffectContinuation on the stack — exact mirror of
        // resumeChooseOptionPipeline so downstream effects read it the same way.
        val updatedStack = stateWithComponent.continuationStack.map { frame ->
            if (frame is EffectContinuation) {
                frame.copy(effectContext = frame.effectContext.copy(
                    pipeline = frame.effectContext.pipeline.copy(
                        chosenValues = frame.effectContext.pipeline.chosenValues +
                            (continuation.storeAs to chosenValue)
                    )
                ))
            } else {
                frame
            }
        }

        val newState = stateWithComponent.copy(continuationStack = updatedStack)

        return checkForMore(newState, emptyList())
    }

    /**
     * Resume after player chose a creature type for a "becomes the creature type
     * of your choice" effect. Creates a floating effect that replaces all creature
     * subtypes with the chosen type.
     */
    fun resumeBecomeCreatureType(
        state: GameState,
        continuation: BecomeCreatureTypeContinuation,
        response: DecisionResponse,
        checkForMore: CheckForMore
    ): ExecutionResult {
        if (response !is OptionChosenResponse) {
            return ExecutionResult.error(state, "Expected option choice response for creature type selection")
        }

        val chosenType = continuation.creatureTypes.getOrNull(response.optionIndex)
            ?: return ExecutionResult.error(state, "Invalid creature type index: ${response.optionIndex}")

        val targetId = continuation.targetId

        // Target must still be on the battlefield
        if (targetId !in state.getBattlefield()) {
            return checkForMore(state, emptyList())
        }

        val targetCard = state.getEntity(targetId)?.get<CardComponent>()
        val targetName = targetCard?.name ?: "creature"

        // Create a floating effect that sets creature subtypes
        val context = EffectContext(
            sourceId = continuation.sourceId,
            controllerId = continuation.controllerId,
            opponentId = null
        )
        val newState = state.addFloatingEffect(
            layer = Layer.TYPE,
            modification = SerializableModification.SetCreatureSubtypes(
                subtypes = setOf(chosenType)
            ),
            affectedEntities = setOf(targetId),
            duration = continuation.duration,
            context = context
        )

        val events = listOf(
            CreatureTypeChangedEvent(
                targetId = targetId,
                targetName = targetName,
                newType = chosenType,
                sourceName = continuation.sourceName ?: "Unknown"
            )
        )

        return checkForMore(newState, events)
    }

    /**
     * Resume after a player chose a creature type for "each player chooses a creature type" effects.
     *
     * Records the chosen type, asks the next player if any remain,
     * or injects the chosen types into the EffectContinuation below on the stack
     * via storedStringLists[storeAs].
     */
    fun resumeEachPlayerChoosesCreatureType(
        state: GameState,
        continuation: EachPlayerChoosesCreatureTypeContinuation,
        response: DecisionResponse,
        checkForMore: CheckForMore
    ): ExecutionResult {
        if (response !is OptionChosenResponse) {
            return ExecutionResult.error(state, "Expected option choice response for creature type selection")
        }

        val chosenType = continuation.creatureTypes.getOrNull(response.optionIndex)
            ?: return ExecutionResult.error(state, "Invalid creature type index: ${response.optionIndex}")

        val updatedChosenTypes = continuation.chosenTypes + chosenType

        // If there are more players, ask the next one
        if (continuation.remainingPlayers.isNotEmpty()) {
            val nextPlayer = continuation.remainingPlayers.first()
            val nextRemaining = continuation.remainingPlayers.drop(1)

            val decisionId = java.util.UUID.randomUUID().toString()
            val decision = ChooseOptionDecision(
                id = decisionId,
                playerId = nextPlayer,
                prompt = "Choose a creature type",
                context = DecisionContext(
                    sourceId = continuation.sourceId,
                    sourceName = continuation.sourceName,
                    phase = DecisionPhase.RESOLUTION
                ),
                options = continuation.creatureTypes
            )

            val newContinuation = continuation.copy(
                decisionId = decisionId,
                currentPlayerId = nextPlayer,
                remainingPlayers = nextRemaining,
                chosenTypes = updatedChosenTypes
            )

            val stateWithDecision = state.withPendingDecision(decision)
            val stateWithContinuation = stateWithDecision.pushContinuation(newContinuation)

            return ExecutionResult.paused(
                stateWithContinuation,
                decision,
                listOf(
                    DecisionRequestedEvent(
                        decisionId = decisionId,
                        playerId = nextPlayer,
                        decisionType = "CHOOSE_OPTION",
                        prompt = decision.prompt
                    )
                )
            )
        }

        // All players have chosen — inject chosen types into EffectContinuation below
        val nextFrame = state.peekContinuation()
        val newState = if (nextFrame is EffectContinuation) {
            val (_, stateAfterPop) = state.popContinuation()
            stateAfterPop.pushContinuation(
                nextFrame.copy(effectContext = nextFrame.effectContext.copy(
                    pipeline = nextFrame.effectContext.pipeline.copy(storedStringLists = nextFrame.effectContext.pipeline.storedStringLists + (continuation.storeAs to updatedChosenTypes))
                ))
            )
        } else {
            state
        }

        return checkForMore(newState, emptyList())
    }

}
