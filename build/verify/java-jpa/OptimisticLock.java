import jakarta.persistence.*;

public class OptimisticLock {

    @Entity(name = "StockLevel")
    @Table(name = "stock_level")
    public static class StockLevel {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE)
        private Long id;
        @Column(nullable = false, unique = true, length = 16)
        private String sku;
        private int onHand;
        @Version
        private long version;                    // Hibernate owns this field: never set it yourself

        protected StockLevel() {}
        StockLevel(String sku, int onHand) { this.sku = sku; this.onHand = onHand; }
        Long id() { return id; }
        int onHand() { return onHand; }
        long version() { return version; }
        void pick(int qty) { onHand -= qty; }
        @Override public String toString() { return sku + " onHand=" + onHand + " v" + version; }
    }

    static EntityManagerFactory emf() {
        return new PersistenceConfiguration("warehouse")
            .managedClass(StockLevel.class)
            .property(PersistenceConfiguration.JDBC_URL, "jdbc:h2:mem:OptimisticLock;DB_CLOSE_DELAY=-1")
            .property(PersistenceConfiguration.JDBC_USER, "sa")
            .property(PersistenceConfiguration.JDBC_PASSWORD, "")
            .property(PersistenceConfiguration.SCHEMAGEN_DATABASE_ACTION, "drop-and-create")
            .property("hibernate.show_sql", "true")
            .createEntityManagerFactory();
    }

    static void section(String title) { System.out.println("-- " + title + " --"); }

    public static void main(String[] args) {
        try (EntityManagerFactory emf = emf()) {
            Long id;
            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                StockLevel s = new StockLevel("BOLT-7", 10);
                em.persist(s);
                em.getTransaction().commit();
                id = s.id();
                System.out.println("seeded " + s);
            }

            section("two pickers load the same row");
            EntityManager picker1 = emf.createEntityManager();
            EntityManager picker2 = emf.createEntityManager();
            picker1.getTransaction().begin();
            picker2.getTransaction().begin();
            StockLevel copy1 = picker1.find(StockLevel.class, id);
            StockLevel copy2 = picker2.find(StockLevel.class, id);
            System.out.println("picker1 sees " + copy1);
            System.out.println("picker2 sees " + copy2);

            section("picker1 commits first: UPDATE ... where id=? and version=?");
            copy1.pick(1);
            picker1.getTransaction().commit();
            System.out.println("picker1 committed " + copy1);

            section("picker2 commits on a stale version");
            copy2.pick(2);
            try {
                picker2.getTransaction().commit();
                System.out.println("(unreachable)");
            } catch (RollbackException e) {
                System.out.println("commit failed: " + e.getClass().getSimpleName()
                    + " caused by " + e.getCause().getClass().getSimpleName());
            }
            picker1.close();
            picker2.close();

            try (EntityManager em = emf.createEntityManager()) {
                System.out.println("database row is " + em.find(StockLevel.class, id)
                    + " (picker1's write survived, picker2's was refused, nothing was lost)");
            }

            section("OPTIMISTIC_FORCE_INCREMENT: bump the version on a read");
            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                StockLevel s = em.find(StockLevel.class, id, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
                em.getTransaction().commit();
                System.out.println("after commit " + s);
            }

            section("PESSIMISTIC_WRITE: the database locks the row until commit");
            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                StockLevel s = em.find(StockLevel.class, id, LockModeType.PESSIMISTIC_WRITE);
                s.pick(3);
                em.getTransaction().commit();
                System.out.println("after commit " + s);
            }
        }
    }
}