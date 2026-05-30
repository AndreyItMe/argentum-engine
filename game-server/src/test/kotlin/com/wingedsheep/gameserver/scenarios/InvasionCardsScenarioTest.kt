package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for four Invasion (INV) cards:
 *  - Sparring Golem ({3} 2/2 Artifact Creature — Golem; +1/+1 per blocker when blocked)
 *  - Metathran Zombie ({1}{U} 1/1; {B}: Regenerate this creature)
 *  - Crown of Flames ({R} Aura; {R}: enchanted creature gets +1/+0; {R}: bounce this Aura)
 *  - Vigorous Charge ({G} Instant; Kicker {W}; trample + kicked lifegain on combat damage)
 */
class InvasionCardsScenarioTest : ScenarioTestBase() {

    private val projector = StateProjector()

    init {
        context("Sparring Golem") {
            test("gets +1/+1 for each creature blocking it") {
                val game = scenario()
                    .withPlayers("Attacker", "Defender")
                    .withCardOnBattlefield(1, "Sparring Golem")  // 2/2
                    .withCardOnBattlefield(2, "Grizzly Bears")   // 2/2
                    .withCardOnBattlefield(2, "Glory Seeker")    // 2/2
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                val golemId = game.findPermanent("Sparring Golem")!!
                val bearId = game.findPermanent("Grizzly Bears")!!
                val seekerId = game.findPermanent("Glory Seeker")!!

                game.execute(DeclareAttackers(game.player1Id, mapOf(golemId to game.player2Id)))
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
                game.execute(DeclareBlockers(game.player2Id, mapOf(
                    bearId to listOf(golemId),
                    seekerId to listOf(golemId)
                )))
                game.resolveStack()

                val projected = projector.project(game.state)
                withClue("Sparring Golem should be 4/4 after +2/+2 for two blockers") {
                    projected.getPower(golemId) shouldBe 4
                    projected.getToughness(golemId) shouldBe 4
                }
            }

            test("is not buffed when unblocked") {
                val game = scenario()
                    .withPlayers("Attacker", "Defender")
                    .withCardOnBattlefield(1, "Sparring Golem")
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                val golemId = game.findPermanent("Sparring Golem")!!
                game.execute(DeclareAttackers(game.player1Id, mapOf(golemId to game.player2Id)))
                game.passUntilPhase(Phase.COMBAT, Step.COMBAT_DAMAGE)

                val projected = projector.project(game.state)
                withClue("Unblocked Sparring Golem stays 2/2") {
                    projected.getPower(golemId) shouldBe 2
                    projected.getToughness(golemId) shouldBe 2
                }
            }
        }

        context("Metathran Zombie") {
            test("regenerates to survive lethal damage") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Metathran Zombie")  // 1/1
                    .withLandsOnBattlefield(1, "Swamp", 1)
                    .withCardInHand(2, "Shock")
                    .withLandsOnBattlefield(2, "Mountain", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val zombieId = game.findPermanent("Metathran Zombie")!!
                val ability = cardRegistry.getCard("Metathran Zombie")!!.script.activatedAbilities.first()

                val activate = game.execute(
                    ActivateAbility(game.player1Id, zombieId, ability.id, emptyList())
                )
                withClue("Regenerate activation should succeed: ${activate.error}") {
                    activate.error shouldBe null
                }
                game.resolveStack()

                withClue("Regen shield (floating effect) should be present") {
                    game.state.floatingEffects.isNotEmpty() shouldBe true
                }

                game.execute(PassPriority(game.player1Id))
                game.castSpell(2, "Shock", zombieId)
                game.resolveStack()

                withClue("Metathran Zombie should survive via regeneration") {
                    game.isOnBattlefield("Metathran Zombie") shouldBe true
                }
            }
        }

        context("Crown of Flames") {
            test("pumps the enchanted creature +1/+0 and can bounce itself") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Crown of Flames")
                    .withCardOnBattlefield(1, "Grizzly Bears")  // 2/2
                    .withLandsOnBattlefield(1, "Mountain", 3)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bearId = game.findPermanent("Grizzly Bears")!!

                val cast = game.castSpell(1, "Crown of Flames", bearId)
                withClue("Crown of Flames should attach: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack()

                val crownId = game.findPermanent("Crown of Flames")!!
                val cardDef = cardRegistry.getCard("Crown of Flames")!!
                val pumpAbility = cardDef.script.activatedAbilities[0]
                val bounceAbility = cardDef.script.activatedAbilities[1]

                // {R}: enchanted creature gets +1/+0
                val pump = game.execute(ActivateAbility(game.player1Id, crownId, pumpAbility.id, emptyList()))
                withClue("Pump activation should succeed: ${pump.error}") {
                    pump.error shouldBe null
                }
                game.resolveStack()

                val pumped = projector.project(game.state)
                withClue("Enchanted Grizzly Bears should be 3/2 after +1/+0") {
                    pumped.getPower(bearId) shouldBe 3
                    pumped.getToughness(bearId) shouldBe 2
                }

                // {R}: return this Aura to its owner's hand
                val bounce = game.execute(ActivateAbility(game.player1Id, crownId, bounceAbility.id, emptyList()))
                withClue("Bounce activation should succeed: ${bounce.error}") {
                    bounce.error shouldBe null
                }
                game.resolveStack()

                withClue("Crown of Flames should be back in its owner's hand") {
                    game.isInHand(1, "Crown of Flames") shouldBe true
                    game.isOnBattlefield("Crown of Flames") shouldBe false
                }
            }
        }

        context("Vigorous Charge") {
            test("kicked: grants trample and gains life equal to combat damage dealt") {
                val game = scenario()
                    .withPlayers("Attacker", "Defender")
                    .withCardInHand(1, "Vigorous Charge")
                    .withCardOnBattlefield(1, "Grizzly Bears")  // 2/2 attacker
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withLandsOnBattlefield(1, "Plains", 1)     // kicker {W}
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bearId = game.findPermanent("Grizzly Bears")!!
                val hand = game.state.getHand(game.player1Id)
                val chargeId = hand.first {
                    game.state.getEntity(it)?.get<CardComponent>()?.name == "Vigorous Charge"
                }

                val startLife = game.getLifeTotal(1)

                val cast = game.execute(
                    CastSpell(
                        game.player1Id,
                        chargeId,
                        listOf(ChosenTarget.Permanent(bearId)),
                        wasKicked = true
                    )
                )
                withClue("Kicked Vigorous Charge cast should succeed: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack()

                val projected = projector.project(game.state)
                withClue("Target creature should have trample") {
                    projected.hasKeyword(bearId, com.wingedsheep.sdk.core.Keyword.TRAMPLE) shouldBe true
                }

                // Attack with the unblocked bear; it deals 2 combat damage to the player.
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.execute(DeclareAttackers(game.player1Id, mapOf(bearId to game.player2Id)))
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
                game.resolveStack()

                withClue("Controller should gain 2 life from the kicked lifegain trigger") {
                    game.getLifeTotal(1) shouldBe startLife + 2
                }
            }

            test("unkicked: grants trample but no lifegain") {
                val game = scenario()
                    .withPlayers("Attacker", "Defender")
                    .withCardInHand(1, "Vigorous Charge")
                    .withCardOnBattlefield(1, "Grizzly Bears")  // 2/2 attacker
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bearId = game.findPermanent("Grizzly Bears")!!
                val startLife = game.getLifeTotal(1)

                val cast = game.castSpell(1, "Vigorous Charge", bearId)
                withClue("Unkicked Vigorous Charge cast should succeed: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack()

                val projected = projector.project(game.state)
                withClue("Target creature should have trample even when unkicked") {
                    projected.hasKeyword(bearId, com.wingedsheep.sdk.core.Keyword.TRAMPLE) shouldBe true
                }

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.execute(DeclareAttackers(game.player1Id, mapOf(bearId to game.player2Id)))
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
                game.resolveStack()

                withClue("Controller should NOT gain life when the spell was not kicked") {
                    game.getLifeTotal(1) shouldBe startLife
                }
            }
        }
    }
}
