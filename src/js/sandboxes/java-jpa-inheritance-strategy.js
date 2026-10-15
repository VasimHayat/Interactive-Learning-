/* JPA & Hibernate §8 — what each inheritance strategy does to the schema and
   to the SQL. Pre-authored shapes that mirror what Hibernate 7 emits for H2;
   nothing is compiled or executed in the browser. */
(function () {
  'use strict';
  var reg = globalThis.LMSandbox.reg, H = globalThis.LMHighlight;

  reg('inheritance-strategy', function (el) {
    var strategySel = el.querySelector('[data-sb-strategy]');
    var nIn = el.querySelector('[data-sb-n]');
    var nOut = el.querySelector('[data-sb-nout]');
    var ops = Array.prototype.slice.call(el.querySelectorAll('[data-sb-op]'));
    var out = el.querySelector('[data-sb-out]');

    var SUBS = ['PercentOff', 'FixedOff', 'BuyNGetOne', 'Clearance', 'Loyalty'];
    var FIELD = { PercentOff: 'percent', FixedOff: 'offCents', BuyNGetOne: 'n', Clearance: 'untilDate', Loyalty: 'tier' };
    var TABLE = { PercentOff: 'percent_off', FixedOff: 'fixed_off', BuyNGetOne: 'buy_n_get_one', Clearance: 'clearance', Loyalty: 'loyalty' };
    var DISC = { PercentOff: 'PCT', FixedOff: 'FIX', BuyNGetOne: 'BNG', Clearance: 'CLR', Loyalty: 'LOY' };

    function cell(title, cols, flag) {
      return '<div class="lm-cell' + (flag ? ' is-hi' : '') + '" style="min-width:11rem;text-align:left">' +
             '<span class="lm-cell-idx">' + H.esc(title) + '</span>' + cols.map(H.esc).join('<br>') + '</div>';
    }

    function render() {
      var strategy = strategySel.value;
      var n = Number(nIn.value);
      nOut.textContent = n;
      var subs = SUBS.slice(0, n);
      var op = (ops.filter(function (r) { return r.checked; })[0] || ops[0]).value;
      var sub = subs[0];

      var tables = '', sql = '', constraints = '', verdict = '', kind = 'note';

      if (strategy === 'SINGLE_TABLE') {
        var cols = ['id bigint PK', 'kind varchar(8) not null', 'code varchar(16) not null'];
        subs.forEach(function (s) { cols.push(FIELD[s] + ' (nullable)'); });
        tables = cell('discount', cols, true);
        if (op === 'base') sql = 'select d1_0.id, d1_0.kind, d1_0.code, ' + subs.map(function (s) { return 'd1_0.' + FIELD[s]; }).join(', ') + '\nfrom discount d1_0';
        else if (op === 'sub') sql = 'select d1_0.id, d1_0.code, d1_0.' + FIELD[sub] + '\nfrom discount d1_0\nwhere d1_0.kind=\'' + DISC[sub] + '\'';
        else sql = 'insert into discount (code, ' + FIELD[sub] + ', kind, id)\nvalues (?, ?, \'' + DISC[sub] + '\', ?)';
        constraints = 'Subclass columns must be nullable: a ' + subs[1] + ' row has no ' + FIELD[sub] + '. Only a check constraint such as <code>kind &lt;&gt; \'' + DISC[sub] + '\' or ' + FIELD[sub] + ' is not null</code> can guard it.';
        verdict = (op === 'base' ? 'One select, no join: the cheapest polymorphic read there is.' : op === 'sub' ? 'The same table with a discriminator predicate — an index on <code>kind</code> makes it cheap.' : 'One row. The columns of every other subtype are written as NULL.') +
                  (n >= 4 ? ' With ' + n + ' subtypes the table is getting wide and sparse; this is the point at which JOINED starts to look tidier.' : '');
      } else if (strategy === 'JOINED') {
        tables = cell('discount', ['id bigint PK', 'code varchar(16) not null'], true) +
                 subs.map(function (s) { return cell(TABLE[s], ['id bigint PK, FK → discount', FIELD[s] + ' not null']); }).join('');
        if (op === 'base') {
          sql = 'select d1_0.id,\n       case ' + subs.map(function (s, i) { return 'when d1_' + (i + 1) + '.id is not null then ' + (i + 1); }).join(' ') + ' end,\n       d1_0.code, ' + subs.map(function (s, i) { return 'd1_' + (i + 1) + '.' + FIELD[s]; }).join(', ') +
                '\nfrom discount d1_0\n' + subs.map(function (s, i) { return 'left join ' + TABLE[s] + ' d1_' + (i + 1) + ' on d1_0.id=d1_' + (i + 1) + '.id'; }).join('\n');
        } else if (op === 'sub') {
          sql = 'select po1_0.id, po1_1.code, po1_0.' + FIELD[sub] + '\nfrom ' + TABLE[sub] + ' po1_0\njoin discount po1_1 on po1_0.id=po1_1.id';
        } else {
          sql = 'insert into discount (code, id) values (?, ?)\ninsert into ' + TABLE[sub] + ' (' + FIELD[sub] + ', id) values (?, ?)';
        }
        constraints = 'Every subclass column can be <code>NOT NULL</code>: it lives in a table only its own rows occupy. The shared primary key is enforced by a foreign key from each subclass table to <code>discount</code>.';
        verdict = op === 'base' ? n + ' left joins and a <code>case</code> expression to work out the type of each row. Fine for a handful of subtypes; expensive per row as ' + n + ' grows.' :
                  op === 'sub' ? 'An inner join to the parent table — one join regardless of how many sibling subtypes exist.' :
                  'Two inserts per entity (parent row, then the subclass row with the same id), and one more per extra level of hierarchy.';
      } else if (strategy === 'TABLE_PER_CLASS') {
        tables = subs.map(function (s) { return cell(TABLE[s], ['id bigint PK (shared sequence)', 'code varchar(16) not null', FIELD[s] + ' not null']); }).join('');
        if (op === 'base') {
          sql = 'select d1_0.id, d1_0.clazz_, d1_0.code, ' + subs.map(function (s) { return 'd1_0.' + FIELD[s]; }).join(', ') + '\nfrom (\n' +
                subs.map(function (s, i) {
                  return '  select id, code, ' + subs.map(function (t) { return t === s ? FIELD[t] : 'null as ' + FIELD[t]; }).join(', ') + ', ' + (i + 1) + ' as clazz_ from ' + TABLE[s];
                }).join('\n  union all\n') + '\n) d1_0';
        } else if (op === 'sub') {
          sql = 'select po1_0.id, po1_0.code, po1_0.' + FIELD[sub] + '\nfrom ' + TABLE[sub] + ' po1_0';
        } else {
          sql = 'select next value for discount_SEQ\ninsert into ' + TABLE[sub] + ' (code, ' + FIELD[sub] + ', id) values (?, ?, ?)';
        }
        constraints = 'Every column can be <code>NOT NULL</code>. There is no <code>discount</code> table at all, so nothing can reference "a discount of any kind" with a plain foreign key, and ids must come from one generator shared by all ' + n + ' tables (<code>IDENTITY</code> is rejected).';
        verdict = op === 'base' ? 'A <code>union all</code> of ' + n + ' tables with a synthetic <code>clazz_</code> column. No index spans a union, so <code>find()</code> by id scans every table. This is why the strategy is rarely used.' :
                  op === 'sub' ? 'The best case: one table, no join, no discriminator — identical to a non-inherited entity.' :
                  'One row in one table. Fast, but the id came from a sequence shared across all subtypes.';
        kind = op === 'base' ? 'gotcha' : 'note';
      } else {
        tables = subs.map(function (s) { return cell(TABLE[s], ['id bigint PK', 'code varchar(16) not null  ← from the superclass', FIELD[s] + ' not null']); }).join('');
        if (op === 'base') {
          sql = '-- no SQL: "select d from Discount d" is a compile-time error in JPQL,\n-- a @MappedSuperclass is not an entity and has no name to query';
          kind = 'gotcha';
        } else if (op === 'sub') {
          sql = 'select po1_0.id, po1_0.code, po1_0.' + FIELD[sub] + '\nfrom ' + TABLE[sub] + ' po1_0';
        } else {
          sql = 'insert into ' + TABLE[sub] + ' (code, ' + FIELD[sub] + ', id) values (?, ?, ?)';
        }
        constraints = 'The superclass\'s mapped fields are copied into each entity table as ordinary columns; all can be <code>NOT NULL</code>. Nothing links the ' + n + ' tables.';
        verdict = op === 'base' ? 'There is no polymorphism at the persistence level. If you need "all discounts", this is the wrong choice; if you only wanted shared columns and code, it is the right one.' :
                  op === 'sub' ? 'Indistinguishable from an entity that declared the inherited fields itself.' :
                  'A single plain insert.';
      }

      out.innerHTML =
        '<div class="lm-stepper-label">Tables</div>' +
        '<div class="lm-mem" style="margin:.4rem 0 .9rem;flex-wrap:wrap">' + tables + '</div>' +
        '<div class="lm-stepper-label">SQL shape for: ' + H.esc(op === 'base' ? 'select d from Discount d' : op === 'sub' ? 'select s from ' + sub + ' s' : 'em.persist(new ' + sub + '(…))') + '</div>' +
        H.renderCode(sql, { lang: 'sql', noBar: true }) +
        '<div class="lm-callout lm-callout--' + kind + '" style="margin-top:.9rem">' +
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">' +
        (kind === 'gotcha' ? '<path d="M12 9v4m0 4h.01M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/>' : '<circle cx="12" cy="12" r="9"/><path d="M12 16v-5m0-3h.01"/>') +
        '</svg><div><div class="lm-callout-title">' + H.esc(strategy) + ' · ' + n + ' subtypes</div>' +
        '<p style="margin:0 0 .4rem">' + verdict + '</p>' +
        '<p style="margin:0;color:var(--lm-text-muted)"><strong>Constraints:</strong> ' + constraints + '</p></div></div>';
    }

    strategySel.addEventListener('change', render);
    nIn.addEventListener('input', render);
    ops.forEach(function (r) { r.addEventListener('change', render); });
    render();
  });
})();
