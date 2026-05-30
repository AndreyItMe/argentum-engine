package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for Obsidian Acolyte.
 *
 * {1}{W} 1/1 Human Cleric with protection from black and
 * "{W}: Target creature gains protection from black until end of turn."
 *
 * Exercises [com.wingedsheep.sdk.dsl.Effects.GrantProtectionFromColor]: a fixed-color
 * protection grant (no player color choice), composed from the `PROTECTION_FROM_<COLOR>`
 * string keyword via the existing GrantKeyword path. Mirrors Crimson Acolyte (red variant).
 */
class ObsidianAcolyteScenarioTest : ScenarioTestBase() {

    init {
        context("Obsidian Acolyte - grant protection from black (fixed color)") {

            test("activated ability grants the target protection from black until end of turn") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardOnBattlefield(1, "Obsidian Acolyte")
                    .withCardOnBattlefield(1, "Grizzly Bears") // 2/2 green — will gain protection
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.state = game.state.updateEntity(game.player1Id) { container ->
                    container.with(ManaPoolComponent(white = 1))
                }

                val bearsId = game.findPermanent("Grizzly Bears")!!
                val acolyteId = game.findPermanent("Obsidian Acolyte")!!
                val ability = cardRegistry.getCard("Obsidian Acolyte")!!.script.activatedAbilities[0]

                withClue("Grizzly Bears should not start with protection from black") {
                    game.state.projectedState.hasKeyword(bearsId, "PROTECTION_FROM_BLACK") shouldBe false
                }

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = acolyteId,
                        abilityId = ability.id,
                        targets = listOf(ChosenTarget.Permanent(bearsId))
                    )
                )
                withClue("Activation should succeed: ${result.error}") {
                    result.error shouldBe null
                }

                game.resolveStack()

                withClue("Grizzly Bears should now have protection from black") {
                    game.state.projectedState.hasKeyword(bearsId, "PROTECTION_FROM_BLACK") shouldBe true
                }
            }

            test("granted protection from black stops a black spell from targeting the creature") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardOnBattlefield(1, "Obsidian Acolyte")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardInHand(2, "Agonizing Demise") // {3}{B} black removal — should not be able to target after grant
                    .withLandsOnBattlefield(2, "Swamp", 4)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.state = game.state.updateEntity(game.player1Id) { container ->
                    container.with(ManaPoolComponent(white = 1))
                }

                val bearsId = game.findPermanent("Grizzly Bears")!!
                val acolyteId = game.findPermanent("Obsidian Acolyte")!!
                val ability = cardRegistry.getCard("Obsidian Acolyte")!!.script.activatedAbilities[0]

                game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = acolyteId,
                        abilityId = ability.id,
                        targets = listOf(ChosenTarget.Permanent(bearsId))
                    )
                )
                game.resolveStack()

                // Opponent attempts to target the protected Grizzly Bears with a black spell — should fail.
                val killResult = game.castSpell(2, "Agonizing Demise", bearsId)
                withClue("Black spell should not be able to target a creature with protection from black") {
                    killResult.error shouldNotBe null
                }
            }
        }
    }
}
