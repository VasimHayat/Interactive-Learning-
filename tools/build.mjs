/* Build one standalone HTML per topic.
 *
 *   scss ──► app.css ─┐
 *   tailwind base ────┼─► single inlined <style>
 *   tailwind utils ───┘
 *   sections/*.html ──► code blocks rendered, steppers pre-rendered, TOC built
 *   js/*.js ──────────► single inlined <script>
 *
 * Every <pre data-code data-verify="Cls"> is compiled and run with the local
 * JDK and its stdout diffed against the following <pre data-output>. A
 * mismatch fails the build — outputs in the shipped file are verified, not
 * asserted. Pass --skip-verify to bypass (CI without a JDK).
 *
 *   --topic=<slug>            build one topic
 *   --files=a.html,b.html     assemble only those section files of that topic
 *                             (an isolated authoring loop; verify dir is per-run,
 *                             output goes to build/partial/, never dist/)
 *   --skip-verify  --no-minify
 *
 * Sandboxes: core handlers live in src/js/playground.js; per-topic handlers
 * live in src/js/sandboxes/*.js (one reg() each) and are bundled after it.
 */
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { readFile, writeFile, mkdir, rm, readdir, stat } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { ensureDeps } from './fetch-deps.mjs';

const exec = promisify(execFile);
const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const p = (...a) => path.join(ROOT, ...a);

const argv = process.argv.slice(2);
const only = (argv.find(a => a.startsWith('--topic=')) || '').split('=')[1] || null;
const SKIP_VERIFY = argv.includes('--skip-verify');
const FILES = ((argv.find(a => a.startsWith('--files=')) || '').split('=')[1] || '')
  .split(',').map(s => s.trim()).filter(Boolean);
if (FILES.length && !only) { console.error('--files requires --topic=<slug>'); process.exit(1); }
const NO_MIN = argv.includes('--no-minify');

const log = (...a) => console.log(...a);
const warn = (...a) => console.warn('  ! ', ...a);

// ── load the tokenizer the browser will use, so highlighting cannot drift ───
const hlSource = await readFile(p('src/js/highlight.js'), 'utf8');
new Function(hlSource).call(globalThis);
const H = globalThis.LMHighlight;

// ── css ─────────────────────────────────────────────────────────────────────
// Uses the sass and postcss JS APIs rather than the .cmd shims — Node on
// Windows refuses to spawn those (EINVAL) without shell:true.
async function buildCss() {
  await mkdir(p('build'), { recursive: true });

  const sass = await import('sass');
  const postcss = (await import('postcss')).default;
  const tailwind = (await import('tailwindcss')).default;
  const cssnano = await import('cssnano').then(m => m.default).catch(() => null);
  const twConfig = (await import(pathToFileURL(p('tailwind.config.js')).href)).default;

  const app = sass.compile(p('src/scss/main.scss'), { style: 'compressed', sourceMap: false }).css;

  async function tw(inputFile) {
    const input = await readFile(p(inputFile), 'utf8');
    const plugins = [tailwind({ ...twConfig, content: twConfig.content.map(c => p(c)) })];
    if (cssnano) plugins.push(cssnano({ preset: ['default', { discardComments: { removeAll: true } }] }));
    const r = await postcss(plugins).process(input, { from: p(inputFile), to: undefined });
    return r.css;
  }

  const base = await tw('src/tw-base.css');
  const utils = await tw('src/tw-utils.css');

  await writeFile(p('build/app.css'), app, 'utf8');

  /* Sass prefixes its output with a UTF-8 BOM. Concatenated into the middle of
     a stylesheet that is fine as bytes but fatal as CSS: the stray U+FEFF makes
     the rule right after it unparseable, which silently dropped the whole
     :root token block. Strip it from every part before joining. */
  const debom = s => s.replace(/^﻿/, '');
  // preflight → our component layer → utilities (utilities must win)
  const out = [base, app, utils].map(debom).join('\n');
  if (out.includes('﻿')) throw new Error('stray U+FEFF survived in the CSS bundle');
  return out;
}

// ── js ──────────────────────────────────────────────────────────────────────
/* A module only ships the sandbox handlers that belong to it: the files whose
   name starts with its slug. Core handlers in playground.js ship everywhere. */
