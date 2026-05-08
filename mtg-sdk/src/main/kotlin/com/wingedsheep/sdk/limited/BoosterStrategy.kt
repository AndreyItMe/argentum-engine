package com.wingedsheep.sdk.limited

import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Rarity
import kotlin.random.Random

/**
 * Generates a single booster pack from a set's card pool.
 *
 * Sets pick a strategy via [com.wingedsheep.sdk.model.MtgSet.boosterStrategy].
 * Strategies are pure functions of (card pool, random) -> list of cards;
 * they do not know about the booster generator, set codes, or basic lands
 * (the generator filters basic lands out before calling).
 *
 * New strategies can be added without touching the engine by writing a new
 * class that implements this interface.
 */
fun interface BoosterStrategy {
    fun generate(pool: List<CardDefinition>, random: Random): List<CardDefinition>
}

/**
 * Standard 15-card booster: N commons + N uncommons + 1 rare slot, with the
 * rare slot upgraded to a mythic with [mythicChance] when mythics exist.
 *
 * No card name is duplicated within a single pack.
 */
data class StandardBooster(
    val commons: Int = 11,
    val uncommons: Int = 3,
    val rares: Int = 1,
    val mythicChance: Double = 0.125,
) : BoosterStrategy {

    override fun generate(pool: List<CardDefinition>, random: Random): List<CardDefinition> {
        val picker = RarityPicker(pool, random)
        val booster = mutableListOf<CardDefinition>()

        repeat(commons) { picker.pick(Rarity.COMMON)?.let(booster::add) }
        repeat(uncommons) { picker.pick(Rarity.UNCOMMON)?.let(booster::add) }
        repeat(rares) {
            val card = pickRareOrMythic(picker, random)
                ?: throw IllegalStateException("No cards available for booster generation")
            booster.add(card)
        }
        return booster
    }

    private fun pickRareOrMythic(picker: RarityPicker, random: Random): CardDefinition? {
        val rolledMythic = picker.hasAny(Rarity.MYTHIC) && random.nextDouble() < mythicChance
        val firstChoice = if (rolledMythic) Rarity.MYTHIC else Rarity.RARE
        return picker.pick(firstChoice)
            ?: picker.pick(Rarity.RARE)
            ?: picker.pick(Rarity.UNCOMMON)
            ?: picker.pick(Rarity.COMMON)
    }
}

/**
 * Dominaria / Kamigawa-style booster: every pack contains a legendary creature.
 *
 * The legendary occupies the slot matching its printed rarity:
 *   - Uncommon legendary → replaces one uncommon slot
 *   - Rare/mythic legendary → replaces the rare slot
 *
 * Falls back to [base] generation when the pool has no legendary creatures.
 */
data class GuaranteedLegendaryBooster(
    val base: StandardBooster = StandardBooster(),
) : BoosterStrategy {

    override fun generate(pool: List<CardDefinition>, random: Random): List<CardDefinition> {
        val legendaries = pool.filter { it.typeLine.isLegendary && it.typeLine.isCreature }
        if (legendaries.isEmpty()) return base.generate(pool, random)

        val legendary = legendaries.random(random)
        val poolWithoutLegendary = pool.filter { it.name != legendary.name }
        val picker = RarityPicker(poolWithoutLegendary, random)
        val booster = mutableListOf<CardDefinition>()

        repeat(base.commons) { picker.pick(Rarity.COMMON)?.let(booster::add) }

        val legendaryIsUncommon = legendary.metadata.rarity == Rarity.UNCOMMON
        val uncommonSlots = if (legendaryIsUncommon) (base.uncommons - 1).coerceAtLeast(0) else base.uncommons
        repeat(uncommonSlots) { picker.pick(Rarity.UNCOMMON)?.let(booster::add) }

        val legendaryIsRareOrMythic = legendary.metadata.rarity == Rarity.RARE ||
            legendary.metadata.rarity == Rarity.MYTHIC
        if (!legendaryIsRareOrMythic) {
            val rolledMythic = picker.hasAny(Rarity.MYTHIC) && random.nextDouble() < base.mythicChance
            val rareSlot = (if (rolledMythic) picker.pick(Rarity.MYTHIC) else picker.pick(Rarity.RARE))
                ?: picker.pick(Rarity.RARE)
                ?: picker.pick(Rarity.UNCOMMON)
                ?: picker.pick(Rarity.COMMON)
            rareSlot?.let(booster::add)
        }

        booster.add(legendary)
        return booster
    }
}

/** Picks cards by rarity without repeating names within a single booster. */
private class RarityPicker(pool: List<CardDefinition>, private val random: Random) {
    private val byRarity: Map<Rarity, MutableList<CardDefinition>> =
        pool.groupBy { it.metadata.rarity }.mapValues { it.value.toMutableList() }
    private val used = mutableSetOf<String>()

    fun hasAny(rarity: Rarity): Boolean = !byRarity[rarity].isNullOrEmpty()

    fun pick(rarity: Rarity): CardDefinition? {
        val available = byRarity[rarity]?.filter { it.name !in used } ?: return null
        if (available.isEmpty()) return null
        val picked = available.random(random)
        used.add(picked.name)
        return picked
    }
}
