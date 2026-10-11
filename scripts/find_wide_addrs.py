#!/usr/bin/env python3
"""Finds WIDE's RAM addresses (pk_wide.h's PK_WIDE_* slots) in a ROM hack built from source we don't have (a
pokeemerald-expansion gcc build: no literal pools in common with retail), from headless dumps of the running game.

    scripts/find_wide_addrs.py ROM SAVE --header ADDR --save1 ADDR [--boot FILE] [--extra "press B,wait 30"]

--header is gMapHeader, --save1 gSaveBlock1Ptr (both already in the hack's NativeConfig). The game boots on the
save (native-capture/boot/default.txt unless --boot), walks one metatile right then one down, and is dumped
after each step (mgba_dump's dump / vdump). Each address is then the one that agrees with every dump:

  VMAP           { s32 width, height; u16 *map } with the layout's size + 15 / + 14 (fieldmap.c's MAP_OFFSET_W/H)
  CAMERA_OFFSET  sFieldCameraOffset { xPixelOffset, yPixelOffset, xTileOffset, yTileOffset }: the pixel offsets
                 are 8 x the tile ones (mod 256) and BG1HOFS = xPixelOffset + sHorizontalCameraPan
  PAN            sHorizontal / sVerticalCameraPan (s16 x 2): VOFS = yPixelOffset + vertical pan + 8
  SPRITES        gSprites[64] (0x44 each): the in-use entries' OamData is in the frame's OAM
  SPRITE_ORDER   sSpriteOrder (u8[64]): a permutation of 0..63
  COORD_OFFSET   gSpriteCoordOffsetX / Y (s16, s16): the player's sprite's OAM position minus its own
  OBJECT_EVENTS  gObjectEvents[16] (0x24 each): the player's currentCoords = pos + 7
  GMAIN          gMain: vblankCounter1 (+0x20) moving 1 a frame, inBattle clear

Prints them (and, for pan, its offset from CAMERA_OFFSET - pk_wide.c's PAN_OFF) for gen_wide_tables.py's HACKS.
"""
import argparse
import os
import shutil
import struct
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, ".."))
IMAGE = "pokedaisy-capture"


class Dump:
    def __init__(self, d, rom):
        self.ew = open(os.path.join(d, "ewram.bin"), "rb").read()
        self.iw = open(os.path.join(d, "iwram.bin"), "rb").read()
        self.oam = open(os.path.join(d, "oam.bin"), "rb").read()
        io = open(os.path.join(d, "iolatch.bin"), "rb").read()
        self.hofs, self.vofs = struct.unpack_from("<HH", io, 0x14)
        self.rom = rom

    def mem(self, a, n):
        if a >> 24 == 2 and (a & 0x3FFFF) + n <= len(self.ew):
            return self.ew[a & 0x3FFFF:(a & 0x3FFFF) + n]
        if a >> 24 == 3 and (a & 0x7FFF) + n <= len(self.iw):
            return self.iw[a & 0x7FFF:(a & 0x7FFF) + n]
        if a >> 24 in (8, 9) and a - 0x08000000 + n <= len(self.rom):
            return self.rom[a - 0x08000000:a - 0x08000000 + n]
        return None

    def u32(self, a):
        b = self.mem(a, 4)
        return struct.unpack("<I", b)[0] if b else None

    def regions(self):
        return [(0x02000000, self.ew), (0x03000000, self.iw)]


