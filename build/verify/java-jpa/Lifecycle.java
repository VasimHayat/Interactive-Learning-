import jakarta.persistence.*;

public class Lifecycle {

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
        String sku() { return sku; }
        int priceInCents() { return priceInCents; }
        void setPriceInCents(int p) { this.priceInCents = p; }
        @Override public String toString() { return "Product[" + id + ", " + sku + ", " + priceInCents + "c]"; }
    }

    static EntityManagerFactory emf() {
        return new PersistenceConfiguration("warehouse")
            .managedClass(Product.class)
            .property(PersistenceConfiguration.JDBC_URL, "jdbc:h2:mem:Lifecycle;DB_CLOSE_DELAY=-1")
            .property(PersistenceConfiguration.JDBC_USER, "sa")
            .property(PersistenceConfiguration.JDBC_PASSWORD, "")
            .property(PersistenceConfiguration.SCHEMAGEN_DATABASE_ACTION, "drop-and-create")
            .property("hibernate.show_sql", "true")
            .createEntityManagerFactory();
    }

    public static void main(String[] args) {
        try (EntityManagerFactory emf = emf()) {

            System.out.println("-- 1. persist: transient -> managed, insert deferred to commit --");
            Long id;
            try (EntityManager em = emf.createEntityManager()) {
                Product bolt = new Product("BOLT-7", 80);
                System.out.println("managed before persist: " + em.contains(bolt));
                em.getTransaction().begin();
                em.persist(bolt);
                System.out.println("managed after persist:  " + em.contains(bolt) + ", id already assigned: " + (bolt.id() != null));
                em.getTransaction().commit();
                id = bolt.id();
            }

            System.out.println("-- 2. find twice in one context: one select, one instance --");
            try (EntityManager em = emf.createEntityManager()) {
                Product a = em.find(Product.class, id);
                Product b = em.find(Product.class, id);
                System.out.println("same instance: " + (a == b));
            }

            System.out.println("-- 3. dirty checking: a plain setter becomes an update at commit --");
            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                Product p = em.find(Product.class, id);
                p.setPriceInCents(85);
                System.out.println("no SQL yet; committing...");
                em.getTransaction().commit();
            }

            System.out.println("-- 4. detach, then modify: nothing is written --");
            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                Product p = em.find(Product.class, id);
                em.detach(p);
                p.setPriceInCents(999);
                em.getTransaction().commit();
                System.out.println("managed after detach: " + em.contains(p));
            }

            System.out.println("-- 5. merge a detached copy: select, then update; a different instance comes back --");
            Product detached;
            try (EntityManager em = emf.createEntityManager()) {
                detached = em.find(Product.class, id);     // loaded, then the context closes
            }
            detached.setPriceInCents(90);                       // modified outside any context
            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                Product merged = em.merge(detached);
                System.out.println("merged == detached: " + (merged == detached)
                        + ", detached managed: " + em.contains(detached) + ", merged managed: " + em.contains(merged));
                em.getTransaction().commit();
            }

            System.out.println("-- 6. refresh discards an in-memory change --");
            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                Product p = em.find(Product.class, id);
                p.setPriceInCents(1);
                em.refresh(p);
                System.out.println("after refresh: " + p.priceInCents() + "c");
                em.getTransaction().commit();
            }

            System.out.println("-- 7. flush: SQL now, visible inside the same transaction --");
            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                em.persist(new Product("NUT-3", 25));
                em.flush();
                Number rows = (Number) em.createNativeQuery("select count(*) from product").getSingleResult();
                System.out.println("rows visible after flush: " + rows);
                em.getTransaction().commit();
            }

            System.out.println("-- 8. getReference: a proxy, no select until a field is read --");
            try (EntityManager em = emf.createEntityManager()) {
                Product ref = em.getReference(Product.class, id);
                System.out.println("is a proxy subclass: " + (ref.getClass() != Product.class));
                System.out.println("reading sku forces the load...");
                System.out.println("sku = " + ref.sku());
            }

            System.out.println("-- 9. remove: delete at commit --");
            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                Product p = em.find(Product.class, id);
                em.remove(p);
                System.out.println("still managed? " + em.contains(p));
                em.getTransaction().commit();
                System.out.println("find after remove: " + em.find(Product.class, id));
            }
        }
    }
}