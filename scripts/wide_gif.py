#!/usr/bin/env python3
"""Turns an mgba_dump `widerec` recording into an animated GIF, or with --sheet a contact sheet of
every Nth frame (how WIDE's transitions were looked at frame by frame).

  python3 scripts/wide_gif.py REC out.gif [--scale 3] [--every 1]
  python3 scripts/wide_gif.py REC sheet.png --sheet --every 4 [--cols 4] [--scale 2]
"""
import argparse
import struct

from PIL import Image, ImageDraw


def frames(path):
    with open(path, "rb") as f:
        w, h, n = struct.unpack("<3I", f.read(12))
        for _ in range(n):
            data = f.read(w * h * 3)
            if len(data) < w * h * 3:
                return
            yield Image.frombytes("RGB", (w, h), data)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("rec")
    ap.add_argument("out")
    ap.add_argument("--scale", type=int, default=3)
    ap.add_argument("--every", type=int, default=1, help="keep every Nth frame")
    ap.add_argument("--sheet", action="store_true", help="a contact sheet instead of an animation")
    ap.add_argument("--cols", type=int, default=4)
    ap.add_argument("--edges", action="store_true", help="mark where the GBA's own 240 px end")
    a = ap.parse_args()
    fs = [im for i, im in enumerate(frames(a.rec)) if i % a.every == 0]
    w, h = fs[0].size
    out = []
    for i, im in enumerate(fs):
        im = im.resize((w * a.scale, h * a.scale), Image.NEAREST)
        d = ImageDraw.Draw(im)
        if a.edges:
            m = (w - 240) // 2 * a.scale
            for x in (m, m + 240 * a.scale):
                d.line([(x, 0), (x, h * a.scale)], fill=(255, 0, 255))
        if a.sheet:
            d.text((4, 2), str(i * a.every), fill=(255, 0, 255))
        out.append(im)
    if a.sheet:
        cols = a.cols
        rows = (len(out) + cols - 1) // cols
        W, H = out[0].size
        sheet = Image.new("RGB", (cols * (W + 4), rows * (H + 4)), (40, 40, 40))
        for i, im in enumerate(out):
            sheet.paste(im, ((i % cols) * (W + 4), (i // cols) * (H + 4)))
        sheet.save(a.out)
    else:
        out[0].save(a.out, save_all=True, append_images=out[1:], duration=round(1000 / 60 * a.every), loop=0)


if __name__ == "__main__":
    main()
