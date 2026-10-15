/* Spring Data §7 — what page N costs under offset paging (Page / Slice) versus
   keyset scrolling (Window). Rows scanned are the database's work with a
   covering index on the sort key; the formulas are the ones every engine
   follows for offset/fetch and for a seek on a unique key. */
(function () {
  'use strict';
  var reg = globalThis.LMSandbox.reg, H = globalThis.LMHighlight;

  function fmt(n) { return Math.round(n).toLocaleString('en-US'); }

  reg('pagination-cost', function (el) {
    var rowsIn = el.querySelector('[data-sb-rows]');
    var rowsOut = el.querySelector('[data-sb-rowsout]');
    var sizeSel = el.querySelector('[data-sb-size]');
    var pageIn = el.querySelector('[data-sb-page]');
    var pageOut = el.querySelector('[data-sb-pageout]');
    var strat = el.querySelector('[data-sb-strategy]');
    var out = el.querySelector('[data-sb-out]');

    function render() {
      var rows = Math.round(Math.pow(10, Number(rowsIn.value)));
      var size = Number(sizeSel.value);
      var pages = Math.max(1, Math.ceil(rows / size));
      pageIn.max = String(Math.max(0, pages - 1));
      var page = Math.min(Number(pageIn.value), pages - 1);
      pageIn.value = String(page);
      rowsOut.textContent = fmt(rows);
      pageOut.textContent = page;

      var s = strat.value;
      var skipped = page * size;
      var scanned, statements, countRows, sql, kind, title, note;

      if (s === 'keyset') {
        scanned = Math.min(size, rows - skipped);
        statements = 1; countRows = 0;
        sql = 'select … from product p\nwhere p.sku > ? or (p.sku = ? and p.id > ?)\norder by p.sku, p.id\nfetch first ' + size + ' rows only';
        kind = 'good'; title = 'Seek, then read ' + fmt(scanned) + ' rows';
        note = 'The position carries the last row’s keys. The index seeks straight to them, so window ' + (page + 1) + ' reads the same ' + fmt(scanned) + ' rows as window 1 and an insert above the cursor cannot shift it. Cost: constant in the page number. The trade: no "jump to page 37" and no total.';
      } else {
        scanned = Math.min(skipped + size + (s === 'slice' ? 1 : 0), rows);
        statements = s === 'page' ? 2 : 1;
        countRows = s === 'page' ? rows : 0;
        sql = 'select … from product p\norder by p.sku\noffset ' + fmt(skipped) + ' rows fetch first ' + (size + (s === 'slice' ? 1 : 0)) + ' rows only' +
              (s === 'page' ? '\n\nselect count(p.id) from product p' : '');
        var deep = skipped > 10000;
        kind = deep ? 'danger' : (skipped > 1000 ? 'gotcha' : 'note');
        title = 'Skip ' + fmt(skipped) + ', read ' + size + (s === 'slice' ? ' + 1' : '') + (s === 'page' ? ', then count ' + fmt(rows) : '');
        note = (skipped ? 'The database walks ' + fmt(skipped) + ' index entries it then discards before returning the page; cost grows linearly with the page number. ' : 'The first page is cheap: nothing to skip. ') +
               (s === 'page' ? 'The count query touches every row that matches the filter — on a plain table a full index scan of ' + fmt(rows) + ' entries, on every request. ' : 'No count: one extra row answers hasNext. ') +
               (deep ? 'At this depth an offset page is a scan, not a lookup; switch to keyset scrolling.' : skipped > 1000 ? 'Still acceptable; watch it on the real table size.' : '');
      }

      out.innerHTML =
        H.renderCode(sql, { lang: 'sql', noBar: true }) +
        '<dl class="lm-kv" style="margin-top:.9rem">' +
        '<dt>Statements</dt><dd>' + statements + '</dd>' +
        '<dt>Rows the page query touches</dt><dd>' + fmt(scanned) + '</dd>' +
        (countRows ? '<dt>Rows the count touches</dt><dd>' + fmt(countRows) + '</dd>' : '') +
        '<dt>Metadata you get</dt><dd>' + (s === 'page' ? 'total elements, total pages, hasNext' : s === 'slice' ? 'hasNext only' : 'hasNext and a position for the next window') + '</dd>' +
        '</dl>' +
        '<div class="lm-callout lm-callout--' + kind + '" style="margin-top:.9rem">' +
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">' +
        (kind === 'good' ? '<path d="M20 6 9 17l-5-5"/>' : kind === 'danger' ? '<circle cx="12" cy="12" r="9"/><path d="m15 9-6 6m0-6 6 6"/>' : kind === 'gotcha' ? '<path d="M12 9v4m0 4h.01M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/>' : '<circle cx="12" cy="12" r="9"/><path d="M12 16v-5m0-3h.01"/>') +
        '</svg><div><div class="lm-callout-title">' + H.esc(title) + '</div>' + note + '</div></div>';
    }

    rowsIn.addEventListener('input', render);
    sizeSel.addEventListener('change', render);
    pageIn.addEventListener('input', render);
    strat.addEventListener('change', render);
    render();
  });
})();
