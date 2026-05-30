package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario test for Spinal Embrace.
 *
 * Spinal Embrace ({3}{U}{U}{B}) — Instant
 *   "Cast this spell only during combat.
 *    Untap target creature you don't control and gain control of it. It gains haste until
 *    end of turn. At the beginning of the next end step, sacrifice it. If you do, you gain
 *    life equal to its toughness."
 */
class SpinalEmbraceScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    init {
        context("Spinal Embrace steals a creature and sacrifices it at the next end step") {
            test("untaps, gains control + haste, then sacrifices for life equal to toughness") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Spinal Embrace")
                    .withLandsOnBattlefield(1, "Island", 4)
                    .withLandsOnBattlefield(1, "Swamp", 2)
                    .withCardOnBattlefield(2, "Grizzly Bears", tapped = true)
                    .withLifeTotal(1, 20)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.BEGIN_COMBAT)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!

                withClue("Grizzly Bears should start tapped") {
                    game.state.getEntity(bears)?.has<TappedComponent>() shouldBe true
                }

                val cast = game.castSpell(1, "Spinal Embrace", bears)
                withClue("Casting Spinal Embrace during combat should succeed") {
                    cast.error shouldBe null
                }
                game.resolveStack()

                val projected = stateProjector.project(game.state)
                withClue("Grizzly Bears should be untapped") {
                    game.state.getEntity(bears)?.has<TappedComponent>() shouldBe false
                }
                withClue("Player 1 should control the stolen creature") {
                    projected.getController(bears) shouldBe game.player1Id
                }
                withClue("Stolen creature should have haste") {
                    projected.hasKeyword(bears, Keyword.HASTE) shouldBe true
                }
                withClue("No life gained yet — sacrifice happens at the end step") {
                    game.getLifeTotal(1) shouldBe 20
                }

                // Advance to the end step: the delayed trigger sacrifices the creature.
                game.passUntilPhase(Phase.ENDING, Step.END)
                game.resolveStack()

                withClue("Grizzly Bears should be sacrificed (gone from the battlefield)") {
                    game.findPermanent("Grizzly Bears") shouldBe null
                }
                withClue("Sacrificed creature goes to its owner's (Player 2) graveyard") {
                    game.isInGraveyard(2, "Grizzly Bears") shouldBe true
                }
                withClue("Player 1 gains life equal to the sacrificed creature's toughness (2)") {
                    game.getLifeTotal(1) shouldBe 22
                }
            }

            test("cannot be cast outside of combat") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Spinal Embrace")
                    .withLandsOnBattlefield(1, "Island", 4)
                    .withLandsOnBattlefield(1, "Swamp", 2)
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!

                val cast = game.castSpell(1, "Spinal Embrace", bears)
                withClue("Casting Spinal Embrace outside combat should be rejected") {
                    cast.error shouldNotBe null
                }
            }
        }
    }
}
