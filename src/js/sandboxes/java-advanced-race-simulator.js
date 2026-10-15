/* Java Advanced §1 — a lost-update simulator.
   Simulated: a seeded scheduler interleaves N threads that each perform
   `count++` as load / add / store. A thread runs for a random slice of
   operations before the scheduler switches; whenever a switch lands between a
   thread's load and its store, the other thread's increments in between are
   overwritten. With the synchronized toggle on, each increment is one
   indivisible step and nothing is lost. It is a model of the race, not a JVM. */
(function () {
  'use strict';
  var reg = globalThis.LMSandbox.reg, H = globalThis.LMHighlight;

  reg('race-simulator', function (el) {
    var thrIn = el.querySelector('[data-sb-threads]');
    var thrOut = el.querySelector('[data-sb-threadsout]');
    var incIn = el.querySelector('[data-sb-inc]');
    var incOut = el.querySelector('[data-sb-incout]');
    var syncIn = el.querySelector('[data-sb-sync]');
    var again = el.querySelector('[data-sb-again]');
    var out = el.querySelector('[data-sb-out]');
    var seed = 1;

    // mulberry32: deterministic for a given seed, so "Run again" is reproducible
    function rng(s) {
      return function () {
        s |= 0; s = s + 0x6D2B79F5 | 0;
        var t = Math.imul(s ^ s >>> 15, 1 | s);
        t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t;
        return ((t ^ t >>> 14) >>> 0) / 4294967296;
      };
    }

    function simulate(threads, perThread, sync, seedValue) {
      var rand = rng(seedValue);
      var shared = 0, trace = [], switches = 0, lostEvents = 0;
      var state = [];
      for (var t = 0; t < threads; t++) state.push({ left: perThread, phase: 0, reg: 0 });
      var runnable = function () { return state.filter(function (s) { return s.left > 0; }).length; };
      var cur = Math.floor(rand() * threads);
      var slice = 1 + Math.floor(rand() * 40);

      while (runnable() > 0) {
        var s = state[cur];
        if (s.left === 0 || slice === 0) {
          // pick another thread that still has work
          var candidates = [];
          for (var i = 0; i < threads; i++) if (state[i].left > 0 && i !== cur) candidates.push(i);
          if (!candidates.length) { candidates = [cur]; }
          var next = candidates[Math.floor(rand() * candidates.length)];
          if (next !== cur) switches++;
          cur = next; s = state[cur];
          slice = 1 + Math.floor(rand() * 40);
        }
        if (sync) {
          shared++; s.left--;
          if (trace.length < 18) trace.push({ t: cur, op: 'lock; load ' + (shared - 1) + '; store ' + shared + '; unlock' });
        } else if (s.phase === 0) {
          s.reg = shared; s.phase = 1;
          if (trace.length < 18) trace.push({ t: cur, op: 'load  → r=' + s.reg });
        } else if (s.phase === 1) {
          s.reg++; s.phase = 2;
          if (trace.length < 18) trace.push({ t: cur, op: 'add   → r=' + s.reg });
        } else {
          if (s.reg <= shared) lostEvents++;    // someone stored since our load: their work is overwritten
          shared = s.reg; s.phase = 0; s.left--;
          if (trace.length < 18) trace.push({ t: cur, op: 'store ← ' + shared });
        }
        slice--;
      }
      return { shared: shared, trace: trace, switches: switches, lostEvents: lostEvents };
    }

    function render() {
      var threads = Number(thrIn.value), per = Number(incIn.value), sync = syncIn.checked;
      thrOut.textContent = threads; incOut.textContent = per;
      var r = simulate(threads, per, sync, seed);
      var expected = threads * per, lost = expected - r.shared;
      var colours = ['var(--lm-info)', 'var(--lm-accent)', 'var(--lm-ok)', 'var(--lm-warn)', 'var(--lm-bad)', '#8b5cf6', '#0d9488', '#db2777'];

      var strip = r.trace.map(function (e) {
        return '<div class="lm-cell" style="border-left:3px solid ' + colours[e.t % colours.length] + ';min-width:0">' +
               '<span class="lm-cell-idx">T' + e.t + '</span>' + H.esc(e.op) + '</div>';
      }).join('');

      out.innerHTML =
        '<dl class="lm-kv">' +
        '<dt>Expected</dt><dd>' + expected + ' (' + threads + ' × ' + per + ')</dd>' +
        '<dt>Final value</dt><dd><strong>' + r.shared + '</strong></dd>' +
        '<dt>Lost updates</dt><dd>' + lost + (expected ? ' (' + (100 * lost / expected).toFixed(1) + '%)' : '') + '</dd>' +
        '<dt>Context switches</dt><dd>' + r.switches + ' · seed ' + seed + '</dd>' +
        '</dl>' +
        '<div class="lm-stepper-label mt-3">First ' + r.trace.length + ' operations the scheduler ran</div>' +
        '<div class="lm-mem" style="margin:.4rem 0 .9rem">' + strip + '</div>' +
        '<div class="lm-callout ' + (lost ? 'lm-callout--danger' : 'lm-callout--good') + '">' +
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">' +
        (lost ? '<circle cx="12" cy="12" r="9"/><path d="m15 9-6 6m0-6 6 6"/>' : '<path d="M20 6 9 17l-5-5"/>') +
        '</svg><div><div class="lm-callout-title">' + (lost ? lost + ' increment' + (lost === 1 ? '' : 's') + ' vanished' : 'Nothing lost') + '</div>' +
        (sync
          ? 'Each increment is one indivisible step: no thread can load the counter while another is between its load and its store. The price is that the threads take turns — the work is serialised at the counter.'
          : (lost
              ? 'Every loss is the same shape: thread A loads the value, thread B loads the <em>same</em> value, both add one, both store — and one of the two increments is overwritten. Nothing throws, nothing warns. Switch on <em>synchronized</em> to see the fix, or press <em>Run again</em> for a different interleaving.'
              : 'This seed happened to switch threads only between complete increments, so nothing was lost — which is exactly why racy code passes its tests and fails in production. Press <em>Run again</em>.')) +
        '</div></div>';
    }

    thrIn.addEventListener('input', render);
    incIn.addEventListener('input', render);
    syncIn.addEventListener('change', render);
    again.addEventListener('click', function () { seed++; render(); });
    render();
  });
})();
