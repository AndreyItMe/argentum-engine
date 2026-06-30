package com.wingedsheep.gameserver.replay

import com.wingedsheep.engine.core.ActionProcessor
import com.wingedsheep.engine.core.EngineServices
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.registry.PrintingRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.view.ClientStateTransformer
import com.wingedsheep.gameserver.protocol.ServerMessage
import com.wingedsheep.gameserver.session.SpectatorSeat
import com.wingedsheep.gameserver.session.SpectatorStateBuilder
import com.wingedsheep.sdk.model.EntityId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** A replay reconstructed back into the snapshot + delta stream the client replay viewer consumes. */
data class ReconstructedReplay(
    val initialSnapshot: ServerMessage.SpectatorStateUpdate,
    val deltas: List<SpectatorReplayDelta>,
) {
    val frameCount: Int get() = 1 + deltas.size
}

/**
 * Re-simulates a [CompactReplay] to regenerate exactly what was (or would have been) shown live.
 *
 * Because the engine is deterministic — same seed + same seat ids + same decks + same ordered
 * actions ⇒ byte-identical [GameState] sequence (entity ids included; the engine never mints a
 * UUID) — we can rebuild the initial state with [GameInitializer], fold the recorded actions
 * through [ActionProcessor], and run the *same* [SpectatorStateBuilder] / [SpectatorReplayDiffCalculator]
 * the live broadcast used. The result is the `{initialSnapshot, deltas}` shape the client's
 * `reconstructSnapshots()` already understands, and any single frame's full unmasked state for the
 * "share frame as scenario" path.
 *
 * Reconstruction is intentionally fault-tolerant: if an action ever fails to apply (e.g. a future
 * engine change makes an old recording diverge), we stop at the last good frame and log, rather
 * than losing the whole replay.
 */
@Component
class ReplayReconstructor(
    cardRegistry: CardRegistry,
    // Same registry the live game was created with, so re-stamped printing images match
    // byte-for-byte. Nullable to mirror GameInitializer / GameSession (tests pass null).
    printingRegistry: PrintingRegistry?,
) {
    private val logger = LoggerFactory.getLogger(ReplayReconstructor::class.java)

    private val services = EngineServices(cardRegistry, printingRegistry)
    private val actionProcessor = ActionProcessor(services)
    private val gameInitializer = GameInitializer(cardRegistry, printingRegistry)
    private val spectatorStateBuilder = SpectatorStateBuilder(cardRegistry, ClientStateTransformer(cardRegistry))

    /** Rebuild the full snapshot + delta stream for [replay]. */
    fun reconstruct(replay: CompactReplay): ReconstructedReplay {
        val setup = replay.setup
        val seats = setup.players.map { SpectatorSeat(EntityId(it.playerId), it.name) }

        var state = applyYields(initialState(replay), replay.yields, afterActionCount = 0)
        var previous = spectatorStateBuilder.buildState(state, seats, setup.seatRoster, replay.gameId)
        val initial = previous
        val deltas = ArrayList<SpectatorReplayDelta>(replay.actions.size)

        for ((index, action) in replay.actions.withIndex()) {
            val result = actionProcessor.process(state, rebind(action, state)).result
            if (result.error != null) {
                logger.warn(
                    "Replay {} diverged at action {} ({}): {} — truncating to {} frames",
                    replay.gameId, index, action::class.simpleName, result.error, 1 + deltas.size,
                )
                break
            }
            // Re-apply any yields set right after this action was originally applied, so the engine's
            // auto-answers reproduce on the next iteration exactly as they did live.
            state = applyYields(result.state, replay.yields, afterActionCount = index + 1)
            val snapshot = spectatorStateBuilder.buildState(state, seats, setup.seatRoster, replay.gameId)
            deltas.add(SpectatorReplayDiffCalculator.computeDelta(previous, snapshot))
            previous = snapshot
        }

        return ReconstructedReplay(initial, deltas)
    }

    /**
     * The full, unmasked [GameState] at [frame] (0 = initial state, N = after the Nth action).
     * Powers the "share frame as scenario" path. Returns null if the frame is out of range or the
     * replay diverges before reaching it.
     */
    fun reconstructStateAt(replay: CompactReplay, frame: Int): GameState? {
        if (frame < 0 || frame > replay.actions.size) return null
        var state = applyYields(initialState(replay), replay.yields, afterActionCount = 0)
        for (index in 0 until frame) {
            val action = replay.actions[index]
            val result = actionProcessor.process(state, rebind(action, state)).result
            if (result.error != null) {
                logger.warn(
                    "Replay {} diverged at action {} while seeking frame {}: {}",
                    replay.gameId, index, frame, result.error,
                )
                return null
            }
            state = applyYields(result.state, replay.yields, afterActionCount = index + 1)
        }
        return state
    }

    /**
     * Re-apply every recorded yield whose [ReplayYieldEntry.afterActionCount] equals [afterActionCount]
     * (i.e. it was originally set right after that many actions had been applied). Mirrors
     * [com.wingedsheep.gameserver.session.GameSession.setAbilityYield] and friends so the engine's
     * auto-answers reproduce identically. Almost always a no-op (most games carry no yields).
     */
    private fun applyYields(state: GameState, yields: List<ReplayYieldEntry>, afterActionCount: Int): GameState {
        if (yields.isEmpty()) return state
        var current = state
        for (entry in yields) {
            if (entry.afterActionCount != afterActionCount) continue
            val playerId = EntityId(entry.playerId)
            current = when (entry.op) {
                ReplayYieldOp.SET -> current.withYield(playerId, entry.identity!!, entry.kind!!)
                ReplayYieldOp.CLEAR_ABILITY -> current.withoutYield(playerId, entry.identity!!)
                ReplayYieldOp.CLEAR_ALL -> current.withoutYields(playerId)
            }
        }
        return current
    }

    /**
     * Re-bind a recorded action to the current reconstructed state. Decision ids are minted from a
     * UUID each run, so a recorded [SubmitDecision] carries the *original* run's id; we retarget it
     * at the id the freshly created pending decision actually has. The choice payload (targets,
     * cards, numbers — all by deterministic entity id) is untouched, so the outcome is identical.
     */
    private fun rebind(action: GameAction, state: GameState): GameAction {
        if (action !is SubmitDecision) return action
        val pendingId = state.pendingDecision?.id ?: return action
        if (pendingId == action.response.decisionId) return action
        return action.copy(response = action.response.withDecisionId(pendingId))
    }

    private fun initialState(replay: CompactReplay): GameState {
        val setup = replay.setup
        val config = GameConfig(
            players = setup.players.map {
                PlayerConfig(
                    name = it.name,
                    deck = it.deck,
                    startingLife = it.startingLife,
                    playerId = EntityId(it.playerId),
                    commanderCardName = it.commanderCardName,
                )
            },
            startingHandSize = setup.startingHandSize,
            skipMulligans = setup.skipMulligans,
            useHandSmoother = setup.useHandSmoother,
            handSmootherCandidates = setup.handSmootherCandidates,
            startingPlayerIndex = setup.startingPlayerIndex,
            format = setup.format,
            attackMode = setup.attackMode,
            teams = setup.teams,
            seed = setup.seed,
        )
        return gameInitializer.initializeGame(config).state
    }
}
