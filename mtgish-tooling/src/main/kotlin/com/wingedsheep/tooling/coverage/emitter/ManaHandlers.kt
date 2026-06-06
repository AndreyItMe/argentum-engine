package com.wingedsheep.tooling.coverage.emitter

import com.wingedsheep.tooling.coverage.strField
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Mana production: an `AddMana { _ManaProduce }` action -> the `Effects.Add*Mana` facade. Used both as
 * the leaf effect of a mana ability ({T}: Add {C}) and inside a composite ({T}: Add any color, this
 * deals 1 to you). The mana-ability flag itself is set by [EmitCtx.activatedBlock], which recognises a
 * targetless ability whose actions add mana.
 */
internal val manaHandlers: Map<String, ActionHandler> = actionHandlers {
    on("AddMana") { _, args, _ -> manaProduceDsl(args) }
}

private val MANA_PRODUCE_COLOR = mapOf(
    "ManaProduceW" to "Color.WHITE", "ManaProduceU" to "Color.BLUE",
    "ManaProduceB" to "Color.BLACK", "ManaProduceR" to "Color.RED", "ManaProduceG" to "Color.GREEN",
)

/** `{_ManaProduce}` -> the matching mana Effect, or null (-> SCAFFOLD) for shapes we don't render. */
internal fun manaProduceDsl(node: JsonElement?): String? =
    when (val produce = (node as? JsonObject)?.strField("_ManaProduce")) {
        null -> null
        "ManaProduceC" -> "Effects.AddColorlessMana(1)"
        "AnyManaColor" -> "Effects.AddManaOfChoice()"
        else -> MANA_PRODUCE_COLOR[produce]?.let { "Effects.AddMana($it)" }
    }

/** True when this ability is a mana ability: no target, and at least one action adds mana. */
internal fun isManaAbility(tvar: String?, actions: List<JsonObject>): Boolean =
    tvar == null && actions.any { it.strField("_Action") in setOf("AddMana", "AddManaRepeated") }
