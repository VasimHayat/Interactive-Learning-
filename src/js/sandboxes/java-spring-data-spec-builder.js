/* Spring Data §9 — compose a Specification from whichever criteria are present.
   The dataset is the six products of the worked example; filtering happens in
   the browser with the same semantics the generated SQL has. */
(function () {
  'use strict';
  var reg = globalThis.LMSandbox.reg, H = globalThis.LMHighlight;

  reg('spec-builder', function (el) {
    var DATA = [
      { sku: 'BOLT-7', cat: 'FASTENER', price: 80, active: true },
      { sku: 'BOLT-9', cat: 'FASTENER', price: 95, active: false },
      { sku: 'NUT-3', cat: 'FASTENER', price: 25, active: true },
      { sku: 'GIRD-1', cat: 'STRUCTURAL', price: 120000, active: true },
      { sku: 'ROD-4', cat: 'STRUCTURAL', price: 4500, active: true },
      { sku: 'CAM-9', cat: 'MOTION', price: 2300, active: true }
    ];
    var q = function (s) { return el.querySelector(s); };
    var useCat = q('[data-sb-usecat]'), cat = q('[data-sb-cat]');
    var min = q('[data-sb-min]'), max = q('[data-sb-max]');
    var minOut = q('[data-sb-minout]'), maxOut = q('[data-sb-maxout]');
    var prefix = q('[data-sb-prefix]'), active = q('[data-sb-active]');
    var comb = q('[data-sb-comb]'), sort = q('[data-sb-sort]'), out = q('[data-sb-out]');
    var MAXV = Number(max.max);

    function render() {
      var lo = Number(min.value) > 0 ? Number(min.value) : null;
      var hi = Number(max.value) < MAXV ? Number(max.value) : null;
      minOut.textContent = lo === null ? 'off' : lo.toLocaleString('en-US');
      maxOut.textContent = hi === null ? 'off' : hi.toLocaleString('en-US');

      // each present criterion: [java factory call, sql predicate, test]
      var parts = [];
      if (useCat.checked) {
        var c = cat.value;
        parts.push(['inCategory(Category.' + c + ')', 'p1_0.category=?', function (p) { return p.cat === c; }]);
      }
      if (lo !== null || hi !== null) {
        var a = lo === null ? 0 : lo, b = hi === null ? 2147483647 : hi;
        parts.push(['priceBetween(' + (lo === null ? '0' : lo) + ', ' + (hi === null ? 'Integer.MAX_VALUE' : hi) + ')',
                    'p1_0.price_in_cents between ? and ?', function (p) { return p.price >= a && p.price <= b; }]);
      }
      if (prefix.value) {
        var pre = prefix.value;
        parts.push(['skuStartsWith("' + pre + '")', "p1_0.sku like ? escape ''", function (p) { return p.sku.indexOf(pre) === 0; }]);
      }
      if (active.checked) {
        parts.push(['isActive()', 'p1_0.active', function (p) { return p.active; }]);
      }

      var or = comb.value === 'or';
      var fn = or ? 'anyOf' : 'allOf';
      var java = 'Specification<Product> spec = Specification.' + fn + '(' +
        (parts.length ? '\n    ' + parts.map(function (x) { return 'ProductSpecs.' + x[0]; }).join(',\n    ') + ')' : ')') + ';\n' +
        'List<Product> hits = products.findAll(spec, ' +
        (sort.value === 'sku' ? 'Sort.by("sku")' : 'Sort.by("priceInCents").descending()') + ');';

      var where = parts.length ? ' where ' + parts.map(function (x) { return x[1]; }).join(or ? ' or ' : ' and ') : '';
      var order = sort.value === 'sku' ? ' order by p1_0.sku' : ' order by p1_0.price_in_cents desc';
      var sql = 'select p1_0.id,p1_0.active,p1_0.category,p1_0.price_in_cents,p1_0.sku,p1_0.supplier_id\nfrom product p1_0' +
                (where ? '\n' + where.trim() : '') + '\n' + order.trim();

      var rows = DATA.filter(function (p) {
        if (!parts.length) return true;
        return or ? parts.some(function (x) { return x[2](p); }) : parts.every(function (x) { return x[2](p); });
      }).sort(function (x, y) {
        return sort.value === 'sku' ? (x.sku < y.sku ? -1 : x.sku > y.sku ? 1 : 0) : y.price - x.price;
      });

      var note;
      if (!parts.length) {
        note = 'No criterion is present, so <code>' + fn + '</code> receives an empty list and returns the unrestricted specification: the SQL has <strong>no <code>where</code> clause at all</strong> — not <code>where 1=1</code>.';
      } else if (or && parts.length > 1) {
        note = 'With <code>anyOf</code> a row needs to satisfy only one criterion. That is rarely what a search form means: most filter UIs want <code>allOf</code>, with <code>or</code> reserved for alternatives of one field.';
      } else if (prefix.value) {
        note = 'Each present criterion contributes one bound predicate. Note <code>escape \'\'</code> on the prefix: a hand-written <code>cb.like</code> treats <code>%</code> and <code>_</code> in user input as wildcards.';
      } else {
        note = 'Each present criterion contributes one bound predicate; absent ones contribute nothing. The same specification would be reused unchanged for the count query of a <code>Page</code>.';
      }

      out.innerHTML =
        '<div class="lm-stepper-label">Java</div>' + H.renderCode(java, { lang: 'java', noBar: true }) +
        '<div class="lm-stepper-label mt-3">SQL Hibernate would emit</div>' + H.renderCode(sql, { lang: 'sql', noBar: true }) +
        '<div class="lm-stepper-label mt-3">Rows (' + rows.length + ' of ' + DATA.length + ')</div>' +
        '<div class="lm-mem" style="margin:.4rem 0 .9rem">' +
        (rows.length ? rows.map(function (p) {
          return '<div class="lm-cell is-hi"><span class="lm-cell-idx">' + H.esc(p.cat) + '</span>' + H.esc(p.sku) +
                 '<br>' + p.price.toLocaleString('en-US') + 'c' + (p.active ? '' : ' · inactive') + '</div>';
        }).join('') : '<div class="lm-cell is-empty">no rows</div>') + '</div>' +
        '<div class="lm-callout lm-callout--note"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 16v-5m0-3h.01"/></svg>' +
        '<div>' + note + '</div></div>';
    }

    [useCat, cat, prefix, active, comb, sort].forEach(function (c) { c.addEventListener('change', render); });
    [min, max].forEach(function (c) { c.addEventListener('input', render); });
    render();
  });
})();
