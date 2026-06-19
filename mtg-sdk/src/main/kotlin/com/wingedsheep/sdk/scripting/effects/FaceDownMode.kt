package com.wingedsheep.sdk.scripting.effects

import kotlinx.serialization.Serializable

/**
 * How a card that enters a zone face down can later be turned face up — i.e. which rule defines
 * the procedure for revealing its real identity.
 *
 * Used by [MoveToZoneEffect] and [MoveCollectionEffect] when moving a card to the battlefield
 * (or, for [HIDDEN], to exile). `null` on those effects means the card enters normally, face up.
 *
 * The engine stores the resulting "turn face up" data on the permanent, and a single generic
 * special action (CR 116.2b) lets the controller turn it face up regardless of which mechanic
 * created it. Adding a new face-down mechanic (e.g. Disguise) is one variant here plus its
 * turn-up-cost rule — not a new effect or action.
 */
@Serializable
enum class FaceDownMode {
    /**
     * Morph (CR 702.37) / Megamorph: the permanent is turned face up by paying the card's morph
     * cost, taken from its [com.wingedsheep.sdk.scripting.KeywordAbility.Morph] ability.
     */
    MORPH,

    /**
     * Manifest (CR 701.40) / Cloak: the permanent is turned face up by paying the card's mana
     * cost, but only if the card representing it is a creature card (CR 701.40b). A manifested
     * non-creature card has no way to be turned face up.
     */
    MANIFEST,

    /**
     * Face down with no turn-up procedure — e.g. a card exiled face down for Hideaway. It is
     * simply hidden; nothing lets it be turned face up in place.
     */
    HIDDEN
}
