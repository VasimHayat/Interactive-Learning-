public class InventoryStart {
    public static void main(String[] args) {
        String sku = "BOLT-7";
        int onHand = 42;
        System.out.println("SKU " + sku + " has " + onHand + " units on hand.");
        System.out.println("Running on Java " + System.getProperty("java.specification.version"));
    }
}