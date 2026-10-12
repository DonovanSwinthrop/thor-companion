// Copies docs/manual's screenshots and art into public/docs/ (gitignored), where the docs pages and the
// docs API point at them (/docs/shots/x.png). Runs before `npm run dev` and `npm run build`.
import { cpSync, existsSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { docsDir } from '../src/lib/docs.js';

const out = join(process.cwd(), 'public/docs');
rmSync(out, { recursive: true, force: true });
for (const dir of ['shots', 'art']) {
  const src = join(docsDir(), dir);
  if (existsSync(src)) cpSync(src, join(out, dir), { recursive: true });
}
console.log(`docs images -> ${out}`);
