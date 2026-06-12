package com.wingedsheep.engine.mechanics.combat

import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.combat.AttackingComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.sdk.model.EntityId

/**
 * Helpers for the *defending* side of multiplayer combat (CR 802.2).
 *
 * In a Free-for-All game the attacking player declares each creature against a specific
 * player or planeswalker, so a single combat can have several defending players at once.
 * "The defending player" is therefore not a single fixed seat — it is derived per attacking
 * creature from where that creature is attacking (CR 802.2a). These helpers answer "who is
 * defending in this combat" so block declaration can be offered to each of them, in turn
 * order starting from the active player (APNAP, CR 101.4).
 */
object CombatDefenders {

    /** The player defending against an attack aimed at [defenderId] (a player attacks as
     *  themselves; a planeswalker/battle defends on behalf of its controller). */
    fun defendingPlayerOf(state: GameState, defenderId: EntityId): EntityId =
        if (defenderId in state.turnOrder) {
            defenderId
        } else {
            state.getEntity(defenderId)?.get<ControllerComponent>()?.playerId ?: defenderId
        }

    /** Every distinct player who has at least one creature attacking them or their
     *  planeswalkers in the current combat. */
    fun defendingPlayers(state: GameState): Set<EntityId> =
        state.getBattlefield()
            .mapNotNull { state.getEntity(it)?.get<AttackingComponent>()?.defenderId }
            .map { defendingPlayerOf(state, it) }
            .toSet()

    /** True if [playerId] is a defending player in the current combat. */
    fun isDefendingPlayer(state: GameState, playerId: EntityId): Boolean =
        defendingPlayers(state).contains(playerId)

    /**
     * The defending players ordered for sequential block declaration: turn order starting
     * from the active player (CR 101.4 APNAP). The active player is never a defender, so in
     * practice this is the defenders in turn order after the active player, wrapping around.
     */
    fun defendingPlayersInApnapOrder(state: GameState): List<EntityId> {
        val defenders = defendingPlayers(state)
        if (defenders.isEmpty()) return emptyList()
        val order = state.turnOrder
        if (order.isEmpty()) return defenders.toList()
        val startIdx = state.activePlayerId?.let { order.indexOf(it) }?.coerceAtLeast(0) ?: 0
        return (order.indices)
            .map { order[(startIdx + it) % order.size] }
            .filter { it in defenders }
    }
}
