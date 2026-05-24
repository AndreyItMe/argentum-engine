package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.SelectManaSourcesDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Scenario tests for Divert Disaster.
 *
 * Divert Disaster: {1}{U}
 * Instant
 * Counter target spell unless its controller pays {2}. If they do, you create a Lander token.
 *
 * Exercises the new onPaid rider on CounterCondition.UnlessPaysMana: the Lander is created
 * only on the "they paid" branch.
 */
class DivertDisasterScenarioTest : ScenarioTestBase() {

    init {
        context("Divert Disaster onPaid rider") {

            test("when opponent pays, spell resolves and caster gets a Lander token") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Divert Disaster")
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withCardInHand(2, "Shock")
                    .withLandsOnBattlefield(2, "Mountain", 3) // {R} to cast bolt + {2} to pay
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Player 2 casts Shock targeting Player 1
                val boltCast = game.castSpellTargetingPlayer(2, "Shock", targetPlayerNumber = 1)
                withClue("Shock should cast: ${boltCast.error}") {
                    boltCast.error shouldBe null
                }
                game.passPriority() // Player 2 passes; Player 1 can respond

                // Player 1 responds with Divert Disaster targeting Shock
                val divertCast = game.castSpellTargetingStackSpell(1, "Divert Disaster", "Shock")
                withClue("Divert Disaster should cast: ${divertCast.error}") {
                    divertCast.error shouldBe null
                }

                // Drain stack until Divert Disaster resolves and pauses for Player 2's pay decision
                game.resolveStack()
                game.hasPendingDecision() shouldBe true
                game.getPendingDecision().shouldNotBeNull()
                game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>()
                (game.getPendingDecision() as YesNoDecision).playerId shouldBe game.player2Id

                // Player 2 chooses to pay {2}
                game.answerYesNo(true)

                // Mana-source selection appears for Player 2 (the {2} pay)
                game.hasPendingDecision() shouldBe true
                game.getPendingDecision().shouldBeInstanceOf<SelectManaSourcesDecision>()
                game.submitManaSourcesAutoPay()

                // Shock resolves: Player 1 takes 2 damage.
                game.resolveStack()
                game.getLifeTotal(1) shouldBe 18

                // The "If they do, you create a Lander token" rider should have fired
                // for Player 1 (controller of Divert Disaster).
                withClue("Player 1 should now control a Lander token") {
                    game.isOnBattlefield("Lander") shouldBe true
                }

                // Divert Disaster itself is in Player 1's graveyard (it resolved).
                game.isInGraveyard(1, "Divert Disaster") shouldBe true
            }

            test("when opponent declines, spell is countered and no Lander is created") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Divert Disaster")
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withCardInHand(2, "Shock")
                    .withLandsOnBattlefield(2, "Mountain", 3) // could pay, but chooses not to
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpellTargetingPlayer(2, "Shock", targetPlayerNumber = 1)
                game.passPriority()
                game.castSpellTargetingStackSpell(1, "Divert Disaster", "Shock")
                game.resolveStack()

                game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>()
                game.answerYesNo(false) // decline payment

                // Continue resolution after the decision.
                game.resolveStack()

                // Shock is countered → in Player 2's graveyard, no damage taken.
                game.isInGraveyard(2, "Shock") shouldBe true
                game.getLifeTotal(1) shouldBe 20

                // Rider must NOT fire on the countered branch.
                withClue("Lander must not be created when the spell is countered") {
                    game.isOnBattlefield("Lander") shouldBe false
                }

                game.isInGraveyard(1, "Divert Disaster") shouldBe true
            }

            test("opponent pays via Springleaf Drum's tap-permanents sub-cost, rider still fires") {
                // Springleaf Drum ({T}, Tap an untapped creature you control: Add one mana of
                // any color) is a mana source with a tap-permanents sub-cost. Paying the {2}
                // through it routes the rider through WardTapPermanentsSubCostContinuation —
                // the path that previously bailed. The Lander must still be created for the
                // Divert Disaster caster once payment completes.
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Divert Disaster")
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withCardInHand(2, "Shock")
                    .withLandsOnBattlefield(2, "Mountain", 2) // one for Shock's {R}, one for {1} of {2}
                    .withCardOnBattlefield(2, "Springleaf Drum")
                    .withCardOnBattlefield(2, "Grizzly Bears") // tapped to satisfy the Drum's sub-cost
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val drumId = game.findPermanent("Springleaf Drum").shouldNotBeNull()
                val bearsId = game.findPermanent("Grizzly Bears").shouldNotBeNull()

                game.castSpellTargetingPlayer(2, "Shock", targetPlayerNumber = 1)
                game.passPriority()
                game.castSpellTargetingStackSpell(1, "Divert Disaster", "Shock")
                game.resolveStack()

                game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>()
                game.answerYesNo(true)

                // Pay {2} by manually picking the Drum plus one land (auto-pay never selects
                // sub-cost sources). The Drum carries requiresTappingAnotherPermanent.
                val manaDecision = game.getPendingDecision().shouldBeInstanceOf<SelectManaSourcesDecision>()
                val drumSource = manaDecision.availableSources.first { it.entityId == drumId }
                drumSource.requiresTappingAnotherPermanent shouldBe true
                val landSource = manaDecision.availableSources.first { !it.requiresTappingAnotherPermanent }
                game.submitManaSourcesDecision(listOf(landSource.entityId, drumSource.entityId), autoPay = false)

                // The Drum's sub-cost prompts for an untapped creature to tap.
                val tapPrompt = game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
                tapPrompt.options shouldContain bearsId
                game.selectCards(listOf(bearsId))

                // Shock resolves: Player 1 takes 2 damage.
                game.resolveStack()
                game.getLifeTotal(1) shouldBe 18

                // The rider fired despite the payment running through the sub-cost path, and
                // the Lander is controlled by Player 1 (the counter's caster — "you").
                val landerId = game.findPermanent("Lander").shouldNotBeNull()
                game.state.getEntity(landerId)?.get<ControllerComponent>()?.playerId shouldBe game.player1Id

                // Drum and the tapped creature are both tapped; Divert Disaster resolved.
                game.isInGraveyard(1, "Divert Disaster") shouldBe true
            }
        }
    }
}
