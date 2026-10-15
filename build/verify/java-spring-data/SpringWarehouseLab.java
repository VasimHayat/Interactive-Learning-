package lm.lab;

import jakarta.persistence.*;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootApplication
@EnableJpaRepositories(considerNestedRepositories = true)
@EnableJpaAuditing
@EnableConfigurationProperties(SpringWarehouseLab.LabProps.class)
public class SpringWarehouseLab {

    // ── step 1: entities ──────────────────────────────────────────────────
    public enum Category { FASTENER, STRUCTURAL, MOTION }

    @Entity(name = "Product") @Table(name = "product")
    @EntityListeners(AuditingEntityListener.class)
    public static class Product {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE) Long id;
        @NotBlank @Column(nullable = false, unique = true, length = 16) String sku;
        @Column(nullable = false) String name;
        @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) Category category;
        @Positive int priceInCents;
        boolean active = true;
        @CreatedDate Instant createdAt;
        @CreatedBy String createdBy;
        protected Product() {}
        Product(String sku, String name, Category category, int priceInCents) {
            this.sku = sku; this.name = name; this.category = category; this.priceInCents = priceInCents;
        }
        @Override public String toString() { return sku + "@" + priceInCents; }
    }

    @Entity(name = "StockLevel") @Table(name = "stock_level")
    public static class StockLevel {
        @Id Long id;
        @OneToOne(fetch = FetchType.LAZY) @MapsId Product product;
        int onHand;
        @Version long version;
        protected StockLevel() {}
        StockLevel(Product product, int onHand) { this.product = product; this.onHand = onHand; }
    }

    @Entity(name = "PurchaseOrder") @Table(name = "purchase_order")
    public static class PurchaseOrder {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE) Long id;
        @Column(nullable = false) LocalDate placed;
        @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
        List<OrderLine> lines = new ArrayList<>();
        protected PurchaseOrder() {}
        PurchaseOrder(LocalDate placed) { this.placed = placed; }
        void addLine(Product p, int qty) { OrderLine l = new OrderLine(this, p, qty); lines.add(l); }
    }

    @Entity(name = "OrderLine") @Table(name = "order_line")
    public static class OrderLine {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE) Long id;
        @ManyToOne(fetch = FetchType.LAZY, optional = false) PurchaseOrder order;
        @ManyToOne(fetch = FetchType.LAZY, optional = false) Product product;
        int qty;
        protected OrderLine() {}
        OrderLine(PurchaseOrder order, Product product, int qty) { this.order = order; this.product = product; this.qty = qty; }
    }

    // ── step 2: repositories, projections, a modifying query ─────────────
    public record CategoryRevenue(Category category, long cents) {}
    public interface LowStock { String getSku(); int getOnHand(); }
    public interface OrderSummary { Long getId(); long getTotal(); }

    public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
        Optional<Product> findBySku(String sku);
        List<Product> findByCategoryOrderBySkuAsc(Category category);
        boolean existsBySku(String sku);

        @org.springframework.data.jpa.repository.Query("select new lm.lab.SpringWarehouseLab$CategoryRevenue(p.category, sum(l.qty * p.priceInCents)) " +
               "from OrderLine l join l.product p group by p.category order by p.category")
        List<CategoryRevenue> revenueByCategory();

        @org.springframework.data.jpa.repository.Query("select p.sku as sku, s.onHand as onHand from StockLevel s join s.product p " +
               "where s.onHand < :threshold order by p.sku")
        List<LowStock> lowStock(@Param("threshold") int threshold);

        @Transactional                       // repository query methods are read-only by default
        @Modifying(clearAutomatically = true)
        @org.springframework.data.jpa.repository.Query("update Product p set p.priceInCents = p.priceInCents + :delta where p.category = :category")
        int reprice(@Param("category") Category category, @Param("delta") int delta);
    }

    public interface StockRepository extends JpaRepository<StockLevel, Long> {
        Optional<StockLevel> findByProductSku(String sku);
    }

    public interface OrderRepository extends JpaRepository<PurchaseOrder, Long> {
        @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"lines", "lines.product"})
        @org.springframework.data.jpa.repository.Query("select o from PurchaseOrder o order by o.id")
        List<PurchaseOrder> findAllWithLines();

        @org.springframework.data.jpa.repository.Query(value = "select o.id as id, sum(l.qty * p.priceInCents) as total from PurchaseOrder o " +
                       "join o.lines l join l.product p group by o.id order by o.id",
               countQuery = "select count(o) from PurchaseOrder o")
        Page<OrderSummary> summaries(Pageable pageable);
    }

    // ── step 3: a transactional service with an optimistic retry ──────────
    public static class OutOfStock extends RuntimeException {
        OutOfStock(String msg) { super(msg); }
    }

    @Service
    public static class InventoryService {
        private final StockRepository stock;
        InventoryService(StockRepository stock) { this.stock = stock; }

        @Transactional
        public int reserve(String sku, int qty) {
            StockLevel level = stock.findByProductSku(sku).orElseThrow();
            if (level.onHand < qty) throw new OutOfStock("only " + level.onHand + " of " + sku);
            level.onHand -= qty;                       // dirty checking issues the versioned update at commit
            return level.onHand;
        }

        /* A second picker: its own transaction, committed before it returns. */
        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public int reserveElsewhere(String sku, int qty) { return reserve(sku, qty); }

        /* Loads the row, then lets `interference` run in its own committed
           transaction, then updates — exactly what a concurrent picker does. */
        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public int reserveWithInterference(String sku, int qty, Runnable interference) {
            StockLevel level = stock.findByProductSku(sku).orElseThrow();
            if (interference != null) interference.run();
            if (level.onHand < qty) throw new OutOfStock("only " + level.onHand + " of " + sku);
            level.onHand -= qty;
            return level.onHand;
        }
    }

    @Component
    public static class ReservationGateway {
        private final InventoryService inventory;
        ReservationGateway(InventoryService inventory) { this.inventory = inventory; }

        public String reserveWithRetry(String sku, int qty, Runnable firstAttemptInterference) {
            for (int attempt = 1; ; attempt++) {
                try {
                    int left = inventory.reserveWithInterference(sku, qty, attempt == 1 ? firstAttemptInterference : null);
                    return "attempts: " + attempt + ", left: " + left;
                } catch (OptimisticLockingFailureException e) {
                    if (attempt == 3) throw e;
                    System.out.println("  attempt " + attempt + " failed with " + e.getClass().getSimpleName() + ", retrying");
                }
            }
        }
    }

    // ── step 5: auditing, domain events, specifications ───────────────────
    public record OrderPlaced(Long orderId, int lineCount) {}

    @Service
    public static class OrderService {
        private final OrderRepository orders;
        private final ApplicationEventPublisher events;
        OrderService(OrderRepository orders, ApplicationEventPublisher events) { this.orders = orders; this.events = events; }

        @Transactional
        public Long place(LocalDate placed, LinkedHashMap<Product, Integer> items) {
            PurchaseOrder order = new PurchaseOrder(placed);
            items.forEach(order::addLine);
            orders.save(order);
            events.publishEvent(new OrderPlaced(order.id, order.lines.size()));   // delivered only after commit
            return order.id;
        }
    }

    @Component
    public static class OrderPlacedListener {
        final AtomicInteger delivered = new AtomicInteger();
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void on(OrderPlaced event) { delivered.incrementAndGet(); }
    }

    static Specification<Product> inCategory(Category c) { return (root, q, cb) -> cb.equal(root.get("category"), c); }
    static Specification<Product> priceBetween(int lo, int hi) { return (root, q, cb) -> cb.between(root.get("priceInCents"), lo, hi); }
    static Specification<Product> active() { return (root, q, cb) -> cb.isTrue(root.get("active")); }

    // ── step 6: configuration ─────────────────────────────────────────────
    @ConfigurationProperties(prefix = "warehouse")
    public record LabProps(int reorderThreshold) {}

    @Bean org.springframework.data.domain.AuditorAware<String> auditor() { return () -> Optional.of("picker-bot"); }

    /* Map.of has no defined iteration order; a LinkedHashMap keeps line ids stable. */
    static LinkedHashMap<Product, Integer> lines(Object... pairs) {
        LinkedHashMap<Product, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) m.put((Product) pairs[i], (Integer) pairs[i + 1]);
        return m;
    }

    // ── the run ───────────────────────────────────────────────────────────
    @Bean CommandLineRunner run(ProductRepository products, StockRepository stock, OrderRepository orders,
                                InventoryService inventory, ReservationGateway gateway, OrderService orderService,
                                OrderPlacedListener listener, LabProps props, EntityManagerFactory emf) {
        return args -> {
            Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();

            // step 1 — seed through repositories
            Product bolt = products.save(new Product("BOLT-7", "Hex bolt M7", Category.FASTENER, 80));
            Product nut = products.save(new Product("NUT-3", "Nut M3", Category.FASTENER, 25));
            Product gird = products.save(new Product("GIRD-1", "Girder 1m", Category.STRUCTURAL, 120_000));
            Product cam = products.save(new Product("CAM-9", "Cam follower", Category.MOTION, 2_300));
            stock.saveAll(List.of(new StockLevel(bolt, 400), new StockLevel(nut, 12), new StockLevel(gird, 3), new StockLevel(cam, 30)));
            System.out.println("step 1  products        = " + products.count() + ", stock rows = " + stock.count());
            System.out.println("step 1  fasteners       = " + products.findByCategoryOrderBySkuAsc(Category.FASTENER));
            System.out.println("step 1  exists NUT-3    = " + products.existsBySku("NUT-3") + ", exists ROD-4 = " + products.existsBySku("ROD-4"));
            System.out.println("step 1  findBySku       = " + products.findBySku("CAM-9").map(p -> p.name).orElse("?"));

            // step 5 (events need orders) — place three orders
            orderService.place(LocalDate.of(2026, 10, 1), lines(bolt, 10, nut, 4));
            orderService.place(LocalDate.of(2026, 10, 2), lines(gird, 1));
            orderService.place(LocalDate.of(2026, 10, 3), lines(cam, 2, bolt, 5));
            System.out.println("step 5  events after commit = " + listener.delivered.get());

            // step 2 — projections and a modifying query
            System.out.println("step 2  revenue         = " + products.revenueByCategory());
            products.lowStock(props.reorderThreshold()).forEach(l ->
                System.out.println("step 2  low stock       = " + l.getSku() + " on hand " + l.getOnHand() + " (threshold " + props.reorderThreshold() + ")"));
            System.out.println("step 2  repriced rows   = " + products.reprice(Category.FASTENER, 5));
            System.out.println("step 2  after reprice   = " + products.findByCategoryOrderBySkuAsc(Category.FASTENER));

            // step 3 — reservations
            System.out.println("step 3  reserve 3 GIRD  = " + inventory.reserve("GIRD-1", 3) + " left");
            try { inventory.reserve("GIRD-1", 1); }
            catch (OutOfStock e) { System.out.println("step 3  refused         = " + e.getMessage()); }
            String outcome = gateway.reserveWithRetry("BOLT-7", 50, () -> inventory.reserveElsewhere("BOLT-7", 1));
            System.out.println("step 3  retry outcome   = " + outcome + "   (399 after the interfering picker, then 349)");
            System.out.println("step 3  version BOLT-7  = " + stock.findByProductSku("BOLT-7").orElseThrow().version);

            // step 4 — N+1-free listing and a projection page
            stats.clear();
            List<PurchaseOrder> all = orders.findAllWithLines();
            int lineTotal = 0;
            for (PurchaseOrder o : all) for (OrderLine l : o.lines) lineTotal += l.qty * l.product.priceInCents;
            System.out.println("step 4  orders          = " + all.size() + ", lines walked = " + all.stream().mapToInt(o -> o.lines.size()).sum()
                    + ", statements = " + stats.getPrepareStatementCount());
            Page<OrderSummary> page = orders.summaries(PageRequest.of(0, 2));
            System.out.println("step 4  page 0/2        = " + page.getContent().stream().map(s -> s.getId() + "=" + s.getTotal()).toList()
                    + ", total elements " + page.getTotalElements() + ", pages " + page.getTotalPages());

            // step 5 — auditing and specifications
            Product audited = products.findBySku("BOLT-7").orElseThrow();
            System.out.println("step 5  audit           = createdBy " + audited.createdBy + ", createdAt set " + (audited.createdAt != null));
            Specification<Product> spec = inCategory(Category.FASTENER).and(priceBetween(0, 50)).and(active());
            System.out.println("step 5  spec search     = " + products.findAll(spec, Sort.by("sku")));

            // step 6 — validation at the boundary
            try {
                products.saveAndFlush(new Product("  ", "Nameless", Category.MOTION, 0));
            } catch (ConstraintViolationException e) {
                List<String> paths = e.getConstraintViolations().stream().map(ConstraintViolation::getPropertyPath).map(Object::toString).sorted().toList();
                System.out.println("step 6  rejected        = " + e.getClass().getSimpleName() + " on " + paths);
            }
            System.out.println("step 6  products        = " + products.count() + "   (the invalid one never reached the table)");
        };
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(SpringWarehouseLab.class);
        app.setDefaultProperties(Map.ofEntries(
            Map.entry("spring.main.web-application-type", "none"),
            Map.entry("spring.main.banner-mode", "off"),
            Map.entry("spring.main.log-startup-info", "false"),
            Map.entry("logging.level.root", "WARN"),
            Map.entry("logging.pattern.console", "%level %logger{0}: %msg%n"),
            Map.entry("spring.datasource.url", "jdbc:h2:mem:springlab;DB_CLOSE_DELAY=-1"),
            Map.entry("spring.jpa.hibernate.ddl-auto", "create-drop"),
            Map.entry("spring.jpa.show-sql", "false"),
            Map.entry("spring.jpa.open-in-view", "false"),
            Map.entry("spring.jpa.properties.hibernate.generate_statistics", "true"),
            Map.entry("warehouse.reorder-threshold", "20")));
        app.run(args).close();
    }
}