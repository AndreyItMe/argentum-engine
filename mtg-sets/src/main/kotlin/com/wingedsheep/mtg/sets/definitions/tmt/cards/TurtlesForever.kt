package com.wingedsheep.mtg.sets.definitions.tmt.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.Chooser
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.effects.MoveCollectionEffect
import com.wingedsheep.sdk.scripting.effects.RevealCollectionEffect
import com.wingedsheep.sdk.scripting.effects.SelectFromCollectionEffect
import com.wingedsheep.sdk.scripting.effects.SelectionMode
import com.wingedsheep.sdk.scripting.effects.SelectionRestriction
import com.wingedsheep.sdk.scripting.effects.ShuffleLibraryEffect
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Turtles Forever
 * {3}{W}
 * Instant
 *
 * Search your library and/or outside the game for exactly four legendary creature cards
 * you own with different names, then reveal those cards. An opponent chooses two of them.
 * Put the chosen cards into your hand and shuffle the rest into your library.
 *
 * A two-chooser "you assemble, they split" pile, composed entirely from the atomic pipeline:
 *  1. Gather every legendary creature card across your library *and* "outside the game" — the
 *     private [Zone.SIDEBOARD] (CR 100.4 / 400.11a), as the wish cycle uses.
 *  2. You search: [SelectionMode.ChooseExactly] four with [SelectionRestriction.OnePerCardName]
 *     for "exactly four ... with different names" (clamps down if fewer eligible exist).
 *  3. Reveal the four you found.
 *  4. The *opponent* ([Chooser.Opponent]) chooses two — those go to your hand; the remainder is
 *     captured and shuffled into your library (note: unchosen cards pulled from outside the game
 *     end up in your library, not back in the sideboard, exactly as printed).
 */
val TurtlesForever = card("Turtles Forever") {
    manaCost = "{3}{W}"
    colorIdentity = "W"
    typeLine = "Instant"
    oracleText = "Search your library and/or outside the game for exactly four legendary creature cards " +
        "you own with different names, then reveal those cards. An opponent chooses two of them. " +
        "Put the chosen cards into your hand and shuffle the rest into your library."

    spell {
        effect = Effects.Composite(
            listOf(
                // 1. Gather legendary creature cards from your library and sideboard ("outside the game").
                GatherCardsEffect(
                    source = CardSource.FromMultipleZones(
                        zones = listOf(Zone.LIBRARY, Zone.SIDEBOARD),
                        player = Player.You,
                        filter = GameObjectFilter.Creature.legendary()
                    ),
                    storeAs = "tfPool"
                ),
                // 2. You search for exactly four with different names.
                SelectFromCollectionEffect(
                    from = "tfPool",
                    selection = SelectionMode.ChooseExactly(DynamicAmount.Fixed(4)),
                    restrictions = listOf(SelectionRestriction.OnePerCardName),
                    storeSelected = "tfFound",
                    prompt = "Search for exactly four legendary creature cards with different names"
                ),
                // 3. Reveal the four found cards.
                RevealCollectionEffect(from = "tfFound"),
                // 4. An opponent splits them: two chosen, the rest as remainder.
                SelectFromCollectionEffect(
                    from = "tfFound",
                    selection = SelectionMode.ChooseExactly(DynamicAmount.Fixed(2)),
                    chooser = Chooser.Opponent,
                    storeSelected = "tfChosen",
                    storeRemainder = "tfRest",
                    prompt = "Choose two of those cards to put into their hand",
                    alwaysPrompt = true
                ),
                // 5. Chosen cards go to your hand.
                MoveCollectionEffect(
                    from = "tfChosen",
                    destination = CardDestination.ToZone(Zone.HAND, Player.You)
                ),
                // 6. Shuffle the rest into your library.
                MoveCollectionEffect(
                    from = "tfRest",
                    destination = CardDestination.ToZone(Zone.LIBRARY, Player.You)
                ),
                ShuffleLibraryEffect()
            )
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "27"
        artist = "Devin Elle Kurtz"
        flavorText = "\"See you around the multiverse, bros.\"\n—One Leonardo or another"
        imageUri = "https://cards.scryfall.io/normal/front/f/0/f0db974a-3289-4727-9aaf-e9cca9113c87.jpg?1760102536"
    }
}
