package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.MayPlayFromExileComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.effects.ManaRestriction
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for Interdimensional Web Watch.
 *
 * Card reference (SPM):
 * "When Interdimensional Web Watch enters, exile the top two cards of your library.
 *  You may play those cards until the end of your next turn."
 * (Activated tap ability for mana spendable only on spells from exile handled separately.)
 */
class InterdimensionalWebWatchScenarioTest : ScenarioTestBase() {

    init {
        context("Interdimensional Web Watch — tap for restricted mana") {

            test("tap ability adds two mana of any color restricted to casting spells from exile") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardOnBattlefield(1, "Interdimensional Web Watch")
                    .withCardInExile(1, "Grizzly Bears")
                    .withCardInExile(1, "Grizzly Bears")
                    .withCardInHand(1, "Glory Seeker")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val watchId = game.findPermanent("Interdimensional Web Watch")!!
                val cardDef = cardRegistry.getCard("Interdimensional Web Watch")!!
                val manaAbility = cardDef.script.activatedAbilities.first()

                val activateResult = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = watchId,
                        abilityId = manaAbility.id
                    )
                )
                withClue("Tap ability should activate successfully: ${activateResult.error}") {
                    activateResult.error shouldBe null
                }

                withClue("Interdimensional Web Watch should be tapped after activation") {
                    game.state.getEntity(watchId)?.has<TappedComponent>() shouldBe true
                }

                val manaPool = game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()
                withClue("Mana pool should contain two restricted mana entries") {
                    manaPool?.restrictedMana?.size shouldBe 2
                }

                val restriction = manaPool?.restrictedMana?.firstOrNull()?.restriction
                withClue("Restricted mana should be limited to casting spells from exile") {
                    restriction shouldBe ManaRestriction.CastFromExileOnly
                }
            }
        }

        context("Interdimensional Web Watch — play permission expiry") {

            test("play permission granted by ETB expires at end of controller's next turn") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Interdimensional Web Watch")
                    .withCardInLibrary(1, "Grizzly Bears")
                    .withCardInLibrary(1, "Grizzly Bears")
                    .withLandsOnBattlefield(1, "Mountain", 4)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withCardInLibrary(2, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val castResult = game.castSpell(1, "Interdimensional Web Watch")
                withClue("Casting should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                val exile = game.state.getExile(game.player1Id)
                withClue("ETB should exile two cards") { exile.size shouldBe 2 }

                val exiledId = exile.first()
                withClue("Permission should be active immediately after ETB") {
                    game.state.getEntity(exiledId)?.get<MayPlayFromExileComponent>() shouldNotBe null
                }

                // Step through two full turns using alternating waypoints so no call is a no-op.
                // [1] no-op (already at PRECOMBAT_MAIN)
                game.passUntilPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                // [2] P1's PRECOMBAT_MAIN → P2's UPKEEP (through P1's end + cleanup round 1)
                game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP)
                // [3] P2's UPKEEP → P2's PRECOMBAT_MAIN (through P2's draw)
                game.passUntilPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                // [4] P2's PRECOMBAT_MAIN → P1's UPKEEP (through P2's end + cleanup round 1, P1's untap round 2)
                game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP)
                // [5] P1's UPKEEP → P1's PRECOMBAT_MAIN (turn 2, through P1's draw)
                game.passUntilPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)

                withClue("Permission should still be active during controller's next turn") {
                    game.state.getEntity(exiledId)?.get<MayPlayFromExileComponent>() shouldNotBe null
                }

                // [6] P1's PRECOMBAT_MAIN (turn 2) → P2's UPKEEP (through P1's end + cleanup round 2)
                //     P1's cleanup runs with turnNumber=2, expiresAfterTurn=2 → permission removed
                game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP)

                withClue("Permission must be removed after the controller's next turn ends") {
                    game.state.getEntity(exiledId)?.get<MayPlayFromExileComponent>() shouldBe null
                }
            }
        }

        context("Interdimensional Web Watch — ETB exile and play permission") {

            test("entering the battlefield exiles the top two library cards and grants play permission until end of controller's next turn") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Interdimensional Web Watch")
                    .withCardInLibrary(1, "Grizzly Bears")
                    .withCardInLibrary(1, "Grizzly Bears")
                    .withLandsOnBattlefield(1, "Mountain", 4)
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val initialLibrarySize = game.librarySize(1)

                val castResult = game.castSpell(1, "Interdimensional Web Watch")
                withClue("Casting Interdimensional Web Watch should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                withClue("Interdimensional Web Watch should be on the battlefield") {
                    game.isOnBattlefield("Interdimensional Web Watch") shouldBe true
                }
                withClue("Library should have two fewer cards after the ETB exile") {
                    game.librarySize(1) shouldBe initialLibrarySize - 2
                }

                val exile = game.state.getExile(game.player1Id)
                withClue("Two cards should be in exile after ETB resolves") {
                    exile.size shouldBe 2
                }

                exile.forEach { cardId ->
                    val mayPlay = game.state.getEntity(cardId)?.get<MayPlayFromExileComponent>()
                    val cardName = game.state.getEntity(cardId)?.get<CardComponent>()?.name
                    withClue("Exiled card '$cardName' should be tagged with play permission for the controller") {
                        mayPlay shouldNotBe null
                    }
                    withClue("Play permission on '$cardName' should be granted to player 1 (the controller)") {
                        mayPlay!!.controllerId shouldBe game.player1Id
                    }
                }
            }
        }
    }
}
