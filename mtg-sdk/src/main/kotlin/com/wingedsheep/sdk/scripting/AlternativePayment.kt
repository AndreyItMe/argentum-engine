package com.wingedsheep.sdk.scripting

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable

/**
 * Represents the player's choice for alternative payment methods like Delve and Convoke.
 *
 * These are specified when casting a spell and affect how the mana cost is paid:
 * - **Delve**: Exile cards from graveyard, each pays {1} generic mana
 * - **Convoke**: Tap creatures, each pays {1} or one mana of the creature's color
 * - **Harmonize**: Tap a single creature you control to reduce the (harmonize) cost
 *   by an amount of generic mana equal to its power
 *
 * @property delvedCards Cards to exile from graveyard for Delve payment
 * @property convokedCreatures Creatures to tap for Convoke payment, with color choice
 * @property harmonizeCreature Single creature tapped for Harmonize, reducing the generic
 *           portion of the cost by its power. Null when no creature is tapped.
 */
@Serializable
data class AlternativePaymentChoice(
    val delvedCards: List<EntityId> = emptyList(),
    val convokedCreatures: Map<EntityId, ConvokePayment> = emptyMap(),
    val harmonizeCreature: EntityId? = null
) {
    /**
     * Whether any alternative payment is being used.
     */
    val isEmpty: Boolean
        get() = delvedCards.isEmpty() && convokedCreatures.isEmpty() && harmonizeCreature == null

    /**
     * Total generic mana reduction from Delve.
     */
    val delveReduction: Int
        get() = delvedCards.size

    /**
     * Total generic mana reduction from Convoke (creatures paying generic).
     */
    val convokeGenericReduction: Int
        get() = convokedCreatures.values.count { it.color == null }

    /**
     * Get Convoke reduction for a specific color.
     */
    fun convokeColorReduction(color: Color): Int =
        convokedCreatures.values.count { it.color == color }

    companion object {
        val NONE = AlternativePaymentChoice()
    }
}

/**
 * Represents how a creature is being used to pay via Convoke.
 *
 * @property color The color of mana this creature is paying for.
 *                 If null, the creature pays for {1} generic mana instead.
 */
@Serializable
data class ConvokePayment(
    val color: Color? = null  // null = pays for generic
)
