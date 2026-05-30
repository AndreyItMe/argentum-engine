package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ChooseColorDecision
import com.wingedsheep.engine.core.ColorChosenResponse
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.model.CardDefinition
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Searing Rays.
 *
 * Card reference:
 * - Searing Rays ({2}{R}): Sorcery
 *   "Choose a color. Searing Rays deals damage to each player equal to the number of
 *    creatures of that color that player controls."
 *
 * Exercises the ChooseColorThen + ForEachPlayer(DealDamage(Count(HasChosenColor))) composition:
 * each player takes damage equal to the number of creatures of the chosen color *that player*
 * controls — so the per-player Count must be scoped to the iterated player.
 */
class SearingRaysScenarioTest : ScenarioTestBase() {

    // Mono-colored vanilla creatures so the chosen-color count is unambiguous.
    private val greenBear = CardDefinition.creature(
        name = "Green Bear", manaCost = ManaCost.parse("{G}"),
        subtypes = setOf(Subtype("Bear")), power = 2, toughness = 2
    )
    private val blueDrake = CardDefinition.creature(
        name = "Blue Drake", manaCost = ManaCost.parse("{U}"),
        subtypes = setOf(Subtype("Drake")), power = 2, toughness = 2
    )

    init {
        cardRegistry.register(greenBear)
        cardRegistry.register(blueDrake)

        context("Searing Rays color-scoped damage") {

            // Caster (P1): two Green Bears, one Blue Drake.
            // Opponent (P2): one Green Bear.
            fun freshGame() = scenario()
                .withPlayers("Caster", "Opponent")
                .withCardInHand(1, "Searing Rays")
                .withCardOnBattlefield(1, "Green Bear")
                .withCardOnBattlefield(1, "Green Bear")
                .withCardOnBattlefield(1, "Blue Drake")
                .withCardOnBattlefield(2, "Green Bear")
                .withLandsOnBattlefield(1, "Mountain", 3) // pays {2}{R}
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            test("choosing green damages each player per their green creatures") {
                val game = freshGame()
                game.castSpell(1, "Searing Rays")
                game.resolveStack()

                val colorDecision = game.getPendingDecision()
                withClue("Should pause for a color choice on resolution") {
                    (colorDecision is ChooseColorDecision) shouldBe true
                }
                game.submitDecision(ColorChosenResponse(colorDecision!!.id, Color.GREEN))
                game.resolveStack()

                withClue("Caster controls 2 green creatures -> takes 2 damage (20 -> 18)") {
                    game.getLifeTotal(1) shouldBe 18
                }
                withClue("Opponent controls 1 green creature -> takes 1 damage (20 -> 19)") {
                    game.getLifeTotal(2) shouldBe 19
                }
            }

            test("choosing blue only counts blue creatures") {
                val game = freshGame()
                game.castSpell(1, "Searing Rays")
                game.resolveStack()

                val colorDecision = game.getPendingDecision()
                game.submitDecision(ColorChosenResponse(colorDecision!!.id, Color.BLUE))
                game.resolveStack()

                withClue("Caster controls 1 blue creature -> takes 1 damage (20 -> 19)") {
                    game.getLifeTotal(1) shouldBe 19
                }
                withClue("Opponent controls no blue creatures -> takes no damage") {
                    game.getLifeTotal(2) shouldBe 20
                }
            }
        }
    }
}
