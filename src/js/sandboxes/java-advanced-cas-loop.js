/* Java Advanced §4 — a compare-and-swap retry loop, one round per click.
   Simulated: every live thread reads the same value, computes value-1, and
   exactly one CAS wins per round; the rest retry. Deterministic winner order. */
(function () {
  'use strict';
  var reg = globalThis.LMSandbox.reg, H = globalThis.LMHighlight;

  reg('cas-loop', function (el) {
    var nIn = el.querySelector('[data-sb-n]');
    var nOut = el.querySelector('[data-sb-nout]');
    var stepBtn = el.querySelector('[data-sb-step]');
    var resetBtn = el.querySelector('[data-sb-reset]');
    var out = el.querySelector('[data-sb-out]');

    var state;

    function reset() {
      var n = Number(nIn.value);
      nOut.textContent = n;
      state = { n: n, value: 600, round: 0, totalCas: 0, threads: [] };
      for (var i = 0; i < n; i++) state.threads.push({ id: i + 1, attempts: 0, done: false, last: '' });
      render();
    }

    function step() {
      var live = state.threads.filter(function (t) { return !t.done; });
      if (!live.length) return;
      state.round++;
      var expected = state.value;
      // the winner rotates so every thread eventually succeeds, as an unfair
      // scheduler would (eventually) allow
      var winner = live[(state.round - 1) % live.length];
      live.forEach(function (t) {
        t.attempts++;
        state.totalCas++;
        if (t === winner) {
          t.done = true;
          t.last = 'CAS(' + expected + ' → ' + (expected - 1) + ') succeeded';
        } else {
          t.last = 'CAS(' + expected + ' → ' + (expected - 1) + ') failed: value is now ' + (expected - 1);
        }
      });
      state.value = expected - 1;
      render();
    }

    function render() {
      var live = state.threads.filter(function (t) { return !t.done; }).length;
      var rows = state.threads.map(function (t) {
        return '<tr class="' + (t.done ? '' : 'is-changed') + '"><td>thread ' + t.id + '</td><td>' +
               (t.done ? 'done after ' + t.attempts + ' attempt' + (t.attempts === 1 ? '' : 's') : 'retrying — ' + t.attempts + ' so far') +
               (t.last ? '<br><span style="color:var(--lm-text-subtle)">' + H.esc(t.last) + '</span>' : '') + '</td></tr>';
      }).join('');
      var ideal = state.n, worst = state.n * (state.n + 1) / 2;
      out.innerHTML =
        '<dl class="lm-kv">' +
        '<dt>units</dt><dd>' + state.value + ' (shared AtomicInteger)</dd>' +
        '<dt>round</dt><dd>' + state.round + (live ? ' — ' + live + ' thread' + (live === 1 ? '' : 's') + ' still contending' : ' — all threads finished') + '</dd>' +
        '<dt>CAS attempts</dt><dd>' + state.totalCas + ' for ' + (state.n - live) + ' successful update' + (state.n - live === 1 ? '' : 's') +
        (live ? '' : ' — ' + ideal + ' would be ideal; ' + worst + ' is the worst case for ' + state.n + ' threads') + '</dd>' +
        '</dl>' +
        '<table class="lm-vartable" style="margin-top:.6rem"><tbody>' + rows + '</tbody></table>' +
        (live ? '' :
          '<div class="lm-callout lm-callout--note" style="margin-top:.9rem"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 16v-5m0-3h.01"/></svg>' +
          '<div>Every update landed and the value is exactly ' + state.value + ': correctness never depended on the scheduler. The cost was ' + state.totalCas + ' CAS instructions instead of ' + ideal + ' — wasted work that grows with the square of the contention, which is what <code>LongAdder</code> avoids by giving each thread its own cell.</div></div>');
      stepBtn.disabled = !live;
    }

    nIn.addEventListener('input', reset);
    stepBtn.addEventListener('click', step);
    resetBtn.addEventListener('click', reset);
    reset();
  });
})();
