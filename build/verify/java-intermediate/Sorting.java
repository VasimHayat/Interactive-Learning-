import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class Sorting {

    static final class Product implements Comparable<Product> {
        private final String sku;
        private final int priceInCents;
        Product(String sku, int priceInCents) { this.sku = sku; this.priceInCents = priceInCents; }
        String sku() { return sku; }
        int priceInCents() { return priceInCents; }
        @Override public int compareTo(Product o) { return sku.compareTo(o.sku); }   // natural order: by SKU
        @Override public String toString() { return sku + "@" + priceInCents; }
    }

    public static void main(String[] args) {
        List<Product> cart = new ArrayList<>(List.of(
                new Product("NUT-3", 25), new Product("BOLT-7", 80),
                new Product("CAM-9", 80), new Product("ROD-4", 300)));

        Collections.sort(cart);                                      // natural order via compareTo
        System.out.println("natural           " + cart);

        cart.sort(Comparator.comparingInt(Product::priceInCents));   // stable: BOLT-7 stays before CAM-9
        System.out.println("by price (stable) " + cart);

        cart.sort(Comparator.comparing(Product::priceInCents).thenComparing(Product::sku).reversed());
        System.out.println("(price,sku) rev   " + cart);

        cart.sort(Comparator.comparing(Product::priceInCents, Comparator.reverseOrder()).thenComparing(Product::sku));
        System.out.println("price desc, sku   " + cart);

        List<String> skus = new ArrayList<>(Arrays.asList("NUT-3", null, "BOLT-7"));
        skus.sort(Comparator.nullsFirst(Comparator.naturalOrder()));
        System.out.println("nulls first       " + skus);

        // a comparator inconsistent with equals: the set treats equal-priced products as duplicates
        Set<Product> byPrice = new TreeSet<>(Comparator.comparingInt(Product::priceInCents));
        byPrice.addAll(cart);
        System.out.println("TreeSet by price  " + byPrice + "  size=" + byPrice.size() + " of " + cart.size());

        int a = Integer.MAX_VALUE, b = -1;
        System.out.println("a - b             " + (a - b) + "  (overflow: wrong sign)");
        System.out.println("Integer.compare   " + Integer.compare(a, b));
    }
}