def capture(rom, save, boot_lines, work):
    lines = list(boot_lines) + ["wait 60"]
    for name, key in (("a", None), ("b", "RIGHT"), ("c", "DOWN")):
        if key:
            lines += [f"keys {key}", "wait 16", "keys NONE", "wait 30"]
        lines += [f"dump /work/{name}", f"vdump /work/{name}"]
    for n in "abc":
        os.makedirs(os.path.join(work, n), exist_ok=True)
    shutil.copy(rom, os.path.join(work, "rom.gba"))
    shutil.copy(save, os.path.join(work, "save.sav"))
    open(os.path.join(work, "cmds.txt"), "w").write("\n".join(lines) + "\n")
    tool = os.path.join(ROOT, "build", "wide", "mgba_dump")
    shutil.copy(tool, os.path.join(work, "mgba_dump"))
    subprocess.run(["docker", "run", "--rm", "-v", f"{work}:/work", "-w", "/work", IMAGE, "-c",
                    "./mgba_dump rom.gba save.sav < cmds.txt"], capture_output=True, check=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("rom")
    ap.add_argument("save")
    ap.add_argument("--header", type=lambda v: int(v, 0), required=True)
    ap.add_argument("--save1", type=lambda v: int(v, 0), required=True)
    ap.add_argument("--boot", default=os.path.join(ROOT, "native-capture", "boot", "default.txt"))
    ap.add_argument("--extra", default="", help="commands after the boot, comma separated")
    ap.add_argument("--keep", help="dump into this dir (and keep it)")
    ap.add_argument("--pos", type=lambda v: int(v, 0), default=0,
                    help="where SaveBlock1's pos is (Heart and Soul 4, Quetzal 0x470: pk_wide.h's SB1_POS)")
    a = ap.parse_args()
    boot = [l for l in open(a.boot).read().splitlines() if l.strip() and not l.startswith(("#", "shot"))]
    boot += [c.strip() for c in a.extra.split(",") if c.strip()]
    work = a.keep or tempfile.mkdtemp()
    capture(os.path.expanduser(a.rom), os.path.expanduser(a.save), boot, work)
    rom = open(os.path.expanduser(a.rom), "rb").read()
    D = [Dump(os.path.join(work, n), rom) for n in "abc"]
    found = {"MAP_HEADER": a.header, "SAVE_BLOCK1_PTR": a.save1}

    pos = []
    for d in D:
        sb = d.u32(a.save1)
        pos.append(struct.unpack("<hh", d.mem(sb + a.pos, 4)))
    print("pos per dump:", pos, " HOFS/VOFS:", [(d.hofs, d.vofs) for d in D])
    if pos[1] != (pos[0][0] + 1, pos[0][1]) or pos[2] != (pos[1][0], pos[1][1] + 1):
        sys.exit("the player didn't walk right then down - the boot didn't reach the field (try --extra)")

    lay = D[0].u32(a.header)
    w, h = struct.unpack("<ii", D[0].mem(lay, 8))

    def scan(test, step=4):
        out = []
        for base, buf in D[0].regions():
            for o in range(0, len(buf) - 64, step):
                if test(base + o):
                    out.append(base + o)
        return out

    def one(name, hits):
        print(f"{name}: " + (", ".join(f"{x:#010x}" for x in hits[:6]) or "NOT FOUND") + (" ..." if len(hits) > 6 else ""))
        if hits:
            found[name] = hits[0]

    one("VMAP", scan(lambda x: all(d.mem(x, 12) and struct.unpack("<iiI", d.mem(x, 12))[:2] == (w + 15, h + 14)
                                    and struct.unpack("<iiI", d.mem(x, 12))[2] >> 24 == 2 for d in D)))

    def cam(x):
        for d in D:
            b = d.mem(x, 4)
            if not b or b[0] != (b[2] * 8) & 0xFF or b[1] != (b[3] * 8) & 0xFF or b[0] != d.hofs & 0xFF:
                return False
        return D[0].mem(x, 4) != D[1].mem(x, 4) != D[2].mem(x, 4)
    one("CAMERA_OFFSET", scan(cam, step=1))
    if "CAMERA_OFFSET" in found:
        co = found["CAMERA_OFFSET"]
        pans = [off for off in range(4, 32, 2)
                if all(((d.mem(co, 2)[0] + struct.unpack("<h", d.mem(co + off, 2))[0]) & 0x1FF) == d.hofs and
                       ((d.mem(co, 2)[1] + struct.unpack("<h", d.mem(co + off + 2, 2))[0] + 8) & 0x1FF) == d.vofs
                       for d in D)]
        print("PAN_OFF:", pans[:4] or "NOT FOUND")

    shown = {D[0].oam[k * 8:k * 8 + 6] for k in range(128)}

    def sprites(x):
        hits = 0
        for i in range(64):
            e = D[0].mem(x + 0x44 * i, 0x44)
            if not e:
                return 0
            if e[0x3E] & 1:
                if e[:6] not in shown:
                    if not e[0x3E] & 4 and (e[1] & 3) != 2:
                        return 0
                else:
                    hits += 1
        return hits
    # A base shifted down by a few entries still covers every sprite in use: the real one is the highest base
    # with the most matches.
    counts = [(n, x) for x in scan(lambda x: sprites(x) >= 2) for n in [sprites(x)]]
    if counts:
        top = max(n for n, _ in counts)
        one("SPRITES", [max(x for n, x in counts if n == top)])
    else:
        one("SPRITES", [])

    one("SPRITE_ORDER", scan(lambda x: all(d.mem(x, 64) and sorted(d.mem(x, 64)) == list(range(64)) for d in D), step=1))

    # The player's sprite: gObjectEvents[0] with currentCoords = pos + 7.
    def objects(x):
        for d, p in zip(D, pos):
            e = d.mem(x, 0x24)
            if not e or not e[0] & 1 or struct.unpack_from("<hh", e, 0x10) != (p[0] + 7, p[1] + 7):
                return False
        return True
    one("OBJECT_EVENTS", scan(objects))
    if "OBJECT_EVENTS" in found and "SPRITES" in found:
        # ObjectEvent.spriteId: +4 in the decomps, +0x23 in pokeemerald-expansion (graphicsId grew to a u16).
        # The right one points every object event in use at a sprite in use, placed in the world.
        def ids_ok(off):
            for i in range(16):
                e = D[0].mem(found["OBJECT_EVENTS"] + 0x24 * i, 0x24)
                if e[0] & 1:
                    sp = D[0].mem(found["SPRITES"] + 0x44 * e[off], 0x44) if e[off] < 64 else None
                    if not sp or (sp[0x3E] & 3) != 3:
                        return False
            return True
        sid_off = next((off for off in (4, 0x23) if ids_ok(off)), None)
        print("SPRITE_ID_OFF:", hex(sid_off) if sid_off is not None else "NOT FOUND")
        sid_off = sid_off or 4
        want = []
        for d in D:
            sid = d.mem(found["OBJECT_EVENTS"] + sid_off, 1)[0]
            sp = d.mem(found["SPRITES"] + 0x44 * sid, 0x44)
            x, y, x2, y2 = struct.unpack_from("<hhhh", sp, 0x20)
            cvx, cvy = struct.unpack_from("<bb", sp, 0x28)
            ox, oy = struct.unpack_from("<H", sp, 2)[0] & 0x1FF, sp[0]
            want.append(((ox - (x + x2 + cvx) + 256) % 512 - 256, (oy - (y + y2 + cvy) + 128) % 256 - 128))
        print("  the player's sprite needs coord offsets", want)
        one("COORD_OFFSET", scan(lambda x: all(d.mem(x, 4) and struct.unpack("<hh", d.mem(x, 4)) == wv
                                               for d, wv in zip(D, want)), step=2))

    def gmain(x):
        c = [d.u32(x + 0x20) for d in D]
        return all(v is not None for v in c) and 40 < c[1] - c[0] < 120 and 40 < c[2] - c[1] < 120 and \
            not D[0].mem(x + 0x439, 1)[0] & 2 and (D[0].u32(x + 4) or 0) >> 24 == 8
    one("GMAIN", scan(gmain))

    print("\nfound:", {k: f"{v:#010x}" for k, v in found.items()})


if __name__ == "__main__":
    main()
