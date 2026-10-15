import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CountDownLatch;

public class Synchronized {

    /* One lock, one invariant: onHand never goes negative and every
       reservation is either fully applied or refused. */
    static final class InventoryService {
        private int onHand;
        private int reserved;
        private int refused;

        InventoryService(int onHand) { this.onHand = onHand; }

        synchronized boolean reserve(int qty) {
            if (qty > onHand) { refused++; return false; }
            onHand -= qty;
            reserved += qty;
            return true;
        }

        synchronized void release(int qty) { onHand += qty; }

        // a synchronized method calling another synchronized method on the
        // same object: the thread already owns the monitor, so it re-enters
        synchronized boolean reserveTwice(int qty) {
            return reserve(qty) && reserve(qty);
        }

        synchronized int onHand() { return onHand; }
        synchronized int reserved() { return reserved; }
        synchronized int refused() { return refused; }
    }

    /* A bounded buffer built on wait/notifyAll: the textbook shape every
       BlockingQueue implements for you. */
    static final class BoundedBuffer {
        private final Deque<Integer> items = new ArrayDeque<>();
        private final int capacity;
        BoundedBuffer(int capacity) { this.capacity = capacity; }

        synchronized void put(int item) throws InterruptedException {
            while (items.size() == capacity) wait();   // loop, never if: spurious wakeups
            items.addLast(item);
            notifyAll();
        }

        synchronized int take() throws InterruptedException {
            while (items.isEmpty()) wait();
            int item = items.removeFirst();
            notifyAll();
            return item;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        // ── 8 pickers hammer one service ────────────────────────────────────
        InventoryService inv = new InventoryService(500);
        int threads = 8, perThread = 100, qty = 1;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            Thread picker = new Thread(() -> {
                try { start.await(); } catch (InterruptedException e) { return; }
                for (int i = 0; i < perThread; i++) inv.reserve(qty);
                done.countDown();
            }, "picker-" + t);
            picker.start();
        }
        start.countDown();                          // everyone leaves the gate together
        done.await();

        int attempts = threads * perThread;         // 800 attempts against 500 units
        System.out.println("attempts        = " + attempts);
        System.out.println("reserved        = " + inv.reserved());
        System.out.println("refused         = " + inv.refused());
        System.out.println("on hand         = " + inv.onHand());
        System.out.println("invariant holds = " + (inv.reserved() + inv.onHand() == 500
                                                   && inv.reserved() + inv.refused() == attempts));

        // ── reentrancy ──────────────────────────────────────────────────────
        InventoryService small = new InventoryService(10);
        System.out.println("reentered       = " + small.reserveTwice(3) + " (on hand " + small.onHand() + ")");

        // ── wait / notifyAll producer-consumer ──────────────────────────────
        BoundedBuffer buffer = new BoundedBuffer(4);
        int n = 1000;
        long[] consumedSum = new long[1];
        Thread producer = new Thread(() -> {
            try { for (int i = 1; i <= n; i++) buffer.put(i); buffer.put(-1); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "producer");
        Thread consumer = new Thread(() -> {
            try { for (int v; (v = buffer.take()) != -1; ) consumedSum[0] += v; }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "consumer");
        producer.start(); consumer.start();
        producer.join(10_000); consumer.join(10_000);
        System.out.println("consumed sum    = " + consumedSum[0] + " (expected " + ((long) n * (n + 1) / 2) + ")");
    }
}