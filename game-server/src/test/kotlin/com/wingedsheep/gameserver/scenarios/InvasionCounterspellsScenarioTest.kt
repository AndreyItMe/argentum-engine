package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
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
 *  - Angelic Shield ({W}{U} Enchantment; creatures you control get +0/+1; Sacrifice: bounce target creature)
 *  - Dismantling Blow ({2}{W} Instant; Kicker {2}{U}; destroy target artifact/enchantment, draw 2 if kicked)
 *  - Exclude ({2}{U} Instant; counter target creature spell, draw a card)
 *  - Prohibit ({1}{U} Instant; Kicker {2}; counter target spell if MV ≤ 2, or ≤ 4 if kicked)
 */
class InvasionCounterspellsScenarioTest : ScenarioTestBase() {

    private val projector = StateProjector()

    init {
        context("Angelic Shield") {
            test("gives creatures you control +0/+1 and can be sacrificed to bounce a creature") {
                val game = scenario()
                    .withPlayers("Player1", "Opponent")
                    .withCardOnBattlefield(1, "Angelic Shield")
                    .withCardOnBattlefield(1, "Grizzly Bears") // 2/2
                    .withCardOnBattlefield(2, "Glory Seeker")  // 2/2 opponent
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bearsId = game.findPermanent("Grizzly Bears")!!
                val seekerId = game.findPermanent("Glory Seeker")!!

                // Anthem: your Grizzly Bears is 2/3, opponent's Glory Seeker is unaffected (2/2).
                val projected = projector.project(game.state)
                withClue("Grizzly Bears should be buffed to 2/3 by Angelic Shield") {
                    projected.getToughness(bearsId) shouldBe 3
                }
                withClue("Opponent's Glory Seeker should be unaffected (2/2)") {
                    projected.getToughness(seekerId) shouldBe 2
                }

                // Sacrifice Angelic Shield to bounce the opponent's Glory Seeker.
                val shieldId = game.findPermanent("Angelic Shield")!!
                val cardDef = cardRegistry.getCard("Angelic Shield")!!
                val ability = cardDef.script.activatedAbilities.first()
                game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = shieldId,
                        abilityId = ability.id,
                        targets = listOf(ChosenTarget.Permanent(seekerId))
                    )
                )
                game.resolveStack()

                withClue("Glory Seeker should be returned to its owner's hand") {
                    game.isInHand(2, "Glory Seeker") shouldBe true
                }
                withClue("Angelic Shield should be sacrificed (in graveyard)") {
                    game.isInGraveyard(1, "Angelic Shield") shouldBe true
                }
            }
        }

        context("Dismantling Blow") {
            test("destroys target artifact without kicker") {
                val game = scenario()
                    .withPlayers("Player1", "Opponent")
                    .withCardInHand(1, "Dismantling Blow")
                    .withLandsOnBattlefield(1, "Plains", 3) // {2}{W}
                    .withCardOnBattlefield(2, "Icy Manipulator")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(1, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val icyId = game.findPermanent("Icy Manipulator")!!
                val handSizeBefore = game.state.getHand(game.player1Id).size

                game.castSpell(1, "Dismantling Blow", icyId)
                game.resolveStack()

                withClue("Icy Manipulator should be destroyed") {
                    game.isInGraveyard(2, "Icy Manipulator") shouldBe true
                }
                // Cast the spell from hand (removes 1), no draw without kicker.
                withClue("No cards drawn without kicker") {
                    game.state.getHand(game.player1Id).size shouldBe handSizeBefore - 1
                }
            }

            test("draws two cards when kicked") {
                val game = scenario()
                    .withPlayers("Player1", "Opponent")
                    .withCardInHand(1, "Dismantling Blow")
                    .withLandsOnBattlefield(1, "Plains", 3)
                    .withLandsOnBattlefield(1, "Island", 3) // {2}{U} kicker
                    .withCardOnBattlefield(2, "Icy Manipulator")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(1, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val icyId = game.findPermanent("Icy Manipulator")!!
                val playerId = game.player1Id
                val handSizeBefore = game.state.getHand(playerId).size
                val cardId = game.state.getHand(playerId).first { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Dismantling Blow"
                }

                game.execute(
                    CastSpell(
                        playerId = playerId,
                        cardId = cardId,
                        targets = listOf(ChosenTarget.Permanent(icyId)),
                        wasKicked = true,
                    )
                )
                game.resolveStack()

                withClue("Icy Manipulator should be destroyed") {
                    game.isInGraveyard(2, "Icy Manipulator") shouldBe true
                }
                // -1 for the cast spell, +2 from the kicked draw → net +1.
                withClue("Kicked Dismantling Blow draws two cards") {
                    game.state.getHand(playerId).size shouldBe handSizeBefore + 1
                }
            }
        }

        context("Exclude") {
            test("counters a creature spell and draws a card") {
                val game = scenario()
                    .withPlayers("Player1", "Opponent")
                    .withCardInHand(1, "Exclude")
                    .withLandsOnBattlefield(1, "Island", 3)
                    .withCardInLibrary(1, "Island")
                    .withCardInHand(2, "Grizzly Bears")
                    .withLandsOnBattlefield(2, "Forest", 2)
                    .withActivePlayer(2)
                    .withPriorityPlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val handBefore = game.state.getHand(game.player1Id).size

                // Opponent casts Grizzly Bears.
                game.castSpell(2, "Grizzly Bears")
                game.passPriority()

                // Player1 responds with Exclude targeting the creature spell.
                game.castSpellTargetingStackSpell(1, "Exclude", "Grizzly Bears")
                game.resolveStack()

                withClue("Grizzly Bears should be countered (in graveyard)") {
                    game.isInGraveyard(2, "Grizzly Bears") shouldBe true
                }
                // -1 Exclude cast, +1 draw → net 0.
                withClue("Exclude draws a card") {
                    game.state.getHand(game.player1Id).size shouldBe handBefore
                }
            }
        }

        context("Prohibit") {
            test("counters a mana value 2 spell without kicker") {
                val game = scenario()
                    .withPlayers("Player1", "Opponent")
                    .withCardInHand(1, "Prohibit")
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withCardInHand(2, "Grizzly Bears") // MV 2
                    .withLandsOnBattlefield(2, "Forest", 2)
                    .withActivePlayer(2)
                    .withPriorityPlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(2, "Grizzly Bears")
                game.passPriority()
                game.castSpellTargetingStackSpell(1, "Prohibit", "Grizzly Bears")
                game.resolveStack()

                withClue("MV 2 Grizzly Bears should be countered") {
                    game.isInGraveyard(2, "Grizzly Bears") shouldBe true
                }
            }

            test("does not counter a mana value 4 spell without kicker") {
                val game = scenario()
                    .withPlayers("Player1", "Opponent")
                    .withCardInHand(1, "Prohibit")
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withCardInHand(2, "Hill Giant") // {3}{R} MV 4
                    .withLandsOnBattlefield(2, "Mountain", 4)
                    .withActivePlayer(2)
                    .withPriorityPlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(2, "Hill Giant")
                game.passPriority()
                game.castSpellTargetingStackSpell(1, "Prohibit", "Hill Giant")
                game.resolveStack()

                withClue("MV 4 Hill Giant should NOT be countered (resolves to battlefield)") {
                    game.isOnBattlefield("Hill Giant") shouldBe true
                }
            }

            test("counters a mana value 4 spell when kicked") {
                val game = scenario()
                    .withPlayers("Player1", "Opponent")
                    .withCardInHand(1, "Prohibit")
                    .withLandsOnBattlefield(1, "Island", 4) // {1}{U} + {2} kicker
                    .withCardInHand(2, "Hill Giant")
                    .withLandsOnBattlefield(2, "Mountain", 4)
                    .withActivePlayer(2)
                    .withPriorityPlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(2, "Hill Giant")
                game.passPriority()

                val playerId = game.player1Id
                val prohibitId = game.state.getHand(playerId).first { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Prohibit"
                }
                val giantSpellId = game.state.stack.first { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Hill Giant"
                }
                game.execute(
                    CastSpell(
                        playerId = playerId,
                        cardId = prohibitId,
                        targets = listOf(ChosenTarget.Spell(giantSpellId)),
                        wasKicked = true,
                    )
                )
                game.resolveStack()

                withClue("Kicked Prohibit counters MV 4 Hill Giant") {
                    game.isInGraveyard(2, "Hill Giant") shouldBe true
                }
            }
        }
    }
}
