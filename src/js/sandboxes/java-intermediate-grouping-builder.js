/* Java Intermediate §6 — groupingBy builder.
   Fixed six-product dataset; the user picks a classifier, a downstream
   collector and a map type, and sees both the Java expression and the Map it
   produces. Semantics mirror the JDK: TreeMap sorts keys, LinkedHashMap keeps
   first-encounter order, HashMap order is unspecified (shown in hash order of
   the String keys, computed with String.hashCode's algorithm, to make the
   point that it is neither sorted nor insertion order). */
(function () {
  'use strict';
  var reg = globalThis.LMSandbox.reg, H = globalThis.LMHighlight;

  reg('grouping-builder', function (el) {
    var DATA = [
      { sku: 'BOLT-7', category: 'FASTENER',   price: 80,     onHand: 400 },
      { sku: 'NUT-3',  category: 'FASTENER',   price: 25,     onHand: 1200 },
      { sku: 'PIN-2',  category: 'FASTENER',   price: 15,     onHand: 6 },
      { sku: 'GIRD-1', category: 'STRUCTURAL', price: 120000, onHand: 3 },
      { sku: 'ROD-4',  category: 'STRUCTURAL', price: 4500,   onHand: 40 },
      { sku: 'CAM-9',  category: 'MOTION',     price: 2300,   onHand: 2 }
    ];
    var ENUM_ORDER = ['FASTENER', 'STRUCTURAL', 'MOTION'];

    var CLASSIFIERS = {
      category: { expr: 'Product::category', keyType: 'Category', key: function (p) { return p.category; } },
      initial:  { expr: 'p -> p.sku().charAt(0)', keyType: 'Character', key: function (p) { return p.sku.charAt(0); } },
      band:     { expr: 'p -> p.priceInCents() < 100 ? "cheap" : "dear"', keyType: 'String',
                  key: function (p) { return p.price < 100 ? 'cheap' : 'dear'; } }
    };

    var DOWNSTREAMS = {
      toList:   { expr: 'Collectors.toList()', valType: 'List<Product>',
                  fold: function (ps) { return '[' + ps.map(function (p) { return p.sku; }).join(', ') + ']'; } },
      counting: { expr: 'Collectors.counting()', valType: 'Long',
                  fold: function (ps) { return String(ps.length); } },
      summing:  { expr: 'Collectors.summingInt(Product::priceInCents)', valType: 'Integer',
                  fold: function (ps) { return String(ps.reduce(function (s, p) { return s + p.price; }, 0)); } },
      mapping:  { expr: 'Collectors.mapping(Product::sku, Collectors.joining("|"))', valType: 'String',
                  fold: function (ps) { return '"' + ps.map(function (p) { return p.sku; }).join('|') + '"'; } },
      maxBy:    { expr: 'Collectors.maxBy(Comparator.comparingInt(Product::priceInCents))', valType: 'Optional<Product>',
                  fold: function (ps) {
                    var best = ps.reduce(function (a, p) { return p.price > a.price ? p : a; }, ps[0]);
                    return 'Optional[' + best.sku + ']';
                  } }
    };

    var MAPS = {
      hash:   { expr: '', name: 'HashMap' },
      tree:   { expr: 'TreeMap::new', name: 'TreeMap' },
      linked: { expr: 'LinkedHashMap::new', name: 'LinkedHashMap' }
    };

    var clsSel = el.querySelector('[data-sb-classifier]');
    var dsSel = el.querySelector('[data-sb-downstream]');
    var mapSel = el.querySelector('[data-sb-map]');
    var exprOut = el.querySelector('[data-sb-expr]');
    var out = el.querySelector('[data-sb-out]');

    // java.lang.String.hashCode, so the "unspecified" HashMap order is at least honest
    function jhash(s) {
      var h = 0;
      for (var i = 0; i < s.length; i++) h = (Math.imul(31, h) + s.charCodeAt(i)) | 0;
      return h;
    }
    function bucket(key) {
      var h = jhash(key);
      h = h ^ (h >>> 16);
      return h & 15;            // default table size 16
    }

    function render() {
      var cls = CLASSIFIERS[clsSel.value];
      var ds = DOWNSTREAMS[dsSel.value];
      var map = MAPS[mapSel.value];

      // group in encounter order first
      var groups = {}, order = [];
      DATA.forEach(function (p) {
        var k = String(cls.key(p));
        if (!groups[k]) { groups[k] = []; order.push(k); }
        groups[k].push(p);
      });

      var keys;
      if (mapSel.value === 'tree') {
        keys = order.slice().sort(function (a, b) {
          if (clsSel.value === 'category') return ENUM_ORDER.indexOf(a) - ENUM_ORDER.indexOf(b);   // enums sort by ordinal
          return a < b ? -1 : a > b ? 1 : 0;
        });
      } else if (mapSel.value === 'linked') {
        keys = order.slice();
      } else {
        // HashMap: by bucket index, then insertion within a bucket. For enum keys
        // the hash is the identity hash, which really is unpredictable run to run.
        keys = order.slice().sort(function (a, b) { return bucket(a) - bucket(b) || order.indexOf(a) - order.indexOf(b); });
      }

      var mapArgs = map.expr ? ', ' + map.expr : '';
      var expr = 'Map<' + cls.keyType + ', ' + ds.valType + '> result = stock.stream()\n' +
                 '    .collect(Collectors.groupingBy(' + cls.expr + mapArgs + ',\n' +
                 '             ' + ds.expr + '));';
      exprOut.innerHTML = H.renderCode(expr, { lang: 'java', noBar: true });

      var rows = keys.map(function (k) {
        return '<tr><td><code>' + H.esc(k) + '</code></td><td><code>' + H.esc(ds.fold(groups[k])) + '</code></td></tr>';
      }).join('');

      var orderNote;
      if (mapSel.value === 'tree') orderNote = 'A <code>TreeMap</code> iterates in key order — enum keys by declaration order, strings and characters alphabetically.';
      else if (mapSel.value === 'linked') orderNote = 'A <code>LinkedHashMap</code> iterates in the order each key was <em>first</em> seen in the stream.';
      else if (clsSel.value === 'category') orderNote = 'The default <code>HashMap</code> gives no ordering guarantee. Enum keys use an identity hash, so this order can differ between runs of the same program — shown here in one plausible bucket order.';
      else orderNote = 'The default <code>HashMap</code> gives no ordering guarantee. Shown in bucket order for a 16-slot table using <code>String.hashCode</code> — notice it is neither sorted nor insertion order.';

      out.innerHTML =
        '<div class="lm-table-wrap"><table>' +
        '<caption class="lm-visually-hidden">Resulting map</caption>' +
        '<thead><tr><th scope="col">Key (' + H.esc(cls.keyType) + ')</th><th scope="col">Value (' + H.esc(ds.valType) + ')</th></tr></thead>' +
        '<tbody>' + rows + '</tbody></table></div>' +
        '<div class="lm-callout lm-callout--note" style="margin:.9rem 0 0">' +
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 16v-5m0-3h.01"/></svg>' +
        '<div><div class="lm-callout-title">' + keys.length + ' key' + (keys.length === 1 ? '' : 's') + ' in a ' + map.name + '</div>' + orderNote +
        (dsSel.value === 'maxBy' ? '<br><span style="color:var(--lm-text-muted)">Every value is an <code>Optional</code> even though no group can be empty — wrap <code>maxBy</code> in <code>collectingAndThen(…, Optional::get)</code> to unwrap.</span>' : '') +
        '</div></div>';
    }

    clsSel.addEventListener('change', render);
    dsSel.addEventListener('change', render);
    mapSel.addEventListener('change', render);
    render();
  });
})();
