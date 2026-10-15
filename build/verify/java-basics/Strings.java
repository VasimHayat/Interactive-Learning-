public class Strings {
    public static void main(String[] args) {
        String a = "BOLT-7";
        String b = "BOLT-7";
        String c = new String("BOLT-7");
        String d = c.intern();

        System.out.println("a == b        -> " + (a == b));
        System.out.println("a == c        -> " + (a == c));
        System.out.println("a.equals(c)   -> " + a.equals(c));
        System.out.println("a == d        -> " + (a == d));

        String s = "BOLT";
        s.concat("-7");
        System.out.println("after concat  : " + s);
        System.out.println("reassigned    : " + s.concat("-7"));
    }
}