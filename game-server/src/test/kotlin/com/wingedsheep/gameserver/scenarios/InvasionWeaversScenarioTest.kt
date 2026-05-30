package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.model.CardDefinition
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for the Invasion "Weaver" cycle of {1}{X} 2/1 Wizards, each with a
 * `{2}: Target [color] or [color] creature [pump/gains keyword] until end of turn` ability.
 *
 * Hate Weaver   — blue/red gets +1/+0
 * Spirit Weaver — green/blue gets +0/+1
 * Rage Weaver   — black/green gains haste
 * Might Weaver  — red/white gains trample
 * Sky Weaver    — white/black gains flying
 *
 * Each ability targets only creatures of two specific colors. These tests verify the
 * color-restricted [com.wingedsheep.sdk.scripting.filters.unified.TargetFilter]
 * (`GameObjectFilter.Creature.withAnyColor(...)`) both accepts a legal-color target and
 * rejects an off-color creature, and that the granted effect lands.
 */
class InvasionWeaversScenarioTest : ScenarioTestBase() {

    init {
        // Test creatures of each color (the registry only has set-registered cards, so
        // define plain vanilla beaters inline to act as targets). The mana cost color
        // drives the creature's color, which is what the Weaver target filters check.
        listOf(
            "Test White Bear" to "{1}{W}",
            "Test Blue Bear" to "{1}{U}",
            "Test Black Bear" to "{1}{B}",
            "Test Red Bear" to "{1}{R}",
            "Test Green Bear" to "{1}{G}",
        ).forEach { (name, cost) ->
            cardRegistry.register(
                CardDefinition.creature(
                    name = name,
                    manaCost = ManaCost.parse(cost),
                    subtypes = setOf(Subtype.BEAR),
                    power = 2,
                    toughness = 2,
                )
            )
        }

        context("Hate Weaver - {2}: target blue or red creature gets +1/+0") {
            test("pumps a legal blue target's power") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardOnBattlefield(1, "Hate Weaver")
                    .withCardOnBattlefield(1, "Test Blue Bear")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.state = game.state.updateEntity(game.player1Id) { it.with(ManaPoolComponent(colorless = 2)) }

                val targetId = game.findPermanent("Test Blue Bear")!!
                val sourceId = game.findPermanent("Hate Weaver")!!
                val ability = cardRegistry.getCard("Hate Weaver")!!.script.activatedAbilities[0]

                val result = game.execute(
                    ActivateAbility(game.player1Id, sourceId, ability.id, listOf(ChosenTarget.Permanent(targetId)))
                )
                withClue("Activation should succeed: ${result.error}") { result.error shouldBe null }
                game.resolveStack()

                withClue("Blue Bear should be 3/2 after +1/+0") {
                    game.state.projectedState.getPower(targetId) shouldBe 3
                    game.state.projectedState.getToughness(targetId) shouldBe 2
                }
            }

            test("cannot target a white creature") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardOnBattlefield(1, "Hate Weaver")
                    .withCardOnBattlefield(1, "Test White Bear")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.state = game.state.updateEntity(game.player1Id) { it.with(ManaPoolComponent(colorless = 2)) }

                val targetId = game.findPermanent("Test White Bear")!!
                val sourceId = game.findPermanent("Hate Weaver")!!
                val ability = cardRegistry.getCard("Hate Weaver")!!.script.activatedAbilities[0]

                val result = game.execute(
                    ActivateAbility(game.player1Id, sourceId, ability.id, listOf(ChosenTarget.Permanent(targetId)))
                )
                withClue("White creature is not a legal blue-or-red target") { result.error shouldNotBe null }
            }
        }

        context("Spirit Weaver - {2}: target green or blue creature gets +0/+1") {
            test("buffs a legal green target's toughness") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardOnBattlefield(1, "Spirit Weaver")
                    .withCardOnBattlefield(1, "Test Green Bear")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.state = game.state.updateEntity(game.player1Id) { it.with(ManaPoolComponent(colorless = 2)) }

                val targetId = game.findPermanent("Test Green Bear")!!
                val sourceId = game.findPermanent("Spirit Weaver")!!
                val ability = cardRegistry.getCard("Spirit Weaver")!!.script.activatedAbilities[0]

                game.execute(
                    ActivateAbility(game.player1Id, sourceId, ability.id, listOf(ChosenTarget.Permanent(targetId)))
                ).error shouldBe null
                game.resolveStack()

                withClue("Green Bear should be 2/3 after +0/+1") {
                    game.state.projectedState.getPower(targetId) shouldBe 2
                    game.state.projectedState.getToughness(targetId) shouldBe 3
                }
            }
        }

        context("Rage Weaver - {2}: target black or green creature gains haste") {
            test("grants haste to a legal black target") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardOnBattlefield(1, "Rage Weaver")
                    .withCardOnBattlefield(1, "Test Black Bear")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.state = game.state.updateEntity(game.player1Id) { it.with(ManaPoolComponent(colorless = 2)) }

                val targetId = game.findPermanent("Test Black Bear")!!
                val sourceId = game.findPermanent("Rage Weaver")!!
                val ability = cardRegistry.getCard("Rage Weaver")!!.script.activatedAbilities[0]

                game.execute(
                    ActivateAbility(game.player1Id, sourceId, ability.id, listOf(ChosenTarget.Permanent(targetId)))
                ).error shouldBe null
                game.resolveStack()

                withClue("Black Bear should have haste") {
                    game.state.projectedState.hasKeyword(targetId, "HASTE") shouldBe true
                }
            }

            test("cannot target a blue creature") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardOnBattlefield(1, "Rage Weaver")
                    .withCardOnBattlefield(1, "Test Blue Bear")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.state = game.state.updateEntity(game.player1Id) { it.with(ManaPoolComponent(colorless = 2)) }

                val targetId = game.findPermanent("Test Blue Bear")!!
                val sourceId = game.findPermanent("Rage Weaver")!!
                val ability = cardRegistry.getCard("Rage Weaver")!!.script.activatedAbilities[0]

                val result = game.execute(
                    ActivateAbility(game.player1Id, sourceId, ability.id, listOf(ChosenTarget.Permanent(targetId)))
                )
                withClue("Blue creature is not a legal black-or-green target") { result.error shouldNotBe null }
            }
        }

        context("Might Weaver - {2}: target red or white creature gains trample") {
            test("grants trample to a legal red target") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardOnBattlefield(1, "Might Weaver")
                    .withCardOnBattlefield(1, "Test Red Bear")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.state = game.state.updateEntity(game.player1Id) { it.with(ManaPoolComponent(colorless = 2)) }

                val targetId = game.findPermanent("Test Red Bear")!!
                val sourceId = game.findPermanent("Might Weaver")!!
                val ability = cardRegistry.getCard("Might Weaver")!!.script.activatedAbilities[0]

                game.execute(
                    ActivateAbility(game.player1Id, sourceId, ability.id, listOf(ChosenTarget.Permanent(targetId)))
                ).error shouldBe null
                game.resolveStack()

                withClue("Red Bear should have trample") {
                    game.state.projectedState.hasKeyword(targetId, "TRAMPLE") shouldBe true
                }
            }
        }

        context("Sky Weaver - {2}: target white or black creature gains flying") {
            test("grants flying to a legal white target") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardOnBattlefield(1, "Sky Weaver")
                    .withCardOnBattlefield(1, "Test White Bear")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.state = game.state.updateEntity(game.player1Id) { it.with(ManaPoolComponent(colorless = 2)) }

                val targetId = game.findPermanent("Test White Bear")!!
                val sourceId = game.findPermanent("Sky Weaver")!!
                val ability = cardRegistry.getCard("Sky Weaver")!!.script.activatedAbilities[0]

                game.execute(
                    ActivateAbility(game.player1Id, sourceId, ability.id, listOf(ChosenTarget.Permanent(targetId)))
                ).error shouldBe null
                game.resolveStack()

                withClue("White Bear should have flying") {
                    game.state.projectedState.hasKeyword(targetId, "FLYING") shouldBe true
                }
            }

            test("cannot target a green creature") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardOnBattlefield(1, "Sky Weaver")
                    .withCardOnBattlefield(1, "Test Green Bear")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.state = game.state.updateEntity(game.player1Id) { it.with(ManaPoolComponent(colorless = 2)) }

                val targetId = game.findPermanent("Test Green Bear")!!
                val sourceId = game.findPermanent("Sky Weaver")!!
                val ability = cardRegistry.getCard("Sky Weaver")!!.script.activatedAbilities[0]

                val result = game.execute(
                    ActivateAbility(game.player1Id, sourceId, ability.id, listOf(ChosenTarget.Permanent(targetId)))
                )
                withClue("Green creature is not a legal white-or-black target") { result.error shouldNotBe null }
            }
        }
    }
}
