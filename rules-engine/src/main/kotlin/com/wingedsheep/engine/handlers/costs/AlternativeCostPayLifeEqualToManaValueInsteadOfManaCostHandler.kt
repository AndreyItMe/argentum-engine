package com.wingedsheep.engine.handlers.costs

import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.core.LifeChangeReason
import com.wingedsheep.engine.core.LifeChangedEvent
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCost

object AlternativeCostPayLifeEqualToManaValueInsteadOfManaCostHandler {

    fun canPay(state: GameState, playerId: EntityId, cost: AdditionalCost.PayLife): Boolean {
        val life = state.getEntity(playerId)?.get<LifeTotalComponent>()?.life ?: 0
        return life >= cost.amount
    }

    fun pay(
        state: GameState,
        playerId: EntityId,
        cost: AdditionalCost.PayLife
    ): Pair<GameState, List<GameEvent>> {
        val oldLife = state.getEntity(playerId)?.get<LifeTotalComponent>()?.life ?: 0
        val newLife = oldLife - cost.amount
        val newState = state.updateEntity(playerId) { container ->
            container.with(LifeTotalComponent(newLife))
        }
        return newState to listOf(LifeChangedEvent(playerId, oldLife, newLife, LifeChangeReason.PAYMENT))
    }
}
