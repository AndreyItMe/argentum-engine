package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.model.CreatureStats
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Ardyn, the Usurper.
 *
 * - "Demons you control have menace, lifelink, and haste." (static keyword grant)
 * - Starscourge — At the beginning of combat on your turn, exile up to one target creature
 *   card from a graveyard. If you exiled a card this way, create a token that's a copy of
 *   that card, except it's a 5/5 black Demon. (exercises the new overrideColors /
 *   overrideSubtypes fields on CreateTokenCopyOfTargetEffect)
 */
class ArdynTheUsurperScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    private fun ScenarioTestBase.TestGame.findToken(name: String): EntityId? =
        state.getBattlefield().find { entityId ->
            val container = state.getEntity(entityId) ?: return@find false
            container.has<TokenComponent>() &&
                container.get<CardComponent>()?.name == name
        }

    init {
        context("Ardyn, the Usurper") {

            test("Demons you control have menace, lifelink, and haste") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Ardyn, the Usurper")
                    .withCardOnBattlefield(1, "Grinning Demon")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val demonId = game.findPermanent("Grinning Demon")!!
                val ardynId = game.findPermanent("Ardyn, the Usurper")!!
                val projected = stateProjector.project(game.state)

                withClue("Your Demon should have menace") {
                    projected.hasKeyword(demonId, Keyword.MENACE) shouldBe true
                }
                withClue("Your Demon should have lifelink") {
                    projected.hasKeyword(demonId, Keyword.LIFELINK) shouldBe true
                }
                withClue("Your Demon should have haste") {
                    projected.hasKeyword(demonId, Keyword.HASTE) shouldBe true
                }
                withClue("Ardyn itself is not a Demon, so it should not gain menace") {
                    projected.hasKeyword(ardynId, Keyword.MENACE) shouldBe false
                }
            }

            test("Starscourge exiles a graveyard creature and makes a 5/5 black Demon copy") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Ardyn, the Usurper")
                    // Grizzly Bears (2/2 green Bear) sits in the opponent's graveyard.
                    .withCardInGraveyard(2, "Grizzly Bears")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .withPriorityPlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bearsId = game.findCardsInGraveyard(2, "Grizzly Bears").single()

                // Advance into combat — Starscourge fires and asks for a target.
                game.passUntilPhase(Phase.COMBAT, Step.BEGIN_COMBAT)
                withClue("Starscourge should ask for a graveyard target at beginning of combat") {
                    game.hasPendingDecision() shouldBe true
                }
                game.selectTargets(listOf(bearsId))
                game.resolveStack()

                withClue("Grizzly Bears should be exiled from the graveyard") {
                    game.isInGraveyard(2, "Grizzly Bears") shouldBe false
                    game.state.getExile(game.player2Id).contains(bearsId) shouldBe true
                }

                val tokenId = game.findToken("Grizzly Bears")
                withClue("A token copy of Grizzly Bears should be on the battlefield") {
                    (tokenId != null) shouldBe true
                }
                val tokenCard = game.state.getEntity(tokenId!!)!!.get<CardComponent>()!!
                withClue("Token is 5/5") { tokenCard.baseStats shouldBe CreatureStats(5, 5) }
                withClue("Token is black") { tokenCard.colors shouldBe setOf(Color.BLACK) }
                withClue("Token is a Demon") { tokenCard.typeLine.subtypes shouldBe setOf(Subtype.DEMON) }
                withClue("Token is still a creature") { tokenCard.typeLine.isCreature shouldBe true }

                // The token is a Demon you control, so Ardyn's static buffs it.
                val projected = stateProjector.project(game.state)
                withClue("Demon token gains menace from Ardyn") {
                    projected.hasKeyword(tokenId, Keyword.MENACE) shouldBe true
                }
                withClue("Demon token gains lifelink from Ardyn") {
                    projected.hasKeyword(tokenId, Keyword.LIFELINK) shouldBe true
                }
                withClue("Demon token gains haste from Ardyn") {
                    projected.hasKeyword(tokenId, Keyword.HASTE) shouldBe true
                }
            }

            test("Starscourge may exile zero cards — no token created") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Ardyn, the Usurper")
                    .withCardInGraveyard(2, "Grizzly Bears")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .withPriorityPlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.passUntilPhase(Phase.COMBAT, Step.BEGIN_COMBAT)
                withClue("Starscourge should still offer the optional target") {
                    game.hasPendingDecision() shouldBe true
                }
                game.skipTargets()
                game.resolveStack()

                withClue("Declining the target leaves the card in the graveyard") {
                    game.isInGraveyard(2, "Grizzly Bears") shouldBe true
                }
                withClue("No Demon token is created when no card is exiled") {
                    game.findToken("Grizzly Bears") shouldBe null
                }
            }
        }
    }
}
