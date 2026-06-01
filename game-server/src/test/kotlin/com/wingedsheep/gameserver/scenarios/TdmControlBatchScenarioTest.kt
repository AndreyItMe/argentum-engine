package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for a TDM control/removal batch:
 *  - Stormplain Detainment (#28): O-Ring — exile a nonland permanent until it leaves.
 *  - Overwhelming Surge (#115): modal "choose one or both" — 3 damage to a creature
 *    and/or destroy a noncreature artifact.
 *  - Spectral Denial (#58): {X}{U} counter-unless-pays-{X}, costing {1} less per controlled
 *    creature with power 4 or greater.
 *  - Inevitable Defeat (#194): can't be countered; exile a nonland permanent, its controller
 *    loses 3 life and you gain 3 life.
 */
class TdmControlBatchScenarioTest : ScenarioTestBase() {

    init {
        context("Stormplain Detainment") {
            // The exile-until-leaves / return-on-LTB seam is exercised end-to-end by
            // BanishingLightTest; here we just confirm Stormplain Detainment exiles an opponent's
            // nonland permanent on ETB and links it for return.
            test("exiles an opponent's creature on ETB") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Stormplain Detainment")
                    .withLandsOnBattlefield(1, "Plains", 3)
                    .withCardOnBattlefield(2, "Hill Giant")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val victim = game.findPermanent("Hill Giant")!!
                val cast = game.castSpell(1, "Stormplain Detainment")
                withClue("Cast should succeed: ${cast.error}") { cast.error shouldBe null }
                game.resolveStack() // enchantment enters → ETB trigger on stack asks for target

                val selected = game.selectTargets(listOf(victim))
                withClue("ETB target selection should succeed: ${selected.error}") {
                    selected.error shouldBe null
                }
                game.resolveStack()

                withClue("Hill Giant should be exiled while the enchantment is in play") {
                    game.isOnBattlefield("Hill Giant") shouldBe false
                    game.state.getExile(game.player2Id).count {
                        game.state.getEntity(it)?.get<CardComponent>()?.name == "Hill Giant"
                    } shouldBe 1
                }
                withClue("Stormplain Detainment should be on the battlefield holding the exile") {
                    game.isOnBattlefield("Stormplain Detainment") shouldBe true
                }
            }
        }

        context("Overwhelming Surge") {
            test("mode 0 deals 3 damage to a creature, killing a 3-toughness creature") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Overwhelming Surge")
                    .withLandsOnBattlefield(1, "Mountain", 3)
                    .withCardOnBattlefield(2, "Hill Giant") // 3/3 — dies to 3 damage
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val victim = game.findPermanent("Hill Giant")!!
                val cast = game.castSpellWithMode(1, "Overwhelming Surge", modeIndex = 0, targetId = victim)
                withClue("Cast (damage mode) should succeed: ${cast.error}") { cast.error shouldBe null }
                game.resolveStack()

                withClue("Hill Giant should be dead") {
                    game.isOnBattlefield("Hill Giant") shouldBe false
                }
            }

            test("mode 2 deals 3 damage to a creature and destroys a noncreature artifact") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Overwhelming Surge")
                    .withLandsOnBattlefield(1, "Mountain", 3)
                    .withCardOnBattlefield(2, "Hill Giant")
                    .withCardOnBattlefield(2, "Mind Stone") // Artifact (noncreature artifact)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val creature = game.findPermanent("Hill Giant")!!
                val artifact = game.findPermanent("Mind Stone")!!
                val modeTargets = listOf(
                    ChosenTarget.Permanent(creature),
                    ChosenTarget.Permanent(artifact),
                )
                val cast = game.execute(
                    CastSpell(
                        game.player1Id,
                        game.findCardsInHand(1, "Overwhelming Surge").first(),
                        modeTargets, // flat union of mode targets
                        chosenModes = listOf(2),
                        modeTargetsOrdered = listOf(modeTargets)
                    )
                )
                withClue("Cast (both modes) should succeed: ${cast.error}") { cast.error shouldBe null }
                game.resolveStack()

                withClue("Hill Giant should be dead and Mind Stone destroyed") {
                    game.isOnBattlefield("Hill Giant") shouldBe false
                    game.isOnBattlefield("Mind Stone") shouldBe false
                }
            }
        }

        context("Spectral Denial") {
            test("counters a spell whose tapped-out controller cannot pay {X}") {
                // Player 2 taps out casting Grizzly Bears; Player 1 responds with Spectral Denial
                // for X=2, which Player 2 cannot pay, so the spell is countered.
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Spectral Denial")
                    .withLandsOnBattlefield(1, "Island", 3) // {X}{U} with X=2 → {2}{U}
                    .withCardInHand(2, "Grizzly Bears")
                    .withLandsOnBattlefield(2, "Forest", 2) // taps out
                    .withActivePlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val gbCast = game.castSpell(2, "Grizzly Bears")
                withClue("Grizzly Bears cast should succeed: ${gbCast.error}") { gbCast.error shouldBe null }
                game.passPriority()

                val targetSpell = game.state.stack.first {
                    game.state.getEntity(it)?.get<CardComponent>()?.name == "Grizzly Bears"
                }
                val cast = game.execute(
                    CastSpell(
                        game.player1Id,
                        game.findCardsInHand(1, "Spectral Denial").first(),
                        listOf(ChosenTarget.Spell(targetSpell)),
                        xValue = 2,
                    )
                )
                withClue("Spectral Denial cast should succeed: ${cast.error}") { cast.error shouldBe null }
                game.resolveStack()
                if (game.hasPendingDecision()) {
                    game.skipSelection()
                    game.resolveStack()
                }

                withClue("Grizzly Bears should be countered") {
                    game.isOnBattlefield("Grizzly Bears") shouldBe false
                }
            }
        }

        context("Inevitable Defeat") {
            test("exiles a nonland permanent, the controller loses 3 life and you gain 3 life") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Inevitable Defeat")
                    .withLandsOnBattlefield(1, "Plains", 1)
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withLandsOnBattlefield(1, "Swamp", 2)
                    .withCardOnBattlefield(2, "Hill Giant")
                    .withLifeTotal(1, 20)
                    .withLifeTotal(2, 20)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val victim = game.findPermanent("Hill Giant")!!
                val cast = game.castSpell(1, "Inevitable Defeat", victim)
                withClue("Cast should succeed: ${cast.error}") { cast.error shouldBe null }
                game.resolveStack()

                withClue("Hill Giant should be exiled") {
                    game.isOnBattlefield("Hill Giant") shouldBe false
                    game.state.getExile(game.player2Id).count {
                        game.state.getEntity(it)?.get<CardComponent>()?.name == "Hill Giant"
                    } shouldBe 1
                }
                withClue("Opponent should lose 3 life and caster gain 3 life") {
                    game.getLifeTotal(2) shouldBe 17
                    game.getLifeTotal(1) shouldBe 23
                }
            }
        }
    }
}
