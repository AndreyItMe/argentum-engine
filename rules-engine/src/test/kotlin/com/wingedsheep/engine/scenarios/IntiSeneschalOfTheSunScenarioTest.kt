package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.SelectManaSourcesDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Inti, Seneschal of the Sun ({1}{R} 2/2 Legendary Creature — Human Knight) — Lost Caverns of Ixalan.
 *
 * "Whenever you attack, you may discard a card. When you do, put a +1/+1 counter on target attacking
 *  creature. It gains trample until end of turn.
 *  Whenever you discard one or more cards, exile the top card of your library. You may play that card
 *  until your next end step."
 *
 * Ability 1 is a reflexive [com.wingedsheep.sdk.scripting.effects.ReflexiveTriggerEffect]: the optional
 * discard is the action; only if a card is actually discarded does "put a +1/+1 counter on target
 * attacking creature; it gains trample" go on the stack. Ability 2 ([com.wingedsheep.sdk.dsl.Triggers.YouDiscard])
 * fires off that same discard, impulse-exiling the top of library with a play window that lasts until
 * the controller's next end step. This test exercises both the discard path and the decline path.
 */
class IntiSeneschalOfTheSunScenarioTest : FunSpec({

    val projector = StateProjector()

    fun driver(): GameTestDriver = GameTestDriver().apply {
        registerCards(TestCards.all)
        initMirrorMatch(deck = Deck.of("Mountain" to 40), startingLife = 20)
    }

    fun GameTestDriver.plusOneCounters(id: EntityId): Int =
        state.getEntity(id)?.get<CountersComponent>()?.counters?.get(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    /** Drive every pending decision/stack resolution to completion, capturing whether a target was chosen. */
    fun GameTestDriver.resolveAttackTriggers(
        you: EntityId,
        target: EntityId,
        discard: Boolean
    ): Boolean {
        var targeted = false
        var guard = 0
        while (guard++ < 80) {
            when (val dec = pendingDecision) {
                is YesNoDecision -> submitYesNo(you, discard)          // "you may discard a card"
                is SelectCardsDecision -> submitCardSelection(you, dec.options.take(dec.minSelections.coerceAtLeast(1)))
                is ChooseTargetsDecision -> { submitTargetSelection(you, listOf(target)); targeted = true }
                is SelectManaSourcesDecision -> submitManaAutoPayOrDecline(you, autoPay = false)
                else -> if (state.stack.isNotEmpty()) bothPass() else return targeted
            }
        }
        return targeted
    }

    test("discarding on attack adds a +1/+1 counter and trample to the target attacker and impulse-exiles the top card") {
        val d = driver()
        val you = d.activePlayer!!
        val opponent = d.getOpponent(you)

        val inti = d.putCreatureOnBattlefield(you, "Inti, Seneschal of the Sun")
        d.removeSummoningSickness(inti)
        // Deterministic impulse target on top of library.
        d.putCardOnTopOfLibrary(you, "Savannah Lions")

        d.plusOneCounters(inti) shouldBe 0
        val handBefore = d.getHandSize(you)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        d.declareAttackers(you, listOf(inti), opponent)

        val targeted = d.resolveAttackTriggers(you, target = inti, discard = true)
        targeted shouldBe true

        // Ability 1: counter + trample on the attacking creature.
        d.plusOneCounters(inti) shouldBe 1
        projector.project(d.state).hasKeyword(inti, Keyword.TRAMPLE) shouldBe true

        // The discard itself cost one card from hand.
        d.getHandSize(you) shouldBe handBefore - 1

        // Ability 2: top card of library impulse-exiled with a play permission.
        d.getExileCardNames(you).contains("Savannah Lions") shouldBe true
        val exiledIds = d.getExile(you)
        d.state.mayPlayPermissions.any { perm -> exiledIds.any { it in perm.cardIds } } shouldBe true
    }

    test("declining the optional discard leaves no counter, no trample, and no impulse exile") {
        val d = driver()
        val you = d.activePlayer!!
        val opponent = d.getOpponent(you)

        val inti = d.putCreatureOnBattlefield(you, "Inti, Seneschal of the Sun")
        d.removeSummoningSickness(inti)
        d.putCardOnTopOfLibrary(you, "Savannah Lions")

        val handBefore = d.getHandSize(you)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        d.declareAttackers(you, listOf(inti), opponent)

        val targeted = d.resolveAttackTriggers(you, target = inti, discard = false)

        targeted shouldBe false
        d.plusOneCounters(inti) shouldBe 0
        projector.project(d.state).hasKeyword(inti, Keyword.TRAMPLE) shouldBe false
        d.getHandSize(you) shouldBe handBefore
        d.getExileCardNames(you).contains("Savannah Lions") shouldBe false
        d.getExile(you).isEmpty() shouldBe true
    }
})
