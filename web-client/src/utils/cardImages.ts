/**
 * Standard MTG morph face-down card art from Scryfall.
 * This is the official morph token from Commander 2019 (TC19 #27) showing the distinctive helmet artwork.
 * Source: https://scryfall.com/card/tc19/27/morph
 */
export const MORPH_FACE_DOWN_IMAGE_URL = 'https://cards.scryfall.io/normal/front/e/9/e9375cbe-93c0-41a5-a6e3-fb4416f54a69.jpg'

/**
 * Standard MTG manifest face-down card art from Scryfall.
 * The official Manifest token from Duskmourn: House of Horror (TDSK #18). Manifested permanents
 * (CR 701.40) are shown with this instead of the morph token.
 * Source: https://scryfall.com/card/tdsk/18/manifest
 */
export const MANIFEST_FACE_DOWN_IMAGE_URL = 'https://cards.scryfall.io/normal/front/0/1/01104ab1-84e1-4c78-853d-637c6554bdf9.jpg'

/**
 * Standard MTG card back image.
 */
export const CARD_BACK_IMAGE_URL = 'https://backs.scryfall.io/normal/2/2/222b7a3b-2321-4d4c-af19-19338b134971.jpg?1677416389'

/**
 * Get the image URL for a card.
 *
 * Uses the provided imageUri if available (from card metadata),
 * otherwise falls back to Scryfall API lookup by card name.
 *
 * @param cardName The card's name (used for Scryfall fallback)
 * @param imageUri The card's direct image URI from metadata (optional)
 * @param version The image version/size to request
 * @returns The image URL to use
 */
export function getCardImageUrl(
  cardName: string,
  imageUri?: string | null,
  version: 'small' | 'normal' | 'large' = 'normal'
): string {
  if (imageUri) {
    return imageUri
  }
  return getScryfallFallbackUrl(cardName, version)
}

/**
 * Get a Scryfall API fallback URL for a card image.
 *
 * @param cardName The card's name
 * @param version The image version/size to request
 * @returns The Scryfall API image URL
 */
export function getScryfallFallbackUrl(
  cardName: string,
  version: 'small' | 'normal' | 'large' = 'normal'
): string {
  // Token names have a " Token" suffix (e.g., "Insect Token") that Scryfall doesn't use
  const scryfallName = cardName.endsWith(' Token') ? cardName.slice(0, -6) : cardName
  return `https://api.scryfall.com/cards/named?exact=${encodeURIComponent(scryfallName)}&format=image&version=${version}`
}

/**
 * Get the landscape *art crop* for a card by name (Scryfall `version=art_crop`).
 *
 * Unlike the full-card images above, this returns just the illustration with no frame,
 * which is the right shape for a wide banner/tile background. Used by the saved-deck
 * gallery to paint each deck's hero art from its rarest card.
 *
 * @param cardName The card's name (the default printing's art is used)
 */
export function getScryfallArtCropUrl(cardName: string): string {
  const scryfallName = cardName.endsWith(' Token') ? cardName.slice(0, -6) : cardName
  return `https://api.scryfall.com/cards/named?exact=${encodeURIComponent(scryfallName)}&format=image&version=art_crop`
}
