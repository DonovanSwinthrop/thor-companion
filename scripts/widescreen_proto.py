#!/usr/bin/env python3
"""Widescreen prototype: rebuild the overworld past the GBA's 240 px from the game's own memory.

The game only keeps a 256x256 ring of map tiles in VRAM (field_camera.c), so a wider picture
can't come from the PPU. This rebuilds it the way field_camera.c's DrawMetatile would:

  map grid   VMap (the map + 7 metatiles of stitched connections each side, EWRAM); past it the
             connected maps' own data (ROM, via gMapHeader.connections), else the border
  metatiles  gMapHeader.mapLayout -> primary / secondary tileset (ROM): 8 tile entries + attributes
  pixels     VRAM, at each field BG's char base (so animated tiles come as the game has them now)
  colours    palette RAM (fades, tints)
  camera     gSaveBlock1.pos + sFieldCameraOffset (+ the pans) - the same sums FieldUpdateBgTilemapScroll does
  sprites    gSprites in gSpriteOrder, with subsprites, from OBJ VRAM; NPCs the game hid for being off
             screen (gObjectEvents' offScreen) are shown - their sprites are kept up to date, only hidden

Input: a capture directory from native-capture/mgba_dump (`dump` + `vdump` + `shot`, see its README).
Checks the middle 240x160 of the rebuild against the real frame (captured with `layers 0x0E`, BG1-3
only, for an exact check) and writes the wide frame: the real frame in the middle, the rebuild around it.

  python3 scripts/widescreen_proto.py ROM CAPTURE_DIR [--width 284] [--out wide.png] [--bg-only]

Retail FireRed rev 1 (BPRE) and Emerald (BPEE), picked by the ROM header.
"""
import argparse
import struct
import sys

import numpy as np
from PIL import Image

# Retail addresses: the decomp ELFs' (QoL builds), mapped onto retail through the literal pools - every
# EWRAM / IWRAM symbol here is the same in both but FireRed's VMap / gSaveBlock1Ptr (0x10 lower in retail).
FIRERED_REV1 = dict(
    name="FireRed rev 1",
    VMap=0x03005040,              # struct BackupMapLayout { s32 width, height; u16 *map; }
    gMapHeader=0x02036DFC,        # +0 mapLayout*, +0xC connections*
    gMapGroups=0x08352718,        # const struct MapHeader *const *[]: the connected maps' headers
    gSaveBlock1Ptr=0x03005008,    # -> pos (s16 x, y) at +0
    sFieldCameraOffset=0x03000E90,  # u8 xPixelOffset, yPixelOffset, xTileOffset, yTileOffset
    sHorizontalCameraPan=0x03000E98,
    sVerticalCameraPan=0x03000E9A,
    gObjectEvents=0x02036E38,     # 16 x 0x24
    gSprites=0x0202063C,          # 65 x 0x44
    gSpriteCoordOffsetX=0x02021BC8,
    gSpriteCoordOffsetY=0x02021BCA,
    gSpriteOrder=0x02021800,      # u8[64], BuildOamBuffer's draw order
    primaryMetatiles=640,
    attrOffset=0x14,              # struct Tileset's metatileAttributes
    attrSize=4,                   # u32 attributes, layer type = bits 29-30
    layerShift=29,
    layerMask=3,
    border="sized",               # MapLayout.borderWidth / borderHeight at +0x18 / +0x19
)
EMERALD = dict(
    FIRERED_REV1,
    name="Emerald",
    VMap=0x03005DC0,              # gBackupMapLayout
    gMapHeader=0x02037318,
    gMapGroups=0x08486578,
    gSaveBlock1Ptr=0x03005D8C,
    sFieldCameraOffset=0x03000E20,
    sHorizontalCameraPan=0x03000E28,
    sVerticalCameraPan=0x03000E2A,
    gObjectEvents=0x02037350,
    gSprites=0x02020630,
    gSpriteCoordOffsetX=0x02021BBC,
    gSpriteCoordOffsetY=0x02021BBE,
    gSpriteOrder=0x020217F4,      # sSpriteOrder
    primaryMetatiles=512,
    attrOffset=0x10,
    attrSize=2,                   # u16 attributes, layer type = bits 12-15
    layerShift=12,
    layerMask=0xF,
    border="2x2",                 # border[((x + 1) & 1) + (((y + 1) & 1) << 1)]
)
GAMES = {b"BPRE": FIRERED_REV1, b"BPEE": EMERALD}

