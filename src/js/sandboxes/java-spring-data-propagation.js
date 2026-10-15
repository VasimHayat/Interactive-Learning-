/* Spring Data §5 — what a propagation level does to the outer and inner work.
   Rules from Spring's AbstractPlatformTransactionManager; NESTED is modelled as
   refused because JpaTransactionManager refused it in this stack (§5, experiment 10). */
(function () {
  'use strict';
  var reg = globalThis.LMSandbox.reg, H = globalThis.LMHighlight;

  reg('propagation', function (el) {
    var outerSel = el.querySelector('[data-sb-outer]');
    var propSel = el.querySelector('[data-sb-prop]');
    var throwsSel = el.querySelector('[data-sb-throws]');
    var catchSel = el.querySelector('[data-sb-catch]');
    var out = el.querySelector('[data-sb-out]');

    function callout(kind, title, body) {
      var icon = kind === 'good' ? '<path d="M20 6 9 17l-5-5"/>'
        : kind === 'danger' ? '<circle cx="12" cy="12" r="9"/><path d="m15 9-6 6m0-6 6 6"/>'
        : '<path d="M12 9v4m0 4h.01M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/>';
      return '<div class="lm-callout lm-callout--' + kind + '" style="margin:.9rem 0 0"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">' +
        icon + '</svg><div><div class="lm-callout-title">' + title + '</div>' + body + '</div></div>';
    }

    function render() {
      var hasOuter = outerSel.value === 'REQUIRED';
      var p = propSel.value, th = throwsSel.value, caught = catchSel.value === 'yes';
      var rollsBack = th === 'runtime';                 // default rule: unchecked rolls back, checked commits

      // where inner runs
      var where, refused = null;
      if (p === 'REQUIRED') where = hasOuter ? 'joined' : 'own';
      else if (p === 'REQUIRES_NEW') where = 'own';
      else if (p === 'SUPPORTS') where = hasOuter ? 'joined' : 'none';
      else if (p === 'MANDATORY') { if (hasOuter) where = 'joined'; else refused = 'IllegalTransactionStateException: No existing transaction found for transaction marked with propagation \'mandatory\''; }
      else if (p === 'NOT_SUPPORTED') where = 'none';
      else if (p === 'NEVER') { if (hasOuter) refused = 'IllegalTransactionStateException: Existing transaction found for transaction marked with propagation \'never\''; else where = 'none'; }
      else if (p === 'NESTED') { if (hasOuter) refused = 'NestedTransactionNotSupportedException (JpaTransactionManager has no savepoints here)'; else where = 'own'; }

      var code =
        (hasOuter ? '@Transactional\n' : '// no @Transactional\n') +
        'public void outer() {\n' +
        '    orders.save(order);                       // outer work\n' +
        (caught ? '    try {\n        other.inner();\n    } catch (Exception e) { /* swallowed */ }\n' : '    other.inner();\n') +
        '}\n\n' +
        '@Transactional(propagation = ' + p + ')\n' +
        'public void inner() {\n' +
        '    points.save(entry);                       // inner work\n' +
        (th === 'none' ? '' : '    throw new ' + (th === 'runtime' ? 'IllegalStateException' : 'Exception') + '("…");\n') +
        '}';

      var rows = [], kind = 'good', title, notes = [];
      var outerFate, innerFate, surfaced = 'nothing';

      if (refused) {
        innerFate = 'never ran';
        surfaced = caught ? 'nothing (outer caught it)' : refused.split(':')[0].split(' (')[0];
        if (hasOuter) {
          outerFate = caught ? 'committed (the refusal was caught; the exception is thrown before any transaction is touched)' : 'rolled back (the refusal is a RuntimeException passing through outer\'s proxy)';
        } else {
          outerFate = 'no transaction — orders.save ran in the repository\'s own transaction and is committed';
        }
        kind = 'danger'; title = 'inner() refused to run';
        notes.push('<code>' + H.esc(refused) + '</code>');
      } else {
        // inner's own fate
        if (where === 'own') innerFate = rollsBack ? 'rolled back (its own transaction)' : 'committed (its own transaction)';
        else if (where === 'none') innerFate = 'no transaction: points.save auto-committed in the repository\'s own transaction' + (th !== 'none' ? ', so the failure does not undo it' : '');
        else innerFate = 'part of outer\'s transaction — shares its fate';

        if (th !== 'none' && !caught) surfaced = th === 'runtime' ? 'IllegalStateException' : 'Exception';

        // outer's fate
        if (!hasOuter) {
          outerFate = 'no transaction — orders.save already committed in the repository\'s own transaction';
        } else if (where === 'joined' && rollsBack) {
          if (caught) {
            outerFate = 'rolled back at commit — inner marked the shared transaction rollback-only';
            surfaced = 'UnexpectedRollbackException (from outer\'s proxy at commit)';
            kind = 'danger'; title = 'The swallowed failure comes back';
            notes.push('inner() joined outer\'s transaction. Its proxy cannot roll back a transaction it does not own, so it marked it <strong>rollback-only</strong>. outer() caught the exception and returned normally; its proxy tried to commit, found the mark, rolled back and threw <code>UnexpectedRollbackException</code>.');
          } else {
            outerFate = 'rolled back — the exception passed through outer\'s proxy';
          }
        } else if (rollsBack && !caught) {
          outerFate = 'rolled back — the exception passed through outer\'s proxy too';
        } else if (th === 'checked' && !caught) {
          outerFate = 'committed — a checked exception passing through outer\'s proxy does not trigger rollback';
        } else {
          outerFate = 'committed';
        }
        if (where === 'joined') {
          innerFate = outerFate.indexOf('rolled back') === 0 ? 'rolled back with outer (shared transaction)' : 'committed with outer (shared transaction)';
        }

        if (!title) {
          if (where === 'own' && hasOuter) {
            title = 'Independent transactions';
            notes.push('Outer\'s transaction was <strong>suspended</strong> while inner ran in its own, then resumed. Each commits or rolls back on its own, so a later failure in outer() will not undo inner\'s committed work. Inner also needs a second connection from the pool while outer holds the first.');
          } else if (where === 'joined') {
            title = 'One shared transaction';
            notes.push('inner() joined outer\'s transaction: both pieces of work commit or roll back together.');
          } else if (where === 'none') {
            title = 'inner() ran without a transaction';
            notes.push(p === 'NOT_SUPPORTED' && hasOuter ? 'Outer\'s transaction was suspended for the duration of inner(). Lazy loading and dirty checking do not work in there.' : 'With no transaction, every repository call is its own short transaction (§5, experiment 1).');
          } else {
            title = 'inner() began its own transaction';
          }
          if (th === 'checked') notes.push('A checked exception does not trigger rollback under the default rule; add <code>rollbackFor = Exception.class</code> if it should.');
          kind = outerFate.indexOf('rolled back') === 0 || innerFate.indexOf('rolled back') === 0 ? 'gotcha' : 'good';
        }
      }

      rows.push(['outer work (orders.save)', outerFate]);
      rows.push(['inner work (points.save)', innerFate]);
      rows.push(['exception seen by outer()\'s caller', surfaced]);

      out.innerHTML =
        H.renderCode(code, { lang: 'java', noBar: true }) +
        '<dl class="lm-kv" style="margin-top:.9rem">' +
        rows.map(function (r) { return '<dt>' + H.esc(r[0]) + '</dt><dd>' + H.esc(r[1]) + '</dd>'; }).join('') +
        '</dl>' +
        callout(kind, title, notes.map(function (n) { return '<p style="margin:0 0 .4rem">' + n + '</p>'; }).join(''));
    }

    [outerSel, propSel, throwsSel, catchSel].forEach(function (c) { c.addEventListener('change', render); });
    render();
  });
})();