async function sandboxFiles(slug) {
  const dir = p('src/js/sandboxes');
  if (!existsSync(dir)) return [];
  return (await readdir(dir))
    .filter(f => f.endsWith('.js') && slug && f.startsWith(slug + '-'))
    .sort().map(f => path.join(dir, f));
}

async function sandboxNames(slug) {
  const sources = [p('src/js/playground.js'), ...(await sandboxFiles(slug))];
  const names = new Set();
  for (const f of sources) {
    const src = await readFile(f, 'utf8');
    for (const m of src.matchAll(/reg\('([^']+)'/g)) {
      if (names.has(m[1])) throw new Error(`sandbox "${m[1]}" is registered twice (second time in ${path.relative(ROOT, f)})`);
      names.add(m[1]);
    }
  }
  return names;
}

/* playground.js exposes its registry as globalThis.LMSandbox so that the
   per-topic files, which are concatenated after it, can call reg(). Each of
   those files is wrapped in its own IIFE and must not rely on anything but
   LMSandbox.reg and LMHighlight. */
async function buildJs(slug) {
  const files = ['highlight.js', 'core.js', 'quiz.js', 'playground.js'];
  const parts = [];
  for (const f of files) parts.push(`/* ${f} */\n` + await readFile(p('src/js', f), 'utf8'));
  for (const f of await sandboxFiles(slug)) {
    parts.push(`/* sandboxes/${path.basename(f)} */\n` + await readFile(f, 'utf8'));
  }
  // the boot pass runs last, after every handler has registered
  parts.push('/* boot */\n(function(){ if (globalThis.LMSandbox) globalThis.LMSandbox.boot(); })();');
  return minifyJs(parts.join('\n'));
}

/* The JS is minified here, on its own, and the HTML minifier is told to leave
   <script> alone. Letting html-minifier-terser minify the inline script was a
   real bug: its ignoreCustomFragments rule for <pre>…</pre> also matched the
   string '<pre><code>' + body + '</code></pre>' inside renderCode (and any
   sandbox that builds a <pre>), preserved that slice verbatim, and terser
   renamed `body` everywhere else — a ReferenceError the moment a sandbox
   called renderCode in the browser. */
async function minifyJs(src) {
  if (NO_MIN) return src;
  const { minify } = await import('terser');
  const r = await minify(src, { compress: { passes: 2 }, mangle: true, format: { comments: false } });
  if (r.error) throw r.error;
  return r.code;
}

// ── attribute parsing ───────────────────────────────────────────────────────
function attrs(str) {
  const out = {};
  const re = /([a-zA-Z0-9-]+)(?:="([^"]*)")?/g;
  let m;
  while ((m = re.exec(str))) out[m[1]] = m[2] === undefined ? '' : m[2];
  return out;
}
const nums = s => (s || '').split(',').map(x => parseInt(x.trim(), 10)).filter(Number.isFinite);

/* A <pre> is parsed as HTML, so an author may write < as &lt; inside it (and must, if
   the snippet contains a literal </pre>). The highlighter escapes once more, so
   entities have to be decoded here or they ship as a visible &lt;. Only the
   five named entities HTML itself needs; nothing else is touched. */
