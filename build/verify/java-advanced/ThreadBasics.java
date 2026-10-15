import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ThreadBasics {
    // shared heap: every thread sees the same list, so it must be a thread-safe one
    static final List<String> results = Collections.synchronizedList(new ArrayList<>());
    static volatile boolean flagRestored;

    // each call lives in a frame on the CALLING thread's stack: three threads can
    // recurse through this at once and n never interferes between them
    static int depth(int n) {
        if (n == 0) return 0;
        return 1 + depth(n - 1);
    }

    static class Picker extends Thread {                               // 1. subclass Thread
        private final String sku;
        Picker(String sku) { super("picker-" + sku); this.sku = sku; }
        @Override public void run() { results.add(sku + " depth=" + depth(500)); }
    }

    public static void main(String[] args) throws InterruptedException {
        Thread a = new Picker("BOLT-7");
        Thread b = new Thread(() -> results.add("NUT-3 depth=" + depth(700)), "picker-NUT-3");   // 2. Runnable
        Thread c = Thread.ofPlatform().name("picker-CAM-9")                                       // 3. builder (Java 21)
                         .start(() -> results.add("CAM-9 depth=" + depth(900)));              //    already started

        Thread housekeeping = new Thread(() -> {
            while (true) { try { Thread.sleep(50); } catch (InterruptedException e) { return; } }
        }, "housekeeping");
        housekeeping.setDaemon(true);          // a daemon does not keep the JVM alive
        housekeeping.start();

        Thread sleeper = new Thread(() -> {
            try { Thread.sleep(10_000); }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();                    // restore the flag for whoever is above us
                flagRestored = Thread.currentThread().isInterrupted();
            }
        }, "sleeper");
        sleeper.start();
        sleeper.interrupt();                   // cooperative: sleep throws, the loop above decides what to do

        a.start(); b.start();
        a.join(); b.join(); c.join(); sleeper.join();                  // nothing below runs until all four have finished

        List<String> sorted = new ArrayList<>(results);
        Collections.sort(sorted);
        System.out.println("all joined           : " + (!a.isAlive() && !b.isAlive() && !c.isAlive()));
        System.out.println("results (sorted)     : " + sorted);
        System.out.println("states after join    : " + a.getState() + " " + b.getState() + " " + c.getState());
        System.out.println("interrupt restored   : " + flagRestored);
        System.out.println("daemon still running : " + housekeeping.isAlive() + " (the JVM exits regardless)");
        System.out.println("this thread          : " + Thread.currentThread().getName());
    }
}