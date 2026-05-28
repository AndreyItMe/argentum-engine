package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for Superior Spider-Man.
 *
 * Superior Spider-Man {2}{U}{B}
 * Legendary Creature — Spider Human Hero
 * 4/4
 * Mind Swap — You may have Superior Spider-Man enter as a copy of any creature card in a graveyard,
 * except his name is Superior Spider-Man and he's a 4/4 Spider Human Hero in addition to his other
 * types. When you do, exile that card.
 *
 * Exercises the graveyard-sourced [com.wingedsheep.sdk.scripting.EntersAsCopy] path: name override,
 * 4/4 P/T override, added Spider/Human/Hero types, and exiling the copied card.
 */
class SuperiorSpiderManScenarioTest : ScenarioTestBase() {

    init {
        context("Superior Spider-Man — graveyard clone with overrides") {

            test("enters as a copy of a creature card in a graveyard, keeping name + 4/4 and exiling the card") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Superior Spider-Man")
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withLandsOnBattlefield(1, "Swamp", 2)
                    .withCardInGraveyard(2, "Glory Seeker") // 2/2 Human Soldier
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val castResult = game.castSpell(1, "Superior Spider-Man")
                withClue("Should cast Superior Spider-Man: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                withClue("Should have pending graveyard copy selection") {
                    game.hasPendingDecision() shouldBe true
                }

                val glorySeekerInGrave = game.state.getGraveyard(game.player2Id).first { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Glory Seeker"
                }
                game.selectCards(listOf(glorySeekerInGrave))

                // The permanent keeps Superior Spider-Man's name (name override).
                val copy = game.findPermanent("Superior Spider-Man")
                withClue("Player should control a permanent named Superior Spider-Man") {
                    copy shouldNotBe null
                }
                val copyCard = game.state.getEntity(copy!!)?.get<CardComponent>()!!

                withClue("Copy should be forced to 4/4") {
                    copyCard.baseStats?.basePower shouldBe 4
                    copyCard.baseStats?.baseToughness shouldBe 4
                }
                withClue("Copy should gain Spider/Human/Hero on top of Glory Seeker's Human Soldier types") {
                    copyCard.typeLine.subtypes.map { it.value } shouldContainAll
                        listOf("Spider", "Human", "Hero", "Soldier")
                }

                // "When you do, exile that card."
                withClue("Copied card should have been exiled, not left in graveyard") {
                    game.state.getGraveyard(game.player2Id).contains(glorySeekerInGrave) shouldBe false
                    game.state.getExile(game.player2Id).contains(glorySeekerInGrave) shouldBe true
                }
            }

            test("optional — declining leaves Superior Spider-Man as his printed 4/4 self, graveyard card untouched") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Superior Spider-Man")
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withLandsOnBattlefield(1, "Swamp", 2)
                    .withCardInGraveyard(2, "Glory Seeker")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Superior Spider-Man")
                game.resolveStack()
                game.skipSelection()

                val spidey = game.findPermanent("Superior Spider-Man")
                withClue("Superior Spider-Man should enter as himself") {
                    spidey shouldNotBe null
                }
                val card = game.state.getEntity(spidey!!)?.get<CardComponent>()!!
                withClue("He is printed as a 4/4") {
                    card.baseStats?.basePower shouldBe 4
                    card.baseStats?.baseToughness shouldBe 4
                }
                withClue("No copy was made, so the graveyard card should be untouched") {
                    val gloryStillInGrave = game.state.getGraveyard(game.player2Id).any { id ->
                        game.state.getEntity(id)?.get<CardComponent>()?.name == "Glory Seeker"
                    }
                    gloryStillInGrave shouldBe true
                }
            }
        }
    }
}
