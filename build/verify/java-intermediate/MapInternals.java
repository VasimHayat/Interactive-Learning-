import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class MapInternals {

    static final class Bin {                        // a mutable key: the mistake this section is about
        String code;
        Bin(String code) { this.code = code; }
        @Override public boolean equals(Object o) { return o instanceof Bin b && b.code.equals(code); }
        @Override public int hashCode() { return code.hashCode(); }
    }

    public static void main(String[] args) {
        Map<Bin, Integer> stock = new HashMap<>();
        Bin key = new Bin("A-01");
        stock.put(key, 40);
        key.code = "A-02";                           // the hash changes; the entry stays in the old bucket
        System.out.println("size=" + stock.size()
                + "  containsKey(A-01)=" + stock.containsKey(new Bin("A-01"))
                + "  containsKey(A-02)=" + stock.containsKey(new Bin("A-02"))
                + "  containsKey(key)=" + stock.containsKey(key));

        Map<String, List<String>> byCategory = new TreeMap<>();
        byCategory.computeIfAbsent("FASTENER", k -> new ArrayList<>()).add("BOLT-7");
        byCategory.computeIfAbsent("FASTENER", k -> new ArrayList<>()).add("NUT-3");
        byCategory.computeIfAbsent("STRUCTURAL", k -> new ArrayList<>()).add("GIRD-1");
        System.out.println("multimap  " + byCategory);

        Map<String, Integer> picks = new TreeMap<>();
        for (String sku : List.of("BOLT-7", "NUT-3", "BOLT-7", "BOLT-7")) picks.merge(sku, 1, Integer::sum);
        System.out.println("counts    " + picks + "  CAM-9 -> " + picks.getOrDefault("CAM-9", 0));

        Map<String, Integer> linked = new LinkedHashMap<>();
        Map<String, Integer> sorted = new TreeMap<>();
        for (String sku : List.of("ROD-4", "BOLT-7", "NUT-3")) { linked.put(sku, 1); sorted.put(sku, 1); }
        System.out.println("linked    " + linked.keySet() + "  insertion order");
        System.out.println("tree      " + sorted.keySet() + "  sorted order");

        TreeMap<Integer, String> bands = new TreeMap<>(Map.of(0, "small", 100, "medium", 1_000, "large"));
        System.out.println("250c is " + bands.floorEntry(250).getValue() + ", bands under 1000: " + bands.headMap(1_000).keySet());

        Map<String, Integer> lru = new LinkedHashMap<>(16, 0.75f, true) {   // true = access order
            @Override protected boolean removeEldestEntry(Map.Entry<String, Integer> e) { return size() > 2; }
        };
        lru.put("BOLT-7", 1); lru.put("NUT-3", 1);
        lru.get("BOLT-7");                           // touching it makes NUT-3 the eldest
        lru.put("CAM-9", 1);
        System.out.println("lru       " + lru.keySet() + "  NUT-3 evicted: least recently used");

        List<String> toShip = new ArrayList<>(List.of("BOLT-7", "NUT-3", "CAM-9", "ROD-4"));
        try {
            for (String s : toShip) if (s.startsWith("B")) toShip.remove(s);
        } catch (ConcurrentModificationException e) {
            System.out.println("for-each remove -> ConcurrentModificationException, list now " + toShip);
        }
        toShip.removeIf(s -> s.startsWith("N"));
        for (Iterator<String> it = toShip.iterator(); it.hasNext();) if (it.next().startsWith("C")) it.remove();
        System.out.println("removeIf + Iterator.remove -> " + toShip);
    }
}