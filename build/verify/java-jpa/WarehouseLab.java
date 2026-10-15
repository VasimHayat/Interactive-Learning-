import jakarta.persistence.*;
import org.hibernate.SessionFactory;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.stat.Statistics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

public class WarehouseLab {

    public enum Category { FASTENER, STRUCTURAL, TOOLING }

    @Embeddable
    public record Money(long amountInCents, String currency) {}

    // ── step 1: products and stock ───────────────────────────────────────────
    @Entity(name = "Product")
    @Table(name = "product")
    @SQLDelete(sql = "update product set deleted = true where id = ?")   // step 6: soft delete
    @SQLRestriction("deleted = false")
    public static class Product {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE)
        private Long id;
        @NaturalId
        @Column(nullable = false, unique = true, length = 16)
        private String sku;
        @Column(nullable = false)
        private String name;
        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 16)
        private Category category;
        @Embedded
        private Money price;
        private boolean deleted;
        private Instant createdAt;
        private Instant updatedAt;
        private int revision;

        protected Product() {}
        Product(String sku, String name, Category category, long priceInCents) {
            this.sku = sku; this.name = name; this.category = category;
            this.price = new Money(priceInCents, "EUR");
        }
        @PrePersist void onCreate() { createdAt = updatedAt = Instant.now(); }
        @PreUpdate  void onUpdate() { updatedAt = Instant.now(); revision++; }

        Long id() { return id; }
        String sku() { return sku; }
        Category category() { return category; }
        Money price() { return price; }
        void rename(String n) { this.name = n; }
        boolean hasTimestamps() { return createdAt != null && updatedAt != null; }
        int revision() { return revision; }
        @Override public String toString() { return sku + " " + price.amountInCents() + "c"; }
    }

    @Entity(name = "StockLevel")
    @Table(name = "stock_level")
    public static class StockLevel {
        @Id
        private Long id;                                  // shares the product's id …
        @OneToOne(fetch = FetchType.LAZY) @MapsId          // … because this is the product's stock
        private Product product;
        private int onHand;
        @Version
        private int version;

        protected StockLevel() {}
        StockLevel(Product product, int onHand) { this.product = product; this.onHand = onHand; }
        int onHand() { return onHand; }
        int version() { return version; }
        void take(int qty) {
            if (qty > onHand) throw new IllegalStateException("only " + onHand + " on hand");
            onHand -= qty;
        }
    }

    // ── step 2: orders, lines, suppliers ────────────────────────────────────
    @Entity(name = "Order")
    @Table(name = "orders")                               // ORDER is a reserved word
    public static class Order {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE)
        private Long id;
        private LocalDate placed;
        @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
        @OrderBy("id")
        private List<OrderLine> lines = new ArrayList<>();

        protected Order() {}
        Order(LocalDate placed) { this.placed = placed; }
        Long id() { return id; }
        LocalDate placed() { return placed; }
        List<OrderLine> lines() { return Collections.unmodifiableList(lines); }
        void addLine(Product p, int qty) {                // keeps both sides in step
            OrderLine l = new OrderLine(this, p, qty, p.price().amountInCents());
            lines.add(l);
        }
        void removeLine(OrderLine l) { lines.remove(l); l.order = null; }
        long totalInCents() { return lines.stream().mapToLong(OrderLine::subtotal).sum(); }
    }

    @Entity(name = "OrderLine")
    @Table(name = "order_line")
    public static class OrderLine {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE)
        private Long id;
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        private Order order;
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        private Product product;
        private int qty;
        private long unitPriceInCents;                    // copied: a later price change must not rewrite history

        protected OrderLine() {}
        OrderLine(Order order, Product product, int qty, long unitPriceInCents) {
            this.order = order; this.product = product; this.qty = qty; this.unitPriceInCents = unitPriceInCents;
        }
        Product product() { return product; }
        int qty() { return qty; }
        long subtotal() { return qty * unitPriceInCents; }
    }

    @Entity(name = "Supplier")
    @Table(name = "supplier")
    public static class Supplier {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE)
        private Long id;
        private String name;
        @ManyToMany
        @JoinTable(name = "supplier_product",
                   joinColumns = @JoinColumn(name = "supplier_id"),
                   inverseJoinColumns = @JoinColumn(name = "product_id"))
        private Set<Product> products = new HashSet<>();   // a Set: a List here makes every removal rewrite the join table

        protected Supplier() {}
        Supplier(String name) { this.name = name; }
        void supplies(Product p) { products.add(p); }
        Set<Product> products() { return products; }
    }

    // ── step 4: a projection that is not an entity ──────────────────────────
    public record CategoryRevenue(Category category, Long revenueInCents) {}

    // ── bootstrap ───────────────────────────────────────────────────────────
    static PersistenceConfiguration base(String schemaAction) {
        return new PersistenceConfiguration("warehouse")
            .managedClass(Product.class).managedClass(StockLevel.class)
            .managedClass(Order.class).managedClass(OrderLine.class).managedClass(Supplier.class)
            .property(PersistenceConfiguration.JDBC_URL, "jdbc:h2:mem:WarehouseLab;DB_CLOSE_DELAY=-1")
            .property(PersistenceConfiguration.JDBC_USER, "sa")
            .property(PersistenceConfiguration.JDBC_PASSWORD, "")
            .property(PersistenceConfiguration.SCHEMAGEN_DATABASE_ACTION, schemaAction)
            .property("hibernate.show_sql", "false")
            .property("hibernate.generate_statistics", "true");
    }

    static Product bySku(EntityManager em, String sku) {         // the natural key is how the business names a product
        return em.createQuery("select p from Product p where p.sku = :sku", Product.class)
                 .setParameter("sku", sku).getSingleResult();
    }

    static Statistics stats(EntityManagerFactory emf) {
        return emf.unwrap(SessionFactory.class).getStatistics();
    }

    // ── step 3: reserve stock ───────────────────────────────────────────────
    static void reservePessimistic(EntityManagerFactory emf, String sku, int qty) {
        try (EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();
            Product p = bySku(em, sku);
            StockLevel s = em.find(StockLevel.class, p.id(), LockModeType.PESSIMISTIC_WRITE);  // select … for update
            s.take(qty);
            em.getTransaction().commit();
        }
    }

    static int reserveOptimistic(EntityManagerFactory emf, EntityManager stale, long productId, int qty) {
        // 'stale' already holds a StockLevel read before someone else changed it
        int attempts = 0;
        EntityManager em = stale;
        while (true) {
            attempts++;
            try {
                if (!em.getTransaction().isActive()) em.getTransaction().begin();
                StockLevel s = em.find(StockLevel.class, productId);
                s.take(qty);
                em.getTransaction().commit();
                em.close();
                return attempts;
            } catch (RollbackException e) {
                if (!(e.getCause() instanceof OptimisticLockException)) throw e;
                em.close();
                em = emf.createEntityManager();           // a fresh context: re-read, re-apply
            }
        }
    }

    public static void main(String[] args) {
        try (EntityManagerFactory emf = base("drop-and-create").createEntityManagerFactory()) {
            Statistics st = stats(emf);

            // ── seed ────────────────────────────────────────────────────────
            long boltId;
            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                Product bolt = new Product("BOLT-7", "M8 bolt", Category.FASTENER, 80);
                Product nut  = new Product("NUT-3", "M8 nut", Category.FASTENER, 25);
                Product gird = new Product("GIRD-1", "Steel girder", Category.STRUCTURAL, 120_000);
                Product cam  = new Product("CAM-9", "Cam follower", Category.TOOLING, 2_300);
                for (Product p : List.of(bolt, nut, gird, cam)) em.persist(p);
                em.persist(new StockLevel(bolt, 500));
                em.persist(new StockLevel(nut, 1_000));
                em.persist(new StockLevel(gird, 3));
                em.persist(new StockLevel(cam, 40));

                Supplier acme = new Supplier("Acme Fasteners");
                acme.supplies(bolt); acme.supplies(nut);
                em.persist(acme);

                Order o1 = new Order(LocalDate.of(2026, 10, 1));
                o1.addLine(bolt, 100); o1.addLine(nut, 100);
                Order o2 = new Order(LocalDate.of(2026, 10, 3));
                o2.addLine(gird, 1); o2.addLine(cam, 2);
                Order o3 = new Order(LocalDate.of(2026, 10, 9));
                o3.addLine(bolt, 20);
                for (Order o : List.of(o1, o2, o3)) em.persist(o);       // cascades to the lines
                em.getTransaction().commit();
                boltId = bolt.id();
            }
            System.out.println("-- step 1+2: mapped and seeded --");
            try (EntityManager em = emf.createEntityManager()) {
                Product bolt = em.find(Product.class, boltId);
                System.out.println("product     = " + bolt + " (" + bolt.category() + ")");
                System.out.println("stock id    = " + em.find(StockLevel.class, boltId).onHand() + " on hand, shares id " + boltId);
                Supplier s = em.createQuery("select s from Supplier s", Supplier.class).getSingleResult();
                System.out.println("supplier    = " + s.products().stream().map(Product::sku).sorted().toList());
                System.out.println("orders      = " + em.createQuery("select count(o) from Order o", Long.class).getSingleResult()
                    + ", lines = " + em.createQuery("select count(l) from OrderLine l", Long.class).getSingleResult());
            }

            // ── step 3 ──────────────────────────────────────────────────────
            System.out.println("-- step 3: reserving stock --");
            reservePessimistic(emf, "BOLT-7", 120);
            try (EntityManager em = emf.createEntityManager()) {
                System.out.println("after pessimistic reserve: " + em.find(StockLevel.class, boltId).onHand());
            }
            EntityManager stale = emf.createEntityManager();
            stale.getTransaction().begin();
            stale.find(StockLevel.class, boltId);                           // version 0 is now in this context
            reservePessimistic(emf, "BOLT-7", 30);                          // someone else commits first: version 1
            int attempts = reserveOptimistic(emf, stale, boltId, 50);
            try (EntityManager em = emf.createEntityManager()) {
                StockLevel s = em.find(StockLevel.class, boltId);
                System.out.println("optimistic reserve took " + attempts + " attempts; on hand " + s.onHand() + ", version " + s.version());
            }

            // ── step 4 ──────────────────────────────────────────────────────
            System.out.println("-- step 4: reports --");
            try (EntityManager em = emf.createEntityManager()) {
                List<CategoryRevenue> rev = em.createQuery(
                    "select new WarehouseLab$CategoryRevenue(p.category, sum(l.qty * l.unitPriceInCents)) " +
                    "from OrderLine l join l.product p group by p.category order by p.category", CategoryRevenue.class)
                    .getResultList();
                for (CategoryRevenue r : rev) System.out.println("revenue " + r.category() + " = " + r.revenueInCents());

                List<Product> top = em.createQuery(
                    "select p from Product p order by p.price.amountInCents desc, p.sku", Product.class)
                    .setFirstResult(0).setMaxResults(2).getResultList();
                System.out.println("top 2 by price = " + top);

                st.clear();
                List<Order> orders = em.createQuery(
                    "select distinct o from Order o join fetch o.lines l join fetch l.product order by o.placed", Order.class)
                    .getResultList();
                for (Order o : orders) {
                    System.out.println("order " + o.placed() + " total " + o.totalInCents() + "c, " + o.lines().size() + " lines, first " + o.lines().get(0).product().sku());
                }
                System.out.println("statements for " + orders.size() + " orders with lines and products: " + st.getPrepareStatementCount());
            }

            // ── step 6 (part a): callbacks ──────────────────────────────────
            System.out.println("-- step 6: callbacks and soft delete --");
            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                Product bolt = em.find(Product.class, boltId);
                System.out.println("timestamps set on persist: " + bolt.hasTimestamps() + ", revision " + bolt.revision());
                bolt.rename("M8 bolt, zinc");
                em.getTransaction().commit();
            }
            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                Product bolt = em.find(Product.class, boltId);
                System.out.println("revision after one update: " + bolt.revision());
                Product cam = bySku(em, "CAM-9");
                em.remove(cam);                                              // runs the @SQLDelete update instead
                em.getTransaction().commit();
            }
            try (EntityManager em = emf.createEntityManager()) {
                long visible = em.createQuery("select count(p) from Product p", Long.class).getSingleResult();
                Number rows = (Number) em.createNativeQuery("select count(*) from product").getSingleResult();
                System.out.println("products via JPQL: " + visible + ", rows in the table: " + rows);
            }
        }

        // ── step 5: bulk import on a second factory tuned for writes ────────
        System.out.println("-- step 5: bulk import --");
        try (EntityManagerFactory bulk = base("none")
                .property("hibernate.jdbc.batch_size", "50")
                .property("hibernate.order_inserts", "true")
                .createEntityManagerFactory()) {
            Statistics st = stats(bulk);
            st.clear();
            try (EntityManager em = bulk.createEntityManager()) {
                em.getTransaction().begin();
                for (int i = 1; i <= 500; i++) {
                    em.persist(new Product("IMP-" + i, "Imported part " + i, Category.TOOLING, 100 + i));
                    if (i % 50 == 0) { em.flush(); em.clear(); }           // keep the context small
                }
                em.getTransaction().commit();
            }
            System.out.println("entities inserted     : " + st.getEntityInsertCount());
            System.out.println("statements prepared   : " + st.getPrepareStatementCount());
            try (EntityManager em = bulk.createEntityManager()) {
                System.out.println("imported rows visible : " +
                    em.createQuery("select count(p) from Product p where p.sku like 'IMP-%'", Long.class).getSingleResult());
            }
        }
    }
}