import jakarta.persistence.*;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import java.util.ArrayList;
import java.util.List;

public class NPlusOne {

    @Entity(name = "Order")
    @Table(name = "orders")
    public static class Order {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE)
        private Long id;
        private String reference;
        @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)   // lazy by default
        private List<OrderLine> lines = new ArrayList<>();
        protected Order() {}
        Order(String reference) { this.reference = reference; }
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
        private int qty;
        protected OrderLine() {}
        OrderLine(String sku, int qty) { this.sku = sku; this.qty = qty; }
        int qty() { return qty; }
    }

    static EntityManagerFactory emf() {
        return new PersistenceConfiguration("warehouse")
            .managedClass(Order.class).managedClass(OrderLine.class)
            .property(PersistenceConfiguration.JDBC_URL, "jdbc:h2:mem:NPlusOne;DB_CLOSE_DELAY=-1")
            .property(PersistenceConfiguration.JDBC_USER, "sa")
            .property(PersistenceConfiguration.JDBC_PASSWORD, "")
            .property(PersistenceConfiguration.SCHEMAGEN_DATABASE_ACTION, "drop-and-create")
            .property("hibernate.show_sql", "true")
            .property("hibernate.generate_statistics", "true")
            .createEntityManagerFactory();
    }

    // the "report": walks every order's lines — touching lines() initialises the collection
    static int totalQty(List<Order> orders) {
        int total = 0;
        for (Order o : orders) for (OrderLine l : o.lines()) total += l.qty();
        return total;
    }

    public static void main(String[] args) {
        try (EntityManagerFactory emf = emf()) {
            Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();

            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                for (int i = 1; i <= 5; i++) {
                    Order o = new Order("ORD-" + i);
                    o.addLine(new OrderLine("BOLT-7", i));
                    o.addLine(new OrderLine("NUT-3", i * 2));
                    o.addLine(new OrderLine("CAM-9", 1));
                    em.persist(o);
                }
                em.getTransaction().commit();
            }

            System.out.println("-- A. lazy default: 1 query for the orders, then 1 per order for its lines --");
            stats.clear();
            try (EntityManager em = emf.createEntityManager()) {
                List<Order> orders = em.createQuery("select o from Order o order by o.id", Order.class).getResultList();
                System.out.println("total qty: " + totalQty(orders) + ", statements: " + stats.getPrepareStatementCount());
            }

            System.out.println("-- B. join fetch: one query, lines arrive with their orders --");
            stats.clear();
            try (EntityManager em = emf.createEntityManager()) {
                List<Order> orders = em.createQuery(
                        "select o from Order o join fetch o.lines order by o.id", Order.class).getResultList();
                System.out.println("orders: " + orders.size() + " (no duplicates: Hibernate 6+ de-duplicates root entities)");
                System.out.println("total qty: " + totalQty(orders) + ", statements: " + stats.getPrepareStatementCount());
            }

            System.out.println("-- C. entity graph: the same plan, declared instead of written into the query --");
            stats.clear();
            try (EntityManager em = emf.createEntityManager()) {
                EntityGraph<Order> graph = em.createEntityGraph(Order.class);
                graph.addAttributeNodes("lines");
                List<Order> orders = em.createQuery("select o from Order o order by o.id", Order.class)
                                       .setHint("jakarta.persistence.fetchgraph", graph)
                                       .getResultList();
                System.out.println("total qty: " + totalQty(orders) + ", statements: " + stats.getPrepareStatementCount());
            }

            System.out.println("-- D. batch fetching (size 10): 1 + ceil(5/10) queries --");
            stats.clear();
            try (EntityManager em = emf.createEntityManager()) {
                em.unwrap(Session.class).setFetchBatchSize(10);   // same effect as @BatchSize(size = 10) on the mapping
                List<Order> orders = em.createQuery("select o from Order o order by o.id", Order.class).getResultList();
                System.out.println("total qty: " + totalQty(orders) + ", statements: " + stats.getPrepareStatementCount());
            }
        }
    }
}