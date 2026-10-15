/* Java Advanced §2 — which synchronisation makes a write visible?
   Pure rule lookup against JLS §17.4.5; nothing is executed. */
(function () {
  'use strict';
  var reg = globalThis.LMSandbox.reg, H = globalThis.LMHighlight;

  reg('happens-before', function (el) {
    var sel = el.querySelector('[data-sb-sync]');
    var out = el.querySelector('[data-sb-out]');

    var CASES = {
      none: {
        ok: false, rule: 'No edge',
        writer: 'reserved = 42;', reader: 'int r = reserved;',
        why: 'There is no happens-before edge between the two threads, so this is a <strong>data race</strong>. The reader may see 0 forever: the JIT is allowed to hoist the read out of a loop, and a core is allowed to keep serving a stale cache line. It may also see 42 immediately. Both are correct executions.'
      },
      'volatile': {
        ok: true, rule: 'Volatile write → subsequent volatile read',
        writer: 'reserved = 42;\nready = true;              // volatile write', reader: 'if (ready) {               // volatile read saw true\n    int r = reserved;      // guaranteed 42\n}',
        why: 'A volatile write happens-before every later read of that same volatile. Because program order gives <code>reserved = 42</code> → <code>ready = true</code>, and transitivity chains the edges, the plain write is visible too. This is "piggybacking" and it is how <code>ConcurrentHashMap</code> and the JDK’s own classes publish state.'
      },
      'sync-same': {
        ok: true, rule: 'Monitor unlock → subsequent lock of the same monitor',
        writer: 'synchronized (lock) { reserved = 42; }', reader: 'synchronized (lock) { int r = reserved; }',
        why: 'Releasing a monitor happens-before every later acquisition of <em>that</em> monitor. Everything the writer did before the unlock is visible to whoever locks next. Note the reader must lock too: a synchronized writer and an unsynchronized reader is still a race.'
      },
      'sync-diff': {
        ok: false, rule: 'Different monitors: no edge',
        writer: 'synchronized (lockA) { reserved = 42; }', reader: 'synchronized (lockB) { int r = reserved; }',
        why: 'The edge is per monitor. Unlocking <code>lockA</code> says nothing about a thread that later locks <code>lockB</code>. This is the classic mistake of synchronising "a lot" rather than on the <em>same</em> object — it compiles, it is slower, and it is still a data race.'
      },
      start: {
        ok: true, rule: 'Thread.start() → every action in the started thread',
        writer: 'reserved = 42;\nt.start();', reader: '// inside t.run():\nint r = reserved;         // guaranteed 42',
        why: 'Everything the parent did before calling <code>start()</code> is visible to the child. That is why it is safe to configure an object and then hand it to a new thread without any lock.'
      },
      join: {
        ok: true, rule: 'Every action in a thread → Thread.join() returning',
        writer: '// inside t.run():\nreserved = 42;', reader: 't.join();\nint r = reserved;         // guaranteed 42',
        why: 'When <code>join()</code> returns, the joined thread has terminated, and all of its writes are visible. The same edge exists for <code>isAlive()</code> returning <code>false</code>. This is why the verified programs in this module read their results only after joining.'
      },
      latch: {
        ok: true, rule: 'countDown() → await() returning',
        writer: 'reserved = 42;\nlatch.countDown();', reader: 'latch.await();\nint r = reserved;         // guaranteed 42',
        why: 'Every class in <code>java.util.concurrent</code> documents its edges: for <code>CountDownLatch</code>, actions before <code>countDown()</code> happen-before actions after a corresponding <code>await()</code> returns. The same holds for <code>Semaphore.release/acquire</code>, <code>BlockingQueue.put/take</code> and <code>Future.get()</code>.'
      },
      atomic: {
        ok: true, rule: 'Atomic write → subsequent atomic read (volatile semantics)',
        writer: 'reserved = 42;\ncounter.incrementAndGet();', reader: 'if (counter.get() > 0) {\n    int r = reserved;      // guaranteed 42\n}',
        why: 'The atomic classes have the memory effects of <code>volatile</code>: a write (or read-modify-write) happens-before any later read that observes it. So an <code>AtomicInteger</code> can publish other state exactly as a volatile field can.'
      },
      executor: {
        ok: true, rule: 'submit() → task runs → get() returns',
        writer: 'reserved = 42;\nFuture<?> f = pool.submit(task);', reader: 'f.get();\nint r = reserved;         // guaranteed 42',
        why: 'Actions before <code>submit()</code> happen-before the task begins, and actions in the task happen-before <code>get()</code> returns. Hand-off through an executor is safe in both directions, with no explicit lock anywhere.'
      }
    };

    function render() {
      var c = CASES[sel.value];
      out.innerHTML =
        '<div class="lm-stepper-grid" style="margin-bottom:.8rem">' +
        '<div class="lm-stepper-pane"><div class="lm-stepper-label">Writer thread</div>' + H.renderCode(c.writer, { lang: 'java', noBar: true }) + '</div>' +
        '<div class="lm-stepper-pane"><div class="lm-stepper-label">Reader thread</div>' + H.renderCode(c.reader, { lang: 'java', noBar: true }) + '</div>' +
        '</div>' +
        '<div class="lm-callout ' + (c.ok ? 'lm-callout--good' : 'lm-callout--danger') + '">' +
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">' +
        (c.ok ? '<path d="M20 6 9 17l-5-5"/>' : '<circle cx="12" cy="12" r="9"/><path d="m15 9-6 6m0-6 6 6"/>') +
        '</svg><div><div class="lm-callout-title">' + (c.ok ? 'Guaranteed to see 42' : 'Not guaranteed — a data race') + '</div>' +
        '<p style="margin:0 0 .3rem"><strong>' + H.esc(c.rule) + '</strong></p>' +
        '<p style="margin:0">' + c.why + '</p></div></div>';
    }

    sel.addEventListener('change', render);
    render();
  });
})();
