package com.wingedsheep.mtg.sets

import com.wingedsheep.mtg.sets.definitions.bloomburrow.BloomburrowSet
import com.wingedsheep.mtg.sets.definitions.brotherswar.TheBrothersWarSet
import com.wingedsheep.mtg.sets.definitions.dft.AetherdriftSet
import com.wingedsheep.mtg.sets.definitions.dominaria.DominariaSet
import com.wingedsheep.mtg.sets.definitions.dominariaunited.DominariaUnitedSet
import com.wingedsheep.mtg.sets.definitions.duskmourn.DuskmournSet
import com.wingedsheep.mtg.sets.definitions.edgeofeternities.EdgeOfEternitiesSet
import com.wingedsheep.mtg.sets.definitions.foundations.FoundationsSet
import com.wingedsheep.mtg.sets.definitions.innistradmidnighthunt.InnistradMidnightHuntSet
import com.wingedsheep.mtg.sets.definitions.khans.KhansOfTarkirSet
import com.wingedsheep.mtg.sets.definitions.legions.LegionsSet
import com.wingedsheep.mtg.sets.definitions.lorwyneclipsed.LorwynEclipsedSet
import com.wingedsheep.mtg.sets.definitions.lostcavernsofixalan.LostCavernsOfIxalanSet
import com.wingedsheep.mtg.sets.definitions.mkm.MurdersAtKarlovManorSet
import com.wingedsheep.mtg.sets.definitions.one.PhyrexiaAllWillBeOneSet
import com.wingedsheep.mtg.sets.definitions.onslaught.OnslaughtSet
import com.wingedsheep.mtg.sets.definitions.portal.PortalSet
import com.wingedsheep.mtg.sets.definitions.scourge.ScourgeSet
import com.wingedsheep.mtg.sets.definitions.spiderman.SpiderManSet
import com.wingedsheep.mtg.sets.definitions.wildsofeldraineset.WildsOfEldrainSet
import com.wingedsheep.sdk.model.MtgSet

/**
 * Single source of truth for all known MTG sets.
 *
 * Adding a new set: implement [MtgSet] and append the object to [all].
 * The game-server, gym, and tests discover sets through this catalog —
 * no other registration is required.
 */
object MtgSetCatalog {

    val all: List<MtgSet> = listOf(
        PortalSet,
        OnslaughtSet,
        ScourgeSet,
        LegionsSet,
        KhansOfTarkirSet,
        DominariaSet,
        DominariaUnitedSet,
        PhyrexiaAllWillBeOneSet,
        TheBrothersWarSet,
        InnistradMidnightHuntSet,
        MurdersAtKarlovManorSet,
        WildsOfEldrainSet,
        LostCavernsOfIxalanSet,
        BloomburrowSet,
        FoundationsSet,
        DuskmournSet,
        AetherdriftSet,
        EdgeOfEternitiesSet,
        LorwynEclipsedSet,
        SpiderManSet,
    )

    private val byCode: Map<String, MtgSet> = all.associateBy { it.code }

    fun byCode(code: String): MtgSet? = byCode[code]

    fun requireByCode(code: String): MtgSet =
        byCode(code) ?: throw IllegalArgumentException("Unknown set code: $code")
}
