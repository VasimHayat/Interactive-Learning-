import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class Relationships {

    @Entity(name = "Product")
    @Table(name = "product")
    public static class Product {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE)
        private Long id;
        @Column(nullable = false, unique = true, length = 16)
        private String sku;

        // many-to-many: THIS side owns the join table because it declares @JoinTable
        @ManyToMany
        @JoinTable(name = "product_supplier",
                   joinColumns = @JoinColumn(name = "product_id"),
                   inverseJoinColumns = @JoinColumn(name = "supplier_id"))
        private Set<Supplier> suppliers = new HashSet<>();

        // one-to-one, inverse side: the FK (shared primary key) lives in product_details
        @OneToOne(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
        private ProductDetails details;

        protected Product() {}
        Product(String sku) { this.sku = sku; }
        Long id() { return id; }
        String sku() { return sku; }
        Set<Supplier> suppliers() { return suppliers; }
        void addSupplier(Supplier s) { suppliers.add(s); s.products.add(this); }
        void setDetails(ProductDetails d) { this.details = d; d.product = this; }
    }

    @Entity(name = "Supplier")
    @Table(name = "supplier")
    public static class Supplier {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE)
        private Long id;
        @Column(nullable = false, unique = true)
        private String name;
        @ManyToMany(mappedBy = "suppliers")                // inverse: never written from here
        private Set<Product> products = new HashSet<>();
        protected Supplier() {}
        Supplier(String name) { this.name = name; }
        String name() { return name; }
    }

    @Entity(name = "ProductDetails")
    @Table(name = "product_details")
    public static class ProductDetails {
        @Id
        private Long id;                                   // no @GeneratedValue: copied from product
        @OneToOne(fetch = FetchType.LAZY)
        @MapsId                                            // "my primary key IS the product's primary key"
        @JoinColumn(name = "product_id")
        private Product product;
        private String description;
        protected ProductDetails() {}
        ProductDetails(String description) { this.description = description; }
        Long id() { return id; }
        String description() { return description; }
    }

    @Entity(name = "Order")
    @Table(name = "orders")                                // ORDER is a reserved word in SQL
    public static class Order {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE)
        private Long id;
        private String reference;

        // inverse side: the FK order_id lives in order_line, so this side is mappedBy
        @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
        private List<OrderLine> lines = new ArrayList<>();

        protected Order() {}
        Order(String reference) { this.reference = reference; }
        Long id() { return id; }
        List<OrderLine> lines() { return lines; }

        // the sync helper: sets BOTH sides, so the owning side gets written
        void addLine(OrderLine line) { lines.add(line); line.order = this; }
        void removeLine(OrderLine line) { lines.remove(line); line.order = null; }
    }

    @Entity(name = "OrderLine")
    @Table(name = "order_line")
    public static class OrderLine {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE)
        private Long id;
        @ManyToOne(fetch = FetchType.LAZY)                 // optional by default, so a line CAN exist with no order
        @JoinColumn(name = "order_id")                     // owning side: this column IS the relationship
        private Order order;
        private String sku;
        private int qty;
        protected OrderLine() {}
        OrderLine(String sku, int qty) { this.sku = sku; this.qty = qty; }
        Long id() { return id; }
        String sku() { return sku; }
    }

    static EntityManagerFactory emf() {
        return new PersistenceConfiguration("warehouse")
            .managedClass(Product.class).managedClass(Supplier.class).managedClass(ProductDetails.class)
            .managedClass(Order.class).managedClass(OrderLine.class)
            .property(PersistenceConfiguration.JDBC_URL, "jdbc:h2:mem:Relationships;DB_CLOSE_DELAY=-1")
            .property(PersistenceConfiguration.JDBC_USER, "sa")
            .property(PersistenceConfiguration.JDBC_PASSWORD, "")
            .property(PersistenceConfiguration.SCHEMAGEN_DATABASE_ACTION, "drop-and-create")
            .property("hibernate.show_sql", "true")
            .createEntityManagerFactory();
    }

    static long count(EntityManager em, String sql) {
        return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
    }

    public static void main(String[] args) {
        try (EntityManagerFactory emf = emf()) {

            System.out.println("-- 1. setting only the inverse side: the FK is never written --");
            Long orphanOrderId;
            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                Order o = new Order("WRONG-1");
                OrderLine line = new OrderLine("BOLT-7", 10);
                o.lines().add(line);                       // inverse side only: line.order stays null
                em.persist(o);
                em.persist(line);
                em.getTransaction().commit();
                orphanOrderId = o.id();
            }
            try (EntityManager em = emf.createEntityManager()) {
                System.out.println("order_line rows with order_id null: " + count(em, "select count(*) from order_line where order_id is null"));
            }

            System.out.println("-- 2. the sync helper sets the owning side: FK written, lines cascaded --");
            Long orderId;
            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                Order o = new Order("ORD-1");
                o.addLine(new OrderLine("BOLT-7", 10));
                o.addLine(new OrderLine("NUT-3", 40));
                o.addLine(new OrderLine("CAM-9", 1));
                em.persist(o);                             // cascade = ALL persists the lines too
                em.getTransaction().commit();
                orderId = o.id();
            }
            try (EntityManager em = emf.createEntityManager()) {
                System.out.println("order_line rows for ORD-1: " + count(em, "select count(*) from order_line where order_id = " + orderId));
            }

            System.out.println("-- 3. orphanRemoval: dropping a line from the collection deletes the row --");
            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                Order o = em.find(Order.class, orderId);
                OrderLine cam = o.lines().stream().filter(l -> l.sku().equals("CAM-9")).findFirst().orElseThrow();
                o.removeLine(cam);
                em.getTransaction().commit();
                System.out.println("lines left: " + count(em, "select count(*) from order_line where order_id = " + orderId));
            }

            System.out.println("-- 4. many-to-many: the @JoinTable side writes the join rows --");
            Long boltId;
            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                Supplier acme = new Supplier("Acme Fasteners");
                Supplier zeta = new Supplier("Zeta Metals");
                Product bolt = new Product("BOLT-7");
                bolt.addSupplier(acme);
                bolt.addSupplier(zeta);
                em.persist(acme); em.persist(zeta); em.persist(bolt);   // no cascade on many-to-many
                em.getTransaction().commit();
                boltId = bolt.id();
            }
            try (EntityManager em = emf.createEntityManager()) {
                Product bolt = em.find(Product.class, boltId);
                Set<String> names = new TreeSet<>();
                for (Supplier s : bolt.suppliers()) names.add(s.name());
                System.out.println("suppliers of BOLT-7: " + names);
                System.out.println("join rows: " + count(em, "select count(*) from product_supplier"));
            }

            System.out.println("-- 5. one-to-one with @MapsId: the details row reuses the product's id --");
            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                Product bolt = em.find(Product.class, boltId);
                bolt.setDetails(new ProductDetails("M8 hex bolt, zinc plated"));
                em.getTransaction().commit();              // cascade ALL on the one-to-one persists it
            }
            try (EntityManager em = emf.createEntityManager()) {
                ProductDetails d = em.find(ProductDetails.class, boltId);
                System.out.println("details id == product id: " + d.id().equals(boltId) + ", " + d.description());
            }

            System.out.println("-- 6. cascade REMOVE: deleting the order deletes its lines first --");
            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                em.remove(em.find(Order.class, orderId));
                em.getTransaction().commit();
                System.out.println("order_line rows left: " + count(em, "select count(*) from order_line"));
            }
        }
    }
}