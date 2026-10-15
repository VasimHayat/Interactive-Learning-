/* Java Advanced §3 — will two threads taking two locks deadlock?
   Pure rule evaluation of the circular-wait condition; nothing is run. */
(function () {
  'use strict';
  var reg = globalThis.LMSandbox.reg, H = globalThis.LMHighlight;

  reg('lock-ordering', function (el) {
    var aSel = el.querySelector('[data-sb-a]');
    var bSel = el.querySelector('[data-sb-b]');
    var modeSel = el.querySelector('[data-sb-mode]');
    var out = el.querySelector('[data-sb-out]');
    var NAME = { 1: 'BOLT-7', 2: 'NUT-3' };

    function cell(label, text, cls) {
      return '<div class="lm-cell' + (cls || '') + '" style="min-width:11rem;text-align:left"><span class="lm-cell-idx">' + H.esc(label) + '</span>' + text + '</div>';
    }

    function render() {
      var a = aSel.value, b = bSel.value, timed = modeSel.value === 'try';
      var sameOrder = a === b;
      var a1 = NAME[a[0]], a2 = NAME[a[1]], b1 = NAME[b[0]], b2 = NAME[b[1]];

      var timeline, kind, title, body;
      if (sameOrder) {
        kind = 'good'; title = 'Deadlock impossible';
        timeline =
          cell('t1', 'A locks <code>' + a1 + '</code>', ' is-hi') +
          cell('t2', 'B wants <code>' + b1 + '</code> — <em>blocked</em>, holding nothing', '') +
          cell('t3', 'A locks <code>' + a2 + '</code>, transfers, releases both', ' is-hi') +
          cell('t4', 'B locks <code>' + b1 + '</code>, then <code>' + b2 + '</code>: done', ' is-hi');
        body = 'Both threads take <code>' + a1 + '</code> first. Whoever gets it owns the only thing the other needs <em>before</em> it waits, so the loser waits while holding nothing. There is no cycle in the wait-for graph, and no cycle means no deadlock — whatever the scheduler does.';
      } else if (!timed) {
        kind = 'danger'; title = 'Deadlock possible';
        timeline =
          cell('t1', 'A locks <code>' + a1 + '</code>', ' is-hi') +
          cell('t2', 'B locks <code>' + b1 + '</code>', ' is-hi') +
          cell('t3', 'A wants <code>' + a2 + '</code> — held by B, <em>waits forever</em>', ' is-bad') +
          cell('t4', 'B wants <code>' + b2 + '</code> — held by A, <em>waits forever</em>', ' is-bad');
        body = 'If the scheduler interleaves the first acquisitions, each thread holds what the other needs. All four conditions hold: the locks are exclusive, each thread holds one while waiting for another, nothing can take a lock away, and the wait-for graph is a cycle (A → B → A). It will not happen on every run — which is exactly why it reaches production.';
      } else {
        kind = 'gotcha'; title = 'Deadlock impossible, livelock possible';
        timeline =
          cell('t1', 'A locks <code>' + a1 + '</code>', ' is-hi') +
          cell('t2', 'B locks <code>' + b1 + '</code>', ' is-hi') +
          cell('t3', 'A <code>tryLock(' + a2 + ')</code> times out — A <em>releases</em> <code>' + a1 + '</code>', '') +
          cell('t4', 'B now gets <code>' + b2 + '</code>, transfers, releases; A retries and succeeds', ' is-hi');
        body = 'A timed acquisition breaks hold-and-wait: a thread that cannot get its second lock gives back its first. The cycle can form, but it cannot persist. The remaining risk is <strong>livelock</strong> — both threads timing out and retrying in perfect lockstep. A small random back-off before each retry breaks the symmetry.';
      }

      var icon = kind === 'good' ? '<path d="M20 6 9 17l-5-5"/>'
               : kind === 'danger' ? '<circle cx="12" cy="12" r="9"/><path d="m15 9-6 6m0-6 6 6"/>'
               : '<path d="M12 9v4m0 4h.01M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/>';

      out.innerHTML =
        '<div class="lm-stepper-label">One unlucky interleaving</div>' +
        '<div class="lm-mem" style="margin:.4rem 0 .9rem">' + timeline + '</div>' +
        '<div class="lm-callout lm-callout--' + kind + '"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">' + icon + '</svg>' +
        '<div><div class="lm-callout-title">' + title + '</div><p class="mb-2">' + body + '</p>' +
        '<p class="mb-0 text-sm"><strong>Rule:</strong> acquire locks in one global order everywhere, or never wait for a second lock while holding a first without a timeout.</p></div></div>';
    }

    aSel.addEventListener('change', render);
    bSel.addEventListener('change', render);
    modeSel.addEventListener('change', render);
    render();
  });
})();
