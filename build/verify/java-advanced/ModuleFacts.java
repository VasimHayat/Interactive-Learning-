import java.lang.module.ModuleDescriptor;

public class ModuleFacts {
    public static void main(String[] args) throws Exception {
        Module base = Object.class.getModule();
        Module mine = ModuleFacts.class.getModule();

        System.out.println("java.lang.Object lives in     = " + base.getName());
        System.out.println("this class's module is named  = " + mine.isNamed());
        System.out.println("java.sql.Driver lives in      = " + Class.forName("java.sql.Driver").getModule().getName());
        System.out.println("boot layer has java.base      = " + ModuleLayer.boot().findModule("java.base").isPresent());
        System.out.println("java.base exports java.lang   = " + base.isExported("java.lang"));
        System.out.println("java.base exports jdk.internal.misc = " + base.isExported("jdk.internal.misc"));
        System.out.println("unnamed module reads java.base = " + mine.canRead(base));

        ModuleDescriptor d = base.getDescriptor();
        System.out.println("java.base requires anything   = " + !d.requires().isEmpty());
        System.out.println("java.base is open             = " + d.isOpen());
    }
}