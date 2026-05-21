package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.PlotCard
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.PlayWithoutPayingCostComponent
import com.wingedsheep.engine.state.components.identity.PlottedComponent
import com.wingedsheep.engine.state.components.player.CreaturesDiedThisTurnComponent
import com.wingedsheep.engine.state.permissions.activeMayPlayFor
import com.wingedsheep.engine.handlers.ConditionEvaluator
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Blacksnag Buzzard.
 *
 * Blacksnag Buzzard: {2}{B} Creature — Bird (2/1)
 *   Flying
 *   This creature enters with a +1/+1 counter on it if a creature died this turn.
 *   Plot {1}{B}
 *
 * These tests double as the smoke tests for the engine's Plot mechanic — the
 * card was the first to exercise it.
 */
class BlacksnagBuzzardScenarioTest : ScenarioTestBase() {

    init {
        context("Plot mechanic") {

            test("paying {1}{B} exiles the card from hand and marks it plotted") {
                val game = scenario()
                    .withPlayers("Active", "Opponent")
                    .withCardInHand(1, "Blacksnag Buzzard")
                    .withLandsOnBattlefield(1, "Swamp", 2)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cardId = game.state.getHand(game.player1Id).single { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Blacksnag Buzzard"
                }

                val result = game.execute(PlotCard(game.player1Id, cardId))

                withClue("plot action should succeed: ${result.error}") {
                    result.error shouldBe null
                }

                withClue("Blacksnag Buzzard should be exiled (no longer in hand)") {
                    game.state.getHand(game.player1Id) shouldBe emptyList()
                }
                withClue("Blacksnag Buzzard should sit in the player's exile zone") {
                    game.state.getExile(game.player1Id) shouldContain cardId
                }

                val plotted = game.state.getEntity(cardId)?.get<PlottedComponent>()
                withClue("the exiled card should carry a PlottedComponent stamped with the current turn") {
                    plotted.shouldNotBeNull()
                    plotted.controllerId shouldBe game.player1Id
                    plotted.turnPlotted shouldBe game.state.turnNumber
                }
                withClue("the exiled card should be marked free-to-cast") {
                    game.state.getEntity(cardId)?.get<PlayWithoutPayingCostComponent>().shouldNotBeNull()
                }

                val evaluator = ConditionEvaluator()
                withClue("the plot may-play permission must NOT be active on the same turn it was plotted") {
                    game.state.activeMayPlayFor(cardId, game.player1Id, evaluator) shouldBe emptyList()
                }
            }

            test("plotted card becomes castable for free on a later turn and ETB without counter") {
                val game = scenario()
                    .withPlayers("Active", "Opponent")
                    .withCardInHand(1, "Blacksnag Buzzard")
                    .withLandsOnBattlefield(1, "Swamp", 2)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cardId = game.state.getHand(game.player1Id).single { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Blacksnag Buzzard"
                }

                game.execute(PlotCard(game.player1Id, cardId)).error shouldBe null

                // Jump to a later turn so the permission gate opens (SourcePlottedOnPriorTurn).
                game.state = game.state.copy(turnNumber = game.state.turnNumber + 1)

                val evaluator = ConditionEvaluator()
                withClue("plot may-play permission should be active on a later turn") {
                    game.state.activeMayPlayFor(cardId, game.player1Id, evaluator).size shouldBe 1
                }

                game.castSpellFromExile(1, "Blacksnag Buzzard").error shouldBe null
                game.resolveStack()

                withClue("Blacksnag Buzzard should be on the battlefield after the free cast") {
                    game.isOnBattlefield("Blacksnag Buzzard") shouldBe true
                }

                val buzzardId = game.findPermanent("Blacksnag Buzzard")!!
                val counters = game.state.getEntity(buzzardId)?.get<CountersComponent>()
                withClue("no creature died this turn — Blacksnag Buzzard should enter without a +1/+1 counter") {
                    (counters?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 0
                }
            }
        }

        context("EntersWith +1/+1 conditional on a creature having died this turn") {

            test("enters with a +1/+1 counter when a creature died earlier this turn") {
                val game = scenario()
                    .withPlayers("Active", "Opponent")
                    .withCardInHand(1, "Blacksnag Buzzard")
                    .withLandsOnBattlefield(1, "Swamp", 3)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Pretend an opponent's creature died earlier this turn.
                game.state = game.state.updateEntity(game.player2Id) { container ->
                    container.with(CreaturesDiedThisTurnComponent(count = 1))
                }

                game.castSpell(1, "Blacksnag Buzzard").error shouldBe null
                game.resolveStack()

                val buzzardId = game.findPermanent("Blacksnag Buzzard")!!
                val counters = game.state.getEntity(buzzardId)?.get<CountersComponent>()
                withClue("Blacksnag Buzzard should enter with one +1/+1 counter") {
                    counters.shouldNotBeNull()
                    counters.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 1
                }
            }
        }
    }
}
