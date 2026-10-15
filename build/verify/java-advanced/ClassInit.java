public class ClassInit {

    static int trace(String what, int value) {
        System.out.println("  init: " + what);
        return value;
    }

    static class Base {
        static int counter = trace("Base.counter", 1);
        static { trace("Base static block", 0); }
    }

    static class Derived extends Base {
        static int level = trace("Derived.level", 2);
        static { trace("Derived static block", 0); }
    }

    static class Lazy {
        static { trace("Lazy static block", 0); }
        static final int SIZE = 8;                 // compile-time constant: inlined by javac
        static int size = 8;                       // a real static field
    }

    static class ViaName {
        static { trace("ViaName static block", 0); }
    }

    static class Holder {
        static { trace("Holder static block", 0); }
        static final Object INSTANCE = new Object();
    }

    interface Config {
        int LIMIT = trace("Config.LIMIT", 10);     // not a constant: initialised on first use
    }

    static class Trap {
        static int first = Trap.second + 1;        // qualified name: legal, reads the DEFAULT 0
        static int second = 5;
    }

    static class Broken {
        static int value = 1 / zero();
        static int zero() { return 0; }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("-- a compile-time constant does not initialise its class --");
        System.out.println("Lazy.SIZE = " + Lazy.SIZE);
        System.out.println("Lazy.size = " + Lazy.size);

        System.out.println("-- superclass first, then subclass, textual order within each --");
        System.out.println("Derived.level = " + Derived.level);

        System.out.println("-- loadClass loads but does not initialise; Class.forName does --");
        ClassLoader app = ClassInit.class.getClassLoader();
        Class<?> loaded = app.loadClass("ClassInit$ViaName");
        System.out.println("loaded " + loaded.getSimpleName() + ", nothing printed yet");
        Class.forName("ClassInit$ViaName", false, app);
        System.out.println("forName(init=false): still nothing");
        Class.forName("ClassInit$ViaName");
        System.out.println("forName(): initialised");

        System.out.println("-- the holder idiom: lazy, thread-safe, no locks --");
        System.out.println("about to touch Holder.INSTANCE");
        Object o = Holder.INSTANCE;
        System.out.println("got instance: " + (o != null));

        System.out.println("-- an interface initialises when a non-constant field is read --");
        System.out.println("Config.LIMIT = " + Config.LIMIT);

        System.out.println("-- forward reference: a static reads a later static's default --");
        System.out.println("Trap.first = " + Trap.first + ", Trap.second = " + Trap.second);

        System.out.println("-- a failing initialiser: two different errors --");
        try {
            System.out.println(Broken.value);
        } catch (ExceptionInInitializerError e) {
            System.out.println("first use : " + e.getClass().getSimpleName()
                    + " caused by " + e.getCause().getClass().getSimpleName());
        }
        try {
            System.out.println(Broken.value);
        } catch (NoClassDefFoundError e) {
            System.out.println("second use: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}