function decodeEntities(s) {
  return s.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"')
          .replace(/&#39;/g, "'").replace(/&amp;/g, '&');
}

function dedent(s) {
  const body = s.replace(/^\n/, '').replace(/\s+$/, '');
  const lines = body.split('\n').filter(l => l.trim());
  const indent = Math.min(...lines.map(l => l.match(/^ */)[0].length));
  return body.split('\n').map(l => l.slice(indent)).join('\n');
}

// ── collect code + output blocks in document order ──────────────────────────
const BLOCK_RE = /<pre\s+(data-code|data-output)([^>]*)>([\s\S]*?)<\/pre>/g;

function collectBlocks(html) {
  const blocks = [];
  let m;
  BLOCK_RE.lastIndex = 0;
  while ((m = BLOCK_RE.exec(html))) {
    blocks.push({ kind: m[1], attrs: attrs(m[2]), code: dedent(decodeEntities(m[3])), raw: m[0], index: m.index });
  }
  return blocks;
}

// ── java verification ───────────────────────────────────────────────────────
async function verifyJava(blocks, slug, libJars = []) {
  /* Pairing is explicit, never positional: <pre data-code data-verify="Cls">
     binds to <pre data-output data-for="Cls">. An earlier positional version
     silently skipped the diff whenever any block sat between the two. */
  const outputs = new Map();
  for (const b of blocks) {
    if (b.kind !== 'data-output') continue;
    const id = b.attrs['data-for'];
    if (!id) continue;
    if (outputs.has(id)) throw new Error(`two <pre data-output data-for="${id}"> blocks — ids must be unique`);
    outputs.set(id, b.code);
  }

  const jobs = [];
  const seen = new Set();
  for (const b of blocks) {
    if (b.kind !== 'data-code' || !b.attrs['data-verify']) continue;
    const cls = b.attrs['data-verify'];
    if (seen.has(cls)) throw new Error(`two programs both declare data-verify="${cls}" — class names must be unique`);
    seen.add(cls);
    const throws = b.attrs['data-verify-throws'] || null;
    const expected = outputs.has(cls) ? outputs.get(cls) : null;
    if (expected === null && !throws) {
      throw new Error(`data-verify="${cls}" has no <pre data-output data-for="${cls}"> to diff against ` +
                      `(add one, or declare data-verify-throws="…" if the program is meant to fail)`);
    }
    /* data-package="com.acme.x" puts the program in its own package: the
       source still lands in <cls>.java (javac only cares that the file is
       named after the public class) but it runs as com.acme.x.<cls>. Needed
       by anything that component-scans — every program shares one classes
       directory, so an unpackaged Spring app would scan its neighbours. */
    jobs.push({ cls, code: b.code, expected, throws, file: b.attrs['data-file'] || '', pkg: b.attrs['data-package'] || '' });
  }

  for (const id of outputs.keys()) {
    if (!seen.has(id)) warn(`<pre data-output data-for="${id}"> matches no verified program — typo?`);
  }
  for (const j of jobs) {
    const decl = (j.code.match(/^\s*package\s+([\w.]+)\s*;/m) || [])[1] || '';
    if (decl !== j.pkg) {
      throw new Error(`data-verify="${j.cls}": data-package="${j.pkg}" but the source declares package "${decl || '(none)'}"`);
    }
  }
  if (!jobs.length) return { ran: 0, ok: 0, failures: [] };
  if (SKIP_VERIFY) { warn(`skipping verification of ${jobs.length} Java programs (--skip-verify)`); return { ran: 0, ok: 0, failures: [], skipped: jobs.length }; }

  const dir = p('build/verify', slug + (FILES.length ? '-partial-' + process.pid : ''));
  await rm(dir, { recursive: true, force: true });
  await mkdir(path.join(dir, 'classes'), { recursive: true });

  for (const j of jobs) await writeFile(path.join(dir, j.cls + '.java'), j.code, 'utf8');

  const cp = ['classes', ...libJars].join(path.delimiter);
  try {
    // pin the encoding — javac otherwise uses the platform charset, which on
    // Windows mangles any non-ASCII character in a source file
    /* A module may declare pinned jars (meta.deps → tools/deps/<name>.json);
       they go on the class path for both javac and java, so a program can use
       Hibernate or a JDBC driver exactly as a reader with those jars would. */
    await exec('javac', ['-nowarn', '-encoding', 'UTF-8', '-d', 'classes', '-cp', cp,
                         ...jobs.map(j => j.cls + '.java')],
               { cwd: dir, maxBuffer: 1 << 24 });
  } catch (e) {
    return { ran: jobs.length, ok: 0, failures: [{ cls: '(compile)', detail: (e.stderr || e.message).trim() }] };
  }

  const failures = [];
  let ok = 0;
  /* Programs are independent processes (each with its own in-memory DB when a
     module uses one), so they run a few at a time. Results are collected in
     job order so failure reports stay stable. */
  const CONCURRENCY = Math.max(1, Math.min(4, parseInt(process.env.LM_VERIFY_JOBS || '4', 10) || 4));
  const results = new Array(jobs.length);
  let next = 0;
  async function worker() {
    while (next < jobs.length) {
      const idx = next++;
      results[idx] = await runOne(jobs[idx]);
    }
  }
  async function runOne(j) {
    let stdout = '', stderr = '', threw = false;
    try {
      // a concurrent program that deadlocks must fail the build, not hang it
      const main = j.pkg ? j.pkg + '.' + j.cls : j.cls;
      const r = await exec('java', ['-cp', cp, ...(libJars.length ? ['-Djava.util.logging.config.file=' + p('tools/deps/logging.properties')] : []), main],
                           { cwd: dir, maxBuffer: 1 << 24, timeout: 90_000, killSignal: 'SIGKILL' });
      stdout = r.stdout; stderr = r.stderr;
    } catch (e) {
      threw = true; stdout = e.stdout || ''; stderr = e.stderr || '';
      if (e.killed) stderr = 'TIMEOUT: the program did not finish within 90 s (deadlock or unbounded wait?)\n' + stderr;
    }
    const norm = s => s.replace(/\r\n/g, '\n').replace(/[ \t]+$/gm, '').replace(/\s+$/, '');

    if (j.throws) {
      if (!threw || !norm(stderr).includes(j.throws)) {
        return { cls: j.cls, detail: `expected a throw containing "${j.throws}"\n  actual stderr: ${norm(stderr).split('\n')[0] || '(none)'}` };
      }
    } else if (threw) {
      return { cls: j.cls, detail: `unexpected exception:\n${norm(stderr).split('\n').slice(0, 4).join('\n')}` };
    }

    if (j.expected !== null) {
      if (norm(stdout) !== norm(j.expected)) {
        return { cls: j.cls, detail: `stdout mismatch\n  --- expected ---\n${norm(j.expected)}\n  --- actual ---\n${norm(stdout)}` };
      }
    }
    return null;
  }
  await Promise.all(Array.from({ length: Math.min(CONCURRENCY, jobs.length) }, worker));
  for (const r of results) { if (r) failures.push(r); else ok++; }
  return { ran: jobs.length, ok, failures };
}

// ── render code / output blocks into final markup ───────────────────────────
function renderBlocks(html) {
  BLOCK_RE.lastIndex = 0;
  return html.replace(BLOCK_RE, (raw, kind, attrStr, body) => {
    const a = attrs(attrStr);
    const code = dedent(decodeEntities(body));
    if (kind === 'data-output') {
      const label = a['data-label'] || 'Expected output';
      const cls = a['data-variant'] === 'error' ? ' style="color:var(--lm-bad)"' : '';
      return `<div class="lm-output"><div class="lm-output-bar">` +
             `<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><path d="m4 17 6-6-6-6"/><path d="M12 19h8"/></svg>` +
             `${H.esc(label)}</div><pre${cls}>${H.esc(code)}</pre></div>`;
    }
    return H.renderCode(code, {
      lang: a['data-lang'] || 'java',
      file: a['data-file'] || '',
      hi: nums(a['data-hi']),
      bad: nums(a['data-bad']),
      noBar: 'data-nobar' in a,
    });
  });
}

/* Pre-render stepper source into its placeholder so the code is readable with
 * JS disabled and in print. Keyed by id, so markup order does not matter:
 *   <div data-stepper-code data-for="ID"></div>
 *   <script type="application/json" data-stepper-data data-id="ID">…</script>
 */
function renderSteppers(html) {
  const byId = new Map();
  const re = /<script type="application\/json" data-stepper-data data-id="([^"]+)">([\s\S]*?)<\/script>/g;
  let m;
  while ((m = re.exec(html))) {
    let data;
    try { data = JSON.parse(m[2]); }
    catch (e) { throw new Error(`stepper "${m[1]}": bad JSON — ${e.message}`); }
    if (!data.code || !Array.isArray(data.steps) || !data.steps.length) {
      throw new Error(`stepper "${m[1]}": needs a code string and a non-empty steps array`);
    }
    const lineCount = data.code.replace(/^\n/, '').replace(/\s+$/, '').split('\n').length;
    for (const s of data.steps) {
      const ls = Array.isArray(s.line) ? s.line : [s.line];
      for (const l of ls) {
        if (!Number.isFinite(l) || l < 1 || l > lineCount) {
          throw new Error(`stepper "${m[1]}": step targets line ${l}, but the snippet has ${lineCount} lines`);
        }
      }
    }
    byId.set(m[1], H.renderCode(data.code, { lang: data.lang || 'java', noBar: true }));
  }

  return html.replace(
    /<div([^>]*?)data-stepper-code data-for="([^"]+)"([^>]*?)><\/div>/g,
    (raw, a, id, b) => {
      const code = byId.get(id);
      if (!code) throw new Error(`stepper placeholder "${id}" has no matching data script`);
      return `<div${a}data-stepper-code${b}>${code}</div>`;
    }
  );
}

