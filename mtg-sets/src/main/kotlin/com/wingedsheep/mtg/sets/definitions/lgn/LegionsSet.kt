package com.wingedsheep.mtg.sets.definitions.lgn

import com.wingedsheep.mtg.sets.definitions.lgn.cards.*
import com.wingedsheep.mtg.sets.definitions.ons.OnslaughtSet
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet

/**
 * Legions Set (2003)
 *
 * Legions is the second set in the Onslaught block, notable for being the only
 * set in Magic history to consist entirely of creature cards. It features tribal
 * themes and the morph mechanic.
 *
 * Set Code: LGN
 * Release Date: February 3, 2003
 * Card Count: 145
 */
object LegionsSet : MtgSet {

    override val code = "LGN"
    override val displayName = "Legions"
    override val block = "Onslaught"
    override val basicLandsFallback = OnslaughtSet
    override val totalSetSize = 145
    override val sealedSupported = true

    override val cards: List<CardDefinition> = listOf(
        // White creatures
        AkromaAngelOfWrath,
        AkromasDevoted,
        AvenRedeemer,
        AvenWarhawk,
        BeaconOfDestiny,
        CelestialGatekeeper,
        CloudreachCavalry,
        DaruMender,
        DaruSanctifier,
        DaruStinger,
        DefenderOfTheOrder,
        DeftbladeElite,
        EssenceSliver,
        GempalmAvenger,
        Glowrider,
        LiegeOfTheAxe,
        LowlandTracker,
        PlanarGuide,
        PlatedSliver,
        StarlightInvoker,
        StoicChampion,
        SunstrikeLegionnaire,
        SwoopingTalon,
        WallOfHope,
        WhipgrassEntangler,
        WardSliver,
        WhiteKnight,
        WindbornMuse,
        WingbeatWarrior,

        // Blue creatures
        AvenEnvoy,
        ChromeshellCrab,
        CephalidPathmage,
        CovertOperative,
        CrookclawElder,
        Dermoplasm,
        DreambornMuse,
        EchoTracer,
        FugitiveWizard,
        GempalmSorcerer,
        GlintwingInvoker,
        KeeneyeAven,
        KeeperOfTheNineGales,
        MasterOfTheVeil,
        MerchantOfSecrets,
        MistformSeaswift,
        MistformSliver,
        MistformUltimus,
        MistformWakecaster,
        PrimocEscapee,
        RiptideDirector,
        RiptideMangler,
        ShiftingSliver,
        SynapseSliver,
        VoidmageApprentice,
        WallOfDeceit,
        WarpedResearcher,
        WeaverOfLies,
        Willbender,

        // Black creatures
        BaneOfTheLiving,
        AphettoExterminator,
        BloodCelebrant,
        CorpseHarvester,
        CryptSliver,
        DarkSupplicant,
        DeathmarkPrelate,
        Earthblighter,
        GravebornMuse,
        InfernalCaretaker,
        EmbalmedBrawler,
        DrinkerOfSorrow,
        DrippingDead,
        GempalmPolluter,
        GhastlyRemains,
        GoblinTurncoat,
        HavocDemon,
        HollowSpecter,
        NoxiousGhoul,
        PhageTheUntouchable,
        ScionOfDarkness,
        Skinthinner,
        SmokespewInvoker,
        SootfeatherFlock,
        SpectralSliver,
        ToxinSliver,
        VileDeacon,
        WitheredWretch,
        ZombieBrute,

        // Red creatures
        BloodstokeHowler,
        BladeSliver,
        Clickslither,
        CrestedCraghorn,
        HunterSliver,
        ImperialHellkite,
        KilnmouthDragon,
        FlamewaveInvoker,
        GoblinAssassin,
        GoblinClearcutter,
        GoblinDynamo,
        GoblinGoon,
        GoblinLookout,
        FreneticRaptor,
        GempalmIncinerator,
        GoblinFirebug,
        GoblinGrappler,
        LavabornMuse,
        MacetailHystrodon,
        MagmaSliver,
        RidgetopRaptor,
        RockshardElemental,
        ShaleskinPlower,
        SkirkAlarmist,
        SkirkDrillSergeant,
        SkirkMarauder,
        SkirkOutrider,
        UnstableHulk,
        WarbreakTrumpeter,

        // Green creatures
        CallerOfTheClaw,
        BerserkMurlodont,
        BranchsnapLorian,
        BroodSliver,
        CanopyCrawler,
        GempalmStrider,
        GloweringRogon,
        Brontotherium,
        DefiantElf,
        ElvishSoultiller,
        EnormousBaloth,
        FeralThrowback,
        Hundroog,
        KrosanCloudscraper,
        KrosanVorine,
        NantukoVigilante,
        NeedleshotGourna,
        PatronOfTheWild,
        PrimalWhisperer,
        QuickSliver,
        RootSliver,
        SeedbornMuse,
        StonewoodInvoker,
        TimberwatchElf,
        TotemSpeaker,
        TribalForcemage,
        VexingBeetle,
        WirewoodChanneler,
        WirewoodHivemaster,
    )
}
