import jakarta.persistence.*;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.hibernate.stat.Statistics;

import java.util.List;

public class Batching {

    /* Phase A: IDENTITY ids — the database assigns the id when the row is inserted,
       so Hibernate must execute every INSERT immediately to learn it. */
    @Entity(name = "IdProduct")
    @Table(name = "id_product")
    public static class IdProduct {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
        @Column(nullable = false, unique = true, length = 16) private String sku;
        private int priceInCents;
        protected IdProduct() {}
        IdProduct(String sku, int priceInCents) { this.sku = sku; this.priceInCents = priceInCents; }
    }

    /* Phase B: SEQUENCE ids — the id is known before the INSERT, so inserts can be
       queued and sent in batches. */
    @Entity(name = "SeqProduct")
    @Table(name = "seq_product")
    public static class SeqProduct {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE) private Long id;
        @Column(nullable = false, unique = true, length = 16) private String sku;
        private int priceInCents;
        protected SeqProduct() {}
        SeqProduct(String sku, int priceInCents) { this.sku = sku; this.priceInCents = priceInCents; }
    }

    /* Phase C: the read paths, on a tiny table, with the SQL echoed. */
    @Entity(name = "Product")
    @Table(name = "product")
    public static class Product {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE) private Long id;
        @Column(nullable = false, unique = true, length = 16) private String sku;
        private int priceInCents;
        protected Product() {}
        Product(String sku, int priceInCents) { this.sku = sku; this.priceInCents = priceInCents; }
        String sku() { return sku; }
        int priceInCents() { return priceInCents; }
        void setPriceInCents(int p) { this.priceInCents = p; }
        @Override public String toString() { return sku + "=" + priceInCents + "c"; }
    }

    /* A projection: no identity, no dirty checking, no persistence context entry. */
    public record ProductView(String sku, int priceInCents) {}

    static EntityManagerFactory emf(boolean showSql, boolean batching, Class<?>... entities) {
        var cfg = new PersistenceConfiguration("warehouse")
            .property(PersistenceConfiguration.JDBC_URL, "jdbc:h2:mem:Batching;DB_CLOSE_DELAY=-1")
            .property(PersistenceConfiguration.JDBC_USER, "sa")
            .property(PersistenceConfiguration.JDBC_PASSWORD, "")
            .property(PersistenceConfiguration.SCHEMAGEN_DATABASE_ACTION, "drop-and-create")
            .property("hibernate.show_sql", String.valueOf(showSql))
            .property("hibernate.generate_statistics", "true");
        if (batching) {
            cfg.property("hibernate.jdbc.batch_size", "20")
               .property("hibernate.order_inserts", "true")
               .property("hibernate.order_updates", "true");
        }
        for (Class<?> c : entities) cfg.managedClass(c);
        return cfg.createEntityManagerFactory();
    }

    static Statistics stats(EntityManagerFactory emf) {
        return emf.unwrap(SessionFactory.class).getStatistics();
    }

    public static void main(String[] args) {
        System.out.println("-- phase A: 100 inserts, IDENTITY ids, batch_size=20 --");
        try (EntityManagerFactory emf = emf(false, true, IdProduct.class)) {
            Statistics st = stats(emf);
            st.clear();
            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                for (int i = 1; i <= 100; i++) em.persist(new IdProduct("ID-" + i, i));
                em.getTransaction().commit();
            }
            System.out.println("statements prepared = " + st.getPrepareStatementCount());
            System.out.println("entities inserted   = " + st.getEntityInsertCount());
        }

        System.out.println("-- phase B: 100 inserts, SEQUENCE ids, batch_size=20, flush+clear every 20 --");
        try (EntityManagerFactory emf = emf(false, true, SeqProduct.class)) {
            Statistics st = stats(emf);
            st.clear();
            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                for (int i = 1; i <= 100; i++) {
                    em.persist(new SeqProduct("SEQ-" + i, i));
                    if (i % 20 == 0) { em.flush(); em.clear(); }   // bound the persistence context
                }
                em.getTransaction().commit();
            }
            System.out.println("statements prepared = " + st.getPrepareStatementCount());
            System.out.println("entities inserted   = " + st.getEntityInsertCount());
        }

        System.out.println("-- phase C: read paths --");
        try (EntityManagerFactory emf = emf(true, false, Product.class)) {
            Statistics st = stats(emf);
            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                em.persist(new Product("BOLT-7", 80));
                em.persist(new Product("NUT-3", 25));
                em.persist(new Product("CAM-9", 2300));
                em.getTransaction().commit();
            }

            System.out.println("- entity query -");
            st.clear();
            try (EntityManager em = emf.createEntityManager()) {
                List<Product> all = em.createQuery("select p from Product p order by p.sku", Product.class).getResultList();
                System.out.println("rows = " + all + ", entities hydrated = " + st.getEntityLoadCount());
            }

            System.out.println("- DTO projection -");
            st.clear();
            try (EntityManager em = emf.createEntityManager()) {
                List<ProductView> views = em.createQuery(
                        "select p.sku, p.priceInCents from Product p order by p.sku", ProductView.class).getResultList();
                System.out.println("rows = " + views + ", entities hydrated = " + st.getEntityLoadCount());
            }

            System.out.println("- read-only session: a change is not written -");
            st.clear();
            try (EntityManager em = emf.createEntityManager()) {
                em.unwrap(Session.class).setDefaultReadOnly(true);
                em.getTransaction().begin();
                Product bolt = em.createQuery("select p from Product p where p.sku = 'BOLT-7'", Product.class).getSingleResult();
                bolt.setPriceInCents(999);
                em.getTransaction().commit();
            }
            System.out.println("entity updates = " + st.getEntityUpdateCount());

            System.out.println("- StatelessSession: no persistence context at all -");
            try (StatelessSession ss = emf.unwrap(SessionFactory.class).openStatelessSession()) {
                ss.getTransaction().begin();
                ss.insert(new Product("PIN-2", 15));
                ss.getTransaction().commit();
            }

            System.out.println("- bulk update in one statement -");
            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                int changed = em.createQuery("update Product p set p.priceInCents = p.priceInCents + 5").executeUpdate();
                em.getTransaction().commit();
                System.out.println("rows changed = " + changed);
            }
            try (EntityManager em = emf.createEntityManager()) {
                System.out.println("after = " + em.createQuery("select p from Product p order by p.sku", Product.class).getResultList());
            }
        }
    }
}