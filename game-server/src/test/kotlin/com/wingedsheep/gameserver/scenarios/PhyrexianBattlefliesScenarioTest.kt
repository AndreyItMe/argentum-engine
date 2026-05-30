package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.AbilityActivatedThisTurnComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for Phyrexian Battleflies.
 *
 * Card reference:
 * - Phyrexian Battleflies ({B}): 0/1 Creature — Phyrexian Insect
 *   Flying
 *   "{B}: This creature gets +1/+0 until end of turn. Activate no more than twice each turn."
 *
 * Exercises the new [com.wingedsheep.sdk.scripting.ActivationRestriction.MaxPerTurn] restriction.
 */
class PhyrexianBattlefliesScenarioTest : ScenarioTestBase() {

    private fun addBlackMana(game: TestGame, amount: Int) {
        game.state = game.state.updateEntity(game.player1Id) { container ->
            container.with(ManaPoolComponent(black = amount))
        }
    }

    private fun pump(game: TestGame): String? {
        val id = game.findPermanent("Phyrexian Battleflies")!!
        val ability = cardRegistry.getCard("Phyrexian Battleflies")!!.script.activatedAbilities.first()
        return game.execute(
            ActivateAbility(playerId = game.player1Id, sourceId = id, abilityId = ability.id)
        ).error
    }

    private fun power(game: TestGame): Int? {
        val id = game.findPermanent("Phyrexian Battleflies")!!
        return game.getClientState(1).cards[id]?.power
    }

    init {
        context("Phyrexian Battleflies — Activate no more than twice each turn") {

            test("can activate twice and gains +1/+0 each time, third activation is rejected") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Phyrexian Battleflies")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                withClue("base power is 0") { power(game) shouldBe 0 }

                addBlackMana(game, 1)
                withClue("first activation succeeds") { pump(game) shouldBe null }
                game.resolveStack()
                withClue("power is 1 after first pump") { power(game) shouldBe 1 }

                addBlackMana(game, 1)
                withClue("second activation succeeds") { pump(game) shouldBe null }
                game.resolveStack()
                withClue("power is 2 after second pump") { power(game) shouldBe 2 }

                addBlackMana(game, 1)
                withClue("third activation is rejected") { pump(game) shouldNotBe null }
            }

            test("the limit resets on a new turn") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Phyrexian Battleflies")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                addBlackMana(game, 1)
                pump(game) shouldBe null
                game.resolveStack()
                addBlackMana(game, 1)
                pump(game) shouldBe null
                game.resolveStack()
                addBlackMana(game, 1)
                withClue("third activation this turn is rejected") { pump(game) shouldNotBe null }

                // Model end-of-turn cleanup: CleanupPhaseManager removes
                // AbilityActivatedThisTurnComponent, which clears the per-turn activation counts.
                val battlefliesId = game.findPermanent("Phyrexian Battleflies")!!
                game.state = game.state.updateEntity(battlefliesId) { c ->
                    c.without<AbilityActivatedThisTurnComponent>()
                }

                addBlackMana(game, 1)
                withClue("first activation on the new turn succeeds again") { pump(game) shouldBe null }
            }
        }
    }
}
