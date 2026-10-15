package lm.auditing;

import jakarta.persistence.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AbstractAggregateRoot;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import java.time.Instant;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootApplication
@EnableJpaRepositories(considerNestedRepositories = true)
@EnableJpaAuditing(auditorAwareRef = "auditor", dateTimeProviderRef = "clock")
public class Auditing {

    // who: in a real application this reads the security context
    @Bean AuditorAware<String> auditor() { return () -> Optional.of("picker-42"); }

    // when: a fixed, ticking clock so the stamps are reproducible (tests do the same)
    @Bean DateTimeProvider clock() {
        AtomicInteger tick = new AtomicInteger();
        return () -> Optional.of(Instant.parse("2026-10-15T09:00:00Z").plusSeconds(tick.getAndIncrement()));
    }

    @MappedSuperclass
    @EntityListeners(AuditingEntityListener.class)
    public static abstract class Auditable {
        @CreatedDate Instant createdAt;
        @LastModifiedDate Instant updatedAt;
        @CreatedBy String createdBy;
        @LastModifiedBy String updatedBy;
    }

    static final AtomicInteger prePersist = new AtomicInteger(), postLoad = new AtomicInteger();

    @Entity(name = "Product")
    @Table(name = "product")
    public static class Product extends Auditable {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE) Long id;
        @Column(nullable = false, unique = true, length = 16) String sku;
        int priceInCents;
        protected Product() {}
        Product(String sku, int priceInCents) { this.sku = sku; this.priceInCents = priceInCents; }
        @PrePersist void beforeInsert() { prePersist.incrementAndGet(); }      // JPA callback, independent of Spring
        @PostLoad void afterLoad() { postLoad.incrementAndGet(); }
        void setPriceInCents(int p) { priceInCents = p; }
    }

    public record OrderPlaced(String reference) {}

    @Entity(name = "Order")
    @Table(name = "orders")
    public static class Order extends AbstractAggregateRoot<Order> {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE) Long id;
        @Column(nullable = false, unique = true) String reference;
        protected Order() {}
        Order(String reference) { this.reference = reference; }
        Order place() { registerEvent(new OrderPlaced(reference)); return this; }   // collected, published by save()
    }

    public interface ProductRepository extends JpaRepository<Product, Long> {}
    public interface OrderRepository extends JpaRepository<Order, Long> {}

    @Component
    public static class Listeners {
        final List<String> log = Collections.synchronizedList(new ArrayList<>());
        @EventListener
        void inTransaction(OrderPlaced e) { log.add("inside tx: " + e.reference()); }
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void afterCommit(OrderPlaced e) { log.add("after commit: " + e.reference()); }
        List<String> drain() { List<String> out = new ArrayList<>(log); Collections.sort(out); log.clear(); return out; }
    }

    @Service
    public static class OrderService {
        private final OrderRepository orders;
        OrderService(OrderRepository orders) { this.orders = orders; }
        @Transactional public void place(String ref) { orders.save(new Order(ref).place()); }
        @Transactional public void placeAndFail(String ref) {
            orders.save(new Order(ref).place());
            throw new IllegalStateException("payment declined");                 // RuntimeException → rollback
        }
    }

    @Bean CommandLineRunner demo(ProductRepository products, OrderService service, Listeners listeners, ApplicationEventPublisher publisher) {
        return args -> {
            System.out.println("-- A: auditing stamps on insert --");
            Product p = products.save(new Product("BOLT-7", 80));
            Product loaded = products.findById(p.id).orElseThrow();
            System.out.println("createdBy = " + loaded.createdBy + ", createdAt = " + loaded.createdAt);
            System.out.println("updatedAt == createdAt: " + loaded.updatedAt.equals(loaded.createdAt));

            System.out.println("-- B: stamps on update --");
            loaded.setPriceInCents(95);
            Product after = products.findById(products.save(loaded).id).orElseThrow();
            System.out.println("updatedBy = " + after.updatedBy + ", updatedAt = " + after.updatedAt);
            System.out.println("updatedAt after createdAt: " + after.updatedAt.isAfter(after.createdAt));
            System.out.println("callbacks: prePersist = " + prePersist.get() + ", postLoad = " + postLoad.get());

            System.out.println("-- C: a domain event, committed --");
            service.place("ORD-1");
            System.out.println(listeners.drain());

            System.out.println("-- D: a domain event, rolled back --");
            try { service.placeAndFail("ORD-2"); } catch (IllegalStateException e) { System.out.println("caught " + e.getMessage()); }
            System.out.println(listeners.drain());

            System.out.println("-- E: published directly, outside any transaction --");
            publisher.publishEvent(new OrderPlaced("ORD-3"));
            System.out.println(listeners.drain());
        };
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(Auditing.class);
        app.setDefaultProperties(Map.of(
            "spring.main.web-application-type", "none",
            "spring.main.banner-mode", "off",
            "spring.main.log-startup-info", "false",
            "logging.level.root", "WARN",
            "logging.pattern.console", "%level %logger{0}: %msg%n",
            "spring.datasource.url", "jdbc:h2:mem:auditing;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto", "create-drop",
            "spring.jpa.show-sql", "true",
            "spring.jpa.open-in-view", "false"));
        app.run(args).close();
    }
}