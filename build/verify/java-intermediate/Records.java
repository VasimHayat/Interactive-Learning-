import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Records {

    record OrderLine(String sku, int qty, int unitPriceInCents) {
        // compact constructor: no parameter list; runs BEFORE the fields are assigned
        OrderLine {
            if (sku == null || sku.isBlank()) throw new IllegalArgumentException("sku required");
            if (qty <= 0) throw new IllegalArgumentException("qty must be positive: " + qty);
            sku = sku.strip().toUpperCase();          // reassigns the PARAMETER; the field is set from it after
        }
        int totalInCents() { return qty * unitPriceInCents; }
    }

    record Address(String city, String country) { }

    record Order(long id, Address shipTo, List<OrderLine> lines) {
        Order {
            lines = List.copyOf(lines);               // defensive, immutable copy
        }
        static Order of(long id, Address shipTo, OrderLine... lines) {   // static factory
            return new Order(id, shipTo, List.of(lines));
        }
        int totalInCents() { return lines.stream().mapToInt(OrderLine::totalInCents).sum(); }
    }

    record Bag(List<String> items) { }                // NO defensive copy: deliberately leaky

    public static void main(String[] args) {
        OrderLine a = new OrderLine("  bolt-7 ", 10, 80);
        OrderLine b = new OrderLine("BOLT-7", 10, 80);
        System.out.println("toString  = " + a);
        System.out.println("equals    = " + a.equals(b) + ", same hash = " + (a.hashCode() == b.hashCode()));
        System.out.println("accessor  = " + a.sku() + " x" + a.qty());

        try { new OrderLine("NUT-3", 0, 25); }
        catch (IllegalArgumentException e) { System.out.println("rejected  = " + e.getMessage()); }

        OrderLine nut = new OrderLine("NUT-3", 4, 25);
        List<OrderLine> lines = new ArrayList<>(List.of(a, nut));
        Order order = Order.of(1001L, new Address("Leeds", "UK"), a, nut);
        Order copied = new Order(1001L, new Address("Leeds", "UK"), lines);
        lines.add(new OrderLine("CAM-9", 1, 2_300));  // the caller mutates its own list afterwards
        System.out.println("totals    = " + order.totalInCents() + "c and " + copied.totalInCents() + "c (the copy did not change)");
        System.out.println("nested eq = " + order.equals(copied));

        Map<OrderLine, Integer> picked = new HashMap<>();
        picked.merge(a, 1, Integer::sum);
        picked.merge(b, 1, Integer::sum);               // an equal record is the same key
        System.out.println("map keys  = " + picked.size() + ", picks of BOLT-7 = " + picked.get(b));

        // shallow immutability: the reference cannot change, the list behind it can
        List<String> items = new ArrayList<>(List.of("x"));
        Bag bag = new Bag(items);
        items.add("y");
        System.out.println("leaky bag = " + bag.items());

        // a local record is a named, typed intermediate for a pipeline
        record SkuTotal(String sku, int total) { }
        List<SkuTotal> totals = order.lines().stream()
            .map(l -> new SkuTotal(l.sku(), l.totalInCents()))
            .toList();
        System.out.println("per line  = " + totals);
    }
}