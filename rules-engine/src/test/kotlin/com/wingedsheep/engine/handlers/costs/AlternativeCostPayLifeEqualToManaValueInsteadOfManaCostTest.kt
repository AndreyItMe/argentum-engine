package com.wingedsheep.engine.handlers.costs

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.AdditionalCost
import com.wingedsheep.sdk.scripting.SelfAlternativeCost
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * BDD tests for the alternative-cost primitive: "pay life equal to a spell's mana value
 * rather than pay its mana cost."
 *
 * A spell may declare [SelfAlternativeCost] with [AdditionalCost.PayLife] equal to its mana
 * value and an empty mana component, modelling the "pay life instead of mana" substitution.
 * The engine must deduct the declared life amount when the alternative cost is chosen, and must
 * reject the cast when the caster's life total is below the required payment.
 *
 * These tests are RED until AlternativeCostPayLifeEqualToManaValueInsteadOfManaCostHandler
 * is wired into the cast-spell execution path.
 */
class AlternativeCostPayLifeEqualToManaValueInsteadOfManaCostTest : FunSpec({

    // Spell with mana value 5. Its self-alternative cost declares "pay no mana, pay 5 life"
    // — the life amount intentionally equals the spell's mana value to model the mechanic.
    val lifePayCreature = card("Life-Cost Test Creature") {
        manaCost = "{3}{B}{B}"   // mana value = 5
        typeLine = "Creature — Horror"
        power = 4
        toughness = 4
        selfAlternativeCost = SelfAlternativeCost(
            manaCost = "{0}",
            additionalCosts = listOf(AdditionalCost.PayLife(5))
        )
    }

    fun createDriver(startingLife: Int = 20): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(lifePayCreature))
        driver.initMirrorMatch(deck = Deck.of("Forest" to 20), startingLife = startingLife)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return driver
    }

    test("casting via pay-life alternative cost deducts life equal to mana value and pays no mana") {
        // GIVEN the player has 20 life, an empty mana pool, and a castable spell with mana value 5
        val driver = createDriver(startingLife = 20)
        val player = driver.activePlayer!!
        val spellId = driver.putCardInHand(player, "Life-Cost Test Creature")

        // WHEN the player casts the spell choosing to pay the alternative life cost
        val result = driver.submit(
            CastSpell(
                playerId = player,
                cardId = spellId,
                useAlternativeCost = true,
                paymentStrategy = PaymentStrategy.FromPool
            )
        )

        // THEN the spell is successfully cast and placed on the stack
        result.isSuccess shouldBe true
        driver.stackSize shouldBe 1

        // AND the player's life total becomes 15 (reduced by exactly the spell's mana value of 5)
        driver.getLifeTotal(player) shouldBe 15

        // AND no mana was deducted (the life payment replaced the mana cost entirely)
    }

    test("casting via pay-life alternative cost is rejected when life is below the spell's mana value") {
        // GIVEN the player's life total (3) is below the spell's mana value (5)
        val driver = createDriver(startingLife = 3)
        val player = driver.activePlayer!!
        val spellId = driver.putCardInHand(player, "Life-Cost Test Creature")

        // WHEN the player attempts to cast the spell choosing to pay the alternative life cost
        val result = driver.submit(
            CastSpell(
                playerId = player,
                cardId = spellId,
                useAlternativeCost = true,
                paymentStrategy = PaymentStrategy.FromPool
            )
        )

        // THEN the engine rejects the cast as an illegal cost payment
        result.isSuccess shouldBe false
    }
})
