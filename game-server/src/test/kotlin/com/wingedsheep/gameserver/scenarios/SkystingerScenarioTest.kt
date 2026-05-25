package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.core.DeclareBlockers
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.GrantKeyword
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Skystinger and the `BlockEvent.attackerFilter` extension.
 *
 * Card reference:
 * - Skystinger (2G): 3/3 Creature — Insect Warrior, Reach.
 *   Whenever this creature blocks a creature with flying, this creature gets +5/+0
 *   until end of turn.
 *
 * The mechanic under test is the new SELF-bound `BlockEvent` with `attackerFilter`:
 * fires only when the blocked attacker matches the filter, and only when the source
 * is actually blocking (not when it's an attacker being blocked).
 */
class SkystingerScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    // Anthem that grants flying to its controller's creatures via a continuous (layer)
    // effect, so flying lives in projected state rather than the attacker's base keywords.
    private val flyingAnthem = card("Test Flying Anthem") {
        manaCost = "{2}{U}"
        typeLine = "Enchantment"
        oracleText = "Creatures you control have flying."
        staticAbility {
            ability = GrantKeyword(Keyword.FLYING, GroupFilter.AllCreaturesYouControl)
        }
    }

    init {
        cardRegistry.register(flyingAnthem)

        context("Skystinger triggers when blocking a flying creature") {
            test("gets +5/+0 when blocking Wind Drake (flying)") {
                val game = scenario()
                    .withPlayers("Attacker", "Defender")
                    .withCardOnBattlefield(1, "Wind Drake")    // 2/2 flying
                    .withCardOnBattlefield(2, "Skystinger")    // 3/3 reach
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                val windDrake = game.findPermanent("Wind Drake")!!
                val skystinger = game.findPermanent("Skystinger")!!

                game.execute(DeclareAttackers(game.player1Id, mapOf(windDrake to game.player2Id)))
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
                game.execute(DeclareBlockers(game.player2Id, mapOf(skystinger to listOf(windDrake))))

                game.resolveStack()

                val projected = stateProjector.project(game.state)
                withClue("Skystinger should be 8/3 after blocking a flying creature") {
                    projected.getPower(skystinger) shouldBe 8
                    projected.getToughness(skystinger) shouldBe 3
                }
            }

            test("does NOT trigger when blocking a non-flying creature") {
                val game = scenario()
                    .withPlayers("Attacker", "Defender")
                    .withCardOnBattlefield(1, "Grizzly Bears") // 2/2 vanilla, no flying
                    .withCardOnBattlefield(2, "Skystinger")
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!
                val skystinger = game.findPermanent("Skystinger")!!

                game.execute(DeclareAttackers(game.player1Id, mapOf(bears to game.player2Id)))
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
                game.execute(DeclareBlockers(game.player2Id, mapOf(skystinger to listOf(bears))))

                game.resolveStack()

                val projected = stateProjector.project(game.state)
                withClue("Skystinger should remain 3/3 when blocking a non-flier") {
                    projected.getPower(skystinger) shouldBe 3
                    projected.getToughness(skystinger) shouldBe 3
                }
            }

            test("does NOT trigger when Skystinger is the attacker blocked by a flier") {
                // Skystinger attacks; opposing flier blocks. Per oracle text, Skystinger
                // didn't "block" the flier — it was blocked by it. No trigger.
                val game = scenario()
                    .withPlayers("Attacker", "Defender")
                    .withCardOnBattlefield(1, "Skystinger")
                    .withCardOnBattlefield(2, "Wind Drake")    // 2/2 flying defender
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                val skystinger = game.findPermanent("Skystinger")!!
                val windDrake = game.findPermanent("Wind Drake")!!

                game.execute(DeclareAttackers(game.player1Id, mapOf(skystinger to game.player2Id)))
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
                game.execute(DeclareBlockers(game.player2Id, mapOf(windDrake to listOf(skystinger))))

                game.resolveStack()

                val projected = stateProjector.project(game.state)
                withClue("Skystinger should remain 3/3 when it's the attacker, not the blocker") {
                    projected.getPower(skystinger) shouldBe 3
                    projected.getToughness(skystinger) shouldBe 3
                }
            }

            test("triggers when blocking a creature whose flying is granted by a continuous effect") {
                // Grizzly Bears has no flying in its base keywords; the anthem grants it
                // via a layer effect. The trigger must read projected keywords, not base.
                val game = scenario()
                    .withPlayers("Attacker", "Defender")
                    .withCardOnBattlefield(1, "Test Flying Anthem")
                    .withCardOnBattlefield(1, "Grizzly Bears") // 2/2 vanilla, flying only from the anthem
                    .withCardOnBattlefield(2, "Skystinger")
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!
                val skystinger = game.findPermanent("Skystinger")!!

                game.execute(DeclareAttackers(game.player1Id, mapOf(bears to game.player2Id)))
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
                game.execute(DeclareBlockers(game.player2Id, mapOf(skystinger to listOf(bears))))

                game.resolveStack()

                val projected = stateProjector.project(game.state)
                withClue("Skystinger should be 8/3 after blocking a creature with granted flying") {
                    projected.getPower(skystinger) shouldBe 8
                    projected.getToughness(skystinger) shouldBe 3
                }
            }
        }
    }
}