/* Structural checks on authored markup. These catch the authoring mistakes that
   would otherwise ship as a silently broken widget: a question with no answer
   key grades every choice wrong, a sandbox with no registered handler renders
   an empty box. Cheap to check, expensive to find by hand across 20 sections. */
function lint(html, slug) {
  const errs = [];
  const REGISTERED_SANDBOXES = new Set(
    [...(globalThis.__sandboxNames || [])]
  );

  // every question needs an answer key, a check button and an explanation
  const qRe = /<div class="lm-q"([^>]*)>([\s\S]*?)(?=<div class="lm-q"|<\/div>\s*<\/section>|$)/g;
  let m;
  while ((m = qRe.exec(html))) {
    const a = attrs(m[1]);
    const id = a.id || '(no id)';
    if (!('data-answer' in a) || !a['data-answer']) errs.push(`question ${id}: missing data-answer`);
    const body = m[2];
    const opts = [...body.matchAll(/class="lm-opt" data-opt="([^"]+)"/g)].map(x => x[1]);
    if (opts.length < 2) errs.push(`question ${id}: fewer than 2 options`);
    if (new Set(opts).size !== opts.length) errs.push(`question ${id}: duplicate data-opt values`);
    for (const k of (a['data-answer'] || '').split(',').map(s => s.trim()).filter(Boolean)) {
      if (!opts.includes(k)) errs.push(`question ${id}: data-answer "${k}" matches no option`);
    }
    // questions inside a scored quiz are graded by its submit button; a
    // per-question check there would leak answers before submission
    const inScoredQuiz = /data-quiz\b/.test(html.slice(0, m.index).split('<section').pop() || '');
    if (!inScoredQuiz && !/data-check/.test(body)) errs.push(`question ${id}: no [data-check] button`);
    if (!/data-explain/.test(body)) errs.push(`question ${id}: no [data-explain] block`);
  }

  // ids must be unique across the assembled document
  const ids = [...html.matchAll(/\sid="([^"]+)"/g)].map(x => x[1]);
  const dupIds = ids.filter((v, i) => ids.indexOf(v) !== i);
  if (dupIds.length) errs.push(`duplicate id(s): ${[...new Set(dupIds)].join(', ')}`);

  // every sandbox must have a handler registered in playground.js
  for (const [, name] of html.matchAll(/data-sandbox="([^"]+)"/g)) {
    if (REGISTERED_SANDBOXES.size && !REGISTERED_SANDBOXES.has(name)) {
      errs.push(`data-sandbox="${name}" has no reg() handler in src/js/playground.js`);
    }
  }

  // ordering and matching widgets need their answer key too
  for (const [, a] of html.matchAll(/<div class="lm-order-wrap"([^>]*)>/g)) {
    if (!/data-solution="/.test(a)) errs.push('an ordering widget has no data-solution');
  }

  /* Control characters are invalid in HTML — a parser silently swaps U+0000
     for U+FFFD — and they are invisible in an editor. A literal NUL reached
     dist once, written as the char default value in a table. */
  const ctrl = html.match(/[\u0000-\u0008\u000b\u000c\u000e-\u001f]/g);
  if (ctrl) {
    const codes = [...new Set(ctrl)].map(c => 'U+' + c.codePointAt(0).toString(16).padStart(4, '0'));
    errs.push(`${ctrl.length} control character(s) in the markup (${codes.join(', ')}) — write the escape sequence instead`);
  }

  // sections need the metadata the TOC is built from
  for (const [, tag] of html.matchAll(/<section class="lm-section"([^>]*)>/g)) {
    const a = attrs(tag);
    if (!a.id) errs.push('a section has no id');
    if (!a['data-title']) errs.push(`section ${a.id || '?'}: no data-title`);
    if (!a['data-num']) errs.push(`section ${a.id || '?'}: no data-num`);
  }

  if (errs.length) {
    console.error(`\n  BUILD FAILED — ${errs.length} markup problem(s) in ${slug}\n`);
    for (const e of errs) console.error('  · ' + e);
    console.error('');
    throw new Error('markup lint failed');
  }
}

