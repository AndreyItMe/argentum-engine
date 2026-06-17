package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.battlefield.PreparedComponent
import com.wingedsheep.engine.state.components.battlefield.PreparedSpellCopyComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for the four Lorehold/spellslinger Secrets of Strixhaven cards added together:
 *  - Blazing Firesinger // Seething Song   (enters prepared; copy adds {R}{R}{R}{R}{R})
 *  - Strife Scholar // Awaken the Ages      (enters prepared + Ward—Pay 2 life; copy makes 2 Spirits)
 *  - Elite Interceptor // Rejoinder         (enters prepared; copy: may tap/untap a creature, draw)
 *  - Thunderdrum Soloist                    (Opus — spell-cast trigger, 1 dmg, 3 instead at 5+ mana)
 *
 * The three prepare cards reuse the existing PREPARE layout + `prepare(name) { }` DSL; this file
 * pins each prepare spell's resolution behaviour and the Opus instead-tier boundary. No new SDK
 * was introduced, so these are pure card-behaviour tests.
 */
class SosLoreholdSpellslingerScenarioTest : ScenarioTestBase() {

    private fun TestGame.findExileCopy(playerNumber: Int, name: String): EntityId? {
        val playerId = if (playerNumber == 1) player1Id else player2Id
        return state.getExile(playerId).firstOrNull { id ->
            val e = state.getEntity(id)
            e?.get<CardComponent>()?.name == name && e.get<PreparedSpellCopyComponent>() != null
        }
    }

    private fun TestGame.redMana(playerNumber: Int): Int {
        val playerId = if (playerNumber == 1) player1Id else player2Id
        return state.getEntity(playerId)?.get<ManaPoolComponent>()?.getAmount(Color.RED) ?: 0
    }

    init {
        context("Blazing Firesinger — Seething Song (enters prepared)") {
            test("enters prepared; casting the copy adds five red mana") {
                var builder = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Blazing Firesinger")
                    .withLandsOnBattlefield(1, "Mountain", 6)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                repeat(5) { builder = builder.withCardInLibrary(1, "Forest") }
                repeat(5) { builder = builder.withCardInLibrary(2, "Forest") }
                val game = builder.build()

                game.castSpell(1, "Blazing Firesinger")
                game.resolveStack()

                val firesinger = game.findPermanent("Blazing Firesinger")!!
                withClue("Blazing Firesinger should be prepared on ETB") {
                    game.state.getEntity(firesinger)?.get<PreparedComponent>() shouldNotBe null
                }

                val copyId = game.findExileCopy(1, "Blazing Firesinger")!!
                // Cast the Seething Song copy for {2}{R}.
                game.execute(CastSpell(game.player1Id, copyId, faceIndex = 0))
                game.resolveStack()

                withClue("Seething Song adds {R}{R}{R}{R}{R} to the pool") {
                    game.redMana(1) shouldBe 5
                }
                withClue("Blazing Firesinger is no longer prepared after casting the copy") {
                    game.state.getEntity(firesinger)?.get<PreparedComponent>() shouldBe null
                }
            }
        }

        context("Strife Scholar — Awaken the Ages (enters prepared + Ward)") {
            test("enters prepared; casting the copy creates two 2/2 red-and-white Spirit tokens") {
                var builder = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Strife Scholar")
                    .withLandsOnBattlefield(1, "Mountain", 10)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                repeat(5) { builder = builder.withCardInLibrary(1, "Forest") }
                repeat(5) { builder = builder.withCardInLibrary(2, "Forest") }
                val game = builder.build()

                game.castSpell(1, "Strife Scholar")
                game.resolveStack()

                val scholar = game.findPermanent("Strife Scholar")!!
                withClue("Strife Scholar should be prepared on ETB") {
                    game.state.getEntity(scholar)?.get<PreparedComponent>() shouldNotBe null
                }

                val spiritsBefore = game.findPermanents("Spirit Token").size
                val copyId = game.findExileCopy(1, "Strife Scholar")!!
                // Cast the Awaken the Ages copy for {5}{R}.
                val castResult = game.execute(CastSpell(game.player1Id, copyId, faceIndex = 0))
                withClue("Casting the Awaken the Ages copy should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                withClue("Awaken the Ages creates two Spirit tokens") {
                    game.findPermanents("Spirit Token").size shouldBe spiritsBefore + 2
                }
                withClue("Strife Scholar is no longer prepared after casting the copy") {
                    game.state.getEntity(scholar)?.get<PreparedComponent>() shouldBe null
                }
            }
        }

        context("Elite Interceptor — Rejoinder (enters prepared)") {
            test("enters prepared; the copy is offered as a {1}{W} cast of face 0 from exile") {
                var builder = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Elite Interceptor")
                    .withLandsOnBattlefield(1, "Plains", 4)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                repeat(5) { builder = builder.withCardInLibrary(1, "Forest") }
                repeat(5) { builder = builder.withCardInLibrary(2, "Forest") }
                val game = builder.build()

                game.castSpell(1, "Elite Interceptor")
                game.resolveStack()

                val interceptor = game.findPermanent("Elite Interceptor")!!
                withClue("Elite Interceptor should be prepared on ETB") {
                    game.state.getEntity(interceptor)?.get<PreparedComponent>() shouldNotBe null
                }

                val copyId = game.findExileCopy(1, "Elite Interceptor")!!
                val prepareAction = game.getLegalActions(1).firstOrNull { la ->
                    val a = la.action
                    a is CastSpell && a.cardId == copyId
                }
                withClue("Rejoinder should be offered as a {1}{W} cast of face 0 from exile") {
                    prepareAction shouldNotBe null
                    (prepareAction!!.action as CastSpell).faceIndex shouldBe 0
                    prepareAction.sourceZone shouldBe "EXILE"
                    prepareAction.manaCostString shouldBe "{1}{W}"
                }
            }
        }

        context("Thunderdrum Soloist — Opus") {
            test("a 1-mana instant deals 1 damage to each opponent (5+ tier not reached)") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Thunderdrum Soloist") // 1/3
                    .withCardInHand(1, "Lightning Bolt") // {R}
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withLandsOnBattlefield(1, "Mountain", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!
                val oppLifeBefore = game.getLifeTotal(2)

                game.castSpell(1, "Lightning Bolt", targetId = bears).error shouldBe null
                game.resolveStack()

                withClue("Opus deals 1 damage to the opponent at <5 mana") {
                    game.getLifeTotal(2) shouldBe oppLifeBefore - 1
                }
            }

            test("a 5-mana spell makes Opus deal 3 damage to each opponent instead") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Thunderdrum Soloist") // 1/3
                    .withCardInHand(1, "Blaze") // {X}{R}
                    .withLandsOnBattlefield(1, "Mountain", 7)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val oppLifeBefore = game.getLifeTotal(2)

                // Blaze X=4 → {4}{R} → 5 mana spent → Opus deals 3 (instead of 1).
                // Blaze targets the opponent (X=4 damage), so the opponent loses 4 (Blaze) + 3 (Opus) = 7.
                game.execute(
                    CastSpell(
                        game.player1Id,
                        game.state.getHand(game.player1Id).first {
                            game.state.getEntity(it)?.get<CardComponent>()?.name == "Blaze"
                        },
                        targets = listOf(ChosenTarget.Player(game.player2Id)),
                        xValue = 4
                    )
                ).error shouldBe null
                game.resolveStack()

                withClue("5 mana spent → Opus deals 3 (not 1); plus Blaze X=4 → opponent loses 7") {
                    oppLifeBefore - game.getLifeTotal(2) shouldBe 7
                }
            }
        }
    }
}
