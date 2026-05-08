package com.wingedsheep.mtg.sets.definitions.scg

import com.wingedsheep.mtg.sets.definitions.scg.cards.*
import com.wingedsheep.mtg.sets.definitions.ons.OnslaughtSet
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet

/**
 * Scourge Set (2003)
 *
 * Scourge was the third and final set in the Onslaught block, featuring
 * the Storm mechanic and heavy tribal themes including Dragons.
 *
 * Set Code: SCG
 * Release Date: May 26, 2003
 * Card Count: 143
 */
object ScourgeSet : MtgSet {

    override val code = "SCG"
    override val displayName = "Scourge"
    override val block = "Onslaught"
    override val basicLandsFallback = OnslaughtSet
    override val sealedSupported = true

    override val cards: List<CardDefinition> = listOf(
        // Artifacts
        ArkOfBlight,
        ProteusMachine,
        Stabilizer,

        // White/Black creatures
        Edgewalker,

        // Black/Red creatures
        BladewingTheRisen,

        // Black creatures
        BladewingsThrall,
        CabalInterrogator,
        CarrionFeeder,
        ConsumptiveGoo,

        // Green creatures
        AmbushCommander,
        AncientOoze,
        ElvishAberration,
        FierceEmpath,
        ForgottenAncient,
        KrosanDrover,
        KrosanWarchief,
        Kurgadon,
        RootElemental,
        TitanicBulvox,
        TreetopScout,
        WirewoodGuardian,
        WirewoodSymbiote,
        Woodcloaker,
        XantidSwarm,

        // Green enchantments
        AlphaStatus,
        DragonFangs,
        OneWithNature,
        PrimitiveEtchings,
        Upwelling,

        // Green instants
        AcceleratedMutation,
        DecreeOfSavagery,
        DivergentGrowth,
        HuntingPack,
        SproutingVines,

        // Green sorceries
        BreakAsunder,
        ClawsOfWirewood,

        // Blue creatures
        AphettoRunecaster,
        CoastWatcher,
        MercurialKite,
        MischievousQuanar,
        MistformWarchief,
        RavenGuildInitiate,
        RavenGuildMaster,
        RiptideSurvivor,
        ScornfulEgotist,
        ShorelineRanger,

        ThundercloudElemental,

        // Blue enchantments
        DayOfTheDragons,
        DecreeOfSilence,
        DragonWings,
        FacesOfThePast,
        FrozenSolid,
        ParallelThoughts,
        PemminsAura,

        // Blue instants
        BrainFreeze,
        DispersalShield,
        HinderingTouch,
        LongTermPlans,
        Metamorphose,
        Stifle,

        // Blue sorceries
        MindsDesire,
        RushOfKnowledge,
        TemporalFissure,

        // Red creatures
        BonethornValesk,
        ChartoothCougar,
        DragonMage,
        DragonspeakerShaman,
        DragonTyrant,
        GoblinBrigand,
        GoblinPsychopath,
        GoblinWarchief,
        RockJockey,
        SiegeGangCommander,
        SkirkVolcanist,

        // Red enchantments
        DragonBreath,
        ExtraArms,
        FormOfTheDragon,
        GripOfChaos,
        PyrostaticPillar,
        SulfuricVortex,
        UncontrolledInfestation,

        // Red sorceries
        DecreeOfAnnihilation,
        Dragonstorm,
        GoblinWarStrike,
        MisguidedRage,
        TorrentOfFire,

        // Red instants
        Carbonize,
        Enrage,
        Scattershot,
        SparkSpray,

        // White creatures
        AgelessSentinels,
        AvenFarseer,
        ExiledDoomsayer,
        EternalDragon,
        AvenLiberator,
        DaruSpiritualist,
        DaruWarchief,
        DawnElemental,
        Dragonstalker,
        FrontlineStrategist,
        KaronasZealot,
        NobleTemplar,
        SilverKnight,
        TrapDigger,
        ZealousInquisitor,

        // White enchantments
        DragonScales,
        ForceBubble,
        GuiltyConscience,

        // White sorceries
        DecreeOfJustice,
        DimensionalBreach,

        // White instants
        AstralSteel,
        GildedLight,
        RainOfBlades,
        Recuperate,
        RewardTheFaithful,
        WipeClean,
        WingShards,

        // Black enchantments
        CallToTheGrave,
        ClutchOfUndeath,
        DragonShadow,
        FatalMutation,
        LethalVapors,
        LingeringDeath,
        UnspeakableSymbol,

        // Black creatures (additional)
        DeathsHeadBuzzard,
        Nefashu,
        PutridRaptor,
        SoulCollector,
        TwistedAbomination,
        UndeadWarchief,
        VengefulDead,
        ZombieCutthroat,

        // Black sorceries
        CabalConditioning,
        DecreeOfPain,
        FinalPunishment,
        Skulltap,
        TendrilsOfAgony,
        Unburden,

        // Black instants
        ChillHaunting,
        ReapingTheGraves,

        // Multicolor creatures
        KaronaFalseGod,
        SliverOverlord,

        // Lands
        TempleOfTheFalseGod,
    )
}
