package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.effects.CompositeEffect
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Tests for [com.wingedsheep.sdk.scripting.effects.DoubleCountersEffect] — the one-shot
 * doubling of +1/+1 counters already on a creature (the engine gap behind Sage of the Fang).
 *
 * Two inline test instants exercise the primitive:
 *  - "Counter Doubler": "Double the number of +1/+1 counters on target creature."
 *  - "Boost and Double": "Put a +1/+1 counter on target creature, then double the number of
 *    +1/+1 counters on that creature." — mirrors Sage of the Fang's renew ability.
 */
class DoubleCountersTest : FunSpec({

    val counterDoubler = card("Counter Doubler") {
        manaCost = "{G}"
        typeLine = "Instant"
        oracleText = "Double the number of +1/+1 counters on target creature."
        spell {
            val target = target("target creature", Targets.Creature)
            effect = Effects.DoubleCounters(Counters.PLUS_ONE_PLUS_ONE, target)
        }
    }

    val boostAndDouble = card("Boost and Double") {
        manaCost = "{G}"
        typeLine = "Instant"
        oracleText =
            "Put a +1/+1 counter on target creature, then double the number of +1/+1 counters on that creature."
        spell {
            val target = target("target creature", Targets.Creature)
            effect = CompositeEffect(
                listOf(
                    Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 1, target),
                    Effects.DoubleCounters(Counters.PLUS_ONE_PLUS_ONE, target)
                )
            )
        }
    }

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(counterDoubler, boostAndDouble))
        return driver
    }

    fun plusCounters(driver: GameTestDriver, entityId: com.wingedsheep.sdk.model.EntityId): Int =
        driver.state.getEntity(entityId)?.get<CountersComponent>()
            ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    test("doubles the +1/+1 counters already on a creature") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Forest" to 20), startingLife = 20)

        val player1 = driver.activePlayer!!
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val spell = driver.putCardInHand(player1, "Counter Doubler")
        val creature = driver.putCreatureOnBattlefield(player1, "Savannah Lions")
        driver.addComponent(
            creature,
            CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 3))
        )
        driver.giveMana(player1, Color.GREEN, 1)

        driver.castSpell(player1, spell, targets = listOf(creature))
        driver.bothPass()

        // 3 existing counters → 3 more placed → 6 total.
        plusCounters(driver, creature) shouldBe 6
    }

    test("does nothing when the creature has no +1/+1 counters") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Forest" to 20), startingLife = 20)

        val player1 = driver.activePlayer!!
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val spell = driver.putCardInHand(player1, "Counter Doubler")
        val creature = driver.putCreatureOnBattlefield(player1, "Savannah Lions")
        driver.giveMana(player1, Color.GREEN, 1)

        driver.castSpell(player1, spell, targets = listOf(creature))
        driver.bothPass()

        plusCounters(driver, creature) shouldBe 0
    }

    test("put-then-double counts the freshly-added counter (Sage of the Fang shape)") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Forest" to 20), startingLife = 20)

        val player1 = driver.activePlayer!!
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val spell = driver.putCardInHand(player1, "Boost and Double")
        val creature = driver.putCreatureOnBattlefield(player1, "Savannah Lions")
        driver.addComponent(
            creature,
            CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 2))
        )
        driver.giveMana(player1, Color.GREEN, 1)

        driver.castSpell(player1, spell, targets = listOf(creature))
        driver.bothPass()

        // 2 existing + 1 added = 3, then doubled = 6.
        plusCounters(driver, creature) shouldBe 6
    }
})
