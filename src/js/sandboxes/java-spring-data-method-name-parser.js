/* Spring Data §3 — assemble a derived-query method name and show what the
   parser makes of it: the predicate tree, the JPQL and the SQL where clause.
   A model of PartTree's grammar for the keywords offered, not the real parser. */
(function () {
  'use strict';
  var reg = globalThis.LMSandbox.reg, H = globalThis.LMHighlight;

  reg('method-name-parser', function (el) {
    var q = function (s) { return el.querySelector(s); };
    var subj = q('[data-sb-subject]'), propA = q('[data-sb-propa]'), opA = q('[data-sb-opa]'),
        conn = q('[data-sb-conn]'), propB = q('[data-sb-propb]'), opB = q('[data-sb-opb]'),
        order = q('[data-sb-order]'), out = q('[data-sb-out]');

    var COLUMN = { sku: 'p1_0.sku', name: 'p1_0.name', category: 'p1_0.category',
                   priceInCents: 'p1_0.price_in_cents', active: 'p1_0.active', 'supplier.name': 's1_0.name' };
    var TYPE = { sku: 'String', name: 'String', category: 'Category', priceInCents: 'int', active: 'boolean', 'supplier.name': 'String' };
    var cap = function (s) { return s.split('.').map(function (x) { return x.charAt(0).toUpperCase() + x.slice(1); }).join(''); };

    // returns { word, jpql, sql, params: [type…] } or { error }
    function part(prop, op, n) {
      var c = COLUMN[prop], t = TYPE[prop], j = 'p.' + prop, P = function (i) { return '?' + (n + i); };
      switch (op) {
        case 'Is': return { word: cap(prop), jpql: j + ' = ' + P(1), sql: c + '=?', params: [t] };
        case 'LessThan':
        case 'GreaterThanEqual':
          if (t === 'boolean' || t === 'Category') return { error: op + ' needs an ordered type; ' + prop + ' is ' + t + '.' };
          return { word: cap(prop) + op, jpql: j + (op === 'LessThan' ? ' < ' : ' >= ') + P(1),
                   sql: c + (op === 'LessThan' ? '<?' : '>=?'), params: [t] };
        case 'Between':
          if (t === 'boolean' || t === 'Category') return { error: 'Between needs an ordered type; ' + prop + ' is ' + t + '.' };
          return { word: cap(prop) + 'Between', jpql: j + ' between ' + P(1) + ' and ' + P(2),
                   sql: c + ' between ? and ?', params: [t, t] };
        case 'StartingWith':
        case 'Containing':
          if (t !== 'String') return { error: op + ' only applies to strings; ' + prop + ' is ' + t + '.' };
          return { word: cap(prop) + op, jpql: j + ' like ' + P(1) + ' (pattern built and escaped for you)',
                   sql: c + " like ? escape '\\'", params: [t] };
        case 'In':
          return { word: cap(prop) + 'In', jpql: j + ' in ' + P(1), sql: c + ' in (?,?,…)', params: ['Collection<' + (t === 'int' ? 'Integer' : t === 'boolean' ? 'Boolean' : t) + '>'] };
        case 'IsNull':
          return { word: cap(prop) + 'IsNull', jpql: j + ' is null', sql: c + ' is null', params: [] };
        case 'True':
          if (t !== 'boolean') return { error: 'True/False only apply to boolean properties; ' + prop + ' is ' + t + '.' };
          return { word: cap(prop) + 'True', jpql: j + ' = true', sql: c + '=true', params: [] };
        case 'IgnoreCase':
          if (t !== 'String') return { error: 'IgnoreCase only applies to strings; ' + prop + ' is ' + t + '.' };
          return { word: cap(prop) + 'IgnoreCase', jpql: 'upper(' + j + ') = upper(' + P(1) + ')',
                   sql: 'upper(' + c + ')=upper(?)', params: [t] };
      }
      return { error: 'unknown operator' };
    }

    var ORDER = {
      '': { word: '', jpql: '', sql: '' },
      SkuAsc: { word: 'OrderBySkuAsc', jpql: ' order by p.sku asc', sql: ' order by p1_0.sku' },
      PriceInCentsDesc: { word: 'OrderByPriceInCentsDesc', jpql: ' order by p.priceInCents desc', sql: ' order by p1_0.price_in_cents desc' },
      CategoryAscSkuAsc: { word: 'OrderByCategoryAscSkuAsc', jpql: ' order by p.category asc, p.sku asc', sql: ' order by p1_0.category,p1_0.sku' }
    };

    var SUBJECT = {
      find:         { prefix: 'find', ret: 'List<Product>', select: 'select p from Product p', sqlSel: 'select p1_0.id,… from product p1_0', limit: '' },
      findFirst3:   { prefix: 'findFirst3', ret: 'List<Product>', select: 'select p from Product p', sqlSel: 'select p1_0.id,… from product p1_0', limit: ' fetch first ? rows only' },
      findDistinct: { prefix: 'findDistinct', ret: 'List<Product>', select: 'select distinct p from Product p', sqlSel: 'select distinct p1_0.id,… from product p1_0', limit: '' },
      count:        { prefix: 'count', ret: 'long', select: 'select count(p) from Product p', sqlSel: 'select count(p1_0.id) from product p1_0', limit: '' },
      exists:       { prefix: 'exists', ret: 'boolean', select: 'select p.id from Product p', sqlSel: 'select p1_0.id from product p1_0', limit: ' fetch first ? rows only' },
      delete:       { prefix: 'delete', ret: 'long', select: 'select p from Product p', sqlSel: 'select p1_0.id,… from product p1_0', limit: '' }
    };

    function callout(kind, title, body) {
      var icon = kind === 'good' ? '<path d="M20 6 9 17l-5-5"/>'
        : kind === 'danger' ? '<circle cx="12" cy="12" r="9"/><path d="m15 9-6 6m0-6 6 6"/>'
        : '<path d="M12 9v4m0 4h.01M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/>';
      return '<div class="lm-callout lm-callout--' + kind + '" style="margin:.9rem 0 0"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">' +
        icon + '</svg><div><div class="lm-callout-title">' + title + '</div>' + body + '</div></div>';
    }

    function render() {
      var s = SUBJECT[subj.value];
      var a = part(propA.value, opA.value, 0);
      var hasB = conn.value !== '';
      propB.disabled = opB.disabled = !hasB;
      var b = hasB && !a.error ? part(propB.value, opB.value, a.params.length) : null;
      var o = ORDER[order.value];
      var err = a.error || (b && b.error);

      if (err) {
        out.innerHTML = callout('danger', 'Fails at startup',
          '<code>PropertyReferenceException</code> / <code>IllegalArgumentException</code> while the repository bean is built: ' + H.esc(err) +
          ' The application never starts, which is the parser doing its job.');
        return;
      }

      var name = s.prefix + 'By' + a.word + (b ? conn.value + b.word : '') + o.word;
      var params = a.params.concat(b ? b.params : []);
      var sig = s.ret + ' ' + name + '(' + params.map(function (t, i) { return t + ' p' + (i + 1); }).join(', ') + ');';

      var where = a.jpql + (b ? ' ' + conn.value.toLowerCase() + ' ' + b.jpql : '');
      var jpql = s.select + (propA.value === 'supplier.name' || (b && propB.value === 'supplier.name') ? ' join p.supplier s' : '') +
                 ' where ' + where + o.jpql;
      var sqlWhere = a.sql + (b ? ' ' + conn.value.toLowerCase() + ' ' + b.sql : '');
      var join = propA.value === 'supplier.name' ? ' join supplier s1_0 on s1_0.id=p1_0.supplier_id' : '';
      var sql = (subj.value === 'delete' ? '-- 1: load the matches\n' : '') + s.sqlSel + join + ' where ' + sqlWhere + o.sql + s.limit +
                (subj.value === 'delete' ? '\n-- 2: one per loaded entity, at flush\ndelete from product where id=?' : '');

      var tree = '<ul class="mb-0 text-sm"><li><strong>subject</strong>: ' + H.esc(s.prefix) + (s.limit && subj.value === 'findFirst3' ? ' (limit 3)' : '') + '</li>' +
        '<li><strong>predicate</strong>: ' + (b ? '<code>' + H.esc(conn.value) + '</code><ul><li>' + H.esc(a.word) + '</li><li>' + H.esc(b.word) + '</li></ul>' : H.esc(a.word)) + '</li>' +
        (o.word ? '<li><strong>sort</strong>: ' + H.esc(o.word.replace('OrderBy', '')) + '</li>' : '') + '</ul>';

      var len = name.length;
      var verdict = len > 40
        ? callout('gotcha', name.length + ' characters: write a @Query', 'The name is valid, but nobody will review it as a specification. Give the method a name that says what it is <em>for</em> and put the predicate in <code>@Query</code> (§4).')
        : subj.value === 'delete'
          ? callout('gotcha', 'A derived delete loads before it deletes', 'One <code>select</code>, then one <code>delete</code> per row at flush, so cascades and callbacks fire. Needs a transaction. For bulk removal use <code>@Modifying @Query("delete …")</code>.')
          : (opA.value === 'Containing' || (b && opB.value === 'Containing'))
            ? callout('gotcha', 'Leading wildcard', '<code>Containing</code> becomes <code>like \'%…%\'</code>; no ordinary B-tree index can serve it.')
            : (opA.value === 'IgnoreCase')
              ? callout('gotcha', 'upper() on the column', 'An index on the plain column is unusable for this predicate unless you add a functional index on <code>upper(' + H.esc(propA.value) + ')</code>.')
              : callout('good', 'Readable and valid', 'Short enough to read as a sentence; Spring Data validates every property against the metamodel at startup.');

      out.innerHTML =
        '<div class="lm-stepper-label">Method</div>' + H.renderCode(sig, { lang: 'java', noBar: true }) +
        '<div class="lm-stepper-label mt-3">Parsed</div>' + tree +
        '<div class="lm-stepper-label mt-3">JPQL</div>' + H.renderCode(jpql, { lang: 'plain', noBar: true }) +
        '<div class="lm-stepper-label mt-3">SQL (Hibernate 7, H2)</div>' + H.renderCode(sql, { lang: 'sql', noBar: true }) +
        verdict;
    }

    [subj, propA, opA, conn, propB, opB, order].forEach(function (c) { c.addEventListener('change', render); });
    render();
  });
})();
