import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Composing {

    record Quote(String sku, int priceInCents, int onHand) {
        String line() { return sku + " @ " + priceInCents + "c, " + onHand + " on hand"; }
    }

    static final Map<String, Integer> PRICES = Map.of(
            "BOLT-7", 80, "NUT-3", 25, "CAM-9", 2300, "GIRD-1", 120_000, "ROD-4", 4500);
    static final Map<String, Integer> STOCK = Map.of(
            "BOLT-7", 400, "NUT-3", 1200, "CAM-9", 2, "GIRD-1", 3, "ROD-4", 40);
    static final Map<String, String> SUPPLIER = Map.of("BOLT-7", "ACME", "NUT-3", "ACME", "CAM-9", "KINETIC");

    // Each "lookup" stands in for a remote call: slow, independent, may fail.
    static int price(String sku) {
        Integer p = PRICES.get(sku);
        if (p == null) throw new IllegalArgumentException("unknown sku " + sku);
        return p;
    }
    static int stock(String sku) { return STOCK.getOrDefault(sku, 0); }
    static String supplier(String sku) { return SUPPLIER.getOrDefault(sku, "none"); }

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);

        // 1. Two independent lookups, combined when BOTH are done.
        CompletableFuture<Integer> price = CompletableFuture.supplyAsync(() -> price("BOLT-7"), pool);
        CompletableFuture<Integer> stock = CompletableFuture.supplyAsync(() -> stock("BOLT-7"), pool);
        CompletableFuture<Quote> quote = price.thenCombine(stock, (p, s) -> new Quote("BOLT-7", p, s));
        System.out.println("thenCombine  = " + quote.join().line());

        // 2. A dependent lookup: thenCompose flattens a stage that itself returns a future.
        CompletableFuture<String> supplier = quote.thenCompose(q ->
                CompletableFuture.supplyAsync(() -> supplier(q.sku()), pool));
        System.out.println("thenCompose  = " + supplier.thenApply(s -> "supplied by " + s).join());

        // 3. A failing lookup. Nothing is thrown here: the failure is stored in the future.
        CompletableFuture<Integer> bad = CompletableFuture.supplyAsync(() -> price("NOPE"), pool);
        System.out.println("recovered    = " + bad.exceptionally(ex -> -1).join());
        System.out.println("handle       = " + bad.handle((v, ex) ->
                ex == null ? "value " + v : "failed: " + ex.getCause().getMessage()).join());
        String wrapping = bad.handle((v, ex) ->
                ex.getClass().getSimpleName() + " wrapping " + ex.getCause().getClass().getSimpleName()).join();
        System.out.println("stage saw    = " + wrapping);
        try { bad.join(); }
        catch (CompletionException e) { System.out.println("join threw   = CompletionException -> " + e.getCause().getMessage()); }
        try { bad.get(); }
        catch (ExecutionException e) { System.out.println("get threw    = ExecutionException -> " + e.getCause().getMessage()); }

        // 4. Fan out five quotes, wait for all, read them back in a stable order.
        List<String> skus = List.of("ROD-4", "BOLT-7", "GIRD-1", "NUT-3", "CAM-9");
        List<CompletableFuture<Quote>> quotes = new ArrayList<>();
        for (String sku : skus) {
            quotes.add(CompletableFuture.supplyAsync(() -> price(sku), pool)
                    .thenCombine(CompletableFuture.supplyAsync(() -> stock(sku), pool),
                                 (p, s) -> new Quote(sku, p, s)));
        }
        CompletableFuture<Void> all = CompletableFuture.allOf(quotes.toArray(new CompletableFuture[0]));
        all.join();                                              // Void: it only says "every one is done"
        List<String> lines = quotes.stream().map(CompletableFuture::join).map(Quote::line).sorted().toList();
        System.out.println("allOf        = done " + all.isDone() + ", " + lines.size() + " quotes");
        lines.forEach(l -> System.out.println("    " + l));

        // 5. A stage that never completes, bounded by a timeout (Java 9+).
        CompletableFuture<String> never = new CompletableFuture<>();
        System.out.println("onTimeout    = " + never.completeOnTimeout("cached quote", 50, TimeUnit.MILLISECONDS).join());
        CompletableFuture<String> never2 = new CompletableFuture<>();
        try { never2.orTimeout(50, TimeUnit.MILLISECONDS).join(); }
        catch (CompletionException e) { System.out.println("orTimeout    = " + e.getCause().getClass().getSimpleName()); }

        // 6. A value already in hand enters the same pipeline without touching a thread.
        System.out.println("completed    = " + CompletableFuture.completedFuture(21).thenApply(x -> x * 2).join());

        pool.shutdown();
        System.out.println("pool stopped = " + pool.awaitTermination(5, TimeUnit.SECONDS));
    }
}