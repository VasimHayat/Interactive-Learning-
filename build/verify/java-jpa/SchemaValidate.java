import jakarta.persistence.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class SchemaValidate {

    /* Version 1 of the mapping: what the database was created from. */
    @Entity(name = "StockLevel")
    @Table(name = "stock_level")
    public static class StockLevel {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE) private Long id;
        @Column(nullable = false, unique = true, length = 16) private String sku;
        private int onHand;
        protected StockLevel() {}
        StockLevel(String sku, int onHand) { this.sku = sku; this.onHand = onHand; }
    }

    /* Version 2 of the mapping: a developer added a field — but nobody migrated the table. */
    @Entity(name = "StockLevelV2")
    @Table(name = "stock_level")
    public static class StockLevelV2 {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE) private Long id;
        @Column(nullable = false, unique = true, length = 16) private String sku;
        private int onHand;
        private int reorderPoint;          // new column, never added to the schema
        protected StockLevelV2() {}
    }

    static PersistenceConfiguration base(Class<?> entity) {
        return new PersistenceConfiguration("warehouse")
            .managedClass(entity)
            .property(PersistenceConfiguration.JDBC_URL, "jdbc:h2:mem:SchemaValidate;DB_CLOSE_DELAY=-1")
            .property(PersistenceConfiguration.JDBC_USER, "sa")
            .property(PersistenceConfiguration.JDBC_PASSWORD, "")
            .property("hibernate.show_sql", "true");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("-- 1. generate the schema from the v1 mapping, and export the DDL to a script --");
        Path script = Path.of("schema.sql");
        Files.deleteIfExists(script);
        var create = base(StockLevel.class)
            .property(PersistenceConfiguration.SCHEMAGEN_DATABASE_ACTION, "drop-and-create")
            .property(PersistenceConfiguration.SCHEMAGEN_SCRIPTS_ACTION, "create")
            .property("jakarta.persistence.schema-generation.scripts.create-target", script.toString());
        try (EntityManagerFactory emf = create.createEntityManagerFactory();
             EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();
            em.persist(new StockLevel("BOLT-7", 120));
            em.getTransaction().commit();
        }
        System.out.println("- schema.sql, the starting point for a hand-written migration -");
        for (String line : Files.readAllLines(script)) System.out.println("  " + line);
        Files.delete(script);

        System.out.println("-- 2. start again with 'none' + validate against the v1 mapping: fine --");
        var validateV1 = base(StockLevel.class)
            .property("hibernate.hbm2ddl.auto", "validate");
        try (EntityManagerFactory emf = validateV1.createEntityManagerFactory();
             EntityManager em = emf.createEntityManager()) {
            List<?> rows = em.createQuery("select s.sku from StockLevel s").getResultList();
            System.out.println("validated, rows = " + rows);
        }

        System.out.println("-- 3. the v2 mapping against the unmigrated table: bootstrap refuses --");
        var validateV2 = base(StockLevelV2.class)
            .property("hibernate.hbm2ddl.auto", "validate");
        EntityManagerFactory never = validateV2.createEntityManagerFactory();   // throws SchemaManagementException
        never.close();
        System.out.println("unreachable");
    }
}