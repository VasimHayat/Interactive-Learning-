import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Optionals {

    record Product(String sku, int priceInCents, String supplierSku) { }   // supplierSku may be null

    static final Map<String, Product> CATALOGUE = Map.of(
        "BOLT-7", new Product("BOLT-7", 80, "ACME-0071"),
        "NUT-3",  new Product("NUT-3", 25, null),
        "FREE-1", new Product("FREE-1", 0, "ACME-0999"));

    // The return type says it: a lookup may find nothing.
    static Optional<Product> findBySku(String sku) {
        return Optional.ofNullable(CATALOGUE.get(sku));
    }

    static Optional<String> supplierOf(Product p) {
        return Optional.ofNullable(p.supplierSku());
    }

    static int fallbackPrice() {
        System.out.println("  (fallbackPrice() was called)");
        return 999;
    }

    public static void main(String[] args) {
        // map transforms the content when present; an empty Optional just flows through
        int bolt = findBySku("BOLT-7").map(Product::priceInCents).orElse(-1);
        int nope = findBySku("NOPE").map(Product::priceInCents).orElse(-1);
        System.out.println("BOLT-7 price = " + bolt + ", NOPE price = " + nope);

        // flatMap for a step that itself returns an Optional (no Optional<Optional<String>>)
        String s1 = findBySku("BOLT-7").flatMap(Optionals::supplierOf).orElse("no supplier");
        String s2 = findBySku("NUT-3").flatMap(Optionals::supplierOf).orElse("no supplier");
        System.out.println("suppliers    = " + s1 + " / " + s2);

        // filter turns a present value that fails the test into empty
        String priced = findBySku("FREE-1").filter(p -> p.priceInCents() > 0)
                                           .map(Product::sku).orElse("not priced");
        System.out.println("FREE-1       = " + priced);

        // orElse evaluates its argument ALWAYS; orElseGet only when empty
        System.out.println("orElse on a present value:");
        int a = findBySku("BOLT-7").map(Product::priceInCents).orElse(fallbackPrice());
        System.out.println("orElseGet on a present value:");
        int b = findBySku("BOLT-7").map(Product::priceInCents).orElseGet(Optionals::fallbackPrice);
        System.out.println("both gave " + a + " and " + b);

        // or() supplies an alternative Optional; orElseThrow names the failure
        Product p = findBySku("NOPE").or(() -> findBySku("NUT-3"))
                                     .orElseThrow(() -> new IllegalStateException("no product"));
        System.out.println("fallback     = " + p.sku());

        // ifPresentOrElse replaces the isPresent()/get() pair
        findBySku("CAM-9").ifPresentOrElse(
            x -> System.out.println("found " + x.sku()),
            () -> System.out.println("CAM-9 is not in the catalogue"));

        // Optional.stream(): keep only the hits, no isPresent() filter needed
        List<String> found = List.of("BOLT-7", "NOPE", "NUT-3").stream()
            .flatMap(sku -> findBySku(sku).stream())
            .map(Product::sku)
            .toList();
        System.out.println("found        = " + found);

        // of() refuses null; ofNullable() is the one that maps null to empty
        try {
            Optional.of(CATALOGUE.get("NOPE"));
        } catch (NullPointerException e) {
            System.out.println("Optional.of(null) threw NullPointerException");
        }
    }
}