/* Spring Data §12 — sizing a HikariCP pool.
   Two numbers have to agree: what the database can usefully run at once
   (HikariCP's rule of thumb: cores × 2 + effective spindles, for the WHOLE
   database) and what the traffic needs (Little's law: arrivals × time held).
   Every application instance has its own pool, so the database sees the sum. */
(function () {
  'use strict';
  var reg = globalThis.LMSandbox.reg, H = globalThis.LMHighlight;

  reg('hikari-sizer', function (el) {
    var cores = el.querySelector('[data-sb-cores]');
    var coresOut = el.querySelector('[data-sb-coresout]');
    var storage = el.querySelector('[data-sb-storage]');
    var inst = el.querySelector('[data-sb-instances]');
    var instOut = el.querySelector('[data-sb-instancesout]');
    var rps = el.querySelector('[data-sb-rps]');
    var rpsOut = el.querySelector('[data-sb-rpsout]');
    var qms = el.querySelector('[data-sb-qms]');
    var qmsOut = el.querySelector('[data-sb-qmsout]');
    var maxConn = el.querySelector('[data-sb-maxconn]');
    var out = el.querySelector('[data-sb-out]');

    var SPINDLES = { ssd: 1, hdd2: 2, hdd4: 4 };
    var ICON_OK = '<path d="M20 6 9 17l-5-5"/>';
    var ICON_WARN = '<path d="M12 9v4m0 4h.01M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/>';
    var ICON_BAD = '<circle cx="12" cy="12" r="9"/><path d="m15 9-6 6m0-6 6 6"/>';

    function callout(kind, icon, title, body) {
      return '<div class="lm-callout lm-callout--' + kind + '" style="margin:.9rem 0 0">' +
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">' + icon + '</svg>' +
        '<div><div class="lm-callout-title">' + title + '</div>' + body + '</div></div>';
    }

    function render() {
      var c = Number(cores.value), n = Number(inst.value), r = Number(rps.value), ms = Number(qms.value);
      var limit = Number(maxConn.value);
      coresOut.textContent = c; instOut.textContent = n; rpsOut.textContent = r; qmsOut.textContent = ms + ' ms';

      var spindles = SPINDLES[storage.value];
      var budget = c * 2 + spindles;                       // the whole database, all instances together
      var busy = r * ms / 1000;                            // Little's law: connections in use on average
      var needed = Math.ceil(busy * 2);                    // ×2 headroom for bursts
      var target = Math.max(needed, Math.min(budget, needed * 2 || 2));
      if (target > budget) target = budget;                // never ask the DB for more than it can run
      var perInstance = Math.max(2, Math.ceil(target / n));
      var total = perInstance * n;

      var props =
        '# per instance — ' + n + ' instance' + (n === 1 ? '' : 's') + ' × ' + perInstance + ' = ' + total + ' connections at the database\n' +
        'spring.datasource.hikari.maximum-pool-size=' + perInstance + '\n' +
        'spring.datasource.hikari.minimum-idle=' + perInstance + '        # a fixed-size pool: no ramp-up under load\n' +
        'spring.datasource.hikari.connection-timeout=3000   # fail in 3 s instead of queueing for 30\n' +
        'spring.datasource.hikari.max-lifetime=1740000      # 29 min, below the usual 30 min server/proxy cut-off\n' +
        '# spring.datasource.hikari.leak-detection-threshold=20000   # only while hunting a leak';

      var rows =
        '<dl class="lm-kv">' +
        '<dt>Database budget</dt><dd><code>' + c + ' cores × 2 + ' + spindles + ' spindle' + (spindles === 1 ? '' : 's') + ' = ' + budget + '</code> connections that can do useful work at once, across every instance</dd>' +
        '<dt>Traffic needs</dt><dd><code>' + r + ' req/s × ' + ms + ' ms = ' + busy.toFixed(1) + '</code> connections busy on average; ×2 for bursts = <strong>' + needed + '</strong></dd>' +
        '<dt>Per instance</dt><dd><strong>' + perInstance + '</strong> (total ' + total + ' across ' + n + ')</dd>' +
        '<dt>Database limit</dt><dd><code>max_connections = ' + limit + '</code>; this service would hold ' + Math.round(total / limit * 100) + '%</dd>' +
        '</dl>';

      var verdict;
      if (needed > budget) {
        verdict = callout('danger', ICON_BAD, 'The database, not the pool, is the bottleneck',
          '<p class="mb-0">The traffic needs about ' + needed + ' connections running at once, but ' + budget +
          ' is roughly all the parallel work this database can do. Raising the pool past that makes every query slower — more context switching, more lock contention — so throughput drops while latency climbs. Cut the time per request (fewer statements, the N+1 fixes in §6, indexes, DTO projections), cache reads, or move reads to a replica.</p>');
      } else if (total > limit * 0.8) {
        verdict = callout('gotcha', ICON_WARN, 'Too close to max_connections',
          '<p class="mb-0">' + total + ' connections from this service alone leaves ' + (limit - total) +
          ' for migrations, admin sessions, other services and a rolling deploy, which briefly runs old and new instances side by side — doubling the count. Shrink each pool or put a pooler such as PgBouncer in front.</p>');
      } else if (perInstance * n > budget * 1.5) {
        verdict = callout('gotcha', ICON_WARN, 'More connections than the database can use',
          '<p class="mb-0">The 2-connection floor per instance adds up to ' + total + ', above the ' + budget +
          ' the database can run at once. With this many instances, the pools mostly sit idle — fine, but do not raise them further.</p>');
      } else {
        verdict = callout('good', ICON_OK, 'A small, fixed pool',
          '<p class="mb-0">Surprisingly small is normal. A connection only does work while a query runs; the rest of a request (JSON, business logic, network) does not need one, which is why ' + perInstance +
          ' per instance serves ' + r + ' requests a second here. Hikari\'s default of 10 is already more than most services need.</p>');
      }

      out.innerHTML = rows + H.renderCode(props, { lang: 'bash', noBar: true }) + verdict;
    }

    [cores, inst, rps, qms].forEach(function (i) { i.addEventListener('input', render); });
    [storage, maxConn].forEach(function (s) { s.addEventListener('change', render); });
    render();
  });
})();
