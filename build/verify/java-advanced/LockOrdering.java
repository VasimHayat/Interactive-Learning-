import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class LockOrdering {

    static final class Stock {
        final String sku;
        final ReentrantLock lock = new ReentrantLock();
        int units;
        Stock(String sku, int units) { this.sku = sku; this.units = units; }
    }

    /* Cure 1: every thread acquires in the same global order, here by SKU.
       Two threads moving stock in opposite directions still lock BOLT-7
       first and NUT-3 second, so neither can hold what the other needs. */
    static void transferOrdered(Stock from, Stock to, int qty) {
        Stock first = from.sku.compareTo(to.sku) < 0 ? from : to;
        Stock second = first == from ? to : from;
        first.lock.lock();
        try {
            second.lock.lock();
            try {
                if (from.units >= qty) { from.units -= qty; to.units += qty; }
            } finally { second.lock.unlock(); }
        } finally { first.lock.unlock(); }
    }

    /* Cure 2: no fixed order, but never wait forever. A thread that cannot
       get the second lock within the timeout releases the first and retries,
       so a would-be deadlock resolves itself. */
    static boolean transferTimed(Stock from, Stock to, int qty) throws InterruptedException {
        for (int attempt = 0; attempt < 1000; attempt++) {
            if (from.lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                try {
                    if (to.lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                        try {
                            if (from.units >= qty) { from.units -= qty; to.units += qty; }
                            return true;
                        } finally { to.lock.unlock(); }
                    }
                } finally { from.lock.unlock(); }
            }
            Thread.onSpinWait();            // back off, then try again
        }
        return false;
    }

    interface Transfer { void run(Stock from, Stock to, int qty) throws InterruptedException; }

    static void race(String label, Transfer transfer) throws InterruptedException {
        Stock bolt = new Stock("BOLT-7", 10_000), nut = new Stock("NUT-3", 10_000);
        int moves = 5_000;
        CountDownLatch start = new CountDownLatch(1), done = new CountDownLatch(2);
        Runnable ab = () -> { try { start.await(); for (int i = 0; i < moves; i++) transfer.run(bolt, nut, 1); } catch (InterruptedException e) { } done.countDown(); };
        Runnable ba = () -> { try { start.await(); for (int i = 0; i < moves; i++) transfer.run(nut, bolt, 1); } catch (InterruptedException e) { } done.countDown(); };
        new Thread(ab, "mover-A").start();
        new Thread(ba, "mover-B").start();
        start.countDown();
        boolean finished = done.await(30, TimeUnit.SECONDS);   // a real deadlock would time out here
        System.out.println(label);
        System.out.println("  transfers completed = " + finished);
        System.out.println("  total units         = " + (bolt.units + nut.units) + " (expected 20000)");
    }

    public static void main(String[] args) throws InterruptedException {
        race("ordered locks", LockOrdering::transferOrdered);
        race("tryLock with timeout", (f, t, q) -> transferTimed(f, t, q));
    }
}