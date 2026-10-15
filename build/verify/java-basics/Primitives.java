public class Primitives {
    public static void main(String[] args) {
        int max = Integer.MAX_VALUE;
        System.out.println("MAX_VALUE     = " + max);
        System.out.println("MAX_VALUE + 1 = " + (max + 1));

        Integer a = 127, b = 127;
        Integer c = 128, d = 128;
        System.out.println("127 == 127    -> " + (a == b));
        System.out.println("128 == 128    -> " + (c == d));
        System.out.println("128 .equals   -> " + c.equals(d));

        System.out.println("0.1 + 0.2     = " + (0.1 + 0.2));
        System.out.println("as BigDecimal = " + new java.math.BigDecimal("0.1").add(new java.math.BigDecimal("0.2")));
    }
}