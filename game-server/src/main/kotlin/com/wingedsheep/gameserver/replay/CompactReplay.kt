package com.wingedsheep.gameserver.replay

import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.state.YieldKind
import com.wingedsheep.gameserver.protocol.ServerMessage
import com.wingedsheep.sdk.core.AttackMode
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.AbilityIdentity
import kotlinx.serialization.Serializable

/**
 * The compact, durable form of a recorded game.
 *
 * The engine is a pure, deterministic function `(GameState, GameAction) -> GameState` — and every
 * entity id it mints is drawn from a state-threaded counter (`e0`, `e1`, …), never a UUID — so a
 * whole game is reproducible from nothing more than the [setup] (which seeds [Format], decks, the
 * RNG seed, and the seat ids) plus the ordered list of [actions] that were applied. This is the
 * "record the inputs, re-simulate" approach used by deterministic game engines everywhere: we store
 * kilobytes of inputs instead of megabytes of per-frame snapshots, and re-derive the full
 * spectator stream on demand via [ReplayReconstructor].
 *
 * Replaces the old snapshot-plus-deltas-plus-full-states record, which kept an entire masked
 * spectator snapshot, a per-frame delta, AND a complete unmasked [com.wingedsheep.engine.state.GameState]
 * for every frame in memory.
 */
@Serializable
data class CompactReplay(
    val version: Int = CURRENT_VERSION,
    val gameId: String,
    /** Every seat in turn order. 2-player is the degenerate case (two entries). */
    val players: List<ReplayPlayerInfo>,
    /** ISO-8601 instant strings, matching what the REST summaries already emit. */
    val startedAt: String,
    val endedAt: String,
    val winnerName: String?,
    val tournamentName: String? = null,
    val tournamentRound: Int? = null,
    /** Everything needed to rebuild the exact initial [com.wingedsheep.engine.state.GameState]. */
    val setup: ReplaySetup,
    /** The ordered input stream applied to the game, replayed verbatim to reconstruct it. */
    val actions: List<GameAction>,
    /**
     * Persistent-yield mutations (MTGO right-click "always yes/no" / "yield" preferences) applied to
     * the game out-of-band of the [actions] stream. They live on [com.wingedsheep.engine.state.GameState]
     * and are *consumed* by the pure engine during resolution (auto-answering optional triggers), so a
     * game where a player set such a yield re-simulates differently unless we re-apply the yields at the
     * exact point they were set — hence [ReplayYieldEntry.afterActionCount]. Empty for games without any
     * yields (the overwhelming majority), and for replays recorded before this field existed.
     */
    val yields: List<ReplayYieldEntry> = emptyList(),
) {
    /** Number of reconstructable frames: the initial state plus one per applied action. */
    val frameCount: Int get() = 1 + actions.size

    companion object {
        /** Bump when a setup/action shape change would break reconstruction of older records. */
        const val CURRENT_VERSION = 1
    }
}

/** Which yield mutation a [ReplayYieldEntry] records. */
@Serializable
enum class ReplayYieldOp { SET, CLEAR_ABILITY, CLEAR_ALL }

/**
 * A single persistent-yield mutation captured in turn order against the [actions] stream.
 * [afterActionCount] is the number of recorded actions that had been applied when the yield was set,
 * so [ReplayReconstructor] re-applies it at the same point and the engine's auto-answers reproduce
 * exactly. [identity]/[kind] are populated per [op] (both for SET, [identity] only for CLEAR_ABILITY,
 * neither for CLEAR_ALL).
 */
@Serializable
data class ReplayYieldEntry(
    val afterActionCount: Int,
    val playerId: String,
    val op: ReplayYieldOp,
    val identity: AbilityIdentity? = null,
    val kind: YieldKind? = null,
)

/** A single seat in a recorded replay, in turn order. */
@Serializable
data class ReplayPlayerInfo(
    val playerId: String,
    val name: String,
)

/**
 * The reproducible inputs to [com.wingedsheep.engine.core.GameInitializer.initializeGame] for a
 * recorded game, mirroring [com.wingedsheep.engine.core.GameConfig] field-for-field (minus the
 * non-serializable [com.wingedsheep.engine.core.PlayerConfig], flattened into [ReplayPlayerSetup]).
 * [seed] is the seed the engine actually used (captured from
 * [com.wingedsheep.engine.core.InitializationResult.seed]), so the shuffle, turn order, and every
 * "at random" choice replay identically.
 */
@Serializable
data class ReplaySetup(
    val seed: Long,
    val format: Format,
    val attackMode: AttackMode,
    val startingHandSize: Int = 7,
    val skipMulligans: Boolean = false,
    val useHandSmoother: Boolean = false,
    val handSmootherCandidates: Int = 3,
    val startingPlayerIndex: Int? = null,
    val teams: List<List<Int>>? = null,
    val players: List<ReplayPlayerSetup>,
    /**
     * The spectator seat roster captured at game start (turn order, team membership). Echoed back
     * into each reconstructed snapshot so the replay viewer renders the same seating it would live.
     */
    val seatRoster: List<ServerMessage.PlayerSeatInfo>,
)

/** Flattened, serializable form of [com.wingedsheep.engine.core.PlayerConfig]. */
@Serializable
data class ReplayPlayerSetup(
    /** The engine [com.wingedsheep.sdk.model.EntityId] value this seat was assigned — replayed verbatim. */
    val playerId: String,
    val name: String,
    val deck: Deck,
    val startingLife: Int = 20,
    val commanderCardName: String? = null,
)
