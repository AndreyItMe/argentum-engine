package com.wingedsheep.engine.handlers.effects
import com.wingedsheep.engine.state.components.battlefield.chosenCreatureRef

import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.stack.SpellOnStackComponent
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.values.EntityReference

/**
 * Utility functions for resolving effect targets from symbolic references to concrete entity IDs.
 *
 * Targets in MTG are late-bound: effects reference targets symbolically
 * (e.g., ContextTarget(0) = "the first target chosen at cast time") and these
 * are resolved at execution time against the current game state.
 */
object TargetResolutionUtils {

    /**
     * Resolve a target from the effect target definition and context.
     */
    fun resolveTarget(effectTarget: EffectTarget, context: EffectContext): EntityId? {
        return when (effectTarget) {
            is EffectTarget.Self -> context.pipeline.iterationTarget ?: context.sourceId
            is EffectTarget.Controller -> context.controllerId
            is EffectTarget.ContextTarget -> context.positionalTarget(effectTarget.index)?.toEntityId()
            is EffectTarget.BoundVariable -> context.pipeline.namedTargets[effectTarget.name]?.toEntityId()
            is EffectTarget.SpecificEntity -> effectTarget.entityId
            is EffectTarget.TriggeringEntity -> context.triggeringEntityId
            is EffectTarget.PipelineTarget ->
                context.pipeline.storedCollections[effectTarget.collectionName]?.getOrNull(effectTarget.index)
            else -> null
        }
    }

    /**
     * Resolve a target with access to game state (for targets like EnchantedCreature
     * that need to look up attachment relationships).
     */
    fun resolveTarget(effectTarget: EffectTarget, context: EffectContext, state: GameState): EntityId? {
        if (effectTarget is EffectTarget.EnchantedCreature ||
            effectTarget is EffectTarget.EquippedCreature ||
            effectTarget is EffectTarget.EnchantedPermanent
        ) {
            val sourceId = context.sourceId ?: return null
            return state.getEntity(sourceId)?.get<AttachedToComponent>()?.targetId
        }
        if (effectTarget is EffectTarget.ChosenCreature) {
            val sourceId = context.sourceId ?: return null
            return state.getEntity(sourceId)?.chosenCreatureRef()
        }
        if (effectTarget is EffectTarget.TargetController) {
            val targetEntity = context.targets.firstOrNull()?.toEntityId() ?: return null
            return state.getEntity(targetEntity)?.get<ControllerComponent>()?.playerId
                ?: state.getEntity(targetEntity)?.get<CardComponent>()?.ownerId
        }
        if (effectTarget is EffectTarget.ControllerOfTriggeringEntity) {
            val triggerId = context.triggeringEntityId ?: return null
            val entity = state.getEntity(triggerId) ?: return null
            return entity.get<ControllerComponent>()?.playerId
                ?: entity.get<CardComponent>()?.ownerId
        }
        if (effectTarget is EffectTarget.ControllerOfPipelineTarget) {
            val targetEntityId = context.pipeline.storedCollections[effectTarget.collectionName]?.getOrNull(effectTarget.index) ?: return null
            val entity = state.getEntity(targetEntityId) ?: return null
            return entity.get<ControllerComponent>()?.playerId
                ?: entity.get<CardComponent>()?.ownerId
        }
        if (effectTarget is EffectTarget.PipelineTarget) {
            return context.pipeline.storedCollections[effectTarget.collectionName]?.getOrNull(effectTarget.index)
        }
        return resolveTarget(effectTarget, context)
    }

    /**
     * Resolve a player target from the effect target definition and context.
     */
    fun resolvePlayerTarget(effectTarget: EffectTarget, context: EffectContext): EntityId? {
        return when (effectTarget) {
            is EffectTarget.Controller -> context.controllerId
            is EffectTarget.ContextTarget -> context.positionalTarget(effectTarget.index)?.toEntityId()
            is EffectTarget.BoundVariable -> context.pipeline.namedTargets[effectTarget.name]?.toEntityId()
            is EffectTarget.PipelineTarget ->
                context.pipeline.storedCollections[effectTarget.collectionName]?.getOrNull(effectTarget.index)
            is EffectTarget.PlayerRef -> when (effectTarget.player) {
                Player.You -> context.controllerId
                Player.Opponent, Player.TargetOpponent, Player.EachOpponent -> context.opponentId
                Player.TargetPlayer, Player.Any -> context.targets.firstOrNull()?.toEntityId()
                Player.TriggeringPlayer -> context.triggeringPlayerId ?: context.triggeringEntityId
                else -> null
            }
            else -> null
        }
    }

    /**
     * Resolve the controller of an entity. For a spell on the stack the controller is the player
     * who cast it ([SpellOnStackComponent.casterId]) — the stack object's [ControllerComponent]
     * still reflects the card's owner, which differs from the controller when a player casts a
     * card they don't own (e.g. casting a spell from an opponent's graveyard). Falls back to the
     * [ControllerComponent] (battlefield permanents) and finally the owner.
     */
    private fun controllerOf(state: GameState, entityId: EntityId): EntityId? {
        val entity = state.getEntity(entityId) ?: return null
        return entity.get<SpellOnStackComponent>()?.casterId
            ?: entity.get<ControllerComponent>()?.playerId
            ?: entity.get<CardComponent>()?.ownerId
    }

