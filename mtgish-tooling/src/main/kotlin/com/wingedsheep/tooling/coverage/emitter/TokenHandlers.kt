package com.wingedsheep.tooling.coverage.emitter

import com.wingedsheep.tooling.coverage.asArr
import com.wingedsheep.tooling.coverage.asInt
import com.wingedsheep.tooling.coverage.asStr
import com.wingedsheep.tooling.coverage.findInteger
import com.wingedsheep.tooling.coverage.pascalToUpperSnake
import com.wingedsheep.tooling.coverage.strField
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** Token creation: a `CreateTokens` action -> `Effects.CreateToken(...)`. Only plain creature tokens
 *  with a P/T, colours, creature subtypes and (optionally) keyword abilities are rendered exactly;
 *  anything else (artifact/enchantment tokens, copy tokens, tokens with full abilities) scaffolds. */
internal val tokenHandlers: Map<String, ActionHandler> = actionHandlers {
    on("CreateTokens") { _, args, _ ->
        val spec = args.asArr?.firstOrNull() as? JsonObject ?: return@on null
        createTokenDsl(spec)
    }
}

private val TOKEN_COLOR = mapOf(
    "White" to "WHITE", "Blue" to "BLUE", "Black" to "BLACK", "Red" to "RED", "Green" to "GREEN",
)

/** A `_CreatableToken` spec -> `Effects.CreateToken(...)`, or null (-> SCAFFOLD) for shapes we can't
 *  render exactly. `NumberTokens` wraps a base spec with a fixed count. */
internal fun EmitCtx.createTokenDsl(spec: JsonObject, count: Int = 1): String? {
    when (spec.strField("_CreatableToken")) {
        "NumberTokens" -> {
            val a = spec["args"].asArr ?: return null
            val n = findInteger(a.getOrNull(0)) as? Int ?: return null
            val inner = a.getOrNull(1) as? JsonObject ?: return null
            return createTokenDsl(inner, n)
        }
        "TokenWithPT" -> {
            // args: [ {_PT [p,t]}, {_TokenColorList [names]}, [supertypes], [cardtypes],
            //         {_TokenSubtypes [subs]}, [abilities] ]
            val a = spec["args"].asArr ?: return null
            val cardtypes = (a.getOrNull(3) as? JsonArray)?.mapNotNull { it.asStr() } ?: emptyList()
            if (cardtypes != listOf("Creature")) return null  // only plain creature tokens
            val pt = (a.getOrNull(0) as? JsonObject)?.get("args").asArr ?: return null
            val power = pt.getOrNull(0).asInt() ?: return null
            val toughness = pt.getOrNull(1).asInt() ?: return null
            val colors = ((a.getOrNull(1) as? JsonObject)?.get("args").asArr ?: JsonArray(emptyList()))
                .mapNotNull { it.asStr()?.let(TOKEN_COLOR::get) }
            val subs = ((a.getOrNull(4) as? JsonObject)?.get("args").asArr ?: JsonArray(emptyList()))
                .mapNotNull { it.asStr() }
            if (subs.isEmpty()) return null
            val tokenKeywords = ((a.getOrNull(5) as? JsonArray) ?: JsonArray(emptyList()))
                .mapNotNull { (it as? JsonObject)?.strField("_Rule") }
                .map { pascalToUpperSnake(it) }
                .filter { it in keywords }
            val parts = mutableListOf("power = $power", "toughness = $toughness")
            if (colors.isNotEmpty()) parts.add("colors = setOf(${colors.joinToString(", ") { "Color.$it" }})")
            parts.add("creatureTypes = setOf(${subs.joinToString(", ") { "\"$it\"" }})")
            if (tokenKeywords.isNotEmpty()) parts.add("keywords = setOf(${tokenKeywords.joinToString(", ") { "Keyword.$it" }})")
            if (count != 1) parts.add("count = $count")
            return "Effects.CreateToken(${parts.joinToString(", ")})"
        }
    }
    return null
}
