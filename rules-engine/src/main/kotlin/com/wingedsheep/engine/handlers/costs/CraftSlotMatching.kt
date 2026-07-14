package com.wingedsheep.engine.handlers.costs

import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.GameObjectFilter

/**
 * Bipartite perfect-matching for heterogeneous (per-slot) Craft materials — CR 702.167.
 *
 * A slot-based craft names one material filter per slot ("Craft with a Dinosaur, a Merfolk, a
 * Pirate, and a Vampire") and each slot must be filled by a **distinct** material. Because a
 * single card can satisfy several slot filters (a Merfolk Pirate matches both the Merfolk and
 * Pirate slots), a plain per-slot count check is wrong: it would let one Merfolk Pirate satisfy
 * two slots at once, or accept four Vampires for the four subtypes. Correctly deciding whether a
 * set of materials can cover every slot is exactly a maximum bipartite matching (slots ↔ distinct
 * materials); every slot must be saturated.
 *
 * This reuses the same Kuhn's augmenting-path routine as
 * [com.wingedsheep.engine.mechanics.combat.BlockPhaseManager]'s must-be-blocked matching. It is a
 * pure function of the `(material, slotFilter) -> Boolean` edge predicate, so both the cost handler
 * (payment / `canPay`, matching against projected state) and the legal-action enumerator (offering
 * the ability) can share one definition of "these materials can satisfy these slots".
 */
object CraftSlotMatching {

    /**
     * True iff every slot in [slots] can be assigned its own distinct material from [materials]
     * under [matchesSlot] — i.e. there is a matching saturating all slots. When
     * `materials.size == slots.size` this is a perfect matching (each material used once).
     *
     * @param matchesSlot edge predicate: may this material fill this slot's filter?
     */
    fun canSatisfyAllSlots(
        slots: List<GameObjectFilter>,
        materials: List<EntityId>,
        matchesSlot: (EntityId, GameObjectFilter) -> Boolean
    ): Boolean {
        if (slots.isEmpty()) return true
        // Kuhn's augmenting-path matching: give each slot its own material, recursively re-homing
        // a material's current slot when contested. `matchedMaterialToSlot[m] = s` means material m
        // is currently assigned to slot index s.
        val matchedMaterialToSlot = HashMap<EntityId, Int>()

        fun assign(slotIndex: Int, visited: MutableSet<EntityId>): Boolean {
            for (materialId in materials) {
                if (!matchesSlot(materialId, slots[slotIndex])) continue
                if (!visited.add(materialId)) continue
                val currentSlot = matchedMaterialToSlot[materialId]
                if (currentSlot == null || assign(currentSlot, visited)) {
                    matchedMaterialToSlot[materialId] = slotIndex
                    return true
                }
            }
            return false
        }

        for (slotIndex in slots.indices) {
            if (!assign(slotIndex, HashSet())) return false
        }
        return true
    }
}
