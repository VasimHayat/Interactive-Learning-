import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

public class ReservationLab {

    record Order(int id, String sku, int qty) {}
    static final Order POISON = new Order(-1, "", 0);

    /* Step 1 — lock-free stock with a CAS loop that can never go negative */
    static final class InventoryService {
        private final ConcurrentHashMap<String, AtomicInteger> stock = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();
        final AtomicInteger reserved = new AtomicInteger();
        final AtomicInteger refused = new AtomicInteger();
        final AtomicBoolean wentNegative = new AtomicBoolean(false);

        void stock(String sku, int qty) { stock.computeIfAbsent(sku, k -> new AtomicInteger()).addAndGet(qty); }
        int onHand(String sku) { AtomicInteger a = stock.get(sku); return a == null ? 0 : a.get(); }

        boolean reserve(String sku, int qty) {
            AtomicInteger level = stock.get(sku);
            if (level == null) { refused.incrementAndGet(); return false; }
            while (true) {
                int current = level.get();
                if (current < qty) { refused.incrementAndGet(); return false; }
                if (level.compareAndSet(current, current - qty)) {
                    if (current - qty < 0) wentNegative.set(true);
                    reserved.incrementAndGet();
                    return true;
                }
                // another thread won the race: re-read and try again
            }
        }

        /* Step 2 — two locks, always taken in SKU order, with a bounded wait */
        boolean transfer(String from, String to, int qty) throws InterruptedException {
            String lo = from.compareTo(to) < 0 ? from : to;
            String hi = lo.equals(from) ? to : from;
            ReentrantLock first = locks.computeIfAbsent(lo, k -> new ReentrantLock());
            ReentrantLock second = locks.computeIfAbsent(hi, k -> new ReentrantLock());
            if (!first.tryLock(2, TimeUnit.SECONDS)) return false;
            try {
                if (!second.tryLock(2, TimeUnit.SECONDS)) return false;
                try {
                    AtomicInteger src = stock.get(from);
                    if (src == null || src.get() < qty) return false;
                    src.addAndGet(-qty);
                    stock.computeIfAbsent(to, k -> new AtomicInteger()).addAndGet(qty);
                    return true;
                } finally { second.unlock(); }
            } finally { first.unlock(); }
        }

        /* Step 6 — a consistent snapshot while writers keep going */
        private final ReadWriteLock rw = new ReentrantReadWriteLock();
        Map<String, Integer> snapshot() {
            rw.readLock().lock();
            try {
                TreeMap<String, Integer> copy = new TreeMap<>();
                stock.forEach((k, v) -> copy.put(k, v.get()));
                return Collections.unmodifiableMap(copy);
            } finally { rw.readLock().unlock(); }
        }
    }

    static final String[] SKUS = { "BOLT-7", "NUT-3", "CAM-9", "GIRD-1" };

    /* Step 3 — producers feed a bounded queue, a fixed pool of consumers drains it */
    static void runPickers(InventoryService inv, ExecutorService consumers, int consumerCount) throws Exception {
        BlockingQueue<Order> queue = new ArrayBlockingQueue<>(32);
        int producers = 4, ordersPerProducer = 100;
        CountDownLatch produced = new CountDownLatch(producers);
        List<Future<Integer>> handled = new ArrayList<>();

        for (int c = 0; c < consumerCount; c++) {
            handled.add(consumers.submit(() -> {
                int n = 0;
                while (true) {
                    Order o = queue.poll(5, TimeUnit.SECONDS);
                    if (o == null || o == POISON) break;
                    inv.reserve(o.sku(), o.qty());
                    n++;
                }
                return n;
            }));
        }
        try (ExecutorService producerPool = Executors.newFixedThreadPool(producers)) {
            for (int p = 0; p < producers; p++) {
                final int base = p * ordersPerProducer;
                producerPool.submit(() -> {
                    try {
                        for (int i = 0; i < ordersPerProducer; i++) {
                            queue.put(new Order(base + i, SKUS[i % SKUS.length], 1 + (i % 3)));
                        }
                    } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    finally { produced.countDown(); }
                });
            }
            produced.await(10, TimeUnit.SECONDS);
            for (int c = 0; c < consumerCount; c++) queue.put(POISON);   // one pill per consumer
        }
        int total = 0;
        for (Future<Integer> f : handled) total += f.get(10, TimeUnit.SECONDS);
        System.out.println("orders processed            = " + total);
    }

