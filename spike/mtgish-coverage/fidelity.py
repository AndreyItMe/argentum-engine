#!/usr/bin/env python3
"""
GENERATION-fidelity probe — "could we auto-AUTHOR this card from mtgish?", not merely "is it
covered?" (that's probe.py).

Two ground truths, in increasing strength:
  STATIC  (--set / --all)  diff the emitter's prediction against each card's committed golden
          snapshot (mtg-sets/.../snapshots/cards/<CODE>.json). Both sides are Argentum @SerialName
          tags, so it's apples-to-apples without compiling anything. A card is tiered:
            AUTO     recall==1 AND emitter.render_card() renders the WHOLE card
            SCAFFOLD recall==1 BUT some structure isn't recovered (render returns incomplete)
            MISS     recall<1  (the bridge doesn't even name a capability the card uses)
          AUTO here means "the emitter emits it whole" — the SAME renderer autogen.py writes and the
          Kotlin gate compiles, so the tier can't drift from what's actually emittable.

  COMPILED (--gate)  the real gate: after `./gradlew :mtg-sets:verifyGeneratedCards` has compiled the
          emitted cards and serialised them, diff those COMPILED trees against the golden using the
          same capability function on both sides. Proves the draft compiles AND its capabilities
          match — not just that the prediction lined up. (Behavioural correctness still needs a
          scenario test; that stays the ultimate gate, exactly as FINDINGS states.)

Usage:
  python3 fidelity.py --set POR                 # tier the whole set + miss/scaffold taxonomy
  python3 fidelity.py --set POR --list SCAFFOLD  # list cards in a tier
  python3 fidelity.py --all                      # cross-set table
  python3 fidelity.py --emit "Wrath of God"      # emit the generated cardDef for one card
  python3 fidelity.py --gate POR                 # COMPILED gate: diff serialised drafts vs golden
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter
from pathlib import Path

sys.dont_write_bytecode = True
SPIKE_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SPIKE_DIR))
import probe     # noqa: E402  registry scan + mtgish extractor/index
import emitter   # noqa: E402  the single mtgish -> DSL renderer (AUTO ⟺ it renders whole)

SNAP_DIR = probe.REPO_ROOT / "mtg-sets/src/test/resources/snapshots/cards"
GEN_DIR = probe.REPO_ROOT / "mtg-sets/build/generated-cards"

# Pure structural plumbing — present in the compiled tree but not a game "action". Excluded from
# the capability comparison on BOTH sides so we score what the card DOES.
PLUMBING = {
    "Composite", "GatherCards", "SelectFromCollection", "ChooseUpTo", "ChooseExactly",
    "ForEachInGroup", "ForEachPlayer", "ForEachTarget", "ToZone", "FromZone",
    "TopOfLibrary", "Random",
}


# ---------------------------------------------------------------------------
# Snapshot parsing (golden and generated share the "// Name\n{json}" format).
# ---------------------------------------------------------------------------
def parse_blocks(text: str) -> dict[str, dict]:
    out: dict[str, dict] = {}
    parts = re.split(r"^// (.+)$", text, flags=re.MULTILINE)
    for i in range(1, len(parts) - 1, 2):
        name, body = parts[i].strip(), parts[i + 1].strip()
        try:
            out[probe.front(name)] = json.loads(body)
        except json.JSONDecodeError:
            pass
    return out


def parse_snapshot(code: str) -> dict[str, dict]:
    path = SNAP_DIR / f"{code.upper()}.json"
    if not path.exists():
        sys.exit(f"no golden snapshot at {path} — this set has no committed snapshot to diff against")
    return parse_blocks(path.read_text())


def walk_types(node, types: Counter, keywords: set):
    if isinstance(node, dict):
        t = node.get("type")
        if isinstance(t, str):
            types[t] += 1
        kws = node.get("keywords")
        if isinstance(kws, list):
            keywords.update(k for k in kws if isinstance(k, str))
        for v in node.values():
            walk_types(v, types, keywords)
    elif isinstance(node, list):
        for v in node:
            walk_types(v, types, keywords)


def truth_caps(cardjson, effects):
    types, keywords = Counter(), set()
    walk_types(cardjson, types, keywords)
    return {t for t in types if t in effects and t not in PLUMBING}, keywords


# ---------------------------------------------------------------------------
# Generated side (prediction): what the mtgish->mapping bridge names in Argentum tags.
# ---------------------------------------------------------------------------
def gen_caps(mtgish_card, mapping, effects, keywords):
    tags = Counter()
    probe.extract_tags(mtgish_card.get("Rules", []), tags)
    eff, kw = set(), set()
    emitter.find_landwalk_keywords(mtgish_card.get("Rules", []), keywords, kw)
    for (disc, val), _n in tags.items():
        entry = mapping.get(f"{disc}:{val}", mapping.get(val))
        if entry is None:
            auto = probe.pascal_to_upper_snake(val) if isinstance(val, str) else ""
            if auto in keywords:
                kw.add(auto)
            continue
        kind = entry.get("kind")
        if kind == "effect" and entry["tag"] in effects:
            eff.add(entry["tag"])
        elif kind == "keyword" and entry["tag"] in keywords:
            kw.add(entry["tag"])
        for t in entry.get("tags", []):
            if t in effects and t not in PLUMBING:
                eff.add(t)
    return eff, kw


# ---------------------------------------------------------------------------
# Scoring
# ---------------------------------------------------------------------------
def score_card(truth, gen, complete):
    t_all, g_all = truth[0] | truth[1], gen[0] | gen[1]
    missing = t_all - g_all
    recall = 1.0 if not t_all else len(t_all & g_all) / len(t_all)
    tier = "MISS" if missing else ("AUTO" if complete else "SCAFFOLD")
    return tier, recall, missing


def score_set(code, effects, keywords, mapping):
    truth = parse_snapshot(code)
    mtgish = probe.load_mtgish_index(set(truth.keys()))
    tiers = {"AUTO": [], "SCAFFOLD": [], "MISS": [], "UNMATCHED": []}
    miss_tax, scaffold_reasons, recalls = Counter(), Counter(), []
    for name in sorted(truth):
        mt = mtgish.get(name)
        if mt is None:
            tiers["UNMATCHED"].append(name)
            continue
        t = truth_caps(truth[name], effects)
        g = gen_caps(mt, mapping, effects, keywords)
        res = emitter.render_card(mt, probe.scryfall_card(code, name), effects, keywords, mapping)
        tier, recall, missing = score_card(t, g, res.complete)
        tiers[tier].append(name)
        recalls.append(recall)
        for m in missing:
            miss_tax[m] += 1
        if tier == "SCAFFOLD":
            for r in res.reasons:
                scaffold_reasons[r] += 1
    total = sum(len(v) for v in tiers.values())
    return {
        "code": code.upper(), "total": total, "matched": total - len(tiers["UNMATCHED"]),
        "tiers": tiers, "avg_recall": (sum(recalls) / len(recalls) * 100 if recalls else 0),
        "miss_tax": miss_tax, "scaffold_reasons": scaffold_reasons,
    }


ALL_SETS = ["POR", "INV", "ONS", "KTK", "DOM", "LGN", "SCG", "ARN"]


def mode_all(effects, keywords, mapping):
    print("== cross-set generation fidelity (one bridge, applied unchanged) ==")
    print("   the gap from POR is per-corpus mapping/emitter debt; convergence = one fix helps all\n")
    print(f"  {'SET':<5} {'matched':>7} {'AUTO':>6} {'SCAFFOLD':>9} {'MISS':>6} {'recall':>7}")
    print("  " + "-" * 46)
    for code in ALL_SETS:
        try:
            s = score_set(code, effects, keywords, mapping)
        except SystemExit:
            continue
        m = s["matched"] or 1
        print(f"  {s['code']:<5} {s['matched']:>7} "
              f"{len(s['tiers']['AUTO']) / m * 100:>5.1f}% "
              f"{len(s['tiers']['SCAFFOLD']) / m * 100:>8.1f}% "
              f"{len(s['tiers']['MISS']) / m * 100:>5.1f}% "
              f"{s['avg_recall']:>6.1f}%")
    return 0


def mode_set(code, effects, keywords, mapping, list_tier):
    s = score_set(code, effects, keywords, mapping)
    tiers, matched = s["tiers"], s["matched"]
    print(f"== {code.upper()} generation fidelity — {matched} cards (vs compiled golden) ==\n")

    def pct(k):
        return f"{len(tiers[k]) / matched * 100:5.1f}%" if matched else "  —  "
    print(f"  AUTO      {len(tiers['AUTO']):>4}  {pct('AUTO')}  emitter renders the whole card "
          f"(recall=1, every action/ability emitted)")
    print(f"  SCAFFOLD  {len(tiers['SCAFFOLD']):>4}  {pct('SCAFFOLD')}  right capabilities, but some "
          f"structure isn't recovered yet")
    print(f"  MISS      {len(tiers['MISS']):>4}  {pct('MISS')}  bridge omits a capability the card uses")
    print(f"\n  mean capability recall: {s['avg_recall']:.1f}%  (effects+keywords matched per card)")
    if tiers["UNMATCHED"]:
        print(f"  (unmatched in mtgish: {len(tiers['UNMATCHED'])})")
    if s["miss_tax"]:
        print("\nMISS taxonomy — capabilities the bridge failed to emit (mapping holes to close):")
        for cap, c in s["miss_tax"].most_common(20):
            print(f"  x{c:<4} {cap}")
    if s["scaffold_reasons"]:
        print("\nSCAFFOLD reasons — structure not recovered (the worklist to push SCAFFOLD->AUTO):")
        for r, c in s["scaffold_reasons"].most_common(20):
            print(f"  x{c:<4} {r}")
    if list_tier:
        names = tiers.get(list_tier.upper(), [])
        print(f"\n{list_tier.upper()} ({len(names)}):")
        for nm in names:
            print(f"  - {nm}")
    return 0


def emit(name, effects, keywords, mapping):
    idx = probe.load_mtgish_index({name})
    card = idx.get(probe.front(name)) or next(
        (c for k, c in idx.items() if k.lower() == probe.front(name).lower()), None)
    if not card:
        print(f"'{name}': not found in mtgish IR")
        return 1
    set_code = next(iter(probe.implemented_names_for_card(card["Name"])), "POR")
    scryfall = probe.scryfall_card(set_code, card["Name"])
    res = emitter.render_card(card, scryfall, effects, keywords, mapping,
                              package=f"com.wingedsheep.mtg.sets.generated.{set_code.lower()}.cards")
    print(res.text)
    print(f"// fidelity tier: {'AUTO' if res.complete else 'SCAFFOLD'}"
          + (f" — unrecovered: {sorted(res.reasons)}" if res.reasons else ""))
    return 0


# ---------------------------------------------------------------------------
# COMPILED gate — diff the serialised generated trees against golden (same caps fn both sides).
# ---------------------------------------------------------------------------
def mode_gate(code, effects, keywords, mapping):
    gen_path = GEN_DIR / f"{code.lower()}.generated.json"
    if not gen_path.exists():
        sys.exit(f"no serialised drafts at {gen_path}\n"
                 f"run the gate first:  ./gradlew :mtg-sets:verifyGeneratedCards -Pset={code.upper()}")
    generated = parse_blocks(gen_path.read_text())
    golden = parse_snapshot(code)
    verified, mismatches = [], []
    for name in sorted(generated):
        if name not in golden:
            continue
        g = truth_caps(generated[name], effects)
        t = truth_caps(golden[name], effects)
        missing = (t[0] | t[1]) - (g[0] | g[1])
        (mismatches if missing else verified).append((name, sorted(missing)))
    not_emitted = sorted(set(golden) - set(generated))
    total = len(golden)
    print(f"== {code.upper()} COMPILED gate — generated cards diffed vs golden ==\n")
    print(f"  emitted & COMPILED:        {len(generated)}/{total}  (every one compiled — Gradle gate)")
    print(f"  VERIFIED (caps match):     {len(verified)}")
    print(f"  capability MISMATCH:       {len(mismatches)}")
    print(f"  not emitted (not AUTO):    {len(not_emitted)}")
    if mismatches:
        print("\nMISMATCH — compiled draft missing golden capabilities (emitter bug to fix):")
        for name, miss in mismatches:
            print(f"  - {name:28} missing {miss}")
    if not_emitted:
        print(f"\nNOT EMITTED ({len(not_emitted)}) — emitter can't render these whole yet:")
        for name in not_emitted:
            print(f"  - {name}")
    ok = not mismatches and not not_emitted
    print(f"\n{'GATE PASS — every golden card emits, compiles, and capability-matches.' if ok else 'GATE INCOMPLETE — see above.'}")
    return 0 if ok else 2


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--set", metavar="CODE", help="tier a whole set against its golden snapshot")
    g.add_argument("--all", action="store_true", help="cross-set table over all substantial sets")
    g.add_argument("--emit", metavar="NAME", nargs="+", help="emit generated DSL for one card")
    g.add_argument("--gate", metavar="CODE", help="COMPILED gate: diff serialised drafts vs golden")
    ap.add_argument("--list", metavar="TIER", help="with --set: list cards in AUTO/SCAFFOLD/MISS")
    args = ap.parse_args()
    effects, keywords, mapping = (probe.load_effect_serialnames(), probe.load_keywords(),
                                  probe.load_mapping())
    if args.emit:
        return emit(" ".join(args.emit), effects, keywords, mapping)
    if args.all:
        return mode_all(effects, keywords, mapping)
    if args.gate:
        return mode_gate(args.gate, effects, keywords, mapping)
    return mode_set(args.set, effects, keywords, mapping, args.list)


if __name__ == "__main__":
    sys.exit(main())
