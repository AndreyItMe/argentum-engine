package com.wingedsheep.sdk.dsl

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.scripting.AbilityCost
import com.wingedsheep.sdk.scripting.ActivatedAbility
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.costs.CostAtom

/**
 * Craft with [filter] — [cost] (CR 702.167, The Lost Caverns of Ixalan).
 *
 * "[Cost], Exile this permanent, Exile [filter] from among permanents you control and/or
 * [filter] cards from your graveyard: Return this card to the battlefield transformed under
 * its owner's control. Activate only as a sorcery."
 *
 * Composed from existing primitives — no special handling at the DSL layer:
 *  - cost = [AbilityCost.Mana] ⊕ [AbilityCost.Craft] (the latter handles both the self-exile
 *    and the materials-exile in one atomic cost shape, recording the exiled materials on
 *    the source so the back face's CDA can read them).
 *  - effect = [com.wingedsheep.sdk.scripting.effects.ReturnSelfFromExileTransformedEffect].
 *  - `timing = TimingRule.SorcerySpeed` enforces "Activate only as a sorcery".
 *
 * Marks the front face with the [Keyword.CRAFT] tag for client display.
 *
 * ```kotlin
 * craft(filter = Filters.Dinosaur, cost = "{4}{R}")                             // one or more
 * craft(filter = Filters.Artifact, cost = "{2}{W}", minCount = 1, maxCount = 1) // exactly one
 * ```
 *
 * @param filter The material filter (the [filter] in "Craft with [filter]").
 * @param cost The mana portion of the craft cost.
 * @param materialDescription Optional override for the filter's name in the rendered cost
 *   description — e.g. "one or more Dinosaurs". Defaults to the filter's own description.
 * @param minCount Minimum number of materials (CR 702.167a).
 * @param maxCount Maximum number of materials, or `null` for "... or more" wordings. Exact-count
 *   crafts ("Craft with artifact", "Craft with two creatures") set `maxCount == minCount`.
 */
fun CardBuilder.craft(
    filter: GameObjectFilter,
    cost: String,
    materialDescription: String? = null,
    minCount: Int = 1,
    maxCount: Int? = null
) {
    val materials = materialDescription ?: "one or more ${filter.description}s"
    activatedAbilities.add(
        ActivatedAbility(
            cost = AbilityCost.Composite(
                listOf(
                    AbilityCost.Atom(CostAtom.Mana(ManaCost.parse(cost))),
                    AbilityCost.Craft(filter, minCount = minCount, maxCount = maxCount)
                )
            ),
            effect = com.wingedsheep.sdk.scripting.effects.ReturnSelfFromExileTransformedEffect,
            targetRequirements = emptyList(),
            timing = TimingRule.SorcerySpeed,
            descriptionOverride = "Craft with $materials — $cost"
        )
    )
    keywordSet.add(Keyword.CRAFT)
}
