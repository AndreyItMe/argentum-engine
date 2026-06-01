package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for a batch of graveyard-centric TDM cards:
 *  - Rainveil Rejuvenator (#152): ETB may mill 3; {T}: add {G} equal to power.
 *  - Kishla Trawlers (#50): ETB may exile a creature card from your graveyard; when you do,
 *    return target instant/sorcery card from your graveyard to hand.
 *  - Yathan Roadwatcher (#236): ETB (if cast) mill 4; when you do, reanimate a creature card
 *    with mana value 3 or less from your graveyard.
 *  - Unrooted Ancestor (#96): {1}, Sacrifice another creature: gain indestructible EOT, tap it.
 */
class TdmGraveyardBatchScenarioTest : ScenarioTestBase() {

    private val rainveilManaAbilityId =
        cardRegistry.getCard("Rainveil Rejuvenator")!!.activatedAbilities.first().id
    private val unrootedAbilityId =
        cardRegistry.getCard("Unrooted Ancestor")!!.activatedAbilities.first().id

    init {
        context("Rainveil Rejuvenator") {
            test("ETB may mill three cards") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Rainveil Rejuvenator")
                    .withLandsOnBattlefield(1, "Forest", 4)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Shock")
                    .withCardInLibrary(1, "Glory Seeker")
                    .withCardInLibrary(1, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cast = game.castSpell(1, "Rainveil Rejuvenator")
                withClue("Casting Rainveil Rejuvenator should succeed: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack()

                withClue("ETB should present a 'may mill' yes/no decision") {
                    game.hasPendingDecision() shouldBe true
                }
                val libBefore = game.librarySize(1)
                game.answerYesNo(true)
                game.resolveStack()

                withClue("Milling three should move three cards from library to graveyard") {
                    game.librarySize(1) shouldBe libBefore - 3
                    game.findCardsInGraveyard(1, "Forest").size shouldBe 1
                }
            }

            test("tap adds {G} equal to power") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    // Place it directly so it has no summoning sickness; its {T} is a mana ability.
                    .withCardOnBattlefield(1, "Rainveil Rejuvenator")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val rejuvenator = game.findPermanent("Rainveil Rejuvenator")!!
                val activation = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = rejuvenator,
                        abilityId = rainveilManaAbilityId,
                    )
                )
                withClue("Activating the mana ability should succeed: ${activation.error}") {
                    activation.error shouldBe null
                }

                withClue("Rainveil Rejuvenator should be tapped after the mana ability") {
                    game.state.getEntity(rejuvenator)?.has<TappedComponent>() shouldBe true
                }
                val pool = game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()
                withClue("Mana pool should hold 2 green (equal to power)") {
                    (pool?.green ?: 0) shouldBe 2
                }
            }

            test("ETB may mill three can be declined") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Rainveil Rejuvenator")
                    .withLandsOnBattlefield(1, "Forest", 4)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Rainveil Rejuvenator")
                game.resolveStack()

                val libBefore = game.librarySize(1)
                game.answerYesNo(false)
                game.resolveStack()

                withClue("Declining the mill leaves the library untouched") {
                    game.librarySize(1) shouldBe libBefore
                }
            }
        }

        context("Kishla Trawlers") {
            test("ETB exiles a creature card and returns an instant/sorcery to hand") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Kishla Trawlers")
                    .withCardInGraveyard(1, "Glory Seeker") // creature card to exile
                    .withCardInGraveyard(1, "Shock")        // instant to return
                    .withLandsOnBattlefield(1, "Island", 3)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cast = game.castSpell(1, "Kishla Trawlers")
                withClue("Casting Kishla Trawlers should succeed: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack()

                // Reflexive "you may" yes/no.
                withClue("ETB should present a 'you may exile' yes/no decision") {
                    game.hasPendingDecision() shouldBe true
                }
                game.answerYesNo(true)
                game.resolveStack()

                // With a single creature card in the graveyard, it is auto-selected and
                // exiled; the reflexive trigger then prompts for the instant/sorcery target.
                withClue("Glory Seeker should be exiled from the graveyard") {
                    game.findCardsInGraveyard(1, "Glory Seeker").size shouldBe 0
                }
                withClue("Should now prompt to return an instant/sorcery to hand") {
                    game.hasPendingDecision() shouldBe true
                }
                val shock = game.findCardsInGraveyard(1, "Shock").first()
                game.selectTargets(listOf(shock))
                game.resolveStack()

                withClue("Glory Seeker should be exiled from the graveyard") {
                    game.findCardsInGraveyard(1, "Glory Seeker").size shouldBe 0
                }
                withClue("Shock should be returned to hand") {
                    game.findCardsInHand(1, "Shock").size shouldBe 1
                    game.findCardsInGraveyard(1, "Shock").size shouldBe 0
                }
            }

            test("ETB does nothing when there is no creature card to exile") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Kishla Trawlers")
                    .withCardInGraveyard(1, "Shock") // only an instant, no creature card
                    .withLandsOnBattlefield(1, "Island", 3)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Kishla Trawlers")
                game.resolveStack()

                withClue("With no creature card to exile, the may-decision is skipped entirely") {
                    game.hasPendingDecision() shouldBe false
                }
                withClue("Shock stays in the graveyard") {
                    game.findCardsInGraveyard(1, "Shock").size shouldBe 1
                }
            }
        }

        context("Yathan Roadwatcher") {
            test("ETB (cast) mills four and reanimates a creature with mana value 3 or less") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Yathan Roadwatcher")
                    .withCardInGraveyard(1, "Glory Seeker") // MV 2 — legal reanimation target
                    .withLandsOnBattlefield(1, "Plains", 1)
                    .withLandsOnBattlefield(1, "Swamp", 1)
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(1, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cast = game.castSpell(1, "Yathan Roadwatcher")
                withClue("Casting Yathan Roadwatcher should succeed: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack()

                // The mill is non-optional; reflexive return then asks for a target.
                withClue("Should prompt for a reanimation target after milling") {
                    game.hasPendingDecision() shouldBe true
                }
                withClue("Four cards should have been milled") {
                    game.librarySize(1) shouldBe 0
                }
                val glorySeeker = game.findCardsInGraveyard(1, "Glory Seeker").first()
                game.selectTargets(listOf(glorySeeker))
                game.resolveStack()

                withClue("Glory Seeker should be returned to the battlefield") {
                    game.isOnBattlefield("Glory Seeker") shouldBe true
                    game.findCardsInGraveyard(1, "Glory Seeker").size shouldBe 0
                }
            }
        }

        context("Unrooted Ancestor") {
            test("activated ability sacrifices another creature, grants indestructible, and taps itself") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Unrooted Ancestor")
                    .withCardOnBattlefield(1, "Glory Seeker") // fodder
                    .withLandsOnBattlefield(1, "Swamp", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val ancestor = game.findPermanent("Unrooted Ancestor")!!
                val fodder = game.findPermanent("Glory Seeker")!!

                withClue("Unrooted Ancestor is not indestructible before activation") {
                    game.state.projectedState.hasKeyword(ancestor, Keyword.INDESTRUCTIBLE) shouldBe false
                }

                val activation = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = ancestor,
                        abilityId = unrootedAbilityId,
                        costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(fodder)),
                    )
                )
                withClue("Activating Unrooted Ancestor should succeed: ${activation.error}") {
                    activation.error shouldBe null
                }
                game.resolveStack()

                withClue("Glory Seeker should be sacrificed to the graveyard") {
                    game.findPermanents("Glory Seeker").contains(fodder) shouldBe false
                    game.findCardsInGraveyard(1, "Glory Seeker").size shouldBe 1
                }
                withClue("Unrooted Ancestor gains indestructible") {
                    game.state.projectedState.hasKeyword(ancestor, Keyword.INDESTRUCTIBLE) shouldBe true
                }
                withClue("Unrooted Ancestor is tapped by its own ability") {
                    game.state.getEntity(ancestor)?.has<TappedComponent>() shouldBe true
                }
            }
        }
    }
}
