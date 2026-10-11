#!/usr/bin/env python3
"""Rewrites a Gen 3 save for a build whose SaveBlock1 is smaller - how the Super Mariomon v1.3 /
v1.2.2 / v1.2.1 fixtures boot the user's v1.5.1 save (v1.5 added a Medicine pocket at the end of
SaveBlock1: 0x3F08 bytes, 0x3E28 before; everything in front of it is laid out the same).

    scripts/trim_save_block1.py <in.sav> <out.sav> <sb1 size, e.g. 0x3E28>

These builds use 4084-byte sectors (pokeemerald-expansion's SECTOR_DATA_SIZE; footer at 0xFF4:
id, checksum, signature 0x08012025, counter), so SaveBlock1's last sector (id 4) holds
size - 3 * 4084 bytes. The bytes past the new size are zeroed and that sector's checksum is
redone over the new length, in both save slots. The input is never written to.
"""
import struct
import sys

SECTOR = 0x1000
DATA = 0xFF4
SIGNATURE = 0x08012025


def main():
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    src, dst, size = sys.argv[1], sys.argv[2], int(sys.argv[3], 0)
    b = bytearray(open(src, "rb").read())
    chunk = size - 3 * DATA
    if not 0 < chunk <= DATA:
        sys.exit(f"SaveBlock1 size {size:#x} doesn't end in its fourth sector")
    done = 0
    for slot in range(2):
        for s in range(14):
            o = slot * 14 * SECTOR + s * SECTOR
            sid, _, sig, _ = struct.unpack_from("<HHII", b, o + DATA)
            if sig != SIGNATURE or sid != 4:
                continue
            b[o + chunk:o + DATA] = bytes(DATA - chunk)
            total = sum(struct.unpack_from(f"<{chunk // 4}I", b, o)) & 0xFFFFFFFF
            struct.pack_into("<H", b, o + DATA + 2, ((total >> 16) + total) & 0xFFFF)
            done += 1
    if not done:
        sys.exit("no SaveBlock1 sector found")
    open(dst, "wb").write(b)
    print(f"wrote {dst}: {done} sector(s) cut to {chunk:#x} bytes")


if __name__ == "__main__":
    main()
