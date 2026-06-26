package com.wingedsheep.engine.handlers.effects.permanent.attachments

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.core.PermanentUnattachedEvent
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.battlefield.AttachmentsComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.scripting.effects.UnattachEquipmentEffect
import kotlin.reflect.KClass

/**
 * Executor for [UnattachEquipmentEffect].
 *
 * Unattaches an Aura/Equipment from its host without moving it to another zone (CR 701.3d): clears
 * the attachment's [AttachedToComponent] and drops it from the host's [AttachmentsComponent]. A
 * no-op when the target isn't currently attached, emitting no event. The inverse of the attach
 * executors — used for "unattach it" riders (Stolen Uniform).
 */
class UnattachEquipmentExecutor : EffectExecutor<UnattachEquipmentEffect> {

    override val effectType: KClass<UnattachEquipmentEffect> = UnattachEquipmentEffect::class

    override fun execute(
        state: GameState,
        effect: UnattachEquipmentEffect,
        context: EffectContext
    ): EffectResult {
        val attachmentId = context.resolveTarget(effect.target, state)
            ?: return EffectResult.success(state) // target gone / illegal — nothing to unattach

        val currentAttachment = state.getEntity(attachmentId)?.get<AttachedToComponent>()
            ?: return EffectResult.success(state) // not attached to anything — no-op

        val hostId = currentAttachment.targetId
        var newState = state

        // Drop the attachment id from the host's attachment list.
        newState = newState.updateEntity(hostId) { container ->
            val attachments = container.get<AttachmentsComponent>()
            if (attachments != null) {
                val updatedIds = attachments.attachedIds.filter { it != attachmentId }
                if (updatedIds.isEmpty()) container.without<AttachmentsComponent>()
                else container.with(AttachmentsComponent(updatedIds))
            } else {
                container
            }
        }

        // Clear the attachment's own AttachedToComponent.
        newState = newState.updateEntity(attachmentId) { container ->
            container.without<AttachedToComponent>()
        }

        val attachmentName = state.getEntity(attachmentId)?.get<CardComponent>()?.name ?: ""
        return EffectResult.success(
            newState,
            listOf(
                PermanentUnattachedEvent(
                    attachmentId = attachmentId,
                    attachmentName = attachmentName,
                    attachedToId = hostId
                )
            )
        )
    }
}
