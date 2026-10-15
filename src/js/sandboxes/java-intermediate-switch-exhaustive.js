/* Sandbox: exhaustiveness of a pattern switch over a sealed hierarchy.
   The verdicts are pre-authored from javac 21's behaviour; nothing is compiled. */
(function () {
  'use strict';
  var reg = globalThis.LMSandbox.reg, H = globalThis.LMHighlight;

  reg('switch-exhaustive', function (el) {
    var caseBoxes = Array.prototype.slice.call(el.querySelectorAll('[data-sb-case]'));
    var defBox = el.querySelector('[data-sb-default]');
    var newBox = el.querySelector('[data-sb-newtype]');
    var srcEl = el.querySelector('[data-sb-src]');
    var out = el.querySelector('[data-sb-out]');

    var BODY = {
      PercentOff: 'gross - gross * pct / 100',
      FixedOff: 'Math.max(0, gross - cents)',
      BuyNGetOne: 'gross - (qty / (n + 1)) * unit',
      Clearance: 'gross / 2'
    };
    var PATTERN = {
      PercentOff: 'PercentOff(int pct)',
      FixedOff: 'FixedOff(int cents)',
      BuyNGetOne: 'BuyNGetOne(int n)',
      Clearance: 'Clearance()'
    };

    function render() {
      var withNew = newBox.checked;
      var permitted = ['PercentOff', 'FixedOff', 'BuyNGetOne'].concat(withNew ? ['Clearance'] : []);
      var covered = caseBoxes.filter(function (b) { return b.checked; }).map(function (b) { return b.dataset.sbCase; });
      var hasDefault = defBox.checked;
      var missing = permitted.filter(function (t) { return covered.indexOf(t) < 0; });

      var code = 'sealed interface Discount permits ' + permitted.join(', ') + ' {}\n\n' +
                 'int price(Discount d) {\n' +
                 '    return switch (d) {\n';
      covered.forEach(function (t) {
        code += '        case ' + PATTERN[t] + ' -> ' + BODY[t] + ';\n';
      });
      if (hasDefault) code += '        default -> gross;\n';
      code += '    };\n}';
      srcEl.innerHTML = H.renderCode(code, { lang: 'java', noBar: true });

      var kind, title, body;
      if (!hasDefault && missing.length === 0) {
        kind = 'good'; title = 'Compiles: the switch is exhaustive';
        body = 'Every permitted subtype has a case, and <code>Discount</code> is sealed, so the compiler can prove no other value can arrive. ' +
               'Tick <em>add a Clearance subtype</em> to see what happens the day someone extends the hierarchy.';
      } else if (!hasDefault) {
        kind = 'danger'; title = 'Compile error';
        body = '<code>error: the switch expression does not cover all possible input values</code><br>' +
               'Missing: <strong>' + missing.map(H.esc).join(', ') + '</strong>. ' +
               (withNew && missing.indexOf('Clearance') > -1
                 ? 'This is the sealed hierarchy earning its keep: a new subtype was added and <em>every</em> switch that forgot it fails to build, pointing straight at the place that needs a pricing rule.'
                 : 'A switch <em>expression</em> must produce a value for every input, so the compiler refuses to guess.');
      } else if (missing.length === 0) {
        kind = 'gotcha'; title = 'Compiles, but the default is a trap';
        body = 'Today the <code>default</code> can never run: every case is covered. Tomorrow, when a subtype is added, it <em>will</em> run, silently, pricing the new discount with whatever placeholder you wrote. ' +
               'Drop the default and let the compiler do the checking instead.';
      } else {
        kind = 'gotcha'; title = 'Compiles, and that is the problem';
        body = '<strong>' + missing.map(H.esc).join(', ') + '</strong> ' + (missing.length === 1 ? 'falls' : 'fall') + ' into <code>default -> gross</code>, so ' +
               (missing.length === 1 ? 'that discount is' : 'those discounts are') + ' quietly ignored. No error, no warning. ' +
               (withNew && missing.indexOf('Clearance') > -1
                 ? 'Nobody told this switch about <code>Clearance</code>, and because of the default nobody ever will.'
                 : 'With a sealed type, a <code>default</code> only ever hides a missing case.');
      }
      var icon = kind === 'good' ? '<path d="M20 6 9 17l-5-5"/>'
               : kind === 'danger' ? '<circle cx="12" cy="12" r="9"/><path d="m15 9-6 6m0-6 6 6"/>'
               : '<path d="M12 9v4m0 4h.01M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/>';
      out.innerHTML =
        '<div class="lm-callout lm-callout--' + kind + '"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">' + icon + '</svg>' +
        '<div><div class="lm-callout-title">' + title + '</div>' + body + '</div></div>';
    }

    caseBoxes.forEach(function (b) { b.addEventListener('change', render); });
    defBox.addEventListener('change', render);
    newBox.addEventListener('change', render);
    render();
  });
})();
