package com.wingedsheep.gameserver.persistence.dto

import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.view.ClientEvent
import com.wingedsheep.gameserver.replay.ReplaySetup
import kotlinx.serialization.Serializable

/**
 * Persistent representation of a GameSession for Redis storage.
 * Excludes transient WebSocket references and reconstructable objects.
 */
@Serializable
data class PersistentGameSession(
    val sessionId: String,
    val gameState: GameState?,
    val deckLists: Map<String, List<String>>,  // playerId.value -> card names
    val lastProcessedMessageId: Map<String, String>,  // playerId.value -> messageId
    val gameLogs: Map<String, List<ClientEvent>>,  // playerId.value -> events
    val playerInfos: List<PersistentPlayerInfo>,
    val lobbyId: String?,
    val sideboards: Map<String, List<String>> = emptyMap(),  // playerId.value -> sideboard card names
    // Compact-replay recording, so a game interrupted by a server restart stays replayable. Null
    // setup = a game started before this field existed, or an injected dev scenario (not replayable).
    val replaySetup: ReplaySetup? = null,
    val recordedActions: List<GameAction> = emptyList(),
    val recordedYields: List<com.wingedsheep.gameserver.replay.ReplayYieldEntry> = emptyList(),
    val replayStartedAt: String? = null,  // ISO-8601 instant
)

/**
 * Persistent player info - contains only the data needed to restore a player's session.
 */
@Serializable
data class PersistentPlayerInfo(
    val playerId: String,
    val playerName: String,
    val token: String,
    val isAi: Boolean = false,
    val aiModelOverride: String? = null
)
