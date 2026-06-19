package com.wingedsheep.engine.state.components.identity

import com.wingedsheep.engine.state.Component
import com.wingedsheep.sdk.scripting.costs.PayCost
import com.wingedsheep.sdk.scripting.effects.Effect
import kotlinx.serialization.Serializable

/**
 * Face-down status for morph/manifest.
 */
@Serializable
data object FaceDownComponent : Component

/**
 * Stores the morph cost and original card identity for face-down creatures.
 * This allows the creature to be turned face up by paying the morph cost.
 */
@Serializable
data class MorphDataComponent(
    val morphCost: PayCost,
    val originalCardDefinitionId: String,
    /** Effect to execute as a replacement effect when turned face up (e.g., put 5 +1/+1 counters on it) */
    val faceUpEffect: Effect? = null
) : Component

/**
 * Marks a card as having a morph keyword ability.
 * Present on the entity regardless of zone, so filters can check
 * whether a card in hand/library/graveyard has morph.
 */
@Serializable
data object HasMorphAbilityComponent : Component

/**
 * Marks a face-down permanent as a *manifested* permanent (CR 701.40a), as opposed to a morphed
 * one. Both are 2/2 face-down creatures, but which mechanic created the permanent is public
 * information (paper Magic represents them with distinct Manifest / Morph tokens), so this drives
 * the face-down art shown to every viewer. Present only while the permanent is face down.
 */
@Serializable
data object ManifestedComponent : Component
