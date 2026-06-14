package com.wingedsheep.engine.handlers.actions.ability

import com.wingedsheep.engine.core.CardPlottedEvent
import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.core.ManaSpentEvent
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.core.PlotCard
import com.wingedsheep.engine.core.TappedEvent
import com.wingedsheep.engine.core.ZoneChangeEvent
import com.wingedsheep.engine.core.EngineServices
import com.wingedsheep.engine.event.TriggerDetector
import com.wingedsheep.engine.event.TriggerProcessor
import com.wingedsheep.engine.handlers.actions.ActionHandler
import com.wingedsheep.engine.mechanics.mana.ManaPool
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.PlayWithoutPayingCostComponent
import com.wingedsheep.engine.state.components.identity.PlottedComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.permissions.MayPlayPermission
import com.wingedsheep.engine.state.permissions.addMayPlayPermission
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.conditions.SourcePlottedOnPriorTurn
import kotlin.reflect.KClass

/**
 * Handler for the [PlotCard] special action (CR 718, Outlaws of Thunder Junction).
 *
 * Plot is a special action — does not use the stack and cannot be responded to once
 * announced. The player must have priority during their main phase with the stack
 * empty (sorcery-speed). The handler pays the plot cost, exiles the card from hand,
 * stamps a [PlottedComponent] + [PlayWithoutPayingCostComponent] on it, and adds a
 * permanent [MayPlayPermission] gated by [SourcePlottedOnPriorTurn] so the card can
 * be cast for free from exile on any later turn.
 *
 * Cleanup on cast is implicit: when the plotted spell resolves into the battlefield,
 * [com.wingedsheep.engine.mechanics.stack.StackResolver] strips the may-play permission
 * for the card and clears the free-cast component. The [PlottedComponent] is harmless
 * on the resulting battlefield permanent (only read sites in exile look at it).
 */
