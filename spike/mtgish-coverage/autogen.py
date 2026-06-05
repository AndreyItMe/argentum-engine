#!/usr/bin/env python3
"""
Auto-generation gap detector + draft generator, on top of the mtgish bridge.

Two modes:
  --gaps SET    Of a set's UNIMPLEMENTED cards, predict which the bridge could auto-author
                today: AUTOGEN (covered + structure recoverable + emitter renders it whole),
                SCAFFOLD (covered but needs hand-wiring), BLOCKED (capability gap), with a
                leaderboard of what blocks the rest.
  --write SET   Emit a draft `.kt` for every AUTOGEN-predicted missing card into a STAGING
                dir (default spike/mtgish-coverage/generated/<set>/). Never the live set.

WHY STAGING, NOT THE LIVE SET. These are *predictions* from approximate mtgish IR with no
golden snapshot to check against — cross-set AUTO precision is ~45% and even clean emits can
be subtly wrong (see FINDINGS: Lava Axe). The project's ground truth is a human-authored card
whose scenario test passes. So this produces review-drafts that still must: (1) compile,
(2) get a scenario test, (3) be eyeballed — then dropped into the set's `cards/` package
(which auto-registers via classpath scan). It is deliberately NOT a card loader.
"""
from __future__ import annotations

import argparse
import re
import sys
from collections import Counter
from pathlib import Path

sys.dont_write_bytecode = True
SPIKE_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SPIKE_DIR))
import probe      # noqa: E402
import fidelity   # noqa: E402

SYMBOL_IMPORTS = {
    "card": "com.wingedsheep.sdk.dsl.card",
    "Rarity": "com.wingedsheep.sdk.model.Rarity",
    "Keyword": "com.wingedsheep.sdk.core.Keyword",
    "Color": "com.wingedsheep.sdk.core.Color",
    "Zone": "com.wingedsheep.sdk.core.Zone",
    "DealDamageEffect": "com.wingedsheep.sdk.scripting.effects.DealDamageEffect",
    "ModifyStatsEffect": "com.wingedsheep.sdk.scripting.effects.ModifyStatsEffect",
    "MoveToZoneEffect": "com.wingedsheep.sdk.scripting.effects.MoveToZoneEffect",
    "DrawCardsEffect": "com.wingedsheep.sdk.scripting.effects.DrawCardsEffect",
    "GainLifeEffect": "com.wingedsheep.sdk.scripting.effects.GainLifeEffect",
    "LoseLifeEffect": "com.wingedsheep.sdk.scripting.effects.LoseLifeEffect",
    "TargetFilter": "com.wingedsheep.sdk.scripting.filters.unified.TargetFilter",
    "TargetCreature": "com.wingedsheep.sdk.scripting.targets.TargetCreature",
    "TargetPlayer": "com.wingedsheep.sdk.scripting.targets.TargetPlayer",
    "TargetPermanent": "com.wingedsheep.sdk.scripting.targets.TargetPermanent",
    "AnyTarget": "com.wingedsheep.sdk.scripting.targets.AnyTarget",
}
PERMANENT_TYPES = {"Creature", "Artifact", "Enchantment", "Land", "Planeswalker"}


def card_tags(card):
    t = Counter()
    probe.extract_tags(card.get("Rules", []), t)
    return t


def is_permanent(card):
    return any(t in PERMANENT_TYPES for t in card.get("Typeline", {}).get("Cardtypes", []))


def spell_actions(card):
    """(targets|None, actions) for a spell — handles Targeted and bare ActionList shapes."""
    targets, actions = fidelity.find_targeted(card)
    if actions:
        return targets, actions
    found = [None]

    def walk(n):
        if isinstance(n, dict):
            if n.get("_Actions") == "ActionList" and isinstance(n.get("args"), list):
                found[0] = n["args"]
            for v in n.values():
                walk(v)
        elif isinstance(n, list):
            for v in n:
                walk(v)
    walk(card.get("Rules", []))
    return None, (found[0] or [])


