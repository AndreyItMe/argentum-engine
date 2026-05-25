import { useState } from 'react'
import { useGameStore } from '@/store/gameStore.ts'
import type {
  CombatResolutionDecision,
  DamageEdge,
  EntityId,
} from '@/types'
import { useResponsive } from '@/hooks/useResponsive.ts'
import { getCardImageUrl } from '@/utils/cardImages.ts'

/**
 * The combat resolution board (CR 510 / 702.22). Renders the bipartite damage graph the engine
 * emits for combat damage: each source (attacker, or a blocker that blocks 2+ attackers) lists
 * its outgoing {@link DamageEdge}s pre-filled with the engine's lethal-first default. The local
 * player adjusts the edges they own ({@link DamageEdge.editableBy}) with +/- steppers, then
 * confirms. The server is authoritative — the board only clamps to `[0, maximum]` and the
 * per-source power budget; CR 510.1c / 702.19b legality is enforced on submit by the engine.
 */
export function CombatResolutionBoard({ decision }: { decision: CombatResolutionDecision }) {
  const submit = useGameStore((s) => s.submitCombatResolutionDecision)
  const gameState = useGameStore((s) => s.gameState)
  const playerId = useGameStore((s) => s.playerId)
  const hoverCard = useGameStore((s) => s.hoverCard)
  const responsive = useResponsive()

  const [amounts, setAmounts] = useState<Record<string, number>>(
    () => Object.fromEntries(decision.edges.map((e) => [e.id, e.amount]))
  )

  // Group edges by source, preserving the engine's emission order.
  const sourceIds: EntityId[] = []
  for (const edge of decision.edges) {
    if (!sourceIds.includes(edge.sourceId)) sourceIds.push(edge.sourceId)
  }

  const edgesBySource = (sourceId: EntityId) =>
    decision.edges.filter((e) => e.sourceId === sourceId).slice().sort((a, b) => a.unlockOrder - b.unlockOrder)

  const sourcePower = (sourceId: EntityId) =>
    decision.edges.filter((e) => e.sourceId === sourceId).reduce((m, e) => Math.max(m, e.maximum), 0)

  const sourceTotal = (sourceId: EntityId) =>
    decision.edges.filter((e) => e.sourceId === sourceId).reduce((sum, e) => sum + (amounts[e.id] ?? 0), 0)

  const ownsAnyEditable = decision.edges.some((e) => e.editableBy === playerId && e.maximum > 0)
  const bandingActive = decision.attackers.some((a) => a.bandId != null)

  const adjust = (edge: DamageEdge, delta: number) => {
    setAmounts((prev) => {
      const current = prev[edge.id] ?? 0
      const next = current + delta
      if (next < 0 || next > edge.maximum) return prev
      // Per-source budget: never assign more than the source's power across its edges.
      if (delta > 0 && sourceTotal(edge.sourceId) - current + next > sourcePower(edge.sourceId)) return prev
      return { ...prev, [edge.id]: next }
    })
  }

  const handleReset = () =>
    setAmounts(Object.fromEntries(decision.edges.map((e) => [e.id, e.amount])))

  const handleConfirm = () => {
    // Submit only the edges this player owns; the engine merges them with the rest.
    const owned = decision.edges.filter((e) => e.editableBy === playerId)
    submit(owned.map((e) => ({ edgeId: e.id, amount: amounts[e.id] ?? 0 })))
  }

  // ── Display helpers ─────────────────────────────────────────────────────
  const cardName = (id: EntityId): string => {
    const a = decision.attackers.find((x) => x.id === id)
    if (a) return a.name
    const b = decision.blockers.find((x) => x.id === id)
    if (b) return b.name
    const d = decision.defenders.find((x) => x.id === id)
    if (d) return d.name
    return gameState?.cards[id]?.name ?? 'Unknown'
  }
  const cardImage = (id: EntityId): string | null | undefined => gameState?.cards[id]?.imageUri
  const isPlayerTarget = (id: EntityId): boolean =>
    decision.defenders.some((d) => d.id === id && d.kind === 'PLAYER')

  const sourceLabel = (id: EntityId): string => {
    const a = decision.attackers.find((x) => x.id === id)
    if (a) return `${a.name}  ${a.power}/${a.toughness}`
    const b = decision.blockers.find((x) => x.id === id)
    if (b) return `${b.name}  ${b.power}/${b.toughness}`
    return cardName(id)
  }

  const cardW = responsive.isMobile ? 84 : 116
  const cardH = Math.round(cardW * 1.4)

  const targetVisual = (id: EntityId) => {
    if (isPlayerTarget(id)) {
      const defender = decision.defenders.find((d) => d.id === id)
      return (
        <div
          style={{
            width: cardW, height: cardH, borderRadius: 8, backgroundColor: '#1a1a2e',
            border: '2px solid #333', display: 'flex', flexDirection: 'column',
            alignItems: 'center', justifyContent: 'center', gap: 6,
          }}
        >
          <span style={{ color: '#888', fontSize: 26 }}>&#9823;</span>
          <span style={{ color: 'white', fontSize: responsive.fontSize.small }}>{defender?.name ?? 'Player'}</span>
          {defender?.lifeOrLoyaltyOrDefense != null && (
            <span style={{ color: '#888', fontSize: responsive.fontSize.small }}>
              Life: {defender.lifeOrLoyaltyOrDefense}
            </span>
          )}
        </div>
      )
    }
    return (
      <img
        src={getCardImageUrl(cardName(id), cardImage(id))}
        alt={cardName(id)}
        onMouseEnter={(e) => hoverCard(id, { x: e.clientX, y: e.clientY })}
        onMouseLeave={() => hoverCard(null)}
        style={{ width: cardW, height: cardH, objectFit: 'cover', borderRadius: 8, border: '2px solid #333' }}
        onError={(e) => { e.currentTarget.style.display = 'none' }}
      />
    )
  }

  const stepBtn = (label: string, onClick: () => void, enabled: boolean, color: string) => (
    <button
      onClick={onClick}
      disabled={!enabled}
      style={{
        width: 30, height: 30, borderRadius: 6, border: 'none',
        backgroundColor: enabled ? color : '#333', color: enabled ? 'white' : '#666',
        fontSize: 18, fontWeight: 'bold', cursor: enabled ? 'pointer' : 'not-allowed',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}
    >
      {label}
    </button>
  )

  const edgeRow = (edge: DamageEdge) => {
    const amount = amounts[edge.id] ?? 0
    const editable = edge.editableBy === playerId
    const isDrain = edge.isTrampleDrain
    const atLethal = !isDrain && edge.lethal > 0 && amount >= edge.lethal
    const arrow = isDrain ? 'trample →' : '→'
    return (
      <div key={edge.id} style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <span style={{ color: '#888', fontSize: responsive.fontSize.small, minWidth: 56, textAlign: 'right' }}>
          {arrow}
        </span>
        {targetVisual(edge.targetId)}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <span style={{ color: 'white', fontSize: responsive.fontSize.small, maxWidth: 140, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {isPlayerTarget(edge.targetId) ? cardName(edge.targetId) : cardName(edge.targetId)}
          </span>
          {!isDrain && (
            <span style={{ color: atLethal ? '#4ade80' : '#f59e0b', fontSize: responsive.fontSize.small }}>
              lethal: {edge.lethal}{atLethal ? ' ✓' : ''}{edge.orderConstrained ? '' : ' (any order)'}
            </span>
          )}
          {isDrain && (
            <span style={{ color: '#60a5fa', fontSize: responsive.fontSize.small }}>overflow</span>
          )}
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginLeft: 'auto' }}>
          {editable
            ? stepBtn('-', () => adjust(edge, -1), amount > 0, '#dc2626')
            : null}
          <span style={{ color: 'white', fontSize: responsive.fontSize.large, fontWeight: 700, minWidth: 28, textAlign: 'center' }}>
            {amount}
          </span>
          {editable
            ? stepBtn('+', () => adjust(edge, +1), amount < edge.maximum && sourceTotal(edge.sourceId) < sourcePower(edge.sourceId), '#16a34a')
            : <span style={{ color: '#666', fontSize: responsive.fontSize.small }}>read-only</span>}
        </div>
      </div>
    )
  }

  return (
    <div
      style={{
        position: 'fixed', inset: 0, backgroundColor: 'rgba(0,0,0,0.92)',
        display: 'flex', flexDirection: 'column', alignItems: 'center',
        gap: responsive.isMobile ? 12 : 18, padding: responsive.containerPadding,
        overflowY: 'auto', pointerEvents: 'auto', zIndex: 1000,
      }}
    >
      <div style={{ textAlign: 'center', marginTop: 8 }}>
        <h2 style={{ color: 'white', margin: 0, fontSize: responsive.isMobile ? 18 : 24, fontWeight: 600 }}>
          Assign Combat Damage
        </h2>
        <p style={{ color: '#aaa', margin: '6px 0 0', fontSize: responsive.fontSize.normal }}>
          {decision.firstStrike ? 'First Strike Damage' : 'Combat Damage'}
        </p>
        {bandingActive && (
          <p style={{ color: '#a855f7', margin: '4px 0 0', fontSize: responsive.fontSize.small }}>
            Banding (CR 702.22): damage division is inverted for some edges.
          </p>
        )}
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 14, width: '100%', maxWidth: 720 }}>
        {sourceIds.map((sourceId) => (
          <div
            key={sourceId}
            style={{
              display: 'flex', gap: 14, alignItems: 'center', padding: 12,
              backgroundColor: 'rgba(255,255,255,0.04)', borderRadius: 10, border: '1px solid #2a2a3a',
            }}
          >
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6 }}>
              {targetVisual(sourceId)}
              <span style={{ color: 'white', fontSize: responsive.fontSize.small, fontWeight: 600, maxWidth: cardW + 30, textAlign: 'center' }}>
                {sourceLabel(sourceId)}
              </span>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10, flex: 1 }}>
              {edgesBySource(sourceId).map(edgeRow)}
            </div>
          </div>
        ))}
      </div>

      <div style={{ display: 'flex', gap: 14, marginBottom: 16 }}>
        {ownsAnyEditable && (
          <button
            onClick={handleReset}
            style={{
              padding: responsive.isMobile ? '10px 20px' : '14px 28px', fontSize: responsive.fontSize.normal,
              backgroundColor: '#4b5563', color: 'white', border: 'none', borderRadius: 8,
              cursor: 'pointer', fontWeight: 500,
            }}
          >
            Reset
          </button>
        )}
        <button
          onClick={handleConfirm}
          style={{
            padding: responsive.isMobile ? '10px 32px' : '14px 44px', fontSize: responsive.fontSize.large,
            backgroundColor: '#16a34a', color: 'white', border: 'none', borderRadius: 8,
            cursor: 'pointer', fontWeight: 600,
          }}
        >
          Confirm Damage
        </button>
      </div>
    </div>
  )
}
