/**
 * Account profile: shows the signed-in user, lets them rename their display name, shows their
 * win/loss record, and offers a small launcher into the deckbuilder's saved-deck browser (the polished
 * overlay that lists account + browser decks with online badges). Prompts sign-in when anonymous.
 */
import { useEffect, useState } from 'react'
import type React from 'react'
import { useNavigate } from 'react-router-dom'
import { type AccountStats, type DeckSummary, fetchStats, listDecks } from '@/api/account'
import { LoginModal } from '@/components/auth/LoginModal'
import { useAuthStore } from '@/store/authStore'

export function ProfilePage() {
  const navigate = useNavigate()
  const user = useAuthStore((s) => s.user)
  const status = useAuthStore((s) => s.status)
  const accountsEnabled = useAuthStore((s) => s.accountsEnabled)
  const init = useAuthStore((s) => s.init)
  const logout = useAuthStore((s) => s.logout)
  const updateDisplayName = useAuthStore((s) => s.updateDisplayName)

  const [stats, setStats] = useState<AccountStats | null>(null)
  const [decks, setDecks] = useState<DeckSummary[]>([])
  const [loginOpen, setLoginOpen] = useState(false)

  const [editingName, setEditingName] = useState(false)
  const [nameDraft, setNameDraft] = useState('')
  const [nameError, setNameError] = useState<string | null>(null)
  const [savingName, setSavingName] = useState(false)

  useEffect(() => {
    if (status === 'idle') void init()
  }, [status, init])

  useEffect(() => {
    if (status !== 'authenticated') return
    void fetchStats().then(setStats).catch(() => setStats(null))
    void listDecks().then(setDecks).catch(() => setDecks([]))
  }, [status])

  const startEditName = () => {
    setNameDraft(user?.displayName ?? '')
    setNameError(null)
    setEditingName(true)
  }

  const submitName = async () => {
    const trimmed = nameDraft.trim()
    if (!trimmed) {
      setNameError('Name cannot be empty')
      return
    }
    setSavingName(true)
    setNameError(null)
    try {
      await updateDisplayName(trimmed)
      setEditingName(false)
    } catch (e) {
      setNameError(e instanceof Error ? e.message : 'Could not update name')
    } finally {
      setSavingName(false)
    }
  }

  if (status === 'authenticated' && user) {
    return (
      <div style={styles.wrap}>
        <div style={styles.container}>
          <div style={styles.header}>
            <button type="button" style={styles.link} onClick={() => navigate('/')}>
              ← Home
            </button>
            <button type="button" style={styles.link} onClick={logout}>
              Sign out
            </button>
          </div>

          {editingName ? (
            <div style={styles.nameEditRow}>
              <input
                style={styles.nameInput}
                value={nameDraft}
                maxLength={40}
                autoFocus
                onChange={(e) => setNameDraft(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') void submitName()
                  if (e.key === 'Escape') setEditingName(false)
                }}
              />
              <button type="button" style={styles.smallPrimary} disabled={savingName} onClick={() => void submitName()}>
                {savingName ? 'Saving…' : 'Save'}
              </button>
              <button type="button" style={styles.smallGhost} onClick={() => setEditingName(false)}>
                Cancel
              </button>
            </div>
          ) : (
            <div style={styles.nameRow}>
              <h1 style={styles.title}>{user.displayName}</h1>
              <button type="button" style={styles.editLink} onClick={startEditName}>
                Edit name
              </button>
            </div>
          )}
          {nameError ? <p style={styles.error}>{nameError}</p> : <p style={styles.muted}>{user.email}</p>}

          <div style={styles.statsRow}>
            <Stat label="Games" value={stats?.games ?? 0} />
            <Stat label="Wins" value={stats?.wins ?? 0} />
            <Stat label="Losses" value={stats?.losses ?? 0} />
            <Stat label="Win rate" value={stats ? `${Math.round(stats.winRate * 100)}%` : '—'} />
          </div>

          <h2 style={styles.section}>Decks</h2>
          {/* Small launcher into the deckbuilder's saved-deck browser (the polished overlay that lists
              account + browser decks with online badges) — no need to duplicate that UI here. */}
          <button type="button" style={styles.deckManager} onClick={() => navigate('/deckbuilder?decks=open')}>
            <span style={styles.deckManagerText}>
              <span style={styles.deckManagerTitle}>Manage my decks</span>
              <span style={styles.muted}>
                {decks.length === 0
                  ? 'No decks saved to your account yet'
                  : `${decks.length} deck${decks.length === 1 ? '' : 's'} saved to your account`}
              </span>
            </span>
            <span style={styles.deckManagerArrow}>Open deck browser →</span>
          </button>
        </div>
      </div>
    )
  }

  const resolving = status === 'idle' || status === 'loading'

  return (
    <div style={styles.wrap}>
      <div style={styles.container}>
        <button type="button" style={styles.link} onClick={() => navigate('/')}>
          ← Home
        </button>
        <h1 style={styles.title}>Your account</h1>
        {accountsEnabled ? (
          <>
            <p style={styles.muted}>
              Sign in to save decks to the cloud and track your win/loss record.
            </p>
            <button type="button" style={styles.primary} onClick={() => setLoginOpen(true)}>
              Sign in
            </button>
            <LoginModal open={loginOpen} onClose={() => setLoginOpen(false)} />
          </>
        ) : resolving ? (
          <p style={styles.muted}>Loading…</p>
        ) : (
          <p style={styles.muted}>Accounts aren't available on this server.</p>
        )}
      </div>
    </div>
  )
}

