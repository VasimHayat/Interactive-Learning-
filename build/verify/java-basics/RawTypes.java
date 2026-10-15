import java.util.ArrayList;
import java.util.List;

public class RawTypes {
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void main(String[] args) {
        List raw = new ArrayList();     // raw type: the compiler stops helping
        raw.add("BOLT-7");
        raw.add(42);                    // accepted without complaint

        for (Object o : raw) System.out.println("element: " + o);

        List<String> skus = raw;        // unchecked assignment, only a warning
        System.out.println("first : " + skus.get(0));
        System.out.println("second: " + skus.get(1));
    }
}