package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Entropic Battlecruiser.
 *
 * Card reference:
 * - Entropic Battlecruiser ({3}{B}): 3/10 Artifact — Spacecraft
 *   Station (Tap another creature you control: Put charge counters equal to its power
 *   on this Spacecraft. Station only as a sorcery. It's an artifact creature at 8+.)
 *   1+ | Whenever an opponent discards a card, they lose 3 life.
 *   8+ | Flying, deathtouch
 *   Whenever this Spacecraft attacks, each opponent discards a card. Each opponent who
 *   can't loses 3 life.
 */
class EntropicBattlecruiserScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    init {
        context("1+ charge counters: opponent-discards trigger") {

            test("does not fire below 1 charge counter") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Entropic Battlecruiser")
                    .withCardInHand(1, "Virus Beetle")
                    .withCardInHand(2, "Mountain")
                    .withLandsOnBattlefield(1, "Swamp", 2)
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Battlecruiser stays at 0 charge counters — below the 1+ threshold.
                val p2LifeBefore = game.getLifeTotal(2)

                val cast = game.castSpell(1, "Virus Beetle")
                withClue("Virus Beetle should cast: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack()

                if (game.hasPendingDecision()) {
                    val decision = game.getPendingDecision() as SelectCardsDecision
                    game.selectCards(listOf(decision.options.first()))
                }
                game.resolveStack()

                withClue("Player 2 should not lose life — Battlecruiser is at 0 charge counters") {
                    game.getLifeTotal(2) shouldBe p2LifeBefore
                }
            }

            test("fires when charge counter ≥ 1: opponent discards → loses 3 life") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Entropic Battlecruiser")
                    .withCardInHand(1, "Virus Beetle")
                    .withCardInHand(2, "Mountain")
                    .withLandsOnBattlefield(1, "Swamp", 2)
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cruiser = game.findPermanent("Entropic Battlecruiser")!!
                game.state = game.state.updateEntity(cruiser) {
                    it.with(CountersComponent().withAdded(CounterType.CHARGE, 1))
                }

                val p2LifeBefore = game.getLifeTotal(2)

                // Cast Virus Beetle → on ETB it makes the opponent discard their Mountain.
                val cast = game.castSpell(1, "Virus Beetle")
                withClue("Virus Beetle should cast: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack()

                // Opponent picks the only card to discard.
                if (game.hasPendingDecision()) {
                    val decision = game.getPendingDecision() as SelectCardsDecision
                    game.selectCards(listOf(decision.options.first()))
                }
                game.resolveStack()

                withClue("Player 2 should have lost 3 life from Entropic Battlecruiser's 1+ trigger") {
                    game.getLifeTotal(2) shouldBe p2LifeBefore - 3
                }
            }

            test("multi-card discard fans out — opponent discards 2, loses 6 life") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Entropic Battlecruiser")
                    .withCardInHand(1, "Mind Rot")
                    .withCardInHand(2, "Mountain")
                    .withCardInHand(2, "Forest")
                    .withLandsOnBattlefield(1, "Swamp", 3)
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cruiser = game.findPermanent("Entropic Battlecruiser")!!
                game.state = game.state.updateEntity(cruiser) {
                    it.with(CountersComponent().withAdded(CounterType.CHARGE, 1))
                }

                val p2LifeBefore = game.getLifeTotal(2)

                // Mind Rot targets opponent and makes them discard two cards in a single
                // resolution. The engine emits one CardsDiscardedEvent with two cardIds;
                // the detector fans that out into two separate trigger firings, so the 1+
                // ability resolves twice for 3 life each.
                val cast = game.castSpellTargetingPlayer(1, "Mind Rot", 2)
                withClue("Mind Rot should cast: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack()

                if (game.hasPendingDecision()) {
                    val decision = game.getPendingDecision() as SelectCardsDecision
                    game.selectCards(decision.options.take(2))
                }
                game.resolveStack()

                withClue("Two cards discarded in one resolution should fire the 1+ trigger twice → 6 life lost") {
                    game.getLifeTotal(2) shouldBe p2LifeBefore - 6
                }
            }

            test("does not fire when an own discard happens (only opponents)") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(2, "Entropic Battlecruiser")
                    .withCardInHand(1, "Virus Beetle")
                    .withCardInHand(2, "Mountain")
                    .withLandsOnBattlefield(1, "Swamp", 2)
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Player 2 owns the Battlecruiser at 1+ charge. Player 2 (the controller)
                // should not lose life when Player 2 discards — only when an opponent
                // (Player 1) discards.
                val cruiser = game.findPermanent("Entropic Battlecruiser")!!
                game.state = game.state.updateEntity(cruiser) {
                    it.with(CountersComponent().withAdded(CounterType.CHARGE, 1))
                }

                val p1LifeBefore = game.getLifeTotal(1)
                val p2LifeBefore = game.getLifeTotal(2)

                // Player 1 casts Virus Beetle → Player 1 is the opponent of the
                // Battlecruiser's controller (Player 2). When Player 2 discards in
                // response to Virus Beetle, that's the Battlecruiser controller's *own*
                // discard, which should NOT trigger the 1+ ability.
                val cast = game.castSpell(1, "Virus Beetle")
                withClue("Virus Beetle should cast: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack()

                if (game.hasPendingDecision()) {
                    val decision = game.getPendingDecision() as SelectCardsDecision
                    game.selectCards(listOf(decision.options.first()))
                }
                game.resolveStack()

                withClue("Player 2 (Battlecruiser controller) should not lose life from own discard") {
                    game.getLifeTotal(2) shouldBe p2LifeBefore
                }
                withClue("Player 1 (the one who cast Virus Beetle) should also be unaffected") {
                    game.getLifeTotal(1) shouldBe p1LifeBefore
                }
            }
        }

        context("8+ charge counters: becomes a creature with flying and deathtouch") {

            test("is not a creature with 7 or fewer charge counters") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Entropic Battlecruiser")
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cruiser = game.findPermanent("Entropic Battlecruiser")!!
                game.state = game.state.updateEntity(cruiser) {
                    it.with(CountersComponent().withAdded(CounterType.CHARGE, 7))
                }

                val projected = stateProjector.project(game.state)
                withClue("7 charge counters: not yet a creature") {
                    projected.isCreature(cruiser) shouldBe false
                }
            }

            test("at 8+ charge counters, gains creature type, flying, and deathtouch") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Entropic Battlecruiser")
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cruiser = game.findPermanent("Entropic Battlecruiser")!!
                game.state = game.state.updateEntity(cruiser) {
                    it.with(CountersComponent().withAdded(CounterType.CHARGE, 8))
                }

                val projected = stateProjector.project(game.state)
                withClue("8 charge counters: is a creature") {
                    projected.isCreature(cruiser) shouldBe true
                }
                withClue("flies at 8+ charge counters") {
                    projected.hasKeyword(cruiser, "FLYING") shouldBe true
                }
                withClue("deathtouch at 8+ charge counters") {
                    projected.hasKeyword(cruiser, "DEATHTOUCH") shouldBe true
                }
                withClue("base P/T 3/10 carries over") {
                    projected.getPower(cruiser) shouldBe 3
                    projected.getToughness(cruiser) shouldBe 10
                }
            }
        }

        context("Attack trigger: discard or lose 3 life") {

            test("attacking forces each opponent to discard a card") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Entropic Battlecruiser")
                    .withCardInHand(2, "Forest")
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cruiser = game.findPermanent("Entropic Battlecruiser")!!
                // Stamp 8 charge counters so it can attack.
                game.state = game.state.updateEntity(cruiser) {
                    it.with(CountersComponent().withAdded(CounterType.CHARGE, 8))
                }

                game.advanceToPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                val attackResult = game.declareAttackers(mapOf("Entropic Battlecruiser" to 2))
                withClue("Attack should declare: ${attackResult.error}") {
                    attackResult.error shouldBe null
                }
                game.resolveStack()

                // Opponent picks the only card to discard.
                if (game.hasPendingDecision()) {
                    val decision = game.getPendingDecision() as SelectCardsDecision
                    game.selectCards(listOf(decision.options.first()))
                }
                game.resolveStack()

                withClue("Opponent should have discarded their card") {
                    game.handSize(2) shouldBe 0
                }
            }

            test("opponent with empty hand loses 3 life from attack trigger") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Entropic Battlecruiser")
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cruiser = game.findPermanent("Entropic Battlecruiser")!!
                game.state = game.state.updateEntity(cruiser) {
                    it.with(CountersComponent().withAdded(CounterType.CHARGE, 8))
                }

                val p2LifeBefore = game.getLifeTotal(2)

                game.advanceToPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                val attackResult = game.declareAttackers(mapOf("Entropic Battlecruiser" to 2))
                withClue("Attack should declare: ${attackResult.error}") {
                    attackResult.error shouldBe null
                }
                game.resolveStack()

                withClue("Opponent with empty hand loses 3 life from attack trigger") {
                    game.getLifeTotal(2) shouldBe p2LifeBefore - 3
                }
            }
        }
    }
}
