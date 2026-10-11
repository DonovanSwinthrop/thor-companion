#!/usr/bin/env python3
"""Generates PokeDaisy's display tables for a CFRU-based FireRed hack (a
binary hack on FireRed 1.0 with no source), straight from the ROM the user
owns (nothing is downloaded).

    scripts/gen_cfru_tables.py <game-key> <rom.gba>

Writes app/src/main/kotlin/.../companion/data/{SpeciesNames,SpeciesTypes,
MoveData,ItemNames}<Suffix>.kt, plus GenderRatios<Suffix>.kt and
MapSecData<Suffix>.kt for a game that asks for them (gender / map_names below).
Item descriptions aren't written: the app reads them from the player's ROM
(RomItemText, NativeConfig.itemDescs = gItems below).

CFRU keeps FireRed's code and repoints its data, so each table is found the
way NATIVE_GAIA_V3_2's icon tables were: the literal-pool words FireRed 1.0
loads gSpeciesNames, gMonIconTable, ... from now hold the hack's addresses
(compare a retail BPRE rev 0 ROM word for word). The shapes:
  - gSpeciesNames: 11-byte names, by the hack's own species ids.
  - gBaseStats: 28-byte records, type1/type2 at +6/+7 (CFRU type ids, Fairy 23).
  - move names: CFRU's 17-byte long names (gMoveNames' 13 truncate
    "Thunder Punch" to "ThunderPunch"); gBattleMoves: 12-byte records, power
    at +1, type at +2.
  - gItems: 44-byte struct Item, name inline (14), .itemId at +14 (the key:
    it can differ from the slot), description pointer at +20.
  - gBaseStats genderRatio at +0x10 (gender), and sMapNames (map_names: FireRed
    1.0's pointer table from MAPSEC 0x58, renamed in place) for a hack with a
    region of its own; its map itself is drawn from the ROM (RomRegionMap).
The sha1 check stops a table being generated from the wrong release.
"""
import hashlib
import os
import sys

from gen_expansion_tables import OUT_DIR, Rom, kstr

