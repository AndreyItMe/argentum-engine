#!/usr/bin/env python3
"""
mtgish coverage probe — phase-rs-lessons.md Lesson 5 (oracle-text -> required-capability).

Predicts, from mtgish's pre-parsed oracle IR, which cards the engine could already
support with NO new engine work ("coverable-now") and which are blocked on a missing
capability. TOOLING ONLY — it never loads a card; ground truth stays authored-DSL +
passing scenario test. See FINDINGS.md.

Three lenses:
  --set CODE        full coverage of a set: implemented / free-to-implement / blocked,
                    plus a feature leaderboard (which missing capability unlocks the most).
  --card "NAME"     analyze one card: its required capabilities and the coverable verdict.
  --calibrate CODE  trust check: every ALREADY-IMPLEMENTED card must classify coverable-now;
                    anything that doesn't is a hole in mapping.json / the registry.

The bridge it leans on is mapping.json (mtgish tag -> Argentum capability), the artifact
whose size/durability the spike measures.
"""
from __future__ import annotations

import argparse
import importlib.machinery
import importlib.util
import json
import re
import subprocess
import sys
import urllib.request
from collections import Counter
from pathlib import Path

sys.dont_write_bytecode = True  # don't drop __pycache__ into scripts/ when importing card-status

SPIKE_DIR = Path(__file__).resolve().parent
REPO_ROOT = SPIKE_DIR.parent.parent
SDK_EFFECTS = REPO_ROOT / "mtg-sdk/src/main/kotlin/com/wingedsheep/sdk/scripting/effects"
KEYWORD_KT = REPO_ROOT / "mtg-sdk/src/main/kotlin/com/wingedsheep/sdk/core/Keyword.kt"
DEFINITIONS = REPO_ROOT / "mtg-sets/src/main/kotlin/com/wingedsheep/mtg/sets/definitions"
MTGISH_LINES = SPIKE_DIR / "data/mtgish.lines.json"
MTGISH_URL = "https://raw.githubusercontent.com/i5jb/mtgish/main/data/mtgish.lines.json"
CARD_STATUS = REPO_ROOT / "scripts/card-status"


# ---------------------------------------------------------------------------
# Reuse card-status's Scryfall cache + set discovery (imported, not duplicated).
# ---------------------------------------------------------------------------
def _load_card_status():
    # card-status has no .py extension, so name the source loader explicitly.
    loader = importlib.machinery.SourceFileLoader("card_status", str(CARD_STATUS))
    spec = importlib.util.spec_from_loader("card_status", loader)
    mod = importlib.util.module_from_spec(spec)
    sys.modules["card_status"] = mod  # @dataclass resolves __module__ via sys.modules
    loader.exec_module(mod)
    return mod


CS = _load_card_status()


def ensure_data() -> None:
    if MTGISH_LINES.exists():
        return
    MTGISH_LINES.parent.mkdir(parents=True, exist_ok=True)
    print(f"downloading mtgish IR (~29MB) -> {MTGISH_LINES} ...", file=sys.stderr)
    urllib.request.urlretrieve(MTGISH_URL, MTGISH_LINES)


# ---------------------------------------------------------------------------
# Part 1 — Capability registry, scanned from Kotlin source (cannot rot).
# ---------------------------------------------------------------------------
def load_effect_serialnames() -> set[str]:
    tags: set[str] = set()
    for kt in SDK_EFFECTS.glob("*.kt"):
        for m in re.finditer(r'@SerialName\("([^"]+)"\)', kt.read_text()):
            tag = m.group(1)
            if "." not in tag:  # drop nested-enum serialnames (SuccessCriterion.Auto)
                tags.add(tag)
    return tags


def load_keywords() -> set[str]:
    return set(re.findall(r"^\s+([A-Z][A-Z0-9_]+)\b", KEYWORD_KT.read_text(), re.MULTILINE))


def load_mapping() -> dict:
    return json.loads((SPIKE_DIR / "mapping.json").read_text())


# ---------------------------------------------------------------------------
# Part 2 — Extractor: walk a mtgish card IR, collect required-capability tags.
# ---------------------------------------------------------------------------
CAPABILITY_DISCRIMINATORS = {
    "_Rule", "_Action", "_Trigger", "_Cost", "_LayerEffect",
    "_ReplacementActionWouldEnter",
}


def extract_tags(node, out: Counter) -> None:
    if isinstance(node, dict):
        for disc in CAPABILITY_DISCRIMINATORS:
            if disc in node:
                out[(disc, node[disc])] += 1
        for v in node.values():
            extract_tags(v, out)
    elif isinstance(node, list):
        for v in node:
            extract_tags(v, out)


