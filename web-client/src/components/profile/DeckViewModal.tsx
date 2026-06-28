/**
 * Modal that shows both seats' decklists for one finished game from the user's history. Opened from
 * the recent-games table so a player can review what they (and their opponent) actually played. The
 * decks come straight from the recorded match — the server only returns games the user took part in.
 */
import { useEffect, useState } from 'react'
import type React from 'react'
import { type GameDecks, fetchGameDecks } from '@/api/account'
import { GameDeckColumns } from '@/components/deck/GameDeckView'

export function DeckViewModal({
  gameId,
  opponentLabel,
  onClose,
}: {
  gameId: string
  opponentLabel: string
  onClose: () => void
}) {
  const [decks, setDecks] = useState<GameDecks | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let live = true
    setDecks(null)
    setError(null)
    fetchGameDecks(gameId)
      .then((d) => live && setDecks(d))
      .catch(() => live && setError('Could not load this game’s decks.'))
    return () => {
      live = false
    }
  }, [gameId])

  return (
    <div style={styles.backdrop} onClick={onClose} role="presentation">
      <div style={styles.modal} onClick={(e) => e.stopPropagation()} role="dialog" aria-modal>
        <div style={styles.header}>
          <h2 style={styles.title}>Game decks</h2>
          <button type="button" style={styles.close} onClick={onClose} aria-label="Close">
            ✕
          </button>
        </div>
        <p style={styles.muted}>vs {opponentLabel}</p>

        {error ? (
          <p style={styles.error}>{error}</p>
        ) : !decks ? (
          <p style={styles.muted}>Loading…</p>
        ) : decks.participants.length === 0 ? (
          <p style={styles.muted}>No decklist was recorded for this game.</p>
        ) : (
          <div style={styles.columnsWrap}>
            <GameDeckColumns participants={decks.participants} />
          </div>
        )}
      </div>
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  backdrop: {
    position: 'fixed',
    inset: 0,
    backgroundColor: 'rgba(0,0,0,0.6)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 16,
    zIndex: 1000,
  },
  modal: {
    width: 'min(820px, 100%)',
    maxHeight: '85vh',
    overflowY: 'auto',
    backgroundColor: '#12121e',
    border: '1px solid #2a2a3e',
    borderRadius: 14,
    padding: '20px 22px',
  },
  header: { display: 'flex', justifyContent: 'space-between', alignItems: 'center' },
  title: { margin: 0, color: '#fff', fontSize: 20 },
  close: { background: 'none', border: 'none', color: '#999', cursor: 'pointer', fontSize: 18 },
  muted: { margin: '4px 0 0', color: '#888', fontSize: 13 },
  error: { margin: '8px 0 0', color: '#ff6b6b', fontSize: 13 },
  columnsWrap: { marginTop: 14 },
}
