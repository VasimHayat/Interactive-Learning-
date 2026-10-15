public class Dispatch {

    static abstract class Product {
        private final String sku;               // private: nobody outside can corrupt it
        private int priceInCents;

        Product(String sku, int priceInCents) {
            if (sku == null || sku.isBlank()) throw new IllegalArgumentException("sku required");
            if (priceInCents < 0) throw new IllegalArgumentException("price must not be negative");
            this.sku = sku;
            this.priceInCents = priceInCents;
        }

        String sku() { return sku; }
        int priceInCents() { return priceInCents; }

        abstract int shippingInCents();          // no body: every subclass must decide

        int totalInCents() { return priceInCents + shippingInCents(); }
    }

    static class Bolt extends Product {
        Bolt(int priceInCents) { super("BOLT-7", priceInCents); }
        @Override int shippingInCents() { return 150; }
    }

    static class Girder extends Product {
        Girder(int priceInCents) { super("GIRD-1", priceInCents); }
        @Override int shippingInCents() { return 9_500; }
        @Override int totalInCents() { return super.totalInCents() + 250; }  // crane surcharge
    }

    public static void main(String[] args) {
        Product[] cart = { new Bolt(80), new Girder(120_000) };
        for (Product p : cart) {
            System.out.printf("%-8s price=%6d ship=%5d total=%6d%n",
                    p.sku(), p.priceInCents(), p.shippingInCents(), p.totalInCents());
        }

        Product p = new Girder(100);
        System.out.println("static type is Product, runtime type is " + p.getClass().getSimpleName());
    }
}