import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

public class LazyInit {

    @Entity(name = "Order")
    @Table(name = "orders")
    public static class Order {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE)
        private Long id;
        private String reference;
        @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
        private List<OrderLine> lines = new ArrayList<>();
        protected Order() {}
        Order(String reference) { this.reference = reference; }
        Long id() { return id; }
        List<OrderLine> lines() { return lines; }
        void addLine(OrderLine l) { lines.add(l); l.order = this; }
    }

    @Entity(name = "OrderLine")
    @Table(name = "order_line")
    public static class OrderLine {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE)
        private Long id;
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "order_id")
        private Order order;
        private String sku;
        protected OrderLine() {}
        OrderLine(String sku) { this.sku = sku; }
    }

    public static void main(String[] args) {
        EntityManagerFactory emf = new PersistenceConfiguration("warehouse")
            .managedClass(Order.class).managedClass(OrderLine.class)
            .property(PersistenceConfiguration.JDBC_URL, "jdbc:h2:mem:LazyInit;DB_CLOSE_DELAY=-1")
            .property(PersistenceConfiguration.JDBC_USER, "sa")
            .property(PersistenceConfiguration.JDBC_PASSWORD, "")
            .property(PersistenceConfiguration.SCHEMAGEN_DATABASE_ACTION, "drop-and-create")
            .property("hibernate.show_sql", "true")
            .createEntityManagerFactory();

        Long id;
        try (EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();
            Order o = new Order("ORD-1");
            o.addLine(new OrderLine("BOLT-7"));
            em.persist(o);
            em.getTransaction().commit();
            id = o.id();
        }

        Order detached;
        try (EntityManager em = emf.createEntityManager()) {
            detached = em.find(Order.class, id);          // lines is an uninitialised proxy collection
        }                                                 // the context that owned it is now closed

        System.out.println("order loaded, context closed; touching lines() ...");
        System.out.println("lines: " + detached.lines().size());   // LazyInitializationException escapes here
        emf.close();
    }
}