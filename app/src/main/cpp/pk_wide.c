// PokeDaisy — WIDE: the overworld past the GBA's 240 px, rebuilt from the game's own memory.
//
// The game keeps only a 256x256 ring of map tiles in VRAM (field_camera.c), so a wider PPU
// would show wrap-around garbage. Instead the strips beside the real frame are drawn the way
// field_camera.c's DrawMetatile and sprite.c's BuildOamBuffer would draw them:
//
//   map grid   VMap (the map + 7 metatiles of stitched connections per side); past it the
//              connected maps' own data (ROM, gMapHeader.connections), else the border
//   metatiles  the layout's primary / secondary tilesets (ROM): 8 tile entries + layer type
//   pixels     VRAM at each field BG's char base, colours from palette RAM (so animated
//              tiles, fades and tints are the game's own)
//   camera     the BG scroll registers the frame was drawn with + the ring's anchor in RAM
//   sprites    gSprites in gSpriteOrder, with subsprites; NPCs the game hid only for being off
//              screen (ObjectEvent.offScreen) are shown - their sprites stay up to date, hidden
//
// Timing: the main loop computes frame N while N-1 is on screen and the VBlank handler uploads
// it, so the RAM after frame N-1 describes the frame now drawn - kept as a snapshot. When a
// step's row redraw runs the main loop past VBlank, a snapshot is caught mid-update (pos moved,
// the ring offsets not yet): every camera candidate (both snapshots, pos +-1 metatile) is
// checked against the frame's own BG tilemaps in VRAM, and the sprite list against the frame's
// OAM. Anything that doesn't check out (battles, menu screens, transitions) fades the margins.
//
// Worked out and checked pixel for pixel headless first (scripts/widescreen_proto.py), then
// this port against the same captures (native-capture/mgba_dump's `wide` / `widecheck`).

#include "pk_wide.h"

#include <stdbool.h>
#include <string.h>

#include <mgba/core/core.h>
#include <mgba/internal/gba/gba.h>

#define MAP_OFFSET 7
#define MAX_MAP_DATA 0x2800        // MAX_MAP_DATA_SIZE, both games (u16 cells)
#define NUM_METATILES_TOTAL 1024
#define LAYER_NORMAL 0
#define LAYER_COVERED 1
#define LAYER_SPLIT 2
#define MIN_SCORE 900              // per mille of the screen's tilemap entries a camera must match
#define FADE_STEP 32               // margins fade over 8 frames

// Retail addresses: the decomp ELFs' (built QoL-patched, so mapped onto retail through the literal
// pools - only FireRed's VMap and gSaveBlock1Ptr differ from them), checked headless frame by frame.
static const int32_t GAMES[][PK_WIDE_CONFIG_SIZE] = {
    [PK_WIDE_FIRERED_REV1] = {
        [PK_WIDE_VMAP] = 0x03005040, [PK_WIDE_MAP_HEADER] = 0x02036DFC, [PK_WIDE_MAP_GROUPS] = 0x08352718,
        [PK_WIDE_SAVE_BLOCK1_PTR] = 0x03005008, [PK_WIDE_CAMERA_OFFSET] = 0x03000E90,
        [PK_WIDE_OBJECT_EVENTS] = 0x02036E38, [PK_WIDE_SPRITES] = 0x0202063C, [PK_WIDE_COORD_OFFSET] = 0x02021BC8,
        [PK_WIDE_SPRITE_ORDER] = 0x02021800, [PK_WIDE_PRIMARY_METATILES] = 640, [PK_WIDE_ATTR_OFFSET] = 0x14,
        [PK_WIDE_ATTR_SIZE] = 4, [PK_WIDE_LAYER_SHIFT] = 29, [PK_WIDE_LAYER_MASK] = 3, [PK_WIDE_BORDER_SIZED] = 1,
    },
    [PK_WIDE_EMERALD] = {
        [PK_WIDE_VMAP] = 0x03005DC0, [PK_WIDE_MAP_HEADER] = 0x02037318, [PK_WIDE_MAP_GROUPS] = 0x08486578,
        [PK_WIDE_SAVE_BLOCK1_PTR] = 0x03005D8C, [PK_WIDE_CAMERA_OFFSET] = 0x03000E20,
        [PK_WIDE_OBJECT_EVENTS] = 0x02037350, [PK_WIDE_SPRITES] = 0x02020630, [PK_WIDE_COORD_OFFSET] = 0x02021BBC,
        [PK_WIDE_SPRITE_ORDER] = 0x020217F4, [PK_WIDE_PRIMARY_METATILES] = 512, [PK_WIDE_ATTR_OFFSET] = 0x10,
        [PK_WIDE_ATTR_SIZE] = 2, [PK_WIDE_LAYER_SHIFT] = 12, [PK_WIDE_LAYER_MASK] = 0xF, [PK_WIDE_BORDER_SIZED] = 0,
    },
};

