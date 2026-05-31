package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for the "group A" Tarkir: Dragonstorm batch:
 * Coordinated Maneuver, Frontline Rush, Salt Road Skirmish, Kin-Tree Severance, Duty Beyond Death.
 *
 * Every card composes existing primitives (modal choose-one, dynamic damage/pump scaled by
 * creatures you control, destroy/exile targeting, temporary haste tokens that sacrifice at the
 * next end step, additional sacrifice cost + group indestructible/counter buff).
 */
class TdmGroupABatchScenarioTest : ScenarioTestBase() {

    init {
        context("Coordinated Maneuver") {

            test("damage mode deals damage equal to the number of creatures you control") {
                val game = scenario()
                    .withPlayers()
                    .withCardInHand(1, "Coordinated Maneuver")
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withCardOnBattlefield(1, "Glory Seeker") // 2/2
                    .withCardOnBattlefield(1, "Glory Seeker") // 2/2 — two creatures you control
                    .withCardOnBattlefield(2, "Glory Seeker") // 2/2 enemy target (will take 2)
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val enemy = game.findPermanents("Glory Seeker")
                    .first { game.state.getEntity(it)?.get<ControllerComponent>()?.playerId == game.player2Id }

                // Mode 0 = damage to creature/planeswalker. 2 creatures controlled → 2 damage → kills the 2/2.
                game.castSpellWithMode(1, "Coordinated Maneuver", modeIndex = 0, targetId = enemy)
                game.resolveStack()

                withClue("Enemy 2/2 should be dead after taking 2 damage (2 creatures you control)") {
                    game.findPermanents("Glory Seeker")
                        .none { game.state.getEntity(it)?.get<ControllerComponent>()?.playerId == game.player2Id } shouldBe true
                }
            }

            test("destroy-enchantment mode destroys target enchantment") {
                val game = scenario()
                    .withPlayers()
                    .withCardInHand(1, "Coordinated Maneuver")
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withCardOnBattlefield(2, "Grand Melee") // enchantment
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val melee = game.findPermanent("Grand Melee")!!
                game.castSpellWithMode(1, "Coordinated Maneuver", modeIndex = 1, targetId = melee)
                game.resolveStack()

                withClue("Grand Melee should be destroyed") {
                    game.findPermanent("Grand Melee") shouldBe null
                }
            }
        }

        context("Frontline Rush") {

            test("token mode creates two 1/1 red Goblins") {
                val game = scenario()
                    .withPlayers()
                    .withCardInHand(1, "Frontline Rush")
                    .withLandsOnBattlefield(1, "Plains", 1)
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpellWithMode(1, "Frontline Rush", modeIndex = 0)
                game.resolveStack()

                withClue("Two Goblin tokens should exist") {
                    game.findPermanents("Goblin Token").size shouldBe 2
                }
            }

            test("pump mode gives +X/+X where X is the number of creatures you control") {
                val game = scenario()
                    .withPlayers()
                    .withCardInHand(1, "Frontline Rush")
                    .withLandsOnBattlefield(1, "Plains", 1)
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withCardOnBattlefield(1, "Glory Seeker") // 2/2
                    .withCardOnBattlefield(1, "Glory Seeker") // 2/2 — X = 2
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val target = game.findPermanents("Glory Seeker").first()
                game.castSpellWithMode(1, "Frontline Rush", modeIndex = 1, targetId = target)
                game.resolveStack()

                val clientState = game.getClientState(1)
                val pumped = clientState.cards[target]
                withClue("Targeted Glory Seeker should be 4/4 (2/2 + 2 creatures you control)") {
                    pumped shouldNotBe null
                    pumped!!.power shouldBe 4
                    pumped.toughness shouldBe 4
                }
            }
        }

        context("Salt Road Skirmish") {

            test("destroys target creature and creates two hasty Warriors") {
                val game = scenario()
                    .withPlayers()
                    .withCardInHand(1, "Salt Road Skirmish")
                    .withLandsOnBattlefield(1, "Swamp", 4)
                    .withCardOnBattlefield(2, "Glory Seeker") // target to destroy
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val victim = game.findPermanent("Glory Seeker")!!
                game.castSpell(1, "Salt Road Skirmish", targetId = victim)
                game.resolveStack()

                withClue("Target creature should be destroyed") {
                    game.findPermanent("Glory Seeker") shouldBe null
                }
                val warriors = game.findPermanents("Warrior Token")
                withClue("Two Warrior tokens should be created with haste") {
                    warriors.size shouldBe 2
                    warriors.all {
                        game.getClientState(1).cards[it]?.keywords?.contains(Keyword.HASTE) == true
                    } shouldBe true
                }

                // They are sacrificed at the beginning of the next end step.
                game.passUntilPhase(Phase.ENDING, Step.END)
                game.resolveStack()
                withClue("Warriors should be sacrificed at the next end step") {
                    game.findPermanents("Warrior Token").size shouldBe 0
                }
            }
        }

        context("Kin-Tree Severance") {

            test("exiles target permanent with mana value 3 or greater") {
                val game = scenario()
                    .withPlayers()
                    .withCardInHand(1, "Kin-Tree Severance")
                    .withLandsOnBattlefield(1, "Plains", 6) // pay {2/W}{2/B}{2/G} as all-generic
                    .withCardOnBattlefield(2, "Marshal of the Lost") // MV 4 creature
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val target = game.findPermanent("Marshal of the Lost")!!
                game.castSpell(1, "Kin-Tree Severance", targetId = target)
                game.resolveStack()

                withClue("Target should be exiled") {
                    game.findPermanent("Marshal of the Lost") shouldBe null
                    game.state.getExile(game.player2Id).contains(target) shouldBe true
                }
            }
        }

        context("Duty Beyond Death") {

            test("sacrifice cost, grants indestructible and a +1/+1 counter to each creature you control") {
                val game = scenario()
                    .withPlayers()
                    .withCardInHand(1, "Duty Beyond Death")
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withCardOnBattlefield(1, "Glory Seeker") // sacrifice fodder
                    .withCardOnBattlefield(1, "Marshal of the Lost") // survivor
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpellWithAdditionalSacrifice(1, "Duty Beyond Death", sacrificeCreatureName = "Glory Seeker")
                game.resolveStack()

                val marshal = game.findPermanent("Marshal of the Lost")!!
                val counters = game.state.getEntity(marshal)?.get<CountersComponent>()
                withClue("Surviving creature should have a +1/+1 counter") {
                    (counters?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 1
                }
                withClue("Surviving creature should have indestructible until end of turn") {
                    game.getClientState(1).cards[marshal]?.keywords?.contains(Keyword.INDESTRUCTIBLE) shouldBe true
                }
                withClue("Glory Seeker was sacrificed as a cost") {
                    game.findPermanent("Glory Seeker") shouldBe null
                }
            }
        }
    }
}
