/**
 * Quick Game Lobby slice — staging-area state for the new quick-game flow.
 *
 * Holds the latest [QuickGameLobbyStateMessage] received from the server and exposes the four
 * actions the UI uses to drive the lobby (create, join, leave, submit deck, ready toggle).
 *
 * The slice deliberately mirrors the server's snapshot rather than building its own derived
 * model — every state change comes from the server as a fresh `quickGameLobbyState` message,
 * so we just store and re-render.
 */
import type { QuickGameLobbyStateMessage } from '@/types'
import {
  createCreateQuickGameLobbyMessage,
  createJoinQuickGameLobbyMessage,
  createLeaveQuickGameLobbyMessage,
  createSubmitQuickGameLobbyDeckMessage,
  createSetQuickGameLobbyReadyMessage,
  createSetQuickGameLobbySetCodeMessage,
  createSetQuickGameLobbyPublicMessage,
} from '@/types'
import type { SliceCreator } from './types'
import { getWebSocket } from './shared'

export interface QuickGameLobbySliceState {
  quickGameLobbyState: QuickGameLobbyStateMessage | null
}

export interface QuickGameLobbySliceActions {
  createQuickGameLobby: (vsAi?: boolean, setCode?: string, isPublic?: boolean) => void
  joinQuickGameLobby: (lobbyId: string) => void
  leaveQuickGameLobby: () => void
  submitQuickGameLobbyDeck: (deckList: Record<string, number>) => void
  setQuickGameLobbyReady: (ready: boolean) => void
  setQuickGameLobbySetCode: (setCode: string | null) => void
  setQuickGameLobbyPublic: (isPublic: boolean) => void
}

export type QuickGameLobbySlice = QuickGameLobbySliceState & QuickGameLobbySliceActions

export const createQuickGameLobbySlice: SliceCreator<QuickGameLobbySlice> = (set) => ({
  quickGameLobbyState: null,

  createQuickGameLobby: (vsAi, setCode, isPublic) => {
    getWebSocket()?.send(createCreateQuickGameLobbyMessage(vsAi, setCode, isPublic))
  },

  joinQuickGameLobby: (lobbyId) => {
    getWebSocket()?.send(createJoinQuickGameLobbyMessage(lobbyId))
  },

  leaveQuickGameLobby: () => {
    getWebSocket()?.send(createLeaveQuickGameLobbyMessage())
    set({ quickGameLobbyState: null })
  },

  submitQuickGameLobbyDeck: (deckList) => {
    getWebSocket()?.send(createSubmitQuickGameLobbyDeckMessage(deckList))
  },

  setQuickGameLobbyReady: (ready) => {
    getWebSocket()?.send(createSetQuickGameLobbyReadyMessage(ready))
  },

  setQuickGameLobbySetCode: (setCode) => {
    getWebSocket()?.send(createSetQuickGameLobbySetCodeMessage(setCode))
  },

  setQuickGameLobbyPublic: (isPublic) => {
    getWebSocket()?.send(createSetQuickGameLobbyPublicMessage(isPublic))
  },
})
