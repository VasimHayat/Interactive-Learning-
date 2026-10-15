package lm.testing;

import jakarta.persistence.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@SpringBootApplication
@EnableJpaRepositories(considerNestedRepositories = true)
public class RollbackHarness {

    @Entity(name = "Product")
    @Table(name = "product")
    public static class Product {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE) Long id;
        @Column(nullable = false, unique = true, length = 16) String sku;
        int priceInCents;
        protected Product() {}
        Product(String sku, int priceInCents) { this.sku = sku; this.priceInCents = priceInCents; }
        @Override public String toString() { return sku + "=" + priceInCents + "c"; }
    }

    public interface ProductRepository extends JpaRepository<Product, Long> {}

    @PersistenceContext EntityManager em;

    @Bean CommandLineRunner demo(ProductRepository repo, PlatformTransactionManager txm, DataSource ds) {
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        TransactionTemplate tx = new TransactionTemplate(txm);
        return args -> {
            System.out.println("-- 1. what a test transaction does: run, assert, roll back --");
            tx.executeWithoutResult(status -> {
                repo.save(new Product("BOLT-7", 80));
                repo.save(new Product("NUT-3", 25));
                em.flush();
                System.out.println("inside the transaction, count = " + repo.count());
                status.setRollbackOnly();                       // what @DataJpaTest does for you at the end
            });
            System.out.println("after rollback, rows = " + jdbc.queryForObject("select count(*) from product", Long.class));

            System.out.println("-- 2. the same body, committed --");
            tx.executeWithoutResult(status -> {
                repo.save(new Product("BOLT-7", 80));
                repo.save(new Product("NUT-3", 25));
            });
            System.out.println("after commit, rows = " + jdbc.queryForObject("select count(*) from product", Long.class));

            System.out.println("-- 3. the flush trap: an assertion that cannot fail --");
            boolean detected = false;
            try {
                tx.executeWithoutResult(status -> {
                    repo.save(new Product("BOLT-7", 1));          // duplicate sku — but nothing is flushed
                    status.setRollbackOnly();                     // ...and the rollback discards it unflushed
                });
            } catch (DataAccessException e) { detected = true; }
            System.out.println("duplicate detected without flush: " + detected);
            String caught = "nothing";
            try {
                tx.executeWithoutResult(status -> {
                    repo.saveAndFlush(new Product("BOLT-7", 1)); // flush through the repository proxy
                    status.setRollbackOnly();
                });
            } catch (DataAccessException e) { detected = true; caught = e.getClass().getSimpleName(); }
            System.out.println("duplicate detected with flush: " + detected + " (" + caught + ")");

            System.out.println("-- 4. the first-level cache hides the database --");
            tx.executeWithoutResult(status -> {
                Product p = repo.save(new Product("CAM-9", 2300));
                em.flush();
                Product again = repo.findById(p.id).orElseThrow();
                System.out.println("same instance without clear(): " + (p == again));
                em.clear();                                       // TestEntityManager.clear()
                Product fresh = repo.findById(p.id).orElseThrow();
                System.out.println("same instance after clear(): " + (p == fresh));
                status.setRollbackOnly();
            });

            System.out.println("-- 5. what @Sql does: a script before the body --");
            String script = """
                insert into product (id, sku, price_in_cents) values (1001, 'SEED-1', 10);
                insert into product (id, sku, price_in_cents) values (1002, 'SEED-2', 20);
                """;
            new ResourceDatabasePopulator(new ByteArrayResource(script.getBytes(StandardCharsets.UTF_8))).execute(ds);
            System.out.println("rows after script = " + repo.count());
        };
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(RollbackHarness.class);
        app.setDefaultProperties(Map.of(
            "spring.main.web-application-type", "none",
            "spring.main.banner-mode", "off",
            "spring.main.log-startup-info", "false",
            "logging.level.root", "WARN",
            "logging.pattern.console", "%level %logger{0}: %msg%n",
            "spring.datasource.url", "jdbc:h2:mem:testing;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto", "create-drop",
            "spring.jpa.show-sql", "true",
            "spring.jpa.open-in-view", "false",
            "logging.level.org.hibernate.orm.jdbc.error", "OFF"));   // H2 names its constraints; keep them out of stdout
        app.run(args).close();
    }
}