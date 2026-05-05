package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Scenario tests for Collective Inferno.
 *
 * Card reference:
 * - Collective Inferno ({3}{R}{R}): Enchantment
 *   "Convoke
 *    As this enchantment enters, choose a creature type.
 *    Double all damage that sources you control of the chosen type would deal."
 */
class CollectiveInfernoScenarioTest : ScenarioTestBase() {

    private fun TestGame.chooseCreatureType(typeName: String) {
        val decision = getPendingDecision()
        decision.shouldNotBeNull()
        decision.shouldBeInstanceOf<ChooseOptionDecision>()
        val index = decision.options.indexOf(typeName)
        withClue("Creature type '$typeName' should be in options") {
            (index >= 0) shouldBe true
        }
        submitDecision(OptionChosenResponse(decision.id, index))
    }

    init {
        context("Collective Inferno - chosen-type damage doubling") {

            test("doubles combat damage from a creature of the chosen type") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Collective Inferno")
                    .withCardOnBattlefield(1, "Elvish Warrior") // 2/3 Elf Warrior
                    .withLandsOnBattlefield(1, "Mountain", 5)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cast = game.castSpell(1, "Collective Inferno")
                cast.error shouldBe null
                game.resolveStack()
                game.chooseCreatureType("Elf")

                val startingLife = game.getLifeTotal(2)

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(mapOf("Elvish Warrior" to 2))
                game.passUntilPhase(Phase.COMBAT, Step.COMBAT_DAMAGE)
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)

                withClue("Elf attacker's 2 damage should be doubled to 4") {
                    game.getLifeTotal(2) shouldBe startingLife - 4
                }
            }

            test("does not double combat damage from a creature of a different type") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Collective Inferno")
                    .withCardOnBattlefield(1, "Goblin Sky Raider") // 2/1 Goblin (flying)
                    .withLandsOnBattlefield(1, "Mountain", 5)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cast = game.castSpell(1, "Collective Inferno")
                cast.error shouldBe null
                game.resolveStack()
                game.chooseCreatureType("Elf")

                val startingLife = game.getLifeTotal(2)

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(mapOf("Goblin Sky Raider" to 2))
                game.passUntilPhase(Phase.COMBAT, Step.COMBAT_DAMAGE)
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)

                withClue("Goblin's 1 damage should NOT be doubled when Elf is the chosen type") {
                    game.getLifeTotal(2) shouldBe startingLife - 1
                }
            }
        }
    }
}
