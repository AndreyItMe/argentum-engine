package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Restock.
 *
 * Card reference:
 * - Restock ({3}{G}{G}): Sorcery
 *   Return two target cards from your graveyard to your hand. Exile Restock.
 */
class RestockScenarioTest : ScenarioTestBase() {

    private fun ScenarioTestBase.TestGame.graveyardCard(playerNumber: Int, name: String): EntityId {
        val playerId = if (playerNumber == 1) player1Id else player2Id
        return state.getGraveyard(playerId).first { id ->
            state.getEntity(id)?.get<CardComponent>()?.name == name
        }
    }

    init {
        context("Restock") {

            test("returns two target cards from graveyard to hand and exiles itself") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Restock")
                    .withLandsOnBattlefield(1, "Forest", 5)
                    .withCardInGraveyard(1, "Llanowar Elite")
                    .withCardInGraveyard(1, "Quirion Trailblazer")
                    .withCardInGraveyard(1, "Recover")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val playerId = game.player1Id
                val restockId = game.state.getHand(playerId).first { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Restock"
                }

                val target1 = game.graveyardCard(1, "Llanowar Elite")
                val target2 = game.graveyardCard(1, "Quirion Trailblazer")
                val targets = listOf(
                    ChosenTarget.Card(target1, playerId, Zone.GRAVEYARD),
                    ChosenTarget.Card(target2, playerId, Zone.GRAVEYARD)
                )

                val castResult = game.execute(CastSpell(playerId, restockId, targets))
                withClue("Cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                withClue("Llanowar Elite returned to hand") {
                    game.isInHand(1, "Llanowar Elite") shouldBe true
                }
                withClue("Quirion Trailblazer returned to hand") {
                    game.isInHand(1, "Quirion Trailblazer") shouldBe true
                }
                withClue("Recover remains in the graveyard (not targeted)") {
                    game.isInGraveyard(1, "Recover") shouldBe true
                }
                withClue("Restock exiles itself instead of going to the graveyard") {
                    game.isInGraveyard(1, "Restock") shouldBe false
                }
            }
        }
    }
}
