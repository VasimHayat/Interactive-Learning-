package lm.derived;

import jakarta.persistence.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@SpringBootApplication
@EnableJpaRepositories(considerNestedRepositories = true)
public class DerivedQueries {

    public enum Category { FASTENER, STRUCTURAL, MOTION }

    @Entity(name = "Product")
    @Table(name = "product")
    public static class Product {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE) Long id;
        @Column(nullable = false, unique = true, length = 16) String sku;
        String name;                                        // nullable on purpose: see findByNameIsNull
        @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) Category category;
        int priceInCents;
        boolean active;
        protected Product() {}
        Product(String sku, String name, Category category, int priceInCents, boolean active) {
            this.sku = sku; this.name = name; this.category = category; this.priceInCents = priceInCents; this.active = active;
        }
        @Override public String toString() { return sku; }
    }

    // Every method below is implemented by Spring Data from its NAME alone.
    public interface ProductRepository extends JpaRepository<Product, Long> {
        List<Product> findByCategoryOrderBySkuAsc(Category category);
        List<Product> findByCategoryAndPriceInCentsLessThanOrderBySku(Category category, int max);
        List<Product> findByPriceInCentsBetweenOrderByPriceInCents(int low, int high);
        List<Product> findBySkuStartingWithOrderBySku(String prefix);
        List<Product> findBySkuContainingOrderBySku(String part);
        List<Product> findBySkuEndingWithOrderBySku(String suffix);
        Optional<Product> findByNameIgnoreCase(String name);
        List<Product> findBySkuInOrderBySku(Collection<String> skus);
        List<Product> findByActiveTrueOrderBySku();
        List<Product> findByNameIsNullOrderBySku();
        List<Product> findFirst3ByOrderByPriceInCentsDesc();
        Optional<Product> findTopByCategoryOrderByPriceInCentsAsc(Category category);
        long countByCategory(Category category);
        boolean existsBySku(String sku);
        long countByActiveFalse();
        long deleteBySku(String sku);                        // derived delete: needs a transaction
        List<Product> findByCategory(Category category, Sort sort);
        Stream<Product> findByPriceInCentsGreaterThanOrderBySku(int min);   // needs an open transaction
    }

    @Bean TransactionTemplate tx(PlatformTransactionManager manager) { return new TransactionTemplate(manager); }

    @Bean CommandLineRunner demo(ProductRepository repo, TransactionTemplate tx) {
        return args -> {
            repo.saveAll(List.of(
                new Product("BOLT-7", "Hex bolt M8", Category.FASTENER, 80, true),
                new Product("NUT-3", "Hex nut M8", Category.FASTENER, 25, true),
                new Product("PIN-2", null, Category.FASTENER, 15, false),
                new Product("GIRD-1", "Steel girder 6m", Category.STRUCTURAL, 120_000, true),
                new Product("ROD-4", "Threaded rod 1m", Category.STRUCTURAL, 4_500, true),
                new Product("CAM-9", "Cam follower", Category.MOTION, 2_300, true),
                new Product("HUB-8", null, Category.MOTION, 7_900, false)));

            System.out.println("-- equality, comparison, range --");
            System.out.println("fasteners         = " + repo.findByCategoryOrderBySkuAsc(Category.FASTENER));
            System.out.println("cheap fasteners   = " + repo.findByCategoryAndPriceInCentsLessThanOrderBySku(Category.FASTENER, 50));
            System.out.println("2000..8000        = " + repo.findByPriceInCentsBetweenOrderByPriceInCents(2_000, 8_000));

            System.out.println("-- string matching --");
            System.out.println("starts BOLT       = " + repo.findBySkuStartingWithOrderBySku("BOLT"));
            System.out.println("contains -        = " + repo.findBySkuContainingOrderBySku("D-"));
            System.out.println("ends 9            = " + repo.findBySkuEndingWithOrderBySku("9"));
            System.out.println("name ignore case  = " + repo.findByNameIgnoreCase("hex BOLT m8").map(p -> p.sku).orElse("none"));

            System.out.println("-- collections, booleans, nulls --");
            System.out.println("in list           = " + repo.findBySkuInOrderBySku(List.of("NUT-3", "CAM-9", "NOPE")));
            System.out.println("active            = " + repo.findByActiveTrueOrderBySku());
            System.out.println("unnamed           = " + repo.findByNameIsNullOrderBySku());

            System.out.println("-- limiting and ordering --");
            System.out.println("top 3 by price    = " + repo.findFirst3ByOrderByPriceInCentsDesc());
            System.out.println("cheapest motion   = " + repo.findTopByCategoryOrderByPriceInCentsAsc(Category.MOTION).map(p -> p.sku).orElse("none"));
            System.out.println("sorted by param   = " + repo.findByCategory(Category.STRUCTURAL, Sort.by(Sort.Direction.DESC, "priceInCents")));

            System.out.println("-- counts and existence --");
            System.out.println("count structural  = " + repo.countByCategory(Category.STRUCTURAL));
            System.out.println("exists ROD-4      = " + repo.existsBySku("ROD-4"));
            System.out.println("inactive          = " + repo.countByActiveFalse());

            System.out.println("-- streaming and deleting need a transaction --");
            tx.executeWithoutResult(status -> {
                try (Stream<Product> s = repo.findByPriceInCentsGreaterThanOrderBySku(1_000)) {
                    System.out.println("streamed          = " + s.map(p -> p.sku).toList());
                }
                System.out.println("deleted           = " + repo.deleteBySku("PIN-2"));
            });
            System.out.println("remaining         = " + repo.count());
        };
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(DerivedQueries.class);
        app.setDefaultProperties(Map.of(
            "spring.main.web-application-type", "none",
            "spring.main.banner-mode", "off",
            "spring.main.log-startup-info", "false",
            "logging.level.root", "WARN",
            "logging.pattern.console", "%level %logger{0}: %msg%n",
            "spring.datasource.url", "jdbc:h2:mem:derived;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto", "create-drop",
            "spring.jpa.show-sql", "true",
            "spring.jpa.open-in-view", "false"));
        app.run(args).close();
    }
}