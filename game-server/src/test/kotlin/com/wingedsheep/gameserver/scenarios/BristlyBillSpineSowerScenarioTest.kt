package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Bristly Bill, Spine Sower (OTJ).
 *
 * {1}{G} Legendary Creature — Plant Druid, 2/2.
 * Landfall — Whenever a land you control enters, put a +1/+1 counter on target creature.
 * {3}{G}{G}: Double the number of +1/+1 counters on each creature you control.
 */
class BristlyBillSpineSowerScenarioTest : ScenarioTestBase() {

    private fun activateDoublingAbility(game: TestGame) {
        val billId = game.findPermanent("Bristly Bill, Spine Sower")!!
        val cardDef = cardRegistry.getCard("Bristly Bill, Spine Sower")!!
        val ability = cardDef.script.activatedAbilities.first()

        val result = game.execute(
            ActivateAbility(
                playerId = game.player1Id,
                sourceId = billId,
                abilityId = ability.id
            )
        )
        withClue("Activation should succeed: ${result.error}") {
            result.error shouldBe null
        }
    }

    private fun addManaForDoublingAbility(game: TestGame) {
        // Cost is {3}{G}{G}: 3 colorless + 2 green
        game.state = game.state.updateEntity(game.player1Id) { container ->
            container.with(ManaPoolComponent(green = 2, colorless = 3))
        }
    }

    private fun plusOneCounters(game: TestGame, cardName: String): Int =
        game.findPermanent(cardName)?.let { id ->
            game.state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE)
        } ?: 0

    init {
        context("Bristly Bill — Landfall trigger") {

            test("puts a +1/+1 counter on target creature when a land enters") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Bristly Bill, Spine Sower")
                    .withCardInHand(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val billId = game.findPermanent("Bristly Bill, Spine Sower")!!
                val forestId = game.state.getHand(game.player1Id).first { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Forest"
                }
                game.execute(PlayLand(game.player1Id, forestId))

                withClue("Landfall should pause for target selection") {
                    game.hasPendingDecision() shouldBe true
                }
                game.selectTargets(listOf(billId))
                game.resolveStack()

                withClue("Bristly Bill should have 1 +1/+1 counter from Landfall") {
                    plusOneCounters(game, "Bristly Bill, Spine Sower") shouldBe 1
                }
            }
        }

        context("Bristly Bill — activated ability doubles counters via EntityReference.IterationEntity") {

            test("doubles +1/+1 counters on each creature independently") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Bristly Bill, Spine Sower")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withLandsOnBattlefield(1, "Forest", 5)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val billId = game.findPermanent("Bristly Bill, Spine Sower")!!
                val bearsId = game.findPermanent("Grizzly Bears")!!

                // Manually place counters: Bill gets 1, Bears gets 3
                game.state = game.state
                    .updateEntity(billId) { c -> c.with(CountersComponent().withAdded(CounterType.PLUS_ONE_PLUS_ONE, 1)) }
                    .updateEntity(bearsId) { c -> c.with(CountersComponent().withAdded(CounterType.PLUS_ONE_PLUS_ONE, 3)) }

                addManaForDoublingAbility(game)
                activateDoublingAbility(game)
                game.resolveStack()

                // Bill: 1 counter → doubled → 2; Bears: 3 counters → doubled → 6
                withClue("Bill should have 2 counters after doubling (was 1)") {
                    plusOneCounters(game, "Bristly Bill, Spine Sower") shouldBe 2
                }
                withClue("Bears should have 6 counters after doubling (was 3)") {
                    plusOneCounters(game, "Grizzly Bears") shouldBe 6
                }
            }

            test("doubling 0 counters is a no-op") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Bristly Bill, Spine Sower")
                    .withLandsOnBattlefield(1, "Forest", 5)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                addManaForDoublingAbility(game)
                activateDoublingAbility(game)
                game.resolveStack()

                withClue("Bill with no counters should remain at 0 after doubling") {
                    plusOneCounters(game, "Bristly Bill, Spine Sower") shouldBe 0
                }
            }
        }
    }
}
