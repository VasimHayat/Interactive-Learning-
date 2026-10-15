import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

public class Enums {

    interface Describable { String describe(); }

    enum Category implements Describable {
        FASTENER("Fasteners", 1.0) {
            @Override int shippingInCents(int weightGrams) { return 150; }            // flat rate
        },
        STRUCTURAL("Structural steel", 2.5) {
            @Override int shippingInCents(int weightGrams) { return weightGrams * 3; }
        },
        TOOLING("Tooling", 1.2) {
            @Override int shippingInCents(int weightGrams) { return 500 + weightGrams; }
        };

        private final String label;
        private final double multiplier;

        Category(String label, double multiplier) {      // always private: nobody else can call new
            this.label = label;
            this.multiplier = multiplier;
        }

        abstract int shippingInCents(int weightGrams);   // every constant must supply a body

        String label() { return label; }
        double multiplier() { return multiplier; }

        @Override public String describe() { return label + " (x" + multiplier + ")"; }
    }

    public static void main(String[] args) {
        for (Category c : Category.values()) {
            System.out.printf("%-10s ordinal=%d  %-22s ship(200g)=%d%n",
                    c, c.ordinal(), c.describe(), c.shippingInCents(200));
        }

        Category parsed = Category.valueOf("TOOLING");
        System.out.println("valueOf -> " + parsed + ", same object: " + (parsed == Category.TOOLING));

        Map<Category, Integer> stockByCategory = new EnumMap<>(Category.class);
        stockByCategory.put(Category.TOOLING, 4);
        stockByCategory.put(Category.FASTENER, 1_200);
        System.out.println("EnumMap keeps declaration order: " + stockByCategory);

        EnumSet<Category> heavy = EnumSet.of(Category.STRUCTURAL, Category.TOOLING);
        System.out.println("complement of " + heavy + " is " + EnumSet.complementOf(heavy));

        String storage = switch (parsed) {               // exhaustive: no default needed
            case FASTENER   -> "bin";
            case STRUCTURAL -> "yard";
            case TOOLING    -> "locked cage";
        };
        System.out.println("switch -> " + storage);

        try {
            Category.valueOf("tooling");
        } catch (IllegalArgumentException e) {
            System.out.println("valueOf is exact-match only: " + e.getMessage());
        }
    }
}