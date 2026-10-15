import jakarta.persistence.*;
import java.util.List;

public class FirstEntity {

    @Entity(name = "Product")              // the name JPQL uses; for a nested class it would otherwise be FirstEntity$Product
    @Table(name = "product")
    public static class Product {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE)
        private Long id;
        @Column(nullable = false, unique = true, length = 16)
        private String sku;
        private int priceInCents;

        protected Product() {}             // JPA instantiates entities reflectively
        Product(String sku, int priceInCents) { this.sku = sku; this.priceInCents = priceInCents; }

        Long id() { return id; }
        String sku() { return sku; }
        int priceInCents() { return priceInCents; }
        void reprice(int cents) { this.priceInCents = cents; }
        @Override public String toString() { return "Product[" + id + ", " + sku + ", " + priceInCents + "c]"; }
    }

    static EntityManagerFactory emf(Class<?>... entities) {
        var cfg = new PersistenceConfiguration("warehouse")
            .property(PersistenceConfiguration.JDBC_URL, "jdbc:h2:mem:FirstEntity;DB_CLOSE_DELAY=-1")
            .property(PersistenceConfiguration.JDBC_USER, "sa")
            .property(PersistenceConfiguration.JDBC_PASSWORD, "")
            .property(PersistenceConfiguration.SCHEMAGEN_DATABASE_ACTION, "drop-and-create")
            .property("hibernate.show_sql", "true");
        for (Class<?> c : entities) cfg.managedClass(c);
        return cfg.createEntityManagerFactory();
    }

    public static void main(String[] args) {
        try (EntityManagerFactory emf = emf(Product.class)) {

            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                em.persist(new Product("BOLT-7", 80));
                em.persist(new Product("NUT-3", 25));
                em.getTransaction().commit();           // the inserts happen here, not at persist()
            }

            try (EntityManager em = emf.createEntityManager()) {
                List<Product> all = em.createQuery(
                        "select p from Product p order by p.sku", Product.class).getResultList();
                System.out.println("loaded " + all);
            }

            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                Product bolt = em.find(Product.class, 1L);
                bolt.reprice(90);                        // no SQL written by you: dirty checking does it
                em.getTransaction().commit();
                System.out.println("after reprice " + bolt);
            }
        }
    }
}