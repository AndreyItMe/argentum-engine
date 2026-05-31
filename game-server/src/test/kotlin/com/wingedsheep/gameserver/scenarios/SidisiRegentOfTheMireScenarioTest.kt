package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Sidisi, Regent of the Mire ({1}{B}, 1/3).
 *
 * "{T}, Sacrifice a creature you control with mana value X other than Sidisi:
 *  Return target creature card with mana value X plus 1 from your graveyard to the
 *  battlefield. Activate only as a sorcery."
 *
 * The returnable card's mana value is cost-linked (X + 1, where X is the sacrificed
 * creature's mana value). These tests exercise that the engine reads X off the
 * cost-sacrificed permanent and filters the graveyard to mana value X + 1.
 *
 * Inline test creatures fix exact mana values:
 *   - "Mire Fodder"  {1}{B}    → mana value 2 (sacrificed → X = 2)
 *   - "Mire Revenant" {2}{B}   → mana value 3 (X + 1 → returnable)
 *   - "Mire Wanderer" {1}{B}{B} → mana value 3 (X + 1 → returnable, for the choice case)
 *   - "Mire Colossus" {3}{B}   → mana value 4 (not returnable)
 *   - "Mire Imp"      {B}       → mana value 1 (not returnable)
 */
class SidisiRegentOfTheMireScenarioTest : ScenarioTestBase() {

    private val mireFodder = CardDefinition.creature(
        "Mire Fodder", ManaCost.parse("{1}{B}"), setOf(Subtype("Zombie")), 1, 1
    )
    private val mireRevenant = CardDefinition.creature(
        "Mire Revenant", ManaCost.parse("{2}{B}"), setOf(Subtype("Zombie")), 3, 3
    )
    private val mireWanderer = CardDefinition.creature(
        "Mire Wanderer", ManaCost.parse("{1}{B}{B}"), setOf(Subtype("Snake")), 2, 4
    )
    private val mireColossus = CardDefinition.creature(
        "Mire Colossus", ManaCost.parse("{3}{B}"), setOf(Subtype("Zombie")), 5, 5
    )
    private val mireImp = CardDefinition.creature(
        "Mire Imp", ManaCost.parse("{B}"), setOf(Subtype("Imp")), 1, 1
    )

    init {
        cardRegistry.register(mireFodder)
        cardRegistry.register(mireRevenant)
        cardRegistry.register(mireWanderer)
        cardRegistry.register(mireColossus)
        cardRegistry.register(mireImp)

        fun sidisiAbilityId() =
            cardRegistry.getCard("Sidisi, Regent of the Mire")!!.script.activatedAbilities.first().id

        context("Sidisi, Regent of the Mire — cost-linked relative mana value return") {

            test("sacrificing a mana value 2 creature prompts for the single mana value 3 creature") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Sidisi, Regent of the Mire")
                    .withCardOnBattlefield(1, "Mire Fodder") // MV 2 → X = 2
                    .withCardInGraveyard(1, "Mire Revenant") // MV 3 → returnable
                    .withCardInGraveyard(1, "Mire Colossus") // MV 4 → not returnable
                    .withCardInGraveyard(1, "Mire Imp")      // MV 1 → not returnable
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val sidisiId = game.findPermanent("Sidisi, Regent of the Mire")!!
                val fodderId = game.findPermanent("Mire Fodder")!!

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = sidisiId,
                        abilityId = sidisiAbilityId(),
                        costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(fodderId))
                    )
                )
                withClue("Ability should activate: ${result.error}") { result.error shouldBe null }
                withClue("Sacrificed creature should be in the graveyard") {
                    game.isInGraveyard(1, "Mire Fodder") shouldBe true
                }

                game.resolveStack()

                // Even with a single eligible card the controller is prompted to confirm.
                withClue("A single eligible card still prompts for confirmation") {
                    game.hasPendingDecision() shouldBe true
                }
                game.selectCards(game.findCardsInGraveyard(1, "Mire Revenant"))

                withClue("The mana value 3 creature should be returned to the battlefield") {
                    game.isOnBattlefield("Mire Revenant") shouldBe true
                }
                withClue("The mana value 4 creature must stay in the graveyard") {
                    game.isInGraveyard(1, "Mire Colossus") shouldBe true
                    game.isOnBattlefield("Mire Colossus") shouldBe false
                }
                withClue("The mana value 1 creature must stay in the graveyard") {
                    game.isInGraveyard(1, "Mire Imp") shouldBe true
                }
            }

            test("with two eligible creatures the controller chooses which to return") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Sidisi, Regent of the Mire")
                    .withCardOnBattlefield(1, "Mire Fodder")  // MV 2 → X = 2
                    .withCardInGraveyard(1, "Mire Revenant")  // MV 3 → eligible
                    .withCardInGraveyard(1, "Mire Wanderer")  // MV 3 → eligible
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val sidisiId = game.findPermanent("Sidisi, Regent of the Mire")!!
                val fodderId = game.findPermanent("Mire Fodder")!!

                game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = sidisiId,
                        abilityId = sidisiAbilityId(),
                        costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(fodderId))
                    )
                ).error shouldBe null

                game.resolveStack()

                withClue("Two eligible mana value 3 cards should prompt a selection") {
                    game.hasPendingDecision() shouldBe true
                }

                val wandererCardId = game.findCardsInGraveyard(1, "Mire Wanderer").first()
                game.selectCards(listOf(wandererCardId))

                withClue("Chosen creature returns to the battlefield") {
                    game.isOnBattlefield("Mire Wanderer") shouldBe true
                }
                withClue("Unchosen eligible creature stays in the graveyard") {
                    game.isInGraveyard(1, "Mire Revenant") shouldBe true
                    game.isOnBattlefield("Mire Revenant") shouldBe false
                }
            }

            test("no eligible creature returns nothing but the cost is still paid") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Sidisi, Regent of the Mire")
                    .withCardOnBattlefield(1, "Mire Fodder")  // MV 2 → X = 2, needs MV 3
                    .withCardInGraveyard(1, "Mire Colossus")  // MV 4 → not eligible
                    .withCardInGraveyard(1, "Mire Imp")       // MV 1 → not eligible
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val sidisiId = game.findPermanent("Sidisi, Regent of the Mire")!!
                val fodderId = game.findPermanent("Mire Fodder")!!

                game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = sidisiId,
                        abilityId = sidisiAbilityId(),
                        costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(fodderId))
                    )
                ).error shouldBe null

                game.resolveStack()

                withClue("No prompt when nothing qualifies") { game.hasPendingDecision() shouldBe false }
                withClue("Nothing is returned to the battlefield") {
                    game.isOnBattlefield("Mire Colossus") shouldBe false
                    game.isOnBattlefield("Mire Imp") shouldBe false
                }
                withClue("The sacrifice cost was still paid") {
                    game.isInGraveyard(1, "Mire Fodder") shouldBe true
                }
            }
        }
    }
}
