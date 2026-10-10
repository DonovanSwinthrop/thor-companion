// PokeDaisy — WIDE: the overworld past the GBA's 240 px, rebuilt from the game's memory (pk_wide.c).
#pragma once

#include <stdint.h>

struct mCore;

#define PK_WIDE_W 240
#define PK_WIDE_H 160
/** Most GBA pixels drawn beside the game, per side (21:9 at 160 tall needs 67). */
#define PK_WIDE_MAX_MARGIN 96

/** The games WIDE knows (WideScreen.kt passes one; 0 = off). Retail only: the QoL builds move every address. */
enum {
    PK_WIDE_OFF = 0,
    PK_WIDE_FIRERED_REV1 = 1,   // BPRE rev 1
    PK_WIDE_EMERALD = 2,        // BPEE
};

/** A game's addresses and layout, in this order (pk_wide.c's table). */
enum {
    PK_WIDE_VMAP,              // struct BackupMapLayout { s32 width, height; u16 *map; }
    PK_WIDE_MAP_HEADER,        // gMapHeader: +0 mapLayout, +0xC connections
    PK_WIDE_MAP_GROUPS,        // gMapGroups (ROM): the connected maps' headers
    PK_WIDE_SAVE_BLOCK1_PTR,   // -> pos (s16 x, y)
    PK_WIDE_CAMERA_OFFSET,     // sFieldCameraOffset: u8 xPixelOffset, yPixelOffset, xTileOffset, yTileOffset
    PK_WIDE_OBJECT_EVENTS,     // gObjectEvents[16], 0x24 each
    PK_WIDE_SPRITES,           // gSprites[64], 0x44 each
    PK_WIDE_COORD_OFFSET,      // gSpriteCoordOffsetX, then Y (s16 each)
    PK_WIDE_SPRITE_ORDER,      // u8[64], BuildOamBuffer's order
    PK_WIDE_PRIMARY_METATILES, // 640 FireRed, 512 Emerald
    PK_WIDE_ATTR_OFFSET,       // struct Tileset's metatileAttributes: 0x14 FireRed, 0x10 Emerald
    PK_WIDE_ATTR_SIZE,         // 4 (u32, FireRed) or 2 (u16, Emerald)
    PK_WIDE_LAYER_SHIFT,       // the layer type's bits: 29 (FireRed) / 12 (Emerald)
    PK_WIDE_LAYER_MASK,        // 3 / 0xF
    PK_WIDE_BORDER_SIZED,      // 1: MapLayout.borderWidth / Height at +0x18 / +0x19 (FireRed); 0: Emerald's 2x2
    PK_WIDE_CONFIG_SIZE
};

/** Sets the running game (PK_WIDE_*); PK_WIDE_OFF or an unknown one switches WIDE off. */
void pk_wide_configure(int game);

/** The PK_WIDE_* game a ROM header's code (0xAC) and revision (0xBC) would be, if the build is retail. */
int pk_wide_game_for_header(const char* code, int revision);

/** Forgets what earlier frames left (a state load, a new core, a rewind). */
void pk_wide_reset(void);

/**
 * Emu thread, after each frame: `out` ((240 + 2 * margin) x 160) gets `frame` (the core's
 * 240 x 160) in the middle and the rebuilt overworld beside it - or, wherever that can't be
 * checked (title, battles, menus, transitions), a glow spread from the frame's own edges,
 * crossfaded. With `centre` set (headless checks), the
 * rebuild of the middle 240 x 160 goes there too, sprites included. Returns how much of the
 * strips is the map, 0..256 (the rest is the glow).
 */
int pk_wide_frame(struct mCore* core, const uint32_t* frame, uint32_t* out, int margin, uint32_t* centre);

/** How the last frame went, for logs and mgba_dump: tilemap match 0..1000, -1 if none was tried. */
int pk_wide_last_score(void);
/** 1 if the last frame's sprites came from an earlier snapshot (the RAM was mid-update). */
int pk_wide_last_torn(void);
