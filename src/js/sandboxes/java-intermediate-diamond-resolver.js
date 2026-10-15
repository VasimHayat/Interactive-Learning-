/* Java Intermediate §3 — default-method resolution. Pure rule evaluation, no
   simulation of the JVM: the three rules from JLS §9.4.1 and §8.4.8. */
(function () {
  'use strict';
  var reg = globalThis.LMSandbox.reg, H = globalThis.LMHighlight;

  reg('diamond-resolver', function (el) {
    var base = el.querySelector('[data-sb-base]');
    var a = el.querySelector('[data-sb-a]');
    var b = el.querySelector('[data-sb-b]');
    var out = el.querySelector('[data-sb-out]');

    var ICON_OK = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><path d="M20 6 9 17l-5-5"/></svg>';
    var ICON_BAD = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="m15 9-6 6m0-6 6 6"/></svg>';

    function render() {
      var hasBase = base.value === 'yes';
      var hasA = a.value === 'default';
      var bMode = b.value;                      // none | default | sub
      var hasB = bMode !== 'none';

      var code =
        (hasBase ? 'class Base { public String describe() { return "Base"; } }\n' : 'class Base { }\n') +
        (hasA ? 'interface Priced { default String describe() { return "Priced"; } }\n' : 'interface Priced { }\n') +
        (bMode === 'sub'
          ? 'interface Shippable extends Priced { default String describe() { return "Shippable"; } }\n'
          : bMode === 'default'
            ? 'interface Shippable { default String describe() { return "Shippable"; } }\n'
            : 'interface Shippable { }\n') +
        'class Bolt extends Base implements Priced, Shippable { }   // no override';

      var ok, title, body, rule;
      if (hasBase) {
        ok = true; title = 'Runs Base.describe()';
        rule = 'Rule 1 — a class wins over any interface.';
        body = 'Base supplies a concrete body, so every interface default is ignored' + ((hasA || hasB) ? ', even though ' + (hasA && hasB ? 'both interfaces define one' : 'an interface defines one') + '.' : '.');
      } else if (bMode === 'sub') {
        ok = true; title = 'Runs Shippable.describe()';
        rule = 'Rule 2 — the more specific interface wins.';
        body = 'Shippable extends Priced, so its default overrides Priced’s' + (hasA ? '. The order in the implements clause does not matter.' : ' (and Priced has none to override anyway).');
      } else if (hasA && hasB) {
        ok = false; title = 'Compile error';
        rule = 'Rule 3 — unrelated defaults must be resolved by the class.';
        body = '<code>error: class Bolt inherits unrelated defaults for describe() from types Priced and Shippable</code><br>Fix: give Bolt an override, for example <code>@Override public String describe() { return Priced.super.describe(); }</code>.';
      } else if (hasA) {
        ok = true; title = 'Runs Priced.describe()';
        rule = 'Only one body is inherited, so there is nothing to resolve.';
        body = 'Shippable contributes no describe(), and Base is empty.';
      } else if (hasB) {
        ok = true; title = 'Runs Shippable.describe()';
        rule = 'Only one body is inherited, so there is nothing to resolve.';
        body = 'Priced contributes no describe(), and Base is empty.';
      } else {
        ok = false; title = 'Compile error';
        rule = 'Nobody defines describe().';
        body = '<code>error: cannot find symbol: method describe()</code> — there is no method to inherit from anywhere.';
      }

      out.innerHTML =
        H.renderCode(code, { lang: 'java', noBar: true }) +
        '<div class="lm-callout ' + (ok ? 'lm-callout--good' : 'lm-callout--danger') + '" style="margin-top:.9rem">' +
        (ok ? ICON_OK : ICON_BAD) +
        '<div><div class="lm-callout-title">' + H.esc(title) + '</div>' +
        '<p style="margin:0 0 .3rem"><strong>' + H.esc(rule) + '</strong></p>' +
        '<p style="margin:0">' + body + '</p></div></div>';
    }

    base.addEventListener('change', render);
    a.addEventListener('change', render);
    b.addEventListener('change', render);
    render();
  });
})();
