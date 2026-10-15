import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class CollectionTour {
    public static void main(String[] args) {
        List<String> picks = new ArrayList<>(List.of("NUT-3", "BOLT-7", "NUT-3"));
        System.out.println("list, dupes kept     = " + picks);

        Set<String> unique = new HashSet<>(picks);
        System.out.println("hashset size         = " + unique.size());
        System.out.println("linkedhashset        = " + new LinkedHashSet<>(picks));
        System.out.println("treeset, sorted      = " + new TreeSet<>(picks));

        Map<String, Integer> onHand = new HashMap<>();
        onHand.put("BOLT-7", 12);
        onHand.put("NUT-3", 0);
        System.out.println("get missing key      = " + onHand.get("NOPE"));
        System.out.println("getOrDefault         = " + onHand.getOrDefault("NOPE", 0));
        System.out.println("containsKey NUT-3    = " + onHand.containsKey("NUT-3"));

        onHand.put("BOLT-7", onHand.get("BOLT-7") + 5);
        System.out.println("after restock        = " + onHand.get("BOLT-7"));

        try {
            List.of("A", "B").add("C");
        } catch (UnsupportedOperationException e) {
            System.out.println("List.of is immutable = true");
        }
    }
}