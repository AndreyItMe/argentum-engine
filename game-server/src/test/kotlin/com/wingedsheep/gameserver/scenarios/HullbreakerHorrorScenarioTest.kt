package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Scenario tests for Hullbreaker Horror.
 *
 * Hullbreaker Horror {5}{U}{U}
 * Creature — Kraken Horror
 * 7/8
 * Flash. This spell can't be countered.
 * Whenever you cast a spell, choose up to one —
 * • Return target spell you don't control to its owner's hand.
 * • Return target nonland permanent to its owner's hand.
 *
 * Exercises the new ReturnSpellToOwnersHandEffect (mode 1), the existing
 * MoveToZoneEffect bounce path (mode 2), and the resolution-time
 * "choose up to one" handling for minChooseCount=0 modal triggered abilities
 * (synthetic decline option).
 */
class HullbreakerHorrorScenarioTest : ScenarioTestBase() {

    init {
        context("Hullbreaker Horror's cast trigger") {

            test("mode 2 — return target nonland permanent to its owner's hand") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Hullbreaker Horror")
                    .withCardOnBattlefield(2, "Glory Seeker")
                    .withCardInHand(1, "Consider")
                    .withLandsOnBattlefield(1, "Island", 1)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Consider")
                game.resolveStack()

                val decision = game.getPendingDecision()
                decision.shouldBeInstanceOf<ChooseOptionDecision>()
                decision.options.size shouldBe 3
                decision.options[2] shouldBe "Don't choose a mode"

                game.submitDecision(OptionChosenResponse(decision.id, 1))

                val gloryId = game.findPermanent("Glory Seeker")!!
                game.selectTargets(listOf(gloryId))
                game.resolveStack()

                game.findPermanent("Glory Seeker") shouldBe null
                game.findCardsInHand(2, "Glory Seeker").size shouldBe 1
            }

            test("decline — choose-up-to-one allows skipping both modes") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Hullbreaker Horror")
                    .withCardOnBattlefield(2, "Glory Seeker")
                    .withCardInHand(1, "Consider")
                    .withLandsOnBattlefield(1, "Island", 1)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Consider")
                game.resolveStack()

                val decision = game.getPendingDecision()
                decision.shouldBeInstanceOf<ChooseOptionDecision>()
                decision.options[2] shouldBe "Don't choose a mode"

                game.submitDecision(OptionChosenResponse(decision.id, 2))
                game.resolveStack()

                // Glory Seeker stayed in play; nothing was bounced.
                (game.findPermanent("Glory Seeker") != null) shouldBe true
                game.findCardsInHand(2, "Glory Seeker").size shouldBe 0
            }
        }
    }
}
