package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.fin.cards.DeadlyEmbrace
import com.wingedsheep.mtg.sets.definitions.fin.cards.MatoyaArchonElder
import com.wingedsheep.mtg.sets.definitions.fin.cards.RelmsSketching
import com.wingedsheep.mtg.sets.definitions.fin.cards.ScorpionSentinel
import com.wingedsheep.mtg.sets.definitions.fin.cards.XandeDarkMage
import com.wingedsheep.mtg.sets.definitions.inv.cards.Opt
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Scenario coverage for the FIN batch:
 *  - Scorpion Sentinel — conditional +3/+0 at seven lands (projected stats).
 *  - Xande, Dark Mage — dynamic +1/+1 per noncreature, nonland card in graveyard.
 *  - Matoya, Archon Elder — scry/surveil triggers an extra draw.
 *  - Deadly Embrace — destroy + draw "for each creature that died this turn", with the
 *    just-destroyed creature counted (resolution timing).
 *  - Relm's Sketching — token copy of a target permanent.
 */
class FinBatchSentinelMatoyaXandeScenarioTest : FunSpec({

    val projector = StateProjector()

    fun createDriver(vararg cards: CardDefinition): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        cards.forEach { driver.registerCard(it) }
        return driver
    }

    // Resolve the whole stack (auto-resolving scry/library-order decisions) without advancing
    // past the current step, so end-of-turn cleanup never perturbs hand counts.
    fun GameTestDriver.resolveStackFully() {
        var guard = 0
        while ((stackSize > 0 || state.pendingDecision != null) && guard++ < 40) {
            if (state.pendingDecision != null) {
                autoResolveDecision()
            } else {
                passPriority(state.priorityPlayerId ?: player1)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Scorpion Sentinel
    // ---------------------------------------------------------------------------------------------

    test("Scorpion Sentinel gets +3/+0 only while you control seven or more lands") {
        val driver = createDriver(ScorpionSentinel)
        driver.initMirrorMatch(deck = Deck.of("Island" to 30), startingLife = 20)
        val active = driver.activePlayer!!

        val sentinel = driver.putCreatureOnBattlefield(active, "Scorpion Sentinel")

        // Six lands: base 1/4.
        repeat(6) { driver.putLandOnBattlefield(active, "Island") }
        projector.getProjectedPower(driver.state, sentinel) shouldBe 1
        projector.getProjectedToughness(driver.state, sentinel) shouldBe 4

        // Seventh land: +3/+0 → 4/4.
        driver.putLandOnBattlefield(active, "Island")
        projector.getProjectedPower(driver.state, sentinel) shouldBe 4
        projector.getProjectedToughness(driver.state, sentinel) shouldBe 4
    }

    // ---------------------------------------------------------------------------------------------
    // Xande, Dark Mage
    // ---------------------------------------------------------------------------------------------

    test("Xande gets +1/+1 for each noncreature, nonland card in your graveyard") {
        val driver = createDriver(XandeDarkMage)
        driver.initMirrorMatch(deck = Deck.of("Swamp" to 30), startingLife = 20)
        val active = driver.activePlayer!!

        val xande = driver.putCreatureOnBattlefield(active, "Xande, Dark Mage")

        // Empty graveyard: base 3/3.
        projector.getProjectedPower(driver.state, xande) shouldBe 3
        projector.getProjectedToughness(driver.state, xande) shouldBe 3

        // An instant (noncreature, nonland) counts: 4/4.
        driver.putCardInGraveyard(active, "Lightning Bolt")
        projector.getProjectedPower(driver.state, xande) shouldBe 4
        projector.getProjectedToughness(driver.state, xande) shouldBe 4

        // A creature card does NOT count.
        driver.putCardInGraveyard(active, "Centaur Courser")
        projector.getProjectedPower(driver.state, xande) shouldBe 4

        // A land card does NOT count.
        driver.putCardInGraveyard(active, "Swamp")
        projector.getProjectedPower(driver.state, xande) shouldBe 4

        // A second instant counts: 5/5.
        driver.putCardInGraveyard(active, "Doom Blade")
        projector.getProjectedPower(driver.state, xande) shouldBe 5
        projector.getProjectedToughness(driver.state, xande) shouldBe 5
    }

    // ---------------------------------------------------------------------------------------------
    // Matoya, Archon Elder
    // ---------------------------------------------------------------------------------------------

    test("Matoya draws a card whenever you scry") {
        val driver = createDriver(MatoyaArchonElder, Opt)
        driver.initMirrorMatch(deck = Deck.of("Island" to 30), startingLife = 20)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val active = driver.activePlayer!!

        driver.putCreatureOnBattlefield(active, "Matoya, Archon Elder")
        val opt = driver.putCardInHand(active, "Opt")
        driver.giveMana(active, Color.BLUE, 1)

        val handBefore = driver.getHandSize(active) // includes Opt

        driver.castSpell(active, opt)
        driver.resolveStackFully()

        // Opt: −1 (cast) +1 (its own draw) +1 (Matoya's scry-triggered draw) = handBefore + 1.
        driver.getHandSize(active) shouldBe handBefore + 1
    }

    // ---------------------------------------------------------------------------------------------
    // Deadly Embrace
    // ---------------------------------------------------------------------------------------------

    test("Deadly Embrace destroys the creature and draws for it (counted as died this turn)") {
        val driver = createDriver(DeadlyEmbrace)
        driver.initMirrorMatch(deck = Deck.of("Swamp" to 30), startingLife = 20)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val active = driver.activePlayer!!
        val opponent = driver.getOpponent(active)

        val victim = driver.putCreatureOnBattlefield(opponent, "Centaur Courser")
        val spell = driver.putCardInHand(active, "Deadly Embrace")
        driver.giveMana(active, Color.BLACK, 5) // {3}{B}{B}

        val handBefore = driver.getHandSize(active) // includes Deadly Embrace

        driver.castSpell(active, spell, listOf(victim))
        driver.resolveStackFully()

        // The creature is destroyed and counts as having died this turn → draw exactly 1.
        // Hand: handBefore −1 (spell cast) +1 (draw) = handBefore. Drawing 0 (wrong timing)
        // would leave handBefore − 1.
        driver.state.getBattlefield().contains(victim) shouldBe false
        driver.getHandSize(active) shouldBe handBefore
    }

    // ---------------------------------------------------------------------------------------------
    // Relm's Sketching
    // ---------------------------------------------------------------------------------------------

    test("Relm's Sketching creates a token copy of the targeted creature") {
        val driver = createDriver(RelmsSketching)
        driver.initMirrorMatch(deck = Deck.of("Island" to 30), startingLife = 20)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val active = driver.activePlayer!!

        val original = driver.putCreatureOnBattlefield(active, "Centaur Courser")
        val spell = driver.putCardInHand(active, "Relm's Sketching")
        driver.giveMana(active, Color.BLUE, 4) // {2}{U}{U}

        driver.castSpell(active, spell, listOf(original))
        driver.resolveStackFully()

        val coursers = driver.state.getBattlefield()
            .filter { driver.getCardName(it) == "Centaur Courser" }
        coursers.size shouldBe 2

        val token = coursers.first { it != original }
        driver.state.getEntity(token)!!.has<TokenComponent>() shouldBe true
    }
})