# ---------------------------------------------------------------------------
# Part 3 — Classification of one card against the registry + mapping.
# ---------------------------------------------------------------------------
# verdicts: ok / composed / supported / ignore  -> covered
#           MISSING (mapped to a capability not in the registry — a named engine gap)
#           UNMAPPED (no mapping entry — triage: new mechanic OR a mapping hole)
def pascal_to_upper_snake(s: str) -> str:
    """FirstStrike -> FIRST_STRIKE, Shadow -> SHADOW (mtgish keyword tag -> Keyword enum)."""
    return re.sub(r"(?<=[a-z0-9])(?=[A-Z])", "_", s).upper()


def analyze(card, effects, keywords, mapping):
    tags = Counter()
    extract_tags(card.get("Rules", []), tags)
    reqs = []          # (disc, val, verdict, detail)
    blockers = []      # (disc, val, verdict) that prevent coverage
    for (disc, val), _n in tags.items():
        entry = mapping.get(f"{disc}:{val}", mapping.get(val))
        if entry is None:
            # Principled fallback: if the tag IS a Keyword enum member, it's covered.
            # Only claims coverage when the keyword genuinely exists (registry-validated),
            # so envelopes like Activated/SpellActions stay correctly unmapped.
            kw = pascal_to_upper_snake(val) if isinstance(val, str) else ""
            if kw in keywords:
                reqs.append((disc, val, "ok", f"{kw} (keyword auto)"))
                continue
            reqs.append((disc, val, "UNMAPPED", ""))
            blockers.append((disc, val, "UNMAPPED"))
            continue
        kind = entry.get("kind")
        if kind == "effect":
            ok = entry["tag"] in effects
            reqs.append((disc, val, "ok" if ok else "MISSING", entry["tag"]))
            if not ok:
                blockers.append((disc, val, "MISSING"))
        elif kind == "keyword":
            ok = entry["tag"] in keywords
            reqs.append((disc, val, "ok" if ok else "MISSING", entry["tag"]))
            if not ok:
                blockers.append((disc, val, "MISSING"))
        else:  # composed / supported / ignore
            reqs.append((disc, val, kind, entry.get("note", "")))
    return (not blockers), reqs, blockers


# ---------------------------------------------------------------------------
# Card-data wiring
# ---------------------------------------------------------------------------
def front(name: str) -> str:
    return CS.front_face(name)


def implemented_names(set_code: str) -> set[str]:
    info = next((s for s in CS.discover_sets() if s.code == set_code.upper()), None)
    if info is None:
        return set()
    return {front(n) for n in CS.scan_implementations(info.cards_dir)}


def canonical_names(set_code: str, *, refresh: bool = False):
    """(draft_names, extra_names) front-faced, from card-status's Scryfall cache."""
    payload = CS.load_canonical(set_code.upper(), force_refresh=refresh)
    if payload is None:
        return None, None
    draft = {front(n) for n in payload["draft_names"]}
    extra = {front(n) for n in payload["extra_names"]} - draft
    return draft, extra


def load_mtgish_index(names: set[str]) -> dict[str, dict]:
    """Map front-faced name -> mtgish card, for the requested names only."""
    ensure_data()
    want = {n.lower() for n in names}
    found: dict[str, dict] = {}
    with MTGISH_LINES.open() as fh:
        for line in fh:
            if '"Name":"' not in line:
                continue
            card = json.loads(line)
            fn = front(card.get("Name", ""))
            if fn.lower() in want:
                found[fn] = card
    return found


# ---------------------------------------------------------------------------
# Modes
# ---------------------------------------------------------------------------
def mode_card(name, effects, keywords, mapping) -> int:
    idx = load_mtgish_index({name})
    # case-insensitive resolve
    card = idx.get(front(name)) or next(
        (c for k, c in idx.items() if k.lower() == front(name).lower()), None)
    if card is None:
        print(f"'{name}': not found in mtgish IR (name mismatch, or an Un-set/excluded card)")
        return 1
    coverable, reqs, _ = analyze(card, effects, keywords, mapping)
    print(f"Card: {card['Name']}")
    print(f"Verdict: {'COVERABLE-NOW (no engine work)' if coverable else 'BLOCKED (needs engine work)'}\n")
    print("Required capabilities (from mtgish IR):")
    order = {"UNMAPPED": 0, "MISSING": 1, "ok": 2, "composed": 3, "supported": 4, "ignore": 5}
    for disc, val, verdict, detail in sorted(reqs, key=lambda r: order.get(r[2], 9)):
        mark = {"ok": "ok  ", "composed": "comp", "supported": "supp", "ignore": "--  ",
                "MISSING": "GAP ", "UNMAPPED": "??  "}[verdict]
        arrow = f" -> {detail}" if detail else ""
        print(f"  [{mark}] {disc:<13} {val}{arrow}")
    impl = implemented_names_for_card(card["Name"])
    if impl:
        print(f"\nAlready implemented in: {', '.join(sorted(impl))}")
    return 0 if coverable else 2


def implemented_names_for_card(name: str) -> set[str]:
    """Which set folders already implement this card name."""
    hits = set()
    fn = front(name)
    for s in CS.discover_sets():
        if fn in {front(n) for n in CS.scan_implementations(s.cards_dir)}:
            hits.add(s.code)
    return hits


