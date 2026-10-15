/* Sandbox: HashMap capacity, threshold and resize behaviour.
   Models the JDK rule exactly (resize when size exceeds capacity × load factor,
   capacity doubles) and distributes entries with a fixed multiplicative hash so
   the bucket strip is deterministic. */
(function () {
  'use strict';
  var reg = globalThis.LMSandbox.reg, H = globalThis.LMHighlight;

  reg('hashmap-resize', function (el) {
    var nIn = el.querySelector('[data-sb-n]');
    var nOut = el.querySelector('[data-sb-nout]');
    var lfIn = el.querySelector('[data-sb-lf]');
    var capIn = el.querySelector('[data-sb-cap]');
    var out = el.querySelector('[data-sb-out]');

    function spread(h) { return (h ^ (h >>> 16)) | 0; }
    function keyHash(i) { return Math.imul(i + 1, 0x9E3779B1) | 0; }   // a stand-in for String.hashCode

    function render() {
      var n = Number(nIn.value), lf = Number(lfIn.value), cap = Number(capIn.value);
      nOut.textContent = n;
      var threshold = Math.floor(cap * lf), resizes = 0, rehashed = 0, events = [];
      for (var i = 1; i <= n; i++) {
        if (i > threshold) {                 // the JDK test is ++size > threshold, after the put
          rehashed += i - 1;
          events.push('after entry ' + i + ': ' + cap + ' → ' + (cap * 2) + ' buckets, ' + (i - 1) + ' entries rehashed');
          cap *= 2; threshold = Math.floor(cap * lf); resizes++;
        }
      }
      var buckets = new Array(cap).fill(0);
      for (var k = 0; k < n; k++) buckets[spread(keyHash(k)) & (cap - 1)]++;
      var occupied = buckets.filter(function (b) { return b > 0; }).length;
      var longest = Math.max.apply(null, buckets.concat([0]));
      var avg = occupied ? (n / occupied) : 0;

      var strip = '';
      for (var b = 0; b < 16; b++) {
        var c = buckets[b];
        strip += '<div class="lm-cell' + (c ? ' is-hi' : ' is-empty') + (c >= 8 ? ' is-bad' : '') + '"><span class="lm-cell-idx">' + b + '</span>' + (c ? c : '·') + '</div>';
      }

      out.innerHTML =
        '<dl class="lm-kv">' +
        '<dt>Entries</dt><dd>' + n + '</dd>' +
        '<dt>Capacity</dt><dd>' + cap + ' buckets (always a power of two)</dd>' +
        '<dt>Threshold</dt><dd>' + threshold + ' = ' + cap + ' × ' + lf + ' — the next resize happens when size exceeds this</dd>' +
        '<dt>Resizes so far</dt><dd>' + resizes + (resizes ? ' (' + rehashed + ' entry re-insertions in total)' : '') + '</dd>' +
        '<dt>Occupied buckets</dt><dd>' + occupied + ' of ' + cap + '; longest chain ' + longest + '; mean chain in occupied buckets ' + avg.toFixed(2) + '</dd>' +
        '</dl>' +
        '<div class="lm-stepper-label mt-3">Buckets 0–15 of ' + cap + ' (entries per bucket)</div>' +
        '<div class="lm-mem" style="margin:.4rem 0 .9rem">' + strip + '</div>' +
        (events.length
          ? '<details class="lm-reveal"><summary>Resize log (' + events.length + ')</summary><div class="lm-reveal-body"><ul class="mb-0 text-sm">' +
            events.map(function (e) { return '<li>' + H.esc(e) + '</li>'; }).join('') + '</ul></div></details>'
          : '<p class="text-sm text-ink-muted mb-0">No resize yet: the table is still under its threshold.</p>') +
        '<div class="lm-callout ' + (longest >= 8 ? 'lm-callout--gotcha' : 'lm-callout--note') + '" style="margin-top:.9rem">' +
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 16v-5m0-3h.01"/></svg><div>' +
        (longest >= 8
          ? 'A chain has reached ' + longest + '. With a table of at least 64 buckets the JDK converts that bin to a red-black tree; below 64 it resizes instead. Either way, this is the load factor working: at 0.75 a chain this long is rare with a decent <code>hashCode</code>.'
          : 'Every resize re-inserts every entry, so a map that will hold ' + (n || 'N') + ' entries is cheaper to create with <code>new HashMap&lt;&gt;(' + (n ? Math.ceil(n / 0.75) : 'N / 0.75') + ')</code> than to let it grow ' + (resizes ? resizes + ' time' + (resizes === 1 ? '' : 's') : 'repeatedly') + '.') +
        '</div></div>';
    }

    nIn.addEventListener('input', render);
    lfIn.addEventListener('change', render);
    capIn.addEventListener('change', render);
    render();
  });
})();
