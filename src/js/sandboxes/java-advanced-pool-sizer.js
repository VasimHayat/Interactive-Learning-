/* Java Advanced §5 — thread-pool sizing from the task profile.
   threads = cores × target utilisation × (1 + wait/compute), the formula from
   Java Concurrency in Practice §8.2; target utilisation is taken as 1. */
(function () {
  'use strict';
  var reg = globalThis.LMSandbox.reg, H = globalThis.LMHighlight;

  reg('pool-sizer', function (el) {
    var cores = el.querySelector('[data-sb-cores]');
    var coresOut = el.querySelector('[data-sb-coresout]');
    var kind = el.querySelector('[data-sb-kind]');
    var ratio = el.querySelector('[data-sb-ratio]');
    var ratioOut = el.querySelector('[data-sb-ratioout]');
    var out = el.querySelector('[data-sb-out]');

    function render() {
      var n = Number(cores.value), r = Number(ratio.value), io = kind.value === 'io';
      coresOut.textContent = n;
      ratioOut.textContent = r;
      ratio.disabled = !io;
      ratio.closest('.lm-field').style.opacity = io ? '1' : '0.45';

      var threads = io ? n * (1 + r) : n;
      var formula = io ? 'threads = cores × (1 + wait ÷ compute) = ' + n + ' × (1 + ' + r + ') = ' + threads
                       : 'threads = cores = ' + n + '   (some add +1 to cover a worker stalled on a page fault)';
      var expr = io
        ? 'new ThreadPoolExecutor(' + threads + ', ' + threads + ', 60, TimeUnit.SECONDS,\n        new ArrayBlockingQueue<>(' + (threads * 4) + '),           // bounded: ~4 tasks per thread\n        named("fetcher"), new ThreadPoolExecutor.CallerRunsPolicy());'
        : 'new ThreadPoolExecutor(' + n + ', ' + n + ', 0, TimeUnit.SECONDS,\n        new ArrayBlockingQueue<>(' + (n * 4) + '),\n        named("pricer"), new ThreadPoolExecutor.AbortPolicy());';

      var notes = [];
      if (io) {
        notes.push('Each thread spends <strong>' + r + '×</strong> as long waiting on a socket or disk as it does computing, so one core can keep <strong>' + (1 + r) + '</strong> such threads busy. Below this number cores sit idle while every thread waits; above it threads queue for the CPU.');
        if (threads > 200) notes.push('<strong>' + threads + ' platform threads</strong> is past the point where stacks, context switches and scheduler overhead dominate. This is the profile virtual threads were built for (§7): one per task, no pool, no sizing arithmetic.');
        else if (threads > 64) notes.push('Dozens of blocked threads are fine; hundreds start to cost real memory (≈1 MB reserved stack each) and scheduler time. Measure before going further, or look at §7.');
      } else {
        notes.push('A CPU-bound task never waits, so more threads than cores only adds context switches and cache misses without adding throughput. Size the <strong>queue</strong>, not the pool, to absorb bursts — and keep it bounded so a burst becomes back-pressure rather than an <code>OutOfMemoryError</code>.');
      }
      notes.push('The ratio is a measurement, not a guess: profile a task (§13) or time its blocking calls. A pool sized for the wrong ratio is the usual reason a service with idle CPUs is still slow.');

      out.innerHTML =
        '<dl class="lm-kv"><dt>Recommended</dt><dd><strong>' + threads + ' thread' + (threads === 1 ? '' : 's') + '</strong></dd>' +
        '<dt>Formula</dt><dd><code>' + H.esc(formula) + '</code></dd></dl>' +
        '<div class="lm-stepper-label mt-3">Constructed on purpose, not from a factory</div>' +
        H.renderCode(expr, { lang: 'java', noBar: true }) +
        '<div class="lm-callout ' + (threads > 200 ? 'lm-callout--gotcha' : 'lm-callout--note') + '" style="margin-top:.9rem">' +
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 16v-5m0-3h.01"/></svg><div>' +
        notes.map(function (s, i) { return '<p style="margin:0' + (i < notes.length - 1 ? ' 0 .4rem' : '') + '">' + s + '</p>'; }).join('') +
        '</div></div>';
    }

    cores.addEventListener('input', render);
    kind.addEventListener('change', render);
    ratio.addEventListener('input', render);
    render();
  });
})();
