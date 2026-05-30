package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Harrow.
 *
 * Card reference:
 * - Harrow ({2}{G}): Instant
 *   As an additional cost to cast this spell, sacrifice a land.
 *   Search your library for up to two basic land cards, put them onto the battlefield, then shuffle.
 */
class HarrowScenarioTest : ScenarioTestBase() {

    init {
        context("Harrow") {

            test("sacrifices a land and puts two basic lands onto the battlefield") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Harrow")
                    .withLandsOnBattlefield(1, "Forest", 4) // {2}{G} + one to sacrifice
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(1, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val initialForests = game.findPermanents("Forest").size

                val castResult = game.castSpellWithAdditionalSacrifice(1, "Harrow", "Forest")
                withClue("Cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                // Pick two basic lands from the library.
                val plains = game.state.getLibrary(game.player1Id).filter { id ->
                    val n = game.state.getEntity(id)?.get<CardComponent>()?.name
                    n == "Plains" || n == "Island"
                }
                game.selectCards(plains)
                game.resolveStack()

                withClue("One Forest was sacrificed (4 -> 3 remaining)") {
                    game.findPermanents("Forest").size shouldBe initialForests - 1
                }
                withClue("Plains entered the battlefield") {
                    game.isOnBattlefield("Plains") shouldBe true
                }
                withClue("Island entered the battlefield") {
                    game.isOnBattlefield("Island") shouldBe true
                }
                withClue("Harrow is in the graveyard after resolving") {
                    game.isInGraveyard(1, "Harrow") shouldBe true
                }
            }
        }
    }
}
