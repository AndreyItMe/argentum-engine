package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.DoubleFacedComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Chrome Host Seedshark.
 *
 *   {2}{U} Creature — Phyrexian Shark, 2/4
 *   Flying
 *   Whenever you cast a noncreature spell, incubate X, where X is that spell's mana value.
 *
 * The trigger uses [com.wingedsheep.sdk.dsl.Effects.Incubate] with a [DynamicAmount]
 * resolved at trigger resolution time — `EntityReference.Triggering`'s `ManaValue`
 * is read off the spell still on the stack underneath the trigger.
 */
class ChromeHostSeedsharkScenarioTest : ScenarioTestBase() {

    init {
        context("Chrome Host Seedshark - Incubate X trigger") {

            test("triggers on a noncreature spell and creates an Incubator with X = mana value") {
                // Fact or Fiction is {3}{U} (mana value 4). Casting it triggers the Seedshark,
                // which should incubate 4 → an Incubator token with 4 +1/+1 counters.
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Chrome Host Seedshark")
                    .withCardInHand(1, "Fact or Fiction")
                    .withLandsOnBattlefield(1, "Island", 4)
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(1, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cast = game.castSpell(1, "Fact or Fiction")
                withClue("Fact or Fiction should cast: ${cast.error}") {
                    cast.error shouldBe null
                }

                // Resolve the Seedshark trigger (sits on top of Fact or Fiction).
                // Fact or Fiction itself will then start resolving and pause for the divvy decision —
                // by that point the Incubator already exists with its counters.
                game.resolveStack()

                val incubatorId = game.findPermanent("Incubator")
                withClue("Incubate trigger should have created an Incubator token") {
                    incubatorId.shouldNotBeNull()
                }

                val container = game.state.getEntity(incubatorId!!)!!

                val dfc = container.get<DoubleFacedComponent>()
                dfc.shouldNotBeNull()
                withClue("Incubator must enter as a DFC token") {
                    dfc.frontCardDefinitionId shouldBe "Incubator"
                    dfc.backCardDefinitionId shouldBe "Phyrexian"
                    dfc.currentFace shouldBe DoubleFacedComponent.Face.FRONT
                }

                val counters = container.get<CountersComponent>()
                counters.shouldNotBeNull()
                withClue("Incubator should have X = 4 +1/+1 counters (Fact or Fiction's mana value)") {
                    counters.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 4
                }
            }

            test("does not trigger on creature spells") {
                // Casting another creature should NOT trigger the Seedshark — only noncreature
                // spells. We mirror the cast flow but assert no Incubator appears.
                // Mossborn Hydra is {1}{G} (mana value 2). Use a green creature we already have.
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Chrome Host Seedshark")
                    .withCardInHand(1, "Mossborn Hydra")
                    .withLandsOnBattlefield(1, "Forest", 4)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cast = game.castSpell(1, "Mossborn Hydra")
                withClue("Mossborn Hydra should cast: ${cast.error}") {
                    cast.error shouldBe null
                }

                game.resolveStack()

                withClue("Casting a creature must not trigger Seedshark's incubate ability") {
                    game.findPermanent("Incubator") shouldBe null
                }
            }
        }
    }
}