def render_kotlin(card, effects, keywords, mapping):
    """Return (text, complete). complete=True only when the emitter renders the WHOLE card."""
    used = {"card", "Rarity"}
    reasons = fidelity.unrecoverable_reasons(card)
    tags = card_tags(card)
    action_tags = [v for (d, v) in tags if d == "_Action"]
    has_trigger = any(d == "_Trigger" for (d, v) in tags)
    has_activated = any(d == "_Rule" and v in ("Activated", "ActivatedWithModifiers")
                        for (d, v) in tags)

    kw = set()
    fidelity.find_landwalk_keywords(card.get("Rules", []), keywords, kw)
    for (disc, val) in tags:
        if val == "Landwalk":
            continue
        entry = mapping.get(f"{disc}:{val}", mapping.get(val))
        auto = probe.pascal_to_upper_snake(val) if isinstance(val, str) else ""
        if (entry and entry.get("kind") == "keyword") or auto in keywords:
            kw.add((entry or {}).get("tag", auto))
    if kw:
        used.add("Keyword")

    spell_lines, complete = [], True
    if is_permanent(card):
        # complete only if it's a pure vanilla/keyword permanent (no abilities we can't render)
        if action_tags or has_trigger or has_activated or reasons:
            complete = False
    else:  # spell
        targets, actions = spell_actions(card)
        if reasons or len(actions) != 1:
            complete = False
        else:
            edsl = fidelity.effect_dsl(actions[0], "t")
            if not edsl:
                complete = False
            elif targets:
                tdsl = fidelity.target_dsl(targets[0])
                if not tdsl or len(targets) != 1:
                    complete = False
                else:
                    spell_lines = ["    spell {",
                                   f'        val t = target("target", {tdsl})',
                                   f"        effect = {edsl}", "    }"]
                    _note_symbols(tdsl + " " + edsl, used)
            else:  # non-targeted single effect
                spell_lines = ["    spell {", f"        effect = {edsl}", "    }"]
                _note_symbols(edsl, used)

    ident = re.sub(r"[^A-Za-z0-9]", "", card["Name"])
    pt = card.get("CardPT")
    body = [f'val {ident} = card("{card["Name"]}") {{',
            f'    manaCost = "{fidelity.render_mana(card.get("ManaCost"))}"',
            f'    typeLine = "{fidelity.render_typeline(card.get("Typeline", {}))}"']
    if pt:
        body += [f'    power = {pt.get("Power")}', f'    toughness = {pt.get("Toughness")}']
    if kw:
        body.append(f'    keywords({", ".join("Keyword." + k for k in sorted(kw))})')
    body += spell_lines
    body.append("    metadata { rarity = Rarity.COMMON /* TODO verify rarity + add Scryfall fields */ }")
    body.append("}")

    imports = sorted({SYMBOL_IMPORTS[s] for s in used if s in SYMBOL_IMPORTS})
    header = [
        "// === GENERATED DRAFT — do NOT merge as-is. ===",
        "// Source: mtgish IR via the coverage bridge (predictive, approximate).",
        "// Before use: (1) compile, (2) write & pass a scenario test, (3) review the rules text.",
        "// Then move into the set's cards/ package (auto-registers via classpath scan).",
        "",
        f"package com.wingedsheep.mtg.sets.definitions.SET.cards   // TODO set package",
        "",
    ] + [f"import {i}" for i in imports] + ["", ""]
    return "\n".join(header + body) + "\n", complete


def _note_symbols(text, used):
    for sym in ("DealDamageEffect", "ModifyStatsEffect", "MoveToZoneEffect", "DrawCardsEffect",
                "GainLifeEffect", "LoseLifeEffect", "TargetCreature", "TargetPlayer",
                "TargetPermanent", "AnyTarget", "TargetFilter", "Color", "Zone"):
        if sym in text:
            used.add(sym)


def classify(card, effects, keywords, mapping):
    coverable, _reqs, _bl = probe.analyze(card, effects, keywords, mapping)
    if not coverable:
        return "BLOCKED"
    _text, complete = render_kotlin(card, effects, keywords, mapping)
    return "AUTOGEN" if complete else "SCAFFOLD"


