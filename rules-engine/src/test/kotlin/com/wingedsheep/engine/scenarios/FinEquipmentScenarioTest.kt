package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.CardScript
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Final Fantasy Equipment / weapons batch:
 *  - Buster Sword ({3} Equipment, +3/+2 + combat-damage "draw, then free-cast a spell with MV ≤ that damage")
 *  - Samurai's Katana ({2}{R} jobSelect Equipment, +2/+2, trample + haste, Samurai)
 *  - White Mage's Staff ({1}{W} jobSelect Equipment, +1/+1, granted "attacks → gain 1 life", Cleric)
 *  - Black Mage's Rod ({1}{B} jobSelect Equipment, +1/+0, granted "cast noncreature → 1 dmg each opponent", Wizard)
 *
 * The jobSelect ETB shell itself (create Hero token + auto-attach) is proven by JobSelectScenarioTest;
 * these tests focus on the per-card equipped-creature bonuses and granted abilities.
 */
class FinEquipmentScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    private val busterEquipId by lazy {
        cardRegistry.requireCard("Buster Sword").activatedAbilities[0].id
    }

    init {
        // A cheap sorcery (MV 1) used by the Buster Sword free-cast test.
        cardRegistry.register(
            CardDefinition.sorcery(
                name = "Cheap Draw",
                manaCost = ManaCost.parse("{U}"),
                oracleText = "Draw a card.",
                script = CardScript(spellEffect = Effects.DrawCards(1))
            )
        )
        // A sorcery with mana value 6 — above the 5 combat damage in the free-cast test, so it
        // must be filtered out of the "mana value ≤ that damage" free-cast pool.
        cardRegistry.register(
            CardDefinition.sorcery(
                name = "Expensive Draw",
                manaCost = ManaCost.parse("{5}{U}"),
                oracleText = "Draw a card.",
                script = CardScript(spellEffect = Effects.DrawCards(1))
            )
        )

        test("Buster Sword: equipped creature gets +3/+2") {
            val game = scenario()
                .withPlayers()
                .withCardOnBattlefield(1, "Grizzly Bears") // 2/2 base
                .withCardOnBattlefield(1, "Buster Sword")
                .withLandsOnBattlefield(1, "Plains", 2)    // pay Equip {2}
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val bears = game.findPermanent("Grizzly Bears")!!
            val sword = game.findPermanent("Buster Sword")!!

            game.execute(
                ActivateAbility(
                    playerId = game.player1Id,
                    sourceId = sword,
                    abilityId = busterEquipId,
                    targets = listOf(ChosenTarget.Permanent(bears))
                )
            ).error shouldBe null
            game.resolveStack()

            val projected = stateProjector.project(game.state)
            withClue("Equipped Bears should be 5/4 (2/2 + 3/+2)") {
                projected.getPower(bears) shouldBe 5
                projected.getToughness(bears) shouldBe 4
            }
        }

        test("Buster Sword: combat damage draws a card, then you may free-cast a spell with MV <= that damage") {
            val game = scenario()
                .withPlayers()
                .withCardOnBattlefield(1, "Grizzly Bears") // 2/2 base -> 5/4 equipped, 5 combat damage
                .withCardOnBattlefield(1, "Buster Sword")
                .withLandsOnBattlefield(1, "Plains", 2)
                .withCardInHand(1, "Cheap Draw")           // MV 1 <= 5 -> free-castable after the draw
                .withCardInHand(1, "Expensive Draw")       // MV 6 > 5 -> excluded from the free-cast pool
                .withCardInLibrary(1, "Plains")            // the card drawn by the trigger
                .withCardInLibrary(1, "Forest")            // resolves the free-cast Cheap Draw's own draw
                .withCardInLibrary(2, "Island")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val bears = game.findPermanent("Grizzly Bears")!!
            val sword = game.findPermanent("Buster Sword")!!

            game.execute(
                ActivateAbility(
                    playerId = game.player1Id,
                    sourceId = sword,
                    abilityId = busterEquipId,
                    targets = listOf(ChosenTarget.Permanent(bears))
                )
            ).error shouldBe null
            game.resolveStack()

            game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
            game.declareAttackers(mapOf("Grizzly Bears" to 2))
            game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
            game.declareNoBlockers()
            game.passUntilPhase(Phase.COMBAT, Step.COMBAT_DAMAGE)
            game.passPriority()
            game.resolveStack()

            withClue("Opponent took 5 combat damage") {
                game.getLifeTotal(2) shouldBe 15
            }

            // Buster Sword: "draw a card, then you may cast a spell..." The draw resolves as part of
            // the trigger, then the player chooses one castable spell (Cheap Draw) to free-cast.
            val cheapDraw = game.findCardsInHand(1, "Cheap Draw")
            val expensiveDraw = game.findCardsInHand(1, "Expensive Draw")
            withClue("Cheap Draw should still be in hand, awaiting the free-cast selection") {
                cheapDraw.isNotEmpty() shouldBe true
            }

            // The "mana value <= that damage" cap is the load-bearing filter: with 5 combat damage,
            // Cheap Draw (MV 1) is offered but Expensive Draw (MV 6) must be excluded from the pool.
            val selection = game.getPendingDecision()
            withClue("A free-cast selection should be pending after combat damage") {
                (selection is SelectCardsDecision) shouldBe true
            }
            val offered = (selection as SelectCardsDecision).options
            withClue("Cheap Draw (MV 1 <= 5) should be offered for free-cast") {
                offered.containsAll(cheapDraw) shouldBe true
            }
            withClue("Expensive Draw (MV 6 > 5) must be excluded from the free-cast pool") {
                offered.any { it in expensiveDraw } shouldBe false
            }

            game.selectCards(cheapDraw)
            game.resolveStack()

            withClue("Cheap Draw (MV 1 <= 5) was free-cast and resolved") {
                game.isInHand(1, "Cheap Draw") shouldBe false
                game.isInGraveyard(1, "Cheap Draw") shouldBe true
            }
        }

        test("White Mage's Staff: +1/+1, granted 'whenever this creature attacks, you gain 1 life', and Cleric") {
            val game = scenario()
                .withPlayers()
                .withCardInHand(1, "White Mage's Staff")
                .withLandsOnBattlefield(1, "Plains", 2)
                .withCardInLibrary(1, "Plains")
                .withCardInLibrary(2, "Island")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "White Mage's Staff").error shouldBe null
            if (game.hasPendingDecision()) game.submitManaSourcesAutoPay()
            game.resolveStack()

            val hero = game.findPermanent("Hero Token")!!
            val projected = stateProjector.project(game.state)
            withClue("Equipped Hero token should be 2/2 (1/1 base + 1/+1)") {
                projected.getPower(hero) shouldBe 2
                projected.getToughness(hero) shouldBe 2
            }
            withClue("Equipped Hero token should be a Cleric in addition to its other types") {
                projected.hasSubtype(hero, "Cleric") shouldBe true
            }

            val lifeBefore = game.getLifeTotal(1)
            game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
            game.declareAttackers(mapOf("Hero Token" to 2))
            game.passPriority()
            game.resolveStack()
            withClue("Attacking with the equipped Hero token gains its controller 1 life") {
                game.getLifeTotal(1) shouldBe lifeBefore + 1
            }
        }

        test("Black Mage's Rod: +1/+0, granted 'cast noncreature -> 1 dmg each opponent', and Wizard") {
            val game = scenario()
                .withPlayers()
                .withCardInHand(1, "Black Mage's Rod")
                .withCardInHand(1, "Lightning Bolt")        // a noncreature spell to trigger the granted ability
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withLandsOnBattlefield(1, "Mountain", 1)   // pay Lightning Bolt
                .withCardInLibrary(1, "Plains")
                .withCardInLibrary(2, "Island")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Black Mage's Rod").error shouldBe null
            if (game.hasPendingDecision()) game.submitManaSourcesAutoPay()
            game.resolveStack()

            val hero = game.findPermanent("Hero Token")!!
            val projected = stateProjector.project(game.state)
            withClue("Equipped Hero token should be 2/1 (1/1 base + 1/+0)") {
                projected.getPower(hero) shouldBe 2
                projected.getToughness(hero) shouldBe 1
            }
            withClue("Equipped Hero token should be a Wizard in addition to its other types") {
                projected.hasSubtype(hero, "Wizard") shouldBe true
            }

            val oppLifeBefore = game.getLifeTotal(2)
            // Cast a noncreature spell (Lightning Bolt at opponent) — the granted trigger should fire,
            // dealing 1 damage to each opponent on top of the Bolt's own 3.
            game.castSpellTargetingPlayer(1, "Lightning Bolt", 2).error shouldBe null
            if (game.hasPendingDecision()) game.submitManaSourcesAutoPay()
            game.resolveStack()

            withClue("Opponent took 3 (Bolt) + 1 (granted ability) = 4 damage") {
                game.getLifeTotal(2) shouldBe oppLifeBefore - 4
            }
        }

        test("Samurai's Katana: +2/+2, trample, haste, and Samurai") {
            val game = scenario()
                .withPlayers()
                .withCardInHand(1, "Samurai's Katana")
                .withLandsOnBattlefield(1, "Mountain", 3)
                .withCardInLibrary(1, "Plains")
                .withCardInLibrary(2, "Island")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Samurai's Katana").error shouldBe null
            if (game.hasPendingDecision()) game.submitManaSourcesAutoPay()
            game.resolveStack()

            val hero = game.findPermanent("Hero Token")!!
            val projected = stateProjector.project(game.state)
            withClue("Equipped Hero token should be 3/3 (1/1 base + 2/+2)") {
                projected.getPower(hero) shouldBe 3
                projected.getToughness(hero) shouldBe 3
            }
            withClue("Equipped Hero token should have trample and haste") {
                projected.hasKeyword(hero, Keyword.TRAMPLE) shouldBe true
                projected.hasKeyword(hero, Keyword.HASTE) shouldBe true
            }
            withClue("Equipped Hero token should be a Samurai in addition to its other types") {
                projected.hasSubtype(hero, "Samurai") shouldBe true
            }
        }
    }
}
