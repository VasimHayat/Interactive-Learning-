public class Overloads {
    static String f(int x)     { return "int"; }
    static String f(long x)    { return "long"; }
    static String f(Integer x) { return "Integer"; }
    static String f(Object x)  { return "Object"; }
    static String f(int... x)  { return "varargs"; }

    public static void main(String[] args) {
        byte b = 1;
        System.out.println("f(1)           -> " + f(1));
        System.out.println("f(b)  byte     -> " + f(b));
        System.out.println("f(1L)          -> " + f(1L));
        System.out.println("f((Integer) 1) -> " + f((Integer) 1));
        System.out.println("f(1, 2)        -> " + f(1, 2));
        System.out.println("f(\"x\")         -> " + f("x"));
    }
}