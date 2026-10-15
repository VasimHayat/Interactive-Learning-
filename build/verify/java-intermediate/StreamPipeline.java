import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

public class StreamPipeline {

    enum Category { FASTENER, STRUCTURAL, TOOLING }
    record Product(String sku, Category category, int priceInCents, int onHand) {
        int stockValueInCents() { return priceInCents * onHand; }
    }
    record OrderLine(String sku, int qty) {}
    record Order(String id, List<OrderLine> lines) {}

    public static void main(String[] args) {
        List<Product> stock = List.of(
            new Product("BOLT-7", Category.FASTENER,   80,      500),
            new Product("NUT-3",  Category.FASTENER,   25,      2_000),
            new Product("GIRD-1", Category.STRUCTURAL, 120_000, 2),
            new Product("ROD-4",  Category.STRUCTURAL, 9_500,   12),
            new Product("CAM-9",  Category.TOOLING,    4_250,   3));

        // 1. filter -> sorted -> limit -> map -> collect: the three most valuable non-tooling lines
        List<String> report = stock.stream()
            .filter(p -> p.category() != Category.TOOLING)
            .sorted(Comparator.comparingInt(Product::stockValueInCents).reversed())
            .limit(3)
            .map(p -> p.sku() + "=" + p.stockValueInCents() / 100 + ".00")
            .toList();
        System.out.println("top value:     " + report);

        // 2. primitive streams: no boxing, statistics built in
        int totalUnits = stock.stream().mapToInt(Product::onHand).sum();
        double avgPrice = stock.stream().mapToInt(Product::priceInCents).average().orElse(0);
        System.out.printf("units=%d avgPrice=%.1f%n", totalUnits, avgPrice);

        // 3. flatMap: an order has many lines; flatten them before counting
        List<Order> orders = List.of(
            new Order("A1", List.of(new OrderLine("BOLT-7", 40), new OrderLine("NUT-3", 100))),
            new Order("A2", List.of(new OrderLine("BOLT-7", 10))),
            new Order("A3", List.of()));
        int boltsOrdered = orders.stream()
            .flatMap(o -> o.lines().stream())
            .filter(l -> l.sku().equals("BOLT-7"))
            .mapToInt(OrderLine::qty)
            .sum();
        System.out.println("bolts ordered: " + boltsOrdered);

        // 4. reduce: the identity must be neutral for the operation (0 is right for max of lengths, wrong for min)
        int longestSku = stock.stream().map(Product::sku).mapToInt(String::length).reduce(0, Math::max);
        System.out.println("longest sku:   " + longestSku);

        // 5. anyMatch short-circuits; distinct is stateful (it remembers what it has seen)
        boolean anyEmpty = orders.stream().anyMatch(o -> o.lines().isEmpty());
        long categories = stock.stream().map(Product::category).distinct().count();
        System.out.println("empty order:   " + anyEmpty + ", distinct categories: " + categories);

        // 6. a range as a source
        String bins = IntStream.rangeClosed(1, 4).mapToObj(i -> "BIN-" + i).reduce((a, b) -> a + "," + b).orElse("");
        System.out.println("bins:          " + bins);

        // 7. a stream is single-use
        var once = stock.stream();
        once.count();
        try { once.count(); }
        catch (IllegalStateException e) { System.out.println("second use:    " + e.getMessage()); }
    }
}