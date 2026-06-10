package com.wingedsheep.sdk.scripting.effects

import kotlinx.serialization.Serializable
import com.wingedsheep.sdk.scripting.text.TextReplaceable
import com.wingedsheep.sdk.scripting.text.TextReplacer
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Sealed hierarchy of effects.
 * Effects define WHAT happens when an ability resolves.
 *
 * Effect implementations are organized across multiple files in the effects/ subdirectory:
 * - LifeEffects.kt - Life gain/loss effects
 * - DamageEffects.kt - Damage dealing effects
 * - DrawingEffects.kt - Card draw/discard effects
 * - RemovalEffects.kt - Destroy/exile/sacrifice effects
 * - PermanentEffects.kt - Permanent state transformations (animation, morph, equipment)
 * - CounterEffects.kt - Counter manipulation effects
 * - KeywordAndAbilityEffects.kt - Keyword/ability grant effects
 * - TypeAndColorEffects.kt - Type/subtype/color change effects
 * - ControlEffects.kt - Control change effects
 * - StatsEffects.kt - Power/toughness modification effects
 * - TapEffects.kt - Tap/untap effects
 * - ManaEffects.kt - Mana-producing effects
 * - TokenEffects.kt - Token creation effects
 * - LibraryEffects.kt - Library manipulation effects
 * - StackEffects.kt - Counterspell effects
 * - PlayerEffects.kt - Turn/phase manipulation effects
 * - CombatEffects.kt - Combat-specific effects
 * - CompositeEffects.kt - Composite/modal/may effects
 * - TransformEffects.kt - Transform effects
 *
 * Supporting types are organized in subdirectories:
 * - values/ - DynamicAmount, PlayerReference, ZoneReference
 * - filters/unified/ - GameObjectFilter, GroupFilter, TargetFilter
 * - targets/ - EffectTarget
 * - costs/ - PayCost
 */
@Serializable
sealed interface Effect : TextReplaceable<Effect> {
    /** Human-readable description of the effect */
    val description: String

    /**
     * Returns a description with dynamic amounts evaluated to concrete values.
     * Override in effects that use [DynamicAmount] to show runtime values on the stack.
     */
    fun runtimeDescription(resolver: (DynamicAmount) -> Int): String = description

    /**
     * Operator to chain effects.
     * Allows syntax like: EffectA then EffectB
     */
    infix fun then(next: Effect): CompositeEffect {
        return if (this is CompositeEffect) {
            CompositeEffect(this.effects + next)
        } else {
            CompositeEffect(listOf(this, next))
        }
    }
}