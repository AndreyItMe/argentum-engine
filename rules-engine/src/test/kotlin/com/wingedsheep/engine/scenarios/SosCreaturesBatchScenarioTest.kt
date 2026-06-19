package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.PreparedComponent
import com.wingedsheep.engine.state.components.player.LifeGainedAmountThisTurnComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for the Secrets of Strixhaven creatures batch:
 *  - Mage Tower Referee     — "whenever you cast a multicolored spell, put a +1/+1 counter on it"
 *  - Scathing Shadelock     — becomes prepared at your first main phase (prepare: Venomous Words)
 *  - Scheming Silvertongue  — becomes prepared at your second main phase IF you gained 2+ life
 *  - Ulna Alley Shopkeep    — Infusion: +2/+0 as long as you gained life this turn
 *
 * (Tester of the Tangential's move-X-counters reflexive is covered separately in
 * TesterOfTheTangentialScenarioTest.)
 */
class SosCreaturesBatchScenarioTest : ScenarioTestBase() {

    private fun TestGame.plusCounters(name: String): Int {
        val id = findPermanent(name) ?: return 0
        return state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0
    }

    init {
        context("Mage Tower Referee — multicolored cast trigger") {
            test("gets a +1/+1 counter when you cast a multicolored spell") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Mage Tower Referee", summoningSickness = false)
                    .withCardInHand(1, "Llanowar Knight") // {G}{W} multicolored creature
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withLandsOnBattlefield(1, "Plains", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                withClue("starts with no +1/+1 counters") { game.plusCounters("Mage Tower Referee") shouldBe 0 }

                game.castSpell(1, "Llanowar Knight").error shouldBe null
                game.resolveStack()

                withClue("casting a multicolored spell adds one +1/+1 counter") {
                    game.plusCounters("Mage Tower Referee") shouldBe 1
                }
            }

            test("does NOT trigger on a monocolored spell") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Mage Tower Referee", summoningSickness = false)
                    .withCardInHand(1, "Grizzly Bears") // {1}{G} monocolored
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Grizzly Bears").error shouldBe null
                game.resolveStack()

                withClue("a monocolored spell does not add a counter") {
                    game.plusCounters("Mage Tower Referee") shouldBe 0
                }
            }
        }

        context("Scathing Shadelock — becomes prepared at first main phase") {
            test("becomes prepared at the beginning of your first main phase") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Scathing Shadelock", summoningSickness = false)
                    .withLandsOnBattlefield(1, "Swamp", 3)
                    .withActivePlayer(1)
                    .inPhase(Phase.BEGINNING, Step.UPKEEP)
                    .build()

                val shadelock = game.findPermanent("Scathing Shadelock")!!
                withClue("not prepared during upkeep") {
                    game.state.getEntity(shadelock)?.get<PreparedComponent>() shouldBe null
                }

                game.passUntilPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                game.resolveStack()

                withClue("the first-main-phase trigger makes it prepared") {
                    game.state.getEntity(shadelock)?.get<PreparedComponent>() shouldNotBe null
                }
            }
        }

        context("Scheming Silvertongue — prepared at second main phase if you gained 2+ life") {
            test("becomes prepared when you gained 2 or more life this turn") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Scheming Silvertongue", summoningSickness = false)
                    .withLandsOnBattlefield(1, "Swamp", 3)
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.END_COMBAT)
                    .build()

                game.state = game.state.updateEntity(game.player1Id) {
                    it.withComponent(LifeGainedAmountThisTurnComponent(2))
                }

                val silvertongue = game.findPermanent("Scheming Silvertongue")!!
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
                game.resolveStack()

                withClue("gained 2+ life → second-main trigger makes it prepared") {
                    game.state.getEntity(silvertongue)?.get<PreparedComponent>() shouldNotBe null
                }
            }

            test("does NOT become prepared when you gained only 1 life this turn") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Scheming Silvertongue", summoningSickness = false)
                    .withLandsOnBattlefield(1, "Swamp", 3)
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.END_COMBAT)
                    .build()

                game.state = game.state.updateEntity(game.player1Id) {
                    it.withComponent(LifeGainedAmountThisTurnComponent(1))
                }

                val silvertongue = game.findPermanent("Scheming Silvertongue")!!
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
                game.resolveStack()

                withClue("only 1 life gained → intervening-if fails, stays unprepared") {
                    game.state.getEntity(silvertongue)?.get<PreparedComponent>() shouldBe null
                }
            }
        }

        context("Ulna Alley Shopkeep — Infusion conditional buff") {
            test("gets +2/+0 only while you gained life this turn") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Ulna Alley Shopkeep", summoningSickness = false)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val shopkeep = game.findPermanent("Ulna Alley Shopkeep")!!
                withClue("base 2/3 with no life gained this turn") {
                    game.state.projectedState.getPower(shopkeep) shouldBe 2
                    game.state.projectedState.getToughness(shopkeep) shouldBe 3
                }

                game.state = game.state.updateEntity(game.player1Id) {
                    it.withComponent(LifeGainedAmountThisTurnComponent(1))
                }

                withClue("after gaining life this turn, Infusion grants +2/+0 → 4/3") {
                    game.state.projectedState.getPower(shopkeep) shouldBe 4
                    game.state.projectedState.getToughness(shopkeep) shouldBe 3
                }
            }
        }
    }
}
