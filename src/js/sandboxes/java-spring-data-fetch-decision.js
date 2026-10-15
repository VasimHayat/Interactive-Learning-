/* Spring Data §6 — where a lazy collection is touched × open-in-view × fetch
   strategy → what happens. Pure rule evaluation from the behaviour the
   verified program demonstrates; the statement arithmetic is Hibernate's. */
(function () {
  'use strict';
  var reg = globalThis.LMSandbox.reg, H = globalThis.LMHighlight;

  reg('fetch-decision', function (el) {
    var where = el.querySelector('[data-sb-where]');
    var osiv = el.querySelector('[data-sb-osiv]');
    var strategy = el.querySelector('[data-sb-strategy]');
    var nIn = el.querySelector('[data-sb-n]');
    var nOut = el.querySelector('[data-sb-nout]');
    var out = el.querySelector('[data-sb-out]');

    var ICON = {
      good: '<path d="M20 6 9 17l-5-5"/>',
      gotcha: '<path d="M12 9v4m0 4h.01M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/>',
      danger: '<circle cx="12" cy="12" r="9"/><path d="m15 9-6 6m0-6 6 6"/>'
    };

    function render() {
      var n = Number(nIn.value), s = strategy.value, w = where.value, o = osiv.value;
      nOut.textContent = n;
      var kind, title, body, statements, where2;

      var fetchedInQuery = (s === 'graph' || s === 'fetch' || s === 'dto');
      var batched = Math.ceil(n / 8);

      if (s === 'dto') {
        kind = 'good'; title = 'Works: nothing lazy exists';
        statements = 1;
        body = 'The query selects scalars into a record. There is no proxy collection to touch anywhere, so the location of the access and the open-in-view setting are irrelevant. This is the default shape for read screens.';
      } else if (fetchedInQuery) {
        kind = 'good'; title = 'Works: the collection arrived with the parents';
        statements = 1;
        body = 'The join in the generated SQL fills the collection while the parents load. <code>Hibernate.isInitialized(lines)</code> is already <code>true</code> when the method returns, so touching it later — even after the transaction, even with open-in-view off — reads memory, not the database.' +
               (n > 100 ? ' At ' + n + ' parents the single result set carries every line row; if the collection is large consider batch fetching instead to keep rows per statement bounded.' : '');
      } else if (w === 'repo') {
        kind = 'gotcha'; title = 'The repository returns proxies';
        statements = 1;
        body = 'A plain derived query loads the parents only. Each <code>lines</code> field holds an uninitialised proxy; what happens next depends on who touches it. Choose where it is touched above.';
      } else if (w === 'service') {
        if (s === 'batch') {
          kind = 'gotcha'; title = 'Works, with ' + (batched + 1) + ' statements';
          statements = batched + 1;
          body = 'Inside the service the transaction is still open. The first touch loads up to 8 collections in one <code>in (…)</code>; ' + batched + ' such statement' + (batched === 1 ? '' : 's') + ' cover ' + n + ' parents. The N+1 is capped, not removed — a graph or fetch join would be one statement.';
        } else {
          kind = 'danger'; title = 'Works — as an N+1 with ' + (n + 1) + ' statements';
          statements = n + 1;
          body = 'Inside <code>@Transactional</code> every <code>getLines()</code> initialises its proxy with a <code>select … where order_id=?</code>. One query for the parents plus ' + n + ' for the collections, all inside one transaction that was opened for this method. The total is right and the page is slow in proportion to its size.';
        }
      } else { // runner / controller / template
        if (o === 'off') {
          kind = 'danger'; title = 'LazyInitializationException';
          statements = 1;
          body = 'The service transaction committed on return and its persistence context closed. The proxy has no session to load through, so the first <code>size()</code>, <code>get()</code> or iteration throws <code>org.hibernate.LazyInitializationException: failed to lazily initialize a collection … no session</code>. This is phase E of the worked example, and it is the behaviour you want: it makes the missing fetch plan visible at the call site.';
        } else {
          kind = 'gotcha';
          statements = (s === 'batch') ? batched + 1 : n + 1;
          title = 'Works, with ' + statements + ' statements — and a connection held until the view is rendered';
          body = 'The <code>OpenEntityManagerInViewInterceptor</code> keeps the persistence context open for the whole request. The lazy loads run from the controller or template, outside any transaction, in auto-commit mode, on a pooled connection that is now held until the response is written. Nothing throws, nothing warns at request time, and the N+1 lives in a file that statistics in the service never see.' +
                 (s === 'batch' ? ' Batch fetching still caps the count (' + batched + ' collection loads for ' + n + ' parents).' : '');
        }
      }

      var where2Label = { repo: 'repository method', service: '@Transactional service', runner: 'caller after the service returned' }[w];
      var stratLabel = { lazy: 'lazy mapping', batch: 'lazy + batch size 8', graph: '@EntityGraph', fetch: 'join fetch', dto: 'DTO projection' }[s];

      out.innerHTML =
        '<dl class="lm-kv">' +
        '<dt>Scenario</dt><dd>' + H.esc(n + ' parents, ' + stratLabel + ', touched in the ' + where2Label + ', open-in-view ' + (o === 'on' ? 'on' : 'off')) + '</dd>' +
        '<dt>Statements</dt><dd>' + statements + (statements === n + 1 ? ' (1 + N)' : statements === batched + 1 && s === 'batch' ? ' (1 + ⌈N / 8⌉)' : '') + '</dd>' +
        '</dl>' +
        '<div class="lm-callout lm-callout--' + kind + '" style="margin-top:.9rem">' +
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">' + ICON[kind] + '</svg>' +
        '<div><div class="lm-callout-title">' + title + '</div>' + body + '</div></div>';
    }

    where.addEventListener('change', render);
    osiv.addEventListener('change', render);
    strategy.addEventListener('change', render);
    nIn.addEventListener('input', render);
    render();
  });
})();
