package com.wingedsheep.sdk.scripting.effects

import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.text.TextReplacer
import com.wingedsheep.sdk.scripting.values.EntityReference
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// =============================================================================
// Group Iteration Effects
// =============================================================================

/**
 * Apply an effect to every entity matching a group filter.
 *
 * The inner [effect] is executed once per matched entity.
 * Within the inner effect, [com.wingedsheep.sdk.scripting.targets.EffectTarget.Self] resolves to the
 * current iteration entity rather than the source permanent.
 *
 * @property filter Which entities are affected
 * @property effect The effect to apply to each matched entity
 * @property noRegenerate If true, affected entities cannot be regenerated
 * @property simultaneous If true (default), the group is snapshotted before effects apply
 */
@SerialName("ForEachInGroup")
@Serializable
data class ForEachInGroupEffect(
    val filter: GroupFilter,
    val effect: Effect,
    val noRegenerate: Boolean = false,
    val simultaneous: Boolean = true
) : Effect {
    override val description: String = renderForEachDescription(effect.description, filter, noRegenerate)

    override fun runtimeDescription(resolver: (com.wingedsheep.sdk.scripting.values.DynamicAmount) -> Int): String =
        renderForEachDescription(effect.runtimeDescription(resolver), filter, noRegenerate)

    override fun applyTextReplacement(replacer: TextReplacer): Effect {
        val newFilter = filter.applyTextReplacement(replacer)
        val newEffect = effect.applyTextReplacement(replacer)
        return if (newFilter !== filter || newEffect !== effect)
            copy(filter = newFilter, effect = newEffect) else this
    }
}

/**
 * Iterate the colors of a referenced entity and run [effect] once per color, exposing the
 * current color through the chosen-color context — the same channel `ChooseColorThen` feeds.
 * This makes `ForEachColorOf` the non-interactive sibling of `ChooseColorThen`: any per-color
 * atom that reads the chosen color (`GrantProtectionFromChosenColor`,
 * `GrantHexproofFromChosenColor`, `GrantCantBeBlockedByChosenColor`, …) composes inside it.
 *
 * The entity's colors are read from projected state while it is on the battlefield (so Layer-5
 * color changes and Devoid are honored), else from its printed colors as last-known information.
 * A colorless source produces zero iterations (colorless is not a color, CR 105.2).
 *
 * Example — "[group] gain protection from each of [source]'s colors" (Éowyn, Fearless Knight):
 *
 *     ForEachColorOf(
 *         source = EntityReference.Target(0),
 *         effect = ForEachInGroupEffect(group, GrantProtectionFromChosenColor(EffectTarget.Self)),
 *     )
 *
 * @property source The entity whose colors are iterated
 * @property effect The effect run once per color, with that color set as the context's chosen color
 */
@SerialName("ForEachColorOf")
@Serializable
data class ForEachColorOfEffect(
    val source: EntityReference,
    val effect: Effect,
) : Effect {
    override val description: String =
        "for each color of ${source.description}, ${effect.description}"

    override fun applyTextReplacement(replacer: TextReplacer): Effect {
        val newEffect = effect.applyTextReplacement(replacer)
        return if (newEffect !== effect) copy(effect = newEffect) else this
    }
}

/**
 * Compose the inner effect's text with the group filter so the result reads
 * naturally on the stack and in oracle text. The inner effect describes a
 * single iteration target as "this creature" (i.e. `EffectTarget.Self`); we
 * rewrite that mention to "each [filter]" so e.g. "Deal X damage to this
 * creature" + `AllCreaturesOpponentsControl` renders as "Deal X damage to
 * each creature an opponent controls" rather than the broken concatenation
 * "Deal X damage to this creature all creatures an opponent controls".
 */
private fun renderForEachDescription(innerText: String, filter: GroupFilter, noRegenerate: Boolean): String {
    val capitalizedInner = innerText.replaceFirstChar { it.uppercase() }
    // Strip the leading "all " from the filter description so we can splice it in
    // after "each".
    val filterNoun = filter.description.removePrefix("all ").trimStart()
    val rewritten = when {
        capitalizedInner.contains(" to this creature") ->
            capitalizedInner.replace(" to this creature", " to each $filterNoun")
        capitalizedInner.endsWith(" this creature") ->
            capitalizedInner.removeSuffix(" this creature") + " each $filterNoun"
        capitalizedInner.contains(" this creature ") ->
            capitalizedInner.replace(" this creature ", " each $filterNoun ")
        else -> "$capitalizedInner ${filter.description.replaceFirstChar { it.lowercase() }}"
    }
    return if (noRegenerate) "$rewritten. They can't be regenerated" else rewritten
}