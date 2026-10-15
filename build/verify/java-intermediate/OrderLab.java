import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class OrderLab {

    enum Category { FASTENER, STRUCTURAL, MECHANICAL }

    // -- step 1: records with validating compact constructors -----------------
    record Product(String sku, String name, Category category, long priceInCents) {
        Product {
            if (sku == null || sku.isBlank()) throw new IllegalArgumentException("sku required");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(category, "category");
            if (priceInCents < 0) throw new IllegalArgumentException("price must not be negative: " + priceInCents);
        }
    }

    record OrderLine(Product product, int qty) {
        OrderLine {
            Objects.requireNonNull(product, "product");
            if (qty <= 0) throw new IllegalArgumentException("qty must be positive: " + qty);
        }
        long subtotal() { return product.priceInCents() * qty; }
    }

    record Order(String id, LocalDate placed, List<OrderLine> lines) {
        Order {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(placed, "placed");
            lines = List.copyOf(lines);                       // immutable, null-hostile, ours
            if (lines.isEmpty()) throw new IllegalArgumentException("order " + id + " has no lines");
        }
        long subtotal() { return lines.stream().mapToLong(OrderLine::subtotal).sum(); }
    }

    // -- step 2: a sealed family consumed by an exhaustive switch -------------
    sealed interface Discount permits PercentOff, FixedOff, BuyNGetOne {}
    record PercentOff(int percent) implements Discount {
        PercentOff { if (percent < 0 || percent > 100) throw new IllegalArgumentException("percent out of range: " + percent); }
    }
    record FixedOff(long cents) implements Discount {}
    record BuyNGetOne(String sku, int n) implements Discount {}

    static long discountFor(Discount d, Order order) {
        return switch (d) {                                   // no default: the compiler checks coverage
            case PercentOff(int pct)           -> order.subtotal() * pct / 100;
            case FixedOff(long cents)          -> Math.min(cents, order.subtotal());
            case BuyNGetOne(String sku, int n) -> order.lines().stream()
                    .filter(l -> l.product().sku().equals(sku))
                    .mapToLong(l -> (long) (l.qty() / (n + 1)) * l.product().priceInCents())
                    .sum();
        };
    }

    // -- step 4: an Optional-returning lookup ---------------------------------
    static final class Catalogue {
        private final Map<String, Product> bySku = new HashMap<>();
        void add(Product p) { bySku.put(p.sku(), p); }
        Optional<Product> findBySku(String sku) { return Optional.ofNullable(bySku.get(sku)); }
    }

    public static void main(String[] args) {
        Product bolt   = new Product("BOLT-7", "M8 bolt",      Category.FASTENER,   80);
        Product nut    = new Product("NUT-3",  "M8 nut",       Category.FASTENER,   25);
        Product girder = new Product("GIRD-1", "Steel girder", Category.STRUCTURAL, 120_000);
        Product cam    = new Product("CAM-9",  "Cam follower", Category.MECHANICAL, 1_450);

        Catalogue catalogue = new Catalogue();
        for (Product p : List.of(bolt, nut, girder, cam)) catalogue.add(p);

        List<Order> orders = List.of(
            new Order("A-100", LocalDate.of(2026, 10, 1),  List.of(new OrderLine(bolt, 40), new OrderLine(nut, 40))),
            new Order("A-101", LocalDate.of(2026, 10, 9),  List.of(new OrderLine(girder, 1))),
            new Order("A-102", LocalDate.of(2026, 10, 12), List.of(new OrderLine(cam, 6), new OrderLine(bolt, 10))),
            new Order("A-103", LocalDate.of(2026, 10, 14), List.of(new OrderLine(cam, 2))));

        // step 2 in action
        Order a100 = orders.get(0);
        System.out.println("A-100 subtotal        = " + a100.subtotal());
        for (Discount d : List.of(new PercentOff(10), new FixedOff(5_000), new BuyNGetOne("BOLT-7", 3))) {
            System.out.printf("  %-27s -> %d off%n", d, discountFor(d, a100));
        }

        // -- step 3: the report -------------------------------------------------
        Map<Category, Long> byCategory = orders.stream()
                .flatMap(o -> o.lines().stream())
                .collect(Collectors.groupingBy(l -> l.product().category(), TreeMap::new,
                                               Collectors.summingLong(OrderLine::subtotal)));
        System.out.println("revenue by category   = " + byCategory);

        Map<String, Long> bySku = orders.stream()
                .flatMap(o -> o.lines().stream())
                .collect(Collectors.groupingBy(l -> l.product().sku(), Collectors.summingLong(OrderLine::subtotal)));
        List<String> top3 = bySku.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(3)
                .map(e -> e.getKey() + "=" + e.getValue())
                .toList();
        System.out.println("top 3 products        = " + top3);

        Map<Boolean, List<String>> bigOrders = orders.stream()
                .collect(Collectors.partitioningBy(o -> o.subtotal() > 10_000,
                                                   Collectors.mapping(Order::id, Collectors.toList())));
        System.out.println("over 10,000 cents     = " + bigOrders);

        // -- step 4: Optional without get() -------------------------------------
        long camPrice = catalogue.findBySku("CAM-9")
                .map(Product::priceInCents)
                .orElseThrow(() -> new IllegalStateException("CAM-9 not in catalogue"));
        String unknown = catalogue.findBySku("NOPE-0").map(Product::name).orElse("(unknown sku)");
        System.out.println("CAM-9 price           = " + camPrice + ", NOPE-0 -> " + unknown);

        // -- step 5: ranking with a composed Comparator -------------------------
        List<String> ranked = orders.stream()
                .sorted(Comparator.comparingLong(Order::subtotal).reversed().thenComparing(Order::placed))
                .map(o -> o.id() + "(" + o.subtotal() + ")")
                .toList();
        System.out.println("ranked by total       = " + ranked);

        // -- step 6: a time window against a fixed reference date ---------------
        LocalDate asOf = LocalDate.of(2026, 10, 15);
        LocalDate from = asOf.minusDays(7);
        List<String> recent = orders.stream()
                .filter(o -> !o.placed().isBefore(from))
                .map(Order::id)
                .toList();
        System.out.println("placed since " + from + " = " + recent);
    }
}