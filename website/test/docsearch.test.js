// The docs search: the cases in docsearch-cases.json (which the app's DocsSearchTest runs too, so the
// two engines agree), then a few against the real manual.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { buildIndex, search, stem, terms, distance } from '../src/lib/docsearch.js';
import { docsApi, loadDocs } from '../src/lib/docs.js';

const cases = JSON.parse(readFileSync(new URL('./docsearch-cases.json', import.meta.url), 'utf8'));
const key = (h) => h.page + (h.anchor ? `#${h.anchor}` : '');

test('words, stems and typos', () => {
  assert.deepEqual(terms('How do I fast-forward the Pokémon battles?'), ['fast', 'forward', 'pokemon', 'battle']);
  assert.equal(stem('saves'), 'save');
  assert.equal(stem('cheating'), 'cheat');
  assert.equal(distance('shader', 'shdaer', 2), 1);
});

test('shared cases rank as expected', () => {
  const index = buildIndex(cases.docs);
  for (const c of cases.cases) {
    const hits = search(index, c.q);
    if (c.top === null) assert.equal(hits.length, 0, `"${c.q}" should find nothing`);
    else assert.equal(key(hits[0] ?? { page: 'nothing' }), c.top, `"${c.q}"`);
  }
});

test('the real manual answers common questions', () => {
  const index = buildIndex(docsApi(loadDocs(), 'https://pokedaisy.web.app'));
  for (const [q, page] of cases.manual) {
    const hits = search(index, q, 3).map((h) => h.page);
    assert.ok(hits.includes(page), `"${q}" should find ${page} in its top 3, got ${hits.join(', ')}`);
  }
});
