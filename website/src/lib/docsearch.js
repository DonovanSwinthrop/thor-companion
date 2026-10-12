// Search over the docs, run in the browser (/docs/) and, ported line for line, in the app (DocsSearch.kt -
// keep the two in step; test/docsearch-cases.json holds queries both test suites must rank the same).
//
// One record per page and one per ## section. A query's words are normalised (case, accents), lightly
// stemmed and matched against each record's words: exact, then as the start of a word (so "fast" finds
// "fast-forward" while you type), then with a typo or two. The synonyms (docs/manual/synonyms.yml) widen a
// word: ff -> fast forward. Rarer words count more (idf), and a hit in a title counts more than in the text.
// Every word has to match somewhere; when nothing matches them all, records matching most of them come back.

const STOP = new Set(['a', 'an', 'the', 'to', 'of', 'in', 'on', 'at', 'and', 'or', 'is', 'it', 'its', 'how', 'do', 'does',
  'i', 'me', 'my', 'what', 'can', 'for', 'with', 'you', 'your', 'be', 'are', 'from', 'by', 'this', 'that', 'there', 'when']);

/** Lowercase words without accents ("Poké Ball" -> ["poke", "ball"]). */
export function words(text) {
  return text
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .split(/[^a-z0-9]+/)
    .filter(Boolean);
}

/** A light English stemmer: plurals, -ing, -ed. The same word in the query and the text stems the same. */
export function stem(w) {
  if (w.length > 4 && w.endsWith('ies')) return w.slice(0, -3) + 'y';
  if (w.length > 5 && w.endsWith('ing')) return w.slice(0, -3);
  if (w.length > 4 && w.endsWith('ed')) return w.slice(0, -2);
  if (w.length > 3 && w.endsWith('s') && !w.endsWith('ss')) return w.slice(0, -1);
  return w;
}

export function terms(text) {
  return words(text).filter((w) => !STOP.has(w)).map(stem);
}

/** Edit distance with transpositions, giving up past [max]. */
export function distance(a, b, max) {
  if (Math.abs(a.length - b.length) > max) return max + 1;
  const d = [];
  for (let i = 0; i <= a.length; i++) d.push([i]);
  for (let j = 1; j <= b.length; j++) d[0][j] = j;
  for (let i = 1; i <= a.length; i++) {
    let rowMin = Infinity;
    for (let j = 1; j <= b.length; j++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1;
      let v = Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost);
      if (i > 1 && j > 1 && a[i - 1] === b[j - 2] && a[i - 2] === b[j - 1]) v = Math.min(v, d[i - 2][j - 2] + 1);
      d[i][j] = v;
      rowMin = Math.min(rowMin, v);
    }
    if (rowMin > max) return max + 1;
  }
  return d[a.length][b.length];
}

const cmp = (x, y) => (x < y ? -1 : x > y ? 1 : 0);

const FIELDS = { title: 6, keywords: 4, context: 1.5, summary: 2, text: 1 };

/** The index over [docs] (the API's shape). Build it once, search it many times. */
export function buildIndex(docs) {
  const records = [];
  for (const page of docs.pages) {
    for (const r of sectionsOf(page)) {
      const fields = {
        title: terms(r.heading ?? page.title),
        keywords: r.anchor ? [] : page.keywords.flatMap(terms),
        context: r.anchor ? terms(page.title) : [],
        summary: r.anchor ? [] : terms(page.summary),
        text: terms(r.text),
      };
      records.push({ page: page.slug, anchor: r.anchor, heading: r.heading, pageTitle: page.title, body: r.text, fields });
    }
  }
  const df = new Map();
  for (const r of records) for (const t of new Set(Object.values(r.fields).flat())) df.set(t, (df.get(t) ?? 0) + 1);
  const synonyms = new Map(Object.entries(docs.synonyms ?? {}).map(([k, v]) => [stem(k), v.flatMap(terms)]));
  return { records, df, vocab: [...df.keys()], synonyms };
}

/** One record per page (its text before the first ##) and one per ## section. */
function sectionsOf(page) {
  const plain = (spans) => spans.map((s) => s.text).join('');
  const recs = [{ anchor: null, heading: null, text: [] }];
  let cur = recs[0];
  const walk = (bs) => {
    for (const b of bs) {
      if (b.t === 'h2') { cur = { anchor: b.id, heading: b.text, text: [] }; recs.push(cur); }
      else if (b.t === 'h3') cur.text.push(b.text);
      else if (b.t === 'p') cur.text.push(plain(b.c));
      else if (b.t === 'ul' || b.t === 'ol') b.items.forEach(walk);
      else if (b.t === 'note') walk(b.c);
      else if (b.t === 'table') [b.head, ...b.rows].forEach((row) => cur.text.push(row.map(plain).join(' ')));
      else if (b.t === 'img') cur.text.push(b.caption ?? b.alt);
    }
  };
  walk(page.blocks);
  return recs.map((r) => ({ ...r, text: r.text.join(' ') }));
}

