package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.CardDefinition
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Tsabo's Assassin's activated ability.
 *
 * Tsabo's Assassin: {2}{B}{B} 1/1 Creature - Phyrexian Zombie Assassin
 * "{T}: Destroy target creature if it shares a color with the most common color among all
 *  permanents or a color tied for most common. A creature destroyed this way can't be regenerated."
 *
 * Exercises the new [com.wingedsheep.sdk.scripting.conditions.TargetSharesMostCommonColor] condition.
 */
class TsabosAssassinScenarioTest : ScenarioTestBase() {

    // Mono-color vanilla creatures so colour tallies are fully deterministic.
    private val blackCreature = CardDefinition.creature(
        name = "Test Black Creature",
        manaCost = ManaCost.parse("{B}"),
        subtypes = emptySet(),
        power = 2,
        toughness = 2
    )
    private val greenCreature = CardDefinition.creature(
        name = "Test Green Creature",
        manaCost = ManaCost.parse("{G}"),
        subtypes = emptySet(),
        power = 2,
        toughness = 2
    )

    init {
        cardRegistry.register(blackCreature)
        cardRegistry.register(greenCreature)

        context("Tsabo's Assassin conditional destroy") {

            test("destroys a target that shares the most common color") {
                // Board: Assassin (B) + target black creature (B) → black is the most common
                // color, the target is black, so it is destroyed and can't be regenerated.
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Tsabo's Assassin")
                    .withCardOnBattlefield(2, "Test Black Creature")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val assassinId = game.findPermanent("Tsabo's Assassin")!!
                val targetId = game.findPermanent("Test Black Creature")!!

                val ability = cardRegistry.getCard("Tsabo's Assassin")!!.script.activatedAbilities.first()

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = assassinId,
                        abilityId = ability.id,
                        targets = listOf(ChosenTarget.Permanent(targetId))
                    )
                )
                withClue("Activation should succeed: ${result.error}") { result.error shouldBe null }

                game.resolveStack()

                withClue("Black target should be destroyed (shares most common colour)") {
                    game.isOnBattlefield("Test Black Creature") shouldBe false
                    game.isInGraveyard(2, "Test Black Creature") shouldBe true
                }
            }

            test("does not destroy a target whose colour is not the most common") {
                // Board: Assassin (B) + two extra black creatures (B,B) → black count 3.
                // The green target contributes green count 1, so black is the unique most common
                // colour. The green target shares no most-common colour and survives.
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Tsabo's Assassin")
                    .withCardOnBattlefield(1, "Test Black Creature")
                    .withCardOnBattlefield(1, "Test Black Creature")
                    .withCardOnBattlefield(2, "Test Green Creature")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val assassinId = game.findPermanent("Tsabo's Assassin")!!
                val targetId = game.findPermanent("Test Green Creature")!!

                val ability = cardRegistry.getCard("Tsabo's Assassin")!!.script.activatedAbilities.first()

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = assassinId,
                        abilityId = ability.id,
                        targets = listOf(ChosenTarget.Permanent(targetId))
                    )
                )
                withClue("Activation should succeed: ${result.error}") { result.error shouldBe null }

                game.resolveStack()

                withClue("Green target should survive (does not share the most common colour)") {
                    game.isOnBattlefield("Test Green Creature") shouldBe true
                }
            }

            test("destroys a target whose colour is tied for most common") {
                // Board: Assassin (B) + green target → black 1, green 1, tied for most common.
                // The green target shares a colour tied for most common, so it is destroyed.
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Tsabo's Assassin")
                    .withCardOnBattlefield(2, "Test Green Creature")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val assassinId = game.findPermanent("Tsabo's Assassin")!!
                val targetId = game.findPermanent("Test Green Creature")!!

                val ability = cardRegistry.getCard("Tsabo's Assassin")!!.script.activatedAbilities.first()

                game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = assassinId,
                        abilityId = ability.id,
                        targets = listOf(ChosenTarget.Permanent(targetId))
                    )
                )
                game.resolveStack()

                withClue("Green target should be destroyed (tied for most common colour)") {
                    game.isOnBattlefield("Test Green Creature") shouldBe false
                    game.isInGraveyard(2, "Test Green Creature") shouldBe true
                }
            }
        }
    }
}
