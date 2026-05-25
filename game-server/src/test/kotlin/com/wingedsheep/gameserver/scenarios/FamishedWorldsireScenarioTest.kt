package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Scenario tests for Famished Worldsire (Edge of Eternities).
 *
 * Card reference:
 * - Famished Worldsire ({5}{G}{G}{G}): Creature — Leviathan 0/0
 *   - Ward {3}
 *   - Devour land 3 (As this creature enters, you may sacrifice any number of
 *     lands. It enters with three times that many +1/+1 counters on it.)
 *   - When this creature enters, look at the top X cards of your library, where
 *     X is this creature's power. Put any number of land cards from among them
 *     onto the battlefield tapped, then shuffle.
 */
class FamishedWorldsireScenarioTest : ScenarioTestBase() {

    init {
        context("Famished Worldsire — Devour land + ETB land tutor") {

            test("sacrifice 2 lands → enters with 6 +1/+1 counters; ETB looks at 6 cards and puts 3 Forests tapped") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Famished Worldsire")
                    // 8 Forests to pay {5}{G}{G}{G} + 2 extra to sacrifice for Devour
                    .withLandsOnBattlefield(1, "Forest", 10)
                    // Stack lands at the top of the library so the ETB peek sees lands
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Mountain")
                    // Padding so the library isn't empty after the peek
                    .withCardInLibrary(2, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val initialLibrarySize = game.librarySize(1)

                val castResult = game.castSpell(1, "Famished Worldsire")
                withClue("Famished Worldsire should cast: ${castResult.error}") {
                    castResult.error shouldBe null
                }

                // Resolve the spell — should pause for Devour land sacrifice choice.
                game.resolveStack()

                withClue("Should pause for Devour land sacrifice decision") {
                    game.hasPendingDecision() shouldBe true
                }

                val devourDecision = game.getPendingDecision()
                devourDecision.shouldNotBeNull()
                devourDecision.shouldBeInstanceOf<SelectCardsDecision>()
                val sacrificeOptions = devourDecision.options
                withClue("Devour should offer all 10 Forests as sacrifice options") {
                    sacrificeOptions.size shouldBe 10
                }

                // Sacrifice 2 lands.
                val landsToSacrifice = sacrificeOptions.take(2)
                game.submitDecision(CardsSelectedResponse(devourDecision.id, landsToSacrifice))

                // Drain any queued triggers (the ETB trigger fires on entry).
                game.resolveStack()

                // Famished Worldsire should now be on the battlefield with 6 +1/+1 counters.
                val worldsireId = game.findPermanent("Famished Worldsire")
                worldsireId.shouldNotBeNull()
                val countersComp = game.state.getEntity(worldsireId)?.get<CountersComponent>()
                withClue("Famished Worldsire should enter with 6 +1/+1 counters (2 lands × 3)") {
                    countersComp.shouldNotBeNull()
                    countersComp.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 6
                }

                // The two sacrificed lands should be in the graveyard.
                withClue("Two sacrificed lands should be in player 1's graveyard") {
                    game.state.getGraveyard(game.player1Id).count { entityId ->
                        game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Forest"
                    } shouldBe 2
                }

                // ETB trigger should now pause for "look at top X" land selection.
                withClue("ETB look-at-top should be pending") {
                    game.hasPendingDecision() shouldBe true
                }
                val lookDecision = game.getPendingDecision()
                lookDecision.shouldNotBeNull()
                lookDecision.shouldBeInstanceOf<SelectCardsDecision>()
                withClue("Should be looking at 6 cards (X = power = 6)") {
                    lookDecision.options.size shouldBe 6
                }

                // Pick the three Forests among the revealed cards.
                val forestCardsAmongLook = lookDecision.options.filter { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Forest"
                }
                forestCardsAmongLook.size shouldBe 3
                game.submitDecision(CardsSelectedResponse(lookDecision.id, forestCardsAmongLook))

                // Continue resolving any remaining shuffle/ordering steps.
                game.resolveStack()

                // The three Forests should be on the battlefield, tapped.
                val controllerForests = game.state.getBattlefield().filter { entityId ->
                    val card = game.state.getEntity(entityId)?.get<CardComponent>()
                    card?.name == "Forest"
                }
                // 10 starting Forests − 2 sacrificed = 8 still on battlefield, + 3 from library = 11 total
                withClue("Should now have 11 Forests on battlefield") {
                    controllerForests.size shouldBe 11
                }
                val newlyEnteredForests = forestCardsAmongLook
                for (forestId in newlyEnteredForests) {
                    withClue("Forests put onto battlefield from library should be tapped") {
                        (game.state.getEntity(forestId)?.get<TappedComponent>() != null) shouldBe true
                    }
                }

                // The library should be shuffled with the 3 Mountains (the non-selected revealed cards).
                // Initial library = 6. We moved 3 Forests out. So library = 3 Mountains.
                withClue("Library should retain the 3 unselected (Mountain) cards") {
                    game.librarySize(1) shouldBe initialLibrarySize - 3
                }
            }

            test("decline Devour — enters as 0/0 and dies to state-based actions; ETB resolves as no-op") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Famished Worldsire")
                    .withLandsOnBattlefield(1, "Forest", 8)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Famished Worldsire")
                game.resolveStack()

                // Devour decision: sacrifice nothing.
                val devourDecision = game.getPendingDecision()
                devourDecision.shouldNotBeNull()
                devourDecision.shouldBeInstanceOf<SelectCardsDecision>()
                game.submitDecision(CardsSelectedResponse(devourDecision.id, emptyList()))

                // With X = 0 the ETB look-at-top gathers 0 cards; the inner ChooseUpTo(0)
                // is a no-op. The 0/0 creature dies to SBAs.
                game.resolveStack()

                withClue("0/0 Famished Worldsire should die immediately") {
                    game.isOnBattlefield("Famished Worldsire") shouldBe false
                }
                withClue("Famished Worldsire should be in graveyard") {
                    game.isInGraveyard(1, "Famished Worldsire") shouldBe true
                }
            }
        }
    }
}
