// The docs API: the whole manual as blocks (src/lib/docs.js). The app's docs reader (DocsApi.kt) fetches
// this and keeps a copy for offline reading; images are at `site` + each image block's src. Built with the
// site, so it changes with an app release, like every other page.
import { docsApi, loadDocs } from '../../../../lib/docs.js';

export function GET({ site }) {
  const body = docsApi(loadDocs(), site ?? 'https://pokedaisy.web.app');
  return new Response(JSON.stringify(body), { headers: { 'Content-Type': 'application/json; charset=utf-8' } });
}
