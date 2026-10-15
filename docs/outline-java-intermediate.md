# Java Intermediate — module outline and authoring contract

Slug `java-intermediate` · meta in `src/meta/java-intermediate.json` · accent violet ·
prev "Java Fundamentals" · next "Java Advanced".

Running example: the same warehouse. `Product` (sku, name, priceInCents, `Category` enum),
`Order` / `OrderLine` (now records), `InventoryService`, a `Discount` family (sealed),
and a reporting pipeline built with streams. SKUs used in Fundamentals: `BOLT-7`, `NUT-3`,
`CAM-9`, `GIRD-1`, `ROD-4`, `PIN-2`, `HUB-8`, `WIDGET-1`.

## Section table (ids, numbers, verified class names, widget names are reserved — do not change)

| File | `id` | `data-num` | `data-title` | Verified programs (`data-verify`) | Sandbox (`data-sandbox`) | Stepper ids |
|---|---|---|---|---|---|---|
| 00-how-to-use.html | how-to-use | i | How to use this module | — | — | — |
| 01-prerequisites.html | prerequisites | ii | Prerequisites | — | — | — |
| 02-objectives.html | objectives | iii | Learning objectives | — | — | — |
| 10-generics.html | generics | 1 | Generics in depth | `Pecs`, `Bounds` | `wildcard-picker` | `erasure` |
| 11-nested.html | nested | 2 | Nested, inner, local and anonymous classes | `Nested` | — | `inner-capture` |
| 12-interfaces-enums.html | interfaces-enums | 3 | Interfaces and enums in depth | `DefaultMethods`, `Enums` | `diamond-resolver` | — |
| 13-lambdas.html | lambdas | 4 | Lambdas, functional interfaces and method references | `Lambdas` | `method-ref` | `target-typing` |
| 14-streams.html | streams | 5 | Streams: pipelines and laziness | `StreamLazy`, `StreamPipeline` | — | `lazy-eval` |
| 15-collectors.html | collectors | 6 | Collectors and grouping | `Grouping` | `grouping-builder` | — |
| 16-optional.html | optional | 7 | Optional | `Optionals` | — | `optional-chain` |
| 17-records.html | records | 8 | Records | `Records` | — | — |
| 18-sealed-patterns.html | sealed-patterns | 9 | Sealed types, switch expressions and pattern matching | `SealedSwitch`, `Patterns` | `switch-exhaustive` | — |
| 19-comparators.html | comparators | 10 | Comparable, Comparator and immutability | `Sorting`, `Immutable` | — | `view-vs-copy` |
| 20-collection-internals.html | collection-internals | 11 | Inside the collections | `MapInternals` | `hashmap-resize` | — |
| 21-java-time.html | java-time | 12 | Dates and times with java.time | `Times` | `period-vs-duration` | — |
| 22-files-io.html | files-io | 13 | Files, paths and I/O | `FilesDemo` | — | — |
| 80-lab.html | lab | L | Hands-on lab | `OrderLab` | — | — |
| 81-cheatsheet.html | cheatsheet | C | Cheat sheet | — | — | — |
| 82-interview.html | interview | I | Interview questions | — | — | — |
| 83-quiz.html | quiz | Q | Self-assessment quiz | — | — | — |
| 84-glossary.html | glossary | G | Glossary | — | — | — |
| 85-reading.html | reading | R | Further reading | — | — | — |

Extra verified programs are welcome; name them `<SectionWord><Thing>` (e.g. `GenericsInvariance`)
so they cannot collide with another section. A program expected to fail uses
`data-verify-throws="ExceptionSimpleName"`.

## Learning objectives (the quiz tests exactly these)

1. **Choose** the wildcard (`? extends`, `? super`, unbounded, or none) for a given API from the PECS rule, and explain what erasure prevents.
2. **Distinguish** static nested, inner, local and anonymous classes, and state what each can capture and why the captured variable must be effectively final.
3. **Resolve** which `default` method wins when a class inherits two, and design an enum with per-constant behaviour.
4. **Convert** any lambda to a method reference (and back), name its functional interface, and predict what `this` means inside each.
5. **Trace** a stream pipeline element by element — laziness, short-circuiting and stateful operations — and say which operations force a full pass.
6. **Build** a `groupingBy` with a downstream collector, and choose between `toMap` merge strategies.
7. **Use** `Optional` as a return type without `get()`, and name the three places it should never appear.
8. **Write** a record with a validating compact constructor, and explain why its immutability is shallow.
9. **Model** a closed hierarchy with `sealed` and consume it with an exhaustive pattern `switch` that needs no `default`.
10. **Explain** what `HashMap` does on resize, why a mutable key breaks it, and the difference between an unmodifiable view and an immutable copy.
11. **Compute** with `java.time` correctly across a DST change, and choose `Duration` versus `Period`.
12. **Read and write** files with `java.nio.file` using an explicit charset and a stream that is always closed.

Out of scope (Java Advanced): threads, the memory model, locks, executors, CompletableFuture,
virtual threads, GC, JVM tuning, class loading, reflection, annotations processing, modules, JNI.

## Authoring contract (same as Fundamentals — see `src/sections/java-basics/*` for live templates)

- Every section: `<section class="lm-section" id="…" data-title="…" data-num="…">`, `<p class="lm-eyebrow">Section N</p>`, `<h2>`, and for core sections + lab a closing `<label class="lm-check mt-6" data-print="hide"><input type="checkbox" data-done-for="<id>"> Mark this section complete</label>`.
- Core sections contain, in roughly this order: a motivating explanation (why, not just what), a **Worked example** that is a verified program against the warehouse domain, at least one interactive widget (stepper, sandbox, ordering or matching), a gotcha callout, and **Check yourself** with at least two questions that each have `data-check`, `data-live`, `data-explain`, and an explanation that says why every wrong option is wrong.
- Question ids: `q-<section id>-N`. Tab ids: `tab-<section id>-<x>` / `panel-<section id>-<x>`. All ids are unique across the whole module.
- `<pre data-code data-lang="java" data-file="X.java" data-verify="X">` must be a complete compilable program with `public class X` and `main`; pair it with `<pre data-output data-for="X">`. Output must be deterministic: no `now()`, no `hashCode()` of identity, no `HashSet` iteration order unless it is the point and is stable, no thread timing.
- Steppers are **Simulated**: carry the badge, pre-authored steps, `line` numbers must exist in `code`.
- A per-topic sandbox handler lives in `src/js/sandboxes/java-intermediate-<name>.js`:
  ```js
  (function () {
    'use strict';
    var reg = globalThis.LMSandbox.reg, H = globalThis.LMHighlight;
    reg('<name>', function (el) { /* read [data-sb-*] controls, write [data-sb-out] */ });
  })();
  ```
  Use `H.esc()` for any text you interpolate. Build any HTML you insert from the same `.lm-callout`, `.lm-kv`, `.lm-cell`, `.lm-mem` classes the core sandboxes use.
- Verify in isolation: `node tools/build.mjs --topic=java-intermediate --files=10-generics.html,11-nested.html` then `npx html-validate build/partial/java-intermediate.10-generics.html`. The build compiles and runs every verified program against the local JDK 21 and fails on any output mismatch, missing answer key, duplicate id, or unregistered sandbox.
- Tone: precise, explanatory, second person, no filler. Prefer "the compiler rejects this because…" over "this is bad". Cross-reference Fundamentals as "Fundamentals §N" and sections within this module as "§N".
