package com.wingedsheep.sdk.model

import com.wingedsheep.sdk.limited.BoosterStrategy
import com.wingedsheep.sdk.limited.StandardBooster

/**
 * A Magic: The Gathering set: a named collection of card definitions that can be
 * registered into the engine and (optionally) used for sealed/draft.
 *
 * Implementations are expected to self-stamp the [setCode] on every card returned
 * by [cards] and [basicLands], so consumers do not have to remember to call
 * `.copy(setCode = ...)`.
 */
interface MtgSet {

    /** Three-letter set code (e.g. "EOE"). Stable identifier across the codebase. */
    val code: String

    /** Display name (e.g. "Edge of Eternities"). */
    val displayName: String

    /**
     * Set release date in ISO `YYYY-MM-DD` form, or null if unknown.
     * Used by the deckbuilder to sort sets chronologically and to display the year.
     */
    val releaseDate: String? get() = null

    /** All non-basic-land card definitions in the set, with [code] already stamped. */
    val cards: List<CardDefinition>

    /** Basic land definitions, with [code] already stamped. Empty if the set has none. */
    val basicLands: List<CardDefinition> get() = emptyList()

    /** Block (e.g. "Onslaught") if the set is part of one, otherwise null. */
    val block: String? get() = null

    /** True when this set's [cards] list is incomplete relative to the official set. */
    val incomplete: Boolean get() = false

    /**
     * Strategy that turns the set's card pool into a single booster pack.
     * Defaults to a standard 11C / 3U / 1R(or mythic) pack; sets override
     * this to express custom slot rules (guaranteed legendary, extra slots,
     * etc.).
     */
    val boosterStrategy: BoosterStrategy get() = StandardBooster()

    /**
     * If this set has no basic lands of its own, the set whose lands should be
     * registered alongside it (Scourge and Legions reuse Onslaught lands).
     */
    val basicLandsFallback: MtgSet? get() = null

    /** Whether the set is wired into the booster generator for sealed/draft. */
    val sealedSupported: Boolean get() = false

    /**
     * Per-printing rows this set contributes to [com.wingedsheep.engine.registry.PrintingRegistry].
     *
     * Most printings are synthesised at startup from each registered [CardDefinition]'s
     * `setCode` + `metadata.collectorNumber`, so this list can stay empty for the canonical
     * printing of each card. Use it to register **additional** printings — e.g. when a card
     * is reprinted in a later set, the canonical [CardDefinition] (script, types, P/T) lives
     * in the original set's package, but the new set's package can contribute a [Printing]
     * row carrying the reprint's art, set code, collector number, and Scryfall metadata.
     *
     * Each entry's `name` must match an existing [CardDefinition.name] in some registered set.
     */
    val printings: List<Printing> get() = emptyList()
}
