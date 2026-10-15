import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

public class Exceptions {

    @Entity(name = "Product")
    @Table(name = "product")
    public static class Product {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE)
        private Long id;
        @Column(nullable = false, unique = true, length = 16)
        private String sku;
        private int priceInCents;
        @Version
        private int version;
        protected Product() {}
        Product(String sku, int priceInCents) { this.sku = sku; this.priceInCents = priceInCents; }
        Long id() { return id; }
        void setPriceInCents(int p) { this.priceInCents = p; }
        int priceInCents() { return priceInCents; }
    }

    @Entity(name = "Order")
    @Table(name = "orders")                          // ORDER is a reserved word in SQL
    public static class Order {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE)
        private Long id;
        @ManyToOne(fetch = FetchType.LAZY)            // no cascade: the product must already be persistent
        private Product product;
        @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
        private List<OrderLine> lines = new ArrayList<>();
        protected Order() {}
        Order(Product product) { this.product = product; }
        Long id() { return id; }
        List<OrderLine> lines() { return lines; }
        void addLine(int qty) { OrderLine l = new OrderLine(this, qty); lines.add(l); }
    }

    @Entity(name = "OrderLine")
    @Table(name = "order_line")
    public static class OrderLine {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE)
        private Long id;
        @ManyToOne(fetch = FetchType.LAZY)
        private Order order;
        private int qty;
        protected OrderLine() {}
        OrderLine(Order order, int qty) { this.order = order; this.qty = qty; }
    }

    static EntityManagerFactory emf() {
        return new PersistenceConfiguration("warehouse")
            .managedClass(Product.class).managedClass(Order.class).managedClass(OrderLine.class)
            .property(PersistenceConfiguration.JDBC_URL, "jdbc:h2:mem:Exceptions;DB_CLOSE_DELAY=-1")
            .property(PersistenceConfiguration.JDBC_USER, "sa")
            .property(PersistenceConfiguration.JDBC_PASSWORD, "")
            .property(PersistenceConfiguration.SCHEMAGEN_DATABASE_ACTION, "drop-and-create")
            .property("hibernate.show_sql", "false")   // nine failure paths: the facts matter here, not the SQL
            .createEntityManagerFactory();
    }

    /* The exception you catch is rarely the one that explains the problem:
       JPA wraps Hibernate's, and commit wraps everything in RollbackException.
       Walk the cause chain and print every link, outermost first. */
    static String chain(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            if (sb.length() > 0) sb.append(" <- ");
            sb.append(c.getClass().getSimpleName());
        }
        return sb.toString();
    }

    interface Case { void run(EntityManager em) throws Exception; }

    static void attempt(EntityManagerFactory emf, String label, Case c) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            c.run(em);
            em.getTransaction().commit();
            System.out.println(label + ": no exception");
        } catch (Exception e) {
            System.out.println(label + ": " + chain(e));
        } finally {
            if (em.isOpen()) {
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                em.close();
            }
        }
    }

    public static void main(String[] args) {
        try (EntityManagerFactory emf = emf()) {
            long boltId, orderId;
            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                Product bolt = new Product("BOLT-7", 80);
                em.persist(bolt);
                em.persist(new Product("NUT-3", 25));
                Order o = new Order(bolt);
                o.addLine(2);
                o.addLine(5);
                em.persist(o);
                em.getTransaction().commit();
                boltId = bolt.id();
                orderId = o.id();
            }

            // 1. a lazy collection touched after the EntityManager is gone
            Order detachedOrder;
            try (EntityManager em = emf.createEntityManager()) {
                detachedOrder = em.find(Order.class, orderId);
            }
            try {
                System.out.println("1 lines on a closed context: " + detachedOrder.lines().size());
            } catch (RuntimeException e) {
                System.out.println("1 lines on a closed context: " + chain(e));
            }

            // 2. an association to an object that was never persisted
            attempt(emf, "2 order -> unsaved product", em -> {
                Order o = new Order(new Product("CAM-9", 2300));   // CAM-9 is transient
                em.persist(o);
            });

            // 3. persist() on an entity that already has an identity
            attempt(emf, "3 persist a detached entity", em -> {
                Product bolt = em.find(Product.class, boltId);
                em.detach(bolt);
                em.persist(bolt);
            });

            // 4 & 5. getSingleResult expects exactly one row
            attempt(emf, "4 single result, two rows", em ->
                em.createQuery("select p from Product p", Product.class).getSingleResult());
            attempt(emf, "5 single result, no rows", em ->
                em.createQuery("select p from Product p where p.sku = 'NOPE'", Product.class).getSingleResult());

            // 6. two contexts edit the same row; the second commit is stale
            EntityManager em1 = emf.createEntityManager();
            EntityManager em2 = emf.createEntityManager();
            try {
                em1.getTransaction().begin();
                em2.getTransaction().begin();
                Product first = em1.find(Product.class, boltId);
                Product second = em2.find(Product.class, boltId);
                first.setPriceInCents(81);
                em1.getTransaction().commit();                  // version 0 -> 1
                second.setPriceInCents(82);
                try {
                    em2.getTransaction().commit();              // expects version 0: refused
                    System.out.println("6 stale update: no exception");
                } catch (RuntimeException e) {
                    System.out.println("6 stale update: " + chain(e));
                }
            } finally {
                if (em2.getTransaction().isActive()) em2.getTransaction().rollback();
                em1.close(); em2.close();
            }

            // 7. a unique constraint, discovered only at flush
            attempt(emf, "7 duplicate sku", em -> em.persist(new Product("BOLT-7", 1)));

            // 8. a JPQL path that does not exist, discovered at query creation
            attempt(emf, "8 unknown attribute in JPQL", em ->
                em.createQuery("select p from Product p where p.colour = 'red'", Product.class).getResultList());

            // 9. a transaction marked rollback-only: commit() quietly becomes rollback()
            attempt(emf, "9 commit after setRollbackOnly", em -> {
                em.find(Product.class, boltId).setPriceInCents(999);
                em.getTransaction().setRollbackOnly();
            });
            try (EntityManager em = emf.createEntityManager()) {
                System.out.println("  price after that commit: " + em.find(Product.class, boltId).priceInCents());
            }

            // the context survived all of it: the data is intact
            try (EntityManager em = emf.createEntityManager()) {
                System.out.println("products still in the database: " +
                    em.createQuery("select count(p) from Product p", Long.class).getSingleResult());
            }
        }
    }
}