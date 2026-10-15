# Java Advanced — module outline and authoring contract

Slug `java-advanced` · meta in `src/meta/java-advanced.json` · accent sky · prev "Java Intermediate" · no next (last module).

Running example: the warehouse's `InventoryService` becomes a **concurrent reservation service**:
many picker threads reserving stock at once, a background re-pricing job, a report that must not
see half-updated state, and a JVM that must stay responsive while it does all of that. Reuse the
SKUs `BOLT-7`, `NUT-3`, `CAM-9`, `GIRD-1`, `ROD-4`, `PIN-2`, `HUB-8`; `Product`/`Order` records and
the `Category` enum from Intermediate may be redeclared (nested) where a program needs them.

## Section table (ids, numbers, verified class names and widget names are reserved — do not change)

| File | `id` | `data-num` | `data-title` | Verified programs | Sandbox | Stepper ids |
|---|---|---|---|---|---|---|
| 00-how-to-use.html | how-to-use | i | How to use this module | — | — | — |
| 01-prerequisites.html | prerequisites | ii | Prerequisites | — | — | — |
| 02-objectives.html | objectives | iii | Learning objectives | — | — | — |
| 10-threads.html | threads | 1 | Threads, stacks and the shared heap | `ThreadBasics`, `LostUpdate` | `race-simulator` | `interleaving` |
| 11-memory-model.html | memory-model | 2 | The Java memory model: visibility and happens-before | `Visibility` | `happens-before` | `reordering` |
| 12-locks.html | locks | 3 | synchronized, ReentrantLock and deadlock | `Synchronized`, `LockOrdering` | `lock-ordering` | `monitor` |
| 13-concurrent-collections.html | concurrent-collections | 4 | Atomics and concurrent collections | `Atomics`, `ConcurrentMaps` | `cas-loop` | — |
| 14-executors.html | executors | 5 | Executors, thread pools and Future | `PoolExecutors` (not `Executors` — it would shadow java.util.concurrent.Executors for every other program) | `pool-sizer` | `work-queue` |
| 15-completable-future.html | completable-future | 6 | CompletableFuture: composing asynchronous work | `Composing` | — | `cf-chain` |
| 16-virtual-threads.html | virtual-threads | 7 | Virtual threads and structured concurrency | `VirtualThreads` | `carrier-mount` | — |
| 17-gc.html | gc | 8 | Garbage collection: generations, G1 and ZGC | `Reachability` | `gc-generations` | `g1-cycle` |
| 18-jvm-tuning.html | jvm-tuning | 9 | Heap sizing, JVM flags and reading GC logs | — (bash + sample logs only) | `heap-sizer` | — |
| 19-classloading.html | classloading | 10 | Class loading, linking and initialization | `ClassInit`, `Loaders` | — | `loader-delegation` |
| 20-reflection.html | reflection | 11 | Reflection, annotations and method handles | `Reflection`, `Annotations` | — | — |
| 21-modules.html | modules | 12 | The module system | `ModuleFacts` | `module-graph` | — |
| 22-performance.html | performance | 13 | Measuring: JIT tiers, JMH, JFR and profilers | — (JMH shape shown, not run) | `jit-tiers` | `inlining` |
| 23-serialization.html | serialization | 14 | Serialization, records and the HTTP client | `SerializeRecord` | — | — |
| 80-lab.html | lab | L | Hands-on lab | `ReservationLab` | — | — |
| 81-cheatsheet.html | cheatsheet | C | Cheat sheet | — | — | — |
| 82-interview.html | interview | I | Interview questions | — | — | — |
| 83-quiz.html | quiz | Q | Self-assessment quiz | — | — | — |
| 84-glossary.html | glossary | G | Glossary | — | — | — |
| 85-reading.html | reading | R | Further reading | — | — | — |

Extra verified programs are welcome; name them `<SectionWord><Thing>` so they cannot collide.
A program whose point is an exception uses `data-verify-throws="ExceptionSimpleName"`.

## The determinism rule for this module (read twice)

The build runs every verified program on the local JDK and diffs stdout **byte for byte**. Concurrent
programs are nondeterministic by nature, so a verified program may print only **derived, stable facts**:

- counts and sums read **after** `join()` / `awaitTermination` / `get()` on every future;
- booleans such as `all tasks completed: true`, `final count == expected: true`;
- results **sorted** before printing; never thread names with numbers, never timings, never
  interleaving-dependent lines, never `availableProcessors()`, never free/total memory, never the
  default GC name, never absolute paths.
- A race that is *meant* to lose updates must be shown as a **Simulated** stepper or sandbox, or
  printed as `lost updates: true` only when the program can guarantee it (e.g. 4 threads × 1,000,000
  unsynchronised `count++` on a plain `int` with a `CountDownLatch` start gate — on any real JVM that
  loses updates, but still print the *fact* `count < 4000000: true`, not the number). If you cannot
  guarantee the fact on every machine, do not verify that program; mark it `data-code` without
  `data-verify` and say in prose that the output varies.
