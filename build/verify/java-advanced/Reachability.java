import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public class Reachability {

    static final class Pallet {
        final String sku;
        Pallet(String sku) { this.sku = sku; }
    }

    static Pallet pinned;                                   // a static field is a GC root

    // System.gc() is a request, not a command. Ask repeatedly, with a bound, and report the fact.
    static boolean gcUntil(BooleanSupplier condition) throws InterruptedException {
        for (int i = 0; i < 50 && !condition.getAsBoolean(); i++) {
            System.gc();
            Thread.sleep(20);
        }
        return condition.getAsBoolean();
    }

    public static void main(String[] args) throws Exception {
        // 1. Weak: cleared as soon as nothing strong reaches the object.
        ReferenceQueue<Pallet> queue = new ReferenceQueue<>();
        Pallet p = new Pallet("BOLT-7");
        WeakReference<Pallet> weak = new WeakReference<>(p, queue);
        System.out.println("weak while a strong ref exists = " + (weak.get() != null));
        p = null;                                           // the last strong reference is gone
        System.out.println("weak cleared after gc          = " + gcUntil(() -> weak.get() == null));
        Reference<? extends Pallet> notified = queue.remove(5_000);
        System.out.println("reference enqueued             = " + (notified == weak));

        // 2. A root keeps it alive no matter how often you ask.
        pinned = new Pallet("GIRD-1");
        WeakReference<Pallet> weakPinned = new WeakReference<>(pinned);
        for (int i = 0; i < 3; i++) System.gc();
        System.out.println("rooted object still reachable  = " + (weakPinned.get() != null));

        // 3. Soft: cleared only under memory pressure, so an idle gc leaves it alone.
        SoftReference<Pallet> soft = new SoftReference<>(new Pallet("CAM-9"));
        for (int i = 0; i < 3; i++) System.gc();
        System.out.println("soft survives an idle gc       = " + (soft.get() != null));

        // 4. WeakHashMap: the entry goes when the key goes.
        Map<Pallet, Integer> onHand = new WeakHashMap<>();
        Pallet key = new Pallet("NUT-3");
        onHand.put(key, 1200);
        System.out.println("entries while key is held      = " + onHand.size());
        key = null;
        System.out.println("entries after key dropped + gc = " + (gcUntil(() -> onHand.isEmpty()) ? 0 : onHand.size()));

        // 5. Cleaner: the replacement for finalize(). The action must not capture the object.
        Cleaner cleaner = Cleaner.create();
        CountDownLatch ran = new CountDownLatch(1);
        Runnable release = ran::countDown;                  // captures the latch, not the Pallet
        Pallet handle = new Pallet("ROD-4");
        Cleaner.Cleanable cleanable = cleaner.register(handle, release);
        handle = null;
        gcUntil(() -> ran.getCount() == 0);
        System.out.println("cleaner ran after gc           = " + ran.await(5, TimeUnit.SECONDS));

        CountDownLatch ran2 = new CountDownLatch(1);
        Cleaner.Cleanable explicit = cleaner.register(new Pallet("PIN-2"), ran2::countDown);
        explicit.clean();                                   // deterministic release: call it yourself, from close()
        System.out.println("explicit clean() ran at once   = " + (ran2.getCount() == 0));
        explicit.clean();                                   // idempotent
        cleanable.clean();                                  // already run by the cleaner thread: also a no-op
        System.out.println("repeated clean() is a no-op    = " + (ran2.getCount() == 0));
    }
}