package com.wingedsheep.engine.mechanics.sba.permanent

import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.handlers.effects.ZoneMovementUtils.cleanupReverseAttachmentLink
import com.wingedsheep.engine.mechanics.sba.SbaOrder
import com.wingedsheep.engine.mechanics.sba.SbaZoneMovementHelper
import com.wingedsheep.engine.mechanics.sba.StateBasedActionCheck
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.identity.CardComponent

/**
 * 704.5m - An Aura attached to an illegal object/player or not attached goes to graveyard.
 * 704.5n - An Equipment or Fortification attached to an illegal permanent becomes unattached
 *          but remains on the battlefield. This drives two Equipment cases below, both asked
 *          of the projected state (so layer-4 type-changing effects are seen):
 *            - The host stops being a creature (an Equipment can only equip a creature, CR
 *              301.5). E.g. the equipped creature is turned into a land, or an animated
 *              artifact's "until end of turn" animation wears off while still equipped.
 *            - The Equipment itself becomes a creature, so it can't legally equip another
 *              creature unless it has reconfigure (CR 301.5c). E.g. Atomic Microsizer turned
 *              into a 0/0 Robot artifact creature by Tezzeret, Cruel Captain's emblem.
 * 704.5p - A battle or creature attached to an object or player becomes unattached but
 *          remains on the battlefield.
 */
class UnattachedAurasCheck : StateBasedActionCheck {
    override val name = "704.5m/n/p Unattached Auras"
    override val order = SbaOrder.UNATTACHED_AURAS

    override fun check(state: GameState): ExecutionResult {
        var newState = state
        val events = mutableListOf<com.wingedsheep.engine.core.GameEvent>()
        val projected = state.projectedState

        for (entityId in state.getBattlefield().toList()) {
            val container = state.getEntity(entityId) ?: continue
            val cardComponent = container.get<CardComponent>() ?: continue

            val isAura = cardComponent.typeLine.isAura
            val isEquipment = cardComponent.typeLine.isEquipment

            if (!isAura && !isEquipment) continue

            val attachedTo = container.get<AttachedToComponent>()
            if (attachedTo == null) {
                if (isAura) {
                    // Aura not attached to anything - goes to graveyard
                    val result = SbaZoneMovementHelper.putPermanentInGraveyard(
                        newState, entityId, cardComponent
                    )
                    newState = result.newState
                    events.addAll(result.events)
                }
                // Equipment not attached to anything is fine - stays on battlefield
            } else if (isAura && attachedTo.targetId in state.turnOrder) {
                // 704.5m — an "enchant player" Aura (Grievous Wound) is attached to a player, not
                // a battlefield permanent. It stays as long as that player is still in the game;
                // once the player leaves, PlayerLeavesGameProcessor removes them from turnOrder and
                // the next check sends the now-unattached Aura to the graveyard.
                continue
            } else {
                // Check if attached target still exists on battlefield
                if (attachedTo.targetId !in state.getBattlefield()) {
                    if (isAura) {
                        // Aura's target gone - goes to graveyard
                        val result = SbaZoneMovementHelper.putPermanentInGraveyard(
                            newState, entityId, cardComponent,
                            lastKnownAttachedTo = attachedTo.targetId
                        )
                        newState = result.newState
                        events.addAll(result.events)
                    } else {
                        // Equipment's target gone - just detach, stays on battlefield
                        newState = cleanupReverseAttachmentLink(newState, entityId)
                        newState = newState.updateEntity(entityId) { c ->
                            c.without<AttachedToComponent>()
                        }
                    }
                } else if (
                    isEquipment && (
                        // CR 704.5n: the host is no longer a legal permanent for an Equipment.
                        // An Equipment can only be attached to a creature, so once the host
                        // stops being a creature (turned into a land, animation wore off, etc.)
                        // the attachment is illegal and the Equipment unattaches.
                        !projected.isCreature(attachedTo.targetId) ||
                        // CR 301.5c / 704.5n: the Equipment itself became a creature, so it
                        // can't equip a creature unless it has reconfigure.
                        (projected.isCreature(entityId) &&
                            !projected.hasKeyword(entityId, "RECONFIGURE"))
                    )
                ) {
                    // Illegal attachment: the Equipment unattaches but stays on the battlefield.
                    newState = cleanupReverseAttachmentLink(newState, entityId)
                    newState = newState.updateEntity(entityId) { c ->
                        c.without<AttachedToComponent>()
                    }
                }
            }
        }

        return ExecutionResult.success(newState, events)
    }
}
