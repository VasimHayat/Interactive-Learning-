/* Java Intermediate §4 — expand a method reference into the lambda the
   compiler treats it as, and name its kind and a typical target interface. */
(function () {
  'use strict';
  var reg = globalThis.LMSandbox.reg, H = globalThis.LMHighlight;

  reg('method-ref', function (el) {
    var sel = el.querySelector('[data-sb-ref]');
    var out = el.querySelector('[data-sb-out]');

    var KINDS = {
      'static':    'Static method — <code>Type::staticMethod</code>. All lambda parameters become arguments.',
      'unbound':   'Unbound instance method — <code>Type::instanceMethod</code>. The FIRST lambda parameter becomes the receiver; the rest become arguments.',
      'bound':     'Bound instance method — <code>expression::instanceMethod</code>. The receiver is evaluated once, when the reference is created; all lambda parameters become arguments.',
      'ctor':      'Constructor — <code>Type::new</code>. All lambda parameters become constructor arguments. The overload is chosen from the target’s parameter list.',
      'arrayctor': 'Array constructor — <code>Type[]::new</code>. The single int parameter is the length.'
    };

    var LABEL = { 'static': 'static method', 'unbound': 'unbound instance method', 'bound': 'bound instance method', 'ctor': 'constructor', 'arrayctor': 'array constructor' };

    var CASES = [
      { ref: 'Product::sku', kind: 'unbound',
        lambda: '(Product p) -> p.sku()',
        target: 'Function<Product, String>',
        note: 'One parameter in, and it becomes the receiver. In a stream: <code>.map(Product::sku)</code>.' },
      { ref: 'String::compareToIgnoreCase', kind: 'unbound',
        lambda: '(String a, String b) -> a.compareToIgnoreCase(b)',
        target: 'Comparator<String>',
        note: 'Two parameters in: the first is the receiver, the second the argument. The arity looks wrong until you count the receiver.' },
      { ref: 'Integer::parseInt', kind: 'static',
        lambda: '(String s) -> Integer.parseInt(s)',
        target: 'ToIntFunction<String>  or  Function<String, Integer>',
        note: 'Which interface depends on the call site: <code>mapToInt(Integer::parseInt)</code> uses the primitive one and avoids boxing.' },
      { ref: 'Math::max', kind: 'static',
        lambda: '(int a, int b) -> Math.max(a, b)',
        target: 'IntBinaryOperator  or  BinaryOperator<Integer>',
        note: '<code>Math.max</code> is overloaded for int, long, float and double; the target type picks the overload. <code>reduce(0, Math::max)</code> on an IntStream chooses the int one.' },
      { ref: 'System.out::println', kind: 'bound',
        lambda: '(Object x) -> System.out.println(x)',
        target: 'Consumer<Object>  (or Consumer<String>, etc.)',
        note: '<code>System.out</code> is an expression, evaluated once when the reference is created. Swap the stream with <code>System.setOut</code> afterwards and this reference keeps the old one.' },
      { ref: 'this::isLowStock', kind: 'bound',
        lambda: '(Product p) -> this.isLowStock(p)',
        target: 'Predicate<Product>',
        note: 'Bound to the current instance. The reference keeps <code>this</code> alive for as long as the reference lives — the same leak risk as an inner class (§2).' },
      { ref: 'inventory::onHand', kind: 'bound',
        lambda: '(String sku) -> inventory.onHand(sku)',
        target: 'ToIntFunction<String>',
        note: '<code>inventory</code> is a local or field; it must be effectively final if local, and it is read once now, not each time the lambda runs.' },
      { ref: 'Product::new', kind: 'ctor',
        lambda: '(String sku, int price) -> new Product(sku, price)',
        target: 'BiFunction<String, Integer, Product>',
        note: 'Given a constructor <code>Product(String, int)</code>. With several constructors the target’s parameter list decides which; with none that fits, a compile error.' },
      { ref: 'ArrayList::new', kind: 'ctor',
        lambda: '() -> new ArrayList<>()',
        target: 'Supplier<List<Product>>',
        note: 'The zero-argument constructor, because <code>Supplier.get()</code> takes nothing. The type argument is inferred from the target, like the diamond.' },
      { ref: 'int[]::new', kind: 'arrayctor',
        lambda: '(int n) -> new int[n]',
        target: 'IntFunction<int[]>',
        note: 'Used by <code>stream.toArray(Product[]::new)</code> — the stream asks for an array of the right length and gets one of the right runtime type, which erased generics could not produce on their own.' }
    ];

    function render() {
      var c = CASES[Number(sel.value)] || CASES[0];
      out.innerHTML =
        '<dl class="lm-kv">' +
        '<dt>Reference</dt><dd><code>' + H.esc(c.ref) + '</code></dd>' +
        '<dt>Equivalent lambda</dt><dd><code>' + H.esc(c.lambda) + '</code></dd>' +
        '<dt>Typical target</dt><dd><code>' + H.esc(c.target) + '</code></dd>' +
        '</dl>' +
        '<div class="lm-callout lm-callout--note" style="margin:.9rem 0 0">' +
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 16v-5m0-3h.01"/></svg>' +
        '<div><div class="lm-callout-title">Kind: ' + H.esc(LABEL[c.kind]) + '</div>' +
        '<p style="margin:0 0 .4rem">' + KINDS[c.kind] + '</p>' +
        '<p style="margin:0;color:var(--lm-text-muted)">' + c.note + '</p></div></div>';
    }

    sel.addEventListener('change', render);
    render();
  });
})();
