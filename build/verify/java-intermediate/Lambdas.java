import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class Lambdas {

    record Product(String sku, int priceInCents, int onHand) {}

    static boolean isLowStock(Product p) { return p.onHand() < 5; }

    public static void main(String[] args) {
        List<Product> stock = List.of(
                new Product("BOLT-7", 80, 500),
                new Product("GIRD-1", 120_000, 2),
                new Product("CAM-9", 425, 3));

        // a lambda has no type of its own: each one below is typed by its target
        Predicate<Product> low = p -> p.onHand() < 5;
        Function<Product, String> sku = Product::sku;                 // unbound instance method
        Supplier<List<Product>> fresh = ArrayList::new;               // constructor
        BiFunction<Integer, Integer, Integer> max = Math::max;        // static method
        Product sample = stock.get(0);
        Supplier<String> boundSku = sample::sku;                      // bound instance method

        System.out.println("low stock:         " + stock.stream().filter(low).map(sku).toList());
        System.out.println("fresh list empty:  " + fresh.get().isEmpty());
        System.out.println("max:               " + max.apply(80, 4_250));
        System.out.println("bound receiver:    " + boundSku.get());

        // composition builds new functions from old ones without a class in sight
        Predicate<Product> cheap = p -> p.priceInCents() < 1_000;
        Predicate<Product> reorder = low.and(cheap.negate());
        System.out.println("low and not cheap: " + stock.stream().filter(reorder).map(Product::sku).toList());

        Function<Integer, Integer> addTax = cents -> cents + cents / 5;
        Function<Integer, Integer> addShipping = cents -> cents + 150;
        System.out.println("tax then shipping: " + addTax.andThen(addShipping).apply(1_000));
        System.out.println("shipping then tax: " + addTax.compose(addShipping).apply(1_000));

        // capture: the lambda copies the VALUE of threshold, so threshold must never change
        int threshold = 5;
        Predicate<Product> under = p -> p.onHand() < threshold;
        // threshold = 6;   // uncomment: "local variables referenced from a lambda expression must be final or effectively final"
        System.out.println("under threshold:   " + stock.stream().filter(under).count());

        Comparator<Product> byPrice = Comparator.comparingInt(Product::priceInCents);
        List<Product> sorted = new ArrayList<>(stock);
        sorted.sort(byPrice.reversed());
        System.out.println("priciest first:    " + sorted.stream().map(Product::sku).toList());
        System.out.println("own static helper: " + stock.stream().filter(Lambdas::isLowStock).count());

        UnaryOperator<String> shout = String::toUpperCase;
        System.out.println(shout.apply("ready"));
    }
}