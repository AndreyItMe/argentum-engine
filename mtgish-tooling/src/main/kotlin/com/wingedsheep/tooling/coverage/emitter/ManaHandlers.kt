package com.wingedsheep.tooling.coverage.emitter

import com.wingedsheep.tooling.coverage.Call
import com.wingedsheep.tooling.coverage.Dsl
import com.wingedsheep.tooling.coverage.arg
import com.wingedsheep.tooling.coverage.asArr
import com.wingedsheep.tooling.coverage.call
import com.wingedsheep.tooling.coverage.strField
import kotlinx.serialization.json.JsonArray
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
    on("AddManaRepeated") { _, args, _ ->
        // "Add {R} for each Goblin on the battlefield" (Brightstone Ritual): a single mana symbol produced
        // a dynamic number of times -> Effects.AddMana(color, <count>). Only one colour/colourless symbol
        // with a recoverable dynamic count renders; anything else scaffolds.
        val arr = args.asArr ?: return@on null
        val amt = dynamicAmount(arr.getOrNull(1)) ?: return@on null
        when (val produce = (arr.getOrNull(0) as? JsonObject)?.strField("_ManaProduce")) {
            "ManaProduceC" -> call("Effects.AddColorlessMana", arg(amt))
            else -> MANA_PRODUCE_COLOR[produce]?.let { call("Effects.AddMana", arg(it), arg(amt)) }
        }
    }
}

private val MANA_PRODUCE_COLOR = mapOf(
    "ManaProduceW" to "Color.WHITE", "ManaProduceU" to "Color.BLUE",
    "ManaProduceB" to "Color.BLACK", "ManaProduceR" to "Color.RED", "ManaProduceG" to "Color.GREEN",
)

/** `{_ManaProduce}` -> the matching mana Effect, or null (-> SCAFFOLD) for shapes we don't render. */
internal fun manaProduceDsl(node: JsonElement?): Dsl? =
    when (val produce = (node as? JsonObject)?.strField("_ManaProduce")) {
        null -> null
        "ManaProduceC" -> call("Effects.AddColorlessMana", arg("1"))
        "AnyManaColor" -> call("Effects.AddManaOfChoice")
        "And" -> manaAndDsl(node)  // {B}{B}{B} (Dark Ritual), {C}{C}{C} (Basalt Monolith), …
        else -> MANA_PRODUCE_COLOR[produce]?.let { call("Effects.AddMana", arg(it)) }
    }

/** `And[<produce>…]` -> one `Effects.Add*Mana(color, count)` per distinct mana, composited (inline) when
 *  the pool mixes colors. Null (-> SCAFFOLD) if any child is itself a non-leaf produce (nested And /
 *  choice), so we never emit a partial pool. */
private fun manaAndDsl(node: JsonObject): Dsl? {
    val children = node["args"] as? JsonArray ?: return null
    val produces = children.map { (it as? JsonObject)?.strField("_ManaProduce") ?: return null }
    if (produces.any { it != "ManaProduceC" && it !in MANA_PRODUCE_COLOR }) return null
    val counts = LinkedHashMap<String, Int>()
    produces.forEach { counts[it] = (counts[it] ?: 0) + 1 }
    val parts = counts.map { (p, n) ->
        if (p == "ManaProduceC") call("Effects.AddColorlessMana", arg("$n"))
        else call("Effects.AddMana", arg(MANA_PRODUCE_COLOR.getValue(p)), arg("$n"))
    }
    // The mana pool uses an INLINE Effects.Composite(...) (single line), distinct from the multi-line
    // Composite node the effect-list builder emits.
    return if (parts.size == 1) parts[0] else Call("Effects.Composite", parts.map { arg(it) })
}

/** True when this ability is a mana ability: no target, and at least one action adds mana. */
internal fun isManaAbility(tvar: String?, actions: List<JsonObject>): Boolean =
    tvar == null && actions.any { it.strField("_Action") in setOf("AddMana", "AddManaRepeated") }
