public class InitOrder {

    static class Base {
        static { System.out.println("1. Base   static block"); }
        {        System.out.println("3. Base   instance initializer"); }
        Base()  { System.out.println("4. Base   constructor"); }
    }

    static class Derived extends Base {
        static { System.out.println("2. Derived static block"); }
        {        System.out.println("5. Derived instance initializer"); }
        Derived() { System.out.println("6. Derived constructor"); }
    }

    public static void main(String[] args) {
        System.out.println("-- before any instance exists --");
        new Derived();
        System.out.println("-- second instance --");
        new Derived();
    }
}