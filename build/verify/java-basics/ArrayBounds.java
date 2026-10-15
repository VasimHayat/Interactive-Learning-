public class ArrayBounds {
    public static void main(String[] args) {
        String[] skus = { "BOLT-7", "NUT-3", "CAM-9" };
        for (int i = 0; i <= skus.length; i++) {
            System.out.println(i + " -> " + skus[i]);
        }
    }
}