- `System.gc()` is a hint; a `WeakReference` demo may loop `while (ref.get() != null) { System.gc(); }`
  with a bounded iteration count and print `collected: true`. Prefer `data-verify-throws` or non-verified
  blocks over anything that could hang the build.
- Deadlock must never actually occur in a verified program: demonstrate the *shape* in a stepper and
  verify the *fix* (lock ordering, `tryLock` with timeout).
- `module-info.java` cannot be placed in the shared verify directory (it would turn every program in the
  module into one Java module). The modules section verifies only `ModuleFacts` (facts such as
  `Object.class.getModule().getName()` → `java.base`, `ModuleFacts.class.getModule().isNamed()` → `false`)
  and shows `module-info.java` plus the `javac --module-path` commands in plain `data-code` / bash blocks.
- JMH and JFR are shown, explained and never run at build time.

## Learning objectives (the quiz tests exactly these)

1. **Explain** what a thread owns (its stack and program counter) and shares (the heap), why `count++` is three operations, and how to read a thread dump.
2. **State** the happens-before rules that make a write visible to another thread, and why `volatile` guarantees visibility but not atomicity.
3. **Choose** between `synchronized`, `ReentrantLock`, `ReadWriteLock` and `StampedLock`, and prevent deadlock by lock ordering or `tryLock` with a timeout.
4. **Use** `AtomicInteger`/`LongAdder`, `ConcurrentHashMap.compute*`/`merge`, `CopyOnWriteArrayList` and `BlockingQueue` correctly, and explain compare-and-swap.
5. **Size** a thread pool for CPU-bound versus I/O-bound work, handle rejected tasks, and shut a pool down so the JVM can exit.
6. **Compose** `CompletableFuture` chains with `thenCompose`, `thenCombine`, `exceptionally`/`handle`, timeouts and `allOf`, and say which thread runs each stage.
7. **Explain** what a virtual thread is, how it mounts on a carrier, what pinning is, and when virtual threads do not help.
8. **Describe** generational collection, what G1 and ZGC each optimise for, the three categories of GC pause, and how to read a GC log line.
9. **Reason** about heap sizing flags (`-Xms`, `-Xmx`, `-XX:MaxRAMPercentage`), metaspace, and the symptoms of an undersized heap versus a leak.
10. **Trace** class loading, linking and initialization order, and the parent-delegation model, and diagnose `NoClassDefFoundError` versus `ClassNotFoundException`.
11. **Use** reflection and annotations safely (and say what they cost), and state when a `MethodHandle` or `VarHandle` is the better tool.
12. **Declare** a module with `requires`, `exports`, `opens` and `uses`/`provides`, and explain strong encapsulation and `--add-opens`.
13. **Measure** before optimising: name the JIT tiers, the JMH pitfalls (dead-code elimination, constant folding, warm-up), and what JFR records.
14. **Serialize** safely: why `Serializable` is a liability, how records serialize, and the shape of an `HttpClient` call with a timeout.

Out of scope: frameworks (Spring, Jakarta), build tools, JNI/FFM in depth (one mention), GraalVM native image (one mention).

## Authoring contract (identical to Intermediate — see README.md and `src/sections/java-intermediate/*` for live templates)

- Section wrapper, eyebrow "Section N", `<h2>`, completion checkbox `data-done-for="<id>"` on every core section and the lab.
- Core sections: motivating explanation (mechanism, not rules), a **Worked example** that is a verified program (or, for §9 and §13, an annotated bash/log block), at least one interactive widget, a gotcha callout, and **Check yourself** with ≥2 questions (`data-check`, `data-live`, `data-explain`, every wrong option explained).
- Ids: `q-<section>-N`, `tab-<section>-x` / `panel-<section>-x`, unique across the module.
- Steppers are **Simulated** (badge, pre-authored steps, every `line` must exist in `code`). An `interleaving` stepper should use `stack` or `vars` to show two threads' program counters side by side.
- Per-topic sandbox handler in `src/js/sandboxes/java-advanced-<name>.js`:
  ```js
  (function () {
    'use strict';
    var reg = globalThis.LMSandbox.reg, H = globalThis.LMHighlight;
    reg('<name>', function (el) { /* read [data-sb-*] controls, write [data-sb-out] */ });
  })();
  ```
  `H.esc()` for interpolated text; `H.renderCode(src, { lang: 'java', noBar: true })` for code. Sandboxes may build `<pre>` strings freely (the JS is minified separately now).
- Prose: `<` and `&` must be written `&lt;` / `&amp;` outside `<pre data-code>` blocks (the HTML minifier fails on raw ones). No `<code>` inside `<option>`.
- Verify in isolation: `node tools/build.mjs --topic=java-advanced --files=10-threads.html,11-memory-model.html` then `npx html-validate build/partial/java-advanced.10-threads.html`.
- Tone: precise, second person, explain the mechanism and the failure it prevents. Cross-reference as "Fundamentals §N", "Intermediate §N", "§N" (this module).
