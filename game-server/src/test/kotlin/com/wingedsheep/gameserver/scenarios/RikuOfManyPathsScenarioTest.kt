package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Scenario tests for Riku of Many Paths ({G}{U}{R} 3/3 Legendary Creature — Human Wizard).
 *
 * Oracle:
 *   "Whenever you cast a modal spell, choose up to X, where X is the number of times
 *    you chose a mode for that spell —
 *    • Exile the top card of your library. Until the end of your next turn, you may play it.
 *    • Put a +1/+1 counter on Riku. It gains trample until end of turn.
 *    • Create a 1/1 blue Bird creature token with flying."
 *
 * These tests double as the smoke tests for:
 *   - [com.wingedsheep.sdk.scripting.events.SpellCastPredicate.IsModal] — gates the
 *     trigger on the cast spell having at least one chosen mode.
 *   - `ContextPropertyKey.MODES_CHOSEN_ON_TRIGGERING_SPELL` + plumbing from
 *     `SpellCastEvent.chosenModesCount` → `TriggerContext.modesChosenCount` →
 *     `EffectContext.triggerModesChosenCount`.
 *   - [com.wingedsheep.sdk.scripting.effects.ModalEffect.dynamicChooseCount] —
 *     resolution-time "choose up to X" with X evaluated from the triggering spell.
 */
class RikuOfManyPathsScenarioTest : ScenarioTestBase() {

    private fun TestGame.pickOption(optionIndex: Int) {
        val decision = getPendingDecision()
        decision.shouldNotBeNull()
        decision.shouldBeInstanceOf<ChooseOptionDecision>()
        submitDecision(OptionChosenResponse(decision.id, optionIndex))
    }

    init {
        context("Triggers only on modal spell casts") {

            test("non-modal spell cast does NOT trigger Riku") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardOnBattlefield(1, "Riku of Many Paths")
                    .withCardInHand(1, "Vault Plunderer")
                    .withLandsOnBattlefield(1, "Swamp", 3)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Vault Plunderer's ETB trigger targets a player — passing priority resolves
                // the spell, then the trigger asks for its target before going on the stack.
                game.castSpell(1, "Vault Plunderer")
                game.resolveStack()
                game.selectTargets(listOf(game.player1Id))
                game.resolveStack()

                withClue("Riku must NOT present a mode choice for non-modal casts") {
                    game.getPendingDecision().shouldBeNull()
                }

                val rikuId = game.findPermanent("Riku of Many Paths")!!
                val counters = game.state.getEntity(rikuId)?.get<CountersComponent>()
                withClue("Riku must still have zero +1/+1 counters") {
                    (counters?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 0
                }
            }
        }

        context("Triggers on modal spell casts and routes by chosen mode") {

            test("modal cast → pick the +1/+1 + trample mode → Riku gains a counter and trample") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardOnBattlefield(1, "Riku of Many Paths")
                    .withCardInHand(1, "Dawn's Truce")
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Dawn's Truce is choose-one — picking mode 0 (no-target hexproof branch)
                // makes the cast carry exactly one chosen mode. X for Riku's trigger = 1.
                game.castSpellWithMode(1, "Dawn's Truce", modeIndex = 0)
                game.resolveStack()

                // Riku's trigger resolves first (LIFO). With X = 1 it presents the
                // "choose up to one mode" decision (3 modes + decline).
                val decision = game.getPendingDecision()
                decision.shouldNotBeNull()
                decision.shouldBeInstanceOf<ChooseOptionDecision>()
                withClue("Riku must offer all three modes plus a decline option (chooseCount=1, min=0)") {
                    decision.options.size shouldBe 4
                }

                // Pick mode index 1 — "+1/+1 counter on Riku + trample until end of turn".
                game.pickOption(1)
                game.resolveStack()

                val rikuId = game.findPermanent("Riku of Many Paths")!!
                val counters = game.state.getEntity(rikuId)?.get<CountersComponent>()
                withClue("Mode 2 must place a +1/+1 counter on Riku") {
                    counters.shouldNotBeNull()
                    counters.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 1
                }

                val projected = game.state.projectedState
                withClue("Mode 2 must grant Riku trample until end of turn") {
                    projected.getKeywords(rikuId) shouldBeContains Keyword.TRAMPLE.name
                }
            }

            test("modal cast → pick the Bird token mode → a 1/1 flying Bird joins the battlefield") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardOnBattlefield(1, "Riku of Many Paths")
                    .withCardInHand(1, "Dawn's Truce")
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val birdsBefore = countBirdTokensControlledBy(game, game.player1Id)

                game.castSpellWithMode(1, "Dawn's Truce", modeIndex = 0)
                game.resolveStack()

                // Pick mode index 2 — "Create a 1/1 blue Bird creature token with flying".
                game.pickOption(2)
                game.resolveStack()

                val birdsAfter = countBirdTokensControlledBy(game, game.player1Id)
                withClue("Mode 3 must put one Bird token onto the battlefield") {
                    birdsAfter shouldBe birdsBefore + 1
                }
            }

            test("modal cast → decline a mode → nothing happens but the trigger still fired") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardOnBattlefield(1, "Riku of Many Paths")
                    .withCardInHand(1, "Dawn's Truce")
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpellWithMode(1, "Dawn's Truce", modeIndex = 0)
                game.resolveStack()

                val decision = game.getPendingDecision()
                decision.shouldNotBeNull()
                decision.shouldBeInstanceOf<ChooseOptionDecision>()

                // The decline option is the last entry per ModalEffectExecutor.
                val declineIndex = decision.options.lastIndex
                game.pickOption(declineIndex)
                game.resolveStack()

                val rikuId = game.findPermanent("Riku of Many Paths")!!
                val counters = game.state.getEntity(rikuId)?.get<CountersComponent>()
                withClue("Declining must leave Riku with no +1/+1 counters") {
                    (counters?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 0
                }
                withClue("Declining must create no Bird tokens") {
                    countBirdTokensControlledBy(game, game.player1Id) shouldBe 0
                }
            }
        }
    }

    private infix fun <T> Iterable<T>.shouldBeContains(expected: T) {
        val found = this.any { it == expected }
        withClue("Expected to contain $expected but was ${this.toList()}") {
            found shouldBe true
        }
    }

    private fun countBirdTokensControlledBy(game: TestGame, playerId: com.wingedsheep.sdk.model.EntityId): Int {
        val battlefield = game.state.getZone(
            com.wingedsheep.engine.state.ZoneKey(playerId, Zone.BATTLEFIELD)
        )
        return battlefield.count { entityId ->
            val container = game.state.getEntity(entityId) ?: return@count false
            val card = container.get<CardComponent>() ?: return@count false
            container.get<TokenComponent>() != null &&
                "Bird" in card.typeLine.subtypes.map { it.value }
        }
    }
}
