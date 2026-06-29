package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.legalactions.EnumerationMode
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.PlayWithFixedAlternativeManaCostComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Airbend (Avatar: The Last Airbender) — "Exile target nonland permanent. While it's exiled, its
 * owner may cast it for {2} rather than its mana cost."
 *
 * Proves the engine feature end-to-end through an inline test instant ([airbendTester]) that uses
 * [Effects.Airbend]:
 *   - the targeted permanent is exiled to *its owner's* exile;
 *   - only the *owner* (not the airbend's controller) is granted a may-play permission;
 *   - the recast cost is a fixed {2}, *replacing* the printed mana cost (not added to it);
 *   - the owner can actually recast it from exile paying {2} of any color, and the fixed-cost
 *     component is cleaned up once it leaves exile.
 */
class AirbendScenarioTest : FunSpec({

    // {W} instant: "Airbend target nonland permanent." Target-agnostic Effects.Airbend() airbends
    // whatever the spell targets (CardSource.ChosenTargets).
    val airbendTester = card("Airbend Tester") {
        manaCost = "{W}"
        colorIdentity = "W"
        typeLine = "Instant"
        oracleText = "Airbend target nonland permanent."
        spell {
            target("target nonland permanent", Targets.NonlandPermanent)
            effect = Effects.Airbend()
        }
    }

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(airbendTester))
        return driver
    }

    /** The "cast this exiled card" legal action for [player], or null if they may not. */
    fun castAction(driver: GameTestDriver, player: EntityId, cardId: EntityId): LegalAction? =
        LegalActionEnumerator.create(driver.cardRegistry)
            .enumerate(driver.state, player, EnumerationMode.FULL)
            .firstOrNull { it.actionType == "CastSpell" && (it.action as? CastSpell)?.cardId == cardId }

    test("airbending an opponent's permanent exiles it to its owner and grants only the owner a {2} recast") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Plains" to 40), skipMulligans = true, startingLife = 20)
        val me = driver.activePlayer!!
        val opp = driver.getOpponent(me)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        // Opponent controls (and owns) Grizzly Bears — printed cost {1}{G}, which needs green mana.
        val bears = driver.putCreatureOnBattlefield(opp, "Grizzly Bears")
        val spell = driver.putCardInHand(me, "Airbend Tester")
        driver.putLandOnBattlefield(me, "Plains") // {W} for the airbend spell

        driver.submitSuccess(
            CastSpell(
                playerId = me,
                cardId = spell,
                targets = listOf(ChosenTarget.Permanent(bears)),
                paymentStrategy = PaymentStrategy.AutoPay
            )
        )
        driver.bothPass()

        // Exiled into its OWNER's exile pile, and off the battlefield.
        driver.state.getZone(ZoneKey(opp, Zone.EXILE)) shouldContain bears
        driver.state.getZone(ZoneKey(opp, Zone.BATTLEFIELD)) shouldNotContain bears

        // Only the owner may recast it, for a fixed {2} (not the printed {1}{G}). The airbend's
        // controller gets no permission.
        val ownerAction = castAction(driver, opp, bears)
        ownerAction.shouldNotBeNull()
        ownerAction.manaCostString shouldBe "{2}"
        castAction(driver, me, bears).shouldBeNull()
    }

    test("airbending your own permanent lets you recast it for {2}, replacing the printed cost") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Plains" to 40), skipMulligans = true, startingLife = 20)
        val me = driver.activePlayer!!
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        // I own Grizzly Bears ({1}{G}); airbend it, then recast it the same turn (sorcery speed —
        // I keep priority in my main) using only white mana, proving {1}{G} became {2} generic.
        val bears = driver.putCreatureOnBattlefield(me, "Grizzly Bears")
        val spell = driver.putCardInHand(me, "Airbend Tester")
        repeat(3) { driver.putLandOnBattlefield(me, "Plains") } // {W} for airbend + {2} for the recast

        driver.submitSuccess(
            CastSpell(
                playerId = me,
                cardId = spell,
                targets = listOf(ChosenTarget.Permanent(bears)),
                paymentStrategy = PaymentStrategy.AutoPay
            )
        )
        driver.bothPass()

        driver.state.getZone(ZoneKey(me, Zone.EXILE)) shouldContain bears
        val recast = castAction(driver, me, bears)
        recast.shouldNotBeNull()
        recast.manaCostString shouldBe "{2}"

        // Recast from exile for {2} of white mana — impossible at the printed {1}{G}.
        driver.submitSuccess(CastSpell(playerId = me, cardId = bears, paymentStrategy = PaymentStrategy.AutoPay))
        driver.bothPass()

        // Back on the battlefield; the fixed-cost stamp is cleaned up so a later exile is unaffected.
        driver.state.getZone(ZoneKey(me, Zone.BATTLEFIELD)) shouldContain bears
        driver.state.getEntity(bears)?.get<PlayWithFixedAlternativeManaCostComponent>().shouldBeNull()
    }
})
