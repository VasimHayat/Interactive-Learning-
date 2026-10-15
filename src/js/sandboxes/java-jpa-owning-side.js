/* JPA & Hibernate §4 — which side of an association is written?
   Pure rule evaluation: the owning side is the one holding the foreign key
   (or declaring the join table); mappedBy marks the inverse side, which
   Hibernate reads from but never writes from. */
(function () {
  'use strict';
  var reg = globalThis.LMSandbox.reg, H = globalThis.LMHighlight;

  reg('owning-side', function (el) {
    var type = el.querySelector('[data-sb-type]');
    var inverse = el.querySelector('[data-sb-inverse]');
    var owning = el.querySelector('[data-sb-owning]');
    var cascade = el.querySelector('[data-sb-cascade]');
    var out = el.querySelector('[data-sb-out]');

    var MODELS = {
      o2m: {
        parent: 'Order', child: 'OrderLine',
        inverseLabel: 'order.getLines().add(line)        // Order.lines is mappedBy = "order": the INVERSE side',
        owningLabel:  'line.setOrder(order)              // OrderLine.order holds the FK: the OWNING side',
        persistParent: 'em.persist(order);',
        childInsert: 'insert into order_line (order_id,qty,sku,id) values (',
        fkName: 'order_id',
        howOwning: 'the <code>@ManyToOne</code> field <code>OrderLine.order</code> — it maps the <code>order_id</code> column'
      },
      m2m: {
        parent: 'Product', child: 'Supplier',
        inverseLabel: 'supplier.getProducts().add(product) // Supplier.products is mappedBy = "suppliers": the INVERSE side',
        owningLabel:  'product.getSuppliers().add(supplier) // Product.suppliers declares @JoinTable: the OWNING side',
        persistParent: 'em.persist(product);',
        childInsert: 'insert into product_supplier (product_id,supplier_id) values (',
        fkName: 'product_supplier row',
        howOwning: 'the side that declares <code>@JoinTable</code>, <code>Product.suppliers</code>'
      }
    };

    function render() {
      var m = MODELS[type.value];
      var inv = inverse.checked, own = owning.checked, casc = cascade.checked;
      var isM2M = type.value === 'm2m';

      var java = m.persistParent.replace('em.persist', '// ...\nem.persist') + '\n';
      var lines = [];
      if (inv) lines.push(m.inverseLabel);
      if (own) lines.push(m.owningLabel);
      if (!lines.length) lines.push('// neither side set: two unrelated objects');
      java = lines.join('\n') + '\n' + (casc ? '' : 'em.persist(' + (isM2M ? 'supplier' : 'line') + ');          // no cascade: the child must be persisted by hand\n') + m.persistParent + (casc ? '                     // cascade = PERSIST reaches the child through the collection' : '') + '\nem.getTransaction().commit();';

      // what Hibernate writes
      var childPersisted = !casc || (casc && inv);              // cascade only travels along the collection that was set
      var sql = [];
      sql.push(isM2M ? 'insert into product (sku,id) values (?,?)' : 'insert into orders (reference,id) values (?,?)');
      if (childPersisted) {
        if (isM2M) sql.push('insert into supplier (name,id) values (?,?)');
        else sql.push('insert into order_line (order_id,qty,sku,id) values (' + (own ? '?' : 'null') + ',?,?,?)');
      }
      if (isM2M && own && childPersisted) sql.push('insert into product_supplier (product_id,supplier_id) values (?,?)');

      var fkWritten = own && childPersisted;
      var verdictKind, title, body;
      if (!childPersisted) {
        verdictKind = 'danger'; title = 'The child is never inserted';
        body = 'Cascade is on, but the parent’s collection was not touched, so the cascade has nothing to walk. The child object stays transient. If anything on the owning side pointed at it, flush would fail with <code>TransientPropertyValueException</code>; here nothing points at it at all, so it is simply lost.';
      } else if (fkWritten) {
        verdictKind = 'good'; title = 'Linked: the ' + H.esc(m.fkName) + ' is written';
        body = 'The owning side was set, so Hibernate emits the ' + (isM2M ? 'join-table row' : 'foreign key') + '. ' +
               (inv ? 'Setting the inverse side too keeps the in-memory graph consistent for this transaction — that is what the sync helper is for.' :
                      'The in-memory <code>' + m.parent + '</code> does not know about its child until it is reloaded; a sync helper that sets both sides avoids that surprise.');
      } else {
        verdictKind = 'danger'; title = 'Not linked: the ' + H.esc(m.fkName) + ' is ' + (isM2M ? 'never inserted' : 'null');
        body = 'Only the <code>mappedBy</code> side was set. Hibernate treats that side as a read-only view of the relationship, so ' +
               (isM2M ? 'no <code>product_supplier</code> row is written.' : 'the row is inserted with <code>order_id = null</code>.') +
               ' The relationship exists in memory and nowhere else — the next load will find the child orphaned.';
      }

      var icon = verdictKind === 'good' ? '<path d="M20 6 9 17l-5-5"/>' : '<circle cx="12" cy="12" r="9"/><path d="m15 9-6 6m0-6 6 6"/>';
      out.innerHTML =
        '<div class="lm-stepper-label">What you wrote</div>' +
        H.renderCode(java, { lang: 'java', noBar: true }) +
        '<div class="lm-stepper-label mt-3">What Hibernate sends at commit</div>' +
        '<div class="lm-console">' + sql.map(H.esc).join('\n') + '</div>' +
        '<div class="lm-callout lm-callout--' + verdictKind + '" style="margin-top:.9rem">' +
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">' + icon + '</svg>' +
        '<div><div class="lm-callout-title">' + title + '</div><p class="mb-0">' + body +
        ' The owning side here is ' + m.howOwning + '.</p></div></div>';
    }

    type.addEventListener('change', render);
    inverse.addEventListener('change', render);
    owning.addEventListener('change', render);
    cascade.addEventListener('change', render);
    render();
  });
})();