def missing_with_mtgish(set_code):
    """(missing_card_names, {name: mtgish_card}) for a set's unimplemented cards."""
    draft, extra = probe.canonical_names(set_code)
    if draft is None:
        sys.exit(f"no Scryfall data for {set_code.upper()} — run: just card-status --set {set_code.upper()}")
    impl = probe.implemented_names(set_code)
    missing = sorted((draft | extra) - impl)
    idx = probe.load_mtgish_index(set(missing))
    return missing, idx


def mode_gaps(set_code, effects, keywords, mapping, list_cat):
    missing, idx = missing_with_mtgish(set_code)
    cats = {"AUTOGEN": [], "SCAFFOLD": [], "BLOCKED": [], "UNMATCHED": []}
    block_tax = Counter()
    for name in missing:
        card = idx.get(name)
        if card is None:
            cats["UNMATCHED"].append(name)
            continue
        cat = classify(card, effects, keywords, mapping)
        cats[cat].append(name)
        if cat == "BLOCKED":
            _ok, _r, blockers = probe.analyze(card, effects, keywords, mapping)
            for _d, v, _verdict in blockers:
                block_tax[v] += 1

    n_missing = len(missing)
    print(f"== {set_code.upper()} auto-generation gap — {n_missing} unimplemented cards ==\n")
    print(f"  AUTOGEN   {len(cats['AUTOGEN']):>4}   bridge could draft a whole card now")
    print(f"  SCAFFOLD  {len(cats['SCAFFOLD']):>4}   covered, but structure needs hand-wiring")
    print(f"  BLOCKED   {len(cats['BLOCKED']):>4}   capability gap (needs mapping or engine work)")
    if cats["UNMATCHED"]:
        print(f"  (unmatched in mtgish: {len(cats['UNMATCHED'])} — name join / Un-set / too new)")
    print(f"\n  -> `just coverage-generate --set {set_code.upper()}` drafts the {len(cats['AUTOGEN'])} "
          f"AUTOGEN cards into a staging dir.")

    if block_tax:
        print("\nBLOCKED leaderboard — capability ranked by # cards it would unlock:")
        for cap, c in block_tax.most_common(15):
            print(f"  x{c:<4} {cap}")
    if list_cat:
        names = cats.get(list_cat.upper(), [])
        print(f"\n{list_cat.upper()} ({len(names)}):")
        for nm in names:
            print(f"  - {nm}")
    return 0


def mode_write(set_code, effects, keywords, mapping, outdir):
    missing, idx = missing_with_mtgish(set_code)
    out = Path(outdir) if outdir else SPIKE_DIR / "generated" / set_code.lower()
    out.mkdir(parents=True, exist_ok=True)
    written = 0
    for name in missing:
        card = idx.get(name)
        if card is None or not probe.analyze(card, effects, keywords, mapping)[0]:
            continue
        text, complete = render_kotlin(card, effects, keywords, mapping)
        if not complete:
            continue
        text = text.replace(".definitions.SET.cards", f".definitions.{set_code.lower()}.cards")
        fname = re.sub(r"[^A-Za-z0-9]", "", name) + ".kt"
        (out / fname).write_text(text)
        written += 1
    print(f"wrote {written} draft card(s) to {out}")
    print("These are DRAFTS — compile, add a scenario test, and review before moving into the set.")
    return 0


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--set", metavar="CODE", required=True)
    g = ap.add_mutually_exclusive_group()
    g.add_argument("--gaps", action="store_true", help="report the auto-gen gap (default)")
    g.add_argument("--write", action="store_true", help="write draft .kt files for AUTOGEN cards")
    ap.add_argument("--list", metavar="CAT", help="with --gaps: list AUTOGEN/SCAFFOLD/BLOCKED")
    ap.add_argument("--out", metavar="DIR", help="with --write: output dir (default generated/<set>)")
    args = ap.parse_args()
    effects, keywords, mapping = (probe.load_effect_serialnames(), probe.load_keywords(),
                                  probe.load_mapping())
    if args.write:
        return mode_write(args.set, effects, keywords, mapping, args.out)
    return mode_gaps(args.set, effects, keywords, mapping, args.list)


if __name__ == "__main__":
    sys.exit(main())
