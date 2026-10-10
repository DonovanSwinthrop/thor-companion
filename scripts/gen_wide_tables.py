#!/usr/bin/env python3
"""WIDE (pk_wide.c) for every retail release the user owns: maps the English references' addresses
into each ROM and writes app/src/main/cpp/pk_wide_games.h, a table keyed by the ROM's CRC32.

    scripts/gen_wide_tables.py <roms dir> [<more roms dirs>...]

Every retail .gba under the dirs (by SHA-1: the app's GameTitles / RetailPortsGen list - hacks and the QoL
builds share the codes but aren't taken) whose game code is FireRed / LeafGreen (BPR / BPG) or Emerald (BPE) gets
the English reference's addresses (FireRed rev 1, Emerald: checked frame by frame headless) mapped
through the literal pools of the code that loads them (port_retail.py's map_literal: the word at the
same place in the other ROM's code). A ROM with an address no pool maps is left out and reported.
build/wide/wide.json keeps every address with its votes, for scripts/verify_wide.py, which boots
each ROM headlessly and checks the rebuild against the real frames.
"""
import hashlib
import json
import struct
import os
import re
import sys
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..")
sys.path.insert(0, HERE)
from port_retail import Rom, map_literal, map_code  # noqa: E402

OUT_H = os.path.join(ROOT, "app", "src", "main", "cpp", "pk_wide_games.h")
OUT_JSON = os.path.join(ROOT, "build", "wide", "wide.json")

# pk_wide.h's PK_WIDE_* address slots, in order.
SLOTS = ["VMAP", "MAP_HEADER", "MAP_GROUPS", "SAVE_BLOCK1_PTR", "CAMERA_OFFSET", "OBJECT_EVENTS", "SPRITES",
         "COORD_OFFSET", "SPRITE_ORDER", "GMAIN"]

# The references: what was mapped onto retail from the decomp ELFs and checked headless (pk_wide.c's
# history), plus ScriptContext_SetupScript for the checks' scripted warps and battles.
REFERENCES = {
    "FR": dict(code="BPRE", rev=1, script=0x08069AF8, a=dict(
        VMAP=0x03005040, MAP_HEADER=0x02036DFC, MAP_GROUPS=0x08352718, SAVE_BLOCK1_PTR=0x03005008,
        CAMERA_OFFSET=0x03000E90, OBJECT_EVENTS=0x02036E38, SPRITES=0x0202063C, COORD_OFFSET=0x02021BC8,
        SPRITE_ORDER=0x02021800, GMAIN=0x030030F0)),
    "EM": dict(code="BPEE", rev=0, script=0x08098EF8, a=dict(
        VMAP=0x03005DC0, MAP_HEADER=0x02037318, MAP_GROUPS=0x08486578, SAVE_BLOCK1_PTR=0x03005D8C,
        CAMERA_OFFSET=0x03000E20, OBJECT_EVENTS=0x02037350, SPRITES=0x02020630, COORD_OFFSET=0x02021BBC,
        SPRITE_ORDER=0x020217F4, GMAIN=0x030022C0)),
}
FAMILY = {"BPR": "FR", "BPG": "FR", "BPE": "EM"}
KOTLIN = os.path.join(ROOT, "app", "src", "main", "kotlin", "com", "pokedaisy", "app")


def retail_sha1s():
    """The retail releases the app knows by SHA-1: GameTitles' hand-written ones (above its hacks) and
    the generated other-language ports (RETAIL_PORT_TITLES). Hacks and the QoL builds share the game
    codes, but WIDE is checked on retail only."""
    hand = open(os.path.join(KOTLIN, "GameTitles.kt")).read()
    hand = hand[hand.index("private val HAND"):hand.index("// Hacks")]
    ports = open(os.path.join(KOTLIN, "companion", "data", "RetailPortsGen.kt")).read()
    ports = ports[ports.index("RETAIL_PORT_TITLES"):]
    return set(re.findall(r'"([0-9a-f]{40})" to', hand + ports))


def roms(dirs):
    for d in dirs:
        for root, _, files in os.walk(d):
            for f in sorted(files):
                if f.lower().endswith(".gba"):
                    yield os.path.join(root, f)


