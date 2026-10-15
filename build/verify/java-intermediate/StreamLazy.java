import java.util.List;
import java.util.Optional;

public class StreamLazy {
    public static void main(String[] args) {
        List<String> skus = List.of("BOLT-7", "NUT-3", "GIRD-1", "CAM-9");

        System.out.println("-- no terminal operation: nothing runs --");
        skus.stream()
            .filter(s -> { System.out.println("  filter " + s); return !s.startsWith("B"); })
            .map(s -> { System.out.println("  map    " + s); return s.toLowerCase(); });
        System.out.println("(pipeline built, zero elements processed)");

        System.out.println("-- findFirst: one element at a time, and it stops early --");
        Optional<String> first = skus.stream()
            .filter(s -> { System.out.println("  filter " + s); return !s.startsWith("B"); })
            .map(s -> { System.out.println("  map    " + s); return s.toLowerCase(); })
            .findFirst();
        System.out.println("result: " + first.orElse("none"));

        System.out.println("-- sorted is a barrier: every element arrives before any leaves --");
        List<String> out = skus.stream()
            .filter(s -> { System.out.println("  filter " + s); return !s.startsWith("N"); })
            .sorted()
            .map(s -> { System.out.println("  map    " + s); return s.toLowerCase(); })
            .limit(2)
            .toList();
        System.out.println("result: " + out);
    }
}