package lm.validation;

import jakarta.persistence.*;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;

import java.lang.annotation.*;
import java.util.Map;
import java.util.TreeSet;

@SpringBootApplication
@EnableJpaRepositories(considerNestedRepositories = true)
public class Validation {

    /* A custom constraint: an annotation plus a validator. */
    @Documented
    @Constraint(validatedBy = SkuValidator.class)
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Sku {
        String message() default "must look like ABC-123";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }
    public static class SkuValidator implements ConstraintValidator<Sku, String> {
        @Override public boolean isValid(String value, ConstraintValidatorContext ctx) {
            return value == null || value.matches("[A-Z]+-\\d+");      // null is @NotBlank's job, not ours
        }
    }

    @Entity(name = "Product")
    @Table(name = "product")
    public static class Product {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE) Long id;
        @NotBlank @Size(max = 16) @Sku
        @Column(nullable = false, unique = true, length = 16) String sku;
        @Positive int priceInCents;
        @Email String supplierContact;
        @Version long version;
        protected Product() {}
        Product(String sku, int priceInCents, String supplierContact) {
            this.sku = sku; this.priceInCents = priceInCents; this.supplierContact = supplierContact;
        }
        @Override public String toString() { return sku + "=" + priceInCents + "c v" + version; }
    }

    public interface ProductRepository extends JpaRepository<Product, Long> {}

    public static class ProductNotFound extends RuntimeException {
        ProductNotFound(long id) { super("no product with id " + id); }
    }

    /* A service with method validation: @Validated on the class, @Valid / constraints on parameters. */
    @Service
    @Validated
    public static class CatalogService {
        private final ProductRepository repo;
        CatalogService(ProductRepository repo) { this.repo = repo; }

        public Product reprice(@Positive long id, @Min(1) @Max(1_000_000) int newPrice) {
            Product p = repo.findById(id).orElseThrow(() -> new ProductNotFound(id));
            p.priceInCents = newPrice;
            return repo.save(p);
        }
        public Product register(@Valid Product p) { return repo.save(p); }
    }

    static String rootOf(Throwable t) {
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return t.getClass().getSimpleName();
    }
    static void report(String label, Throwable t) {
        System.out.println(label + ": " + t.getClass().getSimpleName() + " <- root " + rootOf(t));
    }
    static void violations(Throwable t) {
        while (t != null && !(t instanceof ConstraintViolationException)) t = t.getCause();
        if (t == null) { System.out.println("  (no ConstraintViolationException in the chain)"); return; }
        TreeSet<String> lines = new TreeSet<>();
        for (ConstraintViolation<?> v : ((ConstraintViolationException) t).getConstraintViolations()) {
            lines.add("  " + v.getPropertyPath() + ": " + v.getMessage());
        }
        lines.forEach(System.out::println);
    }

    @Bean CommandLineRunner demo(ProductRepository repo, CatalogService catalog, PlatformTransactionManager txm) {
        return args -> {
            System.out.println("-- 1. entity constraints are checked at flush --");
            try {
                repo.save(new Product("bolt 7", -5, "not-an-email"));
            } catch (RuntimeException e) {
                report("save", e);
                violations(e);
            }

            System.out.println("-- 2. method validation on a service --");
            try {
                catalog.reprice(-1, 0);
            } catch (RuntimeException e) {
                report("reprice", e);
                violations(e);
            }
            try {
                catalog.register(new Product("", 10, null));
            } catch (RuntimeException e) {
                report("register", e);
                violations(e);
            }

            System.out.println("-- 3. a valid product, then a duplicate --");
            Product ok = repo.save(new Product("BOLT-7", 80, "sales@acme.example"));
            System.out.println("saved " + ok);
            try {
                repo.saveAndFlush(new Product("BOLT-7", 90, null));
            } catch (DataAccessException e) {
                System.out.println("duplicate: " + e.getClass().getSimpleName()
                        + " <- most specific " + e.getMostSpecificCause().getClass().getSimpleName());
            }

            System.out.println("-- 4. missing rows --");
            try {
                catalog.reprice(999, 100);
            } catch (ProductNotFound e) {
                System.out.println("lookup: " + e.getClass().getSimpleName() + " (" + e.getMessage() + ")");
            }
            repo.deleteById(999L);
            System.out.println("deleteById(999) threw: false");

            System.out.println("-- 5. a stale update is translated --");
            TransactionTemplate tx = new TransactionTemplate(txm);
            Product stale = tx.execute(s -> repo.findById(ok.id).orElseThrow());       // detached copy, version 0
            tx.executeWithoutResult(s -> repo.findById(ok.id).orElseThrow().priceInCents = 85);  // someone else commits first
            try {
                stale.priceInCents = 70;
                repo.save(stale);                                                        // merge + flush → stale version
            } catch (RuntimeException e) {
                report("stale save", e);
            }
            System.out.println("db now " + repo.findById(ok.id).orElseThrow());
        };
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(Validation.class);
        app.setDefaultProperties(Map.of(
            "spring.main.web-application-type", "none",
            "spring.main.banner-mode", "off",
            "spring.main.log-startup-info", "false",
            "logging.level.root", "WARN",
            "logging.pattern.console", "%level %logger{0}: %msg%n",
            "spring.datasource.url", "jdbc:h2:mem:validation;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto", "create-drop",
            "spring.jpa.show-sql", "false",
            "spring.jpa.open-in-view", "false",
            "logging.level.org.hibernate.orm.jdbc.error", "OFF"));   // H2 names its constraints; keep them out of stdout
        app.run(args).close();
    }
}