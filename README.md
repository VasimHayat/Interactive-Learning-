# Learning modules — interactive Java course

A three-module Java course, Basic → Intermediate → Advanced, shipped as one self-contained
HTML file per module plus a landing page. No framework, no network requests, works offline,
prints cleanly. Every Java program shown in a module is **compiled and executed against the
local JDK during the build** and its real stdout is diffed against the output printed on the
page; a mismatch fails the build.

| Module | Slug | Level | Status |
|---|---|---|---|
| Java Fundamentals | `java-basics` | Basic | complete — 23 sections |
| Java Intermediate | `java-intermediate` | Intermediate | complete — 22 sections, 19 verified programs, 7 sandboxes, 6 steppers |
| Java Advanced | `java-advanced` | Advanced | planned — outline in `docs/outline-java-advanced.md` |

## Build

```
npm install
npm run build          # dist/<slug>.html for every module + dist/index.html
npm run check          # build, then html-validate dist/*.html
npm run serve          # static server for dist/ (prints the URL)
```

Requirements: Node 18+, a JDK 21+ on PATH (`javac`, `java`). Without a JDK pass
`--skip-verify`, which builds the pages but does not prove the outputs.

```
node tools/build.mjs --topic=java-basics                      # one module
node tools/build.mjs --topic=java-intermediate --files=10-generics.html,11-nested.html
                                                               # assemble only those sections
                                                               # → build/partial/java-intermediate.10-generics.html
node tools/build.mjs --no-minify                               # readable output for debugging
```

The `--files` form is the authoring loop: it lints, compiles and runs only the named
sections, writes to `build/partial/` instead of `dist/`, and uses a per-process verify
directory, so several authors can iterate on different sections at the same time.

## Layout

```
src/meta/<slug>.json          title, subtitle, level, tech, time, updated, accent, order, next
src/sections/<slug>/NN-*.html one <section class="lm-section"> per file, assembled in name order
src/templates/module.html     page shell ({{TITLE}}, {{TOC}}, {{SECTIONS}}, {{CSS}}, {{JS}} …)
src/templates/index.html      course landing page, one card per meta file
src/scss/                     tokens (light + dark + per-module accent), base, components, print
src/js/highlight.js           tokenizer — used by the build AND the browser so highlighting cannot drift
src/js/core.js                theme, TOC scrollspy, progress, notes, copy, tabs, shortcuts, localStorage
src/js/quiz.js                MCQ / multi-select, scored quiz, ordering, matching, flashcards
src/js/playground.js          step-through executors, core sandboxes, the sandbox registry
src/js/sandboxes/*.js         per-module sandbox handlers, one reg() each, bundled after playground.js
tools/build.mjs               the whole pipeline (css → js → lint → verify → render → minify)
docs/                         module outlines and authoring contracts
```

## Authoring a section

See `docs/outline-java-intermediate.md` for the full contract. The short version:

- **Verified program:** `<pre data-code data-lang="java" data-file="X.java" data-verify="X">` paired
  with `<pre data-output data-for="X">`. The class name is the pairing key and must be unique
  within the module. A program that is meant to fail declares `data-verify-throws="ExceptionName"`.
  Output must be deterministic on any machine — no `now()`, identity hashes, `HashMap` iteration
  order, absolute paths or locale-dependent formatting.
- **Highlighting:** `data-hi="3,4"` highlights lines, `data-bad="7"` marks a wrong line, `data-nobar`
  drops the file bar for fragments.
- **Stepper (Simulated):** `<div class="lm-stepper" data-stepper>` with a
  `<script type="application/json" data-stepper-data data-id="ID">` carrying `code` and `steps`
  (`line`, `vars`, `out`, `note`, optional `stack`). The build pre-renders the code and checks every
  step's line exists.
- **Sandbox:** `<div class="lm-card" data-sandbox="name">` with `[data-sb-*]` controls and a
  `[data-sb-out]` target; the handler is `reg('name', fn)` in `src/js/playground.js` or
  `src/js/sandboxes/<slug>-name.js`. The build fails if no handler is registered.
- **Questions:** `<div class="lm-q" data-q id="q-<section>-N" data-answer="b">` with `.lm-opt`
  labels, a `[data-check]` button (omitted inside the scored `[data-quiz]`), a `[data-live]` region
  and a `[data-explain]` block that says why each wrong option is wrong.
- **Ordering / matching:** `data-order` + `data-solution`, `data-match` with `.lm-chip` → `.lm-slot`.
- **Completion:** `<input type="checkbox" data-done-for="<section id>">` drives the progress bar.

The build's lint rejects: a question with no answer key or fewer than two options, duplicate ids,
a sandbox with no handler, an ordering widget with no solution, a section without `id` /
`data-title` / `data-num`, and literal control characters in markup.

## Design rules

- Tokens are CSS custom properties in `_tokens.scss`; Tailwind utilities and hand-written
  SCSS both resolve to them, so there is exactly one palette. Dark mode redefines only what
  changes, and every per-module accent is defined for both schemes.
- Everything is keyboard-operable and announced through `aria-live` regions; the steppers
  take `←`/`→`, the matching widget works by click as well as drag.
- Budget: a module stays under 1 MB; the build warns past it.
"# Interactive-Learning-" 
