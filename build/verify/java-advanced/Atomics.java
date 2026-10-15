import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

public class Atomics {

    /* A bounded decrement with compare-and-swap: read, compute, try to swap,
       and retry if another thread moved the value first. No lock, and the
       invariant (never below zero) still holds. */
    static final class Stock {
        private final AtomicInteger units;
        Stock(int units) { this.units = new AtomicInteger(units); }

        boolean reserve(int qty) {
            while (true) {
                int current = units.get();
                if (current < qty) return false;            // refuse: nothing to swap
                if (units.compareAndSet(current, current - qty)) return true;
                // CAS failed: someone else changed units between get and CAS — loop
            }
        }
        int units() { return units.get(); }
    }

    record Price(String sku, int cents) { }

    static void runAll(int threads, Runnable body) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1), done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            new Thread(() -> {
                try { start.await(); body.run(); } catch (InterruptedException e) { }
                done.countDown();
            }).start();
        }
        start.countDown();
        done.await();
    }

    public static void main(String[] args) throws InterruptedException {
        // ── incrementAndGet is one indivisible operation ────────────────────
        AtomicInteger picks = new AtomicInteger();
        runAll(8, () -> { for (int i = 0; i < 100_000; i++) picks.incrementAndGet(); });
        System.out.println("picks            = " + picks.get() + " (expected 800000)");

        // ── CAS loop: 8 threads fight over 600 units, 100 attempts each ─────
        Stock stock = new Stock(600);
        AtomicInteger reserved = new AtomicInteger(), refused = new AtomicInteger();
        runAll(8, () -> {
            for (int i = 0; i < 100; i++) {
                if (stock.reserve(1)) reserved.incrementAndGet(); else refused.incrementAndGet();
            }
        });
        System.out.println("reserved         = " + reserved.get());
        System.out.println("refused          = " + refused.get());
        System.out.println("units left       = " + stock.units());
        System.out.println("never negative   = " + (stock.units() >= 0 && reserved.get() + stock.units() == 600));

        // ── LongAdder: the counter for write-heavy contention ────────────────
        LongAdder cents = new LongAdder();
        runAll(8, () -> { for (int i = 0; i < 10_000; i++) cents.add(25); });
        System.out.println("revenue (cents)  = " + cents.sum());

        // ── AtomicReference: swap an immutable record atomically ────────────
        AtomicReference<Price> price = new AtomicReference<>(new Price("BOLT-7", 80));
        runAll(4, () -> {
            for (int i = 0; i < 1_000; i++) {
                Price old, updated;
                do {
                    old = price.get();
                    updated = new Price(old.sku(), old.cents() + 1);
                } while (!price.compareAndSet(old, updated));
            }
        });
        System.out.println("price after 4000 = " + price.get());
    }
}