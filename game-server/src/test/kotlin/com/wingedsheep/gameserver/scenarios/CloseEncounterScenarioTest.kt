package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.WarpExiledComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.permissions.MayPlayPermission
import com.wingedsheep.engine.state.permissions.addMayPlayPermission
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for Close Encounter (Edge of Eternities).
 *
 * {1}{G} Instant
 *   As an additional cost to cast this spell, choose a creature you control or
 *   a warped creature card you own in exile.
 *   Close Encounter deals damage equal to the power of the chosen creature or
 *   card to target creature.
 *
 * Exercises the new
 * [com.wingedsheep.sdk.scripting.AdditionalCost.ChooseCreatureOrWarpedExile]
 * cost and the [com.wingedsheep.sdk.scripting.values.DynamicAmount.StoredCardPower]
 * dynamic amount, including the LKI snapshot path documented in the printed
 * ruling.
 */
class CloseEncounterScenarioTest : ScenarioTestBase() {

    init {
        context("Choose a creature you control on the battlefield") {
            test("deals damage equal to chosen creature's power to target creature") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Close Encounter")
                    .withCardOnBattlefield(1, "Python")            // 3/2, the chosen one
                    .withCardOnBattlefield(2, "Whiptail Wurm")         // 8/5, the damaged target
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val pythonId = game.findPermanent("Python")!!
                val wurmId = game.findPermanent("Whiptail Wurm")!!
                val closeEncounterId = game.findCardsInHand(1, "Close Encounter").first()

                val castResult = game.execute(
                    CastSpell(
                        playerId = game.player1Id,
                        cardId = closeEncounterId,
                        targets = listOf(ChosenTarget.Permanent(wurmId)),
                        additionalCostPayment = AdditionalCostPayment(
                            beheldCards = listOf(pythonId)
                        )
                    )
                )
                withClue("Cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }

                game.resolveStack()

                withClue("Whiptail Wurm should have 3 damage marked (Python's power)") {
                    game.state.getEntity(wurmId)?.get<DamageComponent>()?.amount shouldBe 3
                }

                withClue("Whiptail Wurm (5 toughness) should still be on the battlefield") {
                    game.findPermanent("Whiptail Wurm") shouldBe wurmId
                }

                withClue("Python should still be on the battlefield — choosing doesn't move it") {
                    game.findPermanent("Python") shouldBe pythonId
                }
            }

            test("uses last-known power when chosen creature leaves the battlefield before resolution") {
                // Ruling: "If that creature is no longer on the battlefield when Close
                // Encounter resolves, use that creature's power as it last existed on the
                // battlefield to determine how much damage is dealt." (Capture a snapshot
                // at cost-pay time and read from it when the live entity is gone.)
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Close Encounter")
                    .withCardOnBattlefield(1, "Python")           // 3/2 — chosen, then exiled
                    .withCardOnBattlefield(2, "Whiptail Wurm")        // 8/5 — damaged target
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val pythonId = game.findPermanent("Python")!!
                val wurmId = game.findPermanent("Whiptail Wurm")!!
                val closeEncounterId = game.findCardsInHand(1, "Close Encounter").first()

                val castResult = game.execute(
                    CastSpell(
                        playerId = game.player1Id,
                        cardId = closeEncounterId,
                        targets = listOf(ChosenTarget.Permanent(wurmId)),
                        additionalCostPayment = AdditionalCostPayment(
                            beheldCards = listOf(pythonId)
                        )
                    )
                )
                castResult.error shouldBe null

                // Simulate the chosen creature leaving the battlefield between cost-pay and
                // resolution by moving it directly to the graveyard (an arbitrary response
                // like Doom Blade resolving in between). Close Encounter is still on the stack.
                val controllerZone = ZoneKey(game.player1Id, Zone.BATTLEFIELD)
                val graveyardZone = ZoneKey(game.player1Id, Zone.GRAVEYARD)
                game.state = game.state
                    .removeFromZone(controllerZone, pythonId)
                    .addToZone(graveyardZone, pythonId)

                game.findPermanent("Python") shouldBe null

                game.resolveStack()

                withClue("Damage should reflect Python's LKI power (3), not 0") {
                    game.state.getEntity(wurmId)?.get<DamageComponent>()?.amount shouldBe 3
                }
            }
        }

        context("Choose a warped creature card you own in exile") {
            test("deals damage equal to chosen card's printed power to target creature") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Close Encounter")
                    .withCardInExile(1, "Weftstalker Ardent")          // 2/3, warped in exile
                    .withCardOnBattlefield(2, "Whiptail Wurm")         // 8/5, damaged target
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Mark the exiled card as a warped exiled card (CR 702.185b). In real play
                // this happens automatically when the warp end-step trigger exiles a warped
                // permanent; here we set it up directly.
                val exiledCardId = game.state.getExile(game.player1Id).first { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Weftstalker Ardent"
                }
                game.state = game.state.updateEntity(exiledCardId) { c ->
                    c.with(WarpExiledComponent(controllerId = game.player1Id))
                }
                // The card must also be castable from exile for the warp loop to be
                // realistic, though Close Encounter doesn't actually require this — it
                // only inspects the WarpExiledComponent marker.
                game.state = game.state.addMayPlayPermission(
                    MayPlayPermission(
                        id = EntityId.generate(),
                        cardIds = setOf(exiledCardId),
                        controllerId = game.player1Id,
                        sourceId = exiledCardId,
                        permanent = true,
                        timestamp = game.state.timestamp,
                    )
                )

                val wurmId = game.findPermanent("Whiptail Wurm")!!
                val closeEncounterId = game.findCardsInHand(1, "Close Encounter").first()

                val castResult = game.execute(
                    CastSpell(
                        playerId = game.player1Id,
                        cardId = closeEncounterId,
                        targets = listOf(ChosenTarget.Permanent(wurmId)),
                        additionalCostPayment = AdditionalCostPayment(
                            beheldCards = listOf(exiledCardId)
                        )
                    )
                )
                withClue("Cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }

                game.resolveStack()

                withClue("Whiptail Wurm should have 2 damage marked (Weftstalker Ardent's printed power)") {
                    game.state.getEntity(wurmId)?.get<DamageComponent>()?.amount shouldBe 2
                }

                withClue("Weftstalker Ardent should still be in exile — choosing doesn't move it") {
                    game.state.getExile(game.player1Id) shouldNotBe emptyList<EntityId>()
                    game.state.getExile(game.player1Id).contains(exiledCardId) shouldBe true
                }
            }

            test("does NOT accept a non-warped exiled creature card") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Close Encounter")
                    .withCardInExile(1, "Weftstalker Ardent")          // exiled but NOT marked as warped
                    .withCardOnBattlefield(2, "Whiptail Wurm")
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val exiledCardId = game.state.getExile(game.player1Id).first { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Weftstalker Ardent"
                }
                val wurmId = game.findPermanent("Whiptail Wurm")!!
                val closeEncounterId = game.findCardsInHand(1, "Close Encounter").first()

                val castResult = game.execute(
                    CastSpell(
                        playerId = game.player1Id,
                        cardId = closeEncounterId,
                        targets = listOf(ChosenTarget.Permanent(wurmId)),
                        additionalCostPayment = AdditionalCostPayment(
                            beheldCards = listOf(exiledCardId)
                        )
                    )
                )

                withClue("Cast must fail — an exiled creature card without WarpExiledComponent is not 'warped'") {
                    castResult.error shouldNotBe null
                }
            }
        }

