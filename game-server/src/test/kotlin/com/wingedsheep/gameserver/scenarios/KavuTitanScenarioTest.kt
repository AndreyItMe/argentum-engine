package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.WasKickedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Kavu Titan.
 *
 * Card reference:
 * - Kavu Titan ({1}{G}): Creature — Kavu 2/2
 *   Kicker {2}{G}
 *   If this creature was kicked, it enters with three +1/+1 counters on it and with trample.
 */
class KavuTitanScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    private fun ScenarioTestBase.TestGame.getCounters(entityId: EntityId): Int {
        return state.getEntity(entityId)
            ?.get<CountersComponent>()
            ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0
    }

    init {
        context("Kavu Titan kicker") {

            test("unkicked enters as a vanilla 2/2 with no counters and no trample") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Kavu Titan")
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val castResult = game.castSpell(1, "Kavu Titan")
                withClue("Cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                val titanId = game.findPermanent("Kavu Titan")!!
                val projected = stateProjector.project(game.state)

                withClue("Unkicked Kavu Titan should have no +1/+1 counters") {
                    game.getCounters(titanId) shouldBe 0
                }
                withClue("Unkicked Kavu Titan should NOT have trample") {
                    projected.hasKeyword(titanId, Keyword.TRAMPLE) shouldBe false
                }
            }

            test("kicked enters with three +1/+1 counters and trample") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Kavu Titan")
                    .withLandsOnBattlefield(1, "Forest", 5) // {1}{G} + {2}{G} kicker
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val playerId = game.player1Id
                val cardId = game.state.getHand(playerId).find { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Kavu Titan"
                }!!

                val castResult = game.execute(CastSpell(playerId, cardId, wasKicked = true))
                withClue("Kicked cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                val titanId = game.findPermanent("Kavu Titan")!!

                withClue("Permanent should have WasKickedComponent") {
                    (game.state.getEntity(titanId)?.has<WasKickedComponent>() == true) shouldBe true
                }
                withClue("Kicked Kavu Titan should have three +1/+1 counters") {
                    game.getCounters(titanId) shouldBe 3
                }

                val projected = stateProjector.project(game.state)
                withClue("Kicked Kavu Titan should have trample") {
                    projected.hasKeyword(titanId, Keyword.TRAMPLE) shouldBe true
                }
            }
        }
    }
}
