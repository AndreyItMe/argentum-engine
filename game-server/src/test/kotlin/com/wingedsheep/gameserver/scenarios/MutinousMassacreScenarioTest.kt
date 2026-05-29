package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.model.CardDefinition
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Scenario test for Mutinous Massacre.
 *
 * Card reference:
 * - Mutinous Massacre ({3}{B}{B}{R}{R}): Sorcery
 *   "Choose odd or even. Destroy each creature with mana value of the chosen quality.
 *   Then gain control of all creatures until end of turn. Untap them.
 *   They gain haste until end of turn. (Zero is even.)"
 *
 * Setup uses creatures with known mana values:
 * - Llanowar Elves : {G}    = MV 1 (odd)
 * - Grizzly Bears  : {1}{G} = MV 2 (even)
 * - Raging Cougar  : {2}{R} = MV 3 (odd)
 * - Hill Giant     : {3}{R} = MV 4 (even)
 *
 * The "(Zero is even.)" clause and the {X}->0 ruling are exercised by an {X}-cost creature
 * (mana value 0), which must be treated as even.
 */
class MutinousMassacreScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    // An {X}-cost creature: X is 0 off the stack, so mana value is 0 — even (CR ruling).
    private val xCostBeast = CardDefinition.creature(
        name = "MM X Beast",
        manaCost = ManaCost.parse("{X}"),
        subtypes = setOf(Subtype("Beast")),
        power = 2,
        toughness = 2
    )

    private fun TestGame.chooseMode(descriptionContains: String) {
        val decision = getPendingDecision()
        decision.shouldNotBeNull()
        decision.shouldBeInstanceOf<ChooseOptionDecision>()
        val idx = decision.options.indexOfFirst { it.contains(descriptionContains, ignoreCase = true) }
        check(idx >= 0) {
            "No mode matched '$descriptionContains' in ${decision.options}"
        }
        submitDecision(OptionChosenResponse(decision.id, idx))
    }

    init {
        cardRegistry.register(xCostBeast)

        context("Mutinous Massacre — Odd mode") {
            test("destroys odd-MV creatures, steals/untaps/hastes the rest") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Mutinous Massacre")
                    .withLandsOnBattlefield(1, "Swamp", 4)
                    .withLandsOnBattlefield(1, "Mountain", 3)
                    // Player 1's board: one of each parity
                    .withCardOnBattlefield(1, "Llanowar Elves")     // MV 1 — odd, dies
                    .withCardOnBattlefield(1, "Grizzly Bears")      // MV 2 — even, survives
                    // Player 2's board: one of each parity, both tapped
                    .withCardOnBattlefield(2, "Raging Cougar", tapped = true) // MV 3 — odd, dies
                    .withCardOnBattlefield(2, "Hill Giant", tapped = true)    // MV 4 — even, survives
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val ownBears = game.findPermanent("Grizzly Bears")!!
                val stolenGiant = game.findPermanent("Hill Giant")!!

                game.castSpell(1, "Mutinous Massacre")
                game.chooseMode("Odd")
                game.resolveStack()

                withClue("Llanowar Elves (MV 1, odd) should be destroyed") {
                    game.isInGraveyard(1, "Llanowar Elves") shouldBe true
                }
                withClue("Raging Cougar (MV 3, odd) should be destroyed") {
                    game.isInGraveyard(2, "Raging Cougar") shouldBe true
                }
                withClue("Grizzly Bears (MV 2, even) should survive") {
                    game.isOnBattlefield("Grizzly Bears") shouldBe true
                }
                withClue("Hill Giant (MV 4, even) should survive") {
                    game.isOnBattlefield("Hill Giant") shouldBe true
                }

                val projected = stateProjector.project(game.state)
                withClue("Player 1 should control their own Grizzly Bears") {
                    projected.getController(ownBears) shouldBe game.player1Id
                }
                withClue("Player 1 should have stolen Hill Giant") {
                    projected.getController(stolenGiant) shouldBe game.player1Id
                }
                withClue("Stolen Hill Giant should be untapped") {
                    game.state.getEntity(stolenGiant)?.has<TappedComponent>() shouldBe false
                }
                withClue("Stolen Hill Giant should have haste") {
                    projected.hasKeyword(stolenGiant, Keyword.HASTE) shouldBe true
                }
                withClue("Player 1's own Bears should also have haste") {
                    projected.hasKeyword(ownBears, Keyword.HASTE) shouldBe true
                }
            }
        }

        context("Mutinous Massacre — Even mode") {
            test("destroys even-MV creatures, steals/untaps/hastes the rest") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Mutinous Massacre")
                    .withLandsOnBattlefield(1, "Swamp", 4)
                    .withLandsOnBattlefield(1, "Mountain", 3)
                    .withCardOnBattlefield(1, "Llanowar Elves") // MV 1, odd, survives
                    .withCardOnBattlefield(1, "Grizzly Bears")  // MV 2, even, dies
                    .withCardOnBattlefield(2, "Raging Cougar", tapped = true) // MV 3, odd, survives
                    .withCardOnBattlefield(2, "Hill Giant", tapped = true)    // MV 4, even, dies
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val ownElves = game.findPermanent("Llanowar Elves")!!
                val stolenCougar = game.findPermanent("Raging Cougar")!!

                game.castSpell(1, "Mutinous Massacre")
                game.chooseMode("Even")
                game.resolveStack()

                withClue("Grizzly Bears (MV 2, even) should be destroyed") {
                    game.isInGraveyard(1, "Grizzly Bears") shouldBe true
                }
                withClue("Hill Giant (MV 4, even) should be destroyed") {
                    game.isInGraveyard(2, "Hill Giant") shouldBe true
                }
                withClue("Llanowar Elves (MV 1, odd) should survive") {
                    game.isOnBattlefield("Llanowar Elves") shouldBe true
                }
                withClue("Raging Cougar (MV 3, odd) should survive") {
                    game.isOnBattlefield("Raging Cougar") shouldBe true
                }

                val projected = stateProjector.project(game.state)
                withClue("Player 1 should now control the stolen Raging Cougar") {
                    projected.getController(stolenCougar) shouldBe game.player1Id
                }
                withClue("Stolen Raging Cougar should be untapped") {
                    game.state.getEntity(stolenCougar)?.has<TappedComponent>() shouldBe false
                }
                withClue("Stolen Raging Cougar should have haste") {
                    projected.hasKeyword(stolenCougar, Keyword.HASTE) shouldBe true
                }
                withClue("Player 1's own Elves should have haste too") {
                    projected.hasKeyword(ownElves, Keyword.HASTE) shouldBe true
                }
            }
        }

        context("Mutinous Massacre — control reverts at end of turn") {
            test("stolen creature returns to original controller at cleanup") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Mutinous Massacre")
                    .withLandsOnBattlefield(1, "Swamp", 4)
                    .withLandsOnBattlefield(1, "Mountain", 3)
                    .withCardOnBattlefield(2, "Hill Giant") // MV 4, survives Odd mode
                    // Library padding so neither player decks out during cleanup turns.
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(2, "Mountain")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val stolenGiant = game.findPermanent("Hill Giant")!!

                game.castSpell(1, "Mutinous Massacre")
                game.chooseMode("Odd")
                game.resolveStack()

                val midTurn = stateProjector.project(game.state)
                withClue("Player 1 should control Hill Giant after resolution") {
                    midTurn.getController(stolenGiant) shouldBe game.player1Id
                }

                game.passUntilPhase(Phase.ENDING, Step.CLEANUP)

                val afterEot = stateProjector.project(game.state)
                withClue("Hill Giant should return to Player 2's control at end of turn") {
                    afterEot.getController(stolenGiant) shouldBe game.player2Id
                }
            }
        }

        context("Mutinous Massacre — zero is even") {
            test("an {X}-cost creature (MV 0) is destroyed by Even, spared by Odd") {
                // Even mode: MV-0 beast dies, odd Llanowar Elves (MV 1) survives.
                run {
                    val game = scenario()
                        .withPlayers("Player", "Opponent")
                        .withCardInHand(1, "Mutinous Massacre")
                        .withLandsOnBattlefield(1, "Swamp", 4)
                        .withLandsOnBattlefield(1, "Mountain", 3)
                        .withCardOnBattlefield(2, "MM X Beast")      // MV 0 — even
                        .withCardOnBattlefield(2, "Llanowar Elves")  // MV 1 — odd
                        .withActivePlayer(1)
                        .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                        .build()

                    game.castSpell(1, "Mutinous Massacre")
                    game.chooseMode("Even")
                    game.resolveStack()

                    withClue("MM X Beast (MV 0) should be destroyed by Even — zero is even") {
                        game.isInGraveyard(2, "MM X Beast") shouldBe true
                    }
                    withClue("Llanowar Elves (MV 1, odd) should survive Even mode") {
                        game.isOnBattlefield("Llanowar Elves") shouldBe true
                    }
                }

                // Odd mode: MV-0 beast survives (and is stolen), odd Llanowar Elves dies.
                run {
                    val game = scenario()
                        .withPlayers("Player", "Opponent")
                        .withCardInHand(1, "Mutinous Massacre")
                        .withLandsOnBattlefield(1, "Swamp", 4)
                        .withLandsOnBattlefield(1, "Mountain", 3)
                        .withCardOnBattlefield(2, "MM X Beast")      // MV 0 — even
                        .withCardOnBattlefield(2, "Llanowar Elves")  // MV 1 — odd
                        .withActivePlayer(1)
                        .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                        .build()

                    val stolenBeast = game.findPermanent("MM X Beast")!!

                    game.castSpell(1, "Mutinous Massacre")
                    game.chooseMode("Odd")
                    game.resolveStack()

                    withClue("MM X Beast (MV 0, even) should survive Odd mode") {
                        game.isOnBattlefield("MM X Beast") shouldBe true
                    }
                    withClue("Llanowar Elves (MV 1, odd) should be destroyed by Odd mode") {
                        game.isInGraveyard(2, "Llanowar Elves") shouldBe true
                    }
                    val projected = stateProjector.project(game.state)
                    withClue("Surviving MV-0 beast should be stolen by Player 1") {
                        projected.getController(stolenBeast) shouldBe game.player1Id
                    }
                }
            }
        }
    }
}
