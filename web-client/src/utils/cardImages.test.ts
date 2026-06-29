import { describe, expect, it } from 'vitest'
import { getCdnArtCropUrl } from './cardImages'

describe('getCdnArtCropUrl', () => {
  it('rewrites the dominant `normal` CDN size to art_crop on the same host', () => {
    expect(
      getCdnArtCropUrl('https://cards.scryfall.io/normal/front/a/b/abc-123.jpg')
    ).toBe('https://cards.scryfall.io/art_crop/front/a/b/abc-123.jpg')
  })

  it('preserves the cache-busting query string', () => {
    expect(
      getCdnArtCropUrl('https://cards.scryfall.io/normal/front/a/b/abc-123.jpg?1677416389')
    ).toBe('https://cards.scryfall.io/art_crop/front/a/b/abc-123.jpg?1677416389')
  })

  it('rewrites the other croppable sizes', () => {
    for (const size of ['small', 'large', 'border_crop']) {
      expect(getCdnArtCropUrl(`https://cards.scryfall.io/${size}/front/a/b/id.jpg`)).toBe(
        'https://cards.scryfall.io/art_crop/front/a/b/id.jpg'
      )
    }
  })

  it('normalises a png source to the jpg art_crop Scryfall serves', () => {
    expect(getCdnArtCropUrl('https://cards.scryfall.io/png/front/a/b/id.png')).toBe(
      'https://cards.scryfall.io/art_crop/front/a/b/id.jpg'
    )
  })

  it('returns null for null/undefined so callers can fall back to the by-name lookup', () => {
    expect(getCdnArtCropUrl(null)).toBeNull()
    expect(getCdnArtCropUrl(undefined)).toBeNull()
    expect(getCdnArtCropUrl('')).toBeNull()
  })

  it('returns null for non-Scryfall-CDN URLs (e.g. the api host or a test fixture)', () => {
    expect(
      getCdnArtCropUrl('https://api.scryfall.com/cards/named?exact=Foo&format=image&version=normal')
    ).toBeNull()
    expect(getCdnArtCropUrl('not-a-url')).toBeNull()
  })
})
