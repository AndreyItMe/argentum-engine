package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for the TDM green batch:
 *  - Temur Monument (#248): ETB tutors a basic Forest/Island/Mountain to hand.
 *  - Sagu Wildling (#157): ETB gains 3 life (Omen back face Roost Seek exercised by other paths).
 *  - Traveling Botanist (#164): on becoming tapped, peeks the top card and (if a land) may take it
 *    to hand, else may bin it.
 *
 * Undergrowth Leopard reuses the well-covered Capashen Unicorn sac-to-destroy pattern, so it has
 * no bespoke scenario here.
 */
class TdmGreenBatchScenarioTest : ScenarioTestBase() {

    init {
        context("Temur Monument") {
            test("ETB tutors a basic Forest/Island/Mountain into hand and shuffles") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Temur Monument")
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(1, "Swamp")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cast = game.castSpell(1, "Temur Monument")
                withClue("Casting Temur Monument should succeed: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack()

                // ETB: search for a basic Forest/Island/Mountain — only the Island qualifies.
                withClue("ETB search should prompt a card selection") {
                    game.hasPendingDecision() shouldBe true
                }
                val decision = game.getPendingDecision() as SelectCardsDecision
                val options = decision.options
                withClue("Only the Island should be a legal choice (Swamp is excluded)") {
                    options.size shouldBe 1
                }
                game.selectCards(listOf(options.first()))
                game.resolveStack()

                withClue("Temur Monument should be on the battlefield") {
                    game.isOnBattlefield("Temur Monument") shouldBe true
                }
                withClue("The Island should be in hand") {
                    game.findCardsInHand(1, "Island").size shouldBe 1
                }
                withClue("The Island should have left the library") {
                    game.findCardsInLibrary(1, "Island").size shouldBe 0
                }
            }
        }

        context("Sagu Wildling") {
            test("ETB gains the controller 3 life") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Sagu Wildling")
                    .withLandsOnBattlefield(1, "Forest", 5)
                    .withLifeTotal(1, 20)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cast = game.castSpell(1, "Sagu Wildling")
                withClue("Casting Sagu Wildling should succeed: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack()

                withClue("Sagu Wildling should be on the battlefield") {
                    game.isOnBattlefield("Sagu Wildling") shouldBe true
                }
                withClue("Controller should have gained 3 life (20 -> 23)") {
                    game.getLifeTotal(1) shouldBe 23
                }
            }
        }

        context("Traveling Botanist") {
            test("becoming tapped via attacking lets the controller take a top land to hand") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Traveling Botanist", summoningSickness = false)
                    .withCardInLibrary(1, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                game.declareAttackers(mapOf("Traveling Botanist" to 2))
                game.resolveStack()

                // Becoming tapped peeks the top card (a Forest) and offers to take it to hand.
                withClue("Tap trigger should prompt to take the land") {
                    game.hasPendingDecision() shouldBe true
                }
                val decision = game.getPendingDecision() as SelectCardsDecision
                game.selectCards(listOf(decision.options.first()))
                game.resolveStack()

                withClue("The Forest should be in hand") {
                    game.findCardsInHand(1, "Forest").size shouldBe 1
                }
                withClue("The Forest should have left the library") {
                    game.findCardsInLibrary(1, "Forest").size shouldBe 0
                }
            }

            test("declining the land, then binning it, puts it into the graveyard") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Traveling Botanist", summoningSickness = false)
                    .withCardInLibrary(1, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                game.declareAttackers(mapOf("Traveling Botanist" to 2))
                game.resolveStack()

                // First prompt: may take the land to hand — decline by selecting nothing.
                withClue("First prompt should be the take-to-hand selection") {
                    game.hasPendingDecision() shouldBe true
                }
                game.skipSelection()
                game.resolveStack()

                // Second prompt: may bin the declined land — accept.
                withClue("Second prompt should be the put-in-graveyard selection") {
                    game.hasPendingDecision() shouldBe true
                }
                val binDecision = game.getPendingDecision() as SelectCardsDecision
                game.selectCards(listOf(binDecision.options.first()))
                game.resolveStack()

                withClue("The Forest should be in the graveyard") {
                    game.findCardsInGraveyard(1, "Forest").size shouldBe 1
                }
                withClue("The Forest should not be in hand") {
                    game.findCardsInHand(1, "Forest").size shouldBe 0
                }
            }
        }
    }
}