def map_shapes(rom, groups):
    """The width x height of the first maps of the first groups, read through gMapGroups - the same
    in every language and revision, so a mapped MAP_GROUPS that gives the reference's shapes is right
    (it only ever gets one literal-pool vote, and the pixel checks never reach the connected maps)."""
    out = []
    try:
        for g in range(6):
            arr = rom.u32(groups + 4 * g)
            for n in range(8):
                hdr = rom.u32(arr + 4 * n)
                lay = rom.u32(hdr)
                out.append((rom.u32(lay), rom.u32(lay + 4)))
    except (struct.error, IndexError):
        return None
    return out


def main():
    dirs = [os.path.expanduser(d) for d in sys.argv[1:]]
    if not dirs:
        sys.exit(__doc__)
    retail = retail_sha1s()
    found = {}
    for path in roms(dirs):
        with open(path, "rb") as f:
            head = f.read(0xC0)
        code = head[0xAC:0xB0].decode("ascii", "replace")
        if code[:3] not in FAMILY:
            continue
        data = open(path, "rb").read()
        if hashlib.sha1(data).hexdigest() in retail:
            found.setdefault(zlib.crc32(data), path)   # the same dump in two dirs once
    refs = {}
    for fam, r in REFERENCES.items():
        ref = next((p for p in found.values() if open(p, "rb").read(0xC0)[0xAC:0xB0].decode() == r["code"]
                    and open(p, "rb").read(0xC0)[0xBC] == r["rev"]), None)
        if not ref:
            sys.exit(f"the {fam} reference ({r['code']} rev {r['rev']}) isn't in the dirs")
        refs[fam] = Rom(ref)
    out, report = [], []
    for crc, path in sorted(found.items(), key=lambda kv: kv[1]):
        dst = Rom(path)
        fam = FAMILY[dst.code[:3]]
        src, ref = refs[fam], REFERENCES[fam]
        addrs, votes, missing = {}, {}, []
        for slot in SLOTS:
            if dst.d == src.d:
                val, n = ref["a"][slot], "ref"
            else:
                val, n = map_literal(src, dst, ref["a"][slot])
            if val is None:
                missing.append(slot)
            addrs[slot], votes[slot] = val, n
        if addrs["MAP_GROUPS"] is not None and map_shapes(dst, addrs["MAP_GROUPS"]) != map_shapes(src, ref["a"]["MAP_GROUPS"]):
            missing.append("MAP_GROUPS (its maps don't match the reference's)")
        script = ref["script"] if dst.d == src.d else map_code(src, dst, ref["script"] | 1)
        entry = dict(file=os.path.basename(path), path=path, crc=f"{crc:08x}", code=dst.code, rev=dst.rev, family=fam,
                     addrs={k: (f"{v:#010x}" if v is not None else None) for k, v in addrs.items()}, votes=votes,
                     script=f"{script:#010x}" if script else None, missing=missing)
        report.append(entry)
        if missing:
            print(f"left out {entry['file']}: no {', '.join(missing)}")
            continue
        out.append(entry)
        print(f"{dst.code} rev {dst.rev} {crc:08x} {entry['file']}: " +
              " ".join(f"{k}={votes[k]}" for k in SLOTS))
    os.makedirs(os.path.dirname(OUT_JSON), exist_ok=True)
    json.dump(report, open(OUT_JSON, "w"), indent=1)
    lines = [
        "// Generated by scripts/gen_wide_tables.py from the user's own ROMs - don't edit by hand.",
        "// WIDE's addresses per retail release, by ROM CRC32 (pk_wide.c looks them up).",
        "#pragma once",
        "",
        "static const struct PkWideRom {",
        "    uint32_t crc;",
        "    int family;   // PK_WIDE_FAMILY_*",
        "    int32_t a[PK_WIDE_ADDRS];",
        "} PK_WIDE_ROMS[] = {",
    ]
    for e in out:
        vals = ", ".join(e["addrs"][s] for s in SLOTS)
        lines.append(f"    {{ 0x{e['crc']}, PK_WIDE_FAMILY_{e['family']}, {{ {vals} }} }},   // {e['code']} rev {e['rev']}: {e['file']}")
    lines += ["};", ""]
    open(OUT_H, "w").write("\n".join(lines))
    print(f"{len(out)} ROMs -> {os.path.relpath(OUT_H, ROOT)}; {len(report) - len(out)} left out")


if __name__ == "__main__":
    main()
