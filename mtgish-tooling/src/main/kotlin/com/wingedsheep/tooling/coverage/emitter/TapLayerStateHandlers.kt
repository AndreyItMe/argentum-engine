package com.wingedsheep.tooling.coverage.emitter

import com.wingedsheep.tooling.coverage.asArr
import com.wingedsheep.tooling.coverage.asInt
import com.wingedsheep.tooling.coverage.findAdjustPt
import com.wingedsheep.tooling.coverage.findInteger
import com.wingedsheep.tooling.coverage.jsonContains
import com.wingedsheep.tooling.coverage.strField
import com.wingedsheep.tooling.coverage.subtypes
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Tap/untap, continuous P/T & keyword grants (CreatePermanentLayerEffectUntil), and turn-state
 *  effects (skip untap / skip combat / extra lands). */
internal val tapLayerStateHandlers: Map<String, ActionHandler> = actionHandlers {

    on("TapPermanent", "UntapPermanent") { node, args, tvar ->
        val tgt = refTarget(args, tvar) ?: return@on null
        "Effects.${if (node.strField("_Action") == "TapPermanent") "Tap" else "Untap"}($tgt)"
    }
    on("GoadCreature") { _, args, tvar ->  // CR 701.15: goad target creature
        val tgt = refTarget(args, tvar) ?: return@on null
        "Effects.Goad($tgt)"
    }
    on("RegeneratePermanent") { _, args, _ ->
        // Self-regeneration ("{cost}: Regenerate this") renders faithfully. A chosen target's
        // requirement isn't always recovered exactly (e.g. "Regenerate target Zombie" flattens the
        // subtype to "permanent"), so scaffold the targeted case rather than emit a too-broad target.
        if (!jsonContains(args, "_Permanent", "ThisPermanent")) return@on null
        "RegenerateEffect(EffectTarget.Self)"
    }
    on("TapEachPermanent", "UntapEachPermanent") { node, args, _ ->
        val verb = if (node.strField("_Action") == "TapEachPermanent") "Tap" else "Untap"
        if (jsonContains(node, "_Permanents", "Ref_TargetPermanents")) {  // Tidal Surge: each chosen target
            return@on "Effects.${verb}EachTarget()"
        }
        val filter = groupFilterDsl(args) ?: return@on null  // mass: tap/untap a group
        "Effects.ForEachInGroup($filter, Effects.$verb(EffectTarget.Self))"
    }

    on("CreatePermanentLayerEffectUntil", "CreateEachPermanentLayerEffectUntil") { node, _, tvar ->
        renderLayerEffect(node, node.strField("_Action")!!, tvar)
    }

    on("CreatePlayerEffectUntil") { node, _, _ ->  // Summer Bloom: may play N additional lands
        val n = findInteger(node)
        if (jsonContains(node, "_PlayerEffect", "MayPlayAdditionalLands") && n is Int) {
            "PlayAdditionalLandsEffect($n)"
        } else null
    }

    on("EachPermanentDoesntUntapDuringControllersNextUntap") { _, _, tvar ->
        if (tvar != null) "SkipUntapEffect($tvar)" else "SkipUntapEffect()"
    }
    on("SkipAllCombatPhasesTheirNextTurn") { _, _, tvar ->
        if (tvar != null) "SkipCombatPhasesEffect($tvar)" else "SkipCombatPhasesEffect()"
    }
}

/** CreatePermanentLayerEffectUntil / its each-permanent form -> ModifyStats / GrantKeyword,
 *  optionally over a group (ForEachInGroup). The `_Expiration` is honoured exactly: end-of-turn uses
 *  the default-duration facade; "for as long as it remains tapped" carries an explicit
 *  Duration.WhileSourceTapped(); any other expiration scaffolds rather than emit a wrong duration. */
internal fun EmitCtx.renderLayerEffect(node: JsonObject, action: String, tvar: String?): String? {
    val mass = action == "CreateEachPermanentLayerEffectUntil"
    val target = if (mass) "EffectTarget.Self" else refTarget(node["args"], tvar)
    if (target == null) return null
    val duration = expirationDsl(node) ?: return null  // unknown expiration -> SCAFFOLD
    val durArg = if (duration.isEmpty()) "" else ", $duration"
    val inner = mutableListOf<String>()
    val pt = findAdjustPt(node)
    if (pt is JsonArray && pt.size == 2) {
        // ModifyStats' facade carries no duration param, so a non-default duration uses the raw effect.
        inner.add(
            if (duration.isEmpty()) "Effects.ModifyStats(${pt[0].asInt()}, ${pt[1].asInt()}, $target)"
            else "ModifyStatsEffect(${pt[0].asInt()}, ${pt[1].asInt()}, $target, $duration)"
        )
    }
    if (jsonContains(node, "_LayerEffect", "AddAbility")) {
        var kw: String? = null
        if (jsonContains(node, "_Rule", "Landwalk")) {  // AddAbility{Landwalk{Forest}} -> FORESTWALK
            val subs = subtypes(node)
            if (subs.isNotEmpty() && (subs[0].uppercase() + "WALK") in keywords) kw = subs[0].uppercase() + "WALK"
        }
        kw = kw ?: keywordOf(node)
        if (kw != null) {
            inner.add("Effects.GrantKeyword(Keyword.$kw, $target$durArg)")
        } else return null
    }
    if (inner.isEmpty()) return null
    val effect = if (inner.size == 1) inner[0] else composite(inner)
    if (mass) {
        val gfArg = (node["args"].asArr)?.getOrNull(0) ?: JsonObject(emptyMap())
        val filter = groupFilterDsl(gfArg) ?: return null
        return "Effects.ForEachInGroup($filter, $effect)"
    }
    return effect
}

/** The layer effect's `_Expiration` -> "" for the default (end-of-turn) facade, an explicit
 *  `Duration.*` DSL for a recognised non-default duration, or null (-> SCAFFOLD) for one we can't
 *  render exactly (so the emitter never silently substitutes the wrong duration). */
private fun expirationDsl(node: JsonObject): String? =
    when (firstExpiration(node)) {
        null, "UntilEndOfTurn" -> ""
        "ForAsLongAsPermanentRemainsTapped" -> "Duration.WhileSourceTapped()"
        else -> null
    }

/** The first `_Expiration` discriminator value anywhere in the subtree. */
private fun firstExpiration(node: JsonElement?): String? {
    when (node) {
        is JsonObject -> {
            node.strField("_Expiration")?.let { return it }
            node.values.forEach { firstExpiration(it)?.let { v -> return v } }
        }
        is JsonArray -> node.forEach { firstExpiration(it)?.let { v -> return v } }
        else -> {}
    }
    return null
}
