import java.util.HashSet;
import java.util.Set;

public class EqualsContract {

    static final class BadPart {
        private static int counter = 0;
        private final String sku;
        private final int id = ++counter;

        BadPart(String sku) { this.sku = sku; }

        @Override public int hashCode() { return id; }   // NOT derived from sku
        @Override public boolean equals(Object o) {
            return o instanceof BadPart other && sku.equals(other.sku);
        }
    }

    static final class GoodPart {
        private final String sku;

        GoodPart(String sku) { this.sku = sku; }

        @Override public int hashCode() { return sku.hashCode(); }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof GoodPart other)) return false;
            return sku.equals(other.sku);
        }
        @Override public String toString() { return "GoodPart[sku=" + sku + "]"; }
    }

    public static void main(String[] args) {
        Set<BadPart> bad = new HashSet<>();
        bad.add(new BadPart("BOLT-7"));
        bad.add(new BadPart("BOLT-7"));
        System.out.println("bad  equals    -> " + new BadPart("X").equals(new BadPart("X")));
        System.out.println("bad  set size  -> " + bad.size());
        System.out.println("bad  contains  -> " + bad.contains(new BadPart("BOLT-7")));

        Set<GoodPart> good = new HashSet<>();
        good.add(new GoodPart("BOLT-7"));
        good.add(new GoodPart("BOLT-7"));
        System.out.println("good equals    -> " + new GoodPart("X").equals(new GoodPart("X")));
        System.out.println("good set size  -> " + good.size());
        System.out.println("good contains  -> " + good.contains(new GoodPart("BOLT-7")));
        System.out.println("good toString  -> " + new GoodPart("BOLT-7"));
    }
}