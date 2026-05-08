package com.wingedsheep.gameserver.config

import com.wingedsheep.ai.engine.SealedDeckGenerator
import com.wingedsheep.ai.engine.deck.RandomDeckGenerator
import com.wingedsheep.engine.limited.BoosterGenerator
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.definitions.custom.JustOneGlassToken
import com.wingedsheep.mtg.sets.definitions.custom.SekshaasEarlySleeper
import com.wingedsheep.mtg.sets.definitions.portal.PortalSet
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GameBeansConfig(
    private val gameProperties: GameProperties,
) {

    private fun activeSets(): List<MtgSet> =
        MtgSetCatalog.all.filter { gameProperties.sets.isEnabled(it.code) }

    @Bean
    fun cardRegistry(): CardRegistry = CardRegistry().apply {
        register(PredefinedTokens.allTokens)
        for (set in activeSets()) {
            register(set.cards.stamp(set.code))
            register(set.basicLands)
            set.basicLandsFallback?.let { register(it.basicLands) }
        }
        // Easter egg card — injected into Rick's deck at game start
        register(SekshaasEarlySleeper)
        register(JustOneGlassToken)
    }

    @Bean
    fun boosterGenerator(): BoosterGenerator = BoosterGenerator(
        activeSets()
            .filter { it.sealedSupported }
            .associate { it.code to it.toBoosterSetConfig() }
    )

    @Bean
    fun sealedDeckGenerator(boosterGenerator: BoosterGenerator): SealedDeckGenerator =
        SealedDeckGenerator(boosterGenerator)

    @Bean
    fun randomDeckGenerator(): RandomDeckGenerator {
        val active = activeSets()
        return RandomDeckGenerator(
            cardPool = active.flatMap { it.cards },
            basicLandVariants = PortalSet.basicLands,
            setCodes = active.map { it.code },
        )
    }
}

private fun List<CardDefinition>.stamp(setCode: String): List<CardDefinition> =
    map { if (it.setCode == null) it.copy(setCode = setCode) else it }

private fun MtgSet.toBoosterSetConfig(): BoosterGenerator.SetConfig =
    BoosterGenerator.SetConfig(
        setCode = code,
        setName = displayName,
        cards = cards,
        basicLands = (basicLandsFallback ?: this).basicLands,
        incomplete = incomplete,
        block = block,
        totalSetSize = totalSetSize,
        guaranteedLegendary = guaranteedLegendary,
    )
