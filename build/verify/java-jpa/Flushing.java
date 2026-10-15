import jakarta.persistence.*;

public class Flushing {

    @Entity(name = "Product")
    @Table(name = "product")
    public static class Product {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE)
        private Long id;
        @Column(nullable = false, unique = true, length = 16)
        private String sku;
        private int priceInCents;
        protected Product() {}
        Product(String sku, int priceInCents) { this.sku = sku; this.priceInCents = priceInCents; }
        Long id() { return id; }
        void setPriceInCents(int c) { priceInCents = c; }
        @Override public String toString() { return "Product[" + id + ", " + sku + ", " + priceInCents + "c]"; }
    }

    static EntityManagerFactory emf() {
        return new PersistenceConfiguration("warehouse")
            .managedClass(Product.class)
            .property(PersistenceConfiguration.JDBC_URL, "jdbc:h2:mem:Flushing;DB_CLOSE_DELAY=-1")
            .property(PersistenceConfiguration.JDBC_USER, "sa")
            .property(PersistenceConfiguration.JDBC_PASSWORD, "")
            .property(PersistenceConfiguration.SCHEMAGEN_DATABASE_ACTION, "drop-and-create")
            .property("hibernate.show_sql", "true")
            .createEntityManagerFactory();
    }

    static long rowsInDatabase(EntityManagerFactory emf) {
        try (EntityManager em = emf.createEntityManager()) {          // a fresh context: sees only committed rows
            return ((Number) em.createNativeQuery("select count(*) from product").getSingleResult()).longValue();
        }
    }

    static void section(String title) { System.out.println("-- " + title + " --"); }

    public static void main(String[] args) {
        try (EntityManagerFactory emf = emf()) {

            section("1. a query flushes pending writes first, then rollback undoes them");
            try (EntityManager em = emf.createEntityManager()) {
                EntityTransaction tx = em.getTransaction();
                tx.begin();
                Product bolt = new Product("BOLT-7", 80);
                em.persist(bolt);                                            // no SQL yet: only scheduled
                System.out.println("persisted, id already assigned = " + bolt.id());
                long seen = em.createQuery("select count(p) from Product p", Long.class).getSingleResult();
                System.out.println("count inside the transaction = " + seen);
                tx.rollback();
                System.out.println("after rollback, em.contains(bolt) = " + em.contains(bolt));
            }
            System.out.println("rows committed = " + rowsInDatabase(emf));

            section("2. FlushModeType.COMMIT: the query does NOT see the pending insert");
            try (EntityManager em = emf.createEntityManager()) {
                em.setFlushMode(FlushModeType.COMMIT);
                EntityTransaction tx = em.getTransaction();
                tx.begin();
                em.persist(new Product("NUT-3", 25));
                long seen = em.createQuery("select count(p) from Product p", Long.class).getSingleResult();
                System.out.println("count inside the transaction = " + seen);
                tx.commit();                                                 // the insert happens here
            }
            System.out.println("rows committed = " + rowsInDatabase(emf));

            section("3. dirty checking: a changed field becomes an UPDATE at flush");
            try (EntityManager em = emf.createEntityManager()) {
                EntityTransaction tx = em.getTransaction();
                tx.begin();
                Product nut = em.createQuery("select p from Product p where p.sku = 'NUT-3'", Product.class).getSingleResult();
                nut.setPriceInCents(30);                                     // no em call at all
                System.out.println("field changed, nothing written yet");
                tx.commit();                                                 // flush compares snapshot → UPDATE
                System.out.println("committed " + nut);
            }

            section("4. a failure marks the transaction rollback-only");
            try (EntityManager em = emf.createEntityManager()) {
                EntityTransaction tx = em.getTransaction();
                tx.begin();
                em.persist(new Product("CAM-9", 2_300));
                try {
                    if (true) throw new IllegalStateException("price feed unavailable");
                } catch (IllegalStateException e) {
                    tx.setRollbackOnly();
                    System.out.println("caught '" + e.getMessage() + "', rollbackOnly = " + tx.getRollbackOnly());
                }
                try {
                    tx.commit();
                    System.out.println("commit() returned normally: Hibernate rolled back instead of committing");
                } catch (RollbackException e) {
                    System.out.println("commit refused: " + e.getClass().getSimpleName());
                }
                System.out.println("tx.isActive() = " + tx.isActive());
            }
            System.out.println("rows committed = " + rowsInDatabase(emf));
        }
    }
}