/* Resolve a module's pinned jar list into build/lib/<name>/.
 *
 *   node tools/fetch-deps.mjs java-jpa        # one list
 *   node tools/fetch-deps.mjs                 # every tools/deps/*.json
 *
 * Each jar is copied from the local Maven cache (~/.m2) when it is there and
 * downloaded from Maven Central otherwise. The list is pinned by hand (no
 * transitive resolution): a version bump is a deliberate edit to the JSON, and
 * the build then re-verifies every program against it. Also importable:
 * ensureDeps(name) returns the jar paths.
 */
import { readFile, writeFile, mkdir, copyFile, access } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const CENTRAL = 'https://repo1.maven.org/maven2';

function coords(spec) {
  const [g, a, v] = spec.split(':');
  if (!g || !a || !v) throw new Error(`bad coordinate "${spec}" (want group:artifact:version)`);
  const rel = `${g.replace(/\./g, '/')}/${a}/${v}/${a}-${v}.jar`;
  return { g, a, v, rel, file: `${a}-${v}.jar` };
}

/* ── Maven-resolved lists ──────────────────────────────────────────────────
   A list may instead declare { "maven": { "bom": "g:a:v", "dependencies": ["g:a", …] } }.
   A pom is generated and `mvn dependency:copy-dependencies` resolves the full
   transitive, BOM-managed graph — the only sane option for Spring Boot. Maven
   is found on PATH, in $MAVEN_HOME, or in the Maven Wrapper's cache. */
async function findMvn() {
  const { readdir } = await import('node:fs/promises');
  const exe = process.platform === 'win32' ? 'mvn.cmd' : 'mvn';
  for (const dirP of (process.env.PATH || '').split(path.delimiter)) {
    if (dirP && existsSync(path.join(dirP, exe))) return path.join(dirP, exe);
  }
  if (process.env.MAVEN_HOME && existsSync(path.join(process.env.MAVEN_HOME, 'bin', exe))) {
    return path.join(process.env.MAVEN_HOME, 'bin', exe);
  }
  const dists = path.join(os.homedir(), '.m2', 'wrapper', 'dists');
  if (existsSync(dists)) {
    const found = [];
    for (const d of await readdir(dists)) {
      const dd = path.join(dists, d);
      for (const h of (await readdir(dd).catch(() => []))) {
        for (const m of (await readdir(path.join(dd, h)).catch(() => []))) {
          const bin = path.join(dd, h, m, 'bin', exe);
          if (existsSync(bin)) found.push(bin);
        }
      }
    }
    // newest version last after a natural sort
    found.sort((a, b) => a.localeCompare(b, undefined, { numeric: true }));
    if (found.length) return found[found.length - 1];
  }
  return null;
}

async function resolveWithMaven(name, spec, dir, log) {
  const { execFile } = await import('node:child_process');
  const { promisify } = await import('node:util');
  const exec = promisify(execFile);
  const { readdir } = await import('node:fs/promises');

  const stamp = path.join(dir, '.resolved.json');
  const want = JSON.stringify(spec);
  if (existsSync(stamp) && (await readFile(stamp, 'utf8')) === want) {
    const jars = (await readdir(dir)).filter(f => f.endsWith('.jar')).sort().map(f => path.join(dir, f));
    if (jars.length) return { dir, jars, fetched: 0 };
  }
  const mvn = await findMvn();
  if (!mvn) throw new Error(`deps "${name}" need Maven to resolve, and no mvn was found on PATH, in MAVEN_HOME or in ~/.m2/wrapper/dists`);

  const [bg, ba, bv] = spec.bom.split(':');
  const deps = spec.dependencies.map(d => {
    const [g, a, v] = d.split(':');
    return `    <dependency><groupId>${g}</groupId><artifactId>${a}</artifactId>${v ? `<version>${v}</version>` : ''}</dependency>`;
  }).join('\n');
  const pom = `<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>lm</groupId><artifactId>deps-${name}</artifactId><version>0</version><packaging>pom</packaging>
  <dependencyManagement><dependencies>
    <dependency><groupId>${bg}</groupId><artifactId>${ba}</artifactId><version>${bv}</version><type>pom</type><scope>import</scope></dependency>
  </dependencies></dependencyManagement>
  <dependencies>
${deps}
  </dependencies>
</project>
`;
  await writeFile(path.join(dir, 'pom.xml'), pom, 'utf8');
  // clear stale jars from a previous spec so the directory is exactly the graph
  for (const f of await readdir(dir)) if (f.endsWith('.jar')) await (await import('node:fs/promises')).rm(path.join(dir, f));
  log(`  resolving with Maven (${path.basename(path.dirname(path.dirname(mvn)))}) …`);
  // mvn.cmd needs a shell on Windows, and a shell needs every path quoted
  // (the Maven Wrapper cache lives under the user's home, which may contain a space)
  const q = s => `"${s}"`;
  const cmd = [q(mvn), '-q', '-B', '-f', q(path.join(dir, 'pom.xml')), 'dependency:copy-dependencies',
               '-DincludeScope=runtime', q('-DoutputDirectory=' + dir)].join(' ');
  const { exec: execShell } = await import('node:child_process');
  await promisify(execShell)(cmd, { cwd: dir, maxBuffer: 1 << 26, timeout: 600_000 });
  const jars = (await readdir(dir)).filter(f => f.endsWith('.jar')).sort().map(f => path.join(dir, f));
  if (!jars.length) throw new Error(`Maven resolved no jars for "${name}"`);
  await writeFile(stamp, want, 'utf8');
  return { dir, jars, fetched: jars.length };
}

export async function ensureDeps(name, { log = () => {} } = {}) {
  const listPath = path.join(ROOT, 'tools/deps', name + '.json');
  const list = JSON.parse(await readFile(listPath, 'utf8'));
  const dir = path.join(ROOT, 'build/lib', name);
  await mkdir(dir, { recursive: true });
  if (list.maven) return resolveWithMaven(name, list.maven, dir, log);
  const m2 = path.join(os.homedir(), '.m2', 'repository');
  const out = [];
  let fetched = 0;
  for (const spec of list.jars) {
    const c = coords(spec);
    const dest = path.join(dir, c.file);
    out.push(dest);
    if (existsSync(dest)) continue;
    const cached = path.join(m2, c.rel);
    if (existsSync(cached)) {
      await copyFile(cached, dest);
      log(`  copied   ${c.file} (from ~/.m2)`);
      continue;
    }
    const url = `${CENTRAL}/${c.rel}`;
    const res = await fetch(url);
    if (!res.ok) throw new Error(`${res.status} fetching ${url}`);
    await writeFile(dest, Buffer.from(await res.arrayBuffer()));
    fetched++;
    log(`  fetched  ${c.file}`);
  }
  return { dir, jars: out, fetched };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const names = process.argv.slice(2);
  const { readdir } = await import('node:fs/promises');
  const all = names.length ? names : (await readdir(path.join(ROOT, 'tools/deps'))).filter(f => f.endsWith('.json')).map(f => f.replace('.json', ''));
  for (const n of all) {
    console.log(`· deps ${n}`);
    const r = await ensureDeps(n, { log: console.log });
    console.log(`  ${r.jars.length} jars in ${path.relative(ROOT, r.dir)}`);
  }
}