function Stat({ label, value }: { label: string; value: number | string }) {
  return (
    <div style={styles.stat}>
      <div style={styles.statValue}>{value}</div>
      <div style={styles.statLabel}>{label}</div>
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  wrap: { minHeight: '100vh', backgroundColor: '#0a0a15', padding: '32px 16px' },
  container: { maxWidth: 720, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 12 },
  header: { display: 'flex', justifyContent: 'space-between' },
  link: { background: 'none', border: 'none', color: '#8b9bff', cursor: 'pointer', fontSize: 14, padding: 0 },
  title: { margin: '8px 0 0', color: '#fff', fontSize: 28 },
  nameRow: { display: 'flex', alignItems: 'baseline', gap: 12, flexWrap: 'wrap' },
  editLink: { background: 'none', border: 'none', color: '#8b9bff', cursor: 'pointer', fontSize: 13, padding: 0 },
  nameEditRow: { display: 'flex', alignItems: 'center', gap: 8, marginTop: 8, flexWrap: 'wrap' },
  nameInput: {
    flex: '1 1 200px',
    minWidth: 0,
    backgroundColor: '#14141f',
    border: '1px solid #2a2a3e',
    borderRadius: 8,
    padding: '8px 12px',
    color: '#fff',
    fontSize: 18,
  },
  smallPrimary: {
    padding: '8px 14px',
    borderRadius: 8,
    border: 'none',
    backgroundColor: '#5b6ee1',
    color: '#fff',
    fontWeight: 600,
    fontSize: 13,
    cursor: 'pointer',
  },
  smallGhost: {
    padding: '8px 14px',
    borderRadius: 8,
    border: '1px solid #2a2a3e',
    backgroundColor: 'transparent',
    color: '#aaa',
    fontSize: 13,
    cursor: 'pointer',
  },
  error: { margin: 0, color: '#ff6b6b', fontSize: 13 },
  muted: { margin: 0, color: '#888', fontSize: 14 },
  section: { margin: '20px 0 4px', color: '#fff', fontSize: 18 },
  statsRow: { display: 'flex', gap: 12, marginTop: 12, flexWrap: 'wrap' },
  stat: {
    flex: '1 1 120px',
    backgroundColor: '#14141f',
    border: '1px solid #2a2a3e',
    borderRadius: 12,
    padding: '16px 12px',
    textAlign: 'center',
  },
  statValue: { color: '#fff', fontSize: 26, fontWeight: 700 },
  statLabel: { color: '#888', fontSize: 12, marginTop: 4, textTransform: 'uppercase', letterSpacing: 0.5 },
  deckManager: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
    textAlign: 'left',
    backgroundColor: '#14141f',
    border: '1px solid #2a2a3e',
    borderRadius: 12,
    padding: '16px 18px',
    color: '#fff',
    cursor: 'pointer',
  },
  deckManagerText: { display: 'flex', flexDirection: 'column', gap: 4 },
  deckManagerTitle: { fontSize: 16, fontWeight: 600 },
  deckManagerArrow: { color: '#8b9bff', fontSize: 14, fontWeight: 600, flexShrink: 0 },
  primary: {
    alignSelf: 'flex-start',
    marginTop: 8,
    padding: '10px 18px',
    borderRadius: 8,
    border: 'none',
    backgroundColor: '#5b6ee1',
    color: '#fff',
    fontWeight: 600,
    cursor: 'pointer',
  },
}
