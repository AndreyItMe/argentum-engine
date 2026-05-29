package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.model.CardDefinition
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Pure Reflection.
 *
 * Card reference:
 * - Pure Reflection ({2}{W}): Enchantment
 *   Whenever a player casts a creature spell, destroy all Reflections. Then that player creates
 *   an X/X white Reflection creature token, where X is the mana value of that spell.
 *
 * Exercises the new `Triggers.anyPlayerCasts(spellFilter)` facade: the trigger fires for *any*
 * player's creature cast, the destroy hits all pre-existing Reflections, and the X/X token is
 * created under the casting player (`Player.TriggeringPlayer`) with P/T = the spell's mana value.
 */
class PureReflectionScenarioTest : ScenarioTestBase() {

    private val oldReflection = CardDefinition.creature(
        name = "PR Old Reflection",
        manaCost = ManaCost.parse("{1}"),
        subtypes = setOf(Subtype("Reflection")),
        power = 2,
        toughness = 2
    )

    // Mana value 3 ({2}{G}).
    private val bigBeast = CardDefinition.creature(
        name = "PR Big Beast",
        manaCost = ManaCost.parse("{2}{G}"),
        subtypes = setOf(Subtype("Beast")),
        power = 4,
        toughness = 4
    )

    init {
        cardRegistry.register(oldReflection)
        cardRegistry.register(bigBeast)

        context("Pure Reflection") {
            test("a creature cast destroys existing Reflections and makes an X/X for the caster") {
                val game = scenario()
                    .withPlayers("Controller", "Caster")
                    .withCardOnBattlefield(1, "Pure Reflection")
                    .withCardOnBattlefield(1, "PR Old Reflection")
                    .withCardInHand(2, "PR Big Beast")
                    .withActivePlayer(2)
                    .withPriorityPlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.state = game.state.updateEntity(game.player2Id) {
                    it.with(ManaPoolComponent(green = 1, colorless = 2))
                }

                game.castSpell(2, "PR Big Beast").error shouldBe null
                game.resolveStack()

                withClue("The pre-existing Reflection is destroyed") {
                    game.findPermanent("PR Old Reflection") shouldBe null
                }

                val tokenId = game.findPermanent("Reflection Token")
                withClue("An X/X Reflection token is created") { tokenId.shouldNotBeNull() }

                withClue("The token is controlled by the casting player") {
                    game.state.getEntity(tokenId!!)?.get<ControllerComponent>()?.playerId shouldBe game.player2Id
                }

                val projected = StateProjector().project(game.state)
                withClue("X equals the cast creature's mana value (3)") {
                    projected.getPower(tokenId!!) shouldBe 3
                    projected.getToughness(tokenId) shouldBe 3
                }
            }
        }
    }
}
