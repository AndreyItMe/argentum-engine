package com.wingedsheep.ai.engine

import com.wingedsheep.engine.limited.BoosterGenerator

/**
 * Generates a 40-card sealed deck by opening 8 boosters from a set and
 * selecting a playable build from the resulting pool.
 *
 * Basic land names in the output are distributed across art variants
 * from the selected set.
 */
class SealedDeckGenerator(
    private val boosterGenerator: BoosterGenerator
) {
    /**
     * Picks a random set code that can actually produce a sealed deck.
     *
     * Only [BoosterGenerator.SetConfig.fullyImplemented] sets are eligible: a partial set
     * (incomplete, or not curated for sealed) can have a card pool too thin for the single-set
     * booster strategy, which then throws `No cards available for booster generation`. Random
     * quick/AI games have no host to opt into a partial set, so they must draw from a playable one.
     * Falls back to all available sets only if — unexpectedly — none are fully implemented.
     */
    fun randomSetCode(): String {
        val playable = boosterGenerator.availableSets.values.filter { it.fullyImplemented }
        val pool = playable.ifEmpty { boosterGenerator.availableSets.values.toList() }
        return pool.random().setCode
    }

    /**
     * Generates a sealed deck from 8 boosters of a random available set.
     *
     * @return A map of card name (or "Name#SetCode-CollectorNumber" for lands) to count
     */
    fun generate(): Map<String, Int> = generate(randomSetCode())

    /**
     * Generates a sealed deck from 8 boosters of the specified set.
     *
     * @param setCode The set to generate boosters from
     * @return A map of card name (or "Name#SetCode-CollectorNumber" for lands) to count
     */
    fun generate(setCode: String): Map<String, Int> {
        requireNotNull(boosterGenerator.availableSets[setCode]) { "Unknown set code: $setCode" }

        val pool = boosterGenerator.generateSealedPool(setCode, boosterCount = 8)
        val deck = buildHeuristicSealedDeck(pool)

        // Distribute basic lands across art variants for visual variety
        val variants = boosterGenerator.getAllBasicLandVariants(setCode)
        return BoosterGenerator.distributeBasicLandVariants(deck, variants)
    }
}
