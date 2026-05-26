package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.model.CardDefinition
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Teeka's Dragon (Mirage).
 *
 * Teeka's Dragon: {9}
 * Artifact Creature — Dragon
 * 5/5
 * Flying; trample; rampage 4 (Whenever this creature becomes blocked, it gets +4/+4
 * until end of turn for each creature blocking it beyond the first.)
 *
 * Rampage fires once when the creature becomes blocked, scaling with the number of
 * blockers beyond the first — so a single blocker grants nothing, and the buff does
 * NOT compound per blocker (which would happen if the trigger fired once per blocker).
 * Blockers carry flying here so they can legally block the flying Dragon.
 */
class TeekasDragonScenarioTest : ScenarioTestBase() {

    private val projector = StateProjector()

    // Inline flying blockers — flying so they can block the Dragon; vanilla otherwise.
    private val skyBlocker = CardDefinition.creature(
        name = "Sky Blocker",
        manaCost = ManaCost.parse("{1}{W}"),
        subtypes = setOf(Subtype("Bird")),
        power = 1, toughness = 1,
        keywords = setOf(Keyword.FLYING)
    )

    init {
        cardRegistry.register(skyBlocker)

        context("Teeka's Dragon rampage 4") {

            test("stays 5/5 when blocked by a single creature (no extra blockers)") {
                val game = scenario()
                    .withPlayers("Attacker", "Defender")
                    .withCardOnBattlefield(1, "Teeka's Dragon")
                    .withCardOnBattlefield(2, "Sky Blocker")
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                val dragonId = game.findPermanent("Teeka's Dragon")!!
                val blockerId = game.findPermanent("Sky Blocker")!!

                game.execute(DeclareAttackers(game.player1Id, mapOf(dragonId to game.player2Id)))
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
                game.execute(DeclareBlockers(game.player2Id, mapOf(blockerId to listOf(dragonId))))
                game.resolveStack()

                val projected = projector.project(game.state)
                withClue("One blocker => +0/+0, Dragon stays 5/5") {
                    projected.getPower(dragonId) shouldBe 5
                    projected.getToughness(dragonId) shouldBe 5
                }
            }

            test("gets +4/+4 when blocked by two creatures") {
                val game = scenario()
                    .withPlayers("Attacker", "Defender")
                    .withCardOnBattlefield(1, "Teeka's Dragon")
                    .withCardOnBattlefield(2, "Sky Blocker")
                    .withCardOnBattlefield(2, "Sky Blocker")
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                val dragonId = game.findPermanent("Teeka's Dragon")!!
                val blockerIds = game.findPermanents("Sky Blocker")
                blockerIds.size shouldBe 2

                game.execute(DeclareAttackers(game.player1Id, mapOf(dragonId to game.player2Id)))
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
                game.execute(DeclareBlockers(game.player2Id, blockerIds.associateWith { listOf(dragonId) }))
                game.resolveStack()

                val projected = projector.project(game.state)
                withClue("Two blockers => +4/+4 once (5/5 -> 9/9), not compounded per blocker") {
                    projected.getPower(dragonId) shouldBe 9
                    projected.getToughness(dragonId) shouldBe 9
                }
            }

            test("gets +8/+8 when blocked by three creatures") {
                val game = scenario()
                    .withPlayers("Attacker", "Defender")
                    .withCardOnBattlefield(1, "Teeka's Dragon")
                    .withCardOnBattlefield(2, "Sky Blocker")
                    .withCardOnBattlefield(2, "Sky Blocker")
                    .withCardOnBattlefield(2, "Sky Blocker")
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                val dragonId = game.findPermanent("Teeka's Dragon")!!
                val blockerIds = game.findPermanents("Sky Blocker")
                blockerIds.size shouldBe 3

                game.execute(DeclareAttackers(game.player1Id, mapOf(dragonId to game.player2Id)))
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
                game.execute(DeclareBlockers(game.player2Id, blockerIds.associateWith { listOf(dragonId) }))
                game.resolveStack()

                val projected = projector.project(game.state)
                withClue("Three blockers => +8/+8 once (5/5 -> 13/13)") {
                    projected.getPower(dragonId) shouldBe 13
                    projected.getToughness(dragonId) shouldBe 13
                }
            }
        }
    }
}
