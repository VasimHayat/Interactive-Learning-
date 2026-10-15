import java.util.Arrays;

public class ArrayBasics {
    public static void main(String[] args) {
        int[] qty = new int[4];
        System.out.println("defaults      = " + Arrays.toString(qty));

        String[] skus = { "BOLT-7", "NUT-3", "CAM-9" };
        System.out.println("length        = " + skus.length);
        System.out.println("last element  = " + skus[skus.length - 1]);

        int[][] weekly = new int[3][];
        weekly[0] = new int[] { 5, 3 };
        weekly[1] = new int[] { 9 };
        weekly[2] = new int[] { 1, 1, 4 };
        System.out.println("jagged        = " + Arrays.deepToString(weekly));

        int[] grown = Arrays.copyOf(qty, 6);
        System.out.println("copyOf(6)     = " + Arrays.toString(grown));

        String[] a = { "BOLT-7" };
        String[] b = { "BOLT-7" };
        System.out.println("a == b        -> " + (a == b));
        System.out.println("a.equals(b)   -> " + a.equals(b));
        System.out.println("Arrays.equals -> " + Arrays.equals(a, b));
    }
}