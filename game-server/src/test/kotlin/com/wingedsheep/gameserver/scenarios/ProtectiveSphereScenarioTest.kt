package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.model.CardDefinition
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Scenario tests for Protective Sphere.
 *
 * "{1}, Pay 1 life: Prevent all damage that would be dealt to you this turn by a source of your
 *  choice that shares a color with the mana spent on this activation cost. (Colorless mana
 *  prevents no damage.)"
 *
 * The "shares a color with the mana spent" restriction is modeled by only offering colored
 * sources for the choice (`PreventionSourceFilter.ChosenColoredSource`).
 */
class ProtectiveSphereScenarioTest : ScenarioTestBase() {

    // A colorless creature (generic-only cost): shares a color with no mana, so it must never
    // be offered as a prevention source.
    private val colorlessGolem = CardDefinition.creature(
        name = "Colorless Golem",
        manaCost = ManaCost.parse("{3}"),
        subtypes = setOf(Subtype("Golem")),
        power = 3, toughness = 3
    )

    private fun TestGame.activateSphere() {
        val sourceId = findPermanent("Protective Sphere")!!
        val ability = cardRegistry.getCard("Protective Sphere")!!.script.activatedAbilities[0]
        val result = execute(
            ActivateAbility(
                playerId = player1Id,
                sourceId = sourceId,
                abilityId = ability.id
            )
        )
        withClue("Activation should succeed: ${result.error}") {
            result.error shouldBe null
        }
    }

    private fun TestGame.chooseSource(sourceName: String) {
        val decision = getPendingDecision()
        decision.shouldNotBeNull()
        decision.shouldBeInstanceOf<SelectCardsDecision>()
        val entityId = decision.options.first { id ->
            state.getEntity(id)?.get<CardComponent>()?.name == sourceName
        }
        submitDecision(CardsSelectedResponse(decision.id, listOf(entityId)))
    }

    init {
        cardRegistry.register(colorlessGolem)

        context("Protective Sphere") {

            test("prevents all combat damage from a chosen colored source and costs 1 life") {
                val game = scenario()
                    .withPlayers("White Mage", "Necromancer")
                    .withCardOnBattlefield(1, "Protective Sphere")
                    .withActivePlayer(2)
                    .withCardOnBattlefield(2, "Bog Raiders") // 2/2 black Zombie
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                // Give P1 the {1} for the activation cost.
                game.state = game.state.updateEntity(game.player1Id) { c ->
                    c.with(ManaPoolComponent(colorless = 1))
                }

                game.declareAttackers(mapOf("Bog Raiders" to 1))

                // P2 (active player) passes priority, then P1 activates Protective Sphere.
                game.passPriority()
                game.activateSphere()

                // Resolve the ability (LIFO) — it pauses for the source choice.
                game.passPriority() // P1 passes
                game.passPriority() // P2 passes → ability resolves and pauses

                game.chooseSource("Bog Raiders")

                // Paid 1 life; no combat damage dealt yet.
                game.getLifeTotal(1) shouldBe 19

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
                game.declareNoBlockers()
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)

                // All 2 combat damage prevented. Net: 20 - 1 (life cost) - 0 = 19.
                game.getLifeTotal(1) shouldBe 19
            }

            test("offers only colored sources — a colorless source is never a valid choice") {
                val game = scenario()
                    .withPlayers("White Mage", "Artificer")
                    .withCardOnBattlefield(1, "Protective Sphere")
                    .withCardOnBattlefield(2, "Bog Raiders")     // colored (black)
                    .withCardOnBattlefield(2, "Colorless Golem") // colorless
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.state = game.state.updateEntity(game.player1Id) { c ->
                    c.with(ManaPoolComponent(colorless = 1))
                }

                game.activateSphere()

                // Resolve the ability — pauses for the source choice.
                game.passPriority() // P1 passes
                game.passPriority() // P2 passes → ability resolves and pauses

                val decision = game.getPendingDecision()
                decision.shouldNotBeNull()
                decision.shouldBeInstanceOf<SelectCardsDecision>()

                val coloredId = game.findPermanent("Bog Raiders")!!
                val colorlessId = game.findPermanent("Colorless Golem")!!

                withClue("Colored source should be offered") {
                    decision.options shouldContain coloredId
                }
                withClue("Colorless source must never be offered") {
                    decision.options shouldNotContain colorlessId
                }

                // Cancel out of the test by choosing the only legal source.
                game.chooseSource("Bog Raiders")
            }
        }
    }
}
