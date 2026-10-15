import java.io.*;

public class SerializeRecord {

    record Product(String sku, long priceInCents) implements Serializable {
        Product {
            if (sku == null || sku.isBlank()) throw new IllegalArgumentException("sku required");
            if (priceInCents < 0) throw new IllegalArgumentException("negative price");
            VALIDATIONS++;
        }
        static int VALIDATIONS = 0;
    }

    static final class Session implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String user;
        private transient String cachedToken;          // never written to the stream

        Session(String user, String token) { this.user = user; this.cachedToken = token; }
        String user() { return user; }
        String cachedToken() { return cachedToken; }
    }

    static final class Registry implements Serializable {
        private static final long serialVersionUID = 1L;
        static final Registry INSTANCE = new Registry();
        private Registry() {}
        // the stream carries a copy; readResolve swaps it for the real singleton
        private Object readResolve() throws ObjectStreamException { return INSTANCE; }
    }

    static byte[] write(Object o) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) { out.writeObject(o); }
        return bytes.toByteArray();
    }

    static Object read(byte[] data, ObjectInputFilter filter) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(data))) {
            if (filter != null) in.setObjectInputFilter(filter);
            return in.readObject();
        }
    }

    public static void main(String[] args) throws Exception {
        Product bolt = new Product("BOLT-7", 80);
        int before = Product.VALIDATIONS;
        Product copy = (Product) read(write(bolt), null);
        System.out.println("record equal after round trip   = " + bolt.equals(copy));
        System.out.println("record is a different instance  = " + (bolt != copy));
        System.out.println("canonical constructor ran again = " + (Product.VALIDATIONS == before + 1));

        Session s = (Session) read(write(new Session("vasim", "tok-123")), null);
        System.out.println("session user survived           = " + s.user());
        System.out.println("transient token after read      = " + s.cachedToken());

        Registry r = (Registry) read(write(Registry.INSTANCE), null);
        System.out.println("readResolve kept the singleton  = " + (r == Registry.INSTANCE));

        // an allow-list filter: only our own classes and the JDK types they need
        ObjectInputFilter allow = ObjectInputFilter.Config.createFilter(
                "SerializeRecord$Product;java.lang.*;java.util.*;!*");
        System.out.println("filtered read of Product        = " + ((Product) read(write(bolt), allow)).sku());
        try {
            read(write(new Session("x", "y")), allow);
            System.out.println("filtered read of Session        = allowed");
        } catch (InvalidClassException e) {
            System.out.println("filtered read of Session        = rejected (" + e.getMessage().replaceAll("\\s+", " ").trim() + ")");
        }
    }
}