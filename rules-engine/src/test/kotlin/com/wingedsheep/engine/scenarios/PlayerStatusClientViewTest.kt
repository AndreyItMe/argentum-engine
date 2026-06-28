package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * The client view exposes two per-player status numbers the player otherwise has to track in their
 * head: the Delirium count (distinct card types in the graveyard, active at 4+) and the effective
 * maximum hand size (CR 402.2). Both ride on [com.wingedsheep.engine.view.ClientPlayer] and the
 * client renders them as the graveyard delirium tracker and the hand-limit badge respectively.
 *
 * The max-hand-size value comes from the shared [com.wingedsheep.engine.core.MaximumHandSize] source
 * of truth, so what the badge shows is exactly what cleanup will enforce (see CursedRackScenarioTest
 * for the cleanup-side discard behavior).
 */
class PlayerStatusClientViewTest : ScenarioTestBase() {

    private val transformer = com.wingedsheep.engine.view.ClientStateTransformer(cardRegistry)
    private val p1 = EntityId.of("player-1")

    private fun viewSelf(state: com.wingedsheep.engine.state.GameState) =
        transformer.transform(state, p1).players.first { it.playerId == p1 }

    init {
        test("graveyardCardTypes counts distinct card types, not raw cards") {
            // Two creatures + one instant = two distinct types (Creature, Instant), not three cards.
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInGraveyard(1, "Grizzly Bears")
                .withCardInGraveyard(1, "Grizzly Bears")
                .withCardInGraveyard(1, "Lightning Bolt")
                .build()

            val player = viewSelf(game.state)
            player.graveyardSize shouldBe 3
            player.graveyardCardTypes shouldBe 2
        }

        test("delirium count reaches four with four distinct card types") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInGraveyard(1, "Grizzly Bears")   // Creature
                .withCardInGraveyard(1, "Lightning Bolt")  // Instant
                .withCardInGraveyard(1, "Pacifism")        // Enchantment
                .withCardInGraveyard(1, "Divination")      // Sorcery
                .build()

            viewSelf(game.state).graveyardCardTypes shouldBe 4
        }

        test("an empty graveyard reports zero card types") {
            val game = scenario().withPlayers("Player1", "Player2").build()
            viewSelf(game.state).graveyardCardTypes shouldBe 0
        }

        test("maximum hand size is the default seven with no effect in play") {
            val game = scenario().withPlayers("Player1", "Player2").build()
            viewSelf(game.state).maxHandSize shouldBe 7
        }

        test("Reliquary Tower reports no maximum hand size (null = unlimited)") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Reliquary Tower")
                .build()

            withClue("Reliquary Tower grants 'You have no maximum hand size'") {
                viewSelf(game.state).maxHandSize.shouldBeNull()
            }
        }

        test("an opponent's Reliquary Tower does not lift the viewing player's maximum") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(2, "Reliquary Tower")
                .build()

            viewSelf(game.state).maxHandSize shouldBe 7
        }
    }
}
