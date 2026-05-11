package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.PlayWithoutPayingCostComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario test for Etali, Primal Storm.
 *
 * Whenever Etali attacks, exile the top card of each player's library, then
 * you may cast any number of spells from among those cards without paying
 * their mana costs.
 */
class EtaliPrimalStormScenarioTest : ScenarioTestBase() {

    init {
        context("Etali, Primal Storm - attack trigger") {

            test("exiles top card of each player's library and grants cast permission to non-lands") {
                val game = scenario()
                    .withPlayers("Etali Player", "Opponent")
                    // Top of player 1's library: Feed the Clan (instant)
                    .withCardInLibrary(1, "Feed the Clan")
                    .withCardInLibrary(1, "Mountain")
                    // Top of player 2's library: a land (Forest) — should be exiled but not castable
                    .withCardInLibrary(2, "Forest")
                    .withCardInLibrary(2, "Mountain")
                    .withCardOnBattlefield(1, "Etali, Primal Storm")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(mapOf("Etali, Primal Storm" to 2))

                game.resolveStack()

                val p1Exile = game.state.getExile(game.player1Id)
                val p2Exile = game.state.getExile(game.player2Id)

                withClue("Player 1's exile should contain Feed the Clan") {
                    p1Exile shouldHaveSize 1
                    val name = game.state.getEntity(p1Exile.first())?.get<CardComponent>()?.name
                    name shouldBe "Feed the Clan"
                }
                withClue("Player 2's exile should contain Forest (routed to its owner's exile)") {
                    p2Exile shouldHaveSize 1
                    val name = game.state.getEntity(p2Exile.first())?.get<CardComponent>()?.name
                    name shouldBe "Forest"
                }

                val exiledInstant = p1Exile.first()
                val exiledLand = p2Exile.first()

                withClue("Non-land card should have a MayPlayPermission for Etali's controller") {
                    val perm = game.state.mayPlayPermissions.firstOrNull { exiledInstant in it.cardIds }
                    perm shouldNotBe null
                    perm!!.controllerId shouldBe game.player1Id
                }
                withClue("Non-land card should have PlayWithoutPayingCostComponent") {
                    game.state.getEntity(exiledInstant)?.get<PlayWithoutPayingCostComponent>() shouldNotBe null
                }
                withClue("Land card should NOT have any MayPlayPermission") {
                    game.state.mayPlayPermissions.any { exiledLand in it.cardIds } shouldBe false
                }
            }

            test("Etali's controller may cast a spell exiled from the opponent's library without paying its mana cost") {
                val game = scenario()
                    .withPlayers("Etali Player", "Opponent")
                    .withCardInLibrary(1, "Mountain")
                    // Top of opponent's library: an instant Etali's controller can cast for free
                    .withCardInLibrary(2, "Feed the Clan")
                    .withCardInLibrary(2, "Mountain")
                    .withCardOnBattlefield(1, "Etali, Primal Storm")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(mapOf("Etali, Primal Storm" to 2))
                game.resolveStack()

                val opponentExile = game.state.getExile(game.player2Id)
                opponentExile shouldHaveSize 1
                val feedId = opponentExile.first()
                game.state.getEntity(feedId)?.get<CardComponent>()?.name shouldBe "Feed the Clan"

                val castResult = game.execute(CastSpell(game.player1Id, feedId))
                withClue("Cast from opponent's exile should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
            }
        }
    }
}
