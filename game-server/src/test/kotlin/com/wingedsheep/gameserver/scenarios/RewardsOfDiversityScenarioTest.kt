package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.model.CardDefinition
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Rewards of Diversity.
 *
 * Card reference:
 * - Rewards of Diversity ({2}{W}): Enchantment
 *   Whenever an opponent casts a multicolored spell, you gain 4 life.
 *
 * Exercises the new `Triggers.opponentCasts(spellFilter)` facade + `GameObjectFilter.Multicolored`:
 * the trigger must fire only for *opponents'* casts (`Player.Opponent` runtime matching) and only
 * for multicolored spells, with the life gain going to the enchantment's controller.
 */
class RewardsOfDiversityScenarioTest : ScenarioTestBase() {

    private val multicoloredBear = CardDefinition.creature(
        name = "RoD Azorius Bear",
        manaCost = ManaCost.parse("{W}{U}"),
        subtypes = setOf(Subtype("Bear")),
        power = 2,
        toughness = 2
    )

    private val monoBear = CardDefinition.creature(
        name = "RoD Mono Bear",
        manaCost = ManaCost.parse("{1}{U}"),
        subtypes = setOf(Subtype("Bear")),
        power = 2,
        toughness = 2
    )

    init {
        cardRegistry.register(multicoloredBear)
        cardRegistry.register(monoBear)

        fun lifeOf(game: TestGame, playerId: com.wingedsheep.sdk.model.EntityId) =
            game.state.getEntity(playerId)?.get<LifeTotalComponent>()?.life

        context("Rewards of Diversity") {
            test("gains 4 life when an opponent casts a multicolored spell") {
                val game = scenario()
                    .withPlayers("Controller", "Opponent")
                    .withCardOnBattlefield(1, "Rewards of Diversity")
                    .withCardInHand(2, "RoD Azorius Bear")
                    .withActivePlayer(2)
                    .withPriorityPlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.state = game.state.updateEntity(game.player2Id) {
                    it.with(ManaPoolComponent(white = 1, blue = 1, colorless = 2))
                }

                game.castSpell(2, "RoD Azorius Bear").error shouldBe null
                game.resolveStack()

                withClue("Controller gains 4 life from the opponent's multicolored cast") {
                    lifeOf(game, game.player1Id) shouldBe 24
                }
            }

            test("does not gain life when an opponent casts a monocolored spell") {
                val game = scenario()
                    .withPlayers("Controller", "Opponent")
                    .withCardOnBattlefield(1, "Rewards of Diversity")
                    .withCardInHand(2, "RoD Mono Bear")
                    .withActivePlayer(2)
                    .withPriorityPlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.state = game.state.updateEntity(game.player2Id) {
                    it.with(ManaPoolComponent(blue = 1, colorless = 1))
                }

                game.castSpell(2, "RoD Mono Bear").error shouldBe null
                game.resolveStack()

                withClue("Monocolored cast does not trigger the enchantment") {
                    lifeOf(game, game.player1Id) shouldBe 20
                }
            }

            test("does not gain life when the controller casts a multicolored spell") {
                val game = scenario()
                    .withPlayers("Controller", "Opponent")
                    .withCardOnBattlefield(1, "Rewards of Diversity")
                    .withCardInHand(1, "RoD Azorius Bear")
                    .withActivePlayer(1)
                    .withPriorityPlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.state = game.state.updateEntity(game.player1Id) {
                    it.with(ManaPoolComponent(white = 1, blue = 1, colorless = 2))
                }

                game.castSpell(1, "RoD Azorius Bear").error shouldBe null
                game.resolveStack()

                withClue("The controller's own multicolored cast does not trigger 'opponent casts'") {
                    lifeOf(game, game.player1Id) shouldBe 20
                }
            }
        }
    }
}
