package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario test for Broodguard Elite's signature interaction: cast via warp ({X}{G}),
 * it enters with X +1/+1 counters, gets exiled at the beginning of the next end step,
 * and its "When this creature leaves the battlefield, put its counters on target
 * creature you control" ability moves those counters to a creature you control.
 *
 * The dies-variant of the counter-move is covered by [EssenceChannelerScenarioTest];
 * this exercises the distinct non-death leave (warp's end-step exile) end to end —
 * the warp cost carrying {X}, the delayed warp-exile trigger, and the leaves-the-
 * battlefield trigger reading last-known counters — which no single existing test covers.
 *
 * Per the EOE ruling (2025-07-25): the ability puts the same number of each kind of
 * counter Broodguard Elite had when it left the battlefield onto the target creature.
 */
class BroodguardEliteScenarioTest : ScenarioTestBase() {

    init {
        test("warped Broodguard Elite moves its X +1/+1 counters to a creature you control when exiled at end step") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Broodguard Elite")
                // The "creature you control" the leaves-the-battlefield ability will target.
                .withCardOnBattlefield(1, "Grizzly Bears")
                // Warp {X}{G} with X = 2 costs {2}{G}; three Forests cover it (AutoPay taps them).
                .withLandsOnBattlefield(1, "Forest", 3)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val grizzlyBears = game.findPermanent("Grizzly Bears")
                ?: error("Grizzly Bears should be on the battlefield")

            // Cast via warp with X = 2. There is no by-name warp+X helper, so build the action
            // directly: useAlternativeCost routes to the warp cost, and xValue feeds both the
            // {X} in {X}{G} and EntersWithDynamicCounters(XValue).
            val broodguardId = game.findCardsInHand(1, "Broodguard Elite").firstOrNull()
                ?: error("Broodguard Elite should be in hand")
            val castResult = game.execute(
                CastSpell(
                    playerId = game.player1Id,
                    cardId = broodguardId,
                    xValue = 2,
                    useAlternativeCost = true
                )
            )
            withClue("Warp cast should succeed: ${castResult.error}") {
                castResult.error shouldBe null
            }
            game.resolveStack()

            // Checkpoint: it entered with X = 2 +1/+1 counters (warp's {X} reached the counters).
            val onBattlefield = game.findPermanent("Broodguard Elite")
                ?: error("Broodguard Elite should be on the battlefield after resolving")
            withClue("Broodguard Elite should enter with 2 +1/+1 counters from X = 2") {
                game.state.getEntity(onBattlefield)?.get<CountersComponent>()
                    ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 2
            }

            // Advance to the end step. The warp delayed trigger ("exile at the beginning of the
            // next end step") is queued on the stack but not yet resolved when this returns.
            game.passUntilPhase(Phase.ENDING, Step.END)

            // Resolve the warp-exile trigger. Exiling Broodguard is a leaves-the-battlefield
            // event, so its second ability triggers and pauses for a target.
            game.resolveStack()
            withClue("Broodguard Elite should be exiled by warp, not still on the battlefield") {
                game.isOnBattlefield("Broodguard Elite") shouldBe false
            }
            withClue("Broodguard Elite should be in exile (warp), not the graveyard") {
                game.state.getExile(game.player1Id).any { entityId ->
                    game.state.getEntity(entityId)
                        ?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()
                        ?.name == "Broodguard Elite"
                } shouldBe true
            }

            // The leaves-the-battlefield trigger targets the only creature Player 1 controls.
            game.selectTargets(listOf(grizzlyBears))
            game.resolveStack()

            // The 2 +1/+1 counters Broodguard Elite had when it left are placed on Grizzly Bears.
            val counters = game.state.getEntity(grizzlyBears)?.get<CountersComponent>()
                ?: error("Grizzly Bears should have counters after the trigger resolves")
            withClue("Grizzly Bears should receive the 2 +1/+1 counters from Broodguard Elite") {
                counters.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 2
            }
        }
    }
}
