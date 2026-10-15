/* JPA & Hibernate §9 — batch planner.
   Models how many JDBC round trips a bulk insert costs under a given id
   strategy, batch size and flush/clear interval, using the rules Hibernate
   applies: IDENTITY forces an immediate INSERT per row; SEQUENCE with the
   pooled optimiser needs one round trip per allocation block plus one to
   establish the first block; batching sends up to batch_size rows per
   statement execution; flush+clear bounds the persistence context. */
(function () {
  'use strict';
  var reg = globalThis.LMSandbox.reg, H = globalThis.LMHighlight;

  reg('batch-planner', function (el) {
    var rowsSel = el.querySelector('[data-sb-rows]');
    var idSel = el.querySelector('[data-sb-id]');
    var batchSel = el.querySelector('[data-sb-batch]');
    var flushSel = el.querySelector('[data-sb-flush]');
    var out = el.querySelector('[data-sb-out]');
    var ALLOC = 50;

    function fmt(n) { return n.toLocaleString('en-US'); }

    function render() {
      var rows = Number(rowsSel.value);
      var identity = idSel.value === 'identity';
      var batch = Number(batchSel.value);          // 0 = off
      var flushEvery = Number(flushSel.value);     // 0 = never
      var chunk = flushEvery || rows;

      var seqCalls = identity ? 0 : Math.ceil(rows / ALLOC) + 1;
      var insertTrips;
      if (identity || !batch) {
        insertTrips = rows;
      } else {
        insertTrips = 0;
        for (var done = 0; done < rows; done += chunk) {
          var n = Math.min(chunk, rows - done);
          insertTrips += Math.ceil(n / batch);
        }
      }
      var trips = seqCalls + insertTrips;
      var contextMax = Math.min(rows, chunk);

      var formula = identity
        ? rows + ' INSERTs (IDENTITY: one round trip each, nothing to batch)'
        : (batch
            ? seqCalls + ' sequence calls (' + rows + ' / ' + ALLOC + ' + 1) + ' + insertTrips + ' batched INSERT executions (' + rows + ' rows / ' + batch + ' per batch' + (flushEvery ? ', per ' + flushEvery + '-row chunk' : '') + ')'
            : seqCalls + ' sequence calls + ' + rows + ' INSERTs (batching off)');

      var kind, title, note;
      if (identity) {
        kind = 'gotcha'; title = 'IDENTITY defeats batching';
        note = 'The database assigns the id as a side effect of the INSERT, and Hibernate must know the id to manage the entity, so every <code>persist</code> executes immediately. <code>hibernate.jdbc.batch_size</code> is silently ignored for these inserts. Switch to <code>SEQUENCE</code> (or <code>UUID</code>) to batch.';
      } else if (!batch) {
        kind = 'note'; title = 'Ids are cheap; inserts are not';
        note = 'The sequence block of ' + ALLOC + ' already saves ' + fmt(rows - seqCalls) + ' round trips over fetching an id per row, but each INSERT is still its own trip. Set <code>hibernate.jdbc.batch_size</code>.';
      } else if (!flushEvery && rows >= 10000) {
        kind = 'gotcha'; title = 'Batched, but the persistence context holds ' + fmt(contextMax) + ' entities';
        note = 'Every managed entity stays in the first-level cache until commit, with a snapshot for dirty checking, so memory grows linearly and each flush rescans everything. Call <code>flush()</code> then <code>clear()</code> every batch_size rows, or use a <code>StatelessSession</code>.';
      } else {
        kind = 'good'; title = 'Batched and bounded';
        note = (flushEvery && flushEvery % batch !== 0
          ? 'Note the flush interval is not a multiple of the batch size, so the last batch of every chunk is short — align them (' + batch + ', ' + batch * 2 + ', …) to send full batches. '
          : '') +
          'Each flush sends full batches, the context never holds more than ' + fmt(contextMax) + ' entities, and the sequence optimiser keeps id fetches to ' + seqCalls + '.';
      }
      var icon = kind === 'good' ? '<path d="M20 6 9 17l-5-5"/>'
               : kind === 'gotcha' ? '<path d="M12 9v4m0 4h.01M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/>'
               : '<circle cx="12" cy="12" r="9"/><path d="M12 16v-5m0-3h.01"/>';

      var config = 'hibernate.jdbc.batch_size = ' + (batch || '(unset)') + '\n' +
                   'hibernate.order_inserts   = ' + (batch ? 'true' : 'false') + '\n' +
                   '@GeneratedValue(strategy = ' + (identity ? 'IDENTITY' : 'SEQUENCE') + ')' +
                   (flushEvery ? '\nif (i % ' + flushEvery + ' == 0) { em.flush(); em.clear(); }' : '');

      out.innerHTML =
        '<dl class="lm-kv">' +
        '<dt>Round trips</dt><dd><strong>' + fmt(trips) + '</strong> for ' + fmt(rows) + ' rows &nbsp;<span class="text-caption text-ink-subtle">' + H.esc(formula) + '</span></dd>' +
        '<dt>Statements prepared</dt><dd>' + fmt(trips) + ' (Hibernate reuses one prepared INSERT per batch execution)</dd>' +
        '<dt>Context high-water mark</dt><dd>' + fmt(contextMax) + ' managed entit' + (contextMax === 1 ? 'y' : 'ies') + (flushEvery ? ' (flush + clear every ' + flushEvery + ')' : ' (never cleared before commit)') + '</dd>' +
        '</dl>' +
        H.renderCode(config, { lang: 'plain', noBar: true }) +
        '<div class="lm-callout lm-callout--' + kind + '" style="margin-top:.9rem"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">' + icon + '</svg>' +
        '<div><div class="lm-callout-title">' + H.esc(title) + '</div>' + note + '</div></div>';
    }

    [rowsSel, idSel, batchSel, flushSel].forEach(function (c) { c.addEventListener('change', render); });
    render();
  });
})();