/** How well [q] matches [t]: 1 the same, 0.75 the start of it, 0.5 a typo away, else 0. */
function matchScore(q, t) {
  if (q === t) return 1;
  if (q.length >= 2 && t.startsWith(q)) return 0.75;
  const max = q.length >= 8 ? 2 : q.length >= 4 ? 1 : 0;
  if (max && distance(q, t, max) <= max) return 0.5;
  return 0;
}

/** Each query word's alternatives: itself, plus its synonyms (worth a little less). */
function expand(index, query) {
  return terms(query).map((q) => {
    const alts = [{ t: q, w: 1 }];
    for (const s of index.synonyms.get(q) ?? []) alts.push({ t: s, w: 0.8 });
    return alts;
  });
}

/**
 * The best [limit] records for [query]: { page, anchor, heading, pageTitle, score, snippet }, best first.
 * An empty query gives nothing.
 */
export function search(index, query, limit = 8) {
  const groups = expand(index, query);
  if (!groups.length) return [];
  const N = index.records.length;
  const idf = (t) => Math.log(1 + N / (index.df.get(t) ?? 1));
  const scored = [];
  for (const r of index.records) {
    let total = 0;
    let matched = 0;
    for (const alts of groups) {
      let best = 0;
      for (const { t: q, w } of alts) {
        for (const [field, weight] of Object.entries(FIELDS)) {
          let fieldBest = 0;
          let fieldTerm = null;
          let hits = 0;
          for (const t of r.fields[field]) {
            const m = matchScore(q, t);
            if (m > 0) hits++;
            if (m > fieldBest) { fieldBest = m; fieldTerm = t; }
          }
          if (!fieldBest) continue;
          const s = weight * w * fieldBest * idf(fieldTerm) * (1 + 0.15 * Math.min(hits - 1, 4));
          if (s > best) best = s;
        }
      }
      if (best > 0) matched++;
      total += best;
    }
    if (!matched) continue;
    // How much of the title the query covers: "game speed" is all of a "Game speed" section but half of
    // "Fast-forward and game speed", so the section comes first.
    const title = [...new Set(r.fields.title)];
    const covered = title.filter((t) => groups.some((alts) => alts.some((a) => matchScore(a.t, t) > 0))).length;
    if (title.length) total *= 1 + 0.6 * (covered / title.length);
    // A page's own record a little ahead of its sections on a tie, so "fast forward" opens the page.
    if (!r.anchor) total *= 1.05;
    scored.push({ r, total, matched });
  }
  const most = Math.max(0, ...scored.map((s) => s.matched));
  return scored
    .filter((s) => s.matched === most)
    .sort((a, b) => b.total - a.total || cmp(a.r.page + (a.r.anchor ?? ''), b.r.page + (b.r.anchor ?? '')))
    .slice(0, limit)
    .map(({ r, total }) => ({
      page: r.page,
      anchor: r.anchor,
      heading: r.heading,
      pageTitle: r.pageTitle,
      score: Math.round(total * 1000) / 1000,
      snippet: snippet(r.body, groups),
    }));
}

/** ~160 characters of [body] around the first word that matched, with [start, end) ranges to highlight. */
export function snippet(body, groups, size = 160) {
  const qs = groups.flat().map((a) => a.t);
  const re = /[A-Za-z0-9À-ɏ]+/g;
  const hits = [];
  let m;
  while ((m = re.exec(body))) {
    const t = stem(words(m[0])[0] ?? '');
    if (t && qs.some((q) => matchScore(q, t) > 0)) hits.push([m.index, m.index + m[0].length]);
  }
  if (!body) return { text: '', marks: [] };
  const first = hits[0]?.[0] ?? 0;
  let start = Math.max(0, first - Math.floor(size / 3));
  if (start > 0) {
    const space = body.indexOf(' ', start);
    start = space >= 0 && space < first ? space + 1 : start;
  }
  let end = Math.min(body.length, start + size);
  if (end < body.length) {
    const space = body.lastIndexOf(' ', end);
    if (space > first) end = space;
  }
  const pre = start > 0 ? '…' : '';
  const text = pre + body.slice(start, end) + (end < body.length ? '…' : '');
  const marks = hits
    .filter(([a, b]) => a >= start && b <= end)
    .map(([a, b]) => [a - start + pre.length, b - start + pre.length]);
  return { text, marks };
}
