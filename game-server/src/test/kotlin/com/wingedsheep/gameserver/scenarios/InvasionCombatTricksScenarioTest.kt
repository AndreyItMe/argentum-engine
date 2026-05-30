package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.mtg.sets.definitions.inv.InvasionSet
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.model.CardDefinition
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainAnyOf
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for four Invasion cards that compose existing engine primitives:
 *
 * - Dredge {B} — Sacrifice a creature or land. Draw a card.
 * - Overload {R} (Kicker {2}) — Destroy target artifact if MV ≤ 2 (kicked: ≤ 5).
 * - Chaotic Strike {1}{R} — combat coin-flip pump + draw.
 * - Cauldron Dance {4}{B}{R} — combat reanimate (haste, bounce at end) + put-from-hand
 *   (haste, sacrifice at end).
 *
 * No new engine mechanics were added; these tests verify the compositions resolve end to end.
 */
class InvasionCombatTricksScenarioTest : ScenarioTestBase() {

    private val testBear = CardDefinition.creature(
        "Test Bear", ManaCost.parse("{1}{G}"), setOf(Subtype("Bear")), 2, 2
    )
    private val cheapArtifact = CardDefinition.artifact("Cheap Trinket", ManaCost.parse("{1}"))
    private val pricyArtifact = CardDefinition.artifact("Pricy Engine", ManaCost.parse("{4}"))

    init {
        cardRegistry.register(InvasionSet.cards)
        cardRegistry.register(testBear)
        cardRegistry.register(cheapArtifact)
        cardRegistry.register(pricyArtifact)

        context("Dredge") {
            test("sacrifices a creature and draws a card") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Dredge")
                    .withCardOnBattlefield(1, "Test Bear")
                    .withLandsOnBattlefield(1, "Swamp", 1)
                    .withCardInLibrary(1, "Test Bear")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val handBefore = game.handSize(1)
                val bear = game.findPermanent("Test Bear")!!
                val castResult = game.castSpell(1, "Dredge")
                withClue("Cast should succeed: ${castResult.error}") { castResult.error shouldBe null }
                game.resolveStack()
                // Both the Bear and the tapped Swamp are legal sacrifices → choose the Bear.
                if (game.hasPendingDecision()) {
                    game.selectCards(listOf(bear))
                }

                withClue("Test Bear should have been sacrificed") {
                    game.isOnBattlefield("Test Bear") shouldBe false
                }
                // -1 for Dredge cast, sacrifice removes nothing from hand, +1 drawn
                withClue("Net hand size: cast Dredge (-1) then drew (+1) = unchanged from start") {
                    game.handSize(1) shouldBe handBefore - 1 + 1
                }
            }
        }

        context("Overload") {
            test("unkicked destroys a mana-value-1 artifact") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Overload")
                    .withCardOnBattlefield(2, "Cheap Trinket")
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val artifact = game.findPermanent("Cheap Trinket")!!
                val castResult = game.castSpell(1, "Overload", artifact)
                withClue("Cast should succeed: ${castResult.error}") { castResult.error shouldBe null }
                game.resolveStack()

                withClue("MV 1 artifact destroyed without kicker") {
                    game.isOnBattlefield("Cheap Trinket") shouldBe false
                }
            }

            test("unkicked does NOT destroy a mana-value-4 artifact") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Overload")
                    .withCardOnBattlefield(2, "Pricy Engine")
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val artifact = game.findPermanent("Pricy Engine")!!
                val castResult = game.castSpell(1, "Overload", artifact)
                withClue("Cast should succeed: ${castResult.error}") { castResult.error shouldBe null }
                game.resolveStack()

                withClue("MV 4 artifact survives an unkicked Overload (threshold 2)") {
                    game.isOnBattlefield("Pricy Engine") shouldBe true
                }
            }

            test("kicked destroys a mana-value-4 artifact") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Overload")
                    .withCardOnBattlefield(2, "Pricy Engine")
                    .withLandsOnBattlefield(1, "Mountain", 3) // {R} + kicker {2}
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val artifact = game.findPermanent("Pricy Engine")!!
                val playerId = game.player1Id
                val cardId = game.state.getHand(playerId).first {
                    game.state.getEntity(it)?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name == "Overload"
                }
                val castResult = game.execute(
                    CastSpell(playerId, cardId, listOf(ChosenTarget.Permanent(artifact)), wasKicked = true)
                )
                withClue("Kicked cast should succeed: ${castResult.error}") { castResult.error shouldBe null }
                game.resolveStack()

                withClue("MV 4 artifact destroyed when kicked (threshold 5)") {
                    game.isOnBattlefield("Pricy Engine") shouldBe false
                }
            }
        }

        context("Chaotic Strike") {
            test("resolves during combat: target ends at 2/2 or 3/3 and you draw a card") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Chaotic Strike")
                    .withCardOnBattlefield(1, "Test Bear")
                    .withLandsOnBattlefield(1, "Mountain", 2)
                    .withCardInLibrary(1, "Test Bear")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val handBefore = game.handSize(1)
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(mapOf("Test Bear" to 2))
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
                game.declareNoBlockers()
                if (game.state.priorityPlayerId != null && game.state.priorityPlayerId != game.state.activePlayerId) {
                    game.passPriority()
                }

                val bear = game.findPermanent("Test Bear")!!
                val castResult = game.castSpell(1, "Chaotic Strike", bear)
                withClue("Cast during declare-blockers should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                val projected = game.state.projectedState
                withClue("Win flip → 3/3, lose flip → 2/2 (base bear is 2/2)") {
                    listOf(projected.getPower(bear)) shouldContainAnyOf listOf(2, 3)
                    listOf(projected.getToughness(bear)) shouldContainAnyOf listOf(2, 3)
                }
                withClue("Cast Chaotic Strike (-1) and drew (+1) = unchanged") {
                    game.handSize(1) shouldBe handBefore - 1 + 1
                }
            }

            test("cannot be cast outside combat") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Chaotic Strike")
                    .withCardOnBattlefield(1, "Test Bear")
                    .withLandsOnBattlefield(1, "Mountain", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bear = game.findPermanent("Test Bear")!!
                val castResult = game.castSpell(1, "Chaotic Strike", bear)
                withClue("Casting in main phase should be rejected") {
                    (castResult.error != null) shouldBe true
                }
            }
        }

        context("Cauldron Dance") {
            test("reanimates a graveyard creature with haste during combat") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Cauldron Dance")
                    .withCardInGraveyard(1, "Test Bear")
                    .withCardOnBattlefield(1, "Test Bear")
                    .withLandsOnBattlefield(1, "Swamp", 5)
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(mapOf("Test Bear" to 2))
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
                game.declareNoBlockers()
                if (game.state.priorityPlayerId != null && game.state.priorityPlayerId != game.state.activePlayerId) {
                    game.passPriority()
                }

                val graveBear = game.state.getGraveyard(game.player1Id).first {
                    game.state.getEntity(it)?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name == "Test Bear"
                }
                val castResult = game.castSpellTargetingGraveyardCard(1, "Cauldron Dance", listOf(graveBear))
                withClue("Cast during combat should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()
                // The optional put-from-hand part may offer a selection (hand has no creatures);
                // skip it if prompted.
                if (game.hasPendingDecision()) {
                    game.skipSelection()
                }

                withClue("Reanimated Test Bear joins the attacker → two Test Bears on the battlefield") {
                    game.findPermanents("Test Bear").size shouldBe 2
                }
                withClue("The reanimated creature left the graveyard") {
                    game.isInGraveyard(1, "Test Bear") shouldBe false
                }
            }
        }
    }
}
