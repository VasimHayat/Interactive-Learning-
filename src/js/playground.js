/* Step-through executors and parameter sandboxes.
   Executor states are pre-authored data, never live evaluation — every stepper
   carries a "Simulated" badge in the markup to say so. */
(function () {
  'use strict';

  var H = globalThis.LMHighlight;

  // ── step-through executor ────────────────────────────────────────────────
  document.querySelectorAll('[data-stepper]').forEach(function (el) {
    var script = el.querySelector('[data-stepper-data]');
    if (!script) return;
    var data;
    try { data = JSON.parse(script.textContent); } catch (e) { return; }
    var steps = data.steps || [];
    if (!steps.length) return;

    var codeWrap = el.querySelector('[data-stepper-code]');
    var varsBody = el.querySelector('[data-stepper-vars]');
    var outEl = el.querySelector('[data-stepper-out]');
    var stackEl = el.querySelector('[data-stepper-stack]');
    var noteEl = el.querySelector('[data-step-note]');
    var countEl = el.querySelector('[data-step-count]');
    var prev = el.querySelector('[data-step-prev]');
    var next = el.querySelector('[data-step-next]');
    var reset = el.querySelector('[data-step-reset]');
    var i = 0, lastVars = {};

    // build.mjs pre-renders the code so it is readable without JS and in print
    if (codeWrap && !codeWrap.children.length && H) {
      codeWrap.innerHTML = H.renderCode(data.code, { lang: data.lang || 'java', noBar: true });
    }
    var lines = Array.prototype.slice.call(el.querySelectorAll('.cl'));

    function paint(animate) {
      var s = steps[i];

      lines.forEach(function (ln, idx) {
        var active = Array.isArray(s.line) ? s.line.indexOf(idx + 1) > -1 : s.line === idx + 1;
        ln.classList.toggle('act', active);
      });
      var first = el.querySelector('.cl.act');
      if (first && animate) {
        var body = el.querySelector('.lm-code-body');
        if (body && body.scrollHeight > body.clientHeight) {
          first.scrollIntoView({ block: 'nearest' });
        }
      }

      if (varsBody) {
        var vars = s.vars || {};
        varsBody.innerHTML = Object.keys(vars).length
          ? Object.keys(vars).map(function (k) {
              var changed = lastVars[k] !== vars[k];
              return '<tr class="' + (changed ? 'is-changed' : '') + '"><td>' + H.esc(k) +
                     '</td><td>' + H.esc(String(vars[k])) + '</td></tr>';
            }).join('')
          : '<tr><td colspan="2" style="color:var(--lm-text-subtle)">no locals in scope yet</td></tr>';
        lastVars = Object.assign({}, vars);
      }

      if (stackEl) {
        var stack = s.stack || [];
        stackEl.innerHTML = stack.length
          ? stack.slice().reverse().map(function (f, d) {
              return '<div class="lm-cell' + (d === 0 ? ' is-hi' : '') + '" style="width:100%">' + H.esc(f) + '</div>';
            }).join('')
          : '<div class="lm-cell is-empty" style="width:100%">(empty)</div>';
      }

      if (outEl) outEl.textContent = s.out || '';
      if (noteEl) noteEl.textContent = s.note || '';
      if (countEl) countEl.textContent = 'Step ' + (i + 1) + ' of ' + steps.length;
      if (prev) prev.disabled = i === 0;
      if (next) next.disabled = i === steps.length - 1;
    }

    function go(n, animate) {
      i = Math.max(0, Math.min(steps.length - 1, n));
      if (i === 0) lastVars = {};
      paint(animate);
    }

    if (prev) prev.addEventListener('click', function () { go(i - 1, true); });
    if (next) next.addEventListener('click', function () { go(i + 1, true); });
    if (reset) reset.addEventListener('click', function () { lastVars = {}; go(0, true); });

    el.addEventListener('keydown', function (e) {
      if (e.target.closest('input, textarea, select')) return;
      if (e.key === 'ArrowRight') { e.preventDefault(); go(i + 1, true); }
      if (e.key === 'ArrowLeft') { e.preventDefault(); go(i - 1, true); }
    });

    go(0, false);
  });

  // ── sandbox registry ──────────────────────────────────────────────────────
  var SANDBOX = {};
  function reg(name, fn) { SANDBOX[name] = fn; }
  // per-topic handler files (src/js/sandboxes/*.js) are bundled after this
  // file and register through this object; build.mjs appends the boot call
  globalThis.LMSandbox = { reg: reg, boot: boot };

  function fmt(n) { return n.toLocaleString('en-US'); }

  /* 1 — primitive widths, ranges, and two's-complement overflow */
  reg('primitives', function (el) {
    var TYPES = {
      byte:   { bits: 8,  min: -128n, max: 127n },
      short:  { bits: 16, min: -32768n, max: 32767n },
      int:    { bits: 32, min: -2147483648n, max: 2147483647n },
      long:   { bits: 64, min: -9223372036854775808n, max: 9223372036854775807n },
      char:   { bits: 16, min: 0n, max: 65535n }
    };
    var sel = el.querySelector('[data-sb-type]');
    var add = el.querySelector('[data-sb-add]');
    var addOut = el.querySelector('[data-sb-addout]');
    var out = el.querySelector('[data-sb-out]');

    function render() {
      var t = TYPES[sel.value];
      var bits = BigInt(t.bits);
      var span = t.max - t.min + 1n;
      var start = t.max;                      // begin at MAX_VALUE
      var delta = BigInt(add.value);
      var raw = start + delta;
      var wrapped = ((raw - t.min) % span + span) % span + t.min;

      addOut.textContent = '+' + add.value;
      out.innerHTML =
        '<dl class="lm-kv">' +
        '<dt>Width</dt><dd>' + t.bits + ' bits (' + (t.bits / 8) + ' byte' + (t.bits > 8 ? 's' : '') + ')</dd>' +
        '<dt>Range</dt><dd><code>' + fmt(Number(t.min)) + '</code> … <code>' + fmt(Number(t.max)) + '</code></dd>' +
        '<dt>Distinct values</dt><dd>2<sup>' + t.bits + '</sup> = ' + span.toString() + '</dd>' +
        '</dl>' +
        '<div class="lm-callout ' + (raw === wrapped ? 'lm-callout--good' : 'lm-callout--gotcha') + '" style="margin:.9rem 0 0">' +
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">' +
        (raw === wrapped ? '<path d="M20 6 9 17l-5-5"/>' : '<path d="M12 9v4m0 4h.01M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/>') +
        '</svg><div><div class="lm-callout-title">' +
        (raw === wrapped ? 'Fits' : 'Silent overflow') + '</div>' +
        '<code>(' + sel.value + ') (' + t.max.toString() + ' + ' + delta.toString() + ')</code> → <code>' + wrapped.toString() + '</code>' +
        (raw === wrapped ? '' : '<br>Java wraps around instead of throwing. The arithmetic result <code>' +
          raw.toString() + '</code> does not fit in ' + t.bits + ' bits, so the high bits are discarded.') +
        '</div></div>';
    }
    sel.addEventListener('change', render);
    add.addEventListener('input', render);
    render();
  });

  /* 2 — array indexing and bounds */
  reg('array-bounds', function (el) {
    var len = el.querySelector('[data-sb-len]');
    var idx = el.querySelector('[data-sb-idx]');
    var lenOut = el.querySelector('[data-sb-lenout]');
    var idxOut = el.querySelector('[data-sb-idxout]');
    var cells = el.querySelector('[data-sb-cells]');
    var verdict = el.querySelector('[data-sb-verdict]');
    var NAMES = ['WIDGET-1', 'WIDGET-2', 'BOLT-7', 'NUT-3', 'CAM-9', 'ROD-4', 'PIN-2', 'HUB-8'];

    function render() {
      var n = Number(len.value), i = Number(idx.value);
      idx.max = String(Math.max(0, n + 2));
      lenOut.textContent = n;
      idxOut.textContent = i;
      var html = '';
      for (var k = 0; k < n; k++) {
        html += '<div class="lm-cell' + (k === i ? ' is-hi' : '') + '"><span class="lm-cell-idx">[' + k + ']</span>' +
                NAMES[k % NAMES.length] + '</div>';
      }
      for (var j = n; j <= Math.max(n, i); j++) {
        html += '<div class="lm-cell is-empty' + (j === i ? ' is-bad' : '') + '"><span class="lm-cell-idx">[' + j + ']</span>—</div>';
      }
      cells.innerHTML = html;
      var ok = i >= 0 && i < n;
      verdict.className = 'lm-callout ' + (ok ? 'lm-callout--good' : 'lm-callout--danger');
      verdict.innerHTML =
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">' +
        (ok ? '<path d="M20 6 9 17l-5-5"/>' : '<circle cx="12" cy="12" r="9"/><path d="m15 9-6 6m0-6 6 6"/>') +
        '</svg><div><code>parts[' + i + ']</code> — ' +
        (ok ? '<strong>valid.</strong> Returns <code>"' + NAMES[i % NAMES.length] + '"</code>.'
            : '<strong>throws.</strong> <code>ArrayIndexOutOfBoundsException: Index ' + i +
              ' out of bounds for length ' + n + '</code>') +
        '<br><span style="color:var(--lm-text-muted)">Valid indices for length ' + n + ': <code>0</code> … <code>' +
        (n - 1) + '</code>. The last index is always <code>length - 1</code>, never <code>length</code>.</span></div>';
    }
    len.addEventListener('input', render);
    idx.addEventListener('input', render);
    render();
  });

  /* 3 — collection chooser */
  reg('collection-picker', function (el) {
    var q = {
      dupes: el.querySelector('[data-sb-dupes]'),
      order: el.querySelector('[data-sb-order]'),
      lookup: el.querySelector('[data-sb-lookup]'),
      insert: el.querySelector('[data-sb-insert]')
    };
    var out = el.querySelector('[data-sb-out]');

    function render() {
      var dupes = q.dupes.value, order = q.order.value, lookup = q.lookup.value, insert = q.insert.value;
      var pick, why, alt;

      if (lookup === 'key') {
        if (order === 'sorted') { pick = 'TreeMap<K,V>'; why = 'Keyed lookup with keys kept in sorted order. O(log n) get/put, and you get navigation methods like <code>firstKey()</code> and <code>headMap()</code>.'; alt = 'LinkedHashMap if you want insertion order instead of sorted order.'; }
        else if (order === 'insertion') { pick = 'LinkedHashMap<K,V>'; why = 'Hash lookup in O(1) while preserving the order entries were added — the usual choice for caches and ordered config.'; alt = 'HashMap if the order genuinely does not matter; it has slightly less overhead.'; }
        else { pick = 'HashMap<K,V>'; why = 'The default map. O(1) average get/put and the lowest overhead of the three.'; alt = 'LinkedHashMap the moment iteration order starts mattering.'; }
      } else if (dupes === 'no') {
        if (order === 'sorted') { pick = 'TreeSet<E>'; why = 'Uniqueness plus sorted iteration, O(log n) per operation. Relies on <code>compareTo</code> or a <code>Comparator</code>, not <code>equals</code>.'; alt = 'LinkedHashSet if you only need predictable order, not sorted order — it is faster.'; }
        else if (order === 'insertion') { pick = 'LinkedHashSet<E>'; why = 'Uniqueness with insertion-order iteration. O(1) contains, and iteration order is reproducible.'; alt = 'HashSet if you never iterate, or do not care about the order when you do.'; }
        else { pick = 'HashSet<E>'; why = 'Uniqueness with O(1) <code>contains</code>. Iteration order is unspecified and may change between runs.'; alt = 'LinkedHashSet if unstable iteration order would make tests flaky.'; }
      } else {
        if (insert === 'ends') { pick = 'ArrayDeque<E>'; why = 'Add and remove at both ends in amortised O(1), with none of the per-node overhead of a linked list.'; alt = 'ArrayList if you only ever append; ArrayDeque wins when you also remove from the front.'; }
        else if (insert === 'middle') { pick = 'ArrayList<E>'; why = 'Even for middle inserts, ArrayList usually beats LinkedList: <code>System.arraycopy</code> is fast and cache-friendly, and LinkedList still pays O(n) to <em>walk</em> to the index first.'; alt = 'LinkedList only when you are inserting via an existing ListIterator, which skips the walk.'; }
        else { pick = 'ArrayList<E>'; why = 'The default list. O(1) random access, amortised O(1) append, contiguous memory the CPU cache likes.'; alt = 'Nothing — reach for something else only when you have measured a problem.'; }
      }

      out.innerHTML =
        '<div class="lm-callout lm-callout--good"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><path d="M20 6 9 17l-5-5"/></svg>' +
        '<div><div class="lm-callout-title">Use <code>' + pick + '</code></div><p>' + why + '</p>' +
        '<p style="margin:.4rem 0 0;color:var(--lm-text-muted)"><strong>Consider instead:</strong> ' + alt + '</p></div></div>';
    }
    Object.keys(q).forEach(function (k) { q[k].addEventListener('change', render); });
    render();
  });

  /* 4 — access modifier visibility matrix */
  reg('access-matrix', function (el) {
    var RULES = {
      'private':   { same: true, pkg: false, sub: false, world: false },
      'no modifier (package-private)': { same: true, pkg: true, sub: false, world: false },
      'protected': { same: true, pkg: true, sub: true, world: false },
      'public':    { same: true, pkg: true, sub: true, world: true }
    };
    var LABEL = {
      same: 'the same class',
      pkg: 'a different class in the same package',
      sub: 'a subclass in a different package',
      world: 'an unrelated class in a different package'
    };
    var mod = el.querySelector('[data-sb-mod]');
    var loc = el.querySelector('[data-sb-loc]');
    var out = el.querySelector('[data-sb-out]');
    var table = el.querySelector('[data-sb-table]');

    function render() {
      var allowed = RULES[mod.value][loc.value];
      out.className = 'lm-callout ' + (allowed ? 'lm-callout--good' : 'lm-callout--danger');
      out.innerHTML =
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">' +
        (allowed ? '<path d="M20 6 9 17l-5-5"/>' : '<circle cx="12" cy="12" r="9"/><path d="m15 9-6 6m0-6 6 6"/>') +
        '</svg><div>A <code>' + mod.value.replace(' (package-private)', '') + '</code> member, referenced from ' +
        LABEL[loc.value] + ': <strong>' + (allowed ? 'allowed' : 'compile error') + '</strong>.' +
        (allowed ? '' : '<br><code>error: ' + (mod.value === 'private' ? 'x has private access' : 'x is not public in Order; cannot be accessed from outside package') + '</code>') +
        (loc.value === 'sub' && mod.value === 'protected'
          ? '<br><span style="color:var(--lm-text-muted)">Note: a subclass may only touch <code>protected</code> members <em>through a reference of its own type</em>, not through a bare superclass reference.</span>' : '') +
        '</div>';

      table.querySelectorAll('tr[data-mod]').forEach(function (tr) {
        tr.classList.toggle('is-hi', tr.dataset.mod === mod.value);
        tr.querySelectorAll('td[data-col]').forEach(function (td) {
          td.classList.toggle('is-hi', tr.dataset.mod === mod.value && td.dataset.col === loc.value);
        });
      });
    }
    mod.addEventListener('change', render);
    loc.addEventListener('change', render);
    render();
  });

  /* 5 — HashSet buckets with and without hashCode */
  reg('hash-buckets', function (el) {
    var toggle = el.querySelector('[data-sb-override]');
    var out = el.querySelector('[data-sb-out]');
    var BUCKETS = 16;
    var items = [
      { sku: 'BOLT-7', id: 0x1a2b3c4d },
      { sku: 'BOLT-7', id: 0x5e6f7a8b },   // equal by value, different identity
      { sku: 'NUT-3',  id: 0x99887766 }
    ];

    function render() {
      var overridden = toggle.checked;
      var buckets = [];
      for (var i = 0; i < BUCKETS; i++) buckets.push([]);
      var stored = [];

      items.forEach(function (it) {
        var h = overridden ? hashString(it.sku) : it.id;
        var b = (h ^ (h >>> 16)) & (BUCKETS - 1);
        var dup = overridden && stored.some(function (s) { return s.sku === it.sku && s.bucket === b; });
        if (!dup) { stored.push({ sku: it.sku, bucket: b }); buckets[b].push(it.sku); }
      });

      var grid = buckets.map(function (b, i) {
        return '<div class="lm-cell' + (b.length ? ' is-hi' : ' is-empty') + '">' +
               '<span class="lm-cell-idx">' + i + '</span>' + (b.length ? b.join('<br>') : '·') + '</div>';
      }).join('');

      out.innerHTML =
        '<div class="lm-mem" style="margin-bottom:.9rem">' + grid + '</div>' +
        '<div class="lm-callout ' + (overridden ? 'lm-callout--good' : 'lm-callout--danger') + '">' +
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">' +
        (overridden ? '<path d="M20 6 9 17l-5-5"/>' : '<path d="M12 9v4m0 4h.01M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/>') +
        '</svg><div><div class="lm-callout-title">set.size() == ' + stored.length + '</div>' +
        (overridden
          ? 'With <code>hashCode()</code> overridden, both <code>BOLT-7</code> instances hash to the same bucket, <code>equals</code> finds the collision, and the duplicate is rejected. Two distinct SKUs remain.'
          : 'Without <code>hashCode()</code>, the two equal <code>BOLT-7</code> instances get identity hashes and land in <em>different</em> buckets. <code>equals</code> is never consulted, so the duplicate is stored. <code>contains(new Part("BOLT-7"))</code> will also return <code>false</code>.') +
        '</div></div>';
    }
    function hashString(s) {
      var h = 0;
      for (var i = 0; i < s.length; i++) { h = (Math.imul(31, h) + s.charCodeAt(i)) | 0; }
      return h;
    }
    toggle.addEventListener('change', render);
    render();
  });

  /* 6 — operator precedence evaluator */
  reg('precedence', function (el) {
    var sel = el.querySelector('[data-sb-expr]');
    var out = el.querySelector('[data-sb-out]');
    var CASES = {
      'a + b * c': { steps: ['b * c  →  3 * 4  =  12', 'a + 12  →  2 + 12  =  14'], result: '14', note: '<code>*</code> binds tighter than <code>+</code>, so it runs first regardless of reading order.' },
      '(a + b) * c': { steps: ['(a + b)  →  (2 + 3)  =  5', '5 * c  →  5 * 4  =  20'], result: '20', note: 'Parentheses outrank every operator.' },
      'a + b + "!"': { steps: ['a + b  →  2 + 3  =  5   (both numeric)', '5 + "!"  →  "5!"   (String concatenation)'], result: '"5!"', note: '<code>+</code> is left-associative. Numeric addition happens first, then the String forces concatenation.' },
      '"!" + a + b': { steps: ['"!" + a  →  "!2"   (String concatenation)', '"!2" + b  →  "!23"'], result: '"!23"', note: 'Same operators, opposite result — once the left operand is a String, everything after it concatenates. This is the classic logging bug.' },
      'a++ + ++b': { steps: ['a++  →  yields 2, a becomes 3   (post-increment)', '++b  →  b becomes 4, yields 4   (pre-increment)', '2 + 4  =  6'], result: '6', note: 'Post-increment yields the <em>old</em> value; pre-increment yields the new one.' },
      '-7 / 2': { steps: ['integer division truncates toward zero', '-7 / 2  →  -3   (not -4)'], result: '-3', note: 'Java truncates toward zero, so it does not match mathematical floor division for negatives.' },
      '-7 % 2': { steps: ['the sign follows the <em>dividend</em>, not the divisor', '-7 % 2  →  -1'], result: '-1', note: 'Unlike Python, Java\'s <code>%</code> can return a negative result. Guard array indexing with <code>Math.floorMod</code>.' }
    };
    function render() {
      var c = CASES[sel.value];
      out.innerHTML =
        '<div class="lm-stepper-label">Given <code>int a = 2, b = 3, c = 4;</code></div>' +
        '<ol style="margin:.6rem 0 .9rem;padding-left:1.3rem">' +
        c.steps.map(function (s) { return '<li><code>' + s + '</code></li>'; }).join('') + '</ol>' +
        '<div class="lm-callout lm-callout--note"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 16v-5m0-3h.01"/></svg>' +
        '<div><div class="lm-callout-title">Result: <code>' + c.result + '</code></div>' + c.note + '</div></div>';
    }
    sel.addEventListener('change', render);
    render();
  });

  // ── boot ──────────────────────────────────────────────────────────────────
  function boot() {
    document.querySelectorAll('[data-sandbox]').forEach(function (el) {
      var fn = SANDBOX[el.dataset.sandbox];
      if (fn) { try { fn(el); } catch (e) { console.error('sandbox ' + el.dataset.sandbox, e); } }
    });
  }
})();
