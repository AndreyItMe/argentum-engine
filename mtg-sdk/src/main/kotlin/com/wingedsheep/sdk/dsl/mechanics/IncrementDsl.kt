package com.wingedsheep.sdk.dsl

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.TriggeredAbility
import com.wingedsheep.sdk.scripting.conditions.Compare
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.ContextPropertyKey
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import com.wingedsheep.sdk.scripting.values.EntityNumericProperty
import com.wingedsheep.sdk.scripting.values.EntityReference

/**
 * Add Increment (Secrets of Strixhaven) — keyword ability + triggered ability.
 *
 * "Increment (Whenever you cast a spell, if the amount of mana you spent is greater than this
 * creature's power or toughness, put a +1/+1 counter on this creature.)"
 *
 * The keyword ability is display-only (no separate Increment handler exists); the behavior lives
 * entirely in the composed triggered ability wired here:
 *
 * - Trigger: [Triggers.YouCastSpell] — any spell you cast (the creature is already on the
 *   battlefield, so it never triggers off its own casting).
 * - Intervening-if (CR 603.4): the triggering spell's total mana spent
 *   ([ContextPropertyKey.MANA_SPENT_ON_TRIGGERING_SPELL]) must be strictly greater than
 *   `min(power, toughness)` of this creature. "greater than its power or toughness" is satisfied
 *   as soon as the mana spent exceeds the *smaller* of the two, so the threshold is `min`. The
 *   condition reads the creature's **projected** power/toughness (counters included), so the bar
 *   rises as the creature grows. It is checked both when the trigger would fire and on resolution.
 * - Effect: put one +1/+1 counter on this creature.
 *
 * The mana-spent value is the same primitive Opus uses; it is surfaced to the intervening-if via
 * the trigger context (`manaSpentOnTriggeringSpell`), populated from `SpellCastEvent.totalManaSpent`.
 */
fun CardBuilder.increment() {
    keywordAbilityList.add(KeywordAbility.of(Keyword.INCREMENT))
    triggeredAbilities.add(
        TriggeredAbility.create(
            trigger = Triggers.YouCastSpell.event,
            binding = Triggers.YouCastSpell.binding,
            triggerCondition = Compare(
                left = DynamicAmount.ContextProperty(ContextPropertyKey.MANA_SPENT_ON_TRIGGERING_SPELL),
                operator = ComparisonOperator.GT,
                right = DynamicAmount.Min(
                    DynamicAmount.EntityProperty(EntityReference.Source, EntityNumericProperty.Power),
                    DynamicAmount.EntityProperty(EntityReference.Source, EntityNumericProperty.Toughness),
                ),
            ),
            effect = Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 1, EffectTarget.Self),
            descriptionOverride = "Whenever you cast a spell, if the amount of mana you spent is " +
                "greater than this creature's power or toughness, put a +1/+1 counter on this creature.",
        )
    )
}
