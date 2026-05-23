package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.battlefield.WarpedComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Full Bore (Edge of Eternities).
 *
 * {R} Instant
 *   Target creature you control gets +3/+2 until end of turn. If that creature
 *   was cast for its warp cost, it also gains trample and haste until end of turn.
 *
 * Exercises the new [com.wingedsheep.sdk.scripting.predicates.StatePredicate.WasCastForWarp]
 * predicate via `Conditions.TargetMatchesFilter(GameObjectFilter.Creature.castForWarp())`.
 */
class FullBoreScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    init {
        context("Full Bore — non-warped target") {

            test("pumps target by +3/+2 but does not grant trample or haste") {
                val game = scenario()
                    .withPlayers("Active", "Opponent")
                    .withCardInHand(1, "Full Bore")
                    .withCardOnBattlefield(1, "Weftstalker Ardent")    // 2/3, not warp-cast
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val ardentId = game.findPermanent("Weftstalker Ardent")!!

                val castResult = game.castSpell(1, "Full Bore", targetId = ardentId)
                withClue("Casting Full Bore for {R} should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                val projected = stateProjector.project(game.state)

                withClue("Ardent should be a 5/5 (2/3 base + 3/+2)") {
                    projected.getPower(ardentId) shouldBe 5
                    projected.getToughness(ardentId) shouldBe 5
                }

                withClue("Non-warped target should NOT gain trample") {
                    projected.hasKeyword(ardentId, Keyword.TRAMPLE) shouldBe false
                }
                withClue("Non-warped target should NOT gain haste") {
                    projected.hasKeyword(ardentId, Keyword.HASTE) shouldBe false
                }
            }
        }

        context("Full Bore — warp-cast target") {

            test("pumps +3/+2 and grants trample + haste when target was cast for its warp cost") {
                val game = scenario()
                    .withPlayers("Active", "Opponent")
                    .withCardInHand(1, "Full Bore")
                    .withCardOnBattlefield(1, "Weftstalker Ardent")
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val ardentId = game.findPermanent("Weftstalker Ardent")!!

                // Mark the creature as having been cast for its warp cost (CR 702.185a).
                // In real play the StackResolver writes this when a warped spell resolves;
                // here we set it directly to isolate Full Bore's branch.
                game.state = game.state.updateEntity(ardentId) { c -> c.with(WarpedComponent) }

                val castResult = game.castSpell(1, "Full Bore", targetId = ardentId)
                withClue("Casting Full Bore for {R} should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                val projected = stateProjector.project(game.state)

                withClue("Ardent should be a 5/5 (2/3 base + 3/+2)") {
                    projected.getPower(ardentId) shouldBe 5
                    projected.getToughness(ardentId) shouldBe 5
                }

                withClue("Warp-cast target should gain trample") {
                    projected.hasKeyword(ardentId, Keyword.TRAMPLE) shouldBe true
                }
                withClue("Warp-cast target should gain haste") {
                    projected.hasKeyword(ardentId, Keyword.HASTE) shouldBe true
                }
            }
        }
    }
}