    /* Step 4 — a quote that combines two async lookups and degrades gracefully */
    static CompletableFuture<String> quote(InventoryService inv, String sku, Executor pool) {
        CompletableFuture<Long> price = CompletableFuture.supplyAsync(() -> {
            if (sku.equals("GIRD-1")) throw new IllegalStateException("pricing service down");
            return switch (sku) { case "BOLT-7" -> 80L; case "NUT-3" -> 25L; default -> 2_300L; };
        }, pool);
        CompletableFuture<Integer> onHand = CompletableFuture.supplyAsync(() -> inv.onHand(sku), pool);
        return price.thenCombine(onHand, (p, h) -> sku + ": " + p + "c, " + h + " on hand")
                    .exceptionally(ex -> sku + ": quote unavailable")
                    .orTimeout(5, TimeUnit.SECONDS);
    }

    public static void main(String[] args) throws Exception {
        InventoryService inv = new InventoryService();
        for (String s : SKUS) inv.stock(s, 300);   // demand is 199 per SKU, so nothing is refused

        // Step 3 on a platform-thread pool
        try (ExecutorService pool = Executors.newFixedThreadPool(4)) { runPickers(inv, pool, 4); }
        Map<String, Integer> afterPlatform = inv.snapshot();
        int stockAfterPicking = afterPlatform.values().stream().mapToInt(Integer::intValue).sum();

        // Step 2 — move stock between SKUs under ordered locks, from several threads at once
        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            List<Future<Boolean>> moves = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                String a = SKUS[i % 4], b = SKUS[(i + 1) % 4];
                moves.add(pool.submit(() -> inv.transfer(a, b, 1)));
            }
            int done = 0;
            for (Future<Boolean> m : moves) if (m.get(10, TimeUnit.SECONDS)) done++;
            System.out.println("transfers completed         = " + done + " of 40");
        }

        // Step 5 — the same workload on virtual threads, fresh stock, identical totals expected
        InventoryService inv2 = new InventoryService();
        for (String s : SKUS) inv2.stock(s, 300);
        try (ExecutorService vpool = Executors.newVirtualThreadPerTaskExecutor()) { runPickers(inv2, vpool, 4); }

        // Step 4
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<CompletableFuture<String>> quotes = new ArrayList<>();
            for (String s : SKUS) quotes.add(quote(inv2, s, pool));
            CompletableFuture.allOf(quotes.toArray(new CompletableFuture<?>[0])).get(10, TimeUnit.SECONDS);
            List<String> lines = new ArrayList<>();
            for (CompletableFuture<String> q : quotes) lines.add(q.join());
            Collections.sort(lines);
            lines.forEach(l -> System.out.println("quote                       = " + l));
        }

        boolean refusedBig = !inv.reserve("BOLT-7", 1_000);          // more than exists: must refuse, never go negative
        System.out.println("oversized order refused     = " + refusedBig);

        // Step 6 — facts only
        int stockAfterTransfers = inv.snapshot().values().stream().mapToInt(Integer::intValue).sum();
        System.out.println("stock never negative        = " + !inv.wentNegative.get());
        System.out.println("reserved + refused == 401   = " + (inv.reserved.get() + inv.refused.get() == 401));
        System.out.println("stock conserved by transfer = " + (stockAfterTransfers == stockAfterPicking));
        System.out.println("platform == virtual totals  = " + (inv.reserved.get() == inv2.reserved.get()
                && inv2.snapshot().equals(afterPlatform)));
        System.out.println("final stock (virtual run)   = " + inv2.snapshot());
    }
}