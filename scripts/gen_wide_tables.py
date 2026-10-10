#!/usr/bin/env python3
"""WIDE (pk_wide.c) for every retail release the user owns, and the ROM hacks in HACKS: maps the English references' addresses
into each ROM and writes app/src/main/cpp/pk_wide_games.h, a table keyed by the ROM's CRC32.

    scripts/gen_wide_tables.py <roms dir> [<more roms dirs>...]

Every retail .gba under the dirs (by SHA-1: the app's GameTitles / RetailPortsGen list - hacks and the QoL
builds share the codes but aren't taken) whose game code is FireRed / LeafGreen (BPR / BPG), Emerald (BPE) or Ruby / Sapphire (AXV / AXP) gets
the English reference's addresses (FireRed rev 1, Emerald, Ruby rev 1: checked frame by frame headless) mapped
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
         "COORD_OFFSET", "COORD_OFFSET_Y", "SPRITE_ORDER", "GMAIN", "MAP_GROUPS_XOR", "NORMAL_BG3", "METATILE_SIZE", "METATILE_MASK", "PRIMARY_OVERRIDE", "PAN_OFF"]

# The references: what was mapped onto retail from the decomp ELFs and checked headless (pk_wide.c's
# history), plus ScriptContext_SetupScript for the checks' scripted warps and battles.
REFERENCES = {
    "FR": dict(code="BPRE", rev=1, script=0x08069AF8, a=dict(
        VMAP=0x03005040, MAP_HEADER=0x02036DFC, MAP_GROUPS=0x08352718, SAVE_BLOCK1_PTR=0x03005008,
        CAMERA_OFFSET=0x03000E90, OBJECT_EVENTS=0x02036E38, SPRITES=0x0202063C, COORD_OFFSET=0x02021BC8,
        COORD_OFFSET_Y=0x02021BCA, SPRITE_ORDER=0x02021800, GMAIN=0x030030F0)),
    "EM": dict(code="BPEE", rev=0, script=0x08098EF8, a=dict(
        VMAP=0x03005DC0, MAP_HEADER=0x02037318, MAP_GROUPS=0x08486578, SAVE_BLOCK1_PTR=0x03005D8C,
        CAMERA_OFFSET=0x03000E20, OBJECT_EVENTS=0x02037350, SPRITES=0x02020630, COORD_OFFSET=0x02021BBC,
        COORD_OFFSET_Y=0x02021BBE, SPRITE_ORDER=0x020217F4, GMAIN=0x030022C0)),
    # pret/pokeruby's ruby_rev1 build is byte for byte retail Ruby rev 1: its ELF's symbols, as they are.
    # SaveBlock1 is static there (no pointer), gSpriteCoordOffsetY isn't next to X.
    "RS": dict(code="AXVE", rev=1, script=0x080655D8, a=dict(
        VMAP=0x03004870, MAP_HEADER=0x0202E828, MAP_GROUPS=0x083085A0, SAVE_BLOCK1_PTR=0x02025734,
        CAMERA_OFFSET=0x03000590, OBJECT_EVENTS=0x030048A0, SPRITES=0x02020004, COORD_OFFSET=0x030024D0,
        COORD_OFFSET_Y=0x030027E0, SPRITE_ORDER=0x03002410, GMAIN=0x03001770)),
}
# The slots that aren't addresses, as retail has them.
CONSTANTS = dict(MAP_GROUPS_XOR=0, NORMAL_BG3=0x3014, METATILE_SIZE=16, METATILE_MASK=0x3FF, PRIMARY_OVERRIDE=0, PAN_OFF=8)

# ROM hacks, by SHA-1. A CFRU hack keeps FireRed rev 0's RAM and most of its code, so its addresses are mapped
# through the literal pools like a retail port's (`mapped`), then `a` overrides what the hack moved; a hack on
# its own build (pokeemerald-expansion, gcc) gives every address in `a`. `save` is its scripts/host_roms.conf name.
HACKS = [
    dict(name="Unbound v2.1.1.1", sha1="b4776b82a4c7915d0fadeaa27e013523f99dfd94", family="FR", mapped=True,
         save="unbound", a=dict(MAP_GROUPS=0x08B70498, MAP_GROUPS_XOR=0x00B749DE, NORMAL_BG3=0)),
    dict(name="Unbound v2.1.1.1 FR", sha1="0ce2a880aa097f1dce4e1db8ee513d0e82d15859", family="FR", mapped=True,
         save="unbound_fr", a=dict(MAP_GROUPS=0x08B70498, MAP_GROUPS_XOR=0x00B749DE, NORMAL_BG3=0)),
    dict(name="Radical Red v4.1", sha1="964f951a0fdaf209e4ea1344883ef0d557bb3a80", family="FR", mapped=True,
         save="radical_red", a=dict(MAP_GROUPS=0x083526A8)),
    # pokeemerald-expansion builds: scripts/find_wide_addrs.py's dumps (gMapHeader, gSaveBlock1Ptr and gMapGroups from
    # the app's NativeConfig / GuideTables), ScriptContext_SetupScript from their battle fixtures' READMEs.
    dict(name="Lazarus v2.0", sha1="7dcdc7e280bc4631487e13dd37e6e0cea04adea6", family="EX", save="lazarus_srm",
         boot=["press B", "wait 30"] * 3, script=0x0820B2B0, a=dict(
             VMAP=0x03002468, MAP_HEADER=0x0200B06C, MAP_GROUPS=0x08FAF098, SAVE_BLOCK1_PTR=0x03003664,
             CAMERA_OFFSET=0x030023F8, OBJECT_EVENTS=0x0200566C, SPRITES=0x0203B5CC, COORD_OFFSET=0x0203CAD8,
             COORD_OFFSET_Y=0x0203CADA, SPRITE_ORDER=0x0203C710, GMAIN=0x030014B8, METATILE_SIZE=24)),
    dict(name="SoulGold v1.1.4", sha1="ea5d369cc8a31cbf1cfacb7c9470ea670f08957b", family="EX", save="soulgold",
         boot=["press B", "wait 30"] * 3, script=0x0823647C, a=dict(
             VMAP=0x03002F58, MAP_HEADER=0x02003828, MAP_GROUPS=0x09587364, SAVE_BLOCK1_PTR=0x030040C4,
             CAMERA_OFFSET=0x03000CB8, OBJECT_EVENTS=0x02003314, SPRITES=0x0203BF38, COORD_OFFSET=0x0203BAB2,
             COORD_OFFSET_Y=0x0203BAB4, SPRITE_ORDER=0x0203BA70, GMAIN=0x030055C0, PAN_OFF=-4, METATILE_SIZE=24,
             METATILE_MASK=0x7FF, PRIMARY_OVERRIDE=1024)),
    # v1.2's two builds: the same RAM, 8 bytes on from v1.1.4's sprite globals; their own ROM data.
    dict(name="SoulGold v1.2", sha1="805d880ee229fb6dc3ce03d7b03baf48f0d759d0", family="EX", save="soulgold_v12",
         boot=["press B", "wait 30"] * 3, script=0x08236D5C, a=dict(
             VMAP=0x03002F58, MAP_HEADER=0x02003828, MAP_GROUPS=0x09592634, SAVE_BLOCK1_PTR=0x030040C4,
             CAMERA_OFFSET=0x03000CB8, OBJECT_EVENTS=0x02003314, SPRITES=0x0203BF40, COORD_OFFSET=0x0203BABA,
             COORD_OFFSET_Y=0x0203BABC, SPRITE_ORDER=0x0203BA78, GMAIN=0x030055C0, PAN_OFF=-4, METATILE_SIZE=24,
             METATILE_MASK=0x7FF, PRIMARY_OVERRIDE=1024)),
    dict(name="SoulGold v1.2 (second build)", sha1="5d6a036260fdbde96f85b5c1b92d0256d3aebafb", family="EX",
         save="soulgold_v12b", boot=["press B", "wait 30"] * 3, script=0x08236CC4, a=dict(
             VMAP=0x03002F58, MAP_HEADER=0x02003828, MAP_GROUPS=0x09592590, SAVE_BLOCK1_PTR=0x030040C4,
             CAMERA_OFFSET=0x03000CB8, OBJECT_EVENTS=0x02003314, SPRITES=0x0203BF40, COORD_OFFSET=0x0203BABA,
             COORD_OFFSET_Y=0x0203BABC, SPRITE_ORDER=0x0203BA78, GMAIN=0x030055C0, PAN_OFF=-4, METATILE_SIZE=24,
             METATILE_MASK=0x7FF, PRIMARY_OVERRIDE=1024)),
]
FAMILY = {"BPR": "FR", "BPG": "FR", "BPE": "EM", "AXV": "RS", "AXP": "RS"}
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
    hacks = {h["sha1"]: h for h in HACKS}
    found, hacked = {}, {}
    for path in roms(dirs):
        with open(path, "rb") as f:
            head = f.read(0xC0)
        code = head[0xAC:0xB0].decode("ascii", "replace")
        if code[:3] not in FAMILY:
            continue
        data = open(path, "rb").read()
        sha1 = hashlib.sha1(data).hexdigest()
        if sha1 in retail:
            found.setdefault(zlib.crc32(data), path)   # the same dump in two dirs once
        elif sha1 in hacks:
            hacked.setdefault(zlib.crc32(data), (path, hacks[sha1]))
    refs = {}
    for fam, r in REFERENCES.items():
        ref = next((p for p in found.values() if open(p, "rb").read(0xC0)[0xAC:0xB0].decode() == r["code"]
                    and open(p, "rb").read(0xC0)[0xBC] == r["rev"]), None)
        if not ref:
            sys.exit(f"the {fam} reference ({r['code']} rev {r['rev']}) isn't in the dirs")
        refs[fam] = Rom(ref)
    out, report = [], []
    jobs = [(crc, path, None) for crc, path in sorted(found.items(), key=lambda kv: kv[1])]
    jobs += [(crc, path, hack) for crc, (path, hack) in sorted(hacked.items(), key=lambda kv: kv[1][0])]
    for crc, path, hack in jobs:
        dst = Rom(path)
        fam = hack["family"] if hack else FAMILY[dst.code[:3]]
        src, ref = refs.get(fam), REFERENCES.get(fam)
        if fam == "RS" and dst.lang == "J":
            fam = "RS_JA"   # mapped from English Ruby, but its own engine variant (pk_wide.c's FAMILIES)
        addrs, votes, missing = {}, {}, []
        for slot in SLOTS:
            if hack and slot in hack["a"]:
                val, n = hack["a"][slot], "hack"
            elif slot in CONSTANTS:
                val, n = CONSTANTS[slot], "-"
            elif hack and not hack.get("mapped"):
                val, n = None, 0
            elif dst.d == src.d:
                val, n = ref["a"][slot], "ref"
            else:
                val, n = map_literal(src, dst, ref["a"][slot])
            if val is None:
                missing.append(slot)
            addrs[slot], votes[slot] = val, n
        if not hack and addrs["MAP_GROUPS"] is not None and map_shapes(dst, addrs["MAP_GROUPS"]) != map_shapes(src, ref["a"]["MAP_GROUPS"]):
            missing.append("MAP_GROUPS (its maps don't match the reference's)")
        if hack and "script" in hack:
            script = hack["script"]
        else:
            script = ref["script"] if dst.d == src.d else map_code(src, dst, ref["script"] | 1)
        entry = dict(file=os.path.basename(path), path=path, crc=f"{crc:08x}", code=dst.code, rev=dst.rev, family=fam,
                     addrs={k: (f"{v:#010x}" if v is not None else None) for k, v in addrs.items()}, votes=votes,
                     script=f"{script:#010x}" if script else None, missing=missing, save=hack["save"] if hack else None,
                     boot=hack.get("boot", []) if hack else [],
                     name=hack["name"] if hack else None)
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
        "// WIDE's addresses per release (retail and the hacks it knows), by ROM CRC32 (pk_wide.c looks them up).",
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
        lines.append(f"    {{ 0x{e['crc']}, PK_WIDE_FAMILY_{e['family']}, {{ {vals} }} }},   // {e['name'] or f"{e['code']} rev {e['rev']}"}: {e['file']}")
    lines += ["};", ""]
    open(OUT_H, "w").write("\n".join(lines))
    print(f"{len(out)} ROMs -> {os.path.relpath(OUT_H, ROOT)}; {len(report) - len(out)} left out")


if __name__ == "__main__":
    main()
