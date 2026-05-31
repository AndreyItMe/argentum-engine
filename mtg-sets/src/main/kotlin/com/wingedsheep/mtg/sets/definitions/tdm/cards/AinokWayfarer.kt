package com.wingedsheep.mtg.sets.definitions.tdm.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.CompositeEffect
import com.wingedsheep.sdk.scripting.effects.ConditionalEffect
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.effects.MoveCollectionEffect
import com.wingedsheep.sdk.scripting.effects.SelectFromCollectionEffect
import com.wingedsheep.sdk.scripting.effects.SelectionMode
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Ainok Wayfarer
 * {1}{G}
 * Creature — Dog Scout
 * 1/1
 *
 * When this creature enters, mill three cards. You may put a land card from among them
 * into your hand. If you don't, put a +1/+1 counter on this creature. (To mill three
 * cards, put the top three cards of your library into your graveyard.)
 *
 * Modeled as the Town Greeter mill-then-take pipeline: gather the top three, send them
 * to the graveyard (the mill), then optionally pull a land card back into hand. The
 * "if you don't" counter is gated on the selected collection being empty — whether the
 * player declined or no land was milled, the counter goes on.
 */
val AinokWayfarer = card("Ainok Wayfarer") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Dog Scout"
    power = 1
    toughness = 1
    oracleText = "When this creature enters, mill three cards. You may put a land card from among " +
        "them into your hand. If you don't, put a +1/+1 counter on this creature. (To mill three " +
        "cards, put the top three cards of your library into your graveyard.)"

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = CompositeEffect(
            listOf(
                GatherCardsEffect(
                    source = CardSource.TopOfLibrary(DynamicAmount.Fixed(3)),
                    storeAs = "milled"
                ),
                MoveCollectionEffect(
                    from = "milled",
                    destination = CardDestination.ToZone(Zone.GRAVEYARD)
                ),
                SelectFromCollectionEffect(
                    from = "milled",
                    selection = SelectionMode.ChooseUpTo(DynamicAmount.Fixed(1)),
                    filter = GameObjectFilter.Land,
                    storeSelected = "selected",
                    showAllCards = true,
                    prompt = "You may put a land card into your hand",
                    selectedLabel = "Put in hand",
                    remainderLabel = "Leave in graveyard"
                ),
                MoveCollectionEffect(
                    from = "selected",
                    destination = CardDestination.ToZone(Zone.HAND)
                ),
                ConditionalEffect(
                    condition = Conditions.Not(Conditions.CollectionContainsMatch("selected")),
                    effect = Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 1, EffectTarget.Self)
                )
            )
        )
        description = "When this creature enters, mill three cards. You may put a land card from " +
            "among them into your hand. If you don't, put a +1/+1 counter on this creature."
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "134"
        artist = "Filipe Pagliuso"
        flavorText = "\"There has to be a faster route through Sagu Jungle. Give me a week.\""
        imageUri = "https://cards.scryfall.io/normal/front/5/7/57695a9b-8f72-4ccc-a946-5d5037b09b8f.jpg?1743204503"
    }
}
