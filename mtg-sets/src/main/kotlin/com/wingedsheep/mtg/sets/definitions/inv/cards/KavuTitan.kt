package com.wingedsheep.mtg.sets.definitions.inv.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.conditions.WasKicked
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Kavu Titan
 * {1}{G}
 * Creature — Kavu
 * 2/2
 * Kicker {2}{G}
 * If this creature was kicked, it enters with three +1/+1 counters on it and with trample.
 */
val KavuTitan = card("Kavu Titan") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Kavu"
    power = 2
    toughness = 2
    oracleText = "Kicker {2}{G} (You may pay an additional {2}{G} as you cast this spell.)\n" +
        "If this creature was kicked, it enters with three +1/+1 counters on it and with trample."

    keywordAbility(KeywordAbility.kicker("{2}{G}"))

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        triggerCondition = WasKicked
        effect = Effects.Composite(
            Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 3, EffectTarget.Self),
            Effects.GrantKeyword(Keyword.TRAMPLE, EffectTarget.Self, Duration.Permanent),
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "194"
        artist = "Todd Lockwood"
        imageUri = "https://cards.scryfall.io/normal/front/2/c/2c5fb86d-1d9a-4da2-bb5b-4266faa20197.jpg?1562904050"
    }
}
