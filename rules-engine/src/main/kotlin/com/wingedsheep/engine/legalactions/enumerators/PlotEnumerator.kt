package com.wingedsheep.engine.legalactions.enumerators

import com.wingedsheep.engine.core.PlotCard
import com.wingedsheep.engine.legalactions.ActionEnumerator
import com.wingedsheep.engine.legalactions.EnumerationContext
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.scripting.KeywordAbility

/**
 * Enumerates plot actions for cards in hand (CR 718, Outlaws of Thunder Junction).
 *
 * Plot is a special action available any time the controller has priority during
 * their main phase while the stack is empty — i.e. the same window as sorcery-speed
 * casts. The cast-from-exile entry for an already-plotted card is emitted by
 * [CastFromZoneEnumerator] (via the may-play permission added by the plot handler).
 */
class PlotEnumerator : ActionEnumerator {

    override fun enumerate(context: EnumerationContext): List<LegalAction> {
        if (!context.canPlaySorcerySpeed) return emptyList()

        val result = mutableListOf<LegalAction>()
        val state = context.state
        val playerId = context.playerId
        val hand = state.getHand(playerId)
        for (cardId in hand) {
            val cardComponent = state.getEntity(cardId)?.get<CardComponent>() ?: continue
            val cardDef = context.cardRegistry.getCard(cardComponent.name) ?: continue
            val plotAbility = cardDef.keywordAbilities.filterIsInstance<KeywordAbility.Plot>().firstOrNull()
                ?: continue

            val canAfford = context.manaSolver.canPay(
                state, playerId, plotAbility.cost, precomputedSources = context.availableManaSources
            )
            val autoTapPreview = if (context.skipAutoTapPreview) null else {
                context.manaSolver.solve(
                    state, playerId, plotAbility.cost, precomputedSources = context.availableManaSources
                )?.sources?.map { it.entityId }
            }
            result.add(
                LegalAction(
                    actionType = "PlotCard",
                    description = "Plot ${cardComponent.name}",
                    action = PlotCard(playerId, cardId),
                    affordable = canAfford,
                    manaCostString = plotAbility.cost.toString(),
                    autoTapPreview = autoTapPreview
                )
            )
        }
        return result
    }
}