static int32_t C[PK_WIDE_CONFIG_SIZE];
static bool configured;

// --- memory -----------------------------------------------------------------------------------

static struct GBA* G;
static bool badRead;   // a pointer led outside RAM / ROM: the frame's rebuild isn't trusted

static const uint8_t* mem(uint32_t a, uint32_t n) {
    switch (a >> 24) {
    case 2:
        a &= 0x3FFFF;
        if (a + n <= 0x40000) return (const uint8_t*) G->memory.wram + a;
        break;
    case 3:
        a &= 0x7FFF;
        if (a + n <= 0x8000) return (const uint8_t*) G->memory.iwram + a;
        break;
    case 8:
    case 9:
        a -= 0x08000000;
        if ((size_t) a + n <= G->memory.romSize) return (const uint8_t*) G->memory.rom + a;
        break;
    }
    badRead = true;
    return NULL;
}

static uint32_t r8(uint32_t a) { const uint8_t* p = mem(a, 1); return p ? p[0] : 0; }
static uint32_t r16(uint32_t a) { const uint8_t* p = mem(a, 2); return p ? (uint32_t) (p[0] | p[1] << 8) : 0; }
static int32_t s16(uint32_t a) { return (int16_t) r16(a); }
static uint32_t r32(uint32_t a) {
    const uint8_t* p = mem(a, 4);
    return p ? (uint32_t) p[0] | (uint32_t) p[1] << 8 | (uint32_t) p[2] << 16 | (uint32_t) p[3] << 24 : 0;
}
static int32_t s32(uint32_t a) { return (int32_t) r32(a); }

static inline uint32_t le16(const uint8_t* p) { return (uint32_t) (p[0] | p[1] << 8); }

// --- snapshots ----------------------------------------------------------------------------------

struct Conn {
    int ox, oy;                    // VMap position of the connected map's (0, 0)
    int w, h;
    uint32_t data;                 // its map cells (ROM)
    int dir;                       // 1 SOUTH 2 NORTH 3 WEST 4 EAST
};

struct Snap {
    bool ok;
    int posX, posY, xTile, yTile;
    int vw, vh;
    int width, height;             // the map itself
    uint32_t border, primary, secondary;
    int bw, bh;
    struct Conn conn[4];
    int nconn;
    uint16_t vmap[MAX_MAP_DATA];
    uint8_t sprites[64 * 0x44];
    uint8_t order[64];
    uint8_t objects[16 * 0x24];
    int coordX, coordY;
};

static struct Snap snaps[2];
static int prev = -1;              // snaps[prev]: the RAM after the frame before this one

