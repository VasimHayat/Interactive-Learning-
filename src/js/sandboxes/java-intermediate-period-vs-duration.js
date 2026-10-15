/* Java Intermediate §12 — Period versus Duration across a DST change.
   Simulated: the arithmetic is reproduced in the browser from the same rules
   java.time applies for Europe/London in 2026 (BST from 29 Mar 01:00 UTC to
   25 Oct 01:00 UTC). It is a model of ZonedDateTime, not a JVM. */
(function () {
  'use strict';
  var reg = globalThis.LMSandbox.reg, H = globalThis.LMHighlight;

  reg('period-vs-duration', function (el) {
    var HOUR = 3600000;
    var BST_START = Date.UTC(2026, 2, 29, 1, 0);   // 01:00 UTC → clocks go forward
    var BST_END   = Date.UTC(2026, 9, 25, 1, 0);   // 01:00 UTC → clocks go back

    var startSel = el.querySelector('[data-sb-start]');
    var amount = el.querySelector('[data-sb-amount]');
    var amountOut = el.querySelector('[data-sb-amountout]');
    var unitSel = el.querySelector('[data-sb-unit]');
    var out = el.querySelector('[data-sb-out]');

    function offsetAt(utc) { return utc >= BST_START && utc < BST_END ? 1 : 0; }

    // A "local" value is a wall-clock date-time encoded as if it were UTC, so
    // Date.UTC arithmetic works on calendar fields without any zone involved.
    function toInstant(local) {
      var valid = [];
      [0, 1].forEach(function (off) {
        var utc = local - off * HOUR;
        if (offsetAt(utc) === off) valid.push(utc);
      });
      if (valid.length === 1) return { utc: valid[0], kind: 'normal' };
      if (valid.length === 2) return { utc: Math.min(valid[0], valid[1]), kind: 'overlap' };   // earlier offset wins
      return { utc: local, kind: 'gap' };   // shifted later by the gap length, later offset
    }
    function fromInstant(utc) { var off = offsetAt(utc); return { local: utc + off * HOUR, off: off, utc: utc }; }

    function parts(local) {
      var d = new Date(local);
      return { y: d.getUTCFullYear(), m: d.getUTCMonth(), d: d.getUTCDate(), h: d.getUTCHours(), mi: d.getUTCMinutes() };
    }
    function daysIn(y, m) { return new Date(Date.UTC(y, m + 1, 0)).getUTCDate(); }
    function pad(n) { return (n < 10 ? '0' : '') + n; }
    function fmt(z) {
      var p = parts(z.local);
      return p.y + '-' + pad(p.m + 1) + '-' + pad(p.d) + 'T' + pad(p.h) + ':' + pad(p.mi) +
             (z.off ? '+01:00' : 'Z') + '[Europe/London]';
    }
    function fmtLocal(local) {
      var p = parts(local);
      return p.y + '-' + pad(p.m + 1) + '-' + pad(p.d) + 'T' + pad(p.h) + ':' + pad(p.mi);
    }

    function render() {
      var n = Number(amount.value);
      amountOut.textContent = n;
      var startLocal = Date.UTC.apply(null, startSel.value.split(/[-T:]/).map(function (x, i) { return i === 1 ? Number(x) - 1 : Number(x); }));
      var start = fromInstant(toInstant(startLocal).utc);
      var unit = unitSel.value;
      var expr, result, clamped = false, resolved = 'normal', calendar;

      if (unit === 'months' || unit === 'days') {
        calendar = true;
        var p = parts(start.local);
        var y = p.y, m = p.m, d = p.d;
        if (unit === 'months') {
          m += n; y += Math.floor(m / 12); m = ((m % 12) + 12) % 12;
          var max = daysIn(y, m);
          if (d > max) { d = max; clamped = true; }
        } else { d += n; }
        var local2 = Date.UTC(y, m, d, p.h, p.mi);
        var r = toInstant(local2);
        resolved = r.kind;
        result = fromInstant(r.utc);
        expr = unit === 'months' ? 'placed.plus(Period.ofMonths(' + n + '))' : 'placed.plusDays(' + n + ')';
      } else {
        calendar = false;
        result = fromInstant(start.utc + n * 24 * HOUR);
        expr = unit === 'durationDays' ? 'placed.plus(Duration.ofDays(' + n + '))' : 'placed.plusHours(' + (24 * n) + ')';
      }

      var elapsed = (result.utc - start.utc) / HOUR;
      var sameWall = parts(start.local).h === parts(result.local).h && parts(start.local).mi === parts(result.local).mi;
      var crossed = start.off !== result.off;

      var notes = [];
      if (calendar) {
        notes.push('A <strong>calendar</strong> amount: the date fields were advanced and the wall-clock time was kept, then the result was re-resolved against the zone rules.');
      } else {
        notes.push('An <strong>elapsed</strong> amount: exactly ' + (24 * n) + ' hours were added to the instant, and the wall clock is whatever the zone says at that moment.');
      }
      if (clamped) notes.push('The day-of-month did not exist in the target month, so java.time <strong>clamped</strong> it to the last valid day rather than rolling into the next month.');
      if (crossed) notes.push('The result sits on the <strong>other side of a DST change</strong>: the offset went from ' + (start.off ? '+01:00' : 'Z') + ' to ' + (result.off ? '+01:00' : 'Z') + ', which is why elapsed time and wall-clock time disagree here.');
      if (resolved === 'gap') notes.push('The target wall-clock time falls in the <strong>spring gap</strong> and does not exist. java.time moved it later by the length of the gap, with the later offset.');
      if (resolved === 'overlap') notes.push('The target wall-clock time falls in the <strong>autumn overlap</strong> and exists twice. java.time chose the earlier offset (+01:00); <code>withLaterOffsetAtOverlap()</code> picks the other.');

      out.innerHTML =
        '<dl class="lm-kv">' +
        '<dt>Start</dt><dd><code>' + H.esc(fmt(start)) + '</code></dd>' +
        '<dt>Expression</dt><dd><code>' + H.esc(expr) + '</code></dd>' +
        '<dt>Result</dt><dd><code>' + H.esc(fmt(result)) + '</code></dd>' +
        '<dt>Elapsed</dt><dd>' + elapsed + ' hours' + (unit !== 'months' && elapsed !== 24 * n ? ' <span class="text-caption text-ink-subtle">(not ' + (24 * n) + ')</span>' : '') + '</dd>' +
        '<dt>Wall clock</dt><dd>' + (sameWall ? 'kept — still ' + H.esc(fmtLocal(start.local).slice(11)) : 'moved — now ' + H.esc(fmtLocal(result.local).slice(11))) + '</dd>' +
        '</dl>' +
        '<div class="lm-callout ' + (crossed || clamped || resolved !== 'normal' ? 'lm-callout--gotcha' : 'lm-callout--note') + '" style="margin:.9rem 0 0">' +
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">' +
        (crossed || clamped || resolved !== 'normal'
          ? '<path d="M12 9v4m0 4h.01M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/>'
          : '<circle cx="12" cy="12" r="9"/><path d="M12 16v-5m0-3h.01"/>') +
        '</svg><div><div class="lm-callout-title">' + (calendar ? 'Period arithmetic' : 'Duration arithmetic') + '</div>' +
        notes.map(function (s) { return '<p style="margin:0 0 .4rem">' + s + '</p>'; }).join('').replace(/<p style="margin:0 0 \.4rem">([^]*)<\/p>$/, '<p style="margin:0">$1</p>') +
        '</div></div>';
    }

    startSel.addEventListener('change', render);
    amount.addEventListener('input', render);
    unitSel.addEventListener('change', render);
    render();
  });
})();
