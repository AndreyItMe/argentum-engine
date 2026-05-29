package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.model.CardDefinition
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Mana Maze (Invasion gap #18).
 *
 * Card reference:
 * - Mana Maze ({1}{U}): Enchantment
 *   Players can't cast spells that share a color with the spell most recently cast this turn.
 *
 * Exercises the `CantCastSpellsSharingColorWithLastCast` global cast restriction backed by
 * `GameState.lastCastSpellColors`: the restriction never blocks the first spell of the turn,
 * blocks a spell sharing a color with the most recently cast one, lets a differently-colored
 * spell through, and is lifted by casting a colorless spell.
 */
class ManaMazeScenarioTest : ScenarioTestBase() {

    private val blueBearA = CardDefinition.creature(
        name = "MM Blue Bear A",
        manaCost = ManaCost.parse("{1}{U}"),
        subtypes = setOf(Subtype("Bear")),
        power = 2,
        toughness = 2
    )

    private val blueBearB = CardDefinition.creature(
        name = "MM Blue Bear B",
        manaCost = ManaCost.parse("{1}{U}"),
        subtypes = setOf(Subtype("Bear")),
        power = 2,
        toughness = 2
    )

    private val redBear = CardDefinition.creature(
        name = "MM Red Bear",
        manaCost = ManaCost.parse("{1}{R}"),
        subtypes = setOf(Subtype("Bear")),
        power = 2,
        toughness = 2
    )

    private val colorlessGolem = CardDefinition.creature(
        name = "MM Colorless Golem",
        manaCost = ManaCost.parse("{2}"),
        subtypes = setOf(Subtype("Golem")),
        power = 2,
        toughness = 2
    )

    init {
        cardRegistry.register(blueBearA)
        cardRegistry.register(blueBearB)
        cardRegistry.register(redBear)
        cardRegistry.register(colorlessGolem)

        fun newGame(): TestGame {
            val game = scenario()
                .withPlayers("Caster", "Opponent")
                .withCardOnBattlefield(1, "Mana Maze")
                .withCardInHand(1, "MM Blue Bear A")
                .withCardInHand(1, "MM Blue Bear B")
                .withCardInHand(1, "MM Red Bear")
                .withCardInHand(1, "MM Colorless Golem")
                .withActivePlayer(1)
                .withPriorityPlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            // Plenty of mana of every color so payment never gates the test.
            game.state = game.state.updateEntity(game.player1Id) {
                it.with(ManaPoolComponent(white = 5, blue = 5, black = 5, red = 5, green = 5, colorless = 5))
            }
            return game
        }

        context("Mana Maze") {
            test("does not block the first spell of the turn") {
                val game = newGame()
                withClue("No spell cast yet this turn — nothing to share a color with") {
                    game.castSpell(1, "MM Blue Bear A").error shouldBe null
                }
            }

            test("blocks a second spell sharing a color with the most recently cast spell") {
                val game = newGame()
                game.castSpell(1, "MM Blue Bear A").error shouldBe null
                game.resolveStack()

                withClue("Second blue spell shares blue with the most recently cast spell") {
                    game.castSpell(1, "MM Blue Bear B").error.shouldNotBeNull()
                }
            }

            test("allows a spell that shares no color with the most recently cast spell") {
                val game = newGame()
                game.castSpell(1, "MM Blue Bear A").error shouldBe null
                game.resolveStack()

                withClue("Red shares no color with the blue spell just cast") {
                    game.castSpell(1, "MM Red Bear").error shouldBe null
                }
            }

            test("a colorless spell is always castable and lifts the restriction") {
                val game = newGame()
                game.castSpell(1, "MM Blue Bear A").error shouldBe null
                game.resolveStack()

                // Colorless shares no color, so it is castable even after a colored spell.
                game.castSpell(1, "MM Colorless Golem").error shouldBe null
                game.resolveStack()

                withClue("After a colorless spell, the most-recent colors are empty, so blue is free again") {
                    game.castSpell(1, "MM Blue Bear B").error shouldBe null
                }
            }
        }
    }
}
