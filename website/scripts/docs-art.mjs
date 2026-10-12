// The docs' invented art, which ui-preview's docs render (gradle docsShots) draws instead of any game's:
//   - each creature (src/lib/creatures.js) as a GBA-style party icon sheet: 32x64, two 32x32 frames, the
//     second a pixel higher (the party screen's bob)  -> ../docs/manual/art/creatures/<id>.png
//   - a region map of an invented island, 240x160 like the games' (the MAP tab's picture)
//                                                   -> ../docs/manual/art/regionmap.png
//   node scripts/docs-art.mjs [--sheet out.png]     (--sheet: the creatures at 8x too, to look at them)
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { CREATURES } from '../src/lib/creatures.js';
import { encodePng } from './png.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const outDir = join(here, '../../docs/manual/art/creatures');

function rgba(hex) {
  const n = parseInt(hex.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255, 255];
}

/** The creature's pixels on a w x h canvas, centred across, its feet [floor] rows from the bottom. */
function paint(c, w, h, floor, lift = 0) {
  const px = new Uint8Array(w * h * 4);
  const cw = Math.max(...c.rows.map((r) => r.length));
  const x0 = Math.floor((w - cw) / 2);
  const y0 = h - floor - c.rows.length - lift;
  c.rows.forEach((row, y) => {
    for (let x = 0; x < row.length; x++) {
      const ch = row[x];
      if (ch === '.') continue;
      const col = c.pal[ch];
      if (!col) throw new Error(`${c.name}: no colour for '${ch}'`);
      const i = ((y0 + y) * w + x0 + x) * 4;
      px.set(rgba(col), i);
    }
  });
  return px;
}

mkdirSync(outDir, { recursive: true });
for (const [id, c] of Object.entries(CREATURES)) {
  const widths = new Set(c.rows.map((r) => r.length));
  if (widths.size > 1) console.warn(`${id}: rows of different widths ${[...widths]}`);
  const sheet = new Uint8Array(32 * 64 * 4);
  sheet.set(paint(c, 32, 32, 2), 0);
  sheet.set(paint(c, 32, 32, 2, 1), 32 * 32 * 4);
  writeFileSync(join(outDir, `${id}.png`), encodePng(32, 64, sheet));
}
console.log(`wrote ${Object.keys(CREATURES).length} icon sheets to ${outDir}`);

writeFileSync(join(outDir, '../regionmap.png'), encodePng(240, 160, regionMap()));
console.log('wrote the region map');

/**
 * Daisy Isle: an island of our own on the games' 30x20 grid of 8 px tiles. Towns are tiles, routes run
 * along tile centres. The town at tile (8, 15) is where the docs save stands (the MAP tab's cursor).
 */
function regionMap() {
  const W = 240, H = 160;
  const px = new Uint8Array(W * H * 4);
  const set = (x, y, hex) => { if (x >= 0 && y >= 0 && x < W && y < H) px.set(rgba(hex), (y * W + x) * 4); };
  const blobs = [[70, 110, 46], [110, 70, 52], [165, 80, 48], [150, 125, 34], [60, 60, 30], [195, 120, 26]];
  const land = (x, y) => blobs.some(([cx, cy, r]) => {
    const wob = 4 * Math.sin(x * 0.21 + cy) + 3 * Math.cos(y * 0.17 + cx);
    return (x - cx) ** 2 + (y - cy) ** 2 < (r + wob) ** 2;
  });
  const hills = (x, y) => (x - 92) ** 2 / 900 + (y - 48) ** 2 / 300 < 1;
  const woods = (x, y) => (x - 182) ** 2 + (y - 70) ** 2 < 420 || (x - 58) ** 2 + (y - 78) ** 2 < 180;
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    if (!land(x, y)) {
      const near = [[2, 0], [-2, 0], [0, 2], [0, -2]].some(([dx, dy]) => land(x + dx, y + dy));
      set(x, y, near ? '#7cc0f0' : (x * 7 + y * 13) % 53 === 0 ? '#9cd4f8' : '#4a90d8');
      continue;
    }
    const edge = [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => !land(x + dx, y + dy));
    if (edge) { set(x, y, '#2a5a30'); continue; }
    const beach = [[3, 0], [-3, 0], [0, 3], [0, -3]].some(([dx, dy]) => !land(x + dx, y + dy));
    if (beach) { set(x, y, '#ecdca0'); continue; }
    if (hills(x, y)) { set(x, y, (x + y) % 6 < 2 ? '#b08850' : '#c8a068'); continue; }
    if (woods(x, y)) { set(x, y, (x * 3 + y) % 5 === 0 ? '#2f7a34' : '#3f9a44'); continue; }
    set(x, y, (x * 5 + y * 3) % 17 === 0 ? '#90d868' : '#78c850');
  }
  // Routes: tile-centre polylines in the road colour, 2 px wide.
  const c = (t) => t * 8 + 3;
  const routes = [
    [[8, 15], [8, 10]], [[8, 10], [14, 10], [14, 6]], [[14, 10], [20, 10]], [[20, 10], [20, 15], [17, 15]],
    [[14, 6], [21, 6], [21, 10]], [[20, 15], [24, 15]], [[8, 10], [6, 10], [6, 7]],
  ];
  for (const r of routes) for (let i = 1; i < r.length; i++) {
    const [ax, ay] = r[i - 1], [bx, by] = r[i];
    for (let x = Math.min(c(ax), c(bx)); x <= Math.max(c(ax), c(bx)) + 1; x++)
      for (let y = Math.min(c(ay), c(by)); y <= Math.max(c(ay), c(by)) + 1; y++) set(x, y, '#f0d070');
  }
  // Towns: framed tiles, red like the games' cities; a cave mouth in the hills.
  for (const [tx, ty] of [[8, 15], [8, 10], [14, 6], [20, 10], [17, 15], [24, 15], [14, 10]]) {
    for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) {
      const frame = x === 0 || y === 0 || x === 7 || y === 7;
      set(tx * 8 + x, ty * 8 + y, frame ? '#202028' : y < 3 ? '#ff8870' : '#e83828');
    }
  }
  for (let y = 0; y < 5; y++) for (let x = 0; x < 6; x++) set(6 * 8 + 1 + x, 6 * 8 + 2 + y, y === 0 || x === 0 || x === 5 ? '#3a2a18' : '#141018');
  return px;
}

const sheetArg = process.argv.indexOf('--sheet');
if (sheetArg > 0) {
  const ids = Object.keys(CREATURES);
  const s = 8, cell = 32, cols = 5, rows = Math.ceil(ids.length / cols);
  const W = cols * cell * s, H = rows * cell * s;
  const out = new Uint8Array(W * H * 4).fill(255);
  ids.forEach((id, n) => {
    const px = paint(CREATURES[id], cell, cell, 2);
    const ox = (n % cols) * cell * s, oy = Math.floor(n / cols) * cell * s;
    for (let y = 0; y < cell * s; y++) for (let x = 0; x < cell * s; x++) {
      const i = (Math.floor(y / s) * cell + Math.floor(x / s)) * 4;
      const o = ((oy + y) * W + ox + x) * 4;
      if (px[i + 3]) out.set(px.subarray(i, i + 4), o);
      else out.set(((x >> 4) + (y >> 4)) % 2 ? [236, 236, 244, 255] : [250, 250, 255, 255], o);
    }
  });
  writeFileSync(process.argv[sheetArg + 1], encodePng(W, H, out));
}
