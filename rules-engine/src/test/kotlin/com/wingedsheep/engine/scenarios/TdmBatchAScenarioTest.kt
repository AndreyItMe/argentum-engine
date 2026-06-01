package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.DistributeDecision
import com.wingedsheep.engine.core.DistributionResponse
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.combat.AttackingComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for the TDM "batch A" cards:
 *  - Agent of Kotis (#36): {1}{U} Human Rogue 2/1 — Renew {3}{U}: put two +1/+1 counters on a creature.
 *  - Armament Dragon (#168): {3}{W}{B}{G} Dragon 3/4, Flying — ETB distribute three +1/+1 counters
 *    among one, two, or three target creatures you control.
 *  - Dragonback Lancer (#9): {3}{W} Human Soldier 3/3, Flying, Mobilize 1.
 *  - Bone-Cairn Butcher (#173): {1}{R}{W}{B} Demon 4/4, Mobilize 2, attacking tokens you control
 *    have deathtouch.
 */
class TdmBatchAScenarioTest : ScenarioTestBase() {

    private val agentRenewAbilityId =
        cardRegistry.getCard("Agent of Kotis")!!.activatedAbilities.first().id

    init {
        context("Agent of Kotis") {
            test("renew puts two +1/+1 counters on target creature, exiling Agent from the graveyard") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInGraveyard(1, "Agent of Kotis")
                    .withCardOnBattlefield(1, "Grizzly Bears") // 2/2
                    .withLandsOnBattlefield(1, "Island", 4) // renew cost {3}{U}
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val agent = game.findCardsInGraveyard(1, "Agent of Kotis").first()
                val bear = game.findPermanent("Grizzly Bears")!!

                val activation = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = agent,
                        abilityId = agentRenewAbilityId,
                        targets = listOf(ChosenTarget.Permanent(bear)),
                    )
                )
                withClue("Activating Agent of Kotis renew should succeed: ${activation.error}") {
                    activation.error shouldBe null
                }
                game.resolveStack()

                withClue("Grizzly Bears gets two +1/+1 counters") {
                    game.state.getEntity(bear)?.get<CountersComponent>()
                        ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 2
                }
                withClue("Agent of Kotis is exiled from the graveyard as part of the cost") {
                    game.findCardsInGraveyard(1, "Agent of Kotis").size shouldBe 0
                    game.state.getExile(game.player1Id).contains(agent) shouldBe true
                }
            }
        }

        context("Armament Dragon") {
            test("flying, and ETB distributes three +1/+1 counters among target creatures you control") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Armament Dragon")
                    .withLandsOnBattlefield(1, "Plains", 3)
                    .withLandsOnBattlefield(1, "Swamp", 1)
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withCardOnBattlefield(1, "Grizzly Bears") // distribute target
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bear = game.findPermanent("Grizzly Bears")!!

                // Cast the Dragon; resolving the stack puts it on the battlefield and fires its ETB.
                withClue("Casting Armament Dragon should succeed") {
                    game.castSpell(1, "Armament Dragon").error shouldBe null
                }
                game.resolveStack() // dragon enters → ETB trigger asks for targets

                val dragon = game.findPermanent("Armament Dragon")!!
                withClue("Armament Dragon has flying") {
                    game.state.projectedState.hasKeyword(dragon, Keyword.FLYING) shouldBe true
                }

                // Choose the Bear as the single target, then distribute all three counters onto it.
                withClue("Targeting the Bear should be legal") {
                    game.selectTargets(listOf(bear)).error shouldBe null
                }
                game.resolveStack()

                if (game.hasPendingDecision()) {
                    val decision = game.getPendingDecision() as DistributeDecision
                    game.submitDecision(
                        DistributionResponse(decision.id, mapOf(bear to decision.totalAmount))
                    )
                    game.resolveStack()
                }

                withClue("All three +1/+1 counters land on the single target") {
                    game.state.getEntity(bear)?.get<CountersComponent>()
                        ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 3
                }
            }
        }

        context("Dragonback Lancer") {
            test("flying, and Mobilize 1 makes one tapped, attacking Warrior token on attack") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Dragonback Lancer", tapped = false, summoningSickness = false)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val lancer = game.findPermanent("Dragonback Lancer")!!
                withClue("Dragonback Lancer has flying") {
                    game.state.projectedState.hasKeyword(lancer, Keyword.FLYING) shouldBe true
                }

                game.advanceToPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                val attack = game.declareAttackers(mapOf("Dragonback Lancer" to 2))
                withClue("Declaring Dragonback Lancer as attacker should succeed: ${attack.error}") {
                    attack.error shouldBe null
                }
                game.resolveStack()

                val warriors = game.findPermanents("Warrior Token")
                withClue("Mobilize 1 creates one Warrior token") { warriors.size shouldBe 1 }
                withClue("The Warrior token is tapped and attacking") {
                    warriors.forEach { token ->
                        game.state.getEntity(token)?.has<TappedComponent>() shouldBe true
                        game.state.getEntity(token)?.has<AttackingComponent>() shouldBe true
                    }
                }
            }
        }

        context("Bone-Cairn Butcher") {
            test("Mobilize 2 makes two Warrior tokens that gain deathtouch while attacking") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Bone-Cairn Butcher", tapped = false, summoningSickness = false)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.advanceToPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                val attack = game.declareAttackers(mapOf("Bone-Cairn Butcher" to 2))
                withClue("Declaring Bone-Cairn Butcher as attacker should succeed: ${attack.error}") {
                    attack.error shouldBe null
                }
                game.resolveStack()

                val warriors = game.findPermanents("Warrior Token")
                withClue("Mobilize 2 creates two Warrior tokens") { warriors.size shouldBe 2 }
                withClue("Each attacking Warrior token gains deathtouch from Bone-Cairn Butcher's static") {
                    warriors.forEach { token ->
                        game.state.projectedState.hasKeyword(token, Keyword.DEATHTOUCH) shouldBe true
                    }
                }
            }
        }
    }
}
