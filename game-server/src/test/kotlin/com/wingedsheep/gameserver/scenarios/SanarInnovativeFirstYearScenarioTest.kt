package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.MayPlayFromExileComponent
import com.wingedsheep.engine.state.permissions.addMayPlayPermission
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Scenario tests for Sanar, Innovative First-Year — specifically the
 * `SelectionRestriction.OnePerColor(matchControllerPermanentColors = true)`
 * cap on the exile selection produced by its Vivid trigger.
 */
class SanarInnovativeFirstYearScenarioTest : ScenarioTestBase() {

    /**
     * Pass priority until Sanar's Vivid trigger has resolved and the
     * SelectCardsDecision is presented (or until we hit a hard cap).
     */
    private fun advanceToSanarSelection(game: TestGame) {
        var iterations = 0
        while (game.state.pendingDecision == null && iterations++ < 30) {
            game.passPriority()
        }
    }

    init {
        context("Sanar, Innovative First-Year — exile selection restrictions") {

            test("OnePerColor + matchControllerPermanentColors caps the choice at one per controller colour") {
                // Controller permanents: Sanar (R/U) + Bloom Tender (G) → colours = {R,U,G}, X = 3.
                // Library top-to-bottom (after the on-turn draw): [Bloom Tender, Spell Snare, Spell Snare, Goatnap, Forest].
                // Sanar reveals X=3 nonland cards: Bloom Tender (G), Spell Snare (U), Spell Snare (U).
                val game = scenario()
                    .withPlayers("Active", "Opponent")
                    .withCardOnBattlefield(1, "Sanar, Innovative First-Year")
                    .withCardOnBattlefield(1, "Bloom Tender")
                    .withCardInLibrary(1, "Forest")        // drawn for turn
                    .withCardInLibrary(1, "Bloom Tender")  // revealed nonland #1 (G)
                    .withCardInLibrary(1, "Spell Snare")   // revealed nonland #2 (U)
                    .withCardInLibrary(1, "Spell Snare")   // revealed nonland #3 (U)
                    .withCardInLibrary(1, "Goatnap")       // not reached
                    .withCardInLibrary(1, "Forest")        // bottom
                    .withCardInLibrary(2, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.BEGINNING, Step.UPKEEP)
                    .build()

                advanceToSanarSelection(game)

                val decision = game.state.pendingDecision
                decision shouldNotBe null
                decision.shouldBeInstanceOf<SelectCardsDecision>()

                // The restriction's own ceiling caps maxSelections at the size of
                // (distinct-colours-of-eligible ∩ controller-permanent-colours) = {U,G} = 2.
                decision.maxSelections shouldBe 2
                decision.minSelections shouldBe 0
                decision.onePerColor shouldBe true
                decision.availableColors!!.toSet() shouldBe setOf("RED", "BLUE", "GREEN")

                // All three revealed cards remain selectable (each shares a colour with the
                // controller's permanents); the restriction normalises picks at submit time.
                val optionNames = decision.options.mapNotNull { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name
                }
                optionNames shouldContainExactlyInAnyOrder
                    listOf("Bloom Tender", "Spell Snare", "Spell Snare")

                // Submit both Spell Snares (both blue). The size cap (max=2) lets this
                // through, but the resumer's OnePerColor enforcement drops the duplicate
                // colour so only one Spell Snare ends up exiled.
                val spellSnareIds = decision.options.filter { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Spell Snare"
                }
                spellSnareIds.size shouldBe 2
                game.selectCards(spellSnareIds)
                game.resolveStack()

                val exiled = game.state.getExile(game.player1Id).mapNotNull { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name
                }
                // Exactly one Spell Snare was exiled, not both.
                exiled.count { it == "Spell Snare" } shouldBe 1
                exiled.size shouldBe 1

                // The other Spell Snare and the green Bloom Tender went back to the library.
                val library = game.state.getLibrary(game.player1Id).mapNotNull { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name
                }
                library shouldContain "Spell Snare"
                library shouldContain "Bloom Tender"
            }

            test("OnePerColor allows one card of each distinct controller colour") {
                // Same setup as above; this time the player picks one G + one U → both exiled.
                val game = scenario()
                    .withPlayers("Active", "Opponent")
                    .withCardOnBattlefield(1, "Sanar, Innovative First-Year")
                    .withCardOnBattlefield(1, "Bloom Tender")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Bloom Tender")
                    .withCardInLibrary(1, "Spell Snare")
                    .withCardInLibrary(1, "Spell Snare")
                    .withCardInLibrary(1, "Goatnap")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.BEGINNING, Step.UPKEEP)
                    .build()

                advanceToSanarSelection(game)

                val decision = game.state.pendingDecision
                decision.shouldBeInstanceOf<SelectCardsDecision>()

                val bloomTenderId = decision.options.first { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Bloom Tender"
                }
                val firstSpellSnareId = decision.options.first { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Spell Snare"
                }
                game.selectCards(listOf(bloomTenderId, firstSpellSnareId))
                game.resolveStack()

                val exiled = game.state.getExile(game.player1Id).mapNotNull { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name
                }
                exiled shouldContainExactlyInAnyOrder listOf("Bloom Tender", "Spell Snare")
            }

            test("revealed nonlands whose colours don't intersect controller's permanents are filtered out") {
                // Controller permanents: Sanar (R/U) + Bloom Tender (G) → colours = {R,U,G}.
                // Library top-to-bottom (after draw): [Bark of Doran (W), Bloom Tender (G), Goatnap (R)].
                // Bark of Doran shares no colour with the controller's permanents and is dropped
                // from the eligible set.
                val game = scenario()
                    .withPlayers("Active", "Opponent")
                    .withCardOnBattlefield(1, "Sanar, Innovative First-Year")
                    .withCardOnBattlefield(1, "Bloom Tender")
                    .withCardInLibrary(1, "Forest")          // drawn for turn
                    .withCardInLibrary(1, "Bark of Doran")   // off-colour (W) — should be filtered
                    .withCardInLibrary(1, "Bloom Tender")    // green — eligible
                    .withCardInLibrary(1, "Goatnap")         // red — eligible
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.BEGINNING, Step.UPKEEP)
                    .build()

                advanceToSanarSelection(game)

                val decision = game.state.pendingDecision
                decision.shouldBeInstanceOf<SelectCardsDecision>()
                decision.onePerColor shouldBe true

                val optionNames = decision.options.mapNotNull { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name
                }
                optionNames shouldContain "Bloom Tender"
                optionNames shouldContain "Goatnap"
                optionNames shouldNotContain "Bark of Doran"
                // Two distinct colours (G + R) intersect the controller's {R,U,G}, so cap = 2.
                decision.maxSelections shouldBe 2
            }

