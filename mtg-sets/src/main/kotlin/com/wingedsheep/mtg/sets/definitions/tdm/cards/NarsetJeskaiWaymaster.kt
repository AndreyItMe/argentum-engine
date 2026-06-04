package com.wingedsheep.mtg.sets.definitions.tdm.cards

import com.wingedsheep.sdk.dsl.EffectPatterns
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.IfYouDoEffect
import com.wingedsheep.sdk.scripting.effects.MayEffect
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Narset, Jeskai Waymaster — Tarkir: Dragonstorm #209
 * {U}{R}{W} · Legendary Creature — Human Monk · 3/4 · Rare
 *
 * At the beginning of your end step, you may discard your hand. If you do, draw cards equal to
 * the number of spells you've cast this turn.
 *
 * "You may discard your hand. If you do, draw …" is the standard
 * [MayEffect] + [IfYouDoEffect] pair (same shape as Vaultguard Trooper): the optional yes/no
 * wraps [EffectPatterns.discardHand]; on "yes" the hand is discarded (even if empty) and the draw
 * fires. The draw amount is [DynamicAmount.SpellsCastThisTurn] for the controller (reads the
 * per-player `spellsCastThisTurnByPlayer` history), counting every spell cast this turn — Narset's
 * own spell included if it was cast this turn.
 */
val NarsetJeskaiWaymaster = card("Narset, Jeskai Waymaster") {
    manaCost = "{U}{R}{W}"
    colorIdentity = "URW"
    typeLine = "Legendary Creature — Human Monk"
    power = 3
    toughness = 4
    oracleText = "At the beginning of your end step, you may discard your hand. If you do, draw " +
        "cards equal to the number of spells you've cast this turn."

    triggeredAbility {
        trigger = Triggers.YourEndStep
        effect = MayEffect(
            IfYouDoEffect(
                action = EffectPatterns.discardHand(EffectTarget.Controller),
                ifYouDo = Effects.DrawCards(DynamicAmount.SpellsCastThisTurn(Player.You))
            )
        )
        description = "You may discard your hand. If you do, draw cards equal to the number of spells you've cast this turn."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "209"
        artist = "Randy Vargas"
        imageUri = "https://cards.scryfall.io/normal/front/6/b/6b77cbc1-dbc8-44d9-aa29-15cbb19afecd.jpg?1743204826"
    }
}
