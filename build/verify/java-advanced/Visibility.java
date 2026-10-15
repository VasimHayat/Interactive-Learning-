import java.util.concurrent.CountDownLatch;

public class Visibility {
    static volatile boolean stop;      // volatile: a write is guaranteed to become visible, promptly
    static int reserved;               // plain: visible to another thread only across a happens-before edge
    static int payload;                // plain, but published under a volatile write below
    static volatile int ready;

    public static void main(String[] args) throws InterruptedException {
        // 1. a volatile flag stops a spinning reader
        CountDownLatch spinning = new CountDownLatch(1);
        Thread reader = new Thread(() -> {
            spinning.countDown();
            while (!stop) Thread.onSpinWait();         // without volatile the JIT may hoist this read out of the loop
        }, "reader");
        reader.start();
        spinning.await();
        stop = true;
        reader.join(5_000);
        System.out.println("reader stopped         : " + !reader.isAlive());

        // 2. Thread.join is a happens-before edge: the plain write is visible after it
        Thread writer = new Thread(() -> reserved = 42, "writer");
        writer.start();
        writer.join();
        System.out.println("seen after join        : " + reserved);

        // 3. piggybacking: a plain write BEFORE a volatile write is visible AFTER the volatile read
        Thread producer = new Thread(() -> { payload = 7; ready = 1; }, "producer");
        producer.start();
        while (ready == 0) Thread.onSpinWait();
        System.out.println("payload after volatile : " + payload);
        producer.join();
    }
}