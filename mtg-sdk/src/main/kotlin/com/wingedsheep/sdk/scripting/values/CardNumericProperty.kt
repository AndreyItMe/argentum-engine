package com.wingedsheep.sdk.scripting.values

import kotlinx.serialization.Serializable

/**
 * A numeric property of a card that can be aggregated over.
 * Used with [DynamicAmount.AggregateBattlefield].
 */
@Serializable
enum class CardNumericProperty(val description: String) {
    MANA_VALUE("mana value"),
    POWER("power"),
    TOUGHNESS("toughness")
}

/**
 * An aggregation function applied to a collection of battlefield entities.
 * Used with [DynamicAmount.AggregateBattlefield].
 */
@Serializable
enum class Aggregation {
    COUNT, MAX, MIN, SUM,
    /** Count distinct card types across all matched entities */
    DISTINCT_TYPES,
    /** Count distinct colors across all matched entities */
    DISTINCT_COLORS,
    /** Count distinct English card names across all matched entities */
    DISTINCT_NAMES,
    /**
     * Count distinct basic land subtypes (Plains, Island, Swamp, Mountain, Forest)
     * across all matched entities. Used for the Domain ability word.
     * Bounded by 5; nonbasic lands with basic subtypes (e.g., Tundra → Plains+Island)
     * contribute each of their basic subtypes.
     */
    DISTINCT_BASIC_LAND_SUBTYPES
}
