package com.wingedsheep.tooling.coverage.bridge

/** Evergreen keywords that map straight to a `Keyword` enum member. Most other keywords resolve for
 *  free via the probe's PascalCase→enum auto-resolve; these are the ones worth pinning explicitly. */
internal fun BridgeBuilder.keywords() {
    keyword("Flying", "FLYING")
    keyword("Haste", "HASTE")
    keyword("Vigilance", "VIGILANCE")
    keyword("Reach", "REACH")
    keyword("Defender", "DEFENDER")

    composed("Landwalk", "specific *WALK keywords (SWAMPWALK, FORESTWALK, ...)")
    // Equip is a keyword ability, but the engine has no `Keyword.EQUIP` enum member: `equipAbility(cost)`
    // synthesises the sorcery-speed "attach to target creature you control" activated ability whose
    // resolution is `AttachEquipment`. So it's composed, not a bare keyword (which would emit a
    // non-existent enum). The emitter renders only the canonical unrestricted shape (see Emitter.equipAbilityLine).
    composed("Equip", "equip: equipAbility(cost) -> sorcery-speed AttachEquipment activated ability", composes = listOf("AttachEquipment"))
}
