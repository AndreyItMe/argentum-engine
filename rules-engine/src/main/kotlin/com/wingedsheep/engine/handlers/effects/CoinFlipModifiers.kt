package com.wingedsheep.engine.handlers.effects

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.FlippedCoinsThisTurnComponent
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.WinCoinFlips

/**
 * Shared application path for coin-flip result replacement (CR 705.3, "An effect may state that a
 * coin flip has a certain result and/or that a certain player wins a coin flip … ignore the actual
 * results and use the indicated results instead"). Consulted from every coin-flip emission point —
 * `FlipCoinExecutor`, `FlipTwoCoinsExecutor`, and `FlipCoinsExecutor` — so any coin flip runs
 * through the same filter.
 *
 * Currently the only source is [WinCoinFlips] (Edgar, King of Figaro's Two-Headed Coin). "Heads" is
 * modeled as a won flip throughout the coin plumbing, so a forced win is a forced heads.
 */
object CoinFlipModifiers {

    /**
     * Whether the coin-flip event [playerId] is about to make should be forced to a win — every
     * coin in it comes up heads (CR 705.3). True when [playerId] controls a permanent with a
     * [WinCoinFlips] static ability that applies to this flip: an "all coin flips" ability always
     * applies, while a `firstFlipEachTurn` ability (Edgar) applies only on the player's first
     * coin-flip event of the turn — detected via [FlippedCoinsThisTurnComponent], which
     * [markFlipped] sets after every flip regardless of any replacement.
     *
     * Uses projected controllers so a stolen coin-flip source routes the "you" to its current
     * controller.
     */
    fun shouldForceWin(state: GameState, cardRegistry: CardRegistry, playerId: EntityId): Boolean {
        val alreadyFlippedThisTurn = state.getEntity(playerId)?.has<FlippedCoinsThisTurnComponent>() == true
        return state.projectedState.getBattlefieldControlledBy(playerId).any { permanentId ->
            val card = state.getEntity(permanentId)?.get<CardComponent>() ?: return@any false
            val cardDef = cardRegistry.getCard(card.cardDefinitionId) ?: return@any false
            cardDef.script.staticAbilities.any { ability ->
                ability is WinCoinFlips && (!ability.firstFlipEachTurn || !alreadyFlippedThisTurn)
            }
        }
    }

    /**
     * Record that [playerId] has flipped one or more coins this turn (idempotent). Set on every
     * flip — even ones no replacement affected — so a `firstFlipEachTurn` ability can tell that a
     * later flip that turn is no longer the first. Cleared at end of turn by CleanupPhaseManager.
     */
    fun markFlipped(state: GameState, playerId: EntityId): GameState =
        state.updateEntity(playerId) { it.with(FlippedCoinsThisTurnComponent) }
}
