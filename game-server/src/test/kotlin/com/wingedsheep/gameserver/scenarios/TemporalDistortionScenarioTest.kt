package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.AbilityFlag
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario test for Temporal Distortion.
 *
 * Temporal Distortion ({3}{U}{U}) — Enchantment
 *   "Whenever a creature or land becomes tapped, put an hourglass counter on it.
 *    Each permanent with an hourglass counter on it doesn't untap during its controller's untap step.
 *    At the beginning of each player's upkeep, remove all hourglass counters from permanents that
 *    player controls."
 */
class TemporalDistortionScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    private fun TestGame.hourglassOn(id: EntityId): Int =
        state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.HOURGLASS) ?: 0

    private fun TestGame.addHourglass(id: EntityId) {
        state = state.updateEntity(id) { c ->
            c.with((c.get<CountersComponent>() ?: CountersComponent()).withAdded(CounterType.HOURGLASS, 1))
        }
    }

    init {
        context("Temporal Distortion taps-to-counter trigger") {
            test("a creature that becomes tapped gets an hourglass counter and stops untapping") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Temporal Distortion")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!

                // Attacking taps Grizzly Bears, which fires the "becomes tapped" trigger.
                game.declareAttackers(mapOf("Grizzly Bears" to 2))
                game.resolveStack()

                withClue("Grizzly Bears should have an hourglass counter after becoming tapped") {
                    game.hourglassOn(bears) shouldBe 1
                }
                withClue("A permanent with an hourglass counter is granted DOESN'T UNTAP") {
                    stateProjector.project(game.state)
                        .hasKeyword(bears, AbilityFlag.DOESNT_UNTAP) shouldBe true
                }
            }
        }

        context("Hourglass counters skip the untap step then clear at upkeep") {
            test("counter keeps the creature tapped through its untap step, then is removed at upkeep") {
                // Start at Player 2's end step so advancing crosses into Player 1's untap + upkeep.
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Temporal Distortion")
                    .withCardOnBattlefield(1, "Grizzly Bears", tapped = true)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(2)
                    .inPhase(Phase.ENDING, Step.END)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!
                game.addHourglass(bears)

                withClue("Setup: Grizzly Bears is tapped with one hourglass counter") {
                    game.state.getEntity(bears)?.has<TappedComponent>() shouldBe true
                    game.hourglassOn(bears) shouldBe 1
                }

                // Cross into Player 1's untap step (skipped) and stop at the upkeep priority window,
                // before the remove-counters trigger resolves.
                game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP)

                withClue("Now on Player 1's turn") {
                    game.state.activePlayerId shouldBe game.player1Id
                }
                withClue("Grizzly Bears did NOT untap during its controller's untap step") {
                    game.state.getEntity(bears)?.has<TappedComponent>() shouldBe true
                }
                withClue("Counter still present until the upkeep trigger resolves") {
                    game.hourglassOn(bears) shouldBe 1
                }

                // Resolve the begin-of-upkeep "remove all hourglass counters" trigger.
                game.resolveStack()

                withClue("Hourglass counter removed at its controller's upkeep") {
                    game.hourglassOn(bears) shouldBe 0
                }
            }

            test("upkeep removal only clears counters from the upkeep player's permanents") {
                // Player 1's upkeep should remove Player 1's hourglass counters but leave Player 2's.
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Temporal Distortion")
                    .withCardOnBattlefield(1, "Grizzly Bears", tapped = true)
                    .withCardOnBattlefield(2, "Hill Giant", tapped = true)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(2)
                    .inPhase(Phase.ENDING, Step.END)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!
                val giant = game.findPermanent("Hill Giant")!!
                game.addHourglass(bears)
                game.addHourglass(giant)

                // Cross into Player 1's upkeep and resolve the removal trigger.
                game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP)
                game.resolveStack()

                withClue("Player 1's permanent loses its hourglass counter on Player 1's upkeep") {
                    game.hourglassOn(bears) shouldBe 0
                }
                withClue("Player 2's permanent keeps its hourglass counter on Player 1's upkeep") {
                    game.hourglassOn(giant) shouldBe 1
                }
            }
        }
    }
}
