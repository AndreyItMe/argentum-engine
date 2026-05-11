package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.battlefield.LinkedExileComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.state.components.identity.PlayWithoutPayingCostComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for Evercoat Ursine ({4}{G}, 6/5, Trample, Hideaway 3 x2).
 *
 * Two ETB "Hideaway 3" triggers each look at the top three cards, exile one
 * face-down linked to the bear, and bottom-randomize the rest. Combat damage
 * to a player grants the bear's controller permission to play one of the
 * linked-exile cards without paying its mana cost.
 */
class EvercoatUrsineScenarioTest : ScenarioTestBase() {

    init {
        context("Evercoat Ursine — Hideaway 3 ETB") {

            test("entering looks at top 3 twice, exiling one face-down linked card each time") {
                val game = scenario()
                    .withPlayers("Ursine Player", "Opponent")
                    .withCardInHand(1, "Evercoat Ursine")
                    .withLandsOnBattlefield(1, "Forest", 5)
                    // Hideaway A pool (top 3, in order): pick Glory Seeker, bottom Plains + Mountain.
                    .withCardInLibrary(1, "Glory Seeker")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(1, "Mountain")
                    // Hideaway B pool (next 3): pick Hill Giant, bottom Forest + Island.
                    .withCardInLibrary(1, "Hill Giant")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Island")
                    // Buffer so the engine never sees an empty library during hideaway.
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val librarySizeBefore = game.state.getLibrary(game.player1Id).size

                val castResult = game.castSpell(1, "Evercoat Ursine")
                withClue("Cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                // Spell resolves → Ursine enters → two Hideaway triggers stack.
                // First trigger's resolution pauses for the controller's pick.
                game.resolveStack()

                val firstDecision = game.state.pendingDecision as? SelectCardsDecision
                withClue("First Hideaway 3 should prompt the controller to pick 1 of 3") {
                    firstDecision shouldNotBe null
                    firstDecision!!.options shouldHaveSize 3
                    firstDecision.minSelections shouldBe 1
                    firstDecision.maxSelections shouldBe 1
                }
                val firstPick = firstDecision!!.options.first { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Glory Seeker"
                }
                game.selectCards(listOf(firstPick))
                game.resolveStack()

                val secondDecision = game.state.pendingDecision as? SelectCardsDecision
                withClue("Second Hideaway 3 should prompt again for the second iteration") {
                    secondDecision shouldNotBe null
                    secondDecision!!.options shouldHaveSize 3
                }
                val secondPick = secondDecision!!.options.first { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Hill Giant"
                }
                game.selectCards(listOf(secondPick))
                game.resolveStack()

                val ursineId = game.findPermanent("Evercoat Ursine")!!
                val linked = game.state.getEntity(ursineId)?.get<LinkedExileComponent>()
                withClue("Ursine should track both exiled cards via LinkedExileComponent") {
                    linked shouldNotBe null
                    linked!!.exiledIds shouldHaveSize 2
                }

                val exile = game.state.getExile(game.player1Id)
                withClue("Both picked cards should be in exile") {
                    exile.toSet() shouldBe linked!!.exiledIds.toSet()
                }

                withClue("Exiled cards should be face down") {
                    exile.forEach { id ->
                        game.state.getEntity(id)
                            ?.get<FaceDownComponent>() shouldNotBe null
                    }
                }

                withClue("Library should shrink by exactly 2 (six looked at, four bottom-returned)") {
                    game.state.getLibrary(game.player1Id).size shouldBe librarySizeBefore - 2
                }
            }
        }

        context("Evercoat Ursine — combat damage free-cast trigger") {

            test("dealing combat damage grants free-cast permission on linked-exile cards") {
                val game = scenario()
                    .withPlayers("Ursine Player", "Opponent")
                    .withCardInHand(1, "Evercoat Ursine")
                    .withLandsOnBattlefield(1, "Forest", 5)
                    .withCardInLibrary(1, "Glory Seeker")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Hill Giant")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Evercoat Ursine")
                game.resolveStack()
                val firstDecision = game.state.pendingDecision as SelectCardsDecision
                val glorySeekerPick = firstDecision.options.first { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Glory Seeker"
                }
                game.selectCards(listOf(glorySeekerPick))
                game.resolveStack()
                val secondDecision = game.state.pendingDecision as SelectCardsDecision
                val hillGiantPick = secondDecision.options.first { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Hill Giant"
                }
                game.selectCards(listOf(hillGiantPick))
                game.resolveStack()

                // Advance past P1's turn 1, all of P2's turn, into P1's turn 3 main —
                // summoning sickness gone, Ursine free to attack.
                game.passUntilPhase(Phase.ENDING, Step.END)
                game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP)
                game.passUntilPhase(Phase.ENDING, Step.END)
                game.passUntilPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(mapOf("Evercoat Ursine" to 2))
                game.passUntilPhase(Phase.COMBAT, Step.END_COMBAT)
                game.resolveStack()

                val ursineId = game.findPermanent("Evercoat Ursine")!!
                val linked = game.state.getEntity(ursineId)?.get<LinkedExileComponent>()
                linked shouldNotBe null
                val linkedIds = linked!!.exiledIds

                withClue("Both linked-exile cards should now have a MayPlayPermission for the controller") {
                    linkedIds.forEach { id ->
                        val perm = game.state.mayPlayPermissions.firstOrNull { id in it.cardIds }
                        perm shouldNotBe null
                        perm!!.controllerId shouldBe game.player1Id
                    }
                }
                withClue("Both linked-exile cards should be flagged free-to-cast") {
                    linkedIds.forEach { id ->
                        game.state.getEntity(id)
                            ?.get<PlayWithoutPayingCostComponent>() shouldNotBe null
                    }
                }

                // Roll forward to post-combat main phase so a sorcery-speed creature is castable.
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)

                val gloryInExile = game.state.getExile(game.player1Id).first { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Glory Seeker"
                }
                val castFromExile = game.execute(CastSpell(game.player1Id, gloryInExile))
                withClue("Free cast from linked exile should succeed: ${castFromExile.error}") {
                    castFromExile.error shouldBe null
                }
                game.resolveStack()

                game.isOnBattlefield("Glory Seeker") shouldBe true
            }
        }
    }
}