def mode_set(code, effects, keywords, mapping, *, show, refresh) -> int:
    draft, extra = canonical_names(code, refresh=refresh)
    if draft is None:
        print(f"no Scryfall data for set {code.upper()} (run: just card-status --set {code.upper()})")
        return 1
    canonical = draft | extra
    impl = implemented_names(code)
    idx = load_mtgish_index(canonical)

    buckets = {"impl": [], "free": [], "blocked": [], "unmatched": []}
    leaderboard = Counter()        # blocking tag -> # missing cards it blocks
    leaderboard_kind = {}          # tag -> MISSING/UNMAPPED
    blocked_detail = {}            # card -> blockers
    for name in sorted(canonical):
        if name in impl:
            buckets["impl"].append(name)
            continue
        card = idx.get(name)
        if card is None:
            buckets["unmatched"].append(name)
            continue
        coverable, _reqs, blockers = analyze(card, effects, keywords, mapping)
        if coverable:
            buckets["free"].append(name)
        else:
            buckets["blocked"].append(name)
            blocked_detail[name] = blockers
            for disc, val, verdict in blockers:
                leaderboard[(disc, val)] += 1
                leaderboard_kind[(disc, val)] = verdict

    total = len(canonical)
    print(f"== {code.upper()} — {total} cards "
          f"({len(draft)} draft / {len(extra)} extra) ==")
    print(f"  implemented:            {len(buckets['impl'])}")
    print(f"  FREE to implement now:  {len(buckets['free'])}   (missing, fully coverable)")
    print(f"  blocked on engine work: {len(buckets['blocked'])}")
    if buckets["unmatched"]:
        print(f"  (unmatched in mtgish:   {len(buckets['unmatched'])} — name join / Un-set)")

    if leaderboard:
        print("\nFeature leaderboard — missing capability ranked by # blocked cards it unlocks:")
        for (disc, val), n in leaderboard.most_common(20):
            tag = "GAP " if leaderboard_kind[(disc, val)] == "MISSING" else "??  "
            label = "engine capability absent" if tag == "GAP " else "unmapped — triage"
            print(f"  [{tag}] x{n:<4} {disc} = {val}   ({label})")

    if show in ("free", "all"):
        print(f"\nFREE to implement now ({len(buckets['free'])}):")
        for n in buckets["free"]:
            print(f"  + {n}")
    if show in ("blocked", "all"):
        print(f"\nBLOCKED ({len(buckets['blocked'])}):")
        for n in buckets["blocked"]:
            why = ",".join(v for _, v, _ in blocked_detail[n])
            print(f"  - {n}   [{why}]")
    return 0


def mode_calibrate(code, effects, keywords, mapping) -> int:
    names = implemented_names(code)
    idx = load_mtgish_index(names)
    coverable_n = 0
    holes = Counter()
    blocked = []
    for name, card in sorted(idx.items()):
        ok, _reqs, blockers = analyze(card, effects, keywords, mapping)
        if ok:
            coverable_n += 1
        else:
            blocked.append((name, blockers))
            for disc, val, _v in blockers:
                holes[(disc, val)] += 1
    total = len(idx)
    recall = coverable_n / total * 100 if total else 0
    print(f"CALIBRATION {code.upper()}: {coverable_n}/{total} implemented cards "
          f"classify coverable-now = {recall:.1f}%  (target ~100%)")
    if holes:
        print("Holes (implemented cards the bridge can't yet cover -> fix mapping/registry):")
        for (disc, val), n in holes.most_common():
            print(f"  x{n:<4} {disc} = {val}")
        for name, blockers in blocked:
            print(f"    {name}: {','.join(v for _, v, _ in blockers)}")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--set", metavar="CODE", help="analyze a whole set (implemented/free/blocked)")
    g.add_argument("--card", metavar="NAME", nargs="+",
                   help="analyze one card by name (quotes optional; tokens are rejoined)")
    g.add_argument("--calibrate", metavar="CODE", help="trust check over implemented cards")
    ap.add_argument("--free", action="store_const", const="free", dest="show",
                    help="with --set: also list the free-to-implement cards")
    ap.add_argument("--blocked", action="store_const", const="blocked", dest="show",
                    help="with --set: also list the blocked cards + reasons")
    ap.add_argument("--all", action="store_const", const="all", dest="show",
                    help="with --set: list both free and blocked")
    ap.add_argument("--refresh", action="store_true", help="force Scryfall re-fetch")
    args = ap.parse_args()

    effects, keywords, mapping = load_effect_serialnames(), load_keywords(), load_mapping()
    if args.card:
        return mode_card(" ".join(args.card), effects, keywords, mapping)
    if args.calibrate:
        return mode_calibrate(args.calibrate, effects, keywords, mapping)
    return mode_set(args.set, effects, keywords, mapping,
                    show=args.show, refresh=args.refresh)


if __name__ == "__main__":
    sys.exit(main())
