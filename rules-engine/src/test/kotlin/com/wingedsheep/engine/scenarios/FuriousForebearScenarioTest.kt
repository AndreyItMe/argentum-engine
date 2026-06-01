package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Scenario test for Furious Forebear (TDM #13) — {1}{W} Spirit Warrior, 3/1.
 *
 * "Whenever a creature you control dies while this card is in your graveyard, you may pay {1}{W}.
 *  If you do, return this card from your graveyard to your hand."
 *
 * A graveyard-zone triggered ability gated to fire only while Furious Forebear sits in its owner's
 * graveyard. We kill another creature the controller owns (Caustic Exhale, -3/-3), then the
 * "you may pay {1}{W}" YesNoDecision surfaces; paying returns Forebear from graveyard to hand.
 * Declining leaves it in the graveyard.
 */
class FuriousForebearScenarioTest : ScenarioTestBase() {

    init {
        context("Furious Forebear's graveyard death trigger") {

            test("paying {1}{W} when a creature you control dies returns it from graveyard to hand") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInGraveyard(1, "Furious Forebear")
                    .withCardOnBattlefield(1, "Grizzly Bears") // 2/2 victim
                    .withCardInHand(1, "Caustic Exhale")
                    .withLandsOnBattlefield(1, "Swamp", 2) // Caustic Exhale: {B} + {1}
                    .withLandsOnBattlefield(1, "Plains", 2) // pay {1}{W} for the trigger
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!
                val cast = game.castSpell(1, "Caustic Exhale", bears)
                withClue("Casting Caustic Exhale on Grizzly Bears should succeed: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack()

                withClue("Grizzly Bears dies to -3/-3") {
                    game.isOnBattlefield("Grizzly Bears") shouldBe false
                }

                // The death triggers Furious Forebear's graveyard ability → "you may pay {1}{W}".
                val decision = game.getPendingDecision()
                withClue("A pay-{1}{W} YesNo decision should be pending; got $decision") {
                    decision.shouldBeInstanceOf<YesNoDecision>()
                }
                game.answerYesNo(true)
                // Paying {1}{W} requires selecting mana sources (no floating mana) — auto-tap.
                game.submitManaSourcesAutoPay()
                game.resolveStack()

                withClue("Furious Forebear returns from graveyard to hand") {
                    game.findCardsInGraveyard(1, "Furious Forebear").size shouldBe 0
                    game.findCardsInHand(1, "Furious Forebear").size shouldBe 1
                }
            }

            test("declining the payment leaves Furious Forebear in the graveyard") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInGraveyard(1, "Furious Forebear")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardInHand(1, "Caustic Exhale")
                    .withLandsOnBattlefield(1, "Swamp", 2)
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!
                val cast = game.castSpell(1, "Caustic Exhale", bears)
                withClue("Casting Caustic Exhale should succeed: ${cast.error}") { cast.error shouldBe null }
                game.resolveStack()

                val decision = game.getPendingDecision()
                withClue("A pay-{1}{W} YesNo decision should be pending; got $decision") {
                    decision.shouldBeInstanceOf<YesNoDecision>()
                }
                game.answerYesNo(false)
                game.resolveStack()

                withClue("Declining leaves Furious Forebear in the graveyard") {
                    game.findCardsInGraveyard(1, "Furious Forebear").size shouldBe 1
                    game.findCardsInHand(1, "Furious Forebear").size shouldBe 0
                }
            }
        }
    }
}
