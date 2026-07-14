package com.wingedsheep.mtg.sets.definitions.lci.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Filters
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GrantTriggeredAbility
import com.wingedsheep.sdk.scripting.LookAtTopOfLibrary
import com.wingedsheep.sdk.scripting.TriggeredAbility
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Glowcap Lantern (LCI #187) — {G} Artifact — Equipment (uncommon)
 *
 * Equipped creature has "You may look at the top card of your library any time" and
 * "Whenever this creature attacks, it explores."
 * Equip {2}
 *
 * Implementation notes:
 * - "Whenever this creature attacks, it explores" is granted to the equipped creature via
 *   [GrantTriggeredAbility] + [Triggers.attacks] (SELF binding). The [Effects.Explore] targets
 *   [EffectTarget.Self] — the granted ability's source, i.e. the equipped creature — so "it"
 *   explores and any +1/+1 counter lands on that creature.
 * - "You may look at the top card of your library any time" is the filterless player-permission
 *   static [LookAtTopOfLibrary], which grants its controller the private peek. There is no engine
 *   primitive to scope a filterless player static onto the *equipped creature* (no generic
 *   grant-static-ability + no is-attached condition), so it is placed directly on the Equipment.
 *   In the common case (Equipment and creature share a controller) this is identical to the
 *   printed card. Minor documented deviation: an *unattached* Glowcap Lantern still lets its
 *   controller peek — harmless, since the peek is private and changes no game state.
 */
val GlowcapLantern = card("Glowcap Lantern") {
    manaCost = "{G}"
    colorIdentity = "G"
    typeLine = "Artifact — Equipment"
    oracleText = "Equipped creature has \"You may look at the top card of your library any time\" and " +
        "\"Whenever this creature attacks, it explores.\" (Reveal the top card of your library. Put that " +
        "card into your hand if it's a land. Otherwise, put a +1/+1 counter on that creature, then put " +
        "the card back or put it into your graveyard.)\n" +
        "Equip {2}"

    // "You may look at the top card of your library any time" — see KDoc deviation note.
    staticAbility {
        ability = LookAtTopOfLibrary
    }

    // Equipped creature has "Whenever this creature attacks, it explores."
    staticAbility {
        ability = GrantTriggeredAbility(
            ability = TriggeredAbility.create(
                trigger = Triggers.attacks().event,
                binding = Triggers.attacks().binding,
                effect = Effects.Explore(EffectTarget.Self)
            ),
            filter = Filters.EquippedCreature
        )
    }

    equipAbility("{2}")

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "187"
        artist = "Irina Nordsol"
        imageUri = "https://cards.scryfall.io/normal/front/b/a/bafde87c-743d-4307-93e0-fbd30f5d92f6.jpg?1782694459"
    }
}
