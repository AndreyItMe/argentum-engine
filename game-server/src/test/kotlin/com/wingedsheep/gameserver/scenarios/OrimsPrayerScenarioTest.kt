package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Orim's Prayer (Tempest).
 *
 * Orim's Prayer ({1}{W}{W}): Enchantment
 *   "Whenever one or more creatures attack you, you gain 1 life for each attacking creature."
 *
 * Per the 2008-04-01 Scryfall ruling, the trigger fires only when creatures attack the
 * controller (the player) — creatures attacking a planeswalker the controller owns do
 * not count.
 */
class OrimsPrayerScenarioTest : ScenarioTestBase() {

    init {
        context("Orim's Prayer triggers on creatures attacking you") {

            test("gains 1 life per attacker when a single creature attacks you") {
                val game = scenario()
                    .withPlayers("Defender", "Attacker")
                    .withCardOnBattlefield(1, "Orim's Prayer")
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withLifeTotal(1, 20)
                    .withActivePlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val lifeBefore = game.getLifeTotal(1)

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                val attackResult = game.declareAttackers(mapOf("Grizzly Bears" to 1))
                withClue("Attack declaration should succeed: ${attackResult.error}") {
                    attackResult.error shouldBe null
                }

                // Trigger goes on the stack — resolve it.
                game.resolveStack()

                withClue("Defender should gain 1 life for the single attacker") {
                    game.getLifeTotal(1) shouldBe lifeBefore + 1
                }
            }

            test("gains life equal to the number of attackers when multiple creatures attack you") {
                val game = scenario()
                    .withPlayers("Defender", "Attacker")
                    .withCardOnBattlefield(1, "Orim's Prayer")
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withCardOnBattlefield(2, "Glory Seeker")
                    .withCardOnBattlefield(2, "Hill Giant")
                    .withLifeTotal(1, 20)
                    .withActivePlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val lifeBefore = game.getLifeTotal(1)

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                val attackResult = game.declareAttackers(
                    mapOf(
                        "Grizzly Bears" to 1,
                        "Glory Seeker" to 1,
                        "Hill Giant" to 1,
                    )
                )
                withClue("Attack declaration should succeed: ${attackResult.error}") {
                    attackResult.error shouldBe null
                }

                game.resolveStack()

                withClue("Defender should gain 3 life — one per attacker") {
                    game.getLifeTotal(1) shouldBe lifeBefore + 3
                }
            }

            test("does not trigger when no creatures attack") {
                val game = scenario()
                    .withPlayers("Defender", "Attacker")
                    .withCardOnBattlefield(1, "Orim's Prayer")
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withLifeTotal(1, 20)
                    .withActivePlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val lifeBefore = game.getLifeTotal(1)

                // Active player declares no attackers and passes through combat.
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(emptyMap())

                withClue("Defender's life should be unchanged when no creatures attack") {
                    game.getLifeTotal(1) shouldBe lifeBefore
                }
            }
        }
    }
}