        context("Cast validation") {
            test("must choose an entity — empty beheldCards is rejected") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Close Encounter")
                    .withCardOnBattlefield(1, "Python")
                    .withCardOnBattlefield(2, "Whiptail Wurm")
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val wurmId = game.findPermanent("Whiptail Wurm")!!
                val closeEncounterId = game.findCardsInHand(1, "Close Encounter").first()

                val castResult = game.execute(
                    CastSpell(
                        playerId = game.player1Id,
                        cardId = closeEncounterId,
                        targets = listOf(ChosenTarget.Permanent(wurmId)),
                        additionalCostPayment = AdditionalCostPayment(beheldCards = emptyList())
                    )
                )

                withClue("Cast without choosing must fail") {
                    castResult.error shouldNotBe null
                }
            }

            test("cannot choose a creature controlled by the opponent") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Close Encounter")
                    .withCardOnBattlefield(2, "Python")             // opponent's creature
                    .withCardOnBattlefield(2, "Whiptail Wurm")
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val opponentPythonId = game.findPermanent("Python")!!
                val wurmId = game.findPermanent("Whiptail Wurm")!!
                val closeEncounterId = game.findCardsInHand(1, "Close Encounter").first()

                val castResult = game.execute(
                    CastSpell(
                        playerId = game.player1Id,
                        cardId = closeEncounterId,
                        targets = listOf(ChosenTarget.Permanent(wurmId)),
                        additionalCostPayment = AdditionalCostPayment(
                            beheldCards = listOf(opponentPythonId)
                        )
                    )
                )

                withClue("Cast with an opponent's creature as the chosen entity must fail") {
                    castResult.error shouldNotBe null
                }
            }
        }
    }
}
