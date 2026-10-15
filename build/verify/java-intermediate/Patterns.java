public class Patterns {

    enum Category { FASTENER, STRUCTURAL, TOOLING }

    static String classify(Object o) {
        return switch (o) {
            case null                 -> "null, handled as a case: no NullPointerException";
            case Integer i when i < 0 -> "negative int " + i;
            case Integer i            -> "int " + i;
            case String s             -> "string of length " + s.length();
            case int[] arr            -> "int array of " + arr.length;
            default                   -> "something else: " + o.getClass().getSimpleName();
        };
    }

    static int leadTimeDays(Category c) {
        return switch (c) {                 // exhaustive over the enum: no default needed
            case FASTENER   -> 1;
            case STRUCTURAL -> 14;
            case TOOLING    -> {            // a block body must yield its value
                int base = 3;
                yield base * 2;
            }
        };
    }

    static String shippingBand(String sku) {
        switch (sku) {                      // the statement form: no value, no exhaustiveness check
            case "BOLT-7", "NUT-3" -> { return "small"; }
            case "GIRD-1"          -> { return "freight"; }
            default                -> { return "standard"; }
        }
    }

    static String label(Object o) {
        if (!(o instanceof String s)) return "not a string";
        return "string " + s.toUpperCase();   // s is in scope: the compiler saw the early return
    }

    public static void main(String[] args) {
        System.out.println(classify(null));
        System.out.println(classify(-4));
        System.out.println(classify(42));
        System.out.println(classify("BOLT-7"));
        System.out.println(classify(new int[3]));
        System.out.println(classify(3.5));
        for (Category c : Category.values()) System.out.println(c + " -> " + leadTimeDays(c) + " days");
        System.out.println(shippingBand("NUT-3") + ", " + shippingBand("GIRD-1") + ", " + shippingBand("CAM-9"));
        System.out.println(label("rod-4") + " / " + label(7));
    }
}