#!/usr/bin/env python3
"""
GENERATION-fidelity probe — answers "could we auto-AUTHOR this card from mtgish?",
not merely "is it covered?" (that's probe.py).

Ground truth = the committed golden snapshot of every card's COMPILED Argentum tree
(mtg-sets/src/test/resources/snapshots/cards/<CODE>.json). No Kotlin parsing, no
compilation: both sides end up as Argentum @SerialName tags, so the comparison is
apples-to-apples in Argentum's own vocabulary.

For each card we compare:
  truth  = the semantic effect/keyword capabilities in the compiled golden tree
  gen    = the Argentum capabilities the mtgish->mapping.json bridge would emit
and tier the card:
  AUTO     recall==1 AND no parameter-hiding structure  -> a generator could emit it whole
  SCAFFOLD recall==1 BUT the tree has target filters / conditions / pipelines whose
           parameters the (capability-level) mapping discards -> right effects, wrong/missing wiring
  MISS     recall<1 -> the mapping doesn't even name a capability the human used

This is a STATIC fidelity estimate. It proves capability+shape alignment, NOT behavioural
correctness — only a compile + scenario test does that (and that's the real gate). Tiers are
a ceiling on "auto-generable", not a guarantee.

Usage:
  python3 fidelity.py --set POR                 # tier the whole set + miss taxonomy
  python3 fidelity.py --set POR --list SCAFFOLD  # list cards in a tier
  python3 fidelity.py --emit "Shivan Dragon"     # show generated cardDef DSL for one card
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
import probe  # noqa: E402  reuse registry scan + mtgish extractor/index

SNAP_DIR = probe.REPO_ROOT / "mtg-sets/src/test/resources/snapshots/cards"

# Pure structural plumbing — present in the compiled tree but not a game "action".
# Excluded from the capability comparison on BOTH sides so we score what the card DOES.
# (SCAFFOLD-vs-AUTO is decided from the mtgish side via unrecoverable_reasons(), not here.)
PLUMBING = {
    "Composite", "GatherCards", "SelectFromCollection", "ChooseUpTo", "ChooseExactly",
    "ForEachInGroup", "ForEachPlayer", "ForEachTarget", "ToZone", "FromZone",
    "TopOfLibrary", "Random",
}


# ---------------------------------------------------------------------------
# Truth side — parse the golden snapshot into per-card compiled trees.
# ---------------------------------------------------------------------------
def parse_snapshot(code: str) -> dict[str, dict]:
    path = SNAP_DIR / f"{code.upper()}.json"
    if not path.exists():
        sys.exit(f"no golden snapshot at {path} — this set has no committed snapshot to diff against")
    text = path.read_text()
    out: dict[str, dict] = {}
    # blocks are "// Name\n{json}\n"
    parts = re.split(r"^// (.+)$", text, flags=re.MULTILINE)
    # parts = ['', name1, body1, name2, body2, ...]
    for i in range(1, len(parts) - 1, 2):
        name, body = parts[i].strip(), parts[i + 1].strip()
        try:
            out[probe.front(name)] = json.loads(body)
        except json.JSONDecodeError:
            pass
    return out


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
    semantic = {t for t in types if t in effects and t not in PLUMBING}
    return semantic, keywords


# ---------------------------------------------------------------------------
# Structure recovery — can a capability-level converter reconstruct this card's
# targets/filters from mtgish, or are there params it must leave for a human?
# This is computed from the MTGISH side ("can WE recover it?"), not the truth side
# ("is the card complex?") — a filtered single target IS recoverable, so AUTO.
# ---------------------------------------------------------------------------
RECOGNIZED_TARGETS = {
    "TargetPlayer", "TargetPermanent", "AnyTarget", "TargetPlayerOrPermanent",
    "TargetGraveyardCard", "TargetSpell",
}
RECOGNIZED_FILTERS = {
    "IsCardtype", "And", "Or", "IsColor", "IsNonColor", "IsLandType", "ControlledByAPlayer",
    "SinglePermanent", "SinglePlayer", "AnyPlayer", "Opponent", "You", "InAPlayersGraveyard",
    "IsAttacking", "IsTapped", "Ref_TargetPermanents", "Ref_TargetPermanent",
}
# mtgish constructs whose PARAMETERS a capability-level converter can't fully render -> SCAFFOLD.
PROBLEMATIC = {
    "If", "Conditional", "Unless", "MayCost", "PayOrSuffer",
    "EachPlayerAction", "EachPlayerActions", "HavePlayerTakeAction",
    "SearchLibrary", "LookAtTheTopNumberCardsOfLibrary", "LookAtTheTopNumberCardsOfPlayersLibrary",
    "DestroyEachPermanent", "DestroyEachPermanentNoRegen", "TapEachPermanent", "UntapEachPermanent",
    "PutEachPermanentIntoItsOwnersHand", "ShuffleGraveyardCardIntoLibrary",
    "NumberTargetPermanents", "UptoNumberTargetPermanents", "OneOrTwoTargetPermanents",
}


def _filter_predicates(node, out):
    if isinstance(node, dict):
        for k in ("_Permanents", "_Players", "_CardsInGraveyard"):
            if isinstance(node.get(k), str):
                out.add(node[k])
        for v in node.values():
            _filter_predicates(v, out)
    elif isinstance(node, list):
        for v in node:
            _filter_predicates(v, out)


def unrecoverable_reasons(card):
    """Empty set => the whole card is structurally recoverable from mtgish (AUTO-eligible)."""
    reasons = set()

    def walk(n):
        if isinstance(n, dict):
            for disc in ("_Rule", "_Action", "_Trigger", "_Cost"):
                if n.get(disc) in PROBLEMATIC:
                    reasons.add(n[disc])
            tgt = n.get("_Target")
            if tgt is not None:
                if tgt not in RECOGNIZED_TARGETS:
                    reasons.add(f"target:{tgt}")
                preds = set()
                _filter_predicates(n.get("args"), preds)
                for p in preds:
                    if p not in RECOGNIZED_FILTERS:
                        reasons.add(f"filter:{p}")
            for v in n.values():
                walk(v)
        elif isinstance(n, list):
            for v in n:
                walk(v)

    walk(card.get("Rules", []))
    return reasons


# ---------------------------------------------------------------------------
# Generated side — what the mtgish->mapping bridge would emit, in Argentum tags.
# ---------------------------------------------------------------------------
def find_landwalk_keywords(node, keywords, out):
    """mtgish encodes landwalk generically as Landwalk{IsLandType: <Subtype>};
    recover the specific <SUBTYPE>WALK keyword the engine actually has."""
    if isinstance(node, dict):
        if node.get("_Rule") == "Landwalk":
            sub = node.get("args", {})
            name = sub.get("args") if isinstance(sub, dict) else None
            if isinstance(name, str):
                kw = name.upper() + "WALK"
                if kw in keywords:
                    out.add(kw)
        for v in node.values():
            find_landwalk_keywords(v, keywords, out)
    elif isinstance(node, list):
        for v in node:
            find_landwalk_keywords(v, keywords, out)


def gen_caps(mtgish_card, mapping, effects, keywords):
    tags = Counter()
    probe.extract_tags(mtgish_card.get("Rules", []), tags)
    eff, kw = set(), set()
    find_landwalk_keywords(mtgish_card.get("Rules", []), keywords, kw)
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
        # `tags` = the concrete primitives a composed verb compiles to, OR a lowering rider
        # on an envelope (e.g. Unless -> PayOrSuffer). Honored for ANY kind.
        for t in entry.get("tags", []):
            if t in effects and t not in PLUMBING:
                eff.add(t)
    return eff, kw


# ---------------------------------------------------------------------------
# Scoring
# ---------------------------------------------------------------------------
def score_card(truth, gen, recoverable):
    t_eff, t_kw = truth
    g_eff, g_kw = gen
    t_all, g_all = t_eff | t_kw, g_eff | g_kw
    missing = t_all - g_all
    recall = 1.0 if not t_all else len(t_all & g_all) / len(t_all)
    if missing:
        tier = "MISS"
    elif not recoverable:
        tier = "SCAFFOLD"
    else:
        tier = "AUTO"
    return tier, recall, missing


def score_set(code, effects, keywords, mapping):
    """Score every snapshot card of a set. Returns a stats dict (used by --set and --all)."""
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
        reasons = unrecoverable_reasons(mt)
        tier, recall, missing = score_card(t, g, not reasons)
        tiers[tier].append(name)
        recalls.append(recall)
        for m in missing:
            miss_tax[m] += 1
        if tier == "SCAFFOLD":
            for r in reasons:
                scaffold_reasons[r] += 1
    total = sum(len(v) for v in tiers.values())
    matched = total - len(tiers["UNMATCHED"])
    return {
        "code": code.upper(), "total": total, "matched": matched, "tiers": tiers,
        "avg_recall": (sum(recalls) / len(recalls) * 100 if recalls else 0),
        "miss_tax": miss_tax, "scaffold_reasons": scaffold_reasons,
    }


# substantial snapshot sets, by implemented-card count (the only ones with a meaningful sample)
ALL_SETS = ["POR", "INV", "ONS", "KTK", "DOM", "LGN", "SCG", "ARN"]


def mode_all(effects, keywords, mapping):
    print("== cross-set generation fidelity (one Portal-tuned bridge, applied unchanged) ==")
    print("   the gap from POR is the per-corpus mapping debt; convergence = one fix helps all\n")
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
    miss_tax, scaffold_reasons, avg_recall = s["miss_tax"], s["scaffold_reasons"], s["avg_recall"]
    print(f"== {code.upper()} generation fidelity — {matched} cards (vs compiled golden) ==\n")

    def pct(k):
        return f"{len(tiers[k]) / matched * 100:5.1f}%" if matched else "  —  "
    print(f"  AUTO      {len(tiers['AUTO']):>4}  {pct('AUTO')}  generator could emit whole "
          f"(recall=1, every target/filter recovered)")
    print(f"  SCAFFOLD  {len(tiers['SCAFFOLD']):>4}  {pct('SCAFFOLD')}  right effects, but some "
          f"structure (condition / pipeline / unrecognized filter) needs human wiring")
    print(f"  MISS      {len(tiers['MISS']):>4}  {pct('MISS')}  bridge omits a capability "
          f"the card uses")
    print(f"\n  mean capability recall: {avg_recall:.1f}%  (effects+keywords matched per card)")
    if tiers["UNMATCHED"]:
        print(f"  (unmatched in mtgish: {len(tiers['UNMATCHED'])})")

    if miss_tax:
        print("\nMISS taxonomy — Argentum capabilities the bridge failed to emit "
              "(mapping holes to close):")
        for cap, c in miss_tax.most_common(20):
            print(f"  x{c:<4} {cap}")
    if scaffold_reasons:
        print("\nSCAFFOLD reasons — mtgish structure not auto-recovered "
              "(ranked; the worklist to push SCAFFOLD->AUTO):")
        for r, c in scaffold_reasons.most_common(15):
            print(f"  x{c:<4} {r}")

    if list_tier:
        names = tiers.get(list_tier.upper(), [])
        print(f"\n{list_tier.upper()} ({len(names)}):")
        for nm in names:
            print(f"  - {nm}")
    return 0


# ---------------------------------------------------------------------------
# Emitter — best-effort mtgish -> Argentum cardDef DSL for one card.
# Faithful for shells (name/cost/type/PT/keywords); spell effects render the mapped
# facade with TODO markers where mtgish params aren't recovered. Illustrative, not a compiler.
# ---------------------------------------------------------------------------
MANA = {"ManaCostW": "{W}", "ManaCostU": "{U}", "ManaCostB": "{B}", "ManaCostR": "{R}",
        "ManaCostG": "{G}", "ManaCostC": "{C}", "ManaCostX": "{X}"}


def render_mana(cost):
    out = []
    for s in cost or []:
        sym = s.get("_ManaSymbol")
        if sym == "ManaCostGeneric":
            out.append("{%s}" % s.get("args", 0))
        else:
            out.append(MANA.get(sym, "{?}"))
    return "".join(out)


def render_typeline(tl):
    sup = " ".join(tl.get("Supertypes", []))
    types = " ".join(tl.get("Cardtypes", []))
    subs = " ".join(tl.get("Subtypes", []))
    left = (sup + " " + types).strip()
    return f"{left} — {subs}" if subs else left


# --- recovery of targets / filters / amounts from mtgish action args -> Argentum DSL ---
def _find_integer(node):
    if isinstance(node, dict):
        if node.get("_GameNumber") == "Integer":
            return node.get("args")
        if node.get("_GameNumber") in ("XValue", "X"):
            return "X"
        for v in node.values():
            r = _find_integer(v)
            if r is not None:
                return r
    elif isinstance(node, list):
        for v in node:
            r = _find_integer(v)
            if r is not None:
                return r
    return None


def _find_adjust_pt(node):
    if isinstance(node, dict):
        if node.get("_LayerEffect") == "AdjustPT":
            return node.get("args")
        for v in node.values():
            r = _find_adjust_pt(v)
            if r:
                return r
    elif isinstance(node, list):
        for v in node:
            r = _find_adjust_pt(v)
            if r:
                return r
    return None


def creature_filter_dsl(filter_node):
    """mtgish _Permanents filter -> TargetFilter.Creature[.notColor()/.color()] (best effort)."""
    base = "TargetFilter.Creature"
    suffix = ""
    preds = json.dumps(filter_node)  # cheap scan; precise enough for the common predicates
    m = re.search(r'"IsNonColor".*?"_Color":\s*"(\w+)"', preds)
    if m:
        suffix += f".notColor(Color.{m.group(1).upper()})"
    m = re.search(r'"IsColor".*?"_Color":\s*"(\w+)"', preds)
    if m:
        suffix += f".color(Color.{m.group(1).upper()})"
    return base + suffix


def target_dsl(tnode):
    """Faithful Argentum target, or None if the filter can't be rendered (-> SCAFFOLD, not a
    silently-wrong AUTO). Honesty matters more than coverage here."""
    ttype = tnode.get("_Target")
    args = tnode.get("args")
    if ttype == "TargetPlayer":
        return "TargetPlayer()"
    if ttype in ("AnyTarget", "TargetPlayerOrPermanent"):
        return "AnyTarget()"
    if ttype == "TargetPermanent":
        blob = json.dumps(args)
        types = set(re.findall(r'"IsCardtype",\s*"args":\s*"(\w+)"', blob))
        if types == {"Creature"}:
            return f"TargetCreature(filter = {creature_filter_dsl(args)})"
        if not types and "IsCardtype" not in blob:
            return "TargetPermanent()"          # genuinely unfiltered
        return None                              # artifact/enchantment/land/multi-type — not renderable yet
    # graveyard / spell / numbered targets need zone/count wiring we don't generate -> SCAFFOLD
    return None


def find_targeted(card):
    """Return (targets, actions) from a SpellActions->Targeted shape, else (None, None)."""
    found = [None]

    def walk(n):
        if isinstance(n, dict):
            if n.get("_Actions") == "Targeted" and isinstance(n.get("args"), list):
                found[0] = n["args"]
            for v in n.values():
                walk(v)
        elif isinstance(n, list):
            for v in n:
                walk(v)
    walk(card.get("Rules", []))
    if not found[0] or len(found[0]) < 2:
        return None, None
    targets = found[0][0] if isinstance(found[0][0], list) else []
    alist = found[0][1]
    actions = alist.get("args", []) if isinstance(alist, dict) else []
    return targets, actions


def effect_dsl(action_node, tvar):
    a = action_node.get("_Action")
    args = action_node.get("args")
    if a in ("SpellDealsDamage", "PermanentDealsDamage"):
        return f"DealDamageEffect({_find_integer(args)}, {tvar})"
    if a == "DestroyPermanent":
        return f"MoveToZoneEffect({tvar}, Zone.GRAVEYARD, byDestruction = true)"
    if a == "CreatePermanentLayerEffectUntil":
        pt = _find_adjust_pt(args)
        if pt:
            return (f"ModifyStatsEffect(powerModifier = {pt[0]}, "
                    f"toughnessModifier = {pt[1]}, target = {tvar})")
    if a in ("DrawNumberCards", "DrawACard"):
        return f"DrawCardsEffect({_find_integer(args) or 1})"
    if a == "GainLife":
        return f"GainLifeEffect({_find_integer(args)})"
    if a == "LoseLife":
        return f"LoseLifeEffect({_find_integer(args)})"
    return None


def emit(name, effects, keywords, mapping):
    idx = probe.load_mtgish_index({name})
    card = idx.get(probe.front(name)) or next(
        (c for k, c in idx.items() if k.lower() == probe.front(name).lower()), None)
    if not card:
        print(f"'{name}': not found in mtgish IR")
        return 1
    kw_lines = set()
    find_landwalk_keywords(card.get("Rules", []), keywords, kw_lines)
    tags = Counter()
    probe.extract_tags(card.get("Rules", []), tags)
    for (disc, val), _n in tags.items():
        if val == "Landwalk":
            continue
        entry = mapping.get(f"{disc}:{val}", mapping.get(val))
        auto = probe.pascal_to_upper_snake(val) if isinstance(val, str) else ""
        if (entry and entry.get("kind") == "keyword") or auto in keywords:
            kw_lines.add((entry or {}).get("tag", auto))

    reasons = unrecoverable_reasons(card)
    pt = card.get("CardPT")
    ident = re.sub(r"[^A-Za-z0-9]", "", name)
    print(f'val {ident} = card("{card["Name"]}") {{')
    print(f'    manaCost = "{render_mana(card.get("ManaCost"))}"')
    print(f'    typeLine = "{render_typeline(card.get("Typeline", {}))}"')
    if pt:
        print(f'    power = {pt.get("Power")}')
        print(f'    toughness = {pt.get("Toughness")}')
    if kw_lines:
        print(f'    keywords({", ".join("Keyword." + k for k in sorted(kw_lines))})')

    # spell block — recover target + effect for the common single-target shape
    targets, actions = find_targeted(card)
    if targets is not None and actions:
        tdsl = target_dsl(targets[0]) if targets else None
        edsl = effect_dsl(actions[0], "t") if actions else None
        if tdsl and edsl and len(actions) == 1:
            print("    spell {")
            print(f'        val t = target("target", {tdsl})')
            print(f"        effect = {edsl}")
            print("    }")
        else:
            print("    spell {  // PARTIAL — recovery incomplete:")
            if not tdsl and targets:
                print(f"        // target not recovered: {targets[0].get('_Target')}")
            if not edsl:
                print(f"        // effect not recovered: {actions[0].get('_Action')}")
            if len(actions) > 1:
                print(f"        // {len(actions)} chained actions — only first inspected")
            print("    }")
    elif reasons:
        print(f"    // STRUCTURE needs human wiring: {', '.join(sorted(reasons))}")

    print("    metadata { /* auto-filled from Scryfall: rarity, collectorNumber, artist, imageUri */ }")
    print("}")
    tier = "AUTO" if not reasons else "SCAFFOLD"
    print(f"// fidelity tier: {tier}" + (f" — reasons: {sorted(reasons)}" if reasons else ""))
    return 0


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--set", metavar="CODE", help="tier a whole set against its golden snapshot")
    g.add_argument("--all", action="store_true", help="cross-set table over all substantial sets")
    g.add_argument("--emit", metavar="NAME", nargs="+", help="emit generated DSL for one card")
    ap.add_argument("--list", metavar="TIER", help="with --set: list cards in AUTO/SCAFFOLD/MISS")
    args = ap.parse_args()
    effects, keywords, mapping = (probe.load_effect_serialnames(), probe.load_keywords(),
                                  probe.load_mapping())
    if args.emit:
        return emit(" ".join(args.emit), effects, keywords, mapping)
    if args.all:
        return mode_all(effects, keywords, mapping)
    return mode_set(args.set, effects, keywords, mapping, args.list)


if __name__ == "__main__":
    sys.exit(main())
