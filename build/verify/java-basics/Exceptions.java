public class Exceptions {

    static class Resource implements AutoCloseable {
        private final String name;
        Resource(String name) { this.name = name; System.out.println("open  " + name); }
        void use() { System.out.println("use   " + name); }
        @Override public void close() { System.out.println("close " + name); }
    }

    static int sneaky() {
        try {
            return 1;
        } finally {
            System.out.println("finally runs before the caller sees the value");
        }
    }

    static int stockFor(String sku) {
        if (sku == null) throw new IllegalArgumentException("sku must not be null");
        return 42;
    }

    public static void main(String[] args) {
        try (Resource a = new Resource("A"); Resource b = new Resource("B")) {
            a.use();
            b.use();
        }

        System.out.println("sneaky() = " + sneaky());

        try {
            stockFor(null);
        } catch (IllegalArgumentException e) {
            System.out.println("caught: " + e.getMessage());
        } finally {
            System.out.println("finally always runs");
        }
    }
}