// ── table of contents ───────────────────────────────────────────────────────
function buildToc(sectionHtml) {
  const re = /<section\s+class="lm-section"\s+id="([^"]+)"[^>]*data-title="([^"]*)"[^>]*data-num="([^"]*)"/g;
  const items = [];
  let m;
  while ((m = re.exec(sectionHtml))) items.push({ id: m[1], title: m[2], num: m[3] });
  return {
    count: items.length,
    html: items.map(i =>
      `<li><a href="#${i.id}"><span class="lm-toc-num">${i.num}</span><span>${i.title}</span></a></li>`
    ).join('\n')
  };
}

// ── minify ──────────────────────────────────────────────────────────────────
async function minify(html) {
  if (NO_MIN) return html;
  const { minify: m } = await import('html-minifier-terser');
  return m(html, {
    collapseWhitespace: true,
    conservativeCollapse: true,
    removeComments: true,
    minifyJS: false,         // the bundle is already minified by terser in buildJs — see minifyJs
    minifyCSS: false,          // already minified; re-parsing breaks :is()/color-mix
    ignoreCustomFragments: [/<pre[\s\S]*?<\/pre>/, /<script type="application\/json"[\s\S]*?<\/script>/],
  });
}

// ── one topic ───────────────────────────────────────────────────────────────
async function buildTopic(slug, css) {
  globalThis.__sandboxNames = await sandboxNames(slug);
  const js = await buildJs(slug);
  const metaPath = p('src/meta', slug + '.json');
  const meta = JSON.parse(await readFile(metaPath, 'utf8'));
  const dir = p('src/sections', slug);
  let files = (await readdir(dir)).filter(f => f.endsWith('.html')).sort();
  if (FILES.length) {
    const missing = FILES.filter(f => !files.includes(f));
    if (missing.length) throw new Error(`--files: not found in src/sections/${slug}: ${missing.join(', ')}`);
    files = files.filter(f => FILES.includes(f));
  }
  if (!files.length) throw new Error(`no sections in src/sections/${slug}`);

  let sections = '';
  for (const f of files) sections += await readFile(path.join(dir, f), 'utf8') + '\n';

  lint(sections, slug);

  const blocks = collectBlocks(sections);
  let libJars = [];
  if (meta.deps && !SKIP_VERIFY) {
    const d = await ensureDeps(meta.deps, { log });
    libJars = d.jars;
    log(`  ${d.jars.length} dependency jars (${meta.deps})${d.fetched ? ', ' + d.fetched + ' downloaded' : ''}`);
  }
  const v = await verifyJava(blocks, slug, libJars);

  if (v.failures.length) {
    console.error(`\n  BUILD FAILED — ${v.failures.length} verification failure(s) in ${slug}\n`);
    for (const f of v.failures) console.error(`  [${f.cls}]\n  ${f.detail.split('\n').join('\n  ')}\n`);
    throw new Error('java verification failed');
  }

  sections = renderSteppers(sections);
  sections = renderBlocks(sections);

  const toc = buildToc(sections);

  let html = await readFile(p('src/templates/module.html'), 'utf8');
  const vars = {
    SLUG: slug,
    // meta text lands in element content and attributes, so it is escaped here
    // (a title such as "JPA & Hibernate" would otherwise ship a raw ampersand)
    TITLE: H.esc(meta.title),
    SUBTITLE: H.esc(meta.subtitle || ''),
    LEVEL: H.esc(meta.level),
    TECH: H.esc(meta.tech),
    TIME: H.esc(meta.time),
    UPDATED: H.esc(meta.updated),
    ACCENT: meta.accent || 'amber',
    NEXT: meta.next || '',
    TOC: toc.html,
    SECTIONS: sections,
    CSS: css,
    JS: js,
  };
  html = html.replace(/\{\{(\w+)\}\}/g, (raw, k) => (k in vars ? vars[k] : raw));

  const outHtml = await minify(html);
  const outDir = FILES.length ? p('build/partial') : p('dist');
  await mkdir(outDir, { recursive: true });
  // partial builds are named after their first section so parallel authoring
  // loops never overwrite each other
  const out = path.join(outDir, slug + (FILES.length ? '.' + files[0].replace(/.html$/, '') : '') + '.html');
  await writeFile(out, outHtml, 'utf8');
  if (FILES.length) {
    // a partial build must never leave a stale verify dir behind
    await rm(p('build/verify', slug + '-partial-' + process.pid), { recursive: true, force: true });
  }

  return { out, bytes: Buffer.byteLength(outHtml), sections: toc.count, verify: v };
}