static void take(struct Snap* s) {
    badRead = false;
    s->ok = false;
    uint32_t sb1 = r32((uint32_t) C[PK_WIDE_SAVE_BLOCK1_PTR]);
    s->posX = s16(sb1);
    s->posY = s16(sb1 + 2);
    uint32_t co = (uint32_t) C[PK_WIDE_CAMERA_OFFSET];
    s->xTile = (int) r8(co + 2);
    s->yTile = (int) r8(co + 3);
    uint32_t vm = (uint32_t) C[PK_WIDE_VMAP];
    s->vw = s32(vm);
    s->vh = s32(vm + 4);
    if (s->vw <= 0 || s->vh <= 0 || s->vw * s->vh > MAX_MAP_DATA) return;
    const uint8_t* cells = mem(r32(vm + 8), (uint32_t) (s->vw * s->vh * 2));
    if (!cells) return;
    memcpy(s->vmap, cells, (size_t) (s->vw * s->vh * 2));

    uint32_t hdr = (uint32_t) C[PK_WIDE_MAP_HEADER];
    uint32_t layout = r32(hdr);
    s->width = s32(layout);
    s->height = s32(layout + 4);
    s->border = r32(layout + 8);
    s->primary = r32(layout + 0x10);
    s->secondary = r32(layout + 0x14);
    if (C[PK_WIDE_BORDER_SIZED]) {
        s->bw = (int) r8(layout + 0x18);
        s->bh = (int) r8(layout + 0x19);
        if (s->bw <= 0 || s->bh <= 0) return;
    }
    // Where fieldmap.c's Fill*Connection puts a connected map's cell (cx, cy) in VMap:
    // SOUTH (offset + 7 + cx, height + 7 + cy), NORTH (offset + 7 + cx, 7 - cHeight + cy),
    // WEST (7 - cWidth + cx, offset + 7 + cy), EAST (width + 7 + cx, offset + 7 + cy).
    s->nconn = 0;
    uint32_t conns = r32(hdr + 0xC);
    int count = conns ? s32(conns) : 0;
    uint32_t arr = conns ? r32(conns + 4) : 0;
    for (int i = 0; i < count && i < 8 && s->nconn < 4; i++) {
        uint32_t c = arr + 12 * (uint32_t) i;   // struct MapConnection { u8 direction; s32 offset; u8 mapGroup, mapNum; }
        int dir = (int) r8(c), off = s32(c + 4);
        if (dir < 1 || dir > 4) continue;       // DIVE / EMERGE aren't beside the map
        uint32_t chdr = r32(r32((uint32_t) C[PK_WIDE_MAP_GROUPS] + 4 * r8(c + 8)) + 4 * r8(c + 9));
        uint32_t clay = r32(chdr);
        struct Conn* k = &s->conn[s->nconn];
        k->w = s32(clay);
        k->h = s32(clay + 4);
        k->data = r32(clay + 0xC);
        k->dir = dir;
        switch (dir) {
        case 1: k->ox = off + MAP_OFFSET; k->oy = s->height + MAP_OFFSET; break;
        case 2: k->ox = off + MAP_OFFSET; k->oy = MAP_OFFSET - k->h; break;
        case 3: k->ox = MAP_OFFSET - k->w; k->oy = off + MAP_OFFSET; break;
        default: k->ox = s->width + MAP_OFFSET; k->oy = off + MAP_OFFSET; break;
        }
        if (k->w > 0 && k->h > 0 && k->w * k->h <= 0x10000 && mem(k->data, (uint32_t) (k->w * k->h * 2))) s->nconn++;
    }

    const uint8_t* p;
    if ((p = mem((uint32_t) C[PK_WIDE_SPRITES], sizeof(s->sprites)))) memcpy(s->sprites, p, sizeof(s->sprites));
    if ((p = mem((uint32_t) C[PK_WIDE_SPRITE_ORDER], sizeof(s->order)))) memcpy(s->order, p, sizeof(s->order));
    if ((p = mem((uint32_t) C[PK_WIDE_OBJECT_EVENTS], sizeof(s->objects)))) memcpy(s->objects, p, sizeof(s->objects));
    s->coordX = s16((uint32_t) C[PK_WIDE_COORD_OFFSET]);
    s->coordY = s16((uint32_t) C[PK_WIDE_COORD_OFFSET] + 2);
    s->ok = !badRead;
}

// --- the map, as DrawMetatile draws it ------------------------------------------------------------

// Metatile id -> each BG's 2x2 tile entries (BG1 top, over sprites; BG2 middle; BG3 bottom), for
// one tileset pair at a time (it only changes with the map).
static uint16_t mtTiles[NUM_METATILES_TOTAL + 1][3][4];
static uint8_t mtDone[NUM_METATILES_TOTAL + 1];
static uint32_t mtPrimary, mtSecondary;

static const uint16_t* metatile(const struct Snap* s, int mid, int bg) {
    if (s->primary != mtPrimary || s->secondary != mtSecondary) {
        memset(mtDone, 0, sizeof(mtDone));
        mtPrimary = s->primary;
        mtSecondary = s->secondary;
    }
    if (mid > NUM_METATILES_TOTAL) mid = 0;     // DrawMetatileAt's own clamp
    if (!mtDone[mid]) {
        int prim = C[PK_WIDE_PRIMARY_METATILES];
        uint32_t ts = mid < prim ? s->primary : s->secondary;
        int i = mid < prim ? mid : mid - prim;
        uint32_t attrs = r32(ts + (uint32_t) C[PK_WIDE_ATTR_OFFSET]);
        uint32_t attr = C[PK_WIDE_ATTR_SIZE] == 4 ? r32(attrs + 4 * (uint32_t) i) : r16(attrs + 2 * (uint32_t) i);
        int layer = (int) ((attr >> C[PK_WIDE_LAYER_SHIFT]) & (uint32_t) C[PK_WIDE_LAYER_MASK]);
        uint32_t tiles = r32(ts + 0xC) + 16 * (uint32_t) i;
        uint16_t lo[4], hi[4];
        for (int k = 0; k < 4; k++) {
            lo[k] = (uint16_t) r16(tiles + 2 * (uint32_t) k);
            hi[k] = (uint16_t) r16(tiles + 8 + 2 * (uint32_t) k);
        }
        for (int k = 0; k < 4; k++) {
            if (layer == LAYER_SPLIT) {
                mtTiles[mid][0][k] = hi[k]; mtTiles[mid][1][k] = 0; mtTiles[mid][2][k] = lo[k];
            } else if (layer == LAYER_COVERED) {
                mtTiles[mid][0][k] = 0; mtTiles[mid][1][k] = hi[k]; mtTiles[mid][2][k] = lo[k];
            } else {   // NORMAL; BG3 gets the game's own "garbage" entry
                mtTiles[mid][0][k] = hi[k]; mtTiles[mid][1][k] = lo[k]; mtTiles[mid][2][k] = 0x3014;
            }
        }
        mtDone[mid] = 1;
    }
    return mtTiles[mid][bg - 1];
}

