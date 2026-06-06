package com.wingedsheep.tooling.coverage.emitter

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * The RENDERING dictionary: each mtgish `_Action` tag → the Argentum Effect DSL string it emits.
 * (Its sibling, the CAPABILITY dictionary, is `bridge/Bridge`.) Entries are split across themed
 * `*Handlers.kt` files and merged here; [EmitCtx.renderAction] looks a card's action up in the result.
 *
 * To add a mapping, open the themed file that fits and add one line:
 *  - `simple("Shuffle", "ShuffleLibraryEffect()")`              — a constant, argument-free effect.
 *  - `on("DrawNumberCards") { node, args, tvar -> ... }`        — needs amount/target/filter recovery.
 *
 * One handler may answer several tags (`on("TapPermanent", "UntapPermanent") { ... }`), and one entry
 * helps every set at once. Imports are derived automatically from the emitted string — handlers never
 * track them. Return `null` whenever exact rendering isn't possible: the card downgrades to SCAFFOLD
 * rather than emit something confidently wrong.
 */
// Lazy so it's assembled on first use (renderAction), by which point every themed handler file's
// top-level `val` is initialised. Eager init can cycle: a handler file's val calls `actionHandlers`
// (defined here), which forces this sum to read that same val before it finishes initialising.
internal val ACTION_HANDLERS: Map<String, ActionHandler> by lazy {
    damageDrawLifeHandlers + zoneHandlers + tapLayerStateHandlers + playerContinuousHandlers +
        manaHandlers + tokenHandlers
}

/** A per-`_Action` rendering rule. Returns the Effect DSL string, or null → SCAFFOLD. */
internal typealias ActionHandler = EmitCtx.(node: JsonObject, args: JsonElement?, tvar: String?) -> String?

/** Fluent builder shared by every themed handler file (mirrors `bridge.BridgeBuilder`). */
internal class ActionRegistry {
    private val map = LinkedHashMap<String, ActionHandler>()

    /** Register a handler under one or more tags. */
    fun on(vararg tags: String, handler: ActionHandler) = tags.forEach { map[it] = handler }

    /** A constant 1:1 effect — `tag → fixed DSL string` (imports auto-derived from the string). */
    fun simple(vararg tags: String, dsl: String) = on(*tags) { _, _, _ -> dsl }

    fun build(): Map<String, ActionHandler> = map
}

/** `actionHandlers { on(...) ; simple(...) }` → an immutable tag→handler map. */
internal fun actionHandlers(init: ActionRegistry.() -> Unit): Map<String, ActionHandler> =
    ActionRegistry().apply(init).build()
