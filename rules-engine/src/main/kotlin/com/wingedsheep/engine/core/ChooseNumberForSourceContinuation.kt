package com.wingedsheep.engine.core

import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.ChoiceSlot
import kotlinx.serialization.Serializable

/**
 * Resume after a player picks the number for a
 * [com.wingedsheep.sdk.scripting.effects.ChooseNumberForSourceEffect]. The resumer writes the
 * chosen value as a [com.wingedsheep.engine.state.components.battlefield.ChoiceValue.NumberChoice]
 * into the source permanent's
 * [com.wingedsheep.engine.state.components.battlefield.CastChoicesComponent] under [slot],
 * replacing any prior value, so a characteristic-defining ability reads the latest choice.
 *
 * @property sourceId The permanent whose cast-choices bag receives the number.
 * @property controllerId The player who made the choice.
 * @property slot Which durable cast-choices slot to write.
 */
@Serializable
data class ChooseNumberForSourceContinuation(
    override val decisionId: String,
    val sourceId: EntityId,
    val controllerId: EntityId,
    val slot: ChoiceSlot
) : ContinuationFrame

/**
 * Resume after a player picks the recipient for a
 * [com.wingedsheep.sdk.scripting.effects.ChooseOpponentForSourceEffect]. The resumer writes the
 * chosen opponent as a [com.wingedsheep.engine.state.components.battlefield.ChoiceValue.EntityChoice]
 * into the source entity's
 * [com.wingedsheep.engine.state.components.battlefield.CastChoicesComponent] under
 * [com.wingedsheep.sdk.scripting.ChoiceSlot.OPPONENT], where
 * [com.wingedsheep.sdk.scripting.references.Player.ChosenOpponent] reads it back.
 *
 * @property sourceId The spell or permanent whose cast-choices bag receives the opponent.
 * @property controllerId The player who made the choice.
 * @property opponentIds The option list shown to the player, positionally aligned with the
 *   [com.wingedsheep.engine.core.ChooseOptionDecision]'s options.
 */
@Serializable
data class ChooseOpponentForSourceContinuation(
    override val decisionId: String,
    val sourceId: EntityId,
    val controllerId: EntityId,
    val opponentIds: List<EntityId>
) : ContinuationFrame
