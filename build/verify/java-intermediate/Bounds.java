import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Bounds {

    static class Product implements Comparable<Product> {
        final String sku; final int priceInCents;
        Product(String sku, int priceInCents) { this.sku = sku; this.priceInCents = priceInCents; }
        @Override public int compareTo(Product o) { return Integer.compare(priceInCents, o.priceInCents); }
        @Override public String toString() { return sku + "@" + priceInCents; }
    }
    interface Stockable { int onHand(); }

    // Perishable inherits compareTo(Product): it is Comparable<Product>, NOT Comparable<Perishable>
    static class Perishable extends Product implements Stockable {
        final int onHand;
        Perishable(String sku, int priceInCents, int onHand) { super(sku, priceInCents); this.onHand = onHand; }
        @Override public int onHand() { return onHand; }
    }

    // Bounded type parameter. Without `? super T`, max(dairy) below would not compile,
    // because Perishable is only Comparable<Product>, never Comparable<Perishable>.
    static <T extends Comparable<? super T>> T max(List<T> items) {
        T best = items.get(0);
        for (T t : items) if (t.compareTo(best) > 0) best = t;
        return best;
    }

    // Generic method: T is inferred from the arguments, the caller never writes <Product>
    static <T> List<T> pair(T a, T b) {
        List<T> out = new ArrayList<>();
        out.add(a); out.add(b);
        return out;
    }

    // Intersection bound: T must be a Product AND Stockable. The class comes first, then interfaces.
    // Inside, every T has both priceInCents and onHand() — no cast, no instanceof.
    static <T extends Product & Stockable> int stockValueInCents(List<T> items) {
        int total = 0;
        for (T t : items) total += t.priceInCents * t.onHand();
        return total;
    }

    public static void main(String[] args) {
        List<Perishable> dairy = List.of(new Perishable("MILK-1", 120, 40), new Perishable("EGGS-6", 240, 12), new Perishable("BUTR-2", 310, 5));
        List<Product> parts = List.of(new Product("BOLT-7", 80), new Product("GIRD-1", 120_000), new Product("NUT-3", 25));

        System.out.println("max(dairy)      = " + max(dairy));      // T = Perishable; bound met via Comparable<Product>
        System.out.println("max(parts)      = " + max(parts));
        System.out.println("stock value     = " + stockValueInCents(dairy));   // T = Perishable satisfies both bounds
        // stockValueInCents(parts);   // does not compile: Product is not Stockable

        List<Product> two = pair(new Product("A", 1), new Product("B", 2));   // T inferred as Product
        List<Object> mixed = pair("BOLT-7", 42);                              // T inferred as Object (see below)
        System.out.println("pair            = " + two + " / " + mixed);

        // A Comparator supplies ordering from outside; List.sort takes Comparator<? super E>,
        // so a Comparator<Product> sorts a List<Perishable>
        Comparator<Product> bySku = Comparator.comparing(p -> p.sku);
        List<Perishable> sorted = new ArrayList<>(dairy);
        sorted.sort(bySku);
        System.out.println("sorted by sku   = " + sorted);

        System.out.println("max(strings)    = " + max(List.of("BOLT", "NUT", "CAM")));  // String is Comparable<String>
    }
}