// MapGridGetMetatileIdAt in VMap coordinates; past VMap, a connected map's own cell where the
// game would have used the border (it never shows those: they're past its 7-metatile copy).
static int block(const struct Snap* s, int x, int y) {
    uint32_t b = 0x3FF;
    if (x >= 0 && x < s->vw && y >= 0 && y < s->vh) {
        b = s->vmap[x + s->vw * y];
    } else {
        int lx = x - MAP_OFFSET, ly = y - MAP_OFFSET;
        for (int i = 0; i < s->nconn; i++) {
            const struct Conn* k = &s->conn[i];
            bool side = k->dir == 1 ? ly >= s->height : k->dir == 2 ? ly < 0 : k->dir == 3 ? lx < 0 : lx >= s->width;
            int cx = x - k->ox, cy = y - k->oy;
            if (side && cx >= 0 && cx < k->w && cy >= 0 && cy < k->h) {
                b = r16(k->data + 2 * (uint32_t) (cx + k->w * cy));
                break;
            }
        }
    }
    if ((b & 0x3FF) != 0x3FF) return (int) (b & 0x3FF);
    if (!C[PK_WIDE_BORDER_SIZED]) return (int) (r16(s->border + 2 * (uint32_t) (((x + 1) & 1) + (((y + 1) & 1) << 1))) & 0x3FF);
    int xp = (x - MAP_OFFSET + 8 * s->bw) % s->bw;
    int yp = (y - MAP_OFFSET + 8 * s->bh) % s->bh;
    return (int) (r16(s->border + 2 * (uint32_t) (xp + yp * s->bw)) & 0x3FF);
}

// --- what the frame was drawn with ----------------------------------------------------------------

static inline uint16_t io(int reg) { return G->memory.io[reg >> 1]; }   // as last written (write-only ones too)
static int bgPrio[4];
static uint32_t bgChar[4], bgScreen[4];
static int order3[3];   // BG1-3, front first

static void readBgs(void) {
    for (int b = 1; b <= 3; b++) {
        uint16_t cnt = io(0x08 + 2 * b);
        bgPrio[b] = cnt & 3;
        bgChar[b] = ((cnt >> 2) & 3) * 0x4000u;
        bgScreen[b] = ((cnt >> 8) & 31) * 0x800u;
    }
    int n = 0;
    for (int p = 0; p < 4; p++)
        for (int b = 1; b <= 3; b++)
            if (bgPrio[b] == p) order3[n++] = b;
}

/** The layers (WININ / WINOUT bits: BG0-3, OBJ, blend) the windows let through at screen pixel (x, y). */
static int windowLayers(int x, int y) {
    uint16_t disp = io(0);
    if (!(disp & 0xE000)) return 0x3F;
    for (int w = 0; w < 2; w++) {
        if (!(disp & (0x2000 << w))) continue;
        int l = io(0x40 + 2 * w) >> 8, r = io(0x40 + 2 * w) & 0xFF, t = io(0x44 + 2 * w) >> 8, b = io(0x44 + 2 * w) & 0xFF;
        bool inX = l <= r ? x >= l && x < r : x >= l || x < r;   // left > right wraps around
        bool inY = t <= b ? y >= t && y < b : y >= t || y < b;
        if (inX && inY) return (io(0x48) >> (8 * w)) & 0x3F;
    }
    return io(0x4A) & 0x3F;   // outside them (an OBJ window would need the sprites: not the field's)
}

/** The field's BG setup: mode 0, BG1-3 on and 256x256, and at both edges of the screen the windows
 * (the field keeps WIN0 over all of it) show BG1-3 and sprites - a Flash-dark cave or a battle
 * transition doesn't. Anything else isn't the overworld as we draw it. */
static bool fieldDisplay(void) {
    uint16_t disp = io(0);
    if ((disp & 7) != 0 || (disp & 0x0E00) != 0x0E00) return false;
    for (int b = 1; b <= 3; b++)
        if (io(0x08 + 2 * b) & 0xC000) return false;
    for (int y = 0; y < PK_WIDE_H; y += 53)
        for (int x = 0; x < PK_WIDE_W; x += PK_WIDE_W - 1)
            if ((windowLayers(x, y) & 0x1E) != 0x1E) return false;
    return true;
}

