/* Java Advanced §9 — heap sizing inside a memory-limited container.
   Pure arithmetic: the JVM's own rule (MaxRAMPercentage of the cgroup limit)
   plus the non-heap costs people forget. Nothing here measures a real JVM. */
(function () {
  'use strict';
  var reg = globalThis.LMSandbox.reg, H = globalThis.LMHighlight;

  reg('heap-sizer', function (el) {
    var limit = el.querySelector('[data-sb-limit]');
    var pct = el.querySelector('[data-sb-pct]');
    var pctOut = el.querySelector('[data-sb-pctout]');
    var nonHeap = el.querySelector('[data-sb-nonheap]');
    var nonHeapOut = el.querySelector('[data-sb-nonheapout]');
    var threads = el.querySelector('[data-sb-threads]');
    var threadsOut = el.querySelector('[data-sb-threadsout]');
    var stack = el.querySelector('[data-sb-stack]');
    var out = el.querySelector('[data-sb-out]');

    function mb(n) { return n >= 1024 && n % 256 === 0 ? (n / 1024) + ' GB' : Math.round(n) + ' MB'; }

    function render() {
      var lim = Number(limit.value);
      var p = Number(pct.value);
      var nh = Number(nonHeap.value);
      var t = Number(threads.value);
      var st = Number(stack.value);               // KB
      pctOut.textContent = p + '%';
      nonHeapOut.textContent = nh + ' MB';
      threadsOut.textContent = t;

      var heap = Math.floor(lim * p / 100);          // what -XX:MaxRAMPercentage yields
      var threadMb = t * st / 1024;                  // reserved stack, committed as used
      var total = heap + nh + threadMb;
      var headroom = lim - total;
      var safeHeap = Math.max(0, Math.floor((lim - nh - threadMb) * 0.9));

      var kind, title, body;
      if (headroom < 0) {
        kind = 'danger';
        title = 'Over the limit by ' + mb(-headroom) + ' — the kernel will kill this container';
        body = 'When the heap fills to its maximum the process exceeds the cgroup limit. That is not an <code>OutOfMemoryError</code>; ' +
               'it is <strong>SIGKILL, exit code 137</strong>, with no heap dump and no stack trace, usually after the service has been up for hours.';
      } else if (headroom < lim * 0.1) {
        kind = 'gotcha';
        title = 'Fits, with only ' + mb(headroom) + ' to spare';
        body = 'Anything you did not count — direct byte buffers, JIT code cache growth, a native library, glibc arenas — comes out of that margin. Leave at least 10% of the limit unassigned.';
      } else {
        kind = 'good';
        title = 'Fits, ' + mb(headroom) + ' headroom';
        body = 'Heap, metaspace and thread stacks all sit inside the limit with room for the native allocations nobody budgets for.';
      }
      var icon = kind === 'good' ? '<path d="M20 6 9 17l-5-5"/>'
               : kind === 'danger' ? '<circle cx="12" cy="12" r="9"/><path d="m15 9-6 6m0-6 6 6"/>'
               : '<path d="M12 9v4m0 4h.01M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/>';

      var cmd = 'java -XX:MaxRAMPercentage=' + p + ' ...   # heap ceiling = ' + mb(heap) + '\n' +
                'java -Xmx' + heap + 'm ...                    # the same ceiling, stated explicitly\n' +
                'java -Xmx' + safeHeap + 'm ...                    # what fits once non-heap is paid for';

      out.innerHTML =
        '<dl class="lm-kv">' +
        '<dt>Container limit</dt><dd>' + mb(lim) + '</dd>' +
        '<dt>Heap ceiling</dt><dd>' + mb(heap) + ' = ' + p + '% of the limit (the JVM reads the cgroup limit, not the host RAM)</dd>' +
        '<dt>Non-heap estimate</dt><dd>' + mb(nh) + ' — metaspace, code cache, GC data structures, direct buffers, native</dd>' +
        '<dt>Thread stacks</dt><dd>' + t + ' × ' + st + ' KB = up to ' + mb(threadMb) + ' (committed only as each stack is touched)</dd>' +
        '<dt>Worst-case total</dt><dd><strong>' + mb(total) + '</strong> against a limit of ' + mb(lim) + '</dd>' +
        '</dl>' +
        H.renderCode(cmd, { lang: 'bash', noBar: true }) +
        '<div class="lm-callout lm-callout--' + kind + '" style="margin-top:.9rem">' +
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">' + icon + '</svg>' +
        '<div><div class="lm-callout-title">' + H.esc(title) + '</div>' + body + '</div></div>';
    }

    [limit, pct, nonHeap, threads, stack].forEach(function (c) {
      c.addEventListener(c.type === 'range' ? 'input' : 'change', render);
    });
    render();
  });
})();