GAMES = {
    # Pokemon Radical Red v4.1. FireRed 1.0's literal pools say: gSpeciesNames
    # 0x094042CC (pool 0x08000144 and 39 more), gMonIconTable 0x097FE6CC,
    # gMonIconPaletteIndices 0x097FE164, sItemIconTable 0x093C8100 (those last
    # three are NATIVE_RADICAL_RED_V4_1's). gBaseStats / gItems / the move
    # tables by shape, each the copy the code references (vanilla's are still
    # in the ROM, unreferenced). Checked against a save's party (species 1331
    # Maushold knows Super Fang, 925 Crocalor Yawn, 844 Pawmo Arm Thrust) and
    # bag (58 Muscle Wing, 675 Wise Glasses, 747 Punch Glove). Moves end at
    # 1003 (Rapid Flow); past that the long-name stride runs into other text.
    "radical_red": dict(
        suffix="RadicalRed",
        label="Pokemon Radical Red v4.1",
        sha1="964f951a0fdaf209e4ea1344883ef0d557bb3a80",
        species_names=0x094042CC, species_max=1400,
        base_stats=0x097B98EC,
        move_names=0x090EEEDC, move_name_len=17, battle_moves=0x091521D0, moves_max=1003,
        items=0x093C0000, items_max=2000,
        checks=dict(species={1: "Bulbasaur", 25: "Pikachu", 277: "Treecko", 925: "Crocalor", 1331: "Maushold"},
                    types={1: (12, 3), 4: (10, 10), 35: (23, 23)},
                    moves={33: ("Tackle", 0, 40), 7: ("Fire Punch", 10, 75), 474: ("Incinerate", 10, 60),
                           825: ("Alluring Voice", 23, 80)},
                    items={1: "Master Ball", 13: "Potion", 58: "Muscle Wing", 675: "Wise Glasses", 747: "Punch Glove"}),
    ),
    # Pokemon Sword and Shield Ultimate Plus v1.2.1.2: Galar on CFRU (32 MB,
    # BPRE rev 0). FireRed 1.0's literal pools say: gSpeciesNames 0x096184B8
    # (40 refs), gBaseStats 0x099AFBEC (56), gMoveNames 0x094B277C (31 of 32;
    # 13-byte names only - no long-name table, so the game prints "Nature's Mad"
    # too), gBattleMoves 0x094BFA7C (137), gItems 0x093C0000 (15, Radical Red's
    # address). Species 1-1267 (SCORBUNNY 1105, ZACIAN 1180, CALYREX 1190); moves
    # end at 895 (Rapid Flow, the G-Max ones before it). Its unused item slots are
    # named "Free SpaceNN". sMapNames stays at FireRed's pointer table, renamed
    # in place: Postwick, Wedgehurst, ... Crown Tundra. Checked against a save's
    # party (SCORBUNNY knows Growl / Tackle) and bag (Potion, Soothe Bell, Old Rod). Its Casual /
    # Performance / Casual and Performance patches (daa2f899... / 4858cc3d... / 932323d1...) leave every
    # name, type, move and the map names as they are (Casual changes some item prices): one set of tables.
    "sword_shield": dict(
        suffix="SwordShield",
        label="Pokemon Sword and Shield Ultimate Plus v1.2.1.2",
        sha1="afe4c8e0f9c7186860fcea5f25d8318f5d7929b9",
        species_names=0x096184B8, species_max=1268,
        base_stats=0x099AFBEC,
        move_names=0x094B277C, move_name_len=13, battle_moves=0x094BFA7C, moves_max=895,
        items=0x093C0000, items_max=2000, item_skip="Free Space",
        gender=True,
        map_names=0x083F1CAC,
        checks=dict(species={1: "Bulbasaur", 25: "Pikachu", 1102: "Grookey", 1105: "Scorbunny", 1108: "Sobble",
                             1180: "Zacian", 1190: "Calyrex"},
                    types={1: (12, 3), 4: (10, 10), 35: (23, 23), 1105: (10, 10)},
                    moves={33: ("Tackle", 0, 40), 45: ("Growl", 0, 0), 7: ("Fire Punch", 10, 75),
                           894: ("Rapid Flow", 11, 10)},
                    items={1: "Master Ball", 13: "Potion", 184: "Soothe Bell", 262: "Old Rod", 550: "Scizorite"},
                    mapsecs={0x58: "Postwick", 0x59: "Wedgehurst", 0x7B: "Wild Area", 0xC3: "Crown Tundra"}),
    ),
}


# sMapNames: FireRed 1.0's 109 sections from MAPSEC_PALLET_TOWN.
FIRST_MAPSEC, MAP_SECTIONS = 0x58, 109


def header(g, what):
    return (f"package com.pokedaisy.app.companion.data\n\n"
            f"// GENERATED by scripts/gen_cfru_tables.py from the {g['label']} ROM\n"
            f"// (sha1 {g['sha1'][:8]}...) - do not hand-edit. {what}\n")


def write(name, body):
    path = os.path.join(OUT_DIR, name)
    with open(path, "w") as f:
        f.write(body)
    print("wrote", os.path.relpath(path))


