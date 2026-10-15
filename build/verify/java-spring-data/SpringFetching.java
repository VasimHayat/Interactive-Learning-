package lm.fetching;

import jakarta.persistence.*;
import org.hibernate.Hibernate;
import org.hibernate.LazyInitializationException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SpringBootApplication
@EnableJpaRepositories(considerNestedRepositories = true)
public class SpringFetching {

    @Entity(name = "Product")
    @Table(name = "product")
    public static class Product {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE) Long id;
        @Column(nullable = false, unique = true, length = 16) String sku;
        protected Product() {}
        Product(String sku) { this.sku = sku; }
        String getSku() { return sku; }
    }

    @Entity(name = "Order")
    @Table(name = "orders")
    public static class Order {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE) Long id;
        @Column(nullable = false, unique = true) String reference;
        @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)        // LAZY by default
        List<OrderLine> lines = new ArrayList<>();
        protected Order() {}
        Order(String reference) { this.reference = reference; }
        String getReference() { return reference; }
        List<OrderLine> getLines() { return lines; }
        void addLine(Product p, int qty) { OrderLine l = new OrderLine(p, qty); l.order = this; lines.add(l); }
    }

    @Entity(name = "OrderLine")
    @Table(name = "order_line")
    public static class OrderLine {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE) Long id;
        @ManyToOne(fetch = FetchType.LAZY, optional = false) Order order;
        @ManyToOne(fetch = FetchType.LAZY, optional = false) Product product;
        int qty;
        protected OrderLine() {}
        OrderLine(Product product, int qty) { this.product = product; this.qty = qty; }
        int getQty() { return qty; }
        Product getProduct() { return product; }
    }

    public interface ProductRepository extends JpaRepository<Product, Long> {}

    public interface OrderRepository extends JpaRepository<Order, Long> {
        List<Order> findAllByOrderByReference();                                  // lazy: the N+1 shape

        @EntityGraph(attributePaths = {"lines", "lines.product"})
        List<Order> findWithGraphByOrderByReference();                            // one statement

        @Query("select distinct o from Order o join fetch o.lines l join fetch l.product order by o.reference")
        List<Order> findAllFetched();                                             // one statement, explicit
    }

    public record OrderSummary(String reference, int lineCount, int totalQty) {}

    @Service
    @Transactional(readOnly = true)
    public static class ReportService {
        private final OrderRepository orders;
        @PersistenceContext private EntityManager em;
        ReportService(OrderRepository orders) { this.orders = orders; }

        public int totalQtyLazy() { return sum(orders.findAllByOrderByReference()); }
        public int totalQtyGraph() { return sum(orders.findWithGraphByOrderByReference()); }
        public int totalQtyFetchQuery() { return sum(orders.findAllFetched()); }
        public int totalQtyBatched() {
            em.unwrap(Session.class).setFetchBatchSize(8);                        // what default_batch_fetch_size does globally
            return sum(orders.findAllByOrderByReference());
        }
        public Order firstOrder() { return orders.findAllByOrderByReference().get(0); }           // entity escapes the transaction
        public OrderSummary firstOrderSummary() {                                                // DTO: nothing lazy escapes
            Order o = orders.findWithGraphByOrderByReference().get(0);
            return new OrderSummary(o.getReference(), o.getLines().size(), sum(List.of(o)));
        }
        private static int sum(List<Order> os) {
            int t = 0;
            for (Order o : os) for (OrderLine l : o.getLines()) t += l.getQty();
            return t;
        }
    }

    @Bean CommandLineRunner demo(ProductRepository products, OrderRepository orders, ReportService report, EntityManagerFactory emf) {
        return args -> {
            Product bolt = products.save(new Product("BOLT-7"));
            Product nut = products.save(new Product("NUT-3"));
            for (int i = 1; i <= 5; i++) {
                Order o = new Order("ORD-" + i);
                o.addLine(bolt, i);
                o.addLine(nut, 10);
                orders.save(o);
            }
            Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();

            System.out.println("-- A: lazy collection in a loop --");
            stats.clear();
            System.out.println("total = " + report.totalQtyLazy() + ", statements = " + stats.getPrepareStatementCount());

            System.out.println("-- B: @EntityGraph --");
            stats.clear();
            System.out.println("total = " + report.totalQtyGraph() + ", statements = " + stats.getPrepareStatementCount());

            System.out.println("-- C: join fetch in @Query --");
            stats.clear();
            System.out.println("total = " + report.totalQtyFetchQuery() + ", statements = " + stats.getPrepareStatementCount());

            System.out.println("-- D: batch fetching, size 8 --");
            stats.clear();
            System.out.println("total = " + report.totalQtyBatched() + ", statements = " + stats.getPrepareStatementCount());

            System.out.println("-- E: an entity leaves the transaction (open-in-view=false) --");
            Order escaped = report.firstOrder();
            System.out.println("lines initialised = " + Hibernate.isInitialized(escaped.getLines()));
            try {
                System.out.println("line count = " + escaped.getLines().size());
            } catch (LazyInitializationException e) {
                System.out.println("caught " + e.getClass().getSimpleName());
            }

            System.out.println("-- F: a DTO leaves instead --");
            System.out.println(report.firstOrderSummary());
        };
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(SpringFetching.class);
        app.setDefaultProperties(Map.of(
            "spring.main.web-application-type", "none",
            "spring.main.banner-mode", "off",
            "spring.main.log-startup-info", "false",
            "logging.level.root", "WARN",
            "logging.pattern.console", "%level %logger{0}: %msg%n",
            "spring.datasource.url", "jdbc:h2:mem:fetching;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto", "create-drop",
            "spring.jpa.show-sql", "true",
            "spring.jpa.properties.hibernate.generate_statistics", "true",
            "spring.jpa.open-in-view", "false"));
        app.run(args).close();
    }
}