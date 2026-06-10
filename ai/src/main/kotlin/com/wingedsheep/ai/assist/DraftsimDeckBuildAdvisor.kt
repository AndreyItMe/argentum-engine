package com.wingedsheep.ai.assist

import com.wingedsheep.ai.draftsim.DraftsimData
import com.wingedsheep.ai.draftsim.DraftsimDeckBuilder
import com.wingedsheep.ai.draftsim.DraftsimPoolCard
import com.wingedsheep.ai.draftsim.toScorerCard

/**
 * Deckbuild engine backed by the ported Draftsim autobuilder ([DraftsimDeckBuilder]): it ranks
 * archetypes (`kf`), greedily builds + refines each (`vX`/`ek`), and returns the highest-scoring
 * 23-nonland + 17-land deck.
 *
 * When [DeckBuildRequest.locked] is empty this is a **full rebuild from the pool** (the "Auto-build"
 * button) — it ranks archetypes and returns the best fresh 40-card limited build. When locked is
 * non-empty (the "Complete Deck" button) it switches the builder to completion mode: the locked
 * cards are forced into the build, protected from removal/swap, and the rest is filled around them in
 * the locked cards' colors. (Draftsim still fixes its own 23/17 land split, so [DeckBuildRequest.targetSize]
 * is not honored.) Basics are reported by name so the client splits them out like any other result.
 */
object DraftsimDeckBuildAdvisor : DeckBuildAdvisor {
    override val id = "draftsim"
    override val displayName = "Draftsim"

    private val COLOR_TO_BASIC = mapOf("W" to "Plains", "U" to "Island", "B" to "Swamp", "R" to "Mountain", "G" to "Forest")

    override fun buildDeck(request: DeckBuildRequest): DeckBuildResult {
        val builder = DraftsimDeckBuilder(DraftsimData.tablesFor(request.setCodes))
        val pool = request.pool.mapIndexed { i, def -> DraftsimPoolCard(def.toScorerCard(), "pool-$i") }

        val builds = builder.buildDecks(pool, mode = "sealed", forced = lockedInstanceIds(pool, request.locked))
        val best = builds.firstOrNull()
            ?: return DeckBuildResult(advisorId = id, deckList = emptyMap(), score = null)

        val byId = pool.associateBy { it.instanceId }
        val deckList = LinkedHashMap<String, Int>()
        for (instanceId in best.deckInstanceIds) {
            val name = byId[instanceId]?.card?.name ?: continue
            deckList[name] = (deckList[name] ?: 0) + 1
        }
        for ((color, count) in best.basicsNeeded) {
            val name = COLOR_TO_BASIC[color] ?: continue
            if (count > 0) deckList[name] = (deckList[name] ?: 0) + count
        }
        return DeckBuildResult(advisorId = id, deckList = deckList, score = best.score, archetype = best.name)
    }

    /**
     * Map the in-progress deck ([DeckBuildRequest.locked], name → count) to concrete nonland pool
     * instances to force into the build. Lands are left out — basics aren't in the pool and the
     * manabase is rebuilt around the kept spells. Empty locked → empty set → a fresh build.
     */
    private fun lockedInstanceIds(pool: List<DraftsimPoolCard>, locked: Map<String, Int>): Set<String> {
        val remaining = locked.filterValues { it > 0 }.toMutableMap()
        if (remaining.isEmpty()) return emptySet()
        val forced = HashSet<String>()
        for (pc in pool) {
            if (pc.card.typeLine.contains("Land", ignoreCase = true)) continue
            val want = remaining[pc.card.name] ?: continue
            if (want <= 0) continue
            forced += pc.instanceId
            remaining[pc.card.name] = want - 1
        }
        return forced
    }
}
