import java.io.IOException;
import java.io.InputStream;

public class Loaders {

    /* Follows standard delegation and, once every parent has given up,
       refuses — so the fall-through is visible. */
    static class Strict extends ClassLoader {
        Strict(ClassLoader parent) { super(parent); }
        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            System.out.println("  Strict.findClass(" + name + "): parents had no answer");
            throw new ClassNotFoundException(name);
        }
    }

    public static class Twin { public Twin() { } }   // public: the copy lives in a different runtime package

    /* Child-first for one class: defines its own copy from the bytes on the
       class path instead of asking its parent. */
    static class ChildFirst extends ClassLoader {
        ChildFirst(ClassLoader parent) { super(parent); }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals("Loaders$Twin")) {
                try (InputStream in = Loaders.class.getResourceAsStream("Loaders$Twin.class")) {
                    byte[] bytes = in.readAllBytes();
                    return defineClass(name, bytes, 0, bytes.length);
                } catch (IOException e) {
                    throw new ClassNotFoundException(name, e);
                }
            }
            return super.loadClass(name, resolve);
        }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader app = Loaders.class.getClassLoader();
        System.out.println("-- the built-in hierarchy --");
        System.out.println("String's loader is bootstrap (null) : " + (String.class.getClassLoader() == null));
        System.out.println("java.sql.Driver's loader           : " + java.sql.Driver.class.getClassLoader().getName());
        System.out.println("Loaders' loader                    : " + app.getName());
        System.out.println("its parent                         : " + app.getParent().getName());
        System.out.println("the platform loader's parent       : " + app.getParent().getParent());

        System.out.println("-- delegation: parents are asked first --");
        Strict strict = new Strict(app);
        Class<?> s = strict.loadClass("java.lang.String");
        System.out.println("String via Strict is the same Class : " + (s == String.class));
        Class<?> me = strict.loadClass("Loaders");
        System.out.println("Loaders via Strict is the same Class: " + (me == Loaders.class)
                + " (defined by " + me.getClassLoader().getName() + ")");
        try {
            strict.loadClass("com.acme.Missing");
        } catch (ClassNotFoundException e) {
            System.out.println("ClassNotFoundException: " + e.getMessage());
        }

        System.out.println("-- identity is (loader, name): the same bytes loaded twice --");
        Class<?> twin2 = new ChildFirst(app).loadClass("Loaders$Twin");
        System.out.println("same name      : " + twin2.getName().equals(Twin.class.getName()));
        System.out.println("same Class     : " + (twin2 == Twin.class));
        Object copy = twin2.getDeclaredConstructor().newInstance();
        System.out.println("instanceof Twin: " + (copy instanceof Twin));
        System.out.println("named module   : " + Loaders.class.getModule().isNamed()
                + "; String lives in " + String.class.getModule().getName());
    }
}