/* Java Advanced §7 — virtual threads mounting on carriers.
   A discrete simulation: each tick, every mounted virtual thread advances one
   phase of its task. Blocking I/O unmounts (the carrier is reused); CPU work
   never unmounts; blocking inside synchronized pins the carrier (JDK 21). */
(function () {
  'use strict';
  var reg = globalThis.LMSandbox.reg, H = globalThis.LMHighlight;

  reg('carrier-mount', function (el) {
    var carriersIn = el.querySelector('[data-sb-carriers]');
    var carriersOut = el.querySelector('[data-sb-carriersout]');
    var vtsIn = el.querySelector('[data-sb-vts]');
    var vtsOut = el.querySelector('[data-sb-vtsout]');
    var taskSel = el.querySelector('[data-sb-task]');
    var stepBtn = el.querySelector('[data-sb-step]');
    var runBtn = el.querySelector('[data-sb-run]');
    var resetBtn = el.querySelector('[data-sb-reset]');
    var out = el.querySelector('[data-sb-out]');

    // phases per task type: cpu = needs a carrier; block = waits (unmounted or pinned)
    var TASKS = {
      io:   [{ kind: 'cpu', n: 1 }, { kind: 'block', n: 3 }, { kind: 'cpu', n: 1 }],
      cpu:  [{ kind: 'cpu', n: 5 }],
      sync: [{ kind: 'cpu', n: 1 }, { kind: 'block', n: 3, pinned: true }, { kind: 'cpu', n: 1 }]
    };

    var state;

    function reset() {
      var nCarriers = Number(carriersIn.value), nVts = Number(vtsIn.value);
      carriersOut.textContent = nCarriers;
      vtsOut.textContent = nVts;
      var phases = TASKS[taskSel.value];
      state = {
        tick: 0,
        carriers: new Array(nCarriers).fill(null),      // index of the mounted vt, or null
        vts: [],
        finishedAt: null
      };
      for (var i = 0; i < nVts; i++) {
        state.vts.push({ id: i, phase: 0, left: phases[0].n, status: 'ready', pinned: false });
      }
      render();
    }

    function advance(vt, phases) {
      vt.left--;
      if (vt.left > 0) return;
      vt.phase++;
      if (vt.phase >= phases.length) { vt.status = 'done'; return; }
      var ph = phases[vt.phase];
      vt.left = ph.n;
      if (ph.kind === 'block') { vt.status = ph.pinned ? 'pinned' : 'parked'; vt.pinned = !!ph.pinned; }
      else vt.status = 'runnable';
    }

    function step() {
      if (state.finishedAt !== null) return;
      var phases = TASKS[taskSel.value];
      state.tick++;

      // 1. mounted threads use their carrier for one tick
      state.carriers.forEach(function (idx, c) {
        if (idx === null) return;
        var vt = state.vts[idx];
        if (vt.status === 'running') {
          advance(vt, phases);
          if (vt.status === 'parked') vt.justParked = true;                                // its wait starts next tick
          if (vt.status === 'done' || vt.status === 'parked') state.carriers[c] = null;   // unmount
          else if (vt.status === 'runnable') vt.status = 'running';                       // next cpu phase, same carrier
          // 'pinned': stays mounted, carrier wasted
        } else if (vt.status === 'pinned') {
          vt.left--;
          if (vt.left <= 0) { vt.phase++; vt.left = phases[vt.phase].n; vt.status = 'running'; vt.pinned = false; }
        }
      });

      // 2. parked threads wait for their I/O off-carrier
      state.vts.forEach(function (vt) {
        if (vt.status !== 'parked') return;
        if (vt.justParked) { vt.justParked = false; return; }
        vt.left--;
        if (vt.left <= 0) { vt.phase++; vt.left = phases[vt.phase].n; vt.status = 'ready'; }
      });

      // 3. free carriers pick up ready threads, FIFO
      state.carriers.forEach(function (idx, c) {
        if (idx !== null) return;
        for (var i = 0; i < state.vts.length; i++) {
          if (state.vts[i].status === 'ready') { state.vts[i].status = 'running'; state.carriers[c] = i; break; }
        }
      });

      if (state.vts.every(function (v) { return v.status === 'done'; })) state.finishedAt = state.tick;
      render();
    }

    function count(status) { return state.vts.filter(function (v) { return v.status === status; }).length; }

    function render() {
      var task = taskSel.value;
      var nC = state.carriers.length, nV = state.vts.length;
      var mounted = state.carriers.filter(function (x) { return x !== null; }).length;
      var pinned = state.vts.filter(function (v) { return v.status === 'pinned'; }).length;

      var carrierCells = state.carriers.map(function (idx, c) {
        var vt = idx === null ? null : state.vts[idx];
        var cls = 'lm-cell' + (vt ? (vt.status === 'pinned' ? ' is-bad' : ' is-hi') : ' is-empty');
        return '<div class="' + cls + '"><span class="lm-cell-idx">carrier ' + c + '</span>' +
               (vt ? 'vt-' + vt.id + (vt.status === 'pinned' ? '<br>PINNED' : '') : 'idle') + '</div>';
      }).join('');

      var vtCells = state.vts.map(function (v) {
        var cls = 'lm-cell';
        if (v.status === 'running') cls += ' is-hi';
        else if (v.status === 'pinned') cls += ' is-bad';
        else if (v.status === 'done') cls += ' is-empty';
        var label = v.status === 'parked' ? 'parked' : v.status === 'ready' ? 'ready' : v.status === 'running' ? 'on cpu' : v.status === 'pinned' ? 'pinned' : 'done';
        return '<div class="' + cls + '" style="min-width:4.2rem"><span class="lm-cell-idx">vt-' + v.id + '</span>' + label + '</div>';
      }).join('');

      var ideal = { io: 5, cpu: 5, sync: 5 }[task];                      // ticks one task takes alone
      var platformPool = Math.ceil(nV / nC) * ideal;                     // what nC platform threads would need
      var verdict;
      if (task === 'io') {
        verdict = 'Each thread needs a carrier for only 2 of its 5 ticks. While it is parked, the carrier serves another thread, so ' + nC + ' carrier' + (nC === 1 ? '' : 's') + ' can keep roughly ' + (nC * 2) + '–' + (nC * 3) + ' threads in flight. A pool of ' + nC + ' platform threads would need about ' + platformPool + ' ticks for the same work.';
      } else if (task === 'cpu') {
        verdict = 'Nothing ever blocks, so nothing ever unmounts: at most ' + nC + ' thread' + (nC === 1 ? '' : 's') + ' make progress per tick, exactly like a fixed pool of ' + nC + '. Expect ' + platformPool + ' ticks — virtual threads add bookkeeping and no parallelism here.';
      } else {
        verdict = 'The block happens inside synchronized, so the carrier cannot be released: it sits idle for 3 ticks per thread. Throughput collapses to that of ' + nC + ' platform thread' + (nC === 1 ? '' : 's') + ', about ' + platformPool + ' ticks. Replace the monitor with a ReentrantLock, or move to Java 24 (JEP 491).';
      }

      out.innerHTML =
        '<dl class="lm-kv">' +
        '<dt>Tick</dt><dd>' + state.tick + (state.finishedAt !== null ? ' — all ' + nV + ' threads finished at tick ' + state.finishedAt : '') + '</dd>' +
        '<dt>Carriers busy</dt><dd>' + mounted + ' of ' + nC + (pinned ? ' (' + pinned + ' pinned, doing nothing)' : '') + '</dd>' +
        '<dt>Virtual threads</dt><dd>' + count('running') + ' on a carrier · ' + count('parked') + ' parked off-carrier · ' + count('pinned') + ' pinned · ' + count('ready') + ' waiting for a carrier · ' + count('done') + ' done</dd>' +
        '</dl>' +
        '<div class="lm-stepper-label mt-3">Carriers (platform threads in the scheduler)</div>' +
        '<div class="lm-mem" style="margin:.4rem 0 .9rem">' + carrierCells + '</div>' +
        '<div class="lm-stepper-label">Virtual threads</div>' +
        '<div class="lm-mem" style="margin:.4rem 0 .9rem">' + vtCells + '</div>' +
        '<div class="lm-callout ' + (task === 'sync' ? 'lm-callout--gotcha' : 'lm-callout--note') + '">' +
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 16v-5m0-3h.01"/></svg>' +
        '<div><div class="lm-callout-title">' + H.esc(task === 'io' ? 'Blocking I/O: the design target' : task === 'cpu' ? 'CPU-bound: no gain' : 'Pinned: the Java 21 trap') + '</div>' + verdict + '</div></div>';
    }

    stepBtn.addEventListener('click', step);
    runBtn.addEventListener('click', function () {
      for (var i = 0; i < 2000 && state.finishedAt === null; i++) step();
    });
    resetBtn.addEventListener('click', reset);
    carriersIn.addEventListener('input', reset);
    vtsIn.addEventListener('input', reset);
    taskSel.addEventListener('change', reset);
    reset();
  });
})();
