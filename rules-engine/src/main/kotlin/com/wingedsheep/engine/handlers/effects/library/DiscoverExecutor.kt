package com.wingedsheep.engine.handlers.effects.library

import com.wingedsheep.engine.core.CardsRevealedEvent
import com.wingedsheep.engine.core.DecisionPhase
import com.wingedsheep.engine.core.DiscoverMayCastContinuation
import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.core.GameEvent as EngineGameEvent
import com.wingedsheep.engine.handlers.DecisionHandler
import com.wingedsheep.engine.handlers.DynamicAmountEvaluator
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.PipelineState
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.handlers.effects.ZoneMovementUtils
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.DiscoverEffect
import com.wingedsheep.sdk.scripting.effects.Effect
import kotlin.reflect.KClass

/**
 * Executor for [DiscoverEffect] (CR 701.57).
 *
 * Walks the controller's library top-down, exiling cards until a nonland card with
 * mana value ≤ N (the threshold, evaluated from [DiscoverEffect.amount]) is exiled —
 * the "discovered card" (CR 701.57c) — or the library is exhausted.
 *
 *  - **Discovered card found** → pause with a two-option decision (cast for free /
 *    put into hand) and push a [DiscoverMayCastContinuation]; the resumer in
 *    [com.wingedsheep.engine.handlers.continuations.LibraryAndZoneContinuationResumer]
 *    bottom-randomizes the other exiled cards, then either free-casts the discovered
 *    card or moves it to hand, then runs any [DiscoverEffect.thenEffect].
 *  - **No nonland ≤ N found** (library exhausted) → every exiled card is bottom-randomized
 *    here, and no cast/hand decision is offered (there is no castable stopping card). But per
 *    CR 701.57c the *final* card exiled is still the "discovered card" if its mana value is ≤ N
 *    (a land at the bottom of the library has mana value 0), so a [DiscoverEffect.thenEffect]
 *    keyed on the discovered card still runs, reading that card's mana value (Hit the Mother Lode
 *    decking itself out still makes its Treasures). If the final card's mana value exceeds N,
 *    nothing was discovered and no `thenEffect` runs.
 *
 * Mirrors [CascadeExecutor] (they share the bottom-randomize helper) but uses an explicit
 * ≤ threshold and keeps the discovered card on the non-cast branch.
 */
class DiscoverExecutor(
    /** Runs a [DiscoverEffect.thenEffect] through the registry (the late-bound recursion entry). */
    private val runEffect: (GameState, Effect, EffectContext) -> EffectResult,
    private val decisionHandler: DecisionHandler = DecisionHandler()
) : EffectExecutor<DiscoverEffect> {

    override val effectType: KClass<DiscoverEffect> = DiscoverEffect::class

    private val amountEvaluator = DynamicAmountEvaluator()

    override fun execute(
        state: GameState,
        effect: DiscoverEffect,
        context: EffectContext
    ): EffectResult {
        val controllerId = context.controllerId
        val threshold = amountEvaluator.evaluate(state, effect.amount, context)

        var currentState = state
        val allEvents = mutableListOf<EngineGameEvent>()
        val exiledCards = mutableListOf<EntityId>()
        var discoveredCard: EntityId? = null

        val library = currentState.getZone(ZoneKey(controllerId, Zone.LIBRARY))
        for (cardId in library) {
            exiledCards.add(cardId)
            val card = currentState.getEntity(cardId)?.get<CardComponent>()
            if (card != null && !card.typeLine.isLand && card.manaValue <= threshold) {
                discoveredCard = cardId
                break
            }
        }

        if (exiledCards.isEmpty()) {
            return EffectResult.success(currentState)
        }

        val sourceName = context.sourceId?.let {
            currentState.getEntity(it)?.get<CardComponent>()?.name
        }
        allEvents.add(
            CardsRevealedEvent(
                revealingPlayerId = controllerId,
                cardIds = exiledCards.toList(),
                cardNames = exiledCards.map { id ->
                    currentState.getEntity(id)?.get<CardComponent>()?.name ?: "Unknown"
                },
                imageUris = exiledCards.map { id ->
                    currentState.getEntity(id)?.get<CardComponent>()?.imageUri
                },
                source = sourceName
            )
        )

        for (cardId in exiledCards) {
            val result = ZoneMovementUtils.moveCardToZone(currentState, cardId, Zone.EXILE)
            if (result.isSuccess) {
                currentState = result.state
                allEvents.addAll(result.events)
            }
        }

        if (discoveredCard == null) {
            // Library exhausted without exiling a nonland card with mana value ≤ N — no castable
            // stopping card, so no may-cast/hand decision; every exiled card is bottom-randomized.
            val bottomEvents = CascadeExecutor.bottomRandomize(currentState, controllerId, exiledCards) { newState ->
                currentState = newState
            }
            // CR 701.57c: the final card exiled is still the "discovered card" if its mana value is
            // ≤ N (a land at the bottom of the library has mana value 0). When so, a thenEffect keyed
            // on the discovered card still runs (Hit the Mother Lode decking itself out on lands still
            // makes Treasures); otherwise nothing was discovered and there is no follow-up.
            val finalCardMv = currentState.getEntity(exiledCards.last())?.get<CardComponent>()?.manaValue
            val thenEffect = effect.thenEffect
            if (thenEffect == null || finalCardMv == null || finalCardMv > threshold) {
                return EffectResult.success(currentState, allEvents + bottomEvents)
            }
            val discoveredCollections = effect.storeDiscoveredAs
                ?.let { mapOf(it to listOf(exiledCards.last())) }
                ?: emptyMap()
            val thenResult = runEffect(
                currentState,
                thenEffect,
                EffectContext(
                    sourceId = context.sourceId,
                    controllerId = controllerId,
                    pipeline = PipelineState.EMPTY.copy(storedCollections = discoveredCollections)
                )
            )
            return if (thenResult.isPaused) {
                EffectResult.paused(thenResult.state, thenResult.pendingDecision!!, allEvents + bottomEvents + thenResult.events)
            } else {
                EffectResult.success(thenResult.state, allEvents + bottomEvents + thenResult.events)
            }
        }

        val discoveredName = currentState.getEntity(discoveredCard)
            ?.get<CardComponent>()?.name ?: "the discovered card"
        val pause = decisionHandler.createYesNoDecision(
            state = currentState,
            playerId = controllerId,
            sourceId = context.sourceId,
            sourceName = sourceName,
            prompt = "Cast $discoveredName without paying its mana cost? (Decline to put it into your hand.)",
            yesText = "Cast for free",
            noText = "Put into hand",
            phase = DecisionPhase.RESOLUTION
        )

        val pendingDecision = pause.pendingDecision
            ?: error("createYesNoDecision must return a pending decision")
        val continuation = DiscoverMayCastContinuation(
            decisionId = pendingDecision.id,
            playerId = controllerId,
            sourceId = context.sourceId,
            exiledCards = exiledCards.toList(),
            discoveredCardId = discoveredCard,
            storeDiscoveredAs = effect.storeDiscoveredAs,
            thenEffect = effect.thenEffect
        )
        val stateWithCont = pause.state.pushContinuation(continuation)

        return EffectResult.paused(
            stateWithCont,
            pendingDecision,
            allEvents + pause.events
        )
    }
}
