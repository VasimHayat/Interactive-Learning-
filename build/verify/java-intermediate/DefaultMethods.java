public class DefaultMethods {

    interface Priced {
        int priceInCents();
        default String describe() { return "priced at " + priceInCents() + "c"; }
        static Priced of(int cents) { return () -> cents; }        // static: called as Priced.of(...)
    }

    interface Shippable {
        int shippingInCents();
        default String describe() { return "ships for " + shippingInCents() + "c"; }
        default int totalInCents(int price) { return price + shippingInCents() + handling(); }
        private int handling() { return 50; }                        // private: shared by defaults only
    }

    // rule 3: two unrelated defaults with one signature -> the class MUST override
    static class Bolt implements Priced, Shippable {
        public int priceInCents() { return 80; }
        public int shippingInCents() { return 150; }
        @Override public String describe() {
            return Priced.super.describe() + ", " + Shippable.super.describe();
        }
    }

    interface Fragile extends Shippable {
        @Override default String describe() { return "fragile: " + Shippable.super.describe(); }
    }
    // rule 2: the more specific interface wins -> Fragile's default, not Shippable's
    static class Lens implements Shippable, Fragile {
        public int shippingInCents() { return 900; }
    }

    static class Base { public String describe() { return "a plain class method"; } }
    // rule 1: a superclass method always beats an interface default
    static class Girder extends Base implements Priced {
        public int priceInCents() { return 120_000; }
    }

    public static void main(String[] args) {
        System.out.println("Bolt:   " + new Bolt().describe());
        System.out.println("Lens:   " + new Lens().describe());
        System.out.println("Girder: " + new Girder().describe());
        System.out.println("total:  " + new Lens().totalInCents(1_000));
        System.out.println("static: " + Priced.of(25).describe());
    }
}