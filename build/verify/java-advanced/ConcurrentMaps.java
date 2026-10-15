import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

public class ConcurrentMaps {

    enum Category { FASTENER, STRUCTURAL, MOTION }
    record Product(String sku, Category category) { }

    static final List<Product> CATALOGUE = List.of(
        new Product("BOLT-7", Category.FASTENER), new Product("NUT-3", Category.FASTENER),
        new Product("PIN-2", Category.FASTENER),  new Product("GIRD-1", Category.STRUCTURAL),
        new Product("ROD-4", Category.STRUCTURAL), new Product("CAM-9", Category.MOTION));

    static void runAll(int threads, Runnable body) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1), done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            new Thread(() -> { try { start.await(); body.run(); } catch (InterruptedException e) { } done.countDown(); }).start();
        }
        start.countDown();
        done.await();
    }

    public static void main(String[] args) throws InterruptedException {
        // ── merge: an atomic read-modify-write per key ──────────────────────
        ConcurrentHashMap<String, Integer> picks = new ConcurrentHashMap<>();
        runAll(8, () -> {
            for (int i = 0; i < 1_000; i++) {
                Product p = CATALOGUE.get(i % CATALOGUE.size());
                picks.merge(p.sku(), 1, Integer::sum);           // never lost, never double-counted
            }
        });
        System.out.println("picks by sku       = " + new TreeMap<>(picks));
        int total = picks.values().stream().mapToInt(Integer::intValue).sum();
        System.out.println("total picks        = " + total + " (expected 8000)");

        // ── computeIfAbsent: build a multimap without a race on the list ────
        ConcurrentHashMap<Category, CopyOnWriteArrayList<String>> byCategory = new ConcurrentHashMap<>();
        runAll(4, () -> {
            for (Product p : CATALOGUE) {
                byCategory.computeIfAbsent(p.category(), k -> new CopyOnWriteArrayList<>()).addIfAbsent(p.sku());
            }
        });
        System.out.println("by category        = " + new TreeMap<>(byCategory));

        // ── compute: a conditional decrement that cannot go negative ────────
        ConcurrentHashMap<String, Integer> onHand = new ConcurrentHashMap<>(Map.of("BOLT-7", 500));
        runAll(8, () -> {
            for (int i = 0; i < 100; i++) {
                onHand.compute("BOLT-7", (k, v) -> v != null && v > 0 ? v - 1 : v);   // 800 attempts, floor at 0
            }
        });
        System.out.println("BOLT-7 on hand     = " + onHand.get("BOLT-7") + " (800 attempts against 500)");

        // ── CopyOnWriteArrayList: iterate a snapshot while others write ─────
        CopyOnWriteArrayList<String> listeners = new CopyOnWriteArrayList<>(List.of("audit", "metrics"));
        for (String l : listeners) {                       // the iterator holds the old array ...
            if (l.equals("audit")) listeners.add("alerts");  // ... so this write does not throw
        }
        System.out.println("listeners          = " + listeners);

        // ── ArrayBlockingQueue: hand-off with a poison pill ─────────────────
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(8);
        int n = 2_000;
        long[] consumed = new long[1];
        Thread producer = new Thread(() -> {
            try { for (int i = 1; i <= n; i++) queue.put(i); queue.put(-1); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        Thread consumer = new Thread(() -> {
            try { for (int v; (v = queue.take()) != -1; ) consumed[0] += v; }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        producer.start(); consumer.start();
        producer.join(10_000); consumer.join(10_000);
        System.out.println("consumed sum       = " + consumed[0] + " (expected " + ((long) n * (n + 1) / 2) + ")");
        System.out.println("queue drained      = " + queue.isEmpty());

        // ── ConcurrentSkipListMap: concurrent AND sorted ────────────────────
        ConcurrentSkipListMap<String, Integer> sorted = new ConcurrentSkipListMap<>();
        runAll(4, () -> { for (Product p : CATALOGUE) sorted.merge(p.sku(), 1, Integer::sum); });
        System.out.println("skip list          = " + sorted);
        System.out.println("first / last key   = " + sorted.firstKey() + " / " + sorted.lastKey());

        // ── nulls are rejected at runtime, not by the compiler ──────────────
        try { picks.put("HUB-8", null); }
        catch (NullPointerException e) { System.out.println("null value         = NullPointerException"); }
    }
}