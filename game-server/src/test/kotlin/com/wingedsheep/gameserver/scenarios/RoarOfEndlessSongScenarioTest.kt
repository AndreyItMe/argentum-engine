package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario test for Roar of Endless Song (TDM) — Enchantment Saga {2}{G}{U}{R}.
 *
 * I, II — Create a 5/5 green Elephant creature token.
 * III  — Double the power and toughness of each creature you control until end of turn.
 *
 * Exercises the reusable `EffectPatterns.doublePowerAndToughnessForAll` helper in a Saga
 * context: the chapter-III doubling must affect both the Elephant tokens minted by chapters
 * I/II and a pre-existing creature, locking in +X/+Y per the standard doubling ruling.
 */
class RoarOfEndlessSongScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    init {
        context("Roar of Endless Song Saga") {
            test("chapters I & II make 5/5 Elephants; chapter III doubles your creatures' P/T") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Roar of Endless Song")
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withCardOnBattlefield(1, "Grizzly Bears") // 2/2
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Cast Roar — it enters with a lore counter (Rule 714) and Chapter I fires.
                game.castSpell(1, "Roar of Endless Song")
                game.resolveStack()

                val sagaId = game.findPermanent("Roar of Endless Song")!!
                withClue("Saga should have 1 lore counter after entering (Chapter I)") {
                    game.state.getEntity(sagaId)!!.get<CountersComponent>()!!.getCount(CounterType.LORE) shouldBe 1
                }
                withClue("Chapter I should create a 5/5 green Elephant token") {
                    val elephant = game.findPermanent("Elephant")
                    elephant shouldNotBe null
                    val projected = stateProjector.project(game.state)
                    projected.getPower(elephant!!) shouldBe 5
                    projected.getToughness(elephant) shouldBe 5
                }

                // Advance to Player1's next precombat main → lore = 2 → Chapter II.
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
                game.passUntilPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN) // Player2's turn
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
                game.passUntilPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN) // back to Player1
                game.state.activePlayerId shouldBe game.player1Id
                withClue("Lore counter should be 2 at start of Player1's next turn") {
                    game.state.getEntity(sagaId)!!.get<CountersComponent>()!!.getCount(CounterType.LORE) shouldBe 2
                }
                game.resolveStack() // resolve Chapter II (second Elephant)

                // Advance to Player1's next precombat main → lore = 3 → Chapter III.
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
                game.passUntilPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN) // Player2's turn
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
                game.passUntilPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN) // back to Player1
                withClue("Lore counter should be 3, firing Chapter III") {
                    game.state.getEntity(sagaId)!!.get<CountersComponent>()!!.getCount(CounterType.LORE) shouldBe 3
                }
                game.resolveStack() // resolve Chapter III (double P/T)

                val projected = stateProjector.project(game.state)
                val bearsId = game.findPermanent("Grizzly Bears")!!
                withClue("Grizzly Bears (2/2) should be doubled to 4/4 by Chapter III") {
                    projected.getPower(bearsId) shouldBe 4
                    projected.getToughness(bearsId) shouldBe 4
                }
                withClue("A 5/5 Elephant token should be doubled to 10/10 by Chapter III") {
                    val elephant = game.findPermanent("Elephant")!!
                    projected.getPower(elephant) shouldBe 10
                    projected.getToughness(elephant) shouldBe 10
                }
            }
        }
    }
}
