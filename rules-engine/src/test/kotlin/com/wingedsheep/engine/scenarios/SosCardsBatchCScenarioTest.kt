package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.PreparedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for the "Secrets of Strixhaven batch C" cards:
 *  - Render Speechless     — targeted nonland discard + two +1/+1 counters on up to one creature.
 *  - Social Snub           — cast-trigger may-copy; edict each player; opponents lose 1, you gain 1.
 *  - Wilt in the Heat      — {2}-less if a card left your graveyard; 5 damage + exile-on-death.
 *  - Vastlands Scavenger   — prepare creature; the "Bind to Life" copy mills 7 and reanimates a creature.
 */
class SosCardsBatchCScenarioTest : ScenarioTestBase() {

    private fun TestGame.plusCounters(entityId: EntityId): Int =
        state.getEntity(entityId)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    private fun TestGame.findExileCopy(playerNumber: Int, name: String): EntityId? {
        val playerId = if (playerNumber == 1) player1Id else player2Id
        return state.getExile(playerId).firstOrNull { id ->
            state.getEntity(id)?.get<CardComponent>()?.name == name
        }
    }

    init {

        context("Render Speechless") {
            test("opponent discards a chosen nonland card and the targeted creature gets two +1/+1 counters") {
                var builder = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Render Speechless")
                    .withCardInHand(2, "Hill Giant")     // nonland — the discardable card
                    .withCardInHand(2, "Forest")         // land — must NOT be choosable
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withLandsOnBattlefield(1, "Swamp", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                val game = builder.build()

                val bears = game.findPermanent("Grizzly Bears")!!
                val cardId = game.findCardsInHand(1, "Render Speechless").first()

                game.execute(
                    CastSpell(
                        game.player1Id,
                        cardId,
                        listOf(
                            ChosenTarget.Player(game.player2Id),
                            ChosenTarget.Permanent(bears),
                        ),
                    ),
                ).error shouldBe null
                game.resolveStack()

                // Controller chooses the nonland card (Hill Giant) to discard.
                val hillGiant = game.findCardsInHand(2, "Hill Giant").first()
                game.selectCards(listOf(hillGiant))
                game.resolveStack()

                withClue("opponent discarded the chosen nonland card") {
                    game.isInGraveyard(2, "Hill Giant") shouldBe true
                }
                withClue("the land was not discardable and stays in hand") {
                    game.isInHand(2, "Forest") shouldBe true
                }
                withClue("targeted creature got two +1/+1 counters") {
                    game.plusCounters(bears) shouldBe 2
                }
            }

            test("can be cast with no creature target (up to one) and still forces the discard") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Render Speechless")
                    .withCardInHand(2, "Hill Giant")
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withLandsOnBattlefield(1, "Swamp", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cardId = game.findCardsInHand(1, "Render Speechless").first()
                game.execute(
                    CastSpell(game.player1Id, cardId, listOf(ChosenTarget.Player(game.player2Id))),
                ).error shouldBe null
                game.resolveStack()

                val hillGiant = game.findCardsInHand(2, "Hill Giant").first()
                game.selectCards(listOf(hillGiant))
                game.resolveStack()

                withClue("opponent still discards even with no creature target") {
                    game.isInGraveyard(2, "Hill Giant") shouldBe true
                }
            }
        }

        context("Social Snub") {
            test("each player sacrifices a creature; opponent loses 1 and you gain 1") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Social Snub")
                    .withCardOnBattlefield(1, "Grizzly Bears")  // your creature (also enables the cast trigger)
                    .withCardOnBattlefield(2, "Hill Giant")     // opponent's creature
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withLandsOnBattlefield(1, "Swamp", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val myLifeBefore = game.getLifeTotal(1)
                val oppLifeBefore = game.getLifeTotal(2)

                val cardId = game.findCardsInHand(1, "Social Snub").first()
                game.execute(CastSpell(game.player1Id, cardId)).error shouldBe null
                game.resolveStack()
                // The cast trigger ("you may copy this spell") pauses for a yes/no — decline the copy.
                if (game.state.pendingDecision != null) {
                    game.answerYesNo(false)
                    game.resolveStack()
                }

                withClue("each player sacrificed their only creature") {
                    game.isInGraveyard(1, "Grizzly Bears") shouldBe true
                    game.isInGraveyard(2, "Hill Giant") shouldBe true
                }
                withClue("each opponent lost 1 life") {
                    game.getLifeTotal(2) shouldBe oppLifeBefore - 1
                }
                withClue("you gained 1 life") {
                    game.getLifeTotal(1) shouldBe myLifeBefore + 1
                }
            }
        }

        context("Wilt in the Heat") {
            test("deals 5 damage and exiles the creature instead of letting it die") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Wilt in the Heat")
                    .withCardOnBattlefield(2, "Grizzly Bears")  // 2/2 — 5 damage is lethal
                    .withLandsOnBattlefield(1, "Mountain", 2)
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!
                game.castSpell(1, "Wilt in the Heat", targetId = bears).error shouldBe null
                game.resolveStack()

                withClue("the creature is exiled, not in the graveyard") {
                    game.isInGraveyard(2, "Grizzly Bears") shouldBe false
                    game.state.getExile(game.player2Id).any {
                        game.state.getEntity(it)?.get<CardComponent>()?.name == "Grizzly Bears"
                    } shouldBe true
                }
            }
        }

        context("Vastlands Scavenger // Bind to Life") {
            test("enters prepared; the Bind to Life copy mills seven and reanimates a creature") {
                var builder = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Vastlands Scavenger")
                    .withLandsOnBattlefield(1, "Forest", 8)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                // Top of library: a creature among lands so the mill puts a creature onto the battlefield.
                builder = builder.withCardInLibrary(1, "Hill Giant")
                repeat(7) { builder = builder.withCardInLibrary(1, "Forest") }
                repeat(5) { builder = builder.withCardInLibrary(2, "Forest") }
                val game = builder.build()

                game.castSpell(1, "Vastlands Scavenger")
                game.resolveStack()

                val scavenger = game.findPermanent("Vastlands Scavenger")!!
                withClue("Vastlands Scavenger enters prepared") {
                    game.state.getEntity(scavenger)?.get<PreparedComponent>() shouldNotBe null
                }

                val copyId = game.findExileCopy(1, "Vastlands Scavenger")!!
                game.execute(CastSpell(game.player1Id, copyId, faceIndex = 0))
                game.resolveStack()

                // The mandatory "put a creature card from among them" selection — pick Hill Giant.
                if (game.state.pendingDecision != null) {
                    val milledGiant = game.findCardsInGraveyard(1, "Hill Giant").firstOrNull()
                    if (milledGiant != null) game.selectCards(listOf(milledGiant))
                    game.resolveStack()
                }

                withClue("the milled creature is reanimated onto the battlefield") {
                    game.findPermanent("Hill Giant") shouldNotBe null
                }
                withClue("casting the copy unprepares Vastlands Scavenger") {
                    game.state.getEntity(scavenger)?.get<PreparedComponent>() shouldBe null
                }
            }
        }
    }
}
