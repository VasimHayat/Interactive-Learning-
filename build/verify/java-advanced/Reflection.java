import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;

public class Reflection {

    interface PriceSource {
        int priceInCents(String sku);
        String name();
    }

    record Product(String sku, int priceInCents) { }

    static class Vault {
        private final String secret = "rotate-me";
        private volatile int stock = 10;

        private static final VarHandle STOCK;
        static {
            try {
                STOCK = MethodHandles.lookup().findVarHandle(Vault.class, "stock", int.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
        boolean reserve(int expected, int remaining) { return STOCK.compareAndSet(this, expected, remaining); }
    }

    public static void main(String[] args) throws Throwable {
        System.out.println("-- inspecting a record --");
        Class<Product> c = Product.class;
        System.out.println("isRecord         = " + c.isRecord() + ", final = " + Modifier.isFinal(c.getModifiers()));
        System.out.println("components       = " + Arrays.stream(c.getRecordComponents()).map(RecordComponent::getName).sorted().toList());
        System.out.println("declared methods = " + Arrays.stream(c.getDeclaredMethods()).filter(m -> !m.isSynthetic()).map(Method::getName).sorted().toList());

        System.out.println("-- constructing and invoking by name --");
        Product p = c.getDeclaredConstructor(String.class, int.class).newInstance("BOLT-7", 80);
        Method sku = c.getMethod("sku");
        System.out.println("sku()            = " + sku.invoke(p) + "  (public: " + Modifier.isPublic(sku.getModifiers()) + ")");

        System.out.println("-- private access: your own class yes, java.base no --");
        Field secret = Vault.class.getDeclaredField("secret");
        secret.setAccessible(true);
        System.out.println("Vault.secret     = " + secret.get(new Vault()));
        try {
            Field value = String.class.getDeclaredField("value");
            value.setAccessible(true);
            System.out.println("opened java.lang.String.value");
        } catch (InaccessibleObjectException e) {
            System.out.println("String.value     : blocked by strong encapsulation");
        }

        System.out.println("-- a dynamic proxy --");
        PriceSource source = (PriceSource) Proxy.newProxyInstance(
                Reflection.class.getClassLoader(), new Class<?>[] { PriceSource.class },
                (self, method, a) -> {
                    System.out.println("  intercepted " + method.getName() + (a == null ? "()" : Arrays.toString(a)));
                    return method.getName().equals("name") ? "proxy" : 80;
                });
        System.out.println("proxy price      = " + source.priceInCents("BOLT-7"));
        System.out.println("proxy name       = " + source.name());

        System.out.println("-- method handles and var handles --");
        MethodHandle price = MethodHandles.lookup()
                .findVirtual(Product.class, "priceInCents", MethodType.methodType(int.class));
        int cents = (int) price.invokeExact(p);
        System.out.println("invokeExact      = " + cents);
        Vault vault = new Vault();
        System.out.println("CAS 10 -> 7      = " + vault.reserve(10, 7));
        System.out.println("CAS 10 -> 3      = " + vault.reserve(10, 3) + "  (stock is now " + vault.stock + ")");
    }
}