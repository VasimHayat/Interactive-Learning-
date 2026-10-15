package lm.paging;

import jakarta.persistence.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Map;

@SpringBootApplication
@EnableJpaRepositories(considerNestedRepositories = true)
public class Paging {

    public enum Category { FASTENER, STRUCTURAL, TOOLING }

    @Entity(name = "Product")
    @Table(name = "product")
    public static class Product {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE) Long id;
        @Column(nullable = false, unique = true, length = 16) String sku;
        @Enumerated(EnumType.STRING) @Column(nullable = false) Category category;
        int priceInCents;
        protected Product() {}
        Product(String sku, Category category, int priceInCents) { this.sku = sku; this.category = category; this.priceInCents = priceInCents; }
        public String getSku() { return sku; }
        public int getPriceInCents() { return priceInCents; }
        @Override public String toString() { return sku + "@" + priceInCents; }
    }

    public record ProductView(String sku, int priceInCents) {}

    public interface ProductRepository extends JpaRepository<Product, Long> {
        Slice<Product> findByCategory(Category category, Pageable pageable);                     // no count query

        @Query(value = "select p from Product p where p.priceInCents > :min",
               countQuery = "select count(p) from Product p where p.priceInCents > :min")
        Page<Product> findExpensive(@Param("min") int min, Pageable pageable);

        Window<Product> findFirst5ByCategoryOrderBySkuAsc(Category category, ScrollPosition position);   // scrolling API

        List<Product> findByCategory(Category category, Limit limit, Sort sort);
    }

    static String skus(Iterable<Product> ps) {
        StringBuilder sb = new StringBuilder("[");
        for (Product p : ps) sb.append(sb.length() > 1 ? ", " : "").append(p.getSku());
        return sb.append("]").toString();
    }

    @Bean CommandLineRunner demo(ProductRepository repo) {
        return args -> {
            Category[] cats = Category.values();
            for (int i = 1; i <= 23; i++) {
                repo.save(new Product(String.format("SKU-%02d", i), cats[i % 3], (i * 37) % 100 * 10));
            }

            System.out.println("-- A: Page with a two-level sort --");
            Pageable first = PageRequest.of(0, 5, Sort.by("priceInCents").descending().and(Sort.by("sku")));
            Page<Product> page = repo.findAll(first);
            System.out.println("content = " + skus(page));
            System.out.println("totalElements = " + page.getTotalElements() + ", totalPages = " + page.getTotalPages()
                    + ", number = " + page.getNumber() + ", hasNext = " + page.hasNext());

            System.out.println("-- B: the last page --");
            Page<Product> last = repo.findAll(PageRequest.of(page.getTotalPages() - 1, 5, first.getSort()));
            System.out.println("content = " + skus(last) + ", numberOfElements = " + last.getNumberOfElements() + ", hasNext = " + last.hasNext());

            System.out.println("-- C: Slice, no count --");
            Slice<Product> slice = repo.findByCategory(Category.FASTENER, PageRequest.of(0, 3, Sort.by("sku")));
            System.out.println("content = " + skus(slice) + ", hasNext = " + slice.hasNext());

            System.out.println("-- D: @Query with its own countQuery --");
            Page<Product> dear = repo.findExpensive(600, PageRequest.of(0, 4, Sort.by("sku")));
            System.out.println("content = " + skus(dear) + ", total = " + dear.getTotalElements());

            System.out.println("-- E: Page.map to a DTO --");
            Page<ProductView> views = page.map(p -> new ProductView(p.getSku(), p.getPriceInCents()));
            System.out.println(views.getContent().get(0) + " of " + views.getTotalElements());

            System.out.println("-- F: keyset scrolling --");
            Window<Product> w1 = repo.findFirst5ByCategoryOrderBySkuAsc(Category.STRUCTURAL, ScrollPosition.keyset());
            System.out.println("window 1 = " + skus(w1) + ", hasNext = " + w1.hasNext());
            Window<Product> w2 = repo.findFirst5ByCategoryOrderBySkuAsc(Category.STRUCTURAL, w1.positionAt(w1.size() - 1));
            System.out.println("window 2 = " + skus(w2) + ", hasNext = " + w2.hasNext());

            System.out.println("-- G: Limit + Sort on a derived query --");
            System.out.println(skus(repo.findByCategory(Category.TOOLING, Limit.of(3), Sort.by("priceInCents").descending())));

            System.out.println("-- H: unpaged --");
            System.out.println("all = " + repo.findAll(Pageable.unpaged()).getTotalElements());
        };
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(Paging.class);
        app.setDefaultProperties(Map.of(
            "spring.main.web-application-type", "none",
            "spring.main.banner-mode", "off",
            "spring.main.log-startup-info", "false",
            "logging.level.root", "WARN",
            "logging.pattern.console", "%level %logger{0}: %msg%n",
            "spring.datasource.url", "jdbc:h2:mem:paging;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto", "create-drop",
            "spring.jpa.show-sql", "true",
            "spring.jpa.open-in-view", "false"));
        app.run(args).close();
    }
}