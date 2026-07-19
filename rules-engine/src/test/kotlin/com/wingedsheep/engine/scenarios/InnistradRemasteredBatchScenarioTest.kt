package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.avr.cards.AngelsTomb
import com.wingedsheep.mtg.sets.definitions.emn.cards.GrappleWithThePast
import com.wingedsheep.mtg.sets.definitions.mid.cards.EccentricFarmer
import com.wingedsheep.mtg.sets.definitions.mid.cards.SiegeZombie
import com.wingedsheep.mtg.sets.definitions.zen.cards.BlazingTorch
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import com.wingedsheep.sdk.scripting.GrantActivatedAbility
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

/**
 * Scenario coverage for the Innistrad Remastered batch: Grapple with the Past (EMN), Eccentric
 * Farmer (MID), Siege Zombie (MID), Angel's Tomb (AVR) and Blazing Torch (ZEN).
 *
 * Every card composes existing SDK primitives, so these tests prove the *composition* — mill
 * ordering vs the graveyard pick, the optional "you may" declines, the tap-three cost shape, and
 * the granted-ability / damage-source wiring on the Equipment.
 */
class InnistradRemasteredBatchScenarioTest : FunSpec({

    val batch = listOf(GrappleWithThePast, EccentricFarmer, SiegeZombie, AngelsTomb, BlazingTorch)
    val projector = StateProjector()

    fun setup(deck: Deck = Deck.of("Forest" to 40)): GameTestDriver = GameTestDriver().apply {
        registerCards(TestCards.all + batch)
        initMirrorMatch(deck = deck, startingLife = 20, skipMulligans = true)
    }

    // ── Grapple with the Past ────────────────────────────────────────────────

    test("Grapple with the Past: mills three, then returns a just-milled creature card to hand") {
        val d = setup()
        val you = d.activePlayer!!
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)

        // Seed the top three so the mill is deterministic: two creatures and a land.
        val third = d.putCardOnTopOfLibrary(you, "Grizzly Bears")
        val second = d.putCardOnTopOfLibrary(you, "Forest")
        val first = d.putCardOnTopOfLibrary(you, "Hill Giant")
        val milled = setOf(first, second, third)

        d.giveMana(you, Color.GREEN, 2)
        val grapple = d.putCardInHand(you, "Grapple with the Past")
        d.castSpell(you, grapple).error shouldBe null
        d.bothPass()

        // The three cards are milled *before* the choice, so all three are offered back.
        val decision = d.pendingDecision as SelectCardsDecision
        decision.maxSelections shouldBe 1
        decision.minSelections shouldBe 0
        milled.forEach { decision.options shouldContain it }

        d.submitCardSelection(you, listOf(first)).error shouldBe null
        while (d.pendingDecision == null && d.stackSize > 0) d.bothPass()

        d.getHand(you) shouldContain first
        d.getGraveyard(you) shouldContain second
        d.getGraveyard(you) shouldContain third
    }

    test("Grapple with the Past: declining the return leaves all three milled cards in the graveyard") {
        val d = setup()
        val you = d.activePlayer!!
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val third = d.putCardOnTopOfLibrary(you, "Grizzly Bears")
        val second = d.putCardOnTopOfLibrary(you, "Forest")
        val first = d.putCardOnTopOfLibrary(you, "Hill Giant")

        d.giveMana(you, Color.GREEN, 2)
        val grapple = d.putCardInHand(you, "Grapple with the Past")
        val handBefore = d.getHandSize(you)
        d.castSpell(you, grapple).error shouldBe null
        d.bothPass()

        // "you may" = choose zero.
        d.submitCardSelection(you, emptyList()).error shouldBe null
        while (d.pendingDecision == null && d.stackSize > 0) d.bothPass()

        // Hand shrank by exactly the Grapple itself; every milled card stayed in the graveyard.
        d.getHandSize(you) shouldBe handBefore - 1
        listOf(first, second, third).forEach { d.getGraveyard(you) shouldContain it }
    }

    // ── Eccentric Farmer ─────────────────────────────────────────────────────

    test("Eccentric Farmer: ETB mills three and offers only land cards from the graveyard") {
        val d = setup()
        val you = d.activePlayer!!
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val creatureOnTop = d.putCardOnTopOfLibrary(you, "Grizzly Bears")
        val landOnTop = d.putCardOnTopOfLibrary(you, "Forest")
        val otherCreature = d.putCardOnTopOfLibrary(you, "Hill Giant")

        d.giveMana(you, Color.GREEN, 3)
        val farmer = d.putCardInHand(you, "Eccentric Farmer")
        d.castSpell(you, farmer).error shouldBe null
        d.bothPass() // resolve the creature spell
        while (d.pendingDecision == null && d.stackSize > 0) d.bothPass()

        val decision = d.pendingDecision as SelectCardsDecision
        decision.options shouldContain landOnTop
        decision.options.contains(creatureOnTop) shouldBe false
        decision.options.contains(otherCreature) shouldBe false

        d.submitCardSelection(you, listOf(landOnTop)).error shouldBe null
        while (d.pendingDecision == null && d.stackSize > 0) d.bothPass()

        d.getHand(you) shouldContain landOnTop
        d.getGraveyard(you) shouldContain creatureOnTop
        d.getGraveyard(you) shouldContain otherCreature
    }

    // ── Siege Zombie ─────────────────────────────────────────────────────────

    test("Siege Zombie: taps three creatures (itself included) and drains each opponent for 1") {
        val d = setup(Deck.of("Swamp" to 40))
        val you = d.activePlayer!!
        val opponent = d.getOpponent(you)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val zombie = d.putCreatureOnBattlefield(you, "Siege Zombie")
        val bear = d.putCreatureOnBattlefield(you, "Grizzly Bears")
        val giant = d.putCreatureOnBattlefield(you, "Hill Giant")
        val abilityId = SiegeZombie.activatedAbilities.single().id

        // No `{T}` in the printed cost, so summoning sickness does not gate the tap and the
        // Zombie may tap itself as one of the three.
        d.submit(
            ActivateAbility(
                playerId = you,
                sourceId = zombie,
                abilityId = abilityId,
                costPayment = AdditionalCostPayment(tappedPermanents = listOf(zombie, bear, giant))
            )
        ).isSuccess shouldBe true

        listOf(zombie, bear, giant).forEach { d.isTapped(it) shouldBe true }
        d.bothPass()

        d.getLifeTotal(opponent) shouldBe 19
        d.getLifeTotal(you) shouldBe 20
    }

    test("Siege Zombie: cannot be activated with only two untapped creatures") {
        val d = setup(Deck.of("Swamp" to 40))
        val you = d.activePlayer!!
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val zombie = d.putCreatureOnBattlefield(you, "Siege Zombie")
        val bear = d.putCreatureOnBattlefield(you, "Grizzly Bears")
        val abilityId = SiegeZombie.activatedAbilities.single().id

        d.submit(
            ActivateAbility(
                playerId = you,
                sourceId = zombie,
                abilityId = abilityId,
                costPayment = AdditionalCostPayment(tappedPermanents = listOf(zombie, bear))
            )
        ).isSuccess shouldBe false
    }

    // ── Angel's Tomb ─────────────────────────────────────────────────────────

    test("Angel's Tomb: accepting the trigger animates it into a 3/3 flying Angel") {
        val d = setup(Deck.of("Plains" to 40))
        val you = d.activePlayer!!
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val tomb = d.putPermanentOnBattlefield(you, "Angel's Tomb")
        projector.project(d.state).isCreature(tomb) shouldBe false

        // Casting a creature triggers the Tomb.
        d.giveMana(you, Color.GREEN, 2)
        val bears = d.putCardInHand(you, "Grizzly Bears")
        d.castSpell(you, bears).error shouldBe null
        d.bothPass() // resolve the creature; the Tomb trigger goes on the stack
        while (d.pendingDecision == null && d.stackSize > 0) d.bothPass()

        (d.pendingDecision is YesNoDecision) shouldBe true
        d.submitYesNo(you, true).error shouldBe null
        while (d.pendingDecision == null && d.stackSize > 0) d.bothPass()

        val projected = projector.project(d.state)
        projected.isCreature(tomb) shouldBe true
        projected.getPower(tomb) shouldBe 3
        projected.getToughness(tomb) shouldBe 3
        projected.hasKeyword(tomb, Keyword.FLYING) shouldBe true
        // Still an artifact — the animation *adds* the creature type.
        projected.hasType(tomb, "ARTIFACT") shouldBe true
    }

    test("Angel's Tomb: declining the trigger leaves it a plain artifact") {
        val d = setup(Deck.of("Plains" to 40))
        val you = d.activePlayer!!
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val tomb = d.putPermanentOnBattlefield(you, "Angel's Tomb")

        d.giveMana(you, Color.GREEN, 2)
        val bears = d.putCardInHand(you, "Grizzly Bears")
        d.castSpell(you, bears).error shouldBe null
        d.bothPass()
        while (d.pendingDecision == null && d.stackSize > 0) d.bothPass()

        (d.pendingDecision is YesNoDecision) shouldBe true
        d.submitYesNo(you, false).error shouldBe null
        while (d.pendingDecision == null && d.stackSize > 0) d.bothPass()

        projector.project(d.state).isCreature(tomb) shouldBe false
    }

    // ── Blazing Torch ────────────────────────────────────────────────────────

    test("Blazing Torch: bearer's granted ability sacrifices the Torch and deals 2 damage") {
        val d = setup(Deck.of("Mountain" to 40))
        val you = d.activePlayer!!
        val opponent = d.getOpponent(you)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val bear = d.putCreatureOnBattlefield(you, "Grizzly Bears")
        d.removeSummoningSickness(bear)
        val torch = d.putPermanentOnBattlefield(you, "Blazing Torch")

        val equipId = BlazingTorch.activatedAbilities.single { it.isEquipAbility }.id
        d.giveColorlessMana(you, 1)
        d.submit(
            ActivateAbility(
                playerId = you,
                sourceId = torch,
                abilityId = equipId,
                targets = listOf(ChosenTarget.Permanent(bear))
            )
        ).isSuccess shouldBe true
        d.bothPass()
        d.state.getEntity(torch)?.get<AttachedToComponent>()?.targetId shouldBe bear

        // The quoted ability lives on the *creature*, granted by the Equipment.
        val grantedId = BlazingTorch.staticAbilities
            .filterIsInstance<GrantActivatedAbility>()
            .single()
            .ability
            .id

        d.submit(
            ActivateAbility(
                playerId = you,
                sourceId = bear,
                abilityId = grantedId,
                targets = listOf(ChosenTarget.Player(opponent))
            )
        ).isSuccess shouldBe true

        // `{T}` taps the bearer; the Torch is sacrificed as part of the cost, before resolution.
        d.isTapped(bear) shouldBe true
        d.findPermanent(you, "Blazing Torch") shouldBe null
        d.getGraveyard(you) shouldContain torch

        d.bothPass()
        d.getLifeTotal(opponent) shouldBe 18
    }

    test("Blazing Torch: equipped creature can't be blocked by a Zombie") {
        val d = setup(Deck.of("Mountain" to 40))
        val you = d.activePlayer!!
        val opponent = d.getOpponent(you)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val bear = d.putCreatureOnBattlefield(you, "Grizzly Bears")
        d.removeSummoningSickness(bear)
        val torch = d.putPermanentOnBattlefield(you, "Blazing Torch")
        val zombie = d.putCreatureOnBattlefield(opponent, "Siege Zombie")
        d.removeSummoningSickness(zombie)

        val equipId = BlazingTorch.activatedAbilities.single { it.isEquipAbility }.id
        d.giveColorlessMana(you, 1)
        d.submit(
            ActivateAbility(
                playerId = you,
                sourceId = torch,
                abilityId = equipId,
                targets = listOf(ChosenTarget.Permanent(bear))
            )
        ).isSuccess shouldBe true
        d.bothPass()

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        d.declareAttackers(you, listOf(bear), opponent).error shouldBe null
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)

        // The Zombie is an otherwise-legal blocker, but the Torch's evasion forbids it.
        (d.declareBlockers(opponent, mapOf(zombie to listOf(bear))).error != null) shouldBe true
    }
})
