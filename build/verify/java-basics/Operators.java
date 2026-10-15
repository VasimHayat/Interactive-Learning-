public class Operators {
    public static void main(String[] args) {
        System.out.println("7 / 2        = " + (7 / 2));
        System.out.println("-7 / 2       = " + (-7 / 2));
        System.out.println("-7 % 2       = " + (-7 % 2));
        System.out.println("floorMod     = " + Math.floorMod(-7, 2));
        System.out.println("7 / 2.0      = " + (7 / 2.0));

        System.out.println("2 + 3 + \"!\"  = " + (2 + 3 + "!"));
        System.out.println("\"!\" + 2 + 3  = " + ("!" + 2 + 3));

        int i = 2;
        System.out.println("i++ + ++i    = " + (i++ + ++i));
        System.out.println("i is now     = " + i);

        int total = 0, count = 0;
        System.out.println("avg guarded  = " + (count > 0 && total / count > 5));
    }
}