def main():
    if len(sys.argv) != 3 or sys.argv[1] not in GAMES:
        sys.exit(f"usage: {sys.argv[0]} <{'|'.join(GAMES)}> <rom.gba>")
    g = GAMES[sys.argv[1]]
    data = open(sys.argv[2], "rb").read()
    sha1 = hashlib.sha1(data).hexdigest()
    if sha1 != g["sha1"]:
        sys.exit(f"sha1 {sha1} isn't {g['label']}'s {g['sha1']}")
    rom = Rom(data)
    sfx = g["suffix"]
    checks = g["checks"]

    # Species: unused slots are "?" - skip them, not stop.
    names = {}
    for i in range(1, g["species_max"]):
        n = rom.text(g["species_names"] + 11 * i, 11)
        if n and not n.startswith("?"):
            names[i] = n
    for i, n in checks["species"].items():
        assert names.get(i) == n, (i, names.get(i), n)
    types = {i: (rom.u8(g["base_stats"] + 28 * i + 6), rom.u8(g["base_stats"] + 28 * i + 7)) for i in names}
    for i, t in checks["types"].items():
        assert types[i] == t, (i, types[i], t)
    write(f"SpeciesNames{sfx}.kt", header(g, "gSpeciesNames by the hack's own species ids.\n") +
          f"val speciesNames{sfx}: Map<Int, String> = mapOf(\n" +
          "".join(f"    {i} to {kstr(n)},\n" for i, n in sorted(names.items())) + ")\n")
    write(f"SpeciesTypes{sfx}.kt", header(g, "gBaseStats type1/type2, CFRU type ids (Fairy 23).\n") +
          f"val speciesTypeData{sfx}: Map<Int, SpeciesTypes> = mapOf(\n" +
          "".join(f"    {i} to SpeciesTypes({a}, {b}),\n" for i, (a, b) in sorted(types.items())) + ")\n")

    moves = {}
    for i in range(1, g["moves_max"] + 1):
        n = rom.text(g["move_names"] + g["move_name_len"] * i, g["move_name_len"])
        if not n or n == "Placeholder" or n.startswith("Z-Move"):
            continue
        a = g["battle_moves"] + 12 * i
        moves[i] = (n, rom.u8(a + 2), rom.u8(a + 1))
    for i, m in checks["moves"].items():
        assert moves.get(i) == m, (i, moves.get(i), m)
    write(f"MoveData{sfx}.kt", header(g, "Long move names + gBattleMoves type/power.\n") +
          f"val moveData{sfx}: Map<Int, MoveInfo> = mapOf(\n" +
          "".join(f"    {i} to MoveInfo({kstr(n)}, {t}, {p}),\n" for i, (n, t, p) in sorted(moves.items())) + ")\n")

    # The array ends where 30 slots in a row aren't items.
    items = {}
    misses = 0
    for slot in range(1, g["items_max"]):
        a = g["items"] + 44 * slot
        if rom.off(a) + 44 > len(data) or misses >= 30:
            break
        iid = rom.u16(a + 14)
        n = rom.text(a, 14)
        if not n or not 0 < iid < 4000 or iid in items:
            misses += 1
            continue
        misses = 0
        if n.startswith("?") or (g.get("item_skip") and n.startswith(g["item_skip"])):
            continue
        items[iid] = n
    for i, n in checks["items"].items():
        assert items.get(i) == n, (i, items.get(i), n)
    write(f"ItemNames{sfx}.kt", header(g, "gItems names, keyed by .itemId.\n") +
          f"val itemNames{sfx}: Map<Int, String> = mapOf(\n" +
          "".join(f"    {i} to {kstr(n)},\n" for i, n in sorted(items.items())) + ")\n")
    if g.get("gender"):
        ratios = "".join("%02x" % (rom.u8(g["base_stats"] + 28 * i + 0x10) if i in names else 255)
                         for i in range(max(names) + 1))
        write(f"GenderRatios{sfx}.kt", header(g, "gBaseStats genderRatio by species id (0 always\n"
              "// male, 254 always female, 255 genderless, else female when (personality & 0xFF) < ratio).\n") +
              f"val genderRatios{sfx}: IntArray by lazy {{ unhexRatios(\"{ratios}\") }}\n")
    secs = {}
    if g.get("map_names"):
        for i in range(MAP_SECTIONS):
            p = rom.u32(g["map_names"] + 4 * i)
            n = rom.text(p, 32) if rom.is_ptr(p) else None
            if n:
                secs[FIRST_MAPSEC + i] = n
        for i, n in checks["mapsecs"].items():
            assert secs.get(i) == n, (i, secs.get(i), n)
        write(f"MapSecData{sfx}.kt", header(g, "sMapNames by map section id, region -1: the MAP\n"
              "// draws its region map and boxes from the ROM (RomRegionMap); these name it elsewhere.\n") +
              f"val mapSecData{sfx}: Map<Int, MapSecInfo> = mapOf(\n" +
              "".join(f"    {i} to MapSecInfo({kstr(n)}, -1, 0, 0, 0, 0),\n" for i, n in sorted(secs.items())) + ")\n")
    print(f"{len(names)} species, {len(moves)} moves, {len(items)} items, {len(secs)} map sections; "
          f"itemDescs = vanillaItems(0x{g['items']:08X}L)")


if __name__ == "__main__":
    main()
