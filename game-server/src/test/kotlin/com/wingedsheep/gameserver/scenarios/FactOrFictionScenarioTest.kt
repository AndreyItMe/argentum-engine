package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Scenario tests for Fact or Fiction — the canonical "divvy" mechanic (CR 700.3).
 *
 *   {3}{U} Instant
 *   "Reveal the top five cards of your library. An opponent separates those cards
 *    into two piles. Put one pile into your hand and the other into your graveyard."
 *
 * Two-step resolution:
 *   1. Opponent partitions the five revealed cards into two piles (any sizes,
 *      including empty — CR 700.3d).
 *   2. The caster (FoF's controller) chooses which pile goes to their hand;
 *      the other goes to their graveyard.
 */
class FactOrFictionScenarioTest : ScenarioTestBase() {

    init {
        context("Fact or Fiction") {

            test("opponent partitions; caster picks the larger pile for the hand") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Fact or Fiction")
                    .withLandsOnBattlefield(1, "Island", 4)
                    // Top five of P1's library (top is index 0)
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(1, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val initialHand = game.handSize(1) // includes Fact or Fiction itself

                game.castSpell(1, "Fact or Fiction").error shouldBe null
                game.resolveStack()

                // Step 1 — opponent partitions.
                val partition = game.getPendingDecision()
                    .shouldNotBeNull()
                    .shouldBeInstanceOf<SelectCardsDecision>()
                withClue("Opponent makes the partition (CR 700.3 / FoF rules)") {
                    partition.playerId shouldBe game.player2Id
                }
                withClue("All five revealed cards should be options") {
                    partition.options.size shouldBe 5
                }
                withClue("Either pile may be empty (CR 700.3d)") {
                    partition.minSelections shouldBe 0
                    partition.maxSelections shouldBe 5
                }

                // Opponent puts Mountain + Forest in pile 1; Plains, Swamp, Island form pile 2.
                val pileA = partition.options.filter { id -> nameOf(game, id) in setOf("Mountain", "Forest") }
                pileA.size shouldBe 2
                game.selectCards(pileA)

                // Step 2 — caster (P1) picks which pile to keep.
                val pickPile = game.getPendingDecision()
                    .shouldNotBeNull()
                    .shouldBeInstanceOf<ChooseOptionDecision>()
                withClue("The caster — not the opponent — picks which pile is which") {
                    pickPile.playerId shouldBe game.player1Id
                }
                withClue("Two pile options are presented") {
                    pickPile.options.size shouldBe 2
                }
                withClue("Each option previews the cards in that pile") {
                    val cardsByOption = pickPile.optionCardIds.shouldNotBeNull()
                    cardsByOption[0]?.toSet() shouldBe pileA.toSet()
                    cardsByOption[1]?.size shouldBe 3
                }

                // Caster keeps the 3-card pile (Pile 2).
                game.submitDecision(OptionChosenResponse(pickPile.id, 1))

                withClue("Chosen (3-card) pile lands in P1's hand") {
                    game.isInHand(1, "Plains") shouldBe true
                    game.isInHand(1, "Swamp") shouldBe true
                    game.isInHand(1, "Island") shouldBe true
                    // Original hand was [Fact or Fiction]; after cast it left, then 3 cards arrived.
                    game.handSize(1) shouldBe initialHand - 1 + 3
                }
                withClue("Other pile (Mountain, Forest) lands in P1's graveyard alongside FoF") {
                    game.isInGraveyard(1, "Mountain") shouldBe true
                    game.isInGraveyard(1, "Forest") shouldBe true
                    game.graveyardSize(1) shouldBe 3
                }
                withClue("Library should be empty (all five top cards consumed)") {
                    game.librarySize(1) shouldBe 0
                }
            }

            test("caster may keep pile 1 instead — pile assignment is the caster's choice") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Fact or Fiction")
                    .withLandsOnBattlefield(1, "Island", 4)
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(1, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Fact or Fiction").error shouldBe null
                game.resolveStack()

                val partition = game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
                // Opponent puts Mountain + Forest in pile 1.
                val pileA = partition.options.filter { id -> nameOf(game, id) in setOf("Mountain", "Forest") }
                game.selectCards(pileA)

                val pickPile = game.getPendingDecision().shouldBeInstanceOf<ChooseOptionDecision>()
                // Caster keeps pile 1 (Mountain, Forest) instead.
                game.submitDecision(OptionChosenResponse(pickPile.id, 0))

                withClue("Pile 1 (Mountain, Forest) lands in P1's hand") {
                    game.isInHand(1, "Mountain") shouldBe true
                    game.isInHand(1, "Forest") shouldBe true
                }
                withClue("The other pile (Plains, Swamp, Island) lands in P1's graveyard with FoF") {
                    val gyNames = game.state.getGraveyard(game.player1Id).mapNotNull { id ->
                        game.state.getEntity(id)?.get<CardComponent>()?.name
                    }
                    gyNames shouldContainExactlyInAnyOrder listOf(
                        "Fact or Fiction", "Plains", "Swamp", "Island"
                    )
                }
            }

            test("empty pile is legal (CR 700.3d) — caster can still choose to keep all five") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Fact or Fiction")
                    .withLandsOnBattlefield(1, "Island", 4)
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(1, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Fact or Fiction").error shouldBe null
                game.resolveStack()

                val partition = game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
                partition.minSelections shouldBe 0

                // Opponent makes pile 1 empty; pile 2 holds all five.
                game.skipSelection()

                val pickPile = game.getPendingDecision().shouldBeInstanceOf<ChooseOptionDecision>()
                pickPile.playerId shouldBe game.player1Id
                val byOption = pickPile.optionCardIds.shouldNotBeNull()
                byOption[0]?.size shouldBe 0
                byOption[1]?.size shouldBe 5

                // Caster keeps the five-card pile.
                game.submitDecision(OptionChosenResponse(pickPile.id, 1))

                withClue("All five revealed cards should be in P1's hand") {
                    listOf("Mountain", "Forest", "Plains", "Swamp", "Island").forEach { name ->
                        game.isInHand(1, name) shouldBe true
                    }
                }
                withClue("Only Fact or Fiction itself should be in the graveyard") {
                    game.graveyardSize(1) shouldBe 1
                    game.isInGraveyard(1, "Fact or Fiction") shouldBe true
                }
            }

            test("opponent can dump everything into one pile and force caster to take it or burn it") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Fact or Fiction")
                    .withLandsOnBattlefield(1, "Island", 4)
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(1, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Fact or Fiction").error shouldBe null
                game.resolveStack()

                val partition = game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
                // Opponent puts ALL five revealed cards into pile 1.
                game.selectCards(partition.options)

                val pickPile = game.getPendingDecision().shouldBeInstanceOf<ChooseOptionDecision>()
                // Caster keeps the five-card pile (pile 1) — the empty pile would have been pointless.
                game.submitDecision(OptionChosenResponse(pickPile.id, 0))

                withClue("All revealed cards land in P1's hand") {
                    listOf("Mountain", "Forest", "Plains", "Swamp", "Island").forEach { name ->
                        game.isInHand(1, name) shouldBe true
                    }
                }
                withClue("Only Fact or Fiction itself should be in the graveyard") {
                    game.graveyardSize(1) shouldBe 1
                }
            }
        }
    }

    private fun nameOf(game: TestGame, id: EntityId): String? =
        game.state.getEntity(id)?.get<CardComponent>()?.name
}
