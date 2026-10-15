/* JPA & Hibernate §5 — how many statements does a fetch plan cost?
   Formulas are the real ones Hibernate follows; the row counts assume each
   child collection holds LINES rows per parent. */
(function () {
  'use strict';
  var reg = globalThis.LMSandbox.reg, H = globalThis.LMHighlight;

  reg('fetch-plan', function (el) {
    var nIn = el.querySelector('[data-sb-n]');
    var nOut = el.querySelector('[data-sb-nout]');
    var kIn = el.querySelector('[data-sb-k]');
    var strat = el.querySelector('[data-sb-strategy]');
    var bIn = el.querySelector('[data-sb-batch]');
    var bOut = el.querySelector('[data-sb-batchout]');
    var bField = el.querySelector('[data-sb-batchfield]');
    var out = el.querySelector('[data-sb-out]');
    var LINES = 3;

    function render() {
      var n = Number(nIn.value), k = Number(kIn.value), b = Number(bIn.value), s = strat.value;
      nOut.textContent = n;
      bOut.textContent = b;
      bField.hidden = s !== 'batch';

      var statements, rows, code, note, kind = 'note';
      var parentRows = n, childRows = n * k * LINES;

      if (s === 'lazy') {
        statements = 1 + n * k; rows = parentRows + childRows;
        code = 'List<Order> orders = em.createQuery(\n    "select o from Order o", Order.class).getResultList();\nfor (Order o : orders) total += o.getLines().size();   // one select per order';
        note = 'The N+1 shape: one query for the parents, then one per parent per collection as the loop touches each one. At ' + n + ' parents that is ' + (1 + n * k) + ' round trips.';
        kind = n > 5 ? 'danger' : 'gotcha';
      } else if (s === 'eager') {
        statements = 1 + n * k; rows = parentRows + childRows;
        code = '@OneToMany(mappedBy = "order", fetch = FetchType.EAGER)   // on the mapping\nprivate List<OrderLine> lines;\n\nList<Order> orders = em.createQuery(\n    "select o from Order o", Order.class).getResultList();';
        note = 'EAGER on the mapping does not change a JPQL query’s SQL: Hibernate runs the parent query, then immediately fetches every collection with secondary selects. Same ' + (1 + n * k) + ' statements, but now you cannot opt out per query. (Only <code>em.find</code> joins eagerly.)';
        kind = 'danger';
      } else if (s === 'join' || s === 'graph') {
        statements = 1; rows = n * Math.pow(LINES, k);
        code = s === 'join'
          ? 'List<Order> orders = em.createQuery(\n    "select o from Order o join fetch o.lines", Order.class).getResultList();'
          : 'EntityGraph<Order> g = em.createEntityGraph(Order.class);\ng.addAttributeNodes("lines");\nList<Order> orders = em.createQuery("select o from Order o", Order.class)\n    .setHint("jakarta.persistence.fetchgraph", g).getResultList();';
        if (k === 1) {
          note = 'One statement. The result set carries each parent repeated once per child (' + rows + ' rows); Hibernate 6+ de-duplicates the root entities for you.';
          kind = 'good';
        } else {
          note = 'One statement, but fetching ' + k + ' collections at once multiplies the rows: ' + n + ' × ' + LINES + '<sup>' + k + '</sup> = ' + rows + ' rows for ' + (parentRows + childRows) + ' useful ones. With <code>List</code> collections Hibernate refuses outright (<code>MultipleBagFetchException</code>); with <code>Set</code> it runs and you pay the cartesian product. Fetch the second collection with a second query or batch fetching.';
          kind = 'gotcha';
        }
      } else if (s === 'batch') {
        statements = 1 + k * Math.ceil(n / b); rows = parentRows + childRows;
        code = '@OneToMany(mappedBy = "order")\n@BatchSize(size = ' + b + ')\nprivate List<OrderLine> lines;\n// or per session: em.unwrap(Session.class).setFetchBatchSize(' + b + ')';
        note = 'Parents first, then the collections in <code>where order_id in (?, …)</code> chunks of ' + b + ': 1 + ' + k + ' × ⌈' + n + ' / ' + b + '⌉ = ' + statements + ' statements, no row duplication. The usual answer when a page shows a list and each row needs a collection.';
        kind = 'good';
      } else {
        statements = 1 + k; rows = parentRows + childRows;
        code = '@OneToMany(mappedBy = "order")\n@Fetch(FetchMode.SUBSELECT)      // org.hibernate.annotations\nprivate List<OrderLine> lines;';
        note = 'When the first collection is touched, Hibernate loads that collection for <em>every</em> parent the original query returned, by re-running it as a subquery: <code>where order_id in (select id from orders …)</code>. 1 + ' + k + ' statements regardless of N — but all N collections are loaded even if you only needed one.';
        kind = 'good';
      }

      out.innerHTML =
        H.renderCode(code, { lang: 'java', noBar: true }) +
        '<dl class="lm-kv" style="margin-top:.9rem">' +
        '<dt>SQL statements</dt><dd><strong>' + statements + '</strong> for ' + n + ' parent' + (n === 1 ? '' : 's') + ' and ' + k + ' collection' + (k === 1 ? '' : 's') + '</dd>' +
        '<dt>Rows transferred</dt><dd>' + rows + ' (each parent has ' + LINES + ' rows per collection)</dd>' +
        '</dl>' +
        '<div class="lm-callout lm-callout--' + kind + '" style="margin-top:.6rem">' +
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">' +
        (kind === 'good' ? '<path d="M20 6 9 17l-5-5"/>' : kind === 'danger' ? '<circle cx="12" cy="12" r="9"/><path d="m15 9-6 6m0-6 6 6"/>' : '<path d="M12 9v4m0 4h.01M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/>') +
        '</svg><div>' + note + '</div></div>';
    }

    nIn.addEventListener('input', render);
    kIn.addEventListener('change', render);
    strat.addEventListener('change', render);
    bIn.addEventListener('input', render);
    render();
  });
})();
