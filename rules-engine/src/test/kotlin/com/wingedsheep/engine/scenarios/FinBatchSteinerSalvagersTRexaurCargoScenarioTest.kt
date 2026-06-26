package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.fin.cards.AdelbertSteiner
import com.wingedsheep.mtg.sets.definitions.fin.cards.AlBhedSalvagers
import com.wingedsheep.mtg.sets.definitions.fin.cards.BalambTRexaur
import com.wingedsheep.mtg.sets.definitions.fin.cards.CargoShip
import com.wingedsheep.mtg.sets.definitions.fin.cards.LionHeart
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

/**
 * Scenario coverage for the FIN batch: Adelbert Steiner (+1/+1 per Equipment you control),
 * Al Bhed Salvagers (drain on a creature/artifact you control dying), Balamb T-Rexaur
 * (ETB gain 3 life), and Cargo Ship (Flying/vigilance + tap mana ability).
 */
class FinBatchSteinerSalvagersTRexaurCargoScenarioTest : FunSpec({

    fun createDriver(vararg cards: com.wingedsheep.sdk.model.CardDefinition): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        cards.forEach { driver.registerCard(it) }
        return driver
    }

    // -----------------------------------------------------------------------------------------
    // Adelbert Steiner
    // -----------------------------------------------------------------------------------------

    test("Adelbert Steiner gets +1/+1 for each Equipment you control") {
        val driver = createDriver(AdelbertSteiner, LionHeart)
        driver.initMirrorMatch(deck = Deck.of("Plains" to 30), startingLife = 20)
        val active = driver.activePlayer!!

        val steiner = driver.putCreatureOnBattlefield(active, "Adelbert Steiner")

        // No Equipment: base 2/1.
        driver.state.projectedState.getPower(steiner) shouldBe 2
        driver.state.projectedState.getToughness(steiner) shouldBe 1

        // One Equipment (unattached) you control: 3/2.
        driver.putPermanentOnBattlefield(active, "Lion Heart")
        driver.state.projectedState.getPower(steiner) shouldBe 3
        driver.state.projectedState.getToughness(steiner) shouldBe 2

        // Two Equipment: 4/3.
        driver.putPermanentOnBattlefield(active, "Lion Heart")
        driver.state.projectedState.getPower(steiner) shouldBe 4
        driver.state.projectedState.getToughness(steiner) shouldBe 3
    }

    // -----------------------------------------------------------------------------------------
    // Al Bhed Salvagers
    // -----------------------------------------------------------------------------------------

    test("Al Bhed Salvagers drains when a creature you control dies") {
        val driver = createDriver(AlBhedSalvagers)
        driver.initMirrorMatch(deck = Deck.of("Swamp" to 20, "Mountain" to 20), startingLife = 20)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val you = driver.activePlayer!!
        val opponent = driver.getOpponent(you)
        val youLifeBefore = driver.getLifeTotal(you)
        val oppLifeBefore = driver.getLifeTotal(opponent)

        driver.putCreatureOnBattlefield(you, "Al Bhed Salvagers")
        val fodder = driver.putCreatureOnBattlefield(you, "Centaur Courser") // 3/3

        // Opponent bolts your creature; it dies and triggers the drain.
        driver.giveMana(opponent, Color.RED, 1)
        val bolt = driver.putCardInHand(opponent, "Lightning Bolt")
        driver.passPriority(you)
        driver.castSpellWithTargets(opponent, bolt, listOf(ChosenTarget.Permanent(fodder))).error shouldBe null

        // Resolve the bolt, then the drain trigger (targeting the lone opponent).
        var safety = 0
        while (driver.stackSize > 0 && safety < 30) {
            val pending = driver.state.pendingDecision
            if (pending != null) {
                driver.submitTargetSelection(pending.playerId, listOf(opponent))
            } else {
                driver.bothPass()
            }
            safety++
        }

        driver.getLifeTotal(opponent) shouldBe (oppLifeBefore - 1)
        driver.getLifeTotal(you) shouldBe (youLifeBefore + 1)
    }

    // -----------------------------------------------------------------------------------------
    // Balamb T-Rexaur
    // -----------------------------------------------------------------------------------------

    test("Balamb T-Rexaur gains 3 life when it enters") {
        val driver = createDriver(BalambTRexaur)
        driver.initMirrorMatch(deck = Deck.of("Forest" to 30), startingLife = 20)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val active = driver.activePlayer!!
        val lifeBefore = driver.getLifeTotal(active)

        val trex = driver.putCardInHand(active, "Balamb T-Rexaur")
        driver.giveMana(active, Color.GREEN, 6)
        driver.castSpell(active, trex).error shouldBe null

        // Resolve the spell, then the ETB life-gain trigger.
        var safety = 0
        while (driver.stackSize > 0 && safety < 20) {
            driver.bothPass(); safety++
        }

        driver.getLifeTotal(active) shouldBe (lifeBefore + 3)
        driver.state.getBattlefield().contains(trex) shouldBe true
    }

    // -----------------------------------------------------------------------------------------
    // Cargo Ship
    // -----------------------------------------------------------------------------------------

    test("Cargo Ship has flying and vigilance and taps for restricted mana") {
        val driver = createDriver(CargoShip)
        driver.initMirrorMatch(deck = Deck.of("Island" to 30), startingLife = 20)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val active = driver.activePlayer!!

        val ship = driver.putPermanentOnBattlefield(active, "Cargo Ship")

        driver.state.projectedState.hasKeyword(ship, Keyword.FLYING).shouldBeTrue()
        driver.state.projectedState.hasKeyword(ship, Keyword.VIGILANCE).shouldBeTrue()

        // Activating the {T}: Add {C} mana ability taps the Vehicle.
        val manaAbility = CargoShip.activatedAbilities.first { it.isManaAbility }
        val result = driver.submit(
            ActivateAbility(
                playerId = active,
                sourceId = ship,
                abilityId = manaAbility.id,
            )
        )
        result.isSuccess shouldBe true
        driver.isTapped(ship) shouldBe true
    }
})
