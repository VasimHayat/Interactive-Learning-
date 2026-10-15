import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class Lab {

    static final class Part {
        private final String sku;
        private final int priceInCents;

        Part(String sku, int priceInCents) {
            if (sku == null || sku.isBlank())
                throw new IllegalArgumentException("sku must not be blank");
            if (priceInCents < 0)
                throw new IllegalArgumentException("price must not be negative: " + priceInCents);
            this.sku = sku;
            this.priceInCents = priceInCents;
        }

        String sku() { return sku; }
        int priceInCents() { return priceInCents; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Part other)) return false;
            return sku.equals(other.sku);
        }
        @Override public int hashCode() { return sku.hashCode(); }
        @Override public String toString() { return "Part[" + sku + ", " + priceInCents + "c]"; }
    }

    static class OutOfStockException extends Exception {
        OutOfStockException(String sku, int requested, int available) {
            super("cannot reserve " + requested + " of " + sku + "; only " + available + " on hand");
        }
    }

    interface Repository<T> {
        void save(T item);
        T findById(String id);
        int count();
    }

    static final class PartRepository implements Repository<Part> {
        private final Map<String, Part> bySku = new HashMap<>();
        @Override public void save(Part item) { bySku.put(item.sku(), item); }
        @Override public Part findById(String id) { return bySku.get(id); }
        @Override public int count() { return bySku.size(); }
    }

    static final class InventoryService {
        private final Map<String, Integer> onHand = new HashMap<>();
        private final Set<String> pickedThisRun = new LinkedHashSet<>();

        void stock(String sku, int qty) { onHand.put(sku, onHand.getOrDefault(sku, 0) + qty); }

        void reserve(String sku, int qty) throws OutOfStockException {
            if (qty <= 0) throw new IllegalArgumentException("qty must be positive: " + qty);
            int available = onHand.getOrDefault(sku, 0);
            if (qty > available) throw new OutOfStockException(sku, qty, available);
            onHand.put(sku, available - qty);
            pickedThisRun.add(sku);
        }

        int onHand(String sku) { return onHand.getOrDefault(sku, 0); }
        Set<String> picked() { return Set.copyOf(pickedThisRun); }
        String pickedInOrder() { return String.join(", ", pickedThisRun); }
    }

    public static void main(String[] args) {
        PartRepository repo = new PartRepository();
        repo.save(new Part("BOLT-7", 80));
        repo.save(new Part("NUT-3", 25));
        repo.save(new Part("BOLT-7", 90));          // same sku: replaces, does not duplicate
        System.out.println("repo count      = " + repo.count());
        System.out.println("lookup          = " + repo.findById("BOLT-7"));
        System.out.println("equal by sku    = " + new Part("BOLT-7", 1).equals(new Part("BOLT-7", 999)));

        InventoryService inv = new InventoryService();
        inv.stock("BOLT-7", 10);
        inv.stock("NUT-3", 4);

        try {
            inv.reserve("BOLT-7", 3);
            inv.reserve("NUT-3", 4);
            inv.reserve("BOLT-7", 2);
            inv.reserve("NUT-3", 1);                 // NUT-3 is exhausted
        } catch (OutOfStockException e) {
            System.out.println("caught          = " + e.getMessage());
        }

        try {
            inv.reserve("BOLT-7", 0);
        } catch (IllegalArgumentException e) {
            System.out.println("bad qty         = " + e.getMessage());
        } catch (OutOfStockException e) {
            System.out.println("unreachable");
        }

        System.out.println("BOLT-7 on hand  = " + inv.onHand("BOLT-7"));
        System.out.println("picked in order = " + inv.pickedInOrder());
        System.out.println("unknown sku     = " + inv.onHand("NOPE"));
    }
}