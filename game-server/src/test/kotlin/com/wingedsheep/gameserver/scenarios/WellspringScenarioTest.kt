package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Wellspring (MIR #288).
 *
 * Card reference:
 * - Wellspring ({1}{G}{W}): Enchantment — Aura
 *   "Enchant land"
 *   "When this Aura enters, gain control of enchanted land until end of turn."
 *   "At the beginning of your upkeep, untap enchanted land. You gain control of
 *    that land until end of turn."
 *
 * Exercises the new EffectTarget.EnchantedPermanent (attachment-relative target that
 * resolves to a non-creature permanent) and the GainControlExecutor state-aware
 * resolution fix.
 */
class WellspringScenarioTest : ScenarioTestBase() {

    init {
        context("Wellspring temporary control of enchanted land") {

            test("ETB grants control of the enchanted land until end of turn") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Wellspring")
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withLandsOnBattlefield(1, "Plains", 1)
                    .withLandsOnBattlefield(2, "Island", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val opponentLand = game.findPermanent("Island")!!

                withClue("Opponent should control their Island before Wellspring") {
                    game.state.projectedState.getController(opponentLand) shouldBe game.player2Id
                }

                val castResult = game.castSpell(1, "Wellspring", opponentLand)
                withClue("Cast should succeed") { castResult.error shouldBe null }
                game.resolveStack()

                withClue("Wellspring should be attached to the opponent's land") {
                    val wellspringId = game.findPermanent("Wellspring")!!
                    game.state.getEntity(wellspringId)!!.get<AttachedToComponent>()!!.targetId shouldBe opponentLand
                }

                withClue("ETB trigger should give P1 control of the enchanted land") {
                    game.state.projectedState.getController(opponentLand) shouldBe game.player1Id
                }
            }

            test("control reverts at end of turn, then the upkeep trigger re-grants it") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Wellspring")
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withLandsOnBattlefield(1, "Plains", 1)
                    .withLandsOnBattlefield(2, "Island", 1)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val opponentLand = game.findPermanent("Island")!!

                game.castSpell(1, "Wellspring", opponentLand)
                game.resolveStack()
                withClue("P1 controls the land after the ETB trigger") {
                    game.state.projectedState.getController(opponentLand) shouldBe game.player1Id
                }

                // Advance into P2's turn (P2's upkeep); the until-end-of-turn control
                // effect from P1's turn should have expired during cleanup.
                game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP)
                withClue("Control reverts to P2 once P1's turn ends") {
                    game.state.projectedState.getController(opponentLand) shouldBe game.player2Id
                }
                val p2Turn = game.state.turnNumber

                // Advance to P1's next upkeep (the turn after P2's).
                var iterations = 0
                while (iterations < 200 &&
                    !(game.state.activePlayerId == game.player1Id &&
                        game.state.step == Step.UPKEEP &&
                        game.state.turnNumber > p2Turn)
                ) {
                    if (game.state.pendingDecision != null) break
                    val p = game.state.priorityPlayerId ?: break
                    game.execute(PassPriority(p))
                    iterations++
                }

                withClue("Should have reached P1's upkeep on a later turn") {
                    (game.state.activePlayerId == game.player1Id && game.state.step == Step.UPKEEP) shouldBe true
                }

                // Resolve the Wellspring upkeep trigger.
                game.resolveStack()

                withClue("Upkeep trigger re-grants control of the enchanted land to P1") {
                    game.state.projectedState.getController(opponentLand) shouldBe game.player1Id
                }
            }
        }
    }
}
