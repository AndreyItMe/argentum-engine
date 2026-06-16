package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.sos.cards.ResonatingLute
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Resonating Lute (Secrets of Strixhaven #221).
 *
 * Resonating Lute ({2}{U}{R}, Artifact):
 *   Lands you control have "{T}: Add two mana of any one color. Spend this mana only to cast
 *   instant and sorcery spells."
 *   {T}: Draw a card. Activate only if you have seven or more cards in your hand.
 *
 * Pins (1) the static grant landing on each land you control (and adding 2 mana of the chosen
 * colour), and (2) the hand-size activation gate on the draw ability (legal at 7+, illegal below).
 */
class ResonatingLuteScenarioTest : FunSpec({

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.registerCards(listOf(ResonatingLute))
        return driver
    }

    val drawAbilityId = ResonatingLute.activatedAbilities.first().id

    fun start(driver: GameTestDriver): com.wingedsheep.sdk.model.EntityId {
        driver.initMirrorMatch(deck = Deck.of("Island" to 40), startingLife = 20)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return driver.activePlayer!!
    }

    test("grants each land you control a {T}: add two mana of one color ability") {
        val driver = createDriver()
        val p = start(driver)
        val opp = driver.getOpponent(p)

        driver.putPermanentOnBattlefield(p, "Resonating Lute") // artifact host
        val myLand = driver.putLandOnBattlefield(p, "Mountain")
        val oppLand = driver.putLandOnBattlefield(opp, "Forest")

        // My land has the granted mana ability; the opponent's does not.
        val myGrants = driver.state.grantedActivatedAbilities.filter { it.entityId == myLand }
        myGrants.size shouldBe 1
        driver.state.grantedActivatedAbilities.filter { it.entityId == oppLand }.size shouldBe 0

        // Activating it adds two mana of one chosen color (mana ability — resolves immediately).
        driver.submitSuccess(
            ActivateAbility(playerId = p, sourceId = myLand, abilityId = myGrants[0].ability.id),
        )
        val pool = driver.state.getEntity(p)?.get<ManaPoolComponent>()!!
        val total = pool.white + pool.blue + pool.black + pool.red + pool.green + pool.colorless
        total shouldBe 2
    }

    test("draw ability is legal with 7+ cards in hand") {
        val driver = createDriver()
        val p = start(driver)

        val lute = driver.putPermanentOnBattlefield(p, "Resonating Lute")
        repeat(7) { driver.putCardInHand(p, "Island") }
        driver.putCardOnTopOfLibrary(p, "Island")

        val result = driver.submit(
            ActivateAbility(playerId = p, sourceId = lute, abilityId = drawAbilityId),
        )
        result.isSuccess shouldBe true
        driver.isTapped(lute) shouldBe true
    }

    test("draw ability is illegal with fewer than 7 cards in hand") {
        val driver = createDriver()
        val p = start(driver)

        val lute = driver.putPermanentOnBattlefield(p, "Resonating Lute")
        // Reduce the opening hand to exactly 6 cards (below the 7-card gate).
        while (driver.getHandSize(p) > 6) {
            driver.moveToGraveyard(driver.getHand(p).first())
        }
        while (driver.getHandSize(p) < 6) {
            driver.putCardInHand(p, "Island")
        }
        driver.getHandSize(p) shouldBe 6

        val result = driver.submit(
            ActivateAbility(playerId = p, sourceId = lute, abilityId = drawAbilityId),
        )
        result.isSuccess shouldBe false
        driver.isTapped(lute) shouldBe false
    }
})
