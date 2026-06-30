package com.wingedsheep.mtg.sets.definitions.fdn.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Goblin Boarders
 * {2}{R}
 * Creature — Goblin Pirate
 * 3/2
 *
 * Raid — This creature enters with a +1/+1 counter on it if you attacked this turn.
 *
 * Raid enters-with-counter follows the engine's established convention (War-Name
 * Aspirant): an enters-the-battlefield trigger gated by the intervening-if condition
 * [Conditions.YouAttackedThisTurn], adding one +1/+1 counter to itself.
 */
val GoblinBoarders = card("Goblin Boarders") {
    manaCost = "{2}{R}"
    colorIdentity = "R"
    typeLine = "Creature — Goblin Pirate"
    power = 3
    toughness = 2
    oracleText = "Raid — This creature enters with a +1/+1 counter on it if you attacked this turn."

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        triggerCondition = Conditions.YouAttackedThisTurn
        effect = Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 1, EffectTarget.Self)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "87"
        artist = "Filipe Pagliuso"
        imageUri = "https://cards.scryfall.io/normal/front/4/4/4409a063-bf2a-4a49-803e-3ce6bd474353.jpg?1782689191"
    }
}
