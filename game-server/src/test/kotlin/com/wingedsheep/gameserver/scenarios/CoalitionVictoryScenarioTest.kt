package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.player.PlayerLostComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.model.CardDefinition
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Coalition Victory.
 *
 * Card reference:
 * - Coalition Victory ({3}{W}{U}{B}{R}{G}): Sorcery
 *   You win the game if you control a land of each basic land type and a creature of each color.
 *
 * The two clauses are modeled as distinct-value aggregations capped at 5 (domain and distinct
 * creature colors). Per the 2006-09-25 ruling, a single multicolored creature / multi-type land
 * counts toward every color / type it has — verified by the "one five-color creature" case below.
 *
 * Mana is primed directly into the pool so the lands on the battlefield only ever exist to drive
 * the domain check (otherwise paying {W}{U}{B}{R}{G} from basics would force all five land types
 * to be present, making the "missing a land type" case impossible to cast).
 */
class CoalitionVictoryScenarioTest : ScenarioTestBase() {

    private fun monoCreature(name: String, pip: String) = CardDefinition.creature(
        name = name,
        manaCost = ManaCost.parse("{$pip}"),
        subtypes = setOf(Subtype("Elemental")),
        power = 1,
        toughness = 1
    )

    private val whiteBear = monoCreature("CV White Bear", "W")
    private val blueBear = monoCreature("CV Blue Bear", "U")
    private val blackBear = monoCreature("CV Black Bear", "B")
    private val redBear = monoCreature("CV Red Bear", "R")
    private val greenBear = monoCreature("CV Green Bear", "G")

    private val fiveColorBeast = CardDefinition.creature(
        name = "CV Rainbow Beast",
        manaCost = ManaCost.parse("{W}{U}{B}{R}{G}"),
        subtypes = setOf(Subtype("Beast")),
        power = 5,
        toughness = 5
    )

    init {
        cardRegistry.register(whiteBear)
        cardRegistry.register(blueBear)
        cardRegistry.register(blackBear)
        cardRegistry.register(redBear)
        cardRegistry.register(greenBear)
        cardRegistry.register(fiveColorBeast)

        context("Coalition Victory") {
            test("wins the game with a land of each basic type and a creature of each color") {
                val game = scenario()
                    .withPlayers("Coalition Player", "Opponent")
                    .withCardInHand(1, "Coalition Victory")
                    .withLandsOnBattlefield(1, "Plains", 1)
                    .withLandsOnBattlefield(1, "Island", 1)
                    .withLandsOnBattlefield(1, "Swamp", 1)
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withCardOnBattlefield(1, "CV White Bear")
                    .withCardOnBattlefield(1, "CV Blue Bear")
                    .withCardOnBattlefield(1, "CV Black Bear")
                    .withCardOnBattlefield(1, "CV Red Bear")
                    .withCardOnBattlefield(1, "CV Green Bear")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.state = game.state.updateEntity(game.player1Id) {
                    it.with(ManaPoolComponent(white = 1, blue = 1, black = 1, red = 1, green = 1, colorless = 3))
                }
                game.castSpell(1, "Coalition Victory").error shouldBe null
                game.resolveStack()

                withClue("Game should be over") { game.state.gameOver shouldBe true }
                withClue("Coalition player should win") {
                    game.state.winnerId shouldBe game.player1Id
                }
                withClue("Opponent should have lost") {
                    game.state.getEntity(game.state.turnOrder[1])
                        ?.has<PlayerLostComponent>() shouldBe true
                }
            }

            test("a single five-color creature satisfies the 'creature of each color' clause") {
                val game = scenario()
                    .withPlayers("Coalition Player", "Opponent")
                    .withCardInHand(1, "Coalition Victory")
                    .withLandsOnBattlefield(1, "Plains", 1)
                    .withLandsOnBattlefield(1, "Island", 1)
                    .withLandsOnBattlefield(1, "Swamp", 1)
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withCardOnBattlefield(1, "CV Rainbow Beast")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.state = game.state.updateEntity(game.player1Id) {
                    it.with(ManaPoolComponent(white = 1, blue = 1, black = 1, red = 1, green = 1, colorless = 3))
                }
                game.castSpell(1, "Coalition Victory").error shouldBe null
                game.resolveStack()

                withClue("One WUBRG creature plus all five land types should win") {
                    game.state.gameOver shouldBe true
                }
                game.state.winnerId shouldBe game.player1Id
            }

            test("does not win when a color is missing among creatures") {
                val game = scenario()
                    .withPlayers("Coalition Player", "Opponent")
                    .withCardInHand(1, "Coalition Victory")
                    .withLandsOnBattlefield(1, "Plains", 1)
                    .withLandsOnBattlefield(1, "Island", 1)
                    .withLandsOnBattlefield(1, "Swamp", 1)
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withCardOnBattlefield(1, "CV White Bear")
                    .withCardOnBattlefield(1, "CV Blue Bear")
                    .withCardOnBattlefield(1, "CV Black Bear")
                    .withCardOnBattlefield(1, "CV Red Bear")
                    // no green creature
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.state = game.state.updateEntity(game.player1Id) {
                    it.with(ManaPoolComponent(white = 1, blue = 1, black = 1, red = 1, green = 1, colorless = 3))
                }
                game.castSpell(1, "Coalition Victory").error shouldBe null
                game.resolveStack()

                withClue("Missing the green creature — no win") {
                    game.state.gameOver shouldBe false
                }
            }

            test("does not win when a basic land type is missing") {
                val game = scenario()
                    .withPlayers("Coalition Player", "Opponent")
                    .withCardInHand(1, "Coalition Victory")
                    // Only four basic land types (no Forest).
                    .withLandsOnBattlefield(1, "Plains", 1)
                    .withLandsOnBattlefield(1, "Island", 1)
                    .withLandsOnBattlefield(1, "Swamp", 1)
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withCardOnBattlefield(1, "CV White Bear")
                    .withCardOnBattlefield(1, "CV Blue Bear")
                    .withCardOnBattlefield(1, "CV Black Bear")
                    .withCardOnBattlefield(1, "CV Red Bear")
                    .withCardOnBattlefield(1, "CV Green Bear")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.state = game.state.updateEntity(game.player1Id) {
                    it.with(ManaPoolComponent(white = 1, blue = 1, black = 1, red = 1, green = 1, colorless = 3))
                }
                game.castSpell(1, "Coalition Victory").error shouldBe null
                game.resolveStack()

                withClue("Missing the Forest land type — no win") {
                    game.state.gameOver shouldBe false
                }
            }
        }
    }
}
