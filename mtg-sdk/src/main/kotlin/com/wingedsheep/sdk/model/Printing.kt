package com.wingedsheep.sdk.model

import kotlinx.serialization.Serializable

/**
 * One specific printing of a card.
 *
 * Multiple `Printing`s can share an `oracleId` — that's the whole point. The engine treats
 * cards by oracle identity (name / `CardDefinition`); printings are a presentation +
 * deckbuilding concern that carry per-print art, set, collector number, and other Scryfall
 * metadata. Decks pick a `Printing` via [PrintingRef]; the engine looks the chosen printing
 * up at game-init and stamps the printing's image URLs onto the per-entity `CardComponent`.
 *
 * The triple `(oracleId, setCode, collectorNumber)` is the natural key. `(setCode,
 * collectorNumber)` alone is unique within Scryfall and is what [PrintingRef] carries.
 */
@Serializable
data class Printing(
    val oracleId: String,
    val name: String,
    val setCode: String,
    val collectorNumber: String,
    val scryfallId: String? = null,
    val artist: String? = null,
    val imageUri: String? = null,
    val backFaceImageUri: String? = null,
    val releaseDate: String? = null,
    val rarity: Rarity = Rarity.COMMON,
    val isPromo: Boolean = false,
    val isFullArt: Boolean = false,
    val frameEffects: List<String> = emptyList(),
) {
    /** Reference form for decks and lookup APIs. */
    val ref: PrintingRef get() = PrintingRef(setCode, collectorNumber)
}
