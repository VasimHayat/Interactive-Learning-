import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class Grouping {

    enum Category { FASTENER, STRUCTURAL, MOTION }

    record Product(String sku, Category category, int priceInCents, int onHand) {
        long stockValueInCents() { return (long) priceInCents * onHand; }
    }

    public static void main(String[] args) {
        List<Product> stock = List.of(
            new Product("BOLT-7", Category.FASTENER,        80,  400),
            new Product("NUT-3",  Category.FASTENER,        25, 1200),
            new Product("PIN-2",  Category.FASTENER,        15,    6),
            new Product("GIRD-1", Category.STRUCTURAL, 120_000,    3),
            new Product("ROD-4",  Category.STRUCTURAL,   4_500,   40),
            new Product("CAM-9",  Category.MOTION,       2_300,    2));

        // 1. groupingBy with a map factory and a downstream: stock value per category
        Map<Category, Long> valueByCategory = stock.stream()
            .collect(Collectors.groupingBy(Product::category, TreeMap::new,
                     Collectors.summingLong(Product::stockValueInCents)));
        System.out.println("value by category = " + valueByCategory);

        // 2. partitioningBy: always exactly two keys, even when one side is empty
        Map<Boolean, List<String>> lowStock = stock.stream()
            .collect(Collectors.partitioningBy(p -> p.onHand() < 10,
                     Collectors.mapping(Product::sku, Collectors.toList())));
        System.out.println("low stock         = " + lowStock.get(true));
        System.out.println("healthy           = " + lowStock.get(false));

        // 3. collectingAndThen unwraps the Optional that maxBy produces
        Map<Category, String> priciest = stock.stream()
            .collect(Collectors.groupingBy(Product::category, TreeMap::new,
                     Collectors.collectingAndThen(
                         Collectors.maxBy(Comparator.comparingInt(Product::priceInCents)),
                         opt -> opt.map(Product::sku).orElse("-"))));
        System.out.println("priciest per cat  = " + priciest);

        // 4. joining with a separator, prefix and suffix
        String skus = stock.stream().map(Product::sku).sorted()
            .collect(Collectors.joining(", ", "[", "]"));
        System.out.println("all skus          = " + skus);

        // 5. toMap with a merge function: three FASTENER prices collide on one key
        Map<Category, Integer> cheapest = stock.stream()
            .collect(Collectors.toMap(Product::category, Product::priceInCents,
                     Math::min, TreeMap::new));
        System.out.println("cheapest per cat  = " + cheapest);

        // 6. teeing: two collectors over one pass, merged into one result
        String summary = stock.stream()
            .collect(Collectors.teeing(
                Collectors.counting(),
                Collectors.summingLong(Product::stockValueInCents),
                (count, total) -> count + " products worth " + total + "c"));
        System.out.println("summary           = " + summary);

        // 7. toMap WITHOUT a merge function throws on the first duplicate key
        try {
            stock.stream().collect(Collectors.toMap(Product::category, Product::sku));
        } catch (IllegalStateException e) {
            System.out.println("no merge fn       = " + e.getMessage());
        }
    }
}