    /**
     * Resolve a player target with access to game state (for relational references like OwnerOf/ControllerOf).
     */
    fun resolvePlayerTarget(effectTarget: EffectTarget, context: EffectContext, state: GameState): EntityId? {
        // Try stateless resolution first
        resolvePlayerTarget(effectTarget, context)?.let { return it }

        // Handle TargetController: resolve the first target, then look up its controller
        if (effectTarget is EffectTarget.TargetController) {
            val targetEntity = context.targets.firstOrNull()?.toEntityId() ?: return null
            return controllerOf(state, targetEntity)
        }

        // Handle ControllerOfPipelineTarget: look up controller of the pipeline-stored entity
        if (effectTarget is EffectTarget.ControllerOfPipelineTarget) {
            val targetEntityId = context.pipeline.storedCollections[effectTarget.collectionName]?.getOrNull(effectTarget.index) ?: return null
            return controllerOf(state, targetEntityId)
        }

        // Handle state-dependent relational references
        return when (effectTarget) {
            is EffectTarget.PlayerRef -> when (val player = effectTarget.player) {
                is Player.OwnerOf -> {
                    val targetEntity = context.targets.firstOrNull()?.toEntityId() ?: return null
                    state.getEntity(targetEntity)?.get<CardComponent>()?.ownerId
                }
                is Player.ControllerOf -> {
                    val targetEntity = context.targets.firstOrNull()?.toEntityId() ?: return null
                    controllerOf(state, targetEntity)
                }
                else -> null
            }
            else -> null
        }
    }

    /**
     * Resolve a player target to a list of player IDs (for multi-player effects like "each player").
     */
    fun resolvePlayerTargets(effectTarget: EffectTarget, state: GameState, context: EffectContext): List<EntityId> {
        return when (effectTarget) {
            is EffectTarget.Controller -> listOf(context.controllerId)
            is EffectTarget.BoundVariable -> context.pipeline.namedTargets[effectTarget.name]?.toEntityId()?.let { listOf(it) } ?: emptyList()
            is EffectTarget.PipelineTarget -> {
                context.pipeline.storedCollections[effectTarget.collectionName]?.getOrNull(effectTarget.index)
                    ?.let { listOf(it) } ?: emptyList()
            }
            is EffectTarget.ControllerOfPipelineTarget -> {
                val targetEntityId = context.pipeline.storedCollections[effectTarget.collectionName]?.getOrNull(effectTarget.index) ?: return emptyList()
                val entity = state.getEntity(targetEntityId) ?: return emptyList()
                val controllerId = entity.get<ControllerComponent>()?.playerId
                    ?: entity.get<CardComponent>()?.ownerId
                controllerId?.let { listOf(it) } ?: emptyList()
            }
            is EffectTarget.PlayerRef -> when (effectTarget.player) {
                Player.Each -> state.turnOrder
                Player.EachOpponent -> state.turnOrder.filter { it != context.controllerId }
                Player.You -> listOf(context.controllerId)
                Player.Opponent, Player.TargetOpponent -> state.turnOrder.filter { it != context.controllerId }
                Player.TargetPlayer, Player.Any -> {
                    context.targets.firstOrNull()?.toEntityId()?.let { listOf(it) } ?: emptyList()
                }
                Player.TriggeringPlayer -> {
                    (context.triggeringPlayerId ?: context.triggeringEntityId)?.let { listOf(it) } ?: emptyList()
                }
                is Player.OwnerOf, is Player.ControllerOf -> {
                    resolvePlayerTarget(effectTarget, context, state)?.let { listOf(it) } ?: emptyList()
                }
                else -> emptyList()
            }
            // Use the state-aware resolver so state-dependent targets (e.g. TargetController,
            // which reads the target spell/permanent's controller) resolve here too. It tries
            // the stateless path first, so this stays a superset of the previous behavior.
            else -> resolvePlayerTarget(effectTarget, context, state)?.let { listOf(it) } ?: emptyList()
        }
    }

    /**
     * Resolve an [EntityReference] (AST-level "which entity" reference used by effects,
     * filters, and dynamic amounts) to a concrete entity id against the current [context]
     * and [state].
     *
     * Counterpart to [resolveTarget], which resolves [EffectTarget]s. [EntityReference] is the
     * value-AST reference (source / chosen target / sacrificed / tapped-as-cost / triggering /
     * affected / iteration / cost-storage / amassed army / enchanted creature). A `Target`
     * resolves to whatever the chosen target points at — permanent, card-in-zone, spell, or
     * player — via [toEntityId]; `EnchantedCreature` reads the source's attachment from [state].
     */
    fun resolveEntityReference(ref: EntityReference, context: EffectContext, state: GameState): EntityId? =
        when (ref) {
            is EntityReference.Source -> context.sourceId
            is EntityReference.EnchantedCreature ->
                context.sourceId?.let { state.getEntity(it)?.get<AttachedToComponent>()?.targetId }
            is EntityReference.Target -> context.positionalTarget(ref.index)?.toEntityId()
            is EntityReference.Sacrificed -> context.sacrificedPermanents.getOrNull(ref.index)?.entityId
            is EntityReference.TappedAsCost -> context.tappedPermanents.getOrNull(ref.index)
            is EntityReference.Triggering -> context.triggeringEntityId
            is EntityReference.AffectedEntity -> context.affectedEntityId
            is EntityReference.IterationEntity -> context.pipeline.iterationTarget
            is EntityReference.FromCostStorage ->
                context.pipeline.storedCollections[ref.collectionName]?.getOrNull(ref.index)
            is EntityReference.AmassedArmy ->
                context.pipeline.storedCollections[EntityReference.AmassedArmy.STORAGE_KEY]?.firstOrNull()
        }

    /**
     * Convert a ChosenTarget to an EntityId.
     */
    fun ChosenTarget.toEntityId(): EntityId = when (this) {
        is ChosenTarget.Player -> playerId
        is ChosenTarget.Permanent -> entityId
        is ChosenTarget.Card -> cardId
        is ChosenTarget.Spell -> spellEntityId
    }
}
