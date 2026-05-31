package com.wingedsheep.mtg.sets.definitions.tdm.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.CompositeEffect

/**
 * Iridescent Tiger
 * {4}{R}
 * Creature — Cat
 * 3/4
 *
 * When this creature enters, if you cast it, add {W}{U}{B}{R}{G}.
 *
 * The "add {W}{U}{B}{R}{G}" produces exactly one mana of each color, modeled as a
 * composite of five single-color [Effects.AddMana]. This is a triggered ability (it
 * uses the stack), not a mana ability, so it can be responded to. The intervening-if
 * "if you cast it" is gated by [Conditions.WasCast] — the trigger does nothing if the
 * creature entered without being cast.
 */
val IridescentTiger = card("Iridescent Tiger") {
    manaCost = "{4}{R}"
    colorIdentity = "R"
    typeLine = "Creature — Cat"
    power = 3
    toughness = 4
    oracleText = "When this creature enters, if you cast it, add {W}{U}{B}{R}{G}."

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        triggerCondition = Conditions.WasCast
        effect = CompositeEffect(
            listOf(
                Effects.AddMana(Color.WHITE),
                Effects.AddMana(Color.BLUE),
                Effects.AddMana(Color.BLACK),
                Effects.AddMana(Color.RED),
                Effects.AddMana(Color.GREEN)
            )
        )
        description = "When this creature enters, if you cast it, add {W}{U}{B}{R}{G}."
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "109"
        artist = "Fajareka Setiawan"
        flavorText = "The power of the dragonstorms transformed not only the land but many of the creatures within it."
        imageUri = "https://cards.scryfall.io/normal/front/e/3/e3abbc8b-2bf8-478e-a541-f8019d150054.jpg?1743213469"
        ruling("2025-04-04", "Iridescent Tiger's ability isn't a mana ability. It uses the stack and can be responded to.")
        ruling("2025-04-04", "Iridescent Tiger's ability triggers if you cast it from any zone. It doesn't trigger if you put Iridescent Tiger onto the battlefield without casting it.")
    }
}
