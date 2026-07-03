package com.wingedsheep.engine.core

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.ConvertEmptyingManaToRed

/**
 * Players who control a permanent with the [ConvertEmptyingManaToRed] static ability
 * (Ozai, the Phoenix King: "If you would lose unspent mana, that mana becomes red instead").
 *
 * The static fires at *every* mana-loss point, not just one: both the end-of-turn cleanup emptying
 * ([CleanupPhaseManager.cleanupEndOfTurn]) and the end-of-combat firebending-mana discard
 * ([com.wingedsheep.engine.mechanics.combat.CombatManager.endCombat]) consult this set, so
 * firebending mana that would otherwise be lost as combat ends becomes red and survives the rest of
 * the turn. Controller is read from projected state so a control-changed Ozai converts for its new
 * controller.
 */
fun playersConvertingEmptyingManaToRed(state: GameState, cardRegistry: CardRegistry): Set<EntityId> {
    val projected = state.projectedState
    val result = mutableSetOf<EntityId>()
    for (entityId in state.getBattlefield()) {
        val card = state.getEntity(entityId)?.get<CardComponent>() ?: continue
        val cardDef = cardRegistry.getCard(card.cardDefinitionId) ?: continue
        if (cardDef.script.staticAbilities.any { it is ConvertEmptyingManaToRed }) {
            projected.getController(entityId)?.let { result.add(it) }
        }
    }
    return result
}
