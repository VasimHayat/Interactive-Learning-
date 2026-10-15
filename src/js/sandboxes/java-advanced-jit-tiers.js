/* Java Advanced §13 — which tier is this method in? A model of HotSpot's
   tiered-compilation policy with its DEFAULT thresholds. The real policy also
   weighs back-edge counts, queue length and the C1/C2 compiler load, so treat
   the numbers as illustrative, which is what the card says. */
(function () {
  'use strict';
  var reg = globalThis.LMSandbox.reg, H = globalThis.LMHighlight;

  reg('jit-tiers', function (el) {
    var inv = el.querySelector('[data-sb-inv]');
    var invOut = el.querySelector('[data-sb-invout]');
    var mega = el.querySelector('[data-sb-mega]');
    var big = el.querySelector('[data-sb-big]');
    var out = el.querySelector('[data-sb-out]');

    // slider 0..100 → 0..20000 on a curve so the interesting region is wide
    function invocations() { var x = Number(inv.value) / 100; return Math.round(20000 * x * x); }
    function fmt(n) { return n.toLocaleString('en-US'); }

    var T3 = 200, T4 = 5000, MAX_INLINE = 35, FREQ_INLINE = 325;

    function render() {
      var n = invocations();
      invOut.textContent = fmt(n);
      var megamorphic = mega.checked, large = big.checked;

      var tier, tierName, how, inlined, inlineWhy, code;
      if (n < T3) {
        tier = 0; tierName = 'Tier 0 — interpreter';
        how = 'Below <code>Tier3InvocationThreshold</code> (' + T3 + '). Every bytecode is dispatched by the template interpreter; invocation and back-edge counters are being incremented, nothing else has happened yet.';
      } else if (n < T4) {
        tier = 3; tierName = 'Tier 3 — C1 with full profiling';
        how = 'Past ' + T3 + ' invocations the method was queued for C1, which compiles fast and instruments the code: branch frequencies, call-site receiver types, and the loop and invocation counters C2 will need. Tier 3 code is roughly 30% slower than tier 2 because of that profiling, which is why it is meant to be temporary.';
      } else {
        tier = 4; tierName = 'Tier 4 — C2 optimised';
        how = 'Past <code>Tier4InvocationThreshold</code> (' + fmt(T4) + ') with a complete profile, C2 compiled the method with the profile baked in: the hot path straight-lined, cold branches replaced by uncommon traps that fall back to the interpreter, and callees inlined where the limits allow.';
      }

      if (tier < 4) {
        inlined = null;
        inlineWhy = 'Inlining decisions that matter are C2\'s. C1 inlines only tiny trivial methods (accessors), so for now each call to <code>product.price()</code> is a real call.';
      } else if (megamorphic) {
        inlined = false;
        inlineWhy = 'The profile saw <strong>three or more receiver types</strong> at this call site (megamorphic). C2 inlines monomorphic sites unconditionally and bimorphic sites with a type guard, but a megamorphic site becomes a real virtual call through the vtable or itable — typically 5–10× the cost of an inlined call, and it blocks every optimisation that needed to see through it.';
      } else if (large) {
        inlined = false;
        inlineWhy = 'The callee is bigger than <code>FreqInlineSize</code> (' + FREQ_INLINE + ' bytecodes), the limit for <em>hot</em> call sites. Even a hot method is not inlined past that size; the fix is usually to split the method so the hot part is small. (Cold call sites use the stricter <code>MaxInlineSize</code>, ' + MAX_INLINE + '.)';
      } else {
        inlined = true;
        inlineWhy = 'Monomorphic call site, small callee: C2 copied the body of <code>product.price()</code> into the caller. No call overhead, and because the <code>Product</code> never escapes, escape analysis can keep its fields in registers — the allocation disappears entirely (scalar replacement).';
      }

      code = '// what the compiled code is equivalent to\n' +
             (tier === 0 ? 'long total(OrderLine line) {\n    return line.product().price() * line.qty();   // interpreted, bytecode by bytecode\n}'
             : tier === 3 ? 'long total(OrderLine line) {\n    profile.recordReceiver(line.product());         // C1 inserted this\n    return line.product().price() * line.qty();   // still a real call\n}'
             : inlined ? 'long total(OrderLine line) {\n    // price() inlined; Product fields read directly, no call, no allocation\n    return line.product.priceInCents * line.qty;\n}'
             : 'long total(OrderLine line) {\n    Product p = line.product();\n    long price = p.price();     // ' + (megamorphic ? 'vtable dispatch — site is megamorphic' : 'call retained — callee too large to inline') + '\n    return price * line.qty();\n}');

      var kind = tier === 4 && inlined ? 'good' : tier === 4 ? 'gotcha' : 'note';
      out.innerHTML =
        '<dl class="lm-kv">' +
        '<dt>Invocations</dt><dd>' + fmt(n) + '</dd>' +
        '<dt>Tier</dt><dd><strong>' + tierName + '</strong></dd>' +
        '<dt>Inlined</dt><dd>' + (inlined === null ? 'not yet decided' : inlined ? 'yes' : 'no') + '</dd>' +
        '</dl>' +
        '<div class="mt-3 mb-3">' + H.renderCode(code, { lang: 'java', noBar: true }) + '</div>' +
        '<div class="lm-callout lm-callout--' + kind + '"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 16v-5m0-3h.01"/></svg>' +
        '<div><p class="mb-2">' + how + '</p><p class="mb-0">' + inlineWhy + '</p></div></div>';
    }

    inv.addEventListener('input', render);
    mega.addEventListener('change', render);
    big.addEventListener('change', render);
    render();
  });
})();
