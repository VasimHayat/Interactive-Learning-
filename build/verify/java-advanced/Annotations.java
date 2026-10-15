import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Annotations {

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.TYPE, ElementType.METHOD })
    @interface Audited {
        String by();
        int level() default 1;
    }

    @Retention(RetentionPolicy.SOURCE)             // discarded by javac
    @Target(ElementType.METHOD)
    @interface Draft { }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT })
    @interface NotBlank { }

    @Repeatable(Tags.class)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Tag { String value(); }

    @Retention(RetentionPolicy.RUNTIME)
    @interface Tags { Tag[] value(); }

    @Audited(by = "ops", level = 3)
    @Tag("inventory") @Tag("critical")
    static class InventoryService {
        @Audited(by = "finance") void reprice() { }
        @Draft void experimental() { }
        void plain() { }
    }

    record Part(@NotBlank String sku, @NotBlank String name, int qty) { }

    /* A tiny validation framework: the metadata is on the type, the behaviour is here. */
    static List<String> validate(Object target) throws IllegalAccessException {
        List<String> problems = new ArrayList<>();
        Field[] fields = target.getClass().getDeclaredFields();
        Arrays.sort(fields, (a, b) -> a.getName().compareTo(b.getName()));
        for (Field f : fields) {
            if (!f.isAnnotationPresent(NotBlank.class)) continue;
            f.setAccessible(true);
            Object v = f.get(target);
            if (v == null || v.toString().isBlank()) problems.add(f.getName() + " must not be blank");
        }
        return problems;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("-- reading annotation values --");
        Audited onType = InventoryService.class.getAnnotation(Audited.class);
        System.out.println("type: by=" + onType.by() + " level=" + onType.level());

        Method[] methods = InventoryService.class.getDeclaredMethods();
        Arrays.sort(methods, (a, b) -> a.getName().compareTo(b.getName()));
        for (Method m : methods) {
            Audited a = m.getAnnotation(Audited.class);
            System.out.println(m.getName() + "(): audited=" + (a != null ? "by " + a.by() + " level " + a.level() : "no")
                    + ", @Draft visible=" + m.isAnnotationPresent(Draft.class));
        }

        System.out.println("-- repeatable --");
        Tag[] tags = InventoryService.class.getAnnotationsByType(Tag.class);
        System.out.println("tags = " + Arrays.stream(tags).map(Tag::value).sorted().toList()
                + " (container present: " + InventoryService.class.isAnnotationPresent(Tags.class) + ")");

        System.out.println("-- annotations drive a validator --");
        System.out.println("valid   : " + validate(new Part("BOLT-7", "hex bolt", 3)));
        System.out.println("invalid : " + validate(new Part("BOLT-7", "   ", 3)));
    }
}