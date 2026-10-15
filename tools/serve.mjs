/* Static server for reviewing dist/. Picks the first free port from a
   candidate list — 8000/9000 and 4321 are already taken on this machine. */
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', 'dist');
const PORTS = [5187, 5188, 5189, 4173, 3939];
const TYPES = { '.html': 'text/html; charset=utf-8', '.css': 'text/css', '.js': 'text/javascript' };

const app = createServer(async (req, res) => {
  const rel = decodeURIComponent(req.url.split('?')[0]).replace(/^\/+/, '') || 'index.html';
  const file = path.join(ROOT, rel);
  if (!file.startsWith(ROOT)) { res.writeHead(403).end('forbidden'); return; }
  try {
    const body = await readFile(file);
    res.writeHead(200, { 'content-type': TYPES[path.extname(file)] || 'application/octet-stream', 'cache-control': 'no-store' });
    res.end(body);
  } catch { res.writeHead(404).end('not found'); }
});

let i = 0;
app.on('error', e => {
  if (e.code === 'EADDRINUSE' && ++i < PORTS.length) app.listen(PORTS[i]);
  else { console.error(e.message); process.exit(1); }
});
app.on('listening', () => console.log(`http://localhost:${PORTS[i]}/index.html`));
app.listen(PORTS[0]);
