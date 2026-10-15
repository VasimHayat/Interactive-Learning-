public class ControlFlow {
    public static void main(String[] args) {
        String[] skus   = { "BOLT-7", "NUT-3", "CAM-9", "ROD-4", "PIN-2" };
        int[]    onHand = {       12,       0,       7,       0,      31 };
        int      needed = 2;
        int      picked = 0;

        for (int i = 0; i < skus.length; i++) {
            if (onHand[i] == 0) {
                System.out.println("skip  " + skus[i] + " (out of stock)");
                continue;
            }
            if (picked == needed) {
                System.out.println("stop  - picked enough");
                break;
            }
            System.out.println("pick  " + skus[i]);
            picked++;
        }
        System.out.println("picked = " + picked);

        for (String sku : new String[] { "BOLT-7", "CAM-9", "ZZZ-1" }) {
            String bin = switch (sku.substring(0, 3)) {
                case "BOL" -> "A1";
                case "CAM" -> "B2";
                default    -> "UNSORTED";
            };
            System.out.println(sku + " -> bin " + bin);
        }
    }
}