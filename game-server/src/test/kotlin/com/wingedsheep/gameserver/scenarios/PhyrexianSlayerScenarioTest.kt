package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.core.DeclareBlockers
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
 * Scenario tests for Phyrexian Slayer.
 *
 * {3}{B} 2/2 Phyrexian Minion
 * Flying
 * Whenever this creature becomes blocked by a white creature, destroy that creature. It can't be regenerated.
 *
 * Reuses the becomes-blocked SELF trigger (filtered by white creature), exposing the
 * blocker as the triggering entity — same pattern as Phyrexian Reaper (green variant).
 */
class PhyrexianSlayerScenarioTest : ScenarioTestBase() {

    // Inline test creatures — avoids depending on specific set cards being registered.
    // Reach lets them block the flying Slayer; color is derived from the mana cost.
    private val whiteBlocker = CardDefinition.creature(
        name = "White Vanilla Reach",
        manaCost = ManaCost.parse("{2}{W}"),
        subtypes = setOf(Subtype("Human")),
        power = 2, toughness = 3,
        keywords = setOf(Keyword.REACH),
    )

    private val greenBlocker = CardDefinition.creature(
        name = "Green Vanilla Reach",
        manaCost = ManaCost.parse("{2}{G}"),
        subtypes = setOf(Subtype("Beast")),
        power = 2, toughness = 3,
        keywords = setOf(Keyword.REACH),
    )

    init {
        cardRegistry.register(whiteBlocker)
        cardRegistry.register(greenBlocker)

        context("Phyrexian Slayer becomes-blocked trigger") {

            test("destroys a white creature that blocks it") {
                val game = scenario()
                    .withPlayers("Attacker", "Defender")
                    .withCardOnBattlefield(1, "Phyrexian Slayer") // 2/2 flyer
                    .withCardOnBattlefield(2, "White Vanilla Reach") // white blocker
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                val slayerId = game.findPermanent("Phyrexian Slayer")!!
                val blockerId = game.findPermanent("White Vanilla Reach")!!

                game.execute(DeclareAttackers(game.player1Id, mapOf(slayerId to game.player2Id)))
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)

                val blockResult = game.execute(
                    DeclareBlockers(game.player2Id, mapOf(blockerId to listOf(slayerId)))
                )
                withClue("Block should succeed: ${blockResult.error}") {
                    blockResult.error shouldBe null
                }

                // Becomes-blocked trigger fires — resolve it.
                game.resolveStack()

                withClue("White blocker should be destroyed by the becomes-blocked trigger") {
                    game.findPermanent("White Vanilla Reach") shouldBe null
                }
            }

            test("does not destroy a non-white creature that blocks it") {
                val game = scenario()
                    .withPlayers("Attacker", "Defender")
                    .withCardOnBattlefield(1, "Phyrexian Slayer")
                    .withCardOnBattlefield(2, "Green Vanilla Reach") // non-white blocker
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                val slayerId = game.findPermanent("Phyrexian Slayer")!!
                val blockerId = game.findPermanent("Green Vanilla Reach")!!

                game.execute(DeclareAttackers(game.player1Id, mapOf(slayerId to game.player2Id)))
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)

                game.execute(DeclareBlockers(game.player2Id, mapOf(blockerId to listOf(slayerId))))
                game.resolveStack()

                withClue("Green blocker should survive — trigger only matches white creatures") {
                    game.findPermanent("Green Vanilla Reach") shouldBe blockerId
                }
            }
        }
    }
}
