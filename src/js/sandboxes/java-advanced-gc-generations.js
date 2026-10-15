/* Java Advanced §8 — a generational young-collection model.
   Eden holds 1,000 objects; survivor space holds 100; old holds 10,000. Each
   young GC keeps a fraction of eden, ages what is already in survivor, and
   promotes anything that reaches the tenuring threshold or no longer fits.
   A model of the mechanism, not a measurement of any JVM. */
(function () {
  'use strict';
  var reg = globalThis.LMSandbox.reg, H = globalThis.LMHighlight;

  reg('gc-generations', function (el) {
    var rateIn = el.querySelector('[data-sb-rate]');
    var rateOut = el.querySelector('[data-sb-rateout]');
    var survIn = el.querySelector('[data-sb-surv]');
    var survOut = el.querySelector('[data-sb-survout]');
    var tenureIn = el.querySelector('[data-sb-tenure]');
    var tenureOut = el.querySelector('[data-sb-tenureout]');
    var out = el.querySelector('[data-sb-out]');

    var EDEN = 1000, SURVIVOR = 125, OLD = 10000, SECONDS = 10;   // SurvivorRatio=8: each survivor space is eden/8

    function render() {
      var rate = Number(rateIn.value);           // objects allocated per second
      var surv = Number(survIn.value) / 100;     // fraction still live at each collection
      var tenure = Number(tenureIn.value);
      rateOut.textContent = rate.toLocaleString('en-US');
      survOut.textContent = Math.round(surv * 100) + '%';
      tenureOut.textContent = tenure;

      var youngGcs = Math.floor(rate * SECONDS / EDEN);
      var ages = [];                              // survivor space: count of objects at each age
      var promotedByAge = 0, promotedByOverflow = 0, copied = 0, old = 0, oldFullAtGc = null;

      for (var g = 1; g <= youngGcs; g++) {
        // objects already in survivor: a fraction die, the rest age by one
        var next = [];
        for (var a = 0; a < ages.length; a++) {
          var alive = Math.round(ages[a] * surv);
          if (a + 1 >= tenure) { promotedByAge += alive; old += alive; }
          else next[a + 1] = (next[a + 1] || 0) + alive;
        }
        // eden survivors enter survivor space at age 1
        var fromEden = Math.round(EDEN * surv);
        if (tenure <= 1) { promotedByAge += fromEden; old += fromEden; }
        else next[1] = (next[1] || 0) + fromEden;

        // survivor space has a fixed size: the overflow is promoted early, oldest first
        var total = 0;
        for (var k = 0; k < next.length; k++) total += next[k] || 0;
        var overflow = Math.max(0, total - SURVIVOR);
        for (var k2 = next.length - 1; k2 >= 0 && overflow > 0; k2--) {
          var take = Math.min(overflow, next[k2] || 0);
          next[k2] -= take; overflow -= take; promotedByOverflow += take; old += take;
        }
        total = Math.min(total, SURVIVOR);
        copied += fromEden + total;                 // every live object in young is copied at every GC
        ages = next;
        if (oldFullAtGc === null && old >= OLD) oldFullAtGc = g;
      }

      var survivorNow = ages.reduce(function (s, n) { return s + (n || 0); }, 0);
      var secondsToOldFull = old > 0 ? (OLD / old) * SECONDS : Infinity;

      var kind, title, body;
      if (youngGcs === 0) {
        kind = 'note'; title = 'Eden never filled';
        body = 'At this allocation rate eden (1,000 objects) does not fill in ' + SECONDS + ' seconds, so no young collection ran. Low allocation is the cheapest GC tuning there is.';
      } else if (surv >= 0.4) {
        kind = 'gotcha'; title = 'The generational hypothesis is not holding';
        body = 'Nearly half of everything allocated is still alive at each collection, so each young GC copies ' + Math.round(copied / youngGcs).toLocaleString('en-US') + ' objects and the old generation fills in about ' + (secondsToOldFull === Infinity ? '∞' : Math.round(secondsToOldFull)) + ' s. Copying collectors assume most objects die young; a workload that keeps most of them is better served by a bigger old generation and a concurrent collector (G1 or ZGC) than by a bigger eden.';
      } else if (promotedByOverflow > 0) {
        kind = 'gotcha'; title = 'Survivor space overflowed: premature promotion';
        body = Math.round(promotedByOverflow / (promotedByAge + promotedByOverflow) * 100) + '% of what reached the old generation got there because survivor space was full, not because it was old. Those objects will mostly die soon — in the old generation, where only a full or mixed collection can reclaim them. The JVM fix is a larger young generation or a smaller <code>-XX:SurvivorRatio</code>; the application fix is to allocate less that lives "a little while".';
      } else {
        kind = 'good'; title = 'Healthy generational behaviour';
        body = youngGcs + ' young collection' + (youngGcs === 1 ? '' : 's') + ' in ' + SECONDS + ' s, each copying only the ' + Math.round(copied / youngGcs).toLocaleString('en-US') + ' live objects rather than the ' + EDEN.toLocaleString('en-US') + ' allocated. The old generation grows by ' + old.toLocaleString('en-US') + ' objects per ' + SECONDS + ' s and would need an old-generation (mixed) collection roughly every ' + (secondsToOldFull === Infinity ? '∞' : Math.round(secondsToOldFull)) + ' s.';
      }

      var ageRows = ages.map(function (n, a) {
        return n ? '<div class="lm-cell is-hi"><span class="lm-cell-idx">age ' + a + '</span>' + n + '</div>' : '';
      }).join('') || '<div class="lm-cell is-empty"><span class="lm-cell-idx">survivor</span>empty</div>';

      out.innerHTML =
        '<dl class="lm-kv">' +
        '<dt>Young GCs</dt><dd>' + youngGcs + ' in ' + SECONDS + ' s (eden fills every ' + (rate ? (EDEN / rate).toFixed(2) : '∞') + ' s)</dd>' +
        '<dt>Copied in total</dt><dd>' + copied.toLocaleString('en-US') + ' object copies — the real cost of a young GC is proportional to the <em>live</em> set, not to eden</dd>' +
        '<dt>Promoted to old</dt><dd>' + old.toLocaleString('en-US') + ' (' + promotedByAge.toLocaleString('en-US') + ' by reaching age ' + tenure + ', ' + promotedByOverflow.toLocaleString('en-US') + ' by survivor overflow)</dd>' +
        '<dt>Old generation</dt><dd>' + Math.min(100, Math.round(old / OLD * 100)) + '% of ' + OLD.toLocaleString('en-US') + ' after ' + SECONDS + ' s' + (oldFullAtGc ? ' — full at young GC #' + oldFullAtGc + ': a mixed/full collection is needed' : '') + '</dd>' +
        '</dl>' +
        '<div class="lm-stepper-label mt-3">Survivor space now (' + survivorNow + ' of ' + SURVIVOR + '), by age</div>' +
        '<div class="lm-mem" style="margin:.4rem 0 .9rem">' + ageRows + '</div>' +
        '<div class="lm-callout lm-callout--' + kind + '">' +
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">' +
        (kind === 'good' ? '<path d="M20 6 9 17l-5-5"/>' : kind === 'gotcha' ? '<path d="M12 9v4m0 4h.01M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/>' : '<circle cx="12" cy="12" r="9"/><path d="M12 16v-5m0-3h.01"/>') +
        '</svg><div><div class="lm-callout-title">' + H.esc(title) + '</div>' + body + '</div></div>';
    }

    rateIn.addEventListener('input', render);
    survIn.addEventListener('input', render);
    tenureIn.addEventListener('input', render);
    render();
  });
})();
