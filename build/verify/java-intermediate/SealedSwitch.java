import java.util.List;

public class SealedSwitch {

    record Product(String sku, int priceInCents) {}
    record Line(Product product, int qty) {}

    sealed interface Discount permits PercentOff, FixedOff, BuyNGetOne {}
    record PercentOff(int percent) implements Discount {
        PercentOff {
            if (percent < 0 || percent > 100) throw new IllegalArgumentException("percent out of range: " + percent);
        }
    }
    record FixedOff(int cents) implements Discount {}
    record BuyNGetOne(int n) implements Discount {}

    static int lineTotal(Line line, Discount discount) {
        int gross = line.product().priceInCents() * line.qty();
        return switch (discount) {                              // no default: the compiler knows every case
            case PercentOff(int pct)                     -> gross - gross * pct / 100;
            case FixedOff(int cents) when cents >= gross -> 0;  // guard: a voucher never goes negative
            case FixedOff(int cents)                     -> gross - cents;
            case BuyNGetOne(int n)                       -> gross - (line.qty() / (n + 1)) * line.product().priceInCents();
        };
    }

    static String describe(Line line) {
        // a nested record pattern takes the line AND the product inside it apart in one go
        if (line instanceof Line(Product(String sku, int price), int qty) && qty > 1) {
            return qty + " x " + sku + " @ " + price + "c";
        }
        return "single " + line.product().sku();
    }

    public static void main(String[] args) {
        Line bolts = new Line(new Product("BOLT-7", 80), 10);
        List<Discount> offers = List.of(new PercentOff(10), new FixedOff(100), new FixedOff(5_000), new BuyNGetOne(4));
        for (Discount d : offers) {
            System.out.printf("%-20s -> %4d%n", d, lineTotal(bolts, d));
        }
        System.out.println(describe(bolts));
        System.out.println(describe(new Line(new Product("GIRD-1", 120_000), 1)));
    }
}