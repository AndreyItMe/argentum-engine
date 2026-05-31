package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario test for Kotis, the Fangkeeper (TDM) — {1}{B}{G}{U} Legendary Zombie Warrior, 2/1.
 *
 * "Indestructible. Whenever Kotis deals combat damage to a player, exile the top X cards of
 * their library, where X is the amount of damage dealt. You may cast any number of spells with
 * mana value X or less from among them without paying their mana costs."
 *
 * Exercises the reusable "exile top X, free-cast the spells with mana value ≤ X" chain
 * (GatherCards(top of triggering player's library) → MoveCollection(exile) →
 * FilterCollection(nonland) → FilterCollection(ManaValueAtMost(damage)) →
 * GrantMayPlayFromExile + GrantPlayWithoutPayingCost), the same shape as Villainous Wealth but
 * driven by a combat-damage trigger. The dynamic mana-value cap comes straight from
 * [com.wingedsheep.sdk.scripting.effects.CollectionFilter.ManaValueAtMost] over the trigger's
 * damage amount — no bespoke effect.
 *
 * Kotis is a 2/1, so X = 2 each combat: the top two cards of the defender's library are exiled,
 * and only the nonland cards with mana value ≤ 2 among them become free-castable by Kotis's
 * controller.
 */
class KotisTheFangkeeperScenarioTest : ScenarioTestBase() {

    init {
        context("Kotis, the Fangkeeper combat damage trigger") {

            test("exiles top X and grants a free cast on the spell with mana value ≤ X") {
                // Defender's top two: Grizzly Bears (MV 2, castable) and Hill Giant (MV 4, too big).
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Kotis, the Fangkeeper", tapped = false, summoningSickness = false)
                    .withCardInLibrary(2, "Grizzly Bears")
                    .withCardInLibrary(2, "Hill Giant")
                    .withLifeTotal(2, 20)
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                game.declareAttackers(mapOf("Kotis, the Fangkeeper" to 2))
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)

                withClue("Kotis (2/1) deals 2 combat damage to the defender") {
                    game.getLifeTotal(2) shouldBe 18
                }
                withClue("X = 2 → the top two cards of the defender's library are exiled") {
                    namesInExile(game, 2) shouldBe setOf("Grizzly Bears", "Hill Giant")
                }

                val freeCastable = freeCastableIds(game)
                withClue("Grizzly Bears (MV 2 ≤ X) may be cast for free") {
                    freeCastable.contains(exileId(game, 2, "Grizzly Bears")) shouldBe true
                }
                withClue("Hill Giant (MV 4 > X) is exiled but NOT free-castable") {
                    freeCastable.contains(exileId(game, 2, "Hill Giant")) shouldBe false
                }

                // Player1 has no mana; a successful cast proves it is free. The card is owned by
                // and sits in Player2's exile, but Player1 casts it via the granted permission.
                val bearsId = exileId(game, 2, "Grizzly Bears")
                val result = game.execute(CastSpell(game.player1Id, bearsId, emptyList()))
                withClue("Casting Grizzly Bears from exile for free must succeed with no mana") {
                    (result.error == null) shouldBe true
                }
                game.resolveStack()

                withClue("The free-cast Grizzly Bears resolves onto the battlefield") {
                    game.isOnBattlefield("Grizzly Bears") shouldBe true
                }
                withClue("Hill Giant stays in exile — uncast cards remain exiled") {
                    namesInExile(game, 2).contains("Hill Giant") shouldBe true
                }
            }

            test("lands among the exiled cards can't be played this way (spells only)") {
                // Defender's top two: Shock (MV 1, castable spell) and Forest (a land).
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Kotis, the Fangkeeper", tapped = false, summoningSickness = false)
                    .withCardInLibrary(2, "Shock")
                    .withCardInLibrary(2, "Forest")
                    .withLifeTotal(2, 20)
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                game.declareAttackers(mapOf("Kotis, the Fangkeeper" to 2))
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)

                withClue("Both top cards are exiled") {
                    namesInExile(game, 2) shouldBe setOf("Shock", "Forest")
                }

                val freeCastable = freeCastableIds(game)
                withClue("Shock (a spell with MV 1 ≤ X) is free-castable") {
                    freeCastable.contains(exileId(game, 2, "Shock")) shouldBe true
                }
                withClue("Forest is a land — 'you may cast spells', so it is excluded") {
                    freeCastable.contains(exileId(game, 2, "Forest")) shouldBe false
                }
            }
        }
    }

    /** All card ids the given game's Player1 may currently cast/play via a may-play permission. */
    private fun freeCastableIds(game: TestGame): Set<EntityId> =
        game.state.mayPlayPermissions
            .filter { it.controllerId == game.player1Id }
            .flatMap { it.cardIds }
            .toSet()

    private fun exileId(game: TestGame, playerNumber: Int, name: String): EntityId {
        val playerId = if (playerNumber == 1) game.player1Id else game.player2Id
        return game.state.getExile(playerId).first { id ->
            game.state.getEntity(id)?.get<CardComponent>()?.name == name
        }
    }

    private fun namesInExile(game: TestGame, playerNumber: Int): Set<String> {
        val playerId = if (playerNumber == 1) game.player1Id else game.player2Id
        return game.state.getExile(playerId).mapNotNull { id ->
            game.state.getEntity(id)?.get<CardComponent>()?.name
        }.toSet()
    }
}
