package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.mtg.sets.definitions.tla.AvatarTheLastAirbenderSet
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for Badgermole Cub.
 *
 * Card reference:
 * - Badgermole Cub {1}{G} — Creature — Badger Mole 2/2
 *   When this creature enters, earthbend 1.
 *   Whenever you tap a creature for mana, add an additional {G}.
 *
 * Test cases:
 * 1. ETB earthbend 1 — animates a land into a 0/0 creature with a +1/+1 counter
 * 2. Additional {G} when a creature is tapped for mana
 * 3. No additional {G} when a land is tapped for mana (only creatures trigger it)
 */
class BadgermoleCubScenarioTest : ScenarioTestBase() {

    // A simple creature with {T}: Add {G} — used to test the "tap creature for mana" trigger.
    private val tapForGreenCreature = card("Tap-for-Green Creature") {
        manaCost = "{G}"
        colorIdentity = "G"
        typeLine = "Creature — Elf"
        power = 1
        toughness = 1
        oracleText = "{T}: Add {G}."
        activatedAbility {
            cost = Costs.Tap
            effect = Effects.AddMana(Color.GREEN, 1)
            manaAbility = true
        }
    }

    init {
        cardRegistry.register(AvatarTheLastAirbenderSet.cards)
        cardRegistry.register(tapForGreenCreature)

        // ------------------------------------------------------------------
        // Earthbend 1 on ETB
        // ------------------------------------------------------------------
        context("ETB earthbend 1") {

            test("animates a land you control into a 1/1 creature with haste") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Badgermole Cub")
                    .withLandsOnBattlefield(1, "Forest", 3) // {1}{G} + earthbend target
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Cast Badgermole Cub, ETB trigger fires for earthbend 1
                game.castSpell(1, "Badgermole Cub")
                game.resolveStack() // resolves spell; ETB trigger goes on stack, then pauses for target

                // Pick one of the Forests as the earthbend target
                val forests = game.findAllPermanents("Forest")
                val targetForest = forests.first()

                withClue("Should pause for earthbend target selection") {
                    game.hasPendingDecision() shouldBe true
                }
                game.selectTargets(listOf(targetForest))
                game.resolveStack() // earthbend effect resolves

                // The targeted Forest is now a 0/0 creature + 1 +1/+1 counter = 1/1
                val clientState = game.getClientState(1)
                val forestInfo = clientState.cards[targetForest]
                withClue("Earthbended land should be visible as a creature") {
                    forestInfo shouldNotBe null
                }
                withClue("Earthbended land should be 1/1 (0/0 base + 1 counter)") {
                    forestInfo!!.power shouldBe 1
                    forestInfo.toughness shouldBe 1
                }
                withClue("Badgermole Cub should be on the battlefield") {
                    game.isOnBattlefield("Badgermole Cub") shouldBe true
                }
            }
        }

        // ------------------------------------------------------------------
        // Additional {G} when tapping a creature for mana
        // ------------------------------------------------------------------
        context("Additional {G} from creature mana tap") {

            test("adds 1 extra {G} when tapping a creature for mana") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Badgermole Cub")
                    .withCardOnBattlefield(1, "Tap-for-Green Creature")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val creatureId = game.findPermanent("Tap-for-Green Creature")!!
                val cardDef = cardRegistry.getCard("Tap-for-Green Creature")!!
                val ability = cardDef.script.activatedAbilities.first()

                // Activate {T}: Add {G}
                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = creatureId,
                        abilityId = ability.id,
                        targets = emptyList()
                    )
                )

                withClue("Mana ability should activate without error: ${result.error}") {
                    result.error shouldBe null
                }

                val manaPool = game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()
                withClue("Player should have 2 {G}: 1 from creature + 1 from Badgermole Cub") {
                    manaPool?.green shouldBe 2
                }
            }

            test("no extra {G} when tapping a land for mana") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Badgermole Cub")
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val forestId = game.findPermanent("Forest")!!
                val forestDef = cardRegistry.getCard("Forest")!!
                val manaAbility = forestDef.script.activatedAbilities.first()

                game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = forestId,
                        abilityId = manaAbility.id,
                        targets = emptyList()
                    )
                )

                val manaPool = game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()
                withClue("Tapping a land should produce exactly 1 {G} (Badgermole Cub doesn't trigger on lands)") {
                    manaPool?.green shouldBe 1
                }
            }

            test("adds 1 extra {G} when tapping an earthbended creature-land for mana") {
                // Cast Badgermole Cub — its ETB earthbends a Forest into a creature-land.
                // Then tap that creature-land for its Forest mana ability.
                // Since it is projected as a creature, Badgermole Cub should trigger.
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Badgermole Cub")
                    .withLandsOnBattlefield(1, "Forest", 3) // 2 for casting, 1 stays for earthbend
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Cast Badgermole Cub: auto-taps 2 Forests, ETB fires, pauses for earthbend target
                game.castSpell(1, "Badgermole Cub")
                game.resolveStack()

                withClue("Should pause for earthbend target selection") {
                    game.hasPendingDecision() shouldBe true
                }

                // Find the remaining untapped Forest to earthbend
                val allForests = game.findAllPermanents("Forest")
                val untappedForest = allForests.first { fid ->
                    game.state.getEntity(fid)?.has<TappedComponent>() == false
                }

                game.selectTargets(listOf(untappedForest))
                game.resolveStack() // earthbend resolves: Forest → 1/1 creature-land

                // Tap the now-creature-land for its Forest mana ability
                val forestDef = cardRegistry.getCard("Forest")!!
                val forestManaAbilityId = forestDef.script.activatedAbilities.first().id

                game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = untappedForest,
                        abilityId = forestManaAbilityId,
                        targets = emptyList()
                    )
                )

                val manaPool = game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()
                withClue("Earthbended Forest (creature-land) should produce 2 {G}: 1 from land + 1 from Badgermole Cub") {
                    manaPool?.green shouldBe 2
                }
            }

            test("no extra {G} without Badgermole Cub") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Tap-for-Green Creature")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val creatureId = game.findPermanent("Tap-for-Green Creature")!!
                val cardDef = cardRegistry.getCard("Tap-for-Green Creature")!!
                val ability = cardDef.script.activatedAbilities.first()

                game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = creatureId,
                        abilityId = ability.id,
                        targets = emptyList()
                    )
                )

                val manaPool = game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()
                withClue("Without Badgermole Cub, creature tap produces exactly 1 {G}") {
                    manaPool?.green shouldBe 1
                }
            }
        }
    }
}
