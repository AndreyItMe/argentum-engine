package com.wingedsheep.mtg.sets.definitions.por.cards

import com.wingedsheep.sdk.model.Printing
import com.wingedsheep.sdk.model.Rarity

/**
 * Blinding Light reprint in Portal.
 *
 * The canonical [com.wingedsheep.sdk.model.CardDefinition] lives in Mirage's `cards/`
 * package (earliest real printing); this file contributes only the Portal-specific
 * presentation row.
 */
val BlindingLightReprint = Printing(
    oracleId = "6b315dc3-c330-4b30-b6ad-4da12ccf6ca3",
    name = "Blinding Light",
    setCode = "POR",
    collectorNumber = "8",
    scryfallId = "4ea283d2-8f00-4836-81b4-c041b0469dcb",
    artist = "John Coulthart",
    imageUri = "https://cards.scryfall.io/normal/front/4/e/4ea283d2-8f00-4836-81b4-c041b0469dcb.jpg?1562446633",
    releaseDate = "1997-05-01",
    rarity = Rarity.RARE,
)
