package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Spreading Plague.
 *
 * Card reference:
 * - Spreading Plague ({4}{B}): Enchantment
 *   Whenever a creature enters, destroy all other creatures that share a color with it.
 *   They can't be regenerated.
 *
 * Exercises the new `GameObjectFilter.sharingColorWith(EntityReference.Triggering)` filter and the
 * `excludeTriggering` flag on `Effects.DestroyAll`: when a creature enters, every *other* creature
 * sharing one of its colors is destroyed, while the entering creature and color-disjoint creatures
 * survive.
 */
class SpreadingPlagueScenarioTest : ScenarioTestBase() {

    private val whiteBear = CardDefinition.creature(
        name = "SP White Bear",
        manaCost = ManaCost.parse("{W}"),
        subtypes = setOf(Subtype("Bear")),
        power = 2,
        toughness = 2
    )

    private val whiteOldOne = CardDefinition.creature(
        name = "SP White Soldier",
        manaCost = ManaCost.parse("{W}"),
        subtypes = setOf(Subtype("Soldier")),
        power = 1,
        toughness = 1
    )

    private val redOldOne = CardDefinition.creature(
        name = "SP Red Goblin",
        manaCost = ManaCost.parse("{R}"),
        subtypes = setOf(Subtype("Goblin")),
        power = 1,
        toughness = 1
    )

    private val colorlessGolem = CardDefinition.creature(
        name = "SP Stone Golem",
        manaCost = ManaCost.parse("{3}"),
        subtypes = setOf(Subtype("Golem")),
        power = 3,
        toughness = 3
    )

    init {
        cardRegistry.register(whiteBear)
        cardRegistry.register(whiteOldOne)
        cardRegistry.register(redOldOne)
        cardRegistry.register(colorlessGolem)

        context("Spreading Plague") {
            test("an entering white creature destroys other white creatures but not itself or non-white ones") {
                val game = scenario()
                    .withPlayers("Controller", "Caster")
                    .withCardOnBattlefield(1, "Spreading Plague")
                    .withCardOnBattlefield(1, "SP White Soldier")
                    .withCardOnBattlefield(2, "SP Red Goblin")
                    .withCardInHand(2, "SP White Bear")
                    .withActivePlayer(2)
                    .withPriorityPlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.state = game.state.updateEntity(game.player2Id) {
                    it.with(ManaPoolComponent(white = 1))
                }

                game.castSpell(2, "SP White Bear").error shouldBe null
                game.resolveStack()

                withClue("The other white creature is destroyed") {
                    game.findPermanent("SP White Soldier") shouldBe null
                }
                withClue("The entering white creature survives (it is 'other'-excluded)") {
                    game.findPermanent("SP White Bear").shouldNotBeNull()
                }
                withClue("The red creature shares no color and survives") {
                    game.findPermanent("SP Red Goblin").shouldNotBeNull()
                }
            }

            test("an entering colorless creature shares no color and destroys nothing") {
                val game = scenario()
                    .withPlayers("Controller", "Caster")
                    .withCardOnBattlefield(1, "Spreading Plague")
                    .withCardOnBattlefield(1, "SP White Soldier")
                    .withCardOnBattlefield(2, "SP Red Goblin")
                    .withCardInHand(2, "SP Stone Golem")
                    .withActivePlayer(2)
                    .withPriorityPlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.state = game.state.updateEntity(game.player2Id) {
                    it.with(ManaPoolComponent(colorless = 3))
                }

                game.castSpell(2, "SP Stone Golem").error shouldBe null
                game.resolveStack()

                withClue("Colorless shares no color, so the white creature survives") {
                    game.findPermanent("SP White Soldier").shouldNotBeNull()
                }
                withClue("The red creature survives") {
                    game.findPermanent("SP Red Goblin").shouldNotBeNull()
                }
                withClue("The colorless golem survives") {
                    game.findPermanent("SP Stone Golem").shouldNotBeNull()
                }
            }
        }
    }
}
