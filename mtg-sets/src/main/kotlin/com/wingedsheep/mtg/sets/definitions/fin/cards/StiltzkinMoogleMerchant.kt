package com.wingedsheep.mtg.sets.definitions.fin.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.GiveControlToTargetPlayerEffect
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetOpponent
import com.wingedsheep.sdk.scripting.targets.TargetPermanent

/**
 * Stiltzkin, Moogle Merchant
 * {W}
 * Legendary Creature — Moogle
 * 1/2
 * Lifelink
 * {2}, {T}: Target opponent gains control of another target permanent you control. If they do, you
 * draw a card.
 *
 * The donation always succeeds when both targets are still legal at resolution, so the "if they do"
 * rider is modeled as a sequenced draw. The only case the printed text would withhold the draw is the
 * rare one where the donated permanent has left the battlefield (or you've lost control of it) before
 * resolution while the opponent target stays legal; the engine has no control-change success
 * criterion to gate on, so the draw still happens in that narrow corner.
 */
val StiltzkinMoogleMerchant = card("Stiltzkin, Moogle Merchant") {
    manaCost = "{W}"
    colorIdentity = "W"
    typeLine = "Legendary Creature — Moogle"
    oracleText = "Lifelink\n{2}, {T}: Target opponent gains control of another target permanent you control. If they do, you draw a card."
    power = 1
    toughness = 2

    keywords(Keyword.LIFELINK)

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{2}"), Costs.Tap)
        val opponent = target("target opponent", TargetOpponent())
        val permanent = target(
            "another target permanent you control",
            TargetPermanent(
                filter = TargetFilter(GameObjectFilter.Permanent.youControl(), excludeSelf = true),
            ),
        )
        effect = Effects.Composite(
            GiveControlToTargetPlayerEffect(
                permanent = permanent,
                newController = opponent,
            ),
            Effects.DrawCards(1),
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "34"
        artist = "Hendry Iwanaga"
        flavorText = "\"How about a three-piece combo of Hi-Potion, Ether, and Phoenix Pinion for 444 Gil?\""
        imageUri = "https://cards.scryfall.io/normal/front/0/6/06a972a4-0c1b-4f12-a5a5-fdea47c4cd35.jpg?1748705882"
    }
}
