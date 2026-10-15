import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class LostUpdate {
    static int plain;                                   // plain++ is load, add, store: three steps
    static final AtomicInteger atomic = new AtomicInteger();
    static final int PICKERS = 4, PER_PICKER = 1_000_000;

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch go = new CountDownLatch(1);      // a start gate, so all four run at once
        Thread[] pickers = new Thread[PICKERS];
        for (int t = 0; t < PICKERS; t++) {
            pickers[t] = new Thread(() -> {
                try { go.await(); } catch (InterruptedException e) { return; }
                for (int i = 0; i < PER_PICKER; i++) {
                    plain++;                            // RACE: another thread can store between our load and our store
                    atomic.incrementAndGet();           // one indivisible read-modify-write
                }
            }, "picker-" + t);
            pickers[t].start();
        }
        go.countDown();
        for (Thread p : pickers) p.join();

        int expected = PICKERS * PER_PICKER;
        System.out.println("expected               : " + expected);
        System.out.println("plain int lost updates : " + (plain < expected));
        System.out.println("AtomicInteger exact    : " + (atomic.get() == expected));
    }
}