class PlotCardHandler(
    private val cardRegistry: CardRegistry,
    private val manaSolver: ManaSolver,
    private val manaAbilitySideEffectExecutor: com.wingedsheep.engine.mechanics.mana.ManaAbilitySideEffectExecutor,
    private val triggerDetector: TriggerDetector,
    private val triggerProcessor: TriggerProcessor
) : ActionHandler<PlotCard> {
    override val actionType: KClass<PlotCard> = PlotCard::class

    override fun validate(state: GameState, action: PlotCard): String? {
        if (state.priorityPlayerId != action.playerId) {
            return "You don't have priority"
        }
        if (!state.step.isMainPhase || state.stack.isNotEmpty() ||
            !state.isActiveTurnFor(action.playerId)) {
            return "Plot can only be activated during your main phase while the stack is empty"
        }

        val container = state.getEntity(action.cardId)
            ?: return "Card not found: ${action.cardId}"
        val cardComponent = container.get<CardComponent>()
            ?: return "Not a card: ${action.cardId}"

        val handZone = ZoneKey(action.playerId, Zone.HAND)
        if (action.cardId !in state.getZone(handZone)) {
            return "Card is not in your hand"
        }

        val cardDef = cardRegistry.getCard(cardComponent.cardDefinitionId)
            ?: return "Card definition not found"
        val plotAbility = cardDef.keywordAbilities.filterIsInstance<KeywordAbility.Plot>().firstOrNull()
            ?: return "This card doesn't have plot"

        if (action.paymentStrategy is PaymentStrategy.Explicit) {
            for (sourceId in action.paymentStrategy.manaAbilitiesToActivate) {
                val sourceContainer = state.getEntity(sourceId)
                    ?: return "Mana source not found: $sourceId"
                if (sourceContainer.has<TappedComponent>()) {
                    return "Mana source is already tapped: $sourceId"
                }
            }
        } else if (!manaSolver.canPay(state, action.playerId, plotAbility.cost)) {
            return "Not enough mana to plot this card"
        }
        return null
    }

    override fun execute(state: GameState, action: PlotCard): ExecutionResult {
        val container = state.getEntity(action.cardId)
            ?: return ExecutionResult.error(state, "Card not found")
        val cardComponent = container.get<CardComponent>()
            ?: return ExecutionResult.error(state, "Not a card")
        val cardDef = cardRegistry.getCard(cardComponent.cardDefinitionId)
            ?: return ExecutionResult.error(state, "Card definition not found")
        val plotAbility = cardDef.keywordAbilities.filterIsInstance<KeywordAbility.Plot>().firstOrNull()
            ?: return ExecutionResult.error(state, "This card doesn't have plot")

        var currentState = state
        val events = mutableListOf<GameEvent>()
        val ownerId = cardComponent.ownerId ?: action.playerId

        // Pay the plot cost — drain mana pool first, then tap lands for the remainder.
        val poolComponent = currentState.getEntity(action.playerId)?.get<ManaPoolComponent>()
            ?: ManaPoolComponent()
        val pool = ManaPool(
            white = poolComponent.white,
            blue = poolComponent.blue,
            black = poolComponent.black,
            red = poolComponent.red,
            green = poolComponent.green,
            colorless = poolComponent.colorless
        )
        val partialResult = pool.payPartial(plotAbility.cost)
        val poolAfterPayment = partialResult.newPool
        val remainingCost = partialResult.remainingCost
        val manaSpentFromPool = partialResult.manaSpent

        var whiteSpent = manaSpentFromPool.white
        var blueSpent = manaSpentFromPool.blue
        var blackSpent = manaSpentFromPool.black
        var redSpent = manaSpentFromPool.red
        var greenSpent = manaSpentFromPool.green
        var colorlessSpent = manaSpentFromPool.colorless

        currentState = currentState.updateEntity(action.playerId) { c ->
            c.with(
                ManaPoolComponent(
                    white = poolAfterPayment.white,
                    blue = poolAfterPayment.blue,
                    black = poolAfterPayment.black,
                    red = poolAfterPayment.red,
                    green = poolAfterPayment.green,
                    colorless = poolAfterPayment.colorless
                )
            )
        }

        if (!remainingCost.isEmpty()) {
            if (action.paymentStrategy is PaymentStrategy.Explicit) {
                for (sourceId in action.paymentStrategy.manaAbilitiesToActivate) {
                    val sourceName = currentState.getEntity(sourceId)
                        ?.get<CardComponent>()?.name ?: "Unknown"
                    currentState = currentState.updateEntity(sourceId) { c -> c.with(TappedComponent) }
                    events.add(TappedEvent(sourceId, sourceName))
                }
            } else {
                val solution = manaSolver.solve(currentState, action.playerId, remainingCost, 0)
                    ?: return ExecutionResult.error(state, "Not enough mana to plot")
                val (stateAfterTaps, tapEvents) = manaAbilitySideEffectExecutor
                    .tapSourcesWithSideEffects(currentState, solution, action.playerId)
                currentState = stateAfterTaps
                events.addAll(tapEvents)

                for ((_, production) in solution.manaProduced) {
                    when (production.color) {
                        Color.WHITE -> whiteSpent++
                        Color.BLUE -> blueSpent++
                        Color.BLACK -> blackSpent++
                        Color.RED -> redSpent++
                        Color.GREEN -> greenSpent++
                        null -> colorlessSpent += production.colorless
                    }
                }
            }
        }

        events.add(
            ManaSpentEvent(
                playerId = action.playerId,
                reason = "Plot ${cardComponent.name}",
                white = whiteSpent,
                blue = blueSpent,
                black = blackSpent,
                red = redSpent,
                green = greenSpent,
                colorless = colorlessSpent
            )
        )

        // Move card from hand → owner's exile (face-up; plotted cards are public).
        val handZone = ZoneKey(action.playerId, Zone.HAND)
        val exileZone = ZoneKey(ownerId, Zone.EXILE)
        currentState = currentState.removeFromZone(handZone, action.cardId)
        currentState = currentState.addToZone(exileZone, action.cardId)
        events.add(
            ZoneChangeEvent(
                entityId = action.cardId,
                entityName = cardComponent.name,
                fromZone = Zone.HAND,
                toZone = Zone.EXILE,
                ownerId = ownerId
            )
        )

        // Stamp plotted-state components and grant the free-cast permission.
        currentState = currentState.updateEntity(action.cardId) { c ->
            c.with(PlottedComponent(controllerId = action.playerId, turnPlotted = currentState.turnNumber))
                .with(PlayWithoutPayingCostComponent(controllerId = action.playerId, permanent = true))
        }
        val (permId, stateWithPerm) = currentState.newEntity()
        currentState = stateWithPerm.addMayPlayPermission(
            MayPlayPermission(
                id = permId,
                cardIds = setOf(action.cardId),
                controllerId = action.playerId,
                sourceId = action.cardId,
                condition = SourcePlottedOnPriorTurn,
                permanent = true,
                timestamp = currentState.timestamp,
            )
        )

        events.add(CardPlottedEvent(action.playerId, action.cardId, cardComponent.name))

        currentState = currentState.tick()

        // Fire any "when this card becomes plotted" triggers (CR 718, e.g. Aloe Alchemist). The
        // ActionProcessor does not run trigger detection centrally — each handler detects and
        // processes its own triggers, like CycleCardHandler does for cycling triggers. The plotted
        // card now sits in exile, so TriggerDetector.detectPlottedCardTriggers picks these up.
        val triggers = triggerDetector.detectTriggers(currentState, events)
        if (triggers.isNotEmpty()) {
            val triggerResult = triggerProcessor.processTriggers(currentState, triggers)
            if (triggerResult.isPaused) {
                return ExecutionResult.paused(
                    triggerResult.state,
                    triggerResult.pendingDecision!!,
                    events + triggerResult.events
                )
            }
            currentState = triggerResult.newState
            events.addAll(triggerResult.events)
        }

        // Plot is a special action — does not change priority and does not use the stack.
        return ExecutionResult.success(currentState, events)
    }

    companion object {
        fun create(services: EngineServices): PlotCardHandler {
            return PlotCardHandler(
                services.cardRegistry,
                services.manaSolver,
                services.manaAbilitySideEffectExecutor,
                services.triggerDetector,
                services.triggerProcessor
            )
        }
    }
}