static inline signed char signed8(int d) { return (signed char) (((d + 128) & 0xFF) - 128); }

/** Per mille of the screen's BG1-3 tilemap entries (one per 8x8 tile) the rebuild at (cx, cy) has too. */
static int score(const struct Snap* s, int cx, int cy, int hofs, int vofs) {
    const uint8_t* vram = (const uint8_t*) G->video.vram;
    int hit = 0, n = 0, missLimit = 30 * 20 * 3 / 10;   // stop once it can't reach 90%
    for (int sy = 4; sy < 160; sy += 8) {
        int ry = ((vofs + sy) >> 3) & 31, my = cy + sy;
        for (int sx = 4; sx < 240; sx += 8) {
            int rx = ((hofs + sx) >> 3) & 31, mx = cx + sx;
            int mid = block(s, mx >> 4, my >> 4);
            int q = ((my >> 3) & 1) * 2 + ((mx >> 3) & 1);
            for (int b = 1; b <= 3; b++) {
                uint32_t off = bgScreen[b] + 2 * (uint32_t) (ry * 32 + rx);
                if (metatile(s, mid, b)[q] == le16(vram + off)) hit++;
                else if (--missLimit < 0) return 0;
                n++;
            }
        }
    }
    return hit * 1000 / n;
}

// --- drawing ---------------------------------------------------------------------------------------

static inline uint32_t color(uint16_t c, uint32_t alpha) {
    uint32_t v = (uint32_t) (c & 0x1F) << 3 | (uint32_t) ((c >> 5) & 0x1F) << 11 | (uint32_t) ((c >> 10) & 0x1F) << 19;
    return v | ((v >> 5) & 0x070707) | alpha;   // mGBA's mColorFrom555
}

/** One map pixel: its colour index in palette RAM (0 = the backdrop), the priority of the BG it's
 * from (4 = backdrop) and that BG (5 = backdrop: BLDCNT's bit numbering). */
static int mapPixel(const struct Snap* s, int mx, int my, int* prio, int* layer) {
    const uint8_t* vram = (const uint8_t*) G->video.vram;
    int mid = block(s, mx >> 4, my >> 4);
    int q = ((my >> 3) & 1) * 2 + ((mx >> 3) & 1);
    for (int i = 0; i < 3; i++) {
        int b = order3[i];
        uint16_t e = metatile(s, mid, b)[q];
        int px = mx & 7, py = my & 7;
        if (e & 0x400) px = 7 - px;
        if (e & 0x800) py = 7 - py;
        uint32_t off = bgChar[b] + (e & 0x3FFu) * 32 + (uint32_t) (py * 4 + px / 2);
        if (off >= 0x10000) continue;            // BG tiles can't reach OBJ VRAM
        int ci = px & 1 ? vram[off] >> 4 : vram[off] & 15;
        if (ci) {
            *prio = bgPrio[b];
            *layer = b;
            return (e >> 12) * 16 + ci;
        }
    }
    *prio = 4;
    *layer = 5;
    return 0;
}

struct Piece {
    int x, y, w, h, tile, prio, pal, mode;   // mode: OAM objMode (0 normal, 1 semi-transparent, 2 OBJ window)
    bool hflip, vflip;
};

static const uint8_t OBJ_DIMS[3][4][2] = {
    { { 8, 8 }, { 16, 16 }, { 32, 32 }, { 64, 64 } },
    { { 16, 8 }, { 32, 8 }, { 32, 16 }, { 64, 32 } },
    { { 8, 16 }, { 8, 32 }, { 16, 32 }, { 32, 64 } },
};

static struct Piece pieces[2][256];
static int npieces[2] = { 0, 0 };
static int shown = 0;   // pieces[shown]: the list drawn (the last one the frame's OAM agreed with)

/** The OAM entries the game would build if the screen were wider, front first (BuildOamBuffer +
 * AddSubspritesToOamBuffer), positions kept as s16 instead of OAM's 9 / 8 bits. */
