package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.battlefield.SummoningSicknessComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.scripting.GrantActivatedAbility
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for Brightcap Badger // Fungus Frolic — also exercising the new Adventure
 * mechanic (CR 715) generally.
 *
 * Brightcap Badger {3}{G}, Creature — Badger Druid, 3/4
 *   Each Fungus and Saproling you control has "{T}: Add {G}."
 *   At the beginning of your end step, create a 1/1 green Saproling creature token.
 * Adventure: Fungus Frolic {2}{G}, Instant — Adventure
 *   Create two 1/1 green Saproling creature tokens.
 */
class BrightcapBadgerScenarioTest : ScenarioTestBase() {

    private fun saprolingsControlledBy(game: TestGame, playerNumber: Int): Int {
        val pid = if (playerNumber == 1) game.player1Id else game.player2Id
        return game.state.getBattlefield(pid).count { entityId ->
            game.state.getEntity(entityId)?.get<CardComponent>()?.let { card ->
                Subtype.SAPROLING in card.typeLine.subtypes
            } == true
        }
    }

    /** Advance from main phase to end step by passing priority until we get there. */
    private fun advanceToEndStep(game: TestGame) {
        var iterations = 0
        while (game.state.step != Step.END && game.state.pendingDecision == null && iterations++ < 12) {
            game.passPriority()
        }
    }

    init {
        context("Adventure mechanic — Fungus Frolic") {

            test("casting the Adventure creates two Saprolings, exiles the card, and grants a cast-from-exile permission") {
                val game = scenario()
                    .withPlayers()
                    .withCardInHand(1, "Brightcap Badger")
                    .withLandsOnBattlefield(1, "Forest", 4)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cardId = game.findCardsInHand(1, "Brightcap Badger").first()

                // Cast the Adventure half (face index 0).
                val castResult = game.execute(CastSpell(game.player1Id, cardId, faceIndex = 0))
                withClue("Casting Fungus Frolic should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                withClue("Spell should be on the stack after cast (stack size=${game.state.stack.size})") {
                    game.state.stack.size shouldBe 1
                }

                // Spell on the stack now; resolve it.
                game.resolveStack()
                withClue("Stack should be empty after resolution (stack=${game.state.stack.map { game.state.getEntity(it)?.get<CardComponent>()?.name }})") {
                    game.state.stack.size shouldBe 0
                }

                withClue("Two Saproling tokens should be on the battlefield") {
                    saprolingsControlledBy(game, 1) shouldBe 2
                }
                withClue("Brightcap Badger should be in exile, not in graveyard or hand") {
                    game.state.getExile(game.player1Id).contains(cardId) shouldBe true
                    game.state.getGraveyard(game.player1Id).contains(cardId) shouldBe false
                    game.state.getHand(game.player1Id).contains(cardId) shouldBe false
                }
                withClue("Caster should hold the cast-from-exile permission (CR 715.5)") {
                    val perm = game.state.mayPlayPermissions.firstOrNull { cardId in it.cardIds }
                    perm.shouldNotBeNull()
                    perm.controllerId shouldBe game.player1Id
                    perm.permanent shouldBe true
                }
            }

            test("after the Adventure resolves, the creature can be cast from exile") {
                val game = scenario()
                    .withPlayers()
                    .withCardInHand(1, "Brightcap Badger")
                    .withLandsOnBattlefield(1, "Forest", 8)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cardId = game.findCardsInHand(1, "Brightcap Badger").first()

                // Cast the Adventure, resolve.
                game.execute(CastSpell(game.player1Id, cardId, faceIndex = 0))
                game.resolveStack()

                game.state.getExile(game.player1Id).contains(cardId) shouldBe true

                // Now cast the creature face from exile.
                val recast = game.execute(CastSpell(game.player1Id, cardId))
                withClue("Re-cast as creature from exile should succeed: ${recast.error}") {
                    recast.error shouldBe null
                }
                game.resolveStack()

                withClue("Brightcap Badger should now be a creature on the battlefield") {
                    game.findPermanent("Brightcap Badger") shouldNotBe null
                }
                withClue("Card should have left exile after entering the battlefield") {
                    game.state.getExile(game.player1Id).contains(cardId) shouldBe false
                }
            }
        }

        context("Brightcap Badger creature face") {

            test("casting Brightcap Badger as a creature from hand puts a Badger Druid on the battlefield") {
                val game = scenario()
                    .withPlayers()
                    .withCardInHand(1, "Brightcap Badger")
                    .withLandsOnBattlefield(1, "Forest", 4)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Brightcap Badger")
                game.resolveStack()

                val brightcap = game.findPermanent("Brightcap Badger")
                withClue("Brightcap Badger should be on the battlefield") {
                    brightcap shouldNotBe null
                }
                val card = game.state.getEntity(brightcap!!)?.get<CardComponent>()!!
                card.typeLine.isCreature shouldBe true
            }

            test("Brightcap Badger creates a Saproling token at your end step") {
                val game = scenario()
                    .withPlayers()
                    .withCardOnBattlefield(1, "Brightcap Badger")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
                    .build()

                advanceToEndStep(game)
                // Trigger fires at the start of the end step.
                withClue("End-step Saproling trigger should be on the stack") {
                    game.state.stack.size shouldBe 1
                }
                game.resolveStack()

                withClue("One Saproling token should now be on the battlefield") {
                    saprolingsControlledBy(game, 1) shouldBe 1
                }
            }
        }

        context("Granted activated abilities on tokens") {

            // A Saproling token created by Brightcap Badger has no registered CardDefinition;
            // its only abilities come from Brightcap's static `GrantActivatedAbility`. Earlier,
            // ActivateAbilityHandler bailed with "Card definition not found" for any source whose
            // cardDefinitionId wasn't in the registry, even though the granted ability was the
            // one being activated. This regression test pins the fix.
            test("Saproling token can tap for {G} via Brightcap Badger's granted mana ability") {
                val game = scenario()
                    .withPlayers()
                    .withCardOnBattlefield(1, "Brightcap Badger")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
                    .build()

                // Run Brightcap's end-step trigger to create the Saproling token.
                advanceToEndStep(game)
                game.resolveStack()

                val saprolingId = game.state.getBattlefield(game.player1Id).first { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.let { card ->
                        Subtype.SAPROLING in card.typeLine.subtypes
                    } == true
                }

                // Newly-created tokens have summoning sickness, which would block tap costs
                // (CR 302.1). Strip it so we can exercise the granted ability directly — the bug
                // we're guarding against fired before the summoning-sickness check.
                game.state = game.state.updateEntity(saprolingId) { c ->
                    c.without<SummoningSicknessComponent>()
                }

                // The granted ability lives on Brightcap's static GrantActivatedAbility.
                val brightcapDef = cardRegistry.getCard("Brightcap Badger")!!
                val grantedAbility = brightcapDef.script.staticAbilities
                    .filterIsInstance<GrantActivatedAbility>()
                    .first()
                    .ability

                val activateResult = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = saprolingId,
                        abilityId = grantedAbility.id
                    )
                )

                withClue("Granted mana ability should activate without 'Card definition not found': ${activateResult.error}") {
                    activateResult.error shouldBe null
                }

                val manaPool = game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()
                manaPool.shouldNotBeNull()
                withClue("Tapping the Saproling should add one green mana") {
                    manaPool.green shouldBe 1
                }
            }

