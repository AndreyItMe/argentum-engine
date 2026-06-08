package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.battlefield.CastChoicesComponent
import com.wingedsheep.engine.state.components.battlefield.ChoiceValue
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.ChoiceSlot
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Jihad (ARN) — exercises two pieces that didn't exist before this card:
 *  - the [ChoiceSlot.OPPONENT] cast-choice slot + [com.wingedsheep.sdk.scripting.references.Player.ChosenOpponent]
 *    reference (read by the static anthem's condition and the state-trigger condition);
 *  - the `nontoken()` × `sharingChosenColorWithSource()` filter composition pinning the buff to
 *    *nontoken* permanents of the *chosen* color on the *chosen* player.
 *
 * The ETB cast-choice flow itself (color + opponent prompt → resumer writes the bag) is covered
 * by Riptide Replicator / Callous Oppressor style tests for [ChoiceSlot.COLOR]; here we set the
 * bag directly to isolate the *reading* paths and avoid duplicating decision-loop plumbing.
 */
class JihadScenarioTest : ScenarioTestBase() {

    private val projector = StateProjector()

    init {
        context("Jihad — static anthem + state-trigger sacrifice gated on chosen player + color") {

            test("anthem applies while the chosen opponent controls a nontoken permanent of the chosen color") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Jihad")
                    .withCardOnBattlefield(1, "Glory Seeker")          // white creature on owner's side
                    .withCardOnBattlefield(2, "Mons's Goblin Raiders")            // chosen player controls a red permanent
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val jihad = game.findPermanent("Jihad")!!
                val whiteCreature = game.findPermanent("Glory Seeker")!!
                game.state = game.state.updateEntity(jihad) { c ->
                    c.with(
                        CastChoicesComponent(
                            chosen = mapOf(
                                ChoiceSlot.COLOR to ChoiceValue.ColorChoice(Color.RED),
                                ChoiceSlot.OPPONENT to ChoiceValue.EntityChoice(game.player2Id)
                            )
                        )
                    )
                }

                game.resolveStack()

                withClue("Glory Seeker (2/2) should be +2/+1 while chosen opponent controls a red nontoken permanent") {
                    projector.getProjectedPower(game.state, whiteCreature) shouldBe (2 + 2)
                    projector.getProjectedToughness(game.state, whiteCreature) shouldBe (2 + 1)
                }
                withClue("Jihad should remain on the battlefield while the condition holds") {
                    game.isOnBattlefield("Jihad") shouldBe true
                }
            }

            test("tokens do not count — chosen opponent only has a red token, so Jihad is sacrificed") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Jihad")
                    .withCardOnBattlefield(1, "Glory Seeker")
                    // Only thing the chosen opponent controls of the chosen color is a TOKEN —
                    // `nontoken()` excludes it, so the condition is false from the start.
                    .withCardOnBattlefield(2, "Mons's Goblin Raiders", isToken = true)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val jihad = game.findPermanent("Jihad")!!
                val whiteCreature = game.findPermanent("Glory Seeker")!!
                game.state = game.state.updateEntity(jihad) { c ->
                    c.with(
                        CastChoicesComponent(
                            chosen = mapOf(
                                ChoiceSlot.COLOR to ChoiceValue.ColorChoice(Color.RED),
                                ChoiceSlot.OPPONENT to ChoiceValue.EntityChoice(game.player2Id)
                            )
                        )
                    )
                }

                // Sanity check: the token *exists* on the battlefield as a permanent...
                val tokenId = game.findPermanent("Mons's Goblin Raiders")!!
                game.state.getEntity(tokenId)?.has<TokenComponent>() shouldBe true

                // ...but the anthem is OFF (filter rejects tokens).
                projector.getProjectedPower(game.state, whiteCreature) shouldBe 2
                projector.getProjectedToughness(game.state, whiteCreature) shouldBe 2

                // Run the state-trigger poller: with no *nontoken* red permanent on the chosen
                // opponent's side, Jihad's state trigger fires and sacrifices it.
                game.passUntilPhase(Phase.COMBAT, Step.BEGIN_COMBAT)
                game.resolveStack()

                withClue("Jihad's state-triggered ability should sacrifice it") {
                    game.isOnBattlefield("Jihad") shouldBe false
                }
            }

            test("untargeted opponent's permanents are ignored — Jihad is sacrificed even if a non-chosen player controls the color") {
                // 3 players' worth of state isn't supported by the 2-player scenario harness;
                // we model the "ignored player" angle by having the controller themselves hold
                // the chosen color while the chosen opponent holds nothing of it.
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Jihad")
                    .withCardOnBattlefield(1, "Mountain")    // controller owns the red permanent, NOT the chosen opponent
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val jihad = game.findPermanent("Jihad")!!
                game.state = game.state.updateEntity(jihad) { c ->
                    c.with(
                        CastChoicesComponent(
                            chosen = mapOf(
                                ChoiceSlot.COLOR to ChoiceValue.ColorChoice(Color.RED),
                                ChoiceSlot.OPPONENT to ChoiceValue.EntityChoice(game.player2Id)
                            )
                        )
                    )
                }

                game.passUntilPhase(Phase.COMBAT, Step.BEGIN_COMBAT)
                game.resolveStack()

                withClue("Only the chosen opponent's permanents satisfy the condition") {
                    game.isOnBattlefield("Jihad") shouldBe false
                }
            }
        }
    }
}
