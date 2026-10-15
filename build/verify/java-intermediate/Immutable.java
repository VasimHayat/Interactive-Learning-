import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Immutable {

    static final class Order {
        private final List<String> skus;                          // final reference to a mutable object
        Order(List<String> skus) { this.skus = new ArrayList<>(skus); }        // defensive copy in
        List<String> skus() { return Collections.unmodifiableList(skus); }     // read-only view out
    }

    public static void main(String[] args) {
        List<String> source = new ArrayList<>(List.of("BOLT-7", "NUT-3"));
        List<String> view = Collections.unmodifiableList(source);
        List<String> copy = List.copyOf(source);

        source.add("CAM-9");
        System.out.println("source " + source);
        System.out.println("view   " + view + "   a view: it follows the source");
        System.out.println("copy   " + copy + "   a snapshot: fixed at copy time");

        try { view.add("ROD-4"); }
        catch (UnsupportedOperationException e) { System.out.println("view.add            -> UnsupportedOperationException"); }

        try { List.of("BOLT-7", null); }
        catch (NullPointerException e) { System.out.println("List.of(.., null)   -> NullPointerException"); }

        List<String> input = new ArrayList<>(List.of("PIN-2"));
        Order order = new Order(input);
        input.add("HUB-8");                                       // the caller keeps mutating its own list
        System.out.println("order  " + order.skus() + "   unaffected: the constructor copied");

        try { order.skus().add("HUB-8"); }
        catch (UnsupportedOperationException e) { System.out.println("order.skus().add    -> UnsupportedOperationException"); }

        System.out.println("List.copyOf(copy) == copy ? " + (List.copyOf(copy) == copy) + "   already immutable, so no new object");
    }
}