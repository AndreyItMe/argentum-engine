package com.wingedsheep.engine.mechanics.mana

import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Subtype

/**
 * The subtypes an object effectively has when evaluating mana-spending restrictions.
 *
 * Changeling makes a card every creature type in every zone, so a Shapeshifter spell on the
 * stack (Firdoch Core) is an Elf spell, a Goblin spell, … and restricted mana keyed to a chosen
 * creature type (Eclipsed Realms, Cavern of Souls, Unclaimed Territory) must be spendable on it.
 * The printed type line alone doesn't say so — spells aren't projected by the layer system, so
 * the expansion the projector does for battlefield permanents has to be redone here.
 */
internal fun paymentSubtypesOf(cardComponent: CardComponent): Set<String> {
    val printed = cardComponent.typeLine.subtypes.map { it.value }.toSet()
    return if (Keyword.CHANGELING in cardComponent.baseKeywords) {
        printed + Subtype.ALL_CREATURE_TYPES
    } else {
        printed
    }
}