// ── course landing page ─────────────────────────────────────────────────────
async function buildIndex(css, js) {
  const metas = [];
  for (const f of (await readdir(p('src/meta'))).filter(f => f.endsWith('.json')).sort()) {
    const slug = f.replace('.json', '');
    const meta = JSON.parse(await readFile(p('src/meta', f), 'utf8'));
    const dir = p('src/sections', slug);
    const sections = existsSync(dir) ? (await readdir(dir)).filter(x => x.endsWith('.html')).length : 0;
    metas.push({ slug, meta, sections, built: existsSync(p('dist', slug + '.html')) });
  }
  metas.sort((a, b) => (a.meta.order || 99) - (b.meta.order || 99));

  const items = metas.map((m, i) => {
    const ready = m.sections > 0 && m.built;
    const inner =
      `<span class="lm-module-num" aria-hidden="true">${i + 1}</span>` +
      `<span class="lm-module-body">` +
      `<span class="lm-module-title">${H.esc(m.meta.title)}</span>` +
      `<span class="lm-module-sub">${H.esc(m.meta.subtitle || '')}</span>` +
      `<span class="lm-module-meta">` +
      `<span class="lm-badge lm-badge--accent">${H.esc(m.meta.level)}</span>` +
      `<span class="lm-badge">${H.esc(m.meta.tech)}</span>` +
      `<span class="lm-badge">${H.esc(m.meta.time)}</span>` +
      (ready ? `<span class="lm-badge">${m.sections} sections</span>` : `<span class="lm-badge">Coming soon</span>`) +
      `</span></span>`;
    return ready
      ? `<li class="lm-module" data-accent="${H.esc(m.meta.accent || 'amber')}"><a class="lm-module-link" href="${m.slug}.html">${inner}</a></li>`
      : `<li class="lm-module is-soon" data-accent="${H.esc(m.meta.accent || 'amber')}"><span class="lm-module-link" aria-disabled="true">${inner}</span></li>`;
  }).join('\n');

  let html = await readFile(p('src/templates/index.html'), 'utf8');
  const vars = { MODULES: items, CSS: css, JS: js };
  html = html.replace(/\{\{(\w+)\}\}/g, (raw, k) => (k in vars ? vars[k] : raw));
  const outHtml = await minify(html);
  await mkdir(p('dist'), { recursive: true });
  await writeFile(p('dist/index.html'), outHtml, 'utf8');
  return { bytes: Buffer.byteLength(outHtml), modules: metas.length };
}

