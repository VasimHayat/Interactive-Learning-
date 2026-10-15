import java.util.ArrayList;
import java.util.List;

public class Nested {

    // 1. STATIC NESTED: a class placed inside another for namespacing. No outer instance.
    static class Line {
        final String sku; final int qty;
        Line(String sku, int qty) { this.sku = sku; this.qty = qty; }
        @Override public String toString() { return sku + "x" + qty; }
    }

    private final List<Line> lines = new ArrayList<>();
    private final String orderId;

    Nested(String orderId) { this.orderId = orderId; }

    // 2. INNER: every instance carries a hidden reference to the enclosing Nested (Nested.this)
    class Cursor {
        private int pos = 0;                                       // the cursor's own state
        boolean hasNext() { return pos < lines.size(); }           // `lines` means Nested.this.lines
        Line next() { return lines.get(pos++); }
        String owner() { return Nested.this.orderId; }             // the qualified form, written out
    }

    Cursor cursor() { return new Cursor(); }                       // `this` becomes the new Cursor's outer

    int totalQty() {
        int base = 100;                                            // a local that a local class will capture

        // 3. LOCAL: declared inside a method; sees the method's effectively-final locals
        class Counter {
            int sum = base;                                        // `base` is COPIED into the Counter at construction
            void add(Line l) { sum += l.qty; }
        }
        Counter c = new Counter();
        for (Line l : lines) c.add(l);
        // base = 0;   // uncomment: "local variables referenced from an inner class must be final or effectively final"
        return c.sum - base;
    }

    public static void main(String[] args) {
        Nested order = new Nested("ORD-1");
        order.lines.add(new Line("BOLT-7", 3));
        order.lines.add(new Line("NUT-3", 10));

        Line standalone = new Nested.Line("CAM-9", 1);             // static nested: no outer instance needed
        System.out.println("static nested = " + standalone);

        Nested.Cursor cur = order.cursor();                        // inner: created THROUGH an instance
        Nested.Cursor other = order.new Cursor();                  // the explicit (and rarely seen) form
        System.out.println("inner owner   = " + cur.owner() + " / " + other.owner());
        StringBuilder walked = new StringBuilder();
        while (cur.hasNext()) walked.append(cur.next()).append(' ');
        System.out.println("walked        = " + walked.toString().trim());

        System.out.println("local class   = " + order.totalQty());

        // 4. ANONYMOUS: a one-off implementation declared at the point of use
        Runnable report = new Runnable() {
            @Override public void run() {
                // `this` here is the anonymous Runnable itself, never the enclosing class
                System.out.println("anonymous     = this is a " + this.getClass().getSuperclass().getSimpleName()
                        + ", simple name '" + this.getClass().getSimpleName() + "'");
            }
        };
        report.run();

        // A lambda has no `this` of its own; it captures the enclosing instance's, if there is one
        Runnable lambda = () -> System.out.println("lambda        = " + order.lines.size() + " lines captured");
        lambda.run();
    }
}