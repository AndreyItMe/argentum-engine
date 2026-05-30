package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Thicket Elemental.
 *
 * Card reference:
 * - Thicket Elemental ({3}{G}{G}): Creature — Elemental 4/4
 *   Kicker {1}{G}
 *   When this creature enters, if it was kicked, you may reveal cards from the top of your library
 *   until you reveal a creature card. If you do, put that card onto the battlefield and shuffle all
 *   other cards revealed this way into your library.
 */
class ThicketElementalScenarioTest : ScenarioTestBase() {

    private fun ScenarioTestBase.TestGame.castKicked(playerNumber: Int, name: String): com.wingedsheep.engine.core.ExecutionResult {
        val playerId = if (playerNumber == 1) player1Id else player2Id
        val cardId = state.getHand(playerId).find { entityId ->
            state.getEntity(entityId)?.get<CardComponent>()?.name == name
        }!!
        return execute(CastSpell(playerId, cardId, wasKicked = true))
    }

    init {
        context("Thicket Elemental kicker") {

            test("unkicked enters as a 4/4 with no ETB effect") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Thicket Elemental")
                    .withLandsOnBattlefield(1, "Forest", 5)
                    .withCardInLibrary(1, "Llanowar Elite")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val castResult = game.castSpell(1, "Thicket Elemental")
                withClue("Cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                withClue("Thicket Elemental is on the battlefield") {
                    game.isOnBattlefield("Thicket Elemental") shouldBe true
                }
                withClue("Library creature was not pulled out (no trigger when unkicked)") {
                    game.isOnBattlefield("Llanowar Elite") shouldBe false
                }
            }

            test("kicked reveals until a creature and puts it onto the battlefield") {
                // Library top-down: Forest, Plains, Llanowar Elite, Forest.
                // Reveal until creature -> Llanowar Elite enters; revealed lands shuffle back.
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Thicket Elemental")
                    .withLandsOnBattlefield(1, "Forest", 7) // {3}{G}{G} + {1}{G} kicker
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(1, "Llanowar Elite")
                    .withCardInLibrary(1, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val initialLibrary = game.librarySize(1)

                val castResult = game.castKicked(1, "Thicket Elemental")
                withClue("Kicked cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                // "may" — say yes.
                withClue("There should be a yes/no decision for the may ability") {
                    game.hasPendingDecision() shouldBe true
                }
                game.answerYesNo(true)
                game.resolveStack()

                withClue("Thicket Elemental is on the battlefield") {
                    game.isOnBattlefield("Thicket Elemental") shouldBe true
                }
                withClue("Llanowar Elite was put onto the battlefield from the library") {
                    game.isOnBattlefield("Llanowar Elite") shouldBe true
                }
                withClue("Library is smaller by one (the creature left; lands shuffled back)") {
                    game.librarySize(1) shouldBe initialLibrary - 1
                }
            }

            test("kicked but declining the may ability leaves the library intact") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Thicket Elemental")
                    .withLandsOnBattlefield(1, "Forest", 7)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Llanowar Elite")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val initialLibrary = game.librarySize(1)

                game.castKicked(1, "Thicket Elemental")
                game.resolveStack()
                game.answerYesNo(false)
                game.resolveStack()

                withClue("Declining leaves the library untouched") {
                    game.librarySize(1) shouldBe initialLibrary
                }
                withClue("Llanowar Elite stays in the library") {
                    game.isOnBattlefield("Llanowar Elite") shouldBe false
                }
            }
        }
    }
}