            // When two Brightcap Badgers are on the battlefield, both grant Saprolings the
            // identical "{T}: Add {G}" ability. They reference the same ActivatedAbility
            // instance (same id), so the duplicates are functionally identical — surfacing two
            // buttons would confuse the UI and adds nothing in play (only one tap is available).
            // CastPermissionUtils.getStaticGrantedAbilitiesWithGranter dedups by ability id.
            test("identical granted abilities from two granters appear once on the recipient") {
                val game = scenario()
                    .withPlayers()
                    .withCardOnBattlefield(1, "Brightcap Badger")
                    .withCardOnBattlefield(1, "Brightcap Badger")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
                    .build()

                advanceToEndStep(game)
                game.resolveStack()

                val saprolingId = game.state.getBattlefield(game.player1Id).first { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.let { card ->
                        Subtype.SAPROLING in card.typeLine.subtypes
                    } == true
                }

                val grantedAbilities = com.wingedsheep.engine.legalactions.utils.CastPermissionUtils(
                    cardRegistry,
                    com.wingedsheep.engine.handlers.PredicateEvaluator(),
                    com.wingedsheep.engine.handlers.ConditionEvaluator()
                ).getStaticGrantedActivatedAbilities(saprolingId, game.state)

                withClue("Two Brightcap Badgers must not surface two identical grants on a Saproling: $grantedAbilities") {
                    grantedAbilities.size shouldBe 1
                }
                withClue("The single surviving grant should be Brightcap's '{T}: Add {G}' mana ability") {
                    grantedAbilities.single().isManaAbility shouldBe true
                }
            }
        }
    }
}
