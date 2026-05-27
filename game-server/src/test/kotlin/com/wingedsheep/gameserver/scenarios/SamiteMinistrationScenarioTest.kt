package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Scenario tests for Samite Ministration.
 *
 * "Prevent all damage that would be dealt to you this turn by a source of your choice.
 *  Whenever damage from a black or red source is prevented this way this turn, you gain that much life."
 */
class SamiteMinistrationScenarioTest : ScenarioTestBase() {

    private fun ScenarioTestBase.TestGame.chooseSource(sourceName: String) {
        val decision = getPendingDecision()
        decision.shouldNotBeNull()
        decision.shouldBeInstanceOf<SelectCardsDecision>()
        val entityId = decision.options.first { id ->
            state.getEntity(id)?.get<CardComponent>()?.name == sourceName
        }
        submitDecision(CardsSelectedResponse(decision.id, listOf(entityId)))
    }

    init {
        context("Samite Ministration") {

            test("prevents all combat damage from a black source and gains that much life") {
                val game = scenario()
                    .withPlayers("White Mage", "Necromancer")
                    .withCardInHand(1, "Samite Ministration")
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withCardOnBattlefield(2, "Bog Raiders") // 2/2 black Zombie
                    .withActivePlayer(2)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                game.declareAttackers(mapOf("Bog Raiders" to 1))

                // P2 passes, P1 casts Samite Ministration (no target — protects the caster)
                game.passPriority()
                game.castSpell(1, "Samite Ministration")

                // Resolve Samite Ministration
                game.passPriority()  // P1 passes
                game.passPriority()  // P2 passes → resolves

                // Choose Bog Raiders as the protected-from source
                game.chooseSource("Bog Raiders")

                // No life gain yet — damage hasn't been dealt
                game.getLifeTotal(1) shouldBe 20

                // Advance through combat damage
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
                game.declareNoBlockers()
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)

                // All 2 damage prevented; black source → gain 2 life. Net: 20 - 0 + 2 = 22.
                game.getLifeTotal(1) shouldBe 22
            }

            test("prevents all combat damage from a green source but gains no life") {
                val game = scenario()
                    .withPlayers("White Mage", "Beastmaster")
                    .withCardInHand(1, "Samite Ministration")
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withCardOnBattlefield(2, "Alpine Grizzly") // 4/2 green
                    .withActivePlayer(2)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                game.declareAttackers(mapOf("Alpine Grizzly" to 1))

                game.passPriority()
                game.castSpell(1, "Samite Ministration")

                game.passPriority()
                game.passPriority()

                game.chooseSource("Alpine Grizzly")

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
                game.declareNoBlockers()
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)

                // All 4 damage prevented; green source → no life gain. Stays at 20.
                game.getLifeTotal(1) shouldBe 20
            }

            test("prevents a red burn spell aimed at you and gains that much life") {
                val game = scenario()
                    .withPlayers("White Mage", "Pyromancer")
                    .withCardInHand(1, "Samite Ministration")
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withCardInHand(2, "Shock")
                    .withLandsOnBattlefield(2, "Mountain", 1)
                    .withActivePlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // P2 casts Shock at P1
                game.castSpellTargetingPlayer(2, "Shock", 1)

                // P2 passes, P1 responds with Samite Ministration
                game.passPriority()
                game.castSpell(1, "Samite Ministration")

                // Resolve Samite Ministration (LIFO)
                game.passPriority()  // P1 passes
                game.passPriority()  // P2 passes → Samite resolves

                // Choose Shock (on the stack) as the source
                game.chooseSource("Shock")

                // No damage dealt yet
                game.getLifeTotal(1) shouldBe 20

                // Resolve Shock
                game.passPriority()  // P1 passes
                game.passPriority()  // P2 passes → Shock resolves

                // 2 damage prevented; red source → gain 2 life. Net stays at 20 + 2 = 22.
                game.getLifeTotal(1) shouldBe 22
            }
        }
    }
}