NUM_METATILES_TOTAL = 1024
MAP_OFFSET = 7
LAYER_NORMAL, LAYER_COVERED, LAYER_SPLIT = 0, 1, 2

# OBJ shape/size -> (w, h) in pixels
OBJ_DIMS = {
    (0, 0): (8, 8), (0, 1): (16, 16), (0, 2): (32, 32), (0, 3): (64, 64),
    (1, 0): (16, 8), (1, 1): (32, 8), (1, 2): (32, 16), (1, 3): (64, 32),
    (2, 0): (8, 16), (2, 1): (8, 32), (2, 2): (16, 32), (2, 3): (32, 64),
}


class Mem:
    def __init__(self, rom, cap, ram=None):
        rd = lambda n: open(f"{cap}/{n}", "rb").read()
        rr = lambda n: open(f"{ram or cap}/{n}", "rb").read()
        self.regions = [
            (0x08000000, rom),
            (0x02000000, rr("ewram.bin")),
            (0x03000000, rr("iwram.bin")),
            (0x06000000, rd("vram.bin")),
            (0x05000000, rd("pal.bin")),
            (0x04000000, rd("io.bin")),
            (0x07000000, rd("oam.bin")),
        ]
        # the I/O registers as last written: the BG scroll offsets the frame was drawn with
        # (write-only, so io.bin has open bus there); vdump's iolatch.bin, mGBA's memory.io
        try:
            self.latch = rd("iolatch.bin")
        except FileNotFoundError:
            self.latch = None

    def bytes(self, addr, n):
        for base, data in self.regions:
            if base <= addr and addr + n <= base + len(data):
                return data[addr - base:addr - base + n]
        raise ValueError(f"unmapped read {addr:08x}+{n}")

    def u8(self, a): return self.bytes(a, 1)[0]
    def u16(self, a): return struct.unpack("<H", self.bytes(a, 2))[0]
    def s16(self, a): return struct.unpack("<h", self.bytes(a, 2))[0]
    def u32(self, a): return struct.unpack("<I", self.bytes(a, 4))[0]
    def s32(self, a): return struct.unpack("<i", self.bytes(a, 4))[0]


def rgb(c):
    """BGR555 -> RGB888 the way mGBA's renderer widens it."""
    r, g, b = c & 31, (c >> 5) & 31, (c >> 10) & 31
    return ((r << 3) | (r >> 2), (g << 3) | (g >> 2), (b << 3) | (b >> 2))


