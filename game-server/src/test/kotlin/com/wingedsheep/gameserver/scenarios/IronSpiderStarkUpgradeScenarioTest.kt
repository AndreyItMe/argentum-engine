package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldContain

/**
 * Scenario tests for Iron Spider, Stark Upgrade.
 *
 * Card reference:
 * - Iron Spider, Stark Upgrade ({1}{W}{U}): Legendary Artifact Creature — Spider Hero, 2/3
 *   "Vigilance"
 *   "{T}: Put a +1/+1 counter on each artifact creature and Vehicle you control."
 *   "{2}, Remove two +1/+1 counters from among artifacts you control: Draw a card."
 */
class IronSpiderStarkUpgradeScenarioTest : ScenarioTestBase() {

    init {
        context("Iron Spider, Stark Upgrade enters the battlefield") {

            test("resolves and lands on battlefield as a legendary artifact creature Spider Hero with 2/3 and vigilance") {
                // GIVEN Player has Iron Spider, Stark Upgrade in hand
                // AND Player has at least {3} of available mana (1W1U)
                // AND It is the player's main phase with priority
                val game = scenario()
                    .withPlayers("Tony", "Opponent")
                    .withCardInHand(1, "Iron Spider, Stark Upgrade")
                    .withLandsOnBattlefield(1, "Plains", 1)
                    .withLandsOnBattlefield(1, "Island", 1)
                    .withLandsOnBattlefield(1, "Plains", 1)
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // WHEN Player casts Iron Spider, Stark Upgrade
                val castResult = game.castSpell(1, "Iron Spider, Stark Upgrade")
                withClue("Casting Iron Spider, Stark Upgrade should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                // THEN Iron Spider, Stark Upgrade resolves and is on the battlefield
                withClue("Iron Spider, Stark Upgrade should be on the battlefield") {
                    game.isOnBattlefield("Iron Spider, Stark Upgrade") shouldBe true
                }

                val ironSpiderId = game.findPermanent("Iron Spider, Stark Upgrade")!!
                val clientState = game.getClientState(1)
                val ironSpiderCard = clientState.cards[ironSpiderId]!!

                // AND It is a legendary artifact creature with subtypes Spider and Hero
                withClue("Iron Spider should have typeLine containing Legendary Artifact Creature") {
                    ironSpiderCard.typeLine shouldBe "Legendary Artifact Creature — Spider Hero"
                }
                withClue("Iron Spider should have Spider subtype") {
                    ironSpiderCard.subtypes shouldContain "Spider"
                }
                withClue("Iron Spider should have Hero subtype") {
                    ironSpiderCard.subtypes shouldContain "Hero"
                }

                // AND It has power 2 and toughness 3
                withClue("Iron Spider should have power 2") {
                    ironSpiderCard.power shouldBe 2
                }
                withClue("Iron Spider should have toughness 3") {
                    ironSpiderCard.toughness shouldBe 3
                }

                // AND It has the vigilance keyword
                withClue("Iron Spider should have vigilance") {
                    ironSpiderCard.keywords shouldContain Keyword.VIGILANCE
                }
            }
        }
    }
}
