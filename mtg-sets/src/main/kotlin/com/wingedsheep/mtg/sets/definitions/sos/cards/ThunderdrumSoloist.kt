package com.wingedsheep.mtg.sets.definitions.sos.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.dsl.opus
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Thunderdrum Soloist — Secrets of Strixhaven #134
 * {1}{R} · Creature — Dwarf Bard · 1/3
 *
 * Reach
 * Opus — Whenever you cast an instant or sorcery spell, this creature deals 1 damage to each
 * opponent. If five or more mana was spent to cast that spell, this creature deals 3 damage to
 * each opponent instead.
 *
 * "Opus" is an ability word (flavor only). The `opus { }` builder wires the spell-cast trigger and
 * the 5+ mana tier (`ContextProperty(MANA_SPENT_ON_TRIGGERING_SPELL) >= 5`). The 3-damage variant
 * replaces the 1-damage base, so it is `insteadIfFiveOrMore` (cf. Colorstorm Stallion, which uses
 * `alsoIfFiveOrMore` for an additive bonus).
 *
 * Substituted for Soaring Stoneglider in this batch: that card's "exile two cards from your
 * graveyard or pay {1}{W}" additional cost has no existing SDK primitive (the only OR-cost
 * additional costs are the bespoke BlightOrPay / BeholdOrPay), so it would require an add-feature.
 */
val ThunderdrumSoloist = card("Thunderdrum Soloist") {
    manaCost = "{1}{R}"
    colorIdentity = "R"
    typeLine = "Creature — Dwarf Bard"
    power = 1
    toughness = 3
    oracleText = "Reach\n" +
        "Opus — Whenever you cast an instant or sorcery spell, this creature deals 1 damage to " +
        "each opponent. If five or more mana was spent to cast that spell, this creature deals 3 " +
        "damage to each opponent instead."

    keywords(Keyword.REACH)

    opus {
        effect = Effects.DealDamage(1, EffectTarget.PlayerRef(Player.EachOpponent))
        insteadIfFiveOrMore = Effects.DealDamage(3, EffectTarget.PlayerRef(Player.EachOpponent))
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "134"
        artist = "Edgar Sánchez Hidalgo"
        imageUri = "https://cards.scryfall.io/normal/front/5/9/590d1d95-ed13-4121-899f-f5a2d8a6617a.jpg?1775937905"
    }
}