// ── main ────────────────────────────────────────────────────────────────────
log('· compiling css');
const css = await buildCss();
log(`  ${(css.length / 1024).toFixed(1)} kB css`);
log('· bundling js');
const coreJs = await buildJs(null);          // landing page: core only
log(`  ${(coreJs.length / 1024).toFixed(1)} kB core js (+ per-module sandboxes)`);

const metas = (await readdir(p('src/meta'))).filter(f => f.endsWith('.json')).map(f => f.replace('.json', ''));
const topics = only ? metas.filter(t => t === only) : metas;
// a topic whose section directory is still empty is "coming soon" on the
// landing page and is skipped here rather than failing the whole build
const buildable = [];
for (const t of topics) {
  const dir = p('src/sections', t);
  const n = existsSync(dir) ? (await readdir(dir)).filter(f => f.endsWith('.html')).length : 0;
  if (n) buildable.push(t); else if (only) { console.error(`no sections in src/sections/${t}`); process.exit(1); } else log(`· skipping ${t} (no sections yet)`);
}
if (!topics.length) { console.error(`no topic matching "${only}"`); process.exit(1); }

let failed = false;
for (const t of buildable) {
  log(`· building ${t}`);
  try {
    const r = await buildTopic(t, css);
    const kb = (r.bytes / 1024).toFixed(1);
    const over = r.bytes > 1.5 * 1024 * 1024 ? '  ** OVER 1.5 MB BUDGET **' : '';
    log(`  ${path.relative(ROOT, r.out)}  ${kb} kB  ·  ${r.sections} sections  ·  ` +
        `java verified ${r.verify.ok}/${r.verify.ran}${r.verify.skipped ? ' (skipped)' : ''}${over}`);
  } catch (e) {
    failed = true;
    console.error(`  FAILED: ${e.message}`);
  }
}
if (!FILES.length && !failed) {
  log('· building index');
  const r = await buildIndex(css, coreJs);
  log(`  dist/index.html  ${(r.bytes / 1024).toFixed(1)} kB  ·  ${r.modules} modules`);
}
process.exit(failed ? 1 : 0);
