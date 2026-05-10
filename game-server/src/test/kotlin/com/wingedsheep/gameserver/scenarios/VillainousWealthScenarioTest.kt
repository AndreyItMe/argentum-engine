package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.PlayWithoutPayingCostComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario test for Villainous Wealth.
 *
 * Card reference:
 * - Villainous Wealth ({X}{B}{G}{U}): Sorcery
 *   "Target opponent exiles the top X cards of their library. You may cast any number
 *    of spells with mana value X or less from among them without paying their mana costs."
 */
class VillainousWealthScenarioTest : ScenarioTestBase() {

    private fun getExile(game: TestGame, playerNumber: Int): List<EntityId> {
        val playerId = if (playerNumber == 1) game.player1Id else game.player2Id
        return game.state.getExile(playerId)
    }

    private fun TestGame.castVillainousWealth(xValue: Int): com.wingedsheep.engine.core.ExecutionResult {
        val hand = state.getHand(player1Id)
        val cardId = hand.find { entityId ->
            state.getEntity(entityId)?.get<CardComponent>()?.name == "Villainous Wealth"
        } ?: error("Villainous Wealth not found in player 1's hand")
        val targets = listOf(ChosenTarget.Player(player2Id))
        return execute(CastSpell(player1Id, cardId, targets, xValue))
    }

    init {
        context("Villainous Wealth basic effect") {
            test("exiles X cards from opponent's library and grants free cast for nonland cards with MV <= X") {
                // X=2, so total cost = {2}{B}{G}{U} = 5 mana
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Villainous Wealth")
                    .withLandsOnBattlefield(1, "Swamp", 3)   // 3 black (2 for generic + 1 for {B})
                    .withLandsOnBattlefield(1, "Forest", 1)   // 1 green for {G}
                    .withLandsOnBattlefield(1, "Island", 1)   // 1 blue for {U}
                    // Opponent's library: top 2 will be exiled
                    .withCardInLibrary(2, "Glory Seeker")    // MV 2 creature - should get permissions
                    .withCardInLibrary(2, "Hill Giant")      // MV 4 creature - should NOT get permissions (MV > X=2)
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val castResult = game.castVillainousWealth(xValue = 2)
                withClue("Cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }

                game.resolveStack()

                // Should have exiled 2 cards from opponent's library
                val exile = getExile(game, 2)
                withClue("Opponent should have 2 cards in exile") {
                    exile shouldHaveSize 2
                }

                val exiledGlorySeeker = exile.firstOrNull { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Glory Seeker"
                }
                val exiledHillGiant = exile.firstOrNull { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Hill Giant"
                }

                withClue("Glory Seeker (MV 2) should have a MayPlayPermission") {
                    exiledGlorySeeker shouldNotBe null
                    game.state.mayPlayPermissions.any { exiledGlorySeeker!! in it.cardIds } shouldBe true
                }
                withClue("Glory Seeker should have PlayWithoutPayingCostComponent") {
                    game.state.getEntity(exiledGlorySeeker!!)?.get<PlayWithoutPayingCostComponent>() shouldNotBe null
                }

                withClue("Hill Giant (MV 4) should NOT have a MayPlayPermission") {
                    exiledHillGiant shouldNotBe null
                    game.state.mayPlayPermissions.any { exiledHillGiant!! in it.cardIds } shouldBe false
                }
            }

            test("can cast exiled spells without paying mana costs") {
                // X=2, total cost = 5 mana
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Villainous Wealth")
                    .withLandsOnBattlefield(1, "Swamp", 3)
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withLandsOnBattlefield(1, "Island", 1)
                    // X=2: exile top 2, can cast MV ≤ 2
                    .withCardInLibrary(2, "Glory Seeker")    // MV 2 - castable
                    .withCardInLibrary(2, "Glory Seeker")    // second card
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castVillainousWealth(xValue = 2)
                game.resolveStack()

                // Find an exiled Glory Seeker with permissions
                val exile = getExile(game, 2)
                val castableCard = exile.first { entityId ->
                    game.state.mayPlayPermissions.any { entityId in it.cardIds }
                }

                // Cast it for free (all lands are tapped from casting Villainous Wealth)
                val freeCastResult = game.execute(CastSpell(game.player1Id, castableCard))
                withClue("Free cast from exile should succeed: ${freeCastResult.error}") {
                    freeCastResult.error shouldBe null
                }

                game.resolveStack()

                // The creature should be on the battlefield
                val creatureOnBf = game.state.getBattlefield().any { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Glory Seeker"
                }
                withClue("Glory Seeker should be on the battlefield") {
                    creatureOnBf shouldBe true
                }
            }

            test("lands among exiled cards do not get cast permissions") {
                // X=2, total cost = 5 mana
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Villainous Wealth")
                    .withLandsOnBattlefield(1, "Swamp", 3)
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withLandsOnBattlefield(1, "Island", 1)
                    // X=2: exile top 2 cards
                    .withCardInLibrary(2, "Forest")          // land - should NOT get permissions
                    .withCardInLibrary(2, "Glory Seeker")    // creature - should get permissions
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castVillainousWealth(xValue = 2)
                game.resolveStack()

                val exile = getExile(game, 2)
                exile shouldHaveSize 2

                val exiledLand = exile.firstOrNull { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.typeLine?.isLand == true
                }

                withClue("Exiled land should NOT have a MayPlayPermission") {
                    exiledLand shouldNotBe null
                    game.state.mayPlayPermissions.any { exiledLand!! in it.cardIds } shouldBe false
                }
            }
        }
    }
}