static int buildPieces(const struct Snap* s, struct Piece* out) {
    bool offscreen[64] = { false };   // sprites the game hid only for being off screen
    for (int i = 0; i < 16; i++) {
        const uint8_t* oe = s->objects + 0x24 * i;
        uint32_t flags = (uint32_t) oe[0] | (uint32_t) oe[1] << 8;
        if ((flags & 1) && (flags & (1 << 14)) && !(flags & (1 << 13)) && oe[4] < 64) offscreen[oe[4]] = true;
    }
    int n = 0;
    for (int k = 0; k < 64 && n < 256; k++) {
        int id = s->order[k];
        if (id >= 64) continue;
        const uint8_t* sp = s->sprites + 0x44 * id;
        uint32_t fl = le16(sp + 0x3E);
        if (!(fl & 1) || ((fl & 4) && !offscreen[id])) continue;   // !inUse, or invisible for its own reasons
        uint32_t a0 = le16(sp), a1 = le16(sp + 2), a2 = le16(sp + 4);
        if ((a0 >> 8) & 3) continue;                                // affine: not on the field
        int cvx = (signed char) sp[0x28], cvy = (signed char) sp[0x29];
        int x = (int16_t) le16(sp + 0x20) + (int16_t) le16(sp + 0x24) + cvx;
        int y = (int16_t) le16(sp + 0x22) + (int16_t) le16(sp + 0x26) + cvy;
        if (fl & 2) {   // coordOffsetEnabled
            x += s->coordX;
            y += s->coordY;
        }
        struct Piece base = { .tile = (int) (a2 & 0x3FF), .prio = (int) ((a2 >> 10) & 3), .pal = (int) (a2 >> 12),
                              .mode = (int) ((a0 >> 10) & 3), .hflip = (a1 & 0x1000) != 0, .vflip = (a1 & 0x2000) != 0 };
        uint32_t tables = (uint32_t) sp[0x18] | (uint32_t) sp[0x19] << 8 | (uint32_t) sp[0x1A] << 16 | (uint32_t) sp[0x1B] << 24;
        int mode = sp[0x42] >> 6;
        uint32_t table = tables && mode ? tables + 8 * (uint32_t) (sp[0x42] & 0x3F) : 0;
        uint32_t subs = table ? r32(table + 4) : 0;
        if (!subs) {
            if ((a0 >> 14) > 2) continue;
            out[n] = base;
            out[n].x = x;
            out[n].y = y;
            out[n].w = OBJ_DIMS[a0 >> 14][a1 >> 14][0];
            out[n].h = OBJ_DIMS[a0 >> 14][a1 >> 14][1];
            n++;
            continue;
        }
        int bx = x - cvx, by = y - cvy;
        int cnt = (int) r8(table);
        for (int j = 0; j < cnt && n < 256; j++) {
            const uint8_t* sub = mem(subs + 4 * (uint32_t) j, 4);
            if (!sub) break;
            uint32_t v = le16(sub + 2);
            if ((v & 3) > 2) continue;
            int w = OBJ_DIMS[v & 3][(v >> 2) & 3][0], h = OBJ_DIMS[v & 3][(v >> 2) & 3][1];
            int sx = (signed char) sub[0], sy = (signed char) sub[1];
            if (base.hflip) sx = -(sx + w);
            if (base.vflip) sy = -(sy + h);
            out[n] = base;
            out[n].x = bx + sx;
            out[n].y = by + sy;
            out[n].w = w;
            out[n].h = h;
            out[n].tile = base.tile + (int) ((v >> 4) & 0x3FF);
            if (mode != 2) out[n].prio = (int) (v >> 14);
            n++;
        }
    }
    return n;
}

/** Every piece that lands on the real screen is in the OAM the frame was drawn with. */
static bool oamAgrees(const struct Piece* p, int n) {
    for (int i = 0; i < n; i++) {
        if (p[i].x + p[i].w <= 0 || p[i].x >= 240 || p[i].y + p[i].h <= 0 || p[i].y >= 160) continue;
        bool found = false;
        for (int k = 0; k < 128 && !found; k++) {
            uint16_t a0 = G->video.oam.raw[k * 4], a1 = G->video.oam.raw[k * 4 + 1], a2 = G->video.oam.raw[k * 4 + 2];
            if (((a0 >> 8) & 3) == 2) continue;
            found = (a1 & 0x1FF) == (p[i].x & 0x1FF) && (a0 & 0xFF) == (p[i].y & 0xFF) && (int) (a2 & 0x3FF) == p[i].tile;
        }
        if (!found) return false;
    }
    return true;
}

// The rebuild of screen columns [x0, x1), all 160 rows, into dst (stride `stride`, column x0 at dst[0]).
static uint8_t prioBuf[PK_WIDE_H][PK_WIDE_W + 2 * PK_WIDE_MAX_MARGIN];
static uint8_t layerBuf[PK_WIDE_H][PK_WIDE_W + 2 * PK_WIDE_MAX_MARGIN];
static uint16_t below[PK_WIDE_H][PK_WIDE_W + 2 * PK_WIDE_MAX_MARGIN];   // the BG pixel's BGR555, for blending
static uint8_t takenBuf[PK_WIDE_H][PK_WIDE_W + 2 * PK_WIDE_MAX_MARGIN];

