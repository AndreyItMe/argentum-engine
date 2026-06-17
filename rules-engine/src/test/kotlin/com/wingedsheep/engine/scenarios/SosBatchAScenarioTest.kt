package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.engine.state.components.battlefield.PreparedComponent
import com.wingedsheep.engine.state.components.battlefield.PreparedSpellCopyComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for the first batch of Secrets of Strixhaven cards:
 *  - Molten Note            ({X}{R}{W} sorcery: damage = total mana spent; untap your creatures; flashback)
 *  - Colorstorm Stallion    (Opus: +1/+1 always; token copy if 5+ mana spent)
 *  - Encouraging Aviator    (becomes prepared on attack; "Jump" grants flying)
 */
class SosBatchAScenarioTest : ScenarioTestBase() {

    private val projector = StateProjector()

    private fun TestGame.findExileCopy(playerNumber: Int, name: String): EntityId? {
        val playerId = if (playerNumber == 1) player1Id else player2Id
        return state.getExile(playerId).firstOrNull { id ->
            val e = state.getEntity(id)
            e?.get<CardComponent>()?.name == name && e.get<PreparedSpellCopyComponent>() != null
        }
    }

    init {
        // -------------------------------------------------------------------
        // Molten Note
        // -------------------------------------------------------------------
        test("Molten Note deals damage equal to total mana spent and untaps your creatures") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Molten Note")
                .withCardOnBattlefield(1, "Grizzly Bears", tapped = true) // 2/2 tapped — should untap
                .withCardOnBattlefield(2, "War Behemoth") // 3/6 target — survives 5 damage
                .withLandsOnBattlefield(1, "Mountain", 4) // R + 3 generic
                .withLandsOnBattlefield(1, "Plains", 1) // W
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val bears = game.findPermanent("Grizzly Bears")!!
            val behemoth = game.findPermanent("War Behemoth")!!

            withClue("Grizzly Bears starts tapped") {
                game.state.getEntity(bears)?.get<TappedComponent>() shouldNotBe null
            }

            // X = 3 → {3}{R}{W} → 5 mana spent.
            game.castXSpell(1, "Molten Note", xValue = 3, targetId = behemoth).error shouldBe null
            game.resolveStack()

            withClue("damage equals the 5 mana spent to cast Molten Note") {
                game.state.getEntity(behemoth)?.get<DamageComponent>()?.amount shouldBe 5
            }
            withClue("all creatures you control untap") {
                game.state.getEntity(bears)?.get<TappedComponent>() shouldBe null
            }
        }

        // -------------------------------------------------------------------
        // Colorstorm Stallion
        // -------------------------------------------------------------------
        test("Colorstorm Stallion gets +1/+1 on a cheap instant/sorcery and makes no token") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Colorstorm Stallion") // 3/3
                .withCardInHand(1, "Lightning Bolt") // {R}
                .withCardOnBattlefield(2, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val stallion = game.findPermanent("Colorstorm Stallion")!!
            val bears = game.findPermanent("Grizzly Bears")!!

            game.castSpell(1, "Lightning Bolt", targetId = bears).error shouldBe null
            game.resolveStack()

            withClue("1 mana spent → +1/+1 only → 4/4") {
                projector.getProjectedPower(game.state, stallion) shouldBe 4
                projector.getProjectedToughness(game.state, stallion) shouldBe 4
            }
            withClue("no token copy below the 5-mana threshold") {
                game.findPermanents("Colorstorm Stallion").size shouldBe 1
            }
        }

        test("Colorstorm Stallion gets +1/+1 AND a token copy when 5+ mana is spent") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Colorstorm Stallion") // 3/3
                .withCardInHand(1, "Blaze") // {X}{R}
                .withCardOnBattlefield(2, "Hill Giant")
                .withLandsOnBattlefield(1, "Mountain", 5)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val stallion = game.findPermanent("Colorstorm Stallion")!!
            val giant = game.findPermanent("Hill Giant")!!

            // Blaze X=4 → {4}{R} → 5 mana spent (boundary).
            game.castXSpell(1, "Blaze", xValue = 4, targetId = giant).error shouldBe null
            game.resolveStack()

            withClue("5 mana spent → +1/+1 → 4/4 on the original") {
                projector.getProjectedPower(game.state, stallion) shouldBe 4
            }
            withClue("a token copy of Colorstorm Stallion is created (2 now exist)") {
                game.findPermanents("Colorstorm Stallion").size shouldBe 2
            }
        }

        // -------------------------------------------------------------------
        // Encouraging Aviator
        // -------------------------------------------------------------------
        test("Encouraging Aviator becomes prepared when it attacks and exposes the Jump copy") {
            var builder = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Encouraging Aviator", summoningSickness = false) // 2/3 flyer
                .withLandsOnBattlefield(1, "Island", 3)
                .withActivePlayer(1)
                .inPhase(Phase.COMBAT, Step.BEGIN_COMBAT)
            repeat(3) { builder = builder.withCardInLibrary(1, "Forest") }
            repeat(3) { builder = builder.withCardInLibrary(2, "Forest") }
            val game = builder.build()

            val aviator = game.findPermanent("Encouraging Aviator")!!
            withClue("does not start prepared") {
                game.state.getEntity(aviator)?.get<PreparedComponent>() shouldBe null
            }

            game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
            game.declareAttackers(mapOf("Encouraging Aviator" to 2)).error shouldBe null
            game.resolveStack()

            withClue("attacking makes Encouraging Aviator prepared") {
                game.state.getEntity(aviator)?.get<PreparedComponent>() shouldNotBe null
            }
            withClue("a Jump prepare-spell copy now exists in exile") {
                game.findExileCopy(1, "Encouraging Aviator") shouldNotBe null
            }
        }

        test("casting the Jump copy grants flying until end of turn, then unprepares the Aviator") {
            var builder = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Encouraging Aviator", summoningSickness = false)
                .withCardOnBattlefield(1, "Grizzly Bears") // 2/2 grounded creature to receive flying
                .withLandsOnBattlefield(1, "Island", 3)
                .withActivePlayer(1)
                .inPhase(Phase.COMBAT, Step.BEGIN_COMBAT)
            repeat(3) { builder = builder.withCardInLibrary(1, "Forest") }
            repeat(3) { builder = builder.withCardInLibrary(2, "Forest") }
            val game = builder.build()

            val aviator = game.findPermanent("Encouraging Aviator")!!
            val bears = game.findPermanent("Grizzly Bears")!!

            game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
            game.declareAttackers(mapOf("Encouraging Aviator" to 2)).error shouldBe null
            game.resolveStack()

            val copyId = game.findExileCopy(1, "Encouraging Aviator")!!
            withClue("Grizzly Bears has no flying before Jump") {
                game.state.projectedState.hasKeyword(bears, com.wingedsheep.sdk.core.Keyword.FLYING) shouldBe false
            }

            game.execute(
                CastSpell(
                    game.player1Id,
                    copyId,
                    targets = listOf(com.wingedsheep.engine.state.components.stack.ChosenTarget.Permanent(bears)),
                    faceIndex = 0
                )
            )
            game.resolveStack()

            withClue("Jump grants flying to the targeted creature") {
                game.state.projectedState.hasKeyword(bears, com.wingedsheep.sdk.core.Keyword.FLYING) shouldBe true
            }
            withClue("the Aviator is no longer prepared after casting the copy") {
                game.state.getEntity(aviator)?.get<PreparedComponent>() shouldBe null
            }
        }
    }
}
