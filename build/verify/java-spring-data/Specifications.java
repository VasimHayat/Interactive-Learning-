package lm.specs;

import jakarta.persistence.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.springframework.data.domain.ExampleMatcher.GenericPropertyMatchers.startsWith;

@SpringBootApplication
@EnableJpaRepositories(considerNestedRepositories = true)
public class Specifications {

    public enum Category { FASTENER, STRUCTURAL, MOTION }

    @Entity(name = "Supplier")
    @Table(name = "supplier")
    public static class Supplier {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE) Long id;
        @Column(nullable = false, unique = true) String name;
        protected Supplier() {}
        Supplier(String name) { this.name = name; }
    }

    @Entity(name = "Product")
    @Table(name = "product")
    public static class Product {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE) Long id;
        @Column(nullable = false, unique = true, length = 16) String sku;
        @Enumerated(EnumType.STRING) @Column(nullable = false) Category category;
        int priceInCents;
        boolean active = true;
        @ManyToOne(fetch = FetchType.LAZY) Supplier supplier;
        protected Product() {}
        Product(String sku, Category category, int priceInCents, boolean active, Supplier supplier) {
            this.sku = sku; this.category = category; this.priceInCents = priceInCents; this.active = active; this.supplier = supplier;
        }
        @Override public String toString() { return sku + "/" + category + "/" + priceInCents + (active ? "" : "/inactive"); }
    }

    /* One static factory per criterion. Each returns a Specification, so they compose. */
    public static final class ProductSpecs {
        static Specification<Product> inCategory(Category c) {
            return (root, query, cb) -> cb.equal(root.get("category"), c);
        }
        static Specification<Product> priceBetween(int lo, int hi) {
            return (root, query, cb) -> cb.between(root.get("priceInCents"), lo, hi);
        }
        static Specification<Product> skuStartsWith(String prefix) {
            return (root, query, cb) -> cb.like(root.get("sku"), prefix + "%");
        }
        static Specification<Product> isActive() {
            return (root, query, cb) -> cb.isTrue(root.get("active"));
        }
        static Specification<Product> suppliedBy(String supplierName) {
            return (root, query, cb) -> {
                if (query != null) query.distinct(true);        // query is null for count/exists/delete
                return cb.equal(root.join("supplier").get("name"), supplierName);
            };
        }
        /* The "ignore absent criteria" pattern: every null argument means "no filter". */
        static Specification<Product> filter(Category c, Integer minPrice, Integer maxPrice, String skuPrefix, Boolean activeOnly) {
            List<Specification<Product>> parts = new ArrayList<>();
            if (c != null) parts.add(inCategory(c));
            if (minPrice != null || maxPrice != null) {
                parts.add(priceBetween(minPrice == null ? 0 : minPrice, maxPrice == null ? Integer.MAX_VALUE : maxPrice));
            }
            if (skuPrefix != null) parts.add(skuStartsWith(skuPrefix));
            if (Boolean.TRUE.equals(activeOnly)) parts.add(isActive());
            return Specification.allOf(parts);                   // empty list → no where clause at all
        }
    }

    public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {}
    public interface SupplierRepository extends JpaRepository<Supplier, Long> {}

    @Bean CommandLineRunner demo(ProductRepository products, SupplierRepository suppliers) {
        return args -> {
            Supplier acme = suppliers.save(new Supplier("Acme"));
            Supplier bolt = suppliers.save(new Supplier("BoltCo"));
            products.saveAll(List.of(
                new Product("BOLT-7", Category.FASTENER, 80, true, bolt),
                new Product("BOLT-9", Category.FASTENER, 95, false, bolt),
                new Product("NUT-3", Category.FASTENER, 25, true, acme),
                new Product("GIRD-1", Category.STRUCTURAL, 120_000, true, acme),
                new Product("ROD-4", Category.STRUCTURAL, 4_500, true, acme),
                new Product("CAM-9", Category.MOTION, 2_300, true, bolt)));
            Sort bySku = Sort.by("sku");

            System.out.println("-- 1. one criterion --");
            System.out.println(products.findAll(ProductSpecs.inCategory(Category.FASTENER), bySku));

            System.out.println("-- 2. and / or / not --");
            Specification<Product> cheapFasteners = Specification.where(ProductSpecs.inCategory(Category.FASTENER))
                    .and(ProductSpecs.priceBetween(0, 90));
            System.out.println(products.findAll(cheapFasteners.or(ProductSpecs.inCategory(Category.MOTION)), bySku));
            System.out.println(products.findAll(Specification.not(ProductSpecs.isActive()), bySku));

            System.out.println("-- 3. allOf / anyOf --");
            System.out.println(products.findAll(Specification.anyOf(
                    ProductSpecs.skuStartsWith("BOLT"), ProductSpecs.skuStartsWith("CAM")), bySku));

            System.out.println("-- 4. the null-tolerant filter --");
            System.out.println(products.findAll(ProductSpecs.filter(null, null, null, null, null), bySku));
            System.out.println(products.findAll(ProductSpecs.filter(Category.STRUCTURAL, 1_000, null, null, true), bySku));

            System.out.println("-- 5. a page needs two statements --");
            Page<Product> page = products.findAll(ProductSpecs.isActive(), PageRequest.of(0, 2, bySku));
            System.out.println("page 0 of " + page.getTotalPages() + ": " + page.getContent() + " total=" + page.getTotalElements());

            System.out.println("-- 6. a join inside a spec --");
            System.out.println(products.findAll(ProductSpecs.suppliedBy("BoltCo"), bySku));

            System.out.println("-- 7. count / exists / delete --");
            System.out.println("count structural = " + products.count(ProductSpecs.inCategory(Category.STRUCTURAL)));
            System.out.println("exists inactive = " + products.exists(Specification.not(ProductSpecs.isActive())));
            System.out.println("deleted inactive = " + products.delete((root, cb) -> cb.isFalse(root.get("active"))));

            System.out.println("-- 8. Query by Example --");
            Product probe = new Product();
            probe.sku = "bo";
            // one probe field only: with two, the order of the and-ed predicates is not guaranteed
            ExampleMatcher matcher = ExampleMatcher.matching()
                    .withIgnoreNullValues()
                    .withIgnorePaths("id", "priceInCents", "active")   // primitives are never null, so they must be ignored explicitly
                    .withMatcher("sku", startsWith())
                    .withIgnoreCase();
            System.out.println(products.findAll(Example.of(probe, matcher), bySku));
        };
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(Specifications.class);
        app.setDefaultProperties(Map.of(
            "spring.main.web-application-type", "none",
            "spring.main.banner-mode", "off",
            "spring.main.log-startup-info", "false",
            "logging.level.root", "WARN",
            "logging.pattern.console", "%level %logger{0}: %msg%n",
            "spring.datasource.url", "jdbc:h2:mem:specs;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto", "create-drop",
            "spring.jpa.show-sql", "true",
            "spring.jpa.open-in-view", "false"));
        app.run(args).close();
    }
}