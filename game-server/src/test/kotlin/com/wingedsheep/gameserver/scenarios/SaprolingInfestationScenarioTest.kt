package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Saproling Infestation.
 *
 * Card reference:
 * - Saproling Infestation ({1}{G}): Enchantment
 *   Whenever a player kicks a spell, you create a 1/1 green Saproling creature token.
 *
 * The trigger watches `SpellCastEvent(player = Player.Each, requires = {WasKicked})`, so it fires
 * for *any* player's kicked spell, and the token is always created by the enchantment's controller.
 * Untamed Kavu (kicker {3}) is the kicked spell driving the trigger.
 */
class SaprolingInfestationScenarioTest : ScenarioTestBase() {

    private fun ScenarioTestBase.TestGame.handCardId(playerId: EntityId, name: String): EntityId =
        state.getHand(playerId).first { entityId ->
            state.getEntity(entityId)?.get<CardComponent>()?.name == name
        }

    private fun ScenarioTestBase.TestGame.saprolingCount(): Int =
        findPermanents("Saproling Token").size

    init {
        context("Saproling Infestation") {

            test("controller kicking a spell creates a Saproling for the controller") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Saproling Infestation")
                    .withCardInHand(1, "Untamed Kavu")
                    .withLandsOnBattlefield(1, "Forest", 5) // {1}{G} + {3} kicker
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cardId = game.handCardId(game.player1Id, "Untamed Kavu")
                val castResult = game.execute(CastSpell(game.player1Id, cardId, wasKicked = true))
                withClue("Kicked cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                withClue("One Saproling token should have been created") {
                    game.saprolingCount() shouldBe 1
                }
                val tokenId = game.findPermanents("Saproling Token").single()
                withClue("Token should be controlled by the enchantment's controller") {
                    game.state.getEntity(tokenId)?.get<ControllerComponent>()?.playerId shouldBe game.player1Id
                }
            }

            test("an opponent kicking a spell still creates a Saproling for the controller") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Saproling Infestation")
                    .withCardInHand(2, "Untamed Kavu")
                    .withLandsOnBattlefield(2, "Forest", 5)
                    .withActivePlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cardId = game.handCardId(game.player2Id, "Untamed Kavu")
                val castResult = game.execute(CastSpell(game.player2Id, cardId, wasKicked = true))
                withClue("Opponent's kicked cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                withClue("Opponent kicking still triggers \"a player kicks a spell\"") {
                    game.saprolingCount() shouldBe 1
                }
                val tokenId = game.findPermanents("Saproling Token").single()
                withClue("Token belongs to the enchantment's controller, not the kicker") {
                    game.state.getEntity(tokenId)?.get<ControllerComponent>()?.playerId shouldBe game.player1Id
                }
            }

            test("casting a spell without kicking it creates no Saproling") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Saproling Infestation")
                    .withCardInHand(1, "Untamed Kavu")
                    .withLandsOnBattlefield(1, "Forest", 5)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val castResult = game.castSpell(1, "Untamed Kavu")
                withClue("Unkicked cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                withClue("Unkicked spell must not trigger Saproling Infestation") {
                    game.saprolingCount() shouldBe 0
                }
            }
        }
    }
}