/** A semi-transparent sprite over a BG: BLDALPHA's EVA / EVB, if BLDCNT has that BG as a second target. */
static uint16_t blend(uint16_t top, uint16_t bottom, int layer) {
    uint16_t cnt = io(0x50);
    if (!(cnt & (0x100 << layer))) return top;
    int eva = io(0x52) & 0x1F, evb = (io(0x52) >> 8) & 0x1F;
    if (eva > 16) eva = 16;
    if (evb > 16) evb = 16;
    uint16_t out = 0;
    for (int sh = 0; sh <= 10; sh += 5) {
        int c = (((top >> sh) & 31) * eva + ((bottom >> sh) & 31) * evb) / 16;
        out |= (uint16_t) ((c > 31 ? 31 : c) << sh);
    }
    return out;
}

static void drawRegion(const struct Snap* s, int cx, int cy, int x0, int x1, uint32_t* dst, int stride, uint32_t alpha) {
    int w = x1 - x0;
    for (int y = 0; y < PK_WIDE_H; y++) {
        for (int x = 0; x < w; x++) {
            int prio, layer;
            int ci = mapPixel(s, cx + x0 + x, cy + y, &prio, &layer);
            dst[y * stride + x] = color(G->video.palette[ci], alpha);
            prioBuf[y][x] = (uint8_t) prio;
            layerBuf[y][x] = (uint8_t) layer;
            below[y][x] = G->video.palette[ci];
            takenBuf[y][x] = 0;
        }
    }
    // The PPU's OBJ layer: per pixel the first opaque piece in OAM order wins among sprites,
    // then shows if its priority is <= the front BG's.
    if (!(io(0) & 0x1000) || !(io(0) & 0x40)) return;   // OBJ off, or 2D tile mapping
    const uint8_t* vram = (const uint8_t*) G->video.vram;
    const struct Piece* p = pieces[shown];
    for (int i = 0; i < npieces[shown]; i++) {
        if (p[i].mode == 2) continue;   // an OBJ window shape, not drawn
        if (p[i].x + p[i].w <= x0 || p[i].x >= x1 || p[i].y + p[i].h <= 0 || p[i].y >= PK_WIDE_H) continue;
        for (int yy = 0; yy < p[i].h; yy++) {
            int Y = p[i].y + yy;
            if (Y < 0 || Y >= PK_WIDE_H) continue;
            int py = p[i].vflip ? p[i].h - 1 - yy : yy;
            for (int xx = 0; xx < p[i].w; xx++) {
                int X = p[i].x + xx - x0;
                if (X < 0 || X >= w || takenBuf[Y][X]) continue;
                int px = p[i].hflip ? p[i].w - 1 - xx : xx;
                int t = p[i].tile + (py / 8) * (p[i].w / 8) + px / 8;   // 1D mapping
                uint8_t v = vram[0x10000 + ((uint32_t) (t * 32 + (py & 7) * 4 + (px & 7) / 2) & 0x7FFF)];
                int ci = px & 1 ? v >> 4 : v & 15;
                if (!ci) continue;
                takenBuf[Y][X] = 1;
                if (p[i].prio > prioBuf[Y][X]) continue;
                uint16_t c = G->video.palette[256 + p[i].pal * 16 + ci];
                if (p[i].mode == 1) c = blend(c, below[Y][X], layerBuf[Y][X]);
                dst[Y * stride + X] = color(c, alpha);
            }
        }
    }
}

// --- the frame ---------------------------------------------------------------------------------------

static int fade;                    // 0..256, the margins' brightness
static int lastScore = -1;
static int lastTorn;
static int rawMargin = -1;          // the margin raw[] was drawn for (-1 = nothing yet)
static uint32_t raw[PK_WIDE_H][2 * PK_WIDE_MAX_MARGIN];   // the last good margins, at full brightness

void pk_wide_configure(int game) {
    configured = game > PK_WIDE_OFF && game < (int) (sizeof(GAMES) / sizeof(GAMES[0]));
    if (configured) memcpy(C, GAMES[game], sizeof(C));
    mtPrimary = mtSecondary = 0;
    pk_wide_reset();
}

int pk_wide_game_for_header(const char* code, int revision) {
    if (!memcmp(code, "BPRE", 4) && revision == 1) return PK_WIDE_FIRERED_REV1;
    if (!memcmp(code, "BPEE", 4) && revision == 0) return PK_WIDE_EMERALD;
    return PK_WIDE_OFF;
}

void pk_wide_reset(void) {
    prev = -1;
    npieces[0] = npieces[1] = 0;
    shown = 0;
    fade = 0;
    rawMargin = -1;
    lastScore = -1;
}

int pk_wide_last_score(void) { return lastScore; }
int pk_wide_last_torn(void) { return lastTorn; }

