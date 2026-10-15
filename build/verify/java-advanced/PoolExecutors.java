import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PoolExecutors {

    record Reservation(String sku, int qty) { }

    static final String[] SKUS = { "BOLT-7", "NUT-3", "CAM-9", "GIRD-1", "ROD-4" };

    public static void main(String[] args) throws Exception {
        // ── a fixed pool runs 20 Callables; invokeAll waits for every one ───
        ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(4, named("picker"));
        List<Callable<Reservation>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            final int n = i;
            tasks.add(() -> new Reservation(SKUS[n % SKUS.length], n + 1));
        }
        List<Future<Reservation>> futures = pool.invokeAll(tasks);

        int totalQty = 0;
        List<String> skus = new ArrayList<>();
        for (Future<Reservation> f : futures) {
            Reservation r = f.get();                       // already complete after invokeAll
            totalQty += r.qty();
            if (!skus.contains(r.sku())) skus.add(r.sku());
        }
        System.out.println("tasks completed    = " + futures.stream().allMatch(Future::isDone));
        System.out.println("total qty          = " + totalQty + " (1+2+...+20)");
        System.out.println("skus (sorted)      = " + skus.stream().sorted().toList());

        // ── a task that throws: the exception travels inside the Future ─────
        Future<Integer> bad = pool.submit(() -> { throw new IllegalStateException("BOLT-7 out of stock"); });
        try {
            bad.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            System.out.println("wrapped cause      = " + e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage());
        }

        // ── orderly shutdown: no new tasks, finish queued ones, then wait ───
        pool.shutdown();
        System.out.println("terminated         = " + pool.awaitTermination(10, TimeUnit.SECONDS));

        // ── scheduling: run once after a delay ──────────────────────────────
        ScheduledExecutorService scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(named("repricer"));
        ScheduledFuture<String> reprice = scheduler.schedule(() -> "reprice done", 20, TimeUnit.MILLISECONDS);
        System.out.println("scheduled result   = " + reprice.get(5, TimeUnit.SECONDS));
        scheduler.shutdown();

        // ── rejection: 1 thread, a queue of 1, 3 tasks that block ───────────
        CountDownLatch gate = new CountDownLatch(1);
        ThreadPoolExecutor tiny = new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1), named("tiny"),
            new ThreadPoolExecutor.AbortPolicy());                    // the default handler
        Runnable blocker = () -> { try { gate.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { } };
        tiny.execute(blocker);                                          // taken by the one thread
        tiny.execute(blocker);                                          // sits in the queue
        try {
            tiny.execute(blocker);                                      // nowhere to go
            System.out.println("third task         = accepted");
        } catch (RejectedExecutionException e) {
            System.out.println("third task         = rejected (RejectedExecutionException)");
        }
        gate.countDown();
        tiny.shutdown();
        System.out.println("tiny terminated    = " + tiny.awaitTermination(10, TimeUnit.SECONDS));

        // ── CallerRunsPolicy: the submitting thread does the work itself ────
        CountDownLatch gate2 = new CountDownLatch(1);
        ThreadPoolExecutor polite = new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1), named("polite"),
            new ThreadPoolExecutor.CallerRunsPolicy());
        AtomicInteger ranOnMain = new AtomicInteger();
        Runnable blocker2 = () -> { try { gate2.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { } };
        polite.execute(blocker2);
        polite.execute(blocker2);
        polite.execute(() -> { if (Thread.currentThread().getName().equals("main")) ranOnMain.incrementAndGet(); });
        System.out.println("overflow ran on    = " + (ranOnMain.get() == 1 ? "the caller (main)" : "a pool thread"));
        gate2.countDown();
        polite.shutdown();
        System.out.println("polite terminated  = " + polite.awaitTermination(10, TimeUnit.SECONDS));
    }

    // name your threads: a dump full of "pool-3-thread-7" is useless at 3 a.m.
    static ThreadFactory named(String prefix) {
        AtomicInteger n = new AtomicInteger();
        return r -> new Thread(r, prefix + "-" + n.incrementAndGet());
    }
}