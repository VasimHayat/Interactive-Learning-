import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
import java.util.ArrayList;
import java.util.List;

public class Queries {

    public enum Category { FASTENER, STRUCTURAL, MOTION }

    @Entity(name = "Product")
    @Table(name = "product")
    @NamedQuery(name = "Product.bySku", query = "select p from Product p where p.sku = :sku")
    public static class Product {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE)
        private Long id;
        @Column(nullable = false, unique = true, length = 16)
        private String sku;
        @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
        private Category category;
        private int priceInCents;

        protected Product() {}
        Product(String sku, Category category, int priceInCents) {
            this.sku = sku; this.category = category; this.priceInCents = priceInCents;
        }
        String sku() { return sku; }
        int priceInCents() { return priceInCents; }
        @Override public String toString() { return sku + "@" + priceInCents + "c"; }
    }

    @Entity(name = "PurchaseOrder")                 // "Order" is an SQL keyword; name the entity and table safely
    @Table(name = "purchase_order")
    public static class PurchaseOrder {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE)
        private Long id;
        @Column(nullable = false, length = 32)
        private String customer;
        @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
        private List<OrderLine> lines = new ArrayList<>();

        protected PurchaseOrder() {}
        PurchaseOrder(String customer) { this.customer = customer; }
        PurchaseOrder add(Product p, int qty) { lines.add(new OrderLine(this, p, qty)); return this; }
        String customer() { return customer; }
    }

    @Entity(name = "OrderLine")
    @Table(name = "order_line")
    public static class OrderLine {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE)
        private Long id;
        @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id")
        private PurchaseOrder order;
        @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id")
        private Product product;
        private int qty;

        protected OrderLine() {}
        OrderLine(PurchaseOrder order, Product product, int qty) { this.order = order; this.product = product; this.qty = qty; }
    }

    /** A DTO for projections: the query instantiates it directly, no entity is hydrated. */
    public record SkuRevenue(String sku, long cents) {}

    static EntityManagerFactory emf() {
        return new PersistenceConfiguration("warehouse")
            .managedClass(Product.class).managedClass(PurchaseOrder.class).managedClass(OrderLine.class)
            .property(PersistenceConfiguration.JDBC_URL, "jdbc:h2:mem:Queries;DB_CLOSE_DELAY=-1")
            .property(PersistenceConfiguration.JDBC_USER, "sa")
            .property(PersistenceConfiguration.JDBC_PASSWORD, "")
            .property(PersistenceConfiguration.SCHEMAGEN_DATABASE_ACTION, "drop-and-create")
            .property("hibernate.show_sql", "true")
            .createEntityManagerFactory();
    }

    static void seed(EntityManagerFactory emf) {
        try (EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();
            Product bolt = new Product("BOLT-7", Category.FASTENER, 80);
            Product nut  = new Product("NUT-3",  Category.FASTENER, 25);
            Product gird = new Product("GIRD-1", Category.STRUCTURAL, 120_000);
            Product cam  = new Product("CAM-9",  Category.MOTION, 2_300);
            for (Product p : List.of(bolt, nut, gird, cam)) em.persist(p);
            em.persist(new PurchaseOrder("acme").add(bolt, 100).add(nut, 400));
            em.persist(new PurchaseOrder("globex").add(bolt, 50).add(gird, 1));
            em.persist(new PurchaseOrder("initech"));                       // an order with no lines
            em.getTransaction().commit();
        }
    }

    static void section(String title) { System.out.println("-- " + title + " --"); }

    public static void main(String[] args) {
        try (EntityManagerFactory emf = emf()) {
            seed(emf);
            try (EntityManager em = emf.createEntityManager()) {

                section("named parameter");
                List<Product> dear = em.createQuery(
                        "select p from Product p where p.priceInCents > :min order by p.sku", Product.class)
                    .setParameter("min", 1_000).getResultList();
                System.out.println(dear);

                section("join + aggregate + having + DTO projection");
                List<SkuRevenue> revenue = em.createQuery(
                        "select new Queries$SkuRevenue(p.sku, sum(l.qty * p.priceInCents)) " +
                        "from OrderLine l join l.product p " +
                        "group by p.sku having sum(l.qty) >= :minQty order by p.sku", SkuRevenue.class)
                    .setParameter("minQty", 100L).getResultList();
                System.out.println(revenue);

                section("left join: products that were never ordered still appear");
                List<Object[]> counts = em.createQuery(
                        "select p.sku, count(l) from Product p left join OrderLine l on l.product = p " +
                        "group by p.sku order by p.sku", Object[].class).getResultList();
                for (Object[] row : counts) System.out.println(row[0] + " ordered on " + row[1] + " line(s)");

                section("pagination");
                List<Product> page2 = em.createQuery("select p from Product p order by p.sku", Product.class)
                    .setFirstResult(2).setMaxResults(2).getResultList();
                System.out.println(page2);

                section("in with a list parameter");
                System.out.println(em.createQuery(
                        "select p from Product p where p.sku in :skus order by p.sku", Product.class)
                    .setParameter("skus", List.of("NUT-3", "CAM-9")).getResultList());

                section("collection predicates: is empty, size()");
                System.out.println(em.createQuery(
                        "select o.customer from PurchaseOrder o where o.lines is empty", String.class).getResultList());
                System.out.println(em.createQuery(
                        "select o.customer from PurchaseOrder o where size(o.lines) = 2 order by o.customer", String.class).getResultList());

                section("named query, single result, result stream");
                Product bolt = em.createNamedQuery("Product.bySku", Product.class)
                    .setParameter("sku", "BOLT-7").getSingleResult();
                System.out.println("single = " + bolt);
                long cheap = em.createQuery("select p from Product p", Product.class)
                    .getResultStream().filter(p -> p.priceInCents() < 100).count();
                System.out.println("cheap via stream = " + cheap);

                section("bulk update bypasses the persistence context");
                em.getTransaction().begin();
                int changed = em.createQuery(
                        "update Product p set p.priceInCents = p.priceInCents + 5 where p.category = :c")
                    .setParameter("c", Category.FASTENER).executeUpdate();
                System.out.println("rows updated = " + changed);
                System.out.println("managed copy still says " + bolt.priceInCents() + "c");
                em.refresh(bolt);
                System.out.println("after refresh " + bolt.priceInCents() + "c");
                em.getTransaction().commit();

                section("native SQL");
                List<Object[]> raw = em.createNativeQuery(
                        "select sku, priceInCents from product where category = 'FASTENER' order by sku", Object[].class)
                    .getResultList();
                for (Object[] row : raw) System.out.println(row[0] + " " + row[1]);

                section("Criteria API: a dynamic filter");
                System.out.println(find(em, Category.FASTENER, null));
                System.out.println(find(em, null, 2_000));

                section("Criteria API: construct projection");
                CriteriaBuilder cb = em.getCriteriaBuilder();
                CriteriaQuery<SkuRevenue> cq = cb.createQuery(SkuRevenue.class);
                Root<OrderLine> l = cq.from(OrderLine.class);
                Join<OrderLine, Product> p = l.join("product");
                cq.select(cb.construct(SkuRevenue.class, p.get("sku"), cb.sum(cb.prod(l.<Integer>get("qty"), p.<Integer>get("priceInCents")))))
                  .groupBy(p.get("sku"))
                  .orderBy(cb.asc(p.get("sku")));
                System.out.println(em.createQuery(cq).getResultList());
            }
        }
    }

    /** Predicates are added only for the filters the caller supplied — no string concatenation. */
    static List<Product> find(EntityManager em, Category category, Integer minPrice) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Product> cq = cb.createQuery(Product.class);
        Root<Product> p = cq.from(Product.class);
        List<Predicate> where = new ArrayList<>();
        if (category != null) where.add(cb.equal(p.get("category"), category));
        if (minPrice != null) where.add(cb.ge(p.get("priceInCents"), minPrice));
        cq.select(p).where(where.toArray(new Predicate[0])).orderBy(cb.asc(p.get("sku")));
        return em.createQuery(cq).getResultList();
    }
}