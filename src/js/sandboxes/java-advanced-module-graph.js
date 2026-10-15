/* Java Advanced §12 — readability vs accessibility vs openness on a two-module
   graph. Pure rule evaluation from JLS §7.7 and java.lang.Module; nothing is
   compiled. */
(function () {
  'use strict';
  var reg = globalThis.LMSandbox.reg, H = globalThis.LMHighlight;

  reg('module-graph', function (el) {
    var requires = el.querySelector('[data-sb-requires]');
    var exports = el.querySelector('[data-sb-exports]');
    var transitive = el.querySelector('[data-sb-transitive]');
    var opens = el.querySelector('[data-sb-opens]');
    var uses = Array.prototype.slice.call(el.querySelectorAll('[data-sb-use]'));
    var srcEl = el.querySelector('[data-sb-src]');
    var out = el.querySelector('[data-sb-out]');

    var ICON = {
      good: '<path d="M20 6 9 17l-5-5"/>',
      danger: '<circle cx="12" cy="12" r="9"/><path d="m15 9-6 6m0-6 6 6"/>',
      gotcha: '<path d="M12 9v4m0 4h.01M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/>'
    };

    function render() {
      var req = requires.checked, exp = exports.checked, tr = transitive.checked, op = opens.checked;
      var use = 'call';
      uses.forEach(function (r) { if (r.checked) use = r.value; });

      var api = 'module com.acme.inventory.api {\n' +
                '    requires ' + (tr ? 'transitive ' : '') + 'json;\n' +
                (exp ? '    exports com.acme.inventory.api;\n' : '    // (nothing exported)\n') +
                (op ? '    opens com.acme.inventory.api to com.acme.inventory.app;\n' : '') +
                '}';
      var app = 'module com.acme.inventory.app {\n' +
                (req ? '    requires com.acme.inventory.api;\n' : '    // (does not require api)\n') +
                '}';
      srcEl.innerHTML = H.renderCode(api + '\n\n' + app, { lang: 'java', noBar: true });

      var kind, title, body;
      if (!req) {
        kind = 'danger'; title = 'Compile error: app does not read api';
        body = '<code>error: package com.acme.inventory.api is not visible<br>&nbsp;&nbsp;(package com.acme.inventory.api is declared in module com.acme.inventory.api, but module com.acme.inventory.app does not read it)</code><br>' +
               '<strong>Readability</strong> is the first gate. Nothing in <code>api</code> is reachable until <code>app</code> declares <code>requires com.acme.inventory.api;</code>.';
      } else if (use === 'internal') {
        kind = 'danger'; title = 'Compile error: the internal package is not exported';
        body = '<code>error: package com.acme.inventory.api.internal is not visible<br>&nbsp;&nbsp;(package com.acme.inventory.api.internal is declared in module com.acme.inventory.api, which does not export it)</code><br>' +
               'Readability passed, <strong>accessibility</strong> failed: <code>app</code> reads <code>api</code>, but only exported packages are accessible, and <code>internal</code> is deliberately not one of them. This is strong encapsulation doing its job; the fix is to ask the API for what you need, not to export the package.';
      } else if (!exp) {
        kind = 'danger'; title = 'Compile error: nothing is exported';
        body = '<code>error: package com.acme.inventory.api is not visible<br>&nbsp;&nbsp;(package com.acme.inventory.api is declared in module com.acme.inventory.api, which does not export it)</code><br>' +
               '<code>app</code> reads <code>api</code>, but a module that exports nothing has no accessible types at all. Every public class in it is public only to itself.';
      } else if (use === 'reflect') {
        if (op) {
          kind = 'good'; title = 'Works: the package is opened to app';
          body = '<code>setAccessible(true)</code> succeeds because <code>api</code> <em>opens</em> the package to <code>app</code>. Being exported was not enough: exports govern compile-time and ordinary run-time access to <em>public</em> members; opens govern deep reflection into everything else. A qualified <code>opens … to</code> keeps the hole as small as possible.';
        } else {
          kind = 'danger'; title = 'Runtime failure: InaccessibleObjectException';
          body = '<code>java.lang.reflect.InaccessibleObjectException: Unable to make field private final java.lang.String com.acme.inventory.api.Product.sku accessible: module com.acme.inventory.api does not "opens com.acme.inventory.api" to module com.acme.inventory.app</code><br>' +
                 'Compiles fine, fails the first time the reflective call runs. Fix it in <code>api</code> with <code>opens com.acme.inventory.api to com.acme.inventory.app;</code>, or, for code you do not own, on the command line: <code>--add-opens com.acme.inventory.api/com.acme.inventory.api=com.acme.inventory.app</code>.';
        }
      } else {
        // ordinary call; depends on whether JsonNode is readable
        if (tr) {
          kind = 'good'; title = 'Compiles and runs';
          body = '<code>app</code> reads <code>api</code>, the package is exported, and because <code>api</code> says <code>requires transitive json</code>, <code>app</code> also reads <code>json</code>, so the <code>JsonNode</code> that <code>toJson()</code> returns is a type <code>app</code> may name and call methods on.';
        } else {
          kind = 'gotcha'; title = 'new Product(…) compiles; product.toJson().size() does not';
          body = '<code>error: cannot access JsonNode<br>&nbsp;&nbsp;class file for com.acme.json.JsonNode not found</code> — or, with json on the module path, <code>package com.acme.json is not visible … module com.acme.inventory.app does not read it</code>.<br>' +
                 '<code>api</code> reads <code>json</code>, but readability is not inherited: <code>app</code> does not. Any type that appears in <code>api</code>\'s public signatures must come from a module <code>api</code> requires <em>transitively</em>. Tick <em>requires transitive json</em> to fix it at the source instead of making every consumer add <code>requires json</code> themselves.';
        }
      }

      out.innerHTML =
        '<div class="lm-callout lm-callout--' + kind + '"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">' + ICON[kind] + '</svg>' +
        '<div><div class="lm-callout-title">' + H.esc(title) + '</div>' + body + '</div></div>';
    }

    [requires, exports, transitive, opens].forEach(function (c) { c.addEventListener('change', render); });
    uses.forEach(function (r) { r.addEventListener('change', render); });
    render();
  });
})();
