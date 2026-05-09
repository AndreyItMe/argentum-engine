package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario test for Fellwar Stone.
 *
 * Card reference:
 * - Fellwar Stone ({2}): Artifact
 *   "{T}: Add one mana of any color that a land an opponent controls could produce."
 *
 * Notable rulings (Scryfall):
 * - Activation works even if the opponent's lands are tapped — only the produced
 *   color set matters, not whether the ability is currently usable.
 * - Fellwar Stone can't be tapped for colorless mana, even if a land an opponent
 *   controls could produce colorless mana.
 * - If the ability resolves with no opponent land able to produce colored mana,
 *   no mana is produced.
 */
class FellwarStoneScenarioTest : ScenarioTestBase() {

    init {
        context("Fellwar Stone mana ability") {
            test("produces a color the opponent's land can produce (untapped Mountain)") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Fellwar Stone")
                    .withCardOnBattlefield(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val fellwarStone = game.findPermanent("Fellwar Stone")!!
                val cardDef = cardRegistry.getCard("Fellwar Stone")!!
                val ability = cardDef.script.activatedAbilities.first()

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = fellwarStone,
                        abilityId = ability.id,
                        manaColorChoice = Color.RED
                    )
                )

                withClue("Ability should activate successfully") {
                    result.isSuccess shouldBe true
                }

                val manaPool = result.newState.getEntity(game.player1Id)?.get<ManaPoolComponent>()
                withClue("Should have 1 red mana from opponent's Mountain") {
                    manaPool shouldNotBe null
                    manaPool!!.red shouldBe 1
                }
            }

            test("works even when the opponent's land is tapped") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Fellwar Stone")
                    .withCardOnBattlefield(2, "Island", tapped = true)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val fellwarStone = game.findPermanent("Fellwar Stone")!!
                val cardDef = cardRegistry.getCard("Fellwar Stone")!!
                val ability = cardDef.script.activatedAbilities.first()

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = fellwarStone,
                        abilityId = ability.id,
                        manaColorChoice = Color.BLUE
                    )
                )

                result.isSuccess shouldBe true
                val manaPool = result.newState.getEntity(game.player1Id)?.get<ManaPoolComponent>()
                withClue("Tapped state of opponent's land must not block production") {
                    manaPool shouldNotBe null
                    manaPool!!.blue shouldBe 1
                }
            }

            test("produces no mana when no opponent lands can produce a color") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Fellwar Stone")
                    // No lands for opponent. Player owns a Mountain (must not count).
                    .withCardOnBattlefield(1, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val fellwarStone = game.findPermanent("Fellwar Stone")!!
                val cardDef = cardRegistry.getCard("Fellwar Stone")!!
                val ability = cardDef.script.activatedAbilities.first()

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = fellwarStone,
                        abilityId = ability.id,
                        manaColorChoice = Color.RED
                    )
                )

                withClue("Ability resolves but produces no mana") {
                    result.isSuccess shouldBe true
                }
                val manaPool = result.newState.getEntity(game.player1Id)?.get<ManaPoolComponent>()
                if (manaPool != null) {
                    manaPool.white shouldBe 0
                    manaPool.blue shouldBe 0
                    manaPool.black shouldBe 0
                    manaPool.red shouldBe 0
                    manaPool.green shouldBe 0
                }
            }

            test("falls back to an available color when the requested color is not producible") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Fellwar Stone")
                    .withCardOnBattlefield(2, "Forest") // only green available
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val fellwarStone = game.findPermanent("Fellwar Stone")!!
                val cardDef = cardRegistry.getCard("Fellwar Stone")!!
                val ability = cardDef.script.activatedAbilities.first()

                // Request red — opponent only has Forest, so green is the only option.
                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = fellwarStone,
                        abilityId = ability.id,
                        manaColorChoice = Color.RED
                    )
                )

                result.isSuccess shouldBe true
                val manaPool = result.newState.getEntity(game.player1Id)?.get<ManaPoolComponent>()
                withClue("Should not produce red — falls back to first available (green)") {
                    manaPool shouldNotBe null
                    manaPool!!.red shouldBe 0
                    manaPool.green shouldBe 1
                }
            }

            test("aggregates colors across multiple opponent lands") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Fellwar Stone")
                    .withCardOnBattlefield(2, "Plains")
                    .withCardOnBattlefield(2, "Swamp")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val fellwarStone = game.findPermanent("Fellwar Stone")!!
                val cardDef = cardRegistry.getCard("Fellwar Stone")!!
                val ability = cardDef.script.activatedAbilities.first()

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = fellwarStone,
                        abilityId = ability.id,
                        manaColorChoice = Color.BLACK
                    )
                )

                result.isSuccess shouldBe true
                val manaPool = result.newState.getEntity(game.player1Id)?.get<ManaPoolComponent>()
                withClue("Black is producible because opponent controls a Swamp") {
                    manaPool shouldNotBe null
                    manaPool!!.black shouldBe 1
                    manaPool.white shouldBe 0
                }
            }
        }
    }
}
