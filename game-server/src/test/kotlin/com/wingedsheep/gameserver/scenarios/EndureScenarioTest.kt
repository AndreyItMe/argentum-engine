package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Scenario tests for the **Endure N** keyword action (Tarkir: Dragonstorm).
 *
 * "Endure N" — the enduring permanent's controller chooses one: put N +1/+1
 * counters on it, or create an N/N white Spirit creature token. Implemented as
 * `Effects.Endure(amount, target)` composing a [com.wingedsheep.sdk.scripting.effects.ModalEffect].
 *
 * These cover every branch of the mechanic:
 * - counter mode + token mode (the binary choice),
 * - fixed N (ETB endure) and dynamic N (Warden's "endure X = counters on me"),
 * - self target ("it endures") and triggering-entity target (Warden endures the
 *   creature that entered),
 * - a non-ETB trigger source (Anafenza's "another creature dies").
 */
class EndureScenarioTest : ScenarioTestBase() {

    /** Resolve a pending Endure modal decision, picking the counter or token mode. */
    private fun ScenarioTestBase.TestGame.resolveEndure(chooseToken: Boolean) {
        val decision = getPendingDecision()
        decision.shouldNotBeNull()
        decision.shouldBeInstanceOf<ChooseOptionDecision>()
        val keyword = if (chooseToken) "token" else "counter"
        val index = decision.options.indexOfFirst { it.contains(keyword, ignoreCase = true) }
        withClue("Endure should offer a '$keyword' mode among ${decision.options}") {
            (index >= 0) shouldBe true
        }
        submitDecision(OptionChosenResponse(decision.id, index))
        resolveStack()
    }

    private fun ScenarioTestBase.TestGame.plusOneCounters(name: String): Int =
        state.getEntity(findPermanent(name)!!)?.get<CountersComponent>()
            ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    init {
        context("Endure as an enters-the-battlefield trigger") {

            test("counter mode puts N +1/+1 counters on the enduring creature") {
                // Fortress Kin-Guard ({1}{W}, 1/2): "When this creature enters, it endures 1."
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Fortress Kin-Guard")
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Fortress Kin-Guard").error shouldBe null
                game.resolveStack()
                game.resolveEndure(chooseToken = false)

                withClue("Fortress Kin-Guard should have endured into a 2/3 (one +1/+1 counter)") {
                    game.plusOneCounters("Fortress Kin-Guard") shouldBe 1
                }
                withClue("Choosing counters should not create a Spirit token") {
                    game.findPermanent("Spirit Token") shouldBe null
                }
            }

            test("token mode creates a single N/N white Spirit token") {
                // Dusyut Earthcarver ({5}{G}, 4/4): "When this creature enters, it endures 3."
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Dusyut Earthcarver")
                    .withLandsOnBattlefield(1, "Forest", 6)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Dusyut Earthcarver").error shouldBe null
                game.resolveStack()
                game.resolveEndure(chooseToken = true)

                val tokenId = game.findPermanent("Spirit Token")
                withClue("Choosing the token mode should create a Spirit token") {
                    tokenId.shouldNotBeNull()
                }
                val token = game.state.getEntity(tokenId!!)!!.get<CardComponent>()!!
                withClue("The Spirit token should be a 3/3 (N = 3)") {
                    token.baseStats?.basePower shouldBe 3
                    token.baseStats?.baseToughness shouldBe 3
                }
                withClue("The Spirit token should be white") {
                    token.colors shouldBe setOf(Color.WHITE)
                }
                withClue("Choosing the token should not put counters on Dusyut Earthcarver") {
                    game.plusOneCounters("Dusyut Earthcarver") shouldBe 0
                }
            }
        }

        context("Endure with a dynamic amount on the triggering creature") {

            test("Warden of the Grove endures the entering creature for X = its own counters") {
                // Warden ({2}{G}, 2/2): "Whenever another nontoken creature you control enters,
                // it endures X, where X is the number of counters on this creature."
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Warden of the Grove")
                    .withCardInHand(1, "Glory Seeker")
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Give Warden two +1/+1 counters so X resolves to 2.
                val warden = game.findPermanent("Warden of the Grove")!!
                game.state = game.state.updateEntity(warden) {
                    it.with(CountersComponent().withAdded(CounterType.PLUS_ONE_PLUS_ONE, 2))
                }

                game.castSpell(1, "Glory Seeker").error shouldBe null
                game.resolveStack()
                game.resolveEndure(chooseToken = false)

                withClue("The entering Glory Seeker should endure 2 (Warden's counter count)") {
                    game.plusOneCounters("Glory Seeker") shouldBe 2
                }
                withClue("Warden itself should be untouched by enduring the other creature") {
                    game.plusOneCounters("Warden of the Grove") shouldBe 2
                }
            }
        }

        context("Endure from a death trigger") {

            test("Anafenza endures when another nontoken creature you control dies") {
                // Anafenza, Unyielding Lineage ({2}{W}, 2/2): "Whenever another nontoken
                // creature you control dies, Anafenza endures 2."
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Anafenza, Unyielding Lineage")
                    .withCardOnBattlefield(1, "Glory Seeker") // 2/2 — Shock kills it
                    .withCardInHand(1, "Shock")
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val glorySeeker = game.findPermanent("Glory Seeker")!!
                game.castSpell(1, "Shock", glorySeeker).error shouldBe null
                game.resolveStack()

                withClue("Glory Seeker should have died to Shock") {
                    game.isOnBattlefield("Glory Seeker") shouldBe false
                }

                game.resolveEndure(chooseToken = false)

                withClue("Anafenza should have endured 2 (two +1/+1 counters)") {
                    game.plusOneCounters("Anafenza, Unyielding Lineage") shouldBe 2
                }
            }
        }
    }
}