class Field:
    def __init__(self, m, cfg):
        self.m, self.cfg = m, cfg
        self.vw, self.vh, self.vmap = m.s32(cfg["VMap"]), m.s32(cfg["VMap"] + 4), m.u32(cfg["VMap"] + 8)
        layout = m.u32(cfg["gMapHeader"])
        self.layout = layout
        self.border = m.u32(layout + 8)
        self.primary, self.secondary = m.u32(layout + 0x10), m.u32(layout + 0x14)
        self.width, self.height = m.s32(layout), m.s32(layout + 4)
        if cfg["border"] == "sized":
            self.bw, self.bh = m.u8(layout + 0x18), m.u8(layout + 0x19)
        self.connections = self.read_connections(m.u32(cfg["gMapHeader"] + 0xC))
        sb1 = m.u32(cfg["gSaveBlock1Ptr"])
        self.pos = (m.s16(sb1), m.s16(sb1 + 2))
        co = cfg["sFieldCameraOffset"]
        self.xpix, self.ypix, self.xtile, self.ytile = (m.u8(co + i) for i in range(4))
        self.hpan, self.vpan = m.s16(cfg["sHorizontalCameraPan"]), m.s16(cfg["sVerticalCameraPan"])
        # BG1-3: char base, screen base, priority (BGxCNT at 0x08 + 2x)
        self.bg = {}
        for b in (1, 2, 3):
            cnt = m.u16(0x04000008 + 2 * b)
            self.bg[b] = dict(prio=cnt & 3, char=0x06000000 + ((cnt >> 2) & 3) * 0x4000,
                              screen=0x06000000 + ((cnt >> 8) & 31) * 0x800)
        self.pal = [rgb(m.u16(0x05000000 + 2 * i)) for i in range(512)]
        self.vram = m.bytes(0x06000000, 0x18000)
        self.latch = m.latch
        self._mt = {}

    # --- what the game draws ---------------------------------------------------------------
    def read_connections(self, conns):
        """[(direction, VMap x / y of the connected map's (0, 0), its width, height, map data)].

        Where field_map.c's Fill*Connection copies a connected map's cell (cx, cy) to in VMap:
        SOUTH (offset + 7 + cx, height + 7 + cy), NORTH (offset + 7 + cx, 7 - cHeight + cy),
        WEST (7 - cWidth + cx, offset + 7 + cy), EAST (width + 7 + cx, offset + 7 + cy)."""
        m, res = self.m, []
        if not conns:
            return res
        count, arr = m.s32(conns), m.u32(conns + 4)
        for i in range(count):
            c = arr + 12 * i  # struct MapConnection { u8 direction; s32 offset; u8 mapGroup, mapNum; }
            d, off, grp, num = m.u8(c), m.s32(c + 4), m.u8(c + 8), m.u8(c + 9)
            if d not in (1, 2, 3, 4):  # SOUTH NORTH WEST EAST (DIVE / EMERGE aren't beside us)
                continue
            hdr = m.u32(m.u32(self.cfg["gMapGroups"] + 4 * grp) + 4 * num)
            lay = m.u32(hdr)
            cw, ch, data = m.s32(lay), m.s32(lay + 4), m.u32(lay + 0xC)
            ox, oy = {1: (off + MAP_OFFSET, self.height + MAP_OFFSET),
                      2: (off + MAP_OFFSET, MAP_OFFSET - ch),
                      3: (MAP_OFFSET - cw, off + MAP_OFFSET),
                      4: (self.width + MAP_OFFSET, off + MAP_OFFSET)}[d]
            res.append((d, ox, oy, cw, ch, data))
        return res

    def connected_block(self, x, y):
        """The block a connected map has at VMap (x, y), past what the game copied into VMap
        (MAP_OFFSET = 7 metatiles of it); None where no connected map is. Only the side the cell
        is on counts (a north map can't show beside the map), like the game's strips."""
        lx, ly = x - MAP_OFFSET, y - MAP_OFFSET
        for d, ox, oy, cw, ch, data in self.connections:
            side = {1: ly >= self.height, 2: ly < 0, 3: lx < 0, 4: lx >= self.width}[d]
            cx, cy = x - ox, y - oy
            if side and 0 <= cx < cw and 0 <= cy < ch:
                return self.m.u16(data + 2 * (cx + cw * cy))
        return None

    def block(self, x, y):
        """MapGridGetMetatileIdAt: VMap, else - where the game would use the border - a connected
        map's own cell (the game never shows those: they're past its 7-metatile copy), else the border."""
        b = None
        if 0 <= x < self.vw and 0 <= y < self.vh:
            b = self.m.u16(self.vmap + 2 * (x + self.vw * y))
        else:
            b = self.connected_block(x, y)
        if b is not None and (b & 0x3FF) != 0x3FF:
            return b & 0x3FF
        if self.cfg["border"] == "2x2":
            return self.m.u16(self.border + 2 * (((x + 1) & 1) + (((y + 1) & 1) << 1))) & 0x3FF
        xp = (x - MAP_OFFSET + 8 * self.bw) % self.bw
        yp = (y - MAP_OFFSET + 8 * self.bh) % self.bh
        return self.m.u16(self.border + 2 * (xp + yp * self.bw)) & 0x3FF

    def metatile(self, mid):
        """{hardware BG: its 2x2 tile entries} the way DrawMetatile fills the tilemaps: BG1 is the
        top layer (over sprites), BG2 the middle, BG3 the bottom. (FireRed's names are misleading:
        its gBGTilemapBuffers2 is BG1 and gBGTilemapBuffers1 BG2 - overworld.c's SetBgTilemapBuffer.)"""
        if mid in self._mt:
            return self._mt[mid]
        cfg = self.cfg
        prim = cfg["primaryMetatiles"]
        ts, i = (self.primary, mid) if mid < prim else (self.secondary, mid - prim)
        attrs = self.m.u32(ts + cfg["attrOffset"])
        attr = self.m.u32(attrs + 4 * i) if cfg["attrSize"] == 4 else self.m.u16(attrs + 2 * i)
        layer = (attr >> cfg["layerShift"]) & cfg["layerMask"]
        if mid > NUM_METATILES_TOTAL:
            ts, i = self.primary, 0
        tiles = [self.m.u16(self.m.u32(ts + 0xC) + 16 * i + 2 * k) for k in range(8)]
        lo, hi, zero = tiles[:4], tiles[4:], [0] * 4
        if layer == LAYER_SPLIT:
            r = {3: lo, 2: zero, 1: hi}
        elif layer == LAYER_COVERED:
            r = {3: lo, 2: hi, 1: zero}
        else:  # NORMAL (and anything else, as the switch's fallthrough leaves the buffers alone)
            r = {3: [0x3014] * 4, 2: lo, 1: hi}
        self._mt[mid] = r
        return r

    def camera(self, others=()):
        """(map pixel x, y in VMap space at the screen's top-left corner, how sure: 0..1).

        The tile ring's (xTileOffset, yTileOffset) holds metatile pos, and the BGs scroll by
        HOFS / VOFS (FieldUpdateBgTilemapScroll: xPixelOffset + hpan, yPixelOffset + vpan + 8). The
        offset into the ring is signed: a step right / down moves pos at its start, so the screen's
        edge sits up to 15 px before metatile pos until the step ends.

        With the latched scroll registers (what the frame was drawn with) the camera is checked
        against the frame's own BG tilemaps in VRAM: RAM can be caught mid-update when a step's row
        redraw runs the main loop past VBlank (pos already moved, the tile offsets not yet), so
        every snapshot given (`others`: Fields of the other RAM snapshot) and pos +-1 metatile
        compete, and the one whose tile entries match the most wins."""
        signed = lambda d: ((d + 128) & 0xFF) - 128
        if self.latch is None:
            hofs = self.xpix + self.hpan
            vofs = self.ypix + self.vpan + 8
            return (self.pos[0] * 16 + signed(hofs - self.xtile * 8),
                    self.pos[1] * 16 + signed(vofs - self.ytile * 8)), None
        hofs = struct.unpack_from("<H", self.latch, 0x14)[0] & 0x1FF  # BG1HOFS (BG2 / BG3 are the same)
        vofs = struct.unpack_from("<H", self.latch, 0x16)[0] & 0x1FF
        cands = []
        for f in (self, *others):
            for dx in (0, -1, 1):
                for dy in (0, -1, 1):
                    c = ((f.pos[0] + dx) * 16 + signed(hofs - f.xtile * 8), (f.pos[1] + dy) * 16 + signed(vofs - f.ytile * 8))
                    if c not in cands:
                        cands.append(c)
        best = max(cands, key=lambda c: self.tilemap_score(c, hofs, vofs))  # first wins a tie: this RAM, as is
        return best, self.tilemap_score(best, hofs, vofs)

    def tilemap_score(self, cam, hofs, vofs):
        """Share of the screen's BG1-3 tilemap entries in VRAM that the rebuild at `cam` has too
        (one sample per 8x8 tile; door animations and the like miss a few)."""
        hit = n = 0
        for sy in range(4, 160, 8):
            ry = ((vofs + sy) >> 3) & 31
            my = cam[1] + sy
            for sx in range(4, 240, 8):
                rx = ((hofs + sx) >> 3) & 31
                mx = cam[0] + sx
                mt = self.metatile(self.block(mx >> 4, my >> 4))
                q = ((my >> 3) & 1) * 2 + ((mx >> 3) & 1)
                for b in (1, 2, 3):
                    off = self.bg[b]["screen"] - 0x06000000 + 2 * (ry * 32 + rx)
                    hit += mt[b][q] == self.vram[off] | (self.vram[off + 1] << 8)
                    n += 1
        return hit / n

    def tile_pixel(self, b, entry, px, py):
        """Palette index (0 = transparent) of 4bpp tile `entry` at (px, py)."""
        if entry & 0x400:
            px = 7 - px
        if entry & 0x800:
            py = 7 - py
        off = self.bg[b]["char"] - 0x06000000 + (entry & 0x3FF) * 32 + py * 4 + px // 2
        if off >= 0x10000:  # BG tiles can't reach OBJ VRAM
            return 0
        v = self.vram[off]
        ci = (v >> 4) if px & 1 else (v & 15)
        return ((entry >> 12) * 16 + ci) if ci else 0

    def render_bgs(self, x0, y0, w, h):
        """BG1-3 composited by priority over the backdrop: (h, w, 3) uint8, plus each pixel's
        top BG priority (4 = backdrop) for putting sprites in between."""
        out = np.zeros((h, w, 3), np.uint8)
        prio = np.full((h, w), 4, np.uint8)
        order = sorted((1, 2, 3), key=lambda b: (self.bg[b]["prio"], b))  # front first
        backdrop = self.pal[0]
        for y in range(h):
            my = y0 + y
            for x in range(w):
                mx = x0 + x
                mt = self.metatile(self.block(mx >> 4, my >> 4))
                q = ((my >> 3) & 1) * 2 + ((mx >> 3) & 1)
                for b in order:
                    ci = self.tile_pixel(b, mt[b][q], mx & 7, my & 7)
                    if ci:
                        out[y, x] = self.pal[ci]
                        prio[y, x] = self.bg[b]["prio"]
                        break
                else:
                    out[y, x] = backdrop
        return out, prio

    # --- sprites ----------------------------------------------------------------------------
    def oam_pieces(self):
        """The OAM entries the game would build if the screen were wider, front first.

        BuildOamBuffer's own rules (gSpriteOrder, AddSubspritesToOamBuffer), with two changes:
        positions are kept as s16 instead of OAM's 9 / 8 bits, and an object event's sprite the
        game hid only for being off screen (ObjectEvent.offScreen: more than 16 px out) is shown.
        Object events load ~2 metatiles past each edge, which covers a 16:9 screen's margins."""
        m, cfg = self.m, self.cfg
        cox, coy = m.s16(cfg["gSpriteCoordOffsetX"]), m.s16(cfg["gSpriteCoordOffsetY"])
        offscreen = set()  # sprite ids hidden only for being off screen
        for i in range(16):
            oe = cfg["gObjectEvents"] + 0x24 * i
            flags = m.u32(oe)
            if flags & 1 and flags & (1 << 14) and not flags & (1 << 13):  # active, offScreen, !invisible
                offscreen.add(m.u8(oe + 4))
        pieces = []
        for k in range(64):
            sid = m.u8(cfg["gSpriteOrder"] + k)
            sp = cfg["gSprites"] + 0x44 * sid
            fl = m.u16(sp + 0x3E)
            if not fl & 1 or (fl & 4 and sid not in offscreen):  # !inUse, or invisible for its own reasons
                continue
            a0, a1, a2 = m.u16(sp), m.u16(sp + 2), m.u16(sp + 4)
            if (a0 >> 8) & 3:  # affine: none on the field we care about yet
                continue
            cvx = struct.unpack("b", m.bytes(sp + 0x28, 1))[0]
            cvy = struct.unpack("b", m.bytes(sp + 0x29, 1))[0]
            x = m.s16(sp + 0x20) + m.s16(sp + 0x24) + cvx
            y = m.s16(sp + 0x22) + m.s16(sp + 0x26) + cvy
            if fl & 2:  # coordOffsetEnabled
                x += cox
                y += coy
            base = dict(tile=a2 & 0x3FF, prio=(a2 >> 10) & 3, pal=a2 >> 12, hflip=bool(a1 & 0x1000), vflip=bool(a1 & 0x2000))
            tables, mode = m.u32(sp + 0x18), m.u8(sp + 0x42) >> 6
            table = tables + 8 * (m.u8(sp + 0x42) & 0x3F) if tables and mode else 0
            subs = m.u32(table + 4) if table else 0
            if not subs:
                pieces.append(dict(base, x=x, y=y, dims=OBJ_DIMS[(a0 >> 14, a1 >> 14)]))
                continue
            bx, by = x - cvx, y - cvy
            for j in range(m.u8(table)):
                sx, sy = struct.unpack("bb", m.bytes(subs + 4 * j, 2))
                v = m.u16(subs + 4 * j + 2)
                dims = OBJ_DIMS[(v & 3, (v >> 2) & 3)]
                if base["hflip"]:
                    sx = -(sx + dims[0])
                if base["vflip"]:
                    sy = -(sy + dims[1])
                pieces.append(dict(base, x=bx + sx, y=by + sy, dims=dims, tile=base["tile"] + ((v >> 4) & 0x3FF),
                                   prio=base["prio"] if mode == 2 else v >> 14))
        return pieces

    def oam_agrees(self, pieces):
        """Whether every piece that lands on the real screen is in the OAM the frame was drawn with
        (same 9-bit x / 8-bit y / tile). It isn't when the RAM was caught mid-update (a lag frame:
        the game shows its previous sprites again) - then keep the last list that agreed."""
        oam = self.m.bytes(0x07000000, 0x400)
        real = set()
        for i in range(128):
            a0, a1, a2 = struct.unpack_from("<3H", oam, 8 * i)
            if (a0 >> 8) & 3 != 2:  # not hidden
                real.add((a1 & 0x1FF, a0 & 0xFF, a2 & 0x3FF))
        for p in pieces:
            w_, h_ = p["dims"]
            if p["x"] + w_ > 0 and p["x"] < 240 and p["y"] + h_ > 0 and p["y"] < 160:
                if (p["x"] & 0x1FF, p["y"] & 0xFF, p["tile"]) not in real:
                    return False
        return True

    def draw_sprites(self, img, prio, pieces, ox, oy=0):
        """The PPU's OBJ layer: per pixel the first opaque piece in OAM order wins among sprites,
        then shows if its priority is <= the front BG's. img's (0, 0) is screen (-ox, -oy)."""
        h, w, _ = img.shape
        taken = np.zeros((h, w), bool)
        for s in pieces:
            sw, sh = s["dims"]
            for yy in range(sh):
                Y = s["y"] + yy + oy
                if not 0 <= Y < h:
                    continue
                py = sh - 1 - yy if s["vflip"] else yy
                for xx in range(sw):
                    X = s["x"] + xx + ox
                    if not 0 <= X < w or taken[Y, X]:
                        continue
                    px = sw - 1 - xx if s["hflip"] else xx
                    t = s["tile"] + (py // 8) * (sw // 8) + px // 8  # 1D mapping
                    v = self.vram[0x10000 + (t * 32 + (py & 7) * 4 + (px & 7) // 2) % 0x8000]
                    ci = (v >> 4) if px & 1 else (v & 15)
                    if ci:
                        taken[Y, X] = True
                        if s["prio"] <= prio[Y, X]:
                            img[Y, X] = self.pal[256 + s["pal"] * 16 + ci]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("rom")
    ap.add_argument("cap")
    ap.add_argument("--ram", help="the PREVIOUS frame's capture: its RAM is what the frame in CAP shows (the main loop\n"
                                  "computes frame N while N-1 is on screen and the VBlank handler uploads it); CAP's\n"
                                  "own RAM then only helps find the camera")
    ap.add_argument("--width", type=int, default=284, help="output width in GBA pixels (Thor: 1920/6.75 = 284)")
    ap.add_argument("--out")
    ap.add_argument("--bg-only", action="store_true", help="no sprites (for a capture taken with `layers 0x0E`)")
    ap.add_argument("--no-frame", action="store_true", help="the rebuild everywhere, not the real frame in the middle")
    a = ap.parse_args()

    rom = open(a.rom, "rb").read()
    cfg = GAMES[rom[0xAC:0xB0]]
    f = Field(Mem(rom, a.cap, a.ram), cfg)
    (cx, cy), sure = f.camera([Field(Mem(rom, a.cap), cfg)] if a.ram else [])
    margin = (a.width - 240) // 2
    wide, prio = f.render_bgs(cx - margin, cy, a.width, 160)
    if not a.bg_only:
        pieces = f.oam_pieces()
        if not f.oam_agrees(pieces):
            print("sprites: the RAM was caught mid-update (lag frame) - the app keeps the last list that agreed")
        f.draw_sprites(wide, prio, pieces, margin)

    shot = np.array(Image.open(f"{a.cap}/shot.ppm").convert("RGB"))
    diff = np.any(wide[:, margin:margin + 240] != shot, axis=2)
    print(f"pos={f.pos} tileOff=({f.xtile},{f.ytile}) pixOff=({f.xpix},{f.ypix}) pan=({f.hpan},{f.vpan}) "
          f"cam=({cx},{cy}) tilemap match={sure} vmap={f.vw}x{f.vh}  centre mismatch: {diff.sum()} / {diff.size} px")
    if not a.no_frame:
        wide[:, margin:margin + 240] = shot
    if a.out:
        Image.fromarray(wide).save(a.out)
    return 0 if diff.sum() == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
