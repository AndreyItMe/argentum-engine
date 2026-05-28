package com.wingedsheep.engine.state.components.identity

import com.wingedsheep.engine.state.Component
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable

/**
 * Marks a creature as a player's **Ring-bearer** (CR 701.52a–e).
 *
 * Being a Ring-bearer is a designation, not a copiable value (701.52b). A creature "is [ownerId]'s
 * Ring-bearer" only while it has this component **and** is on the battlefield under [ownerId]'s
 * control (701.52e) — so losing control of the creature ends the designation's effects without the
 * component having to be eagerly removed. The designation moves to a new creature each time that
 * player is tempted (handled by the tempt executor), and at most one creature carries this component
 * per owner.
 *
 * @property ownerId The player who designated this creature (whose Ring emblem grants its abilities).
 */
@Serializable
data class RingBearerComponent(
    val ownerId: EntityId
) : Component
