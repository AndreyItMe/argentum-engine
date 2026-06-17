package com.wingedsheep.mtg.sets.definitions.sos.cards

import com.wingedsheep.sdk.core.Counters
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
import com.wingedsheep.sdk.scripting.effects.MoveType
import com.wingedsheep.sdk.scripting.effects.RevealHandEffect
import com.wingedsheep.sdk.scripting.effects.SelectFromCollectionEffect
import com.wingedsheep.sdk.scripting.effects.SelectionMode
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.targets.TargetCreature
import com.wingedsheep.sdk.scripting.targets.TargetOpponent
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Render Speechless — Secrets of Strixhaven #220
 * {2}{W}{B} · Sorcery
 *
 * Target opponent reveals their hand. You choose a nonland card from it. That player discards
 * that card.
 * Put two +1/+1 counters on up to one target creature.
 *
 * Two independently-targeted clauses (CR 601.2c): the opponent (target 0) is the discarded-from
 * player, and an optional creature (target 1) receives the counters. The targeted discard is the
 * Duress pipeline (reveal → gather hand → controller chooses a nonland card → discard), filtered
 * to nonland only. The counter clause uses an optional creature target so it can be cast with no
 * legal/desired creature, in which case [Effects.AddCounters] no-ops on the empty target.
 */
val RenderSpeechless = card("Render Speechless") {
    manaCost = "{2}{W}{B}"
    colorIdentity = "WB"
    typeLine = "Sorcery"
    oracleText = "Target opponent reveals their hand. You choose a nonland card from it. That " +
        "player discards that card.\nPut two +1/+1 counters on up to one target creature."

    spell {
        val opponent = target("target opponent", TargetOpponent())
        val creature = target("up to one target creature", TargetCreature(optional = true))
        effect = Effects.Composite(
            listOf(
                RevealHandEffect(opponent),
                GatherCardsEffect(
                    source = CardSource.FromZone(Zone.HAND, Player.ContextPlayer(0)),
                    storeAs = "opponentHand",
                ),
                SelectFromCollectionEffect(
                    from = "opponentHand",
                    selection = SelectionMode.ChooseExactly(DynamicAmount.Fixed(1)),
                    chooser = Chooser.Controller,
                    filter = GameObjectFilter.Nonland,
                    storeSelected = "toDiscard",
                    prompt = "Choose a nonland card to discard",
                    alwaysPrompt = true,
                    showAllCards = true,
                ),
                MoveCollectionEffect(
                    from = "toDiscard",
                    destination = CardDestination.ToZone(Zone.GRAVEYARD, Player.ContextPlayer(0)),
                    moveType = MoveType.Discard,
                ),
                Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 2, EffectTarget.ContextTarget(1)),
            ),
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "220"
        artist = "David Astruga"
        flavorText = "\"Seems like I took the words right out of your mouth.\""
        imageUri = "https://cards.scryfall.io/normal/front/2/5/25bbb1c7-14e8-444f-ab98-e95f50927460.jpg?1775938531"
    }
}
