import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;

public class VirtualThreads {
    public static void main(String[] args) throws Exception {
        LongAdder done = new LongAdder();
        LongAdder virtualInside = new LongAdder();

        long t0 = System.nanoTime();
        try (ExecutorService ex = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 10_000; i++) {
                ex.submit(() -> {
                    Thread.sleep(10);                               // a blocking call: the thread unmounts
                    if (Thread.currentThread().isVirtual()) virtualInside.increment();
                    done.increment();
                    return null;
                });
            }
        }                                                           // close() waits for every task
        long seconds = (System.nanoTime() - t0) / 1_000_000_000L;
        System.out.println("tasks completed       = " + done.sum());
        System.out.println("all ran virtual       = " + (virtualInside.sum() == 10_000));
        System.out.println("100 s of sleeps took under 30 s = " + (seconds < 30));

        Thread picker = Thread.ofVirtual().name("picker-", 0).unstarted(() -> { });
        System.out.println("isVirtual             = " + picker.isVirtual());
        System.out.println("isDaemon              = " + picker.isDaemon());
        System.out.println("name                  = " + picker.getName());
        System.out.println("priority              = " + picker.getPriority() + " (always NORM_PRIORITY)");
        System.out.println("platform isVirtual    = " + Thread.ofPlatform().unstarted(() -> { }).isVirtual());

        // ThreadLocal is per thread, virtual or not. That is what makes it expensive at a million threads.
        ThreadLocal<String> tenant = ThreadLocal.withInitial(() -> "unset");
        Thread vt = Thread.startVirtualThread(() -> tenant.set("warehouse-A"));
        vt.join();
        System.out.println("main sees ThreadLocal = " + tenant.get());

        // A j.u.c. lock parks the virtual thread and frees its carrier for someone else.
        ReentrantLock lock = new ReentrantLock();
        LongAdder counter = new LongAdder();
        try (ExecutorService ex = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 1_000; i++) {
                ex.submit(() -> { lock.lock(); try { counter.increment(); } finally { lock.unlock(); } });
            }
        }
        System.out.println("locked increments     = " + counter.sum());
    }
}