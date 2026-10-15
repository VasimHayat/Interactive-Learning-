/* Java Intermediate §1 — choose a wildcard from what the method does with the collection. */
(function () {
  'use strict';
  var reg = globalThis.LMSandbox.reg, H = globalThis.LMHighlight;

  reg('wildcard-picker', function (el) {
    var reads = el.querySelector('[data-sb-reads]');
    var writes = el.querySelector('[data-sb-writes]');
    var out = el.querySelector('[data-sb-out]');

    var CASES = {
      'yes/no': {
        sig: 'static int totalInCents(List<? extends Product> src)',
        rule: 'Producer → <code>extends</code>',
        why: 'The method only takes elements <em>out</em>. Every element of a <code>List&lt;Perishable&gt;</code> is a <code>Product</code>, so reading through <code>? extends Product</code> is always safe, and the caller may pass a list of any subtype.',
        wrong: 'src.add(new Product("BOLT-7", 80));',
        err: 'error: incompatible types: Product cannot be converted to CAP#1\n  where CAP#1 is a fresh type-variable: CAP#1 extends Product from capture of ? extends Product',
        errWhy: 'The compiler does not know <em>which</em> subtype the list holds — it could be a <code>List&lt;Perishable&gt;</code> — so it refuses to let you add a plain <code>Product</code>. The only value you can add is <code>null</code>.',
        accepts: ['List&lt;Product&gt;', 'List&lt;Perishable&gt;', 'ArrayList&lt;Perishable&gt;'],
        rejects: ['List&lt;Object&gt;']
      },
      'no/yes': {
        sig: 'static void restock(List<? super Perishable> dst)',
        rule: 'Consumer → <code>super</code>',
        why: 'The method only puts elements <em>in</em>. A <code>Perishable</code> can be stored in a list of <code>Perishable</code>, of <code>Product</code>, or of <code>Object</code> — any supertype — so <code>? super Perishable</code> lets the caller pass all three.',
        wrong: 'Perishable p = dst.get(0);',
        err: 'error: incompatible types: CAP#1 cannot be converted to Perishable\n  where CAP#1 is a fresh type-variable: CAP#1 extends Object super: Perishable from capture of ? super Perishable',
        errWhy: 'Reading gives you only what every possible list has in common, which is <code>Object</code>. The list might be a <code>List&lt;Object&gt;</code> holding a <code>String</code>.',
        accepts: ['List&lt;Perishable&gt;', 'List&lt;Product&gt;', 'List&lt;Object&gt;'],
        rejects: ['List&lt;String&gt;']
      },
      'yes/yes': {
        sig: 'static void swapFirstTwo(List<Product> list)',
        rule: 'Both → no wildcard',
        why: 'The method reads a <code>Product</code> out and writes one back in. Neither wildcard permits both, so the type argument must be exact. The cost is invariance: callers must pass precisely a <code>List&lt;Product&gt;</code>. If that is too restrictive, make the method generic — <code>static &lt;T&gt; void swapFirstTwo(List&lt;T&gt; list)</code> — which reads and writes the <em>same</em> unknown <code>T</code> and accepts any list.',
        wrong: 'List<Perishable> dairy = …;\nswapFirstTwo(dairy);',
        err: 'error: incompatible types: List<Perishable> cannot be converted to List<Product>',
        errWhy: 'Invariance. <code>List&lt;Perishable&gt;</code> is not a <code>List&lt;Product&gt;</code>, because if it were, the method could insert a non-perishable <code>Product</code> into a list the caller believes holds only perishables.',
        accepts: ['List&lt;Product&gt;', 'ArrayList&lt;Product&gt;'],
        rejects: ['List&lt;Perishable&gt;', 'List&lt;Object&gt;']
      },
      'no/no': {
        sig: 'static boolean isEmptyOrNullFree(List<?> any)',
        rule: 'Neither → unbounded <code>?</code>',
        why: 'The method only asks about the <em>shape</em> of the list — <code>size()</code>, <code>isEmpty()</code>, <code>contains(null)</code>, <code>clear()</code> — and never depends on the element type. <code>List&lt;?&gt;</code> accepts every list there is, unlike the raw type <code>List</code>, which does the same job while switching off type checking.',
        wrong: 'any.add("x");',
        err: 'error: incompatible types: String cannot be converted to CAP#1\n  where CAP#1 is a fresh type-variable: CAP#1 extends Object from capture of ?',
        errWhy: 'With nothing known about the element type, nothing but <code>null</code> can be added. Reads come back as <code>Object</code>.',
        accepts: ['List&lt;Product&gt;', 'List&lt;String&gt;', 'List&lt;List&lt;Integer&gt;&gt;'],
        rejects: []
      }
    };

    function render() {
      var c = CASES[reads.value + '/' + writes.value];
      out.innerHTML =
        '<div class="lm-callout lm-callout--good">' +
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><path d="M20 6 9 17l-5-5"/></svg>' +
        '<div><div class="lm-callout-title">' + c.rule + '</div>' +
        '<code style="display:block;margin:.3rem 0 .6rem;white-space:pre-wrap">' + H.esc(c.sig) + '</code>' +
        '<p class="mb-2">' + c.why + '</p>' +
        '<p class="mb-0 text-sm"><strong>Accepts:</strong> <code>' + c.accepts.join('</code>, <code>') + '</code>' +
        (c.rejects.length ? ' &nbsp;·&nbsp; <strong>Rejects:</strong> <code>' + c.rejects.join('</code>, <code>') + '</code>' : '') +
        '</p></div></div>' +
        '<div class="lm-callout lm-callout--danger" style="margin-top:.75rem">' +
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="m15 9-6 6m0-6 6 6"/></svg>' +
        '<div><div class="lm-callout-title">What the compiler refuses inside this method</div>' +
        '<code style="display:block;margin:.3rem 0;white-space:pre-wrap">' + H.esc(c.wrong) + '</code>' +
        '<pre style="margin:.3rem 0 .6rem;font-size:.78rem;white-space:pre-wrap;color:var(--lm-bad)">' + H.esc(c.err) + '</pre>' +
        '<p class="mb-0 text-sm">' + c.errWhy + '</p></div></div>';
    }
    reads.addEventListener('change', render);
    writes.addEventListener('change', render);
    render();
  });
})();
