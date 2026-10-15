import java.util.ArrayList;
import java.util.List;

public class Pecs {

    static class Product {
        final String sku; final int priceInCents;
        Product(String sku, int priceInCents) { this.sku = sku; this.priceInCents = priceInCents; }
        @Override public String toString() { return sku; }
    }
    static class Perishable extends Product {
        final int shelfDays;
        Perishable(String sku, int priceInCents, int shelfDays) { super(sku, priceInCents); this.shelfDays = shelfDays; }
    }

    // PRODUCER: we only READ Products out of `src`, so accept any List of a subtype
    static int totalInCents(List<? extends Product> src) {
        int sum = 0;
        for (Product p : src) sum += p.priceInCents;   // reading as Product is always safe
        return sum;
    }

    // CONSUMER: we only WRITE Perishables into `dst`, so accept any List of a supertype
    static void restockPerishables(List<? super Perishable> dst) {
        dst.add(new Perishable("MILK-1", 120, 7));      // writing a Perishable is always safe
        dst.add(new Perishable("EGGS-6", 240, 21));
    }

    // BOTH: reads and writes, so the type must be exact — no wildcard
    static void swapFirstTwo(List<Product> list) {
        Product first = list.get(0);
        list.set(0, list.get(1));
        list.set(1, first);
    }

    public static void main(String[] args) {
        List<Perishable> dairy = new ArrayList<>();
        restockPerishables(dairy);                      // List<Perishable> is a List<? super Perishable>
        System.out.println("dairy         = " + dairy);

        List<Product> catalogue = new ArrayList<>();
        catalogue.add(new Product("BOLT-7", 80));
        catalogue.add(new Product("GIRD-1", 120_000));
        restockPerishables(catalogue);                  // List<Product> is ALSO a List<? super Perishable>
        System.out.println("catalogue     = " + catalogue);

        System.out.println("total(dairy)  = " + totalInCents(dairy));       // List<Perishable> as a producer
        System.out.println("total(catal.) = " + totalInCents(catalogue));   // List<Product> as a producer

        List<Object> anything = new ArrayList<>();
        restockPerishables(anything);                   // even List<Object> is a List<? super Perishable>
        System.out.println("anything      = " + anything);

        swapFirstTwo(catalogue);
        System.out.println("swapped       = " + catalogue);

        // totalInCents(anything);                       // does not compile: Object is not a Product
        // restockPerishables(new ArrayList<String>());  // does not compile: String is not a supertype
    }
}