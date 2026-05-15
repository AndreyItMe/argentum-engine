package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario test for Thought Vessel.
 *
 * Card reference:
 * - Thought Vessel ({2}): Artifact
 *   "You have no maximum hand size."
 *   "{T}: Add {C}."
 */
class ThoughtVesselScenarioTest : ScenarioTestBase() {

    init {
        context("Thought Vessel — no maximum hand size") {
            test("controller with 8 cards in hand discards nothing during cleanup") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Thought Vessel")
                    .withCardsInHand(1, "Mountain", 8)
                    .withActivePlayer(1)
                    .inPhase(Phase.ENDING, Step.END)
                    .build()

                // Both players pass priority in END to transition into CLEANUP.
                game.execute(PassPriority(game.player1Id))
                game.execute(PassPriority(game.player2Id))

                withClue("Cleanup must not raise a discard decision") {
                    game.hasPendingDecision() shouldBe false
                }
                withClue("Hand should still contain all 8 cards") {
                    game.handSize(1) shouldBe 8
                }
            }

            test("controller without Thought Vessel must discard down to 7") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardsInHand(1, "Mountain", 8)
                    .withActivePlayer(1)
                    .inPhase(Phase.ENDING, Step.END)
                    .build()

                game.execute(PassPriority(game.player1Id))
                game.execute(PassPriority(game.player2Id))

                withClue("Cleanup should raise a discard decision for the active player") {
                    game.hasPendingDecision() shouldBe true
                }
            }
        }

        context("Thought Vessel — mana ability") {
            test("{T}: Add {C} produces one colorless mana") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Thought Vessel")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val vessel = game.findPermanent("Thought Vessel")!!
                val ability = cardRegistry.getCard("Thought Vessel")!!.script.activatedAbilities.first()

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = vessel,
                        abilityId = ability.id,
                    )
                )

                withClue("Mana ability should activate successfully: ${result.error}") {
                    result.isSuccess shouldBe true
                }

                val pool = result.newState.getEntity(game.player1Id)?.get<ManaPoolComponent>()
                pool shouldNotBe null
                pool!!.colorless shouldBe 1
            }
        }
    }
}