static inline uint32_t dim(uint32_t c, int f) {
    if (f >= 256) return c;
    uint32_t rb = ((c & 0xFF00FF) * (uint32_t) f >> 8) & 0xFF00FF, g = ((c & 0x00FF00) * (uint32_t) f >> 8) & 0x00FF00;
    return (c & 0xFF000000) | rb | g;
}

int pk_wide_frame(struct mCore* core, const uint32_t* frame, uint32_t* out, int margin, uint32_t* centre) {
    if (margin < 0) margin = 0;
    if (margin > PK_WIDE_MAX_MARGIN) margin = PK_WIDE_MAX_MARGIN;
    int ow = PK_WIDE_W + 2 * margin;
    for (int y = 0; y < PK_WIDE_H; y++) memcpy(out + y * ow + margin, frame + y * PK_WIDE_W, PK_WIDE_W * sizeof(uint32_t));
    if (!core || core->platform(core) != mPLATFORM_GBA || !configured) {
        for (int y = 0; y < PK_WIDE_H; y++)
            for (int x = 0; x < margin; x++) out[y * ow + x] = out[y * ow + margin + PK_WIDE_W + x] = frame[y * PK_WIDE_W] & 0xFF000000;
        return 0;
    }
    G = (struct GBA*) core->board;
    uint32_t alpha = frame[0] & 0xFF000000;

    int cur = prev == 0 ? 1 : 0;
    take(&snaps[cur]);

    // The camera: what the frame was scrolled by, anchored on whichever snapshot's ring offsets
    // (and pos +-1 metatile) reproduce the frame's own tilemaps best.
    lastScore = -1;
    lastTorn = 0;
    int best = -1, bestCx = 0, bestCy = 0, bestSnap = -1;
    if (fieldDisplay()) {
        readBgs();
        int hofs = io(0x14) & 0x1FF, vofs = io(0x16) & 0x1FF;
        const int tryOrder[2] = { prev, cur };
        for (int t = 0; t < 2; t++) {
            int si = tryOrder[t];
            if (si < 0 || !snaps[si].ok) continue;
            const struct Snap* s = &snaps[si];
            for (int d = 0; d < 9; d++) {
                int dx = (int[]) { 0, -1, 1, 0, 0, -1, 1, -1, 1 }[d], dy = (int[]) { 0, 0, 0, -1, 1, -1, -1, 1, 1 }[d];
                int cx = (s->posX + dx) * 16 + signed8(hofs - s->xTile * 8);
                int cy = (s->posY + dy) * 16 + signed8(vofs - s->yTile * 8);
                int sc = score(s, cx, cy, hofs, vofs);
                if (sc > best) {
                    best = sc; bestCx = cx; bestCy = cy; bestSnap = si;
                }
                if (sc == 1000) break;
            }
            if (best == 1000) break;
        }
        lastScore = best;
    }
    bool valid = best >= MIN_SCORE;

    // Sprites: the frame shows the snapshot before it; if its list isn't what the OAM has, the
    // RAM was caught mid-update (a lag frame repeats the last picture) - keep the last good list.
    if (valid && prev >= 0 && snaps[prev].ok) {
        int other = shown ^ 1;
        npieces[other] = buildPieces(&snaps[prev], pieces[other]);
        if (oamAgrees(pieces[other], npieces[other]) || npieces[shown] == 0) shown = other;
        else lastTorn = 1;
    }

    if (valid) {
        const struct Snap* s = &snaps[bestSnap];
        if (margin > 0) {
            drawRegion(s, bestCx, bestCy, -margin, 0, &raw[0][0], 2 * PK_WIDE_MAX_MARGIN, alpha);
            drawRegion(s, bestCx, bestCy, PK_WIDE_W, PK_WIDE_W + margin, &raw[0][margin], 2 * PK_WIDE_MAX_MARGIN, alpha);
        }
        if (centre) drawRegion(s, bestCx, bestCy, 0, PK_WIDE_W, centre, PK_WIDE_W, alpha);
        rawMargin = margin;
        fade = fade + FADE_STEP > 256 ? 256 : fade + FADE_STEP;
    } else {
        fade = fade - FADE_STEP < 0 ? 0 : fade - FADE_STEP;
    }
    if (rawMargin != margin) fade = 0;   // nothing drawn at this width yet
    for (int y = 0; y < PK_WIDE_H; y++) {
        for (int x = 0; x < margin; x++) {
            out[y * ow + x] = fade ? dim(raw[y][x], fade) : alpha;
            out[y * ow + margin + PK_WIDE_W + x] = fade ? dim(raw[y][margin + x], fade) : alpha;
        }
    }
    prev = cur;
    return fade;
}
