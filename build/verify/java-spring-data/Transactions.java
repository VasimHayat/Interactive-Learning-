package lm.tx;

import jakarta.persistence.*;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizer;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.stereotype.Service;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.NestedTransactionNotSupportedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Map;
import java.util.Optional;

@SpringBootApplication
@EnableJpaRepositories(considerNestedRepositories = true)
public class Transactions {

    @Entity(name = "StockLevel")
    @Table(name = "stock_level")
    public static class StockLevel {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE) Long id;
        @Column(nullable = false, unique = true, length = 16) String sku;
        int onHand;
        protected StockLevel() {}
        StockLevel(String sku, int onHand) { this.sku = sku; this.onHand = onHand; }
    }

    public interface StockRepository extends JpaRepository<StockLevel, Long> {
        Optional<StockLevel> findBySku(String sku);
    }

    static String txName() {
        return TransactionSynchronizationManager.isActualTransactionActive()
            ? TransactionSynchronizationManager.getCurrentTransactionName() : "(no transaction)";
    }

    /* A second bean: calling it goes through a proxy, so REQUIRES_NEW takes effect. */
    @Service
    public static class Auditor {
        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public String inner() { return txName(); }

        @Transactional(propagation = Propagation.NESTED)
        public void stockAndFail(StockRepository repo, String sku) {
            repo.saveAndFlush(new StockLevel(sku, 1));     // flushed so the savepoint rollback has a row to undo
            throw new IllegalStateException("boom inside NESTED");
        }
    }

    @Service
    public static class InventoryService {
        private final StockRepository repo;
        private final Auditor auditor;
        InventoryService(StockRepository repo, Auditor auditor) { this.repo = repo; this.auditor = auditor; }

        @Transactional
        public void twoCallsOneTransaction() {
            repo.save(new StockLevel("BOLT-7", 100));
            repo.save(new StockLevel("NUT-3", 40));
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public String inner() { return txName(); }

        @Transactional
        public void selfInvocation() {
            System.out.println("outer runs in     = " + txName());
            System.out.println("this.inner() in   = " + inner());              // the trap: no proxy, no new transaction
            System.out.println("auditor.inner() in= " + auditor.inner());      // another bean: proxied, REQUIRES_NEW honoured
        }

        @Transactional
        public void addThenFail(String sku) {
            repo.save(new StockLevel(sku, 5));
            throw new IllegalStateException("runtime failure after save");
        }

        @Transactional
        public void addThenFailChecked(String sku) throws Exception {
            repo.save(new StockLevel(sku, 5));
            throw new Exception("checked failure after save");
        }

        @Transactional(rollbackFor = Exception.class)
        public void addThenFailCheckedRollback(String sku) throws Exception {
            repo.save(new StockLevel(sku, 5));
            throw new Exception("checked failure, rollbackFor set");
        }

        @Transactional(readOnly = true)
        public void tryToModify(String sku) {
            StockLevel s = repo.findBySku(sku).orElseThrow();
            s.onHand = 999;                                   // dirty, but a read-only transaction never flushes
            System.out.println("read-only tx      = " + TransactionSynchronizationManager.isCurrentTransactionReadOnly());
        }

        @Transactional(propagation = Propagation.MANDATORY)
        public void requiresCaller() { }

        @Transactional
        public void nestedSavepoint() {
            repo.save(new StockLevel("CAM-9", 7));
            try { auditor.stockAndFail(repo, "GIRD-1"); }
            catch (NestedTransactionNotSupportedException e) {
                System.out.println("NESTED refused    = " + e.getClass().getSimpleName());   // JPA has no savepoints
            }
            repo.save(new StockLevel("ROD-4", 3));            // the outer transaction carries on and commits
        }
    }

    @Bean TransactionTemplate tx(PlatformTransactionManager manager) {
        TransactionTemplate t = new TransactionTemplate(manager);
        t.setName("lm.tx.Transactions.template");            // a template has no method to be named after
        return t;
    }

    /* Boot's JpaTransactionManager refuses NESTED unless told otherwise. */
    @Bean TransactionManagerCustomizer<JpaTransactionManager> allowNested() {
        return manager -> manager.setNestedTransactionAllowed(true);
    }

    @Bean CommandLineRunner demo(StockRepository repo, InventoryService service, TransactionTemplate tx, EntityManagerFactory emf) {
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        return args -> {
            System.out.println("-- 1. every repository call outside a service transaction is its own transaction --");
            long before = stats.getTransactionCount();
            repo.count(); repo.count();
            System.out.println("runner runs in    = " + txName());
            System.out.println("transactions used = " + (stats.getTransactionCount() - before));

            System.out.println("-- 2. a @Transactional service method: two calls, one transaction --");
            before = stats.getTransactionCount();
            service.twoCallsOneTransaction();
            System.out.println("transactions used = " + (stats.getTransactionCount() - before));

            System.out.println("-- 3. self-invocation bypasses the proxy --");
            service.selfInvocation();

            System.out.println("-- 4/5/6. what rolls back --");
            try { service.addThenFail("PIN-2"); } catch (IllegalStateException e) { }
            System.out.println("after runtime ex  = " + repo.findBySku("PIN-2").isPresent());
            try { service.addThenFailChecked("HUB-8"); } catch (Exception e) { }
            System.out.println("after checked ex  = " + repo.findBySku("HUB-8").isPresent());
            try { service.addThenFailCheckedRollback("WIDGET-1"); } catch (Exception e) { }
            System.out.println("with rollbackFor  = " + repo.findBySku("WIDGET-1").isPresent());

            System.out.println("-- 7. readOnly = true: dirty entities are never flushed --");
            service.tryToModify("BOLT-7");
            System.out.println("BOLT-7 on hand    = " + repo.findBySku("BOLT-7").orElseThrow().onHand);

            System.out.println("-- 8. programmatic: TransactionTemplate --");
            String name = tx.execute(status -> { repo.save(new StockLevel("TMPL-1", 1)); return txName(); });
            System.out.println("template tx name  = " + name);

            System.out.println("-- 9. MANDATORY outside any transaction --");
            try { service.requiresCaller(); }
            catch (IllegalTransactionStateException e) { System.out.println("threw             = " + e.getClass().getSimpleName()); }

            System.out.println("-- 10. NESTED: a savepoint inside the outer transaction --");
            service.nestedSavepoint();
            System.out.println("CAM-9 present     = " + repo.findBySku("CAM-9").isPresent());
            System.out.println("GIRD-1 present    = " + repo.findBySku("GIRD-1").isPresent());
            System.out.println("ROD-4 present     = " + repo.findBySku("ROD-4").isPresent());
            System.out.println("rows              = " + repo.count());
        };
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(Transactions.class);
        app.setDefaultProperties(Map.ofEntries(                     // Map.of stops at ten pairs
            Map.entry("spring.main.web-application-type", "none"),
            Map.entry("spring.main.banner-mode", "off"),
            Map.entry("spring.main.log-startup-info", "false"),
            Map.entry("logging.level.root", "WARN"),
            Map.entry("logging.pattern.console", "%level %logger{0}: %msg%n"),
            Map.entry("spring.datasource.url", "jdbc:h2:mem:tx;DB_CLOSE_DELAY=-1"),
            Map.entry("spring.jpa.hibernate.ddl-auto", "create-drop"),
            Map.entry("spring.jpa.show-sql", "false"),
            Map.entry("spring.jpa.properties.hibernate.generate_statistics", "true"),
            Map.entry("spring.jpa.open-in-view", "false")));
        app.run(args).close();
    }
}