            test("casting an exiled BlightOrPay spell exposes both pay and blight legal actions") {
                // Set up Cinder Strike in player 1's exile with MayPlayFromExile permission
                // (mimicking what Sanar grants), plus a creature for the optional blight target.
                // Sanar himself (a creature) is on the battlefield so the blight path has a target.
                val game = scenario()
                    .withPlayers("Active", "Opponent")
                    .withCardOnBattlefield(1, "Sanar, Innovative First-Year")
                    .withCardOnBattlefield(2, "Glory Seeker")
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withCardInExile(1, "Cinder Strike")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Grant Cinder Strike a may-play-from-exile permission for player 1.
                val cinderStrikeId = game.state.getExile(game.player1Id).firstOrNull { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Cinder Strike"
                }!!
                game.state = game.state.updateEntity(cinderStrikeId) {
                    it.with(MayPlayFromExileComponent(controllerId = game.player1Id))
                }.addMayPlayPermission(
                    com.wingedsheep.engine.state.permissions.MayPlayPermission(
                        id = com.wingedsheep.sdk.model.EntityId.generate(),
                        cardIds = setOf(cinderStrikeId),
                        controllerId = game.player1Id,
                        timestamp = game.state.timestamp,
                    )
                )

                val enumerator = LegalActionEnumerator.create(cardRegistry)
                val legalActions = enumerator.enumerate(game.state, game.player1Id)
                val cinderStrikeActions = legalActions.filter { la ->
                    la.actionType == "CastSpell" && la.description.startsWith("Cast Cinder Strike")
                }

                // Two distinct legal actions: pay path and blight path.
                cinderStrikeActions.size shouldBe 2

                val payAction = cinderStrikeActions.first { it.description == "Cast Cinder Strike" }
                val blightAction = cinderStrikeActions.first { it.description.contains("Blight") }

                // Pay path has no Blight cost-info attached.
                payAction.additionalCostInfo?.costType shouldNotBe "Blight"

                // Blight path surfaces blight target info so the client can prompt the player.
                blightAction.additionalCostInfo shouldNotBe null
                blightAction.additionalCostInfo!!.costType shouldBe "Blight"
                blightAction.additionalCostInfo!!.blightAmount shouldBe 1
                blightAction.additionalCostInfo!!.validBlightTargets.isEmpty() shouldBe false
            }

            test("colourless cards are not eligible when matchControllerPermanentColors filters them out") {
                // Controller permanents: Sanar (R/U) + Bloom Tender (G) → colours = {R,U,G}.
                // Library: [Foraging Wickermaw (colourless artefact), Spell Snare (U), Goatnap (R)].
                // The colourless card has no overlap with controller's permanent colours, so
                // it is filtered out of the eligible set.
                val game = scenario()
                    .withPlayers("Active", "Opponent")
                    .withCardOnBattlefield(1, "Sanar, Innovative First-Year")
                    .withCardOnBattlefield(1, "Bloom Tender")
                    .withCardInLibrary(1, "Forest")              // drawn for turn
                    .withCardInLibrary(1, "Foraging Wickermaw")  // colourless
                    .withCardInLibrary(1, "Spell Snare")         // U
                    .withCardInLibrary(1, "Goatnap")             // R
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.BEGINNING, Step.UPKEEP)
                    .build()

                advanceToSanarSelection(game)

                val decision = game.state.pendingDecision
                decision.shouldBeInstanceOf<SelectCardsDecision>()

                val optionNames = decision.options.mapNotNull { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name
                }
                optionNames shouldNotContain "Foraging Wickermaw"
                optionNames shouldContain "Spell Snare"
                optionNames shouldContain "Goatnap"
            }
        }
    }
}
