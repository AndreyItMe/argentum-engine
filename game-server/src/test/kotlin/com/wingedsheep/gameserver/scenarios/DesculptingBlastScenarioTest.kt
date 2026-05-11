package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Desculpting Blast.
 *
 * Card reference:
 * - Desculpting Blast ({1}{U}): Instant
 *   "Return target nonland permanent to its owner's hand. If it was attacking,
 *    create a 1/1 colorless Drone artifact creature token with flying and
 *    'This token can block only creatures with flying.'"
 *
 * Also covers a regression in CreatePredefinedTokenExecutor: predefined creature
 * tokens previously entered with null baseStats and died immediately to the
 * zero-toughness SBA. The Drone-creation branch only passes if the executor
 * copies baseStats/baseKeywords/colors from the registered CardDefinition.
 */
class DesculptingBlastScenarioTest : ScenarioTestBase() {

    init {
        context("Desculpting Blast") {

            test("returns an attacking creature to its owner's hand and creates a Drone token") {
                val game = scenario()
                    .withPlayers("Caster", "Attacker")
                    .withCardInHand(1, "Desculpting Blast")
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withCardOnBattlefield(2, "Hill Giant") // 3/3, P2's attacker
                    .withActivePlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Advance into P2's declare-attackers step and have Hill Giant attack.
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(mapOf("Hill Giant" to 1))

                // After declaring attackers, the active player (P2) holds priority. Pass it
                // to the defender (P1) so P1 can cast Desculpting Blast at instant speed
                // while Hill Giant is still on the battlefield as an attacker.
                if (game.state.priorityPlayerId == game.state.activePlayerId) {
                    game.passPriority()
                }

                val attackerId = game.findPermanent("Hill Giant")!!
                val cast = game.castSpell(1, "Desculpting Blast", attackerId)
                withClue("Desculpting Blast should cast successfully: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack()

                withClue("Hill Giant should be returned to its owner's hand") {
                    game.isOnBattlefield("Hill Giant") shouldBe false
                    game.isInHand(2, "Hill Giant") shouldBe true
                }

                // The conditional Drone-creation branch fired because the target was attacking.
                withClue("A Drone token should be on P1's battlefield") {
                    game.isOnBattlefield("Drone") shouldBe true
                }

                // Regression: the Drone must have baseStats from its CardDefinition.
                // Without the executor fix it would enter with null toughness and die
                // to the zero-toughness state-based action before reaching this assertion.
                val droneId = game.findPermanent("Drone")!!
                val drone = game.getClientState(1).cards[droneId]!!
                withClue("Drone should be a 1/1 with flying") {
                    drone.power shouldBe 1
                    drone.toughness shouldBe 1
                    drone.keywords shouldBe setOf(Keyword.FLYING)
                }
            }

            test("returns a non-attacking creature to its owner's hand without creating a Drone") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Desculpting Blast")
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withCardOnBattlefield(2, "Hill Giant")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val targetId = game.findPermanent("Hill Giant")!!
                val cast = game.castSpell(1, "Desculpting Blast", targetId)
                withClue("Desculpting Blast should cast successfully: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack()

                withClue("Hill Giant should be returned to its owner's hand") {
                    game.isOnBattlefield("Hill Giant") shouldBe false
                    game.isInHand(2, "Hill Giant") shouldBe true
                }
                withClue("No Drone token should be created — target was not attacking") {
                    game.isOnBattlefield("Drone") shouldBe false
                }
            }
        }
    }
}
