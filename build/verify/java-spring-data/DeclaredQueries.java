package lm.declared;

import jakarta.persistence.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.Map;

@SpringBootApplication
@EnableJpaRepositories(considerNestedRepositories = true)
public class DeclaredQueries {

    public enum Category { FASTENER, STRUCTURAL, MOTION }

    @Entity(name = "Product")
    @Table(name = "product")
    @NamedQuery(name = "Product.cheaperThan",
                query = "select p from Product p where p.priceInCents < :max order by p.sku")
    public static class Product {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE) Long id;
        @Column(nullable = false, unique = true, length = 16) String sku;
        @Column(nullable = false) String name;
        @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) Category category;
        int priceInCents;
        boolean active;
        protected Product() {}
        Product(String sku, String name, Category category, int priceInCents, boolean active) {
            this.sku = sku; this.name = name; this.category = category; this.priceInCents = priceInCents; this.active = active;
        }
        public String getSku() { return sku; }
        public int getPriceInCents() { return priceInCents; }
        @Override public String toString() { return sku + "@" + priceInCents; }
    }

    /* Projections: three ways to say "I do not want the whole entity". */
    public interface SkuAndPrice { String getSku(); int getPriceInCents(); }              // closed: only these columns
    public interface SkuLabel { @Value("#{target.sku + ' @ ' + target.priceInCents + 'c'}") String getLabel(); }  // open: all columns
    public record SkuPrice(String sku, int priceInCents) {}                                // DTO: only these columns
    public interface SkuCents { String getSku(); int getCents(); }                         // for the native query's aliases

    public interface ProductRepository extends JpaRepository<Product, Long> {

        @Query("select p from Product p where p.category = :category and p.priceInCents <= :max order by p.sku")
        List<Product> affordable(@Param("category") Category category, @Param("max") int max);

        @Query("select p from Product p where p.sku like ?1 order by p.sku")
        List<Product> skuLike(String pattern);

        @Query("select p from Product p where p.category = :category")
        List<Product> byCategory(@Param("category") Category category, Sort sort);

        @Query(value = "select p from Product p where p.active = true",
               countQuery = "select count(p) from Product p where p.active = true")
        Page<Product> activePage(Pageable pageable);

        @Query(value = "select sku, price_in_cents as cents from product where price_in_cents > :min order by sku",
               nativeQuery = true)
        List<SkuCents> nativeAbove(@Param("min") int min);

        List<Product> cheaperThan(@Param("max") int max);          // resolved to the @NamedQuery "Product.cheaperThan"

        @Query("select count(p) from #{#entityName} p")
        long total();

        @Modifying @Transactional
        @Query("update Product p set p.priceInCents = p.priceInCents + :delta where p.category = :category")
        int repriceStale(@Param("category") Category category, @Param("delta") int delta);

        @Modifying(clearAutomatically = true) @Transactional
        @Query("update Product p set p.priceInCents = p.priceInCents + :delta where p.category = :category")
        int repriceClearing(@Param("category") Category category, @Param("delta") int delta);

        @Modifying @Transactional
        @Query("delete from Product p where p.active = false")
        int purgeInactive();

        List<SkuAndPrice> findByCategoryOrderBySku(Category category);                       // closed projection
        List<SkuLabel> findByPriceInCentsGreaterThanOrderBySku(int min);                      // open projection
        List<SkuPrice> findByCategoryAndActiveTrueOrderBySku(Category category);              // DTO, derived
        @Query("select new lm.declared.DeclaredQueries$SkuPrice(p.sku, p.priceInCents) from Product p where p.category = :category order by p.sku")
        List<SkuPrice> prices(@Param("category") Category category);                          // DTO, declared
        <T> List<T> findByCategoryOrderBySkuDesc(Category category, Class<T> type);           // dynamic projection
    }

    @Bean TransactionTemplate tx(PlatformTransactionManager manager) { return new TransactionTemplate(manager); }

    @Bean CommandLineRunner demo(ProductRepository repo, TransactionTemplate tx) {
        return args -> {
            repo.saveAll(List.of(
                new Product("BOLT-7", "Hex bolt M8", Category.FASTENER, 80, true),
                new Product("NUT-3", "Hex nut M8", Category.FASTENER, 25, true),
                new Product("PIN-2", "Split pin", Category.FASTENER, 15, false),
                new Product("GIRD-1", "Steel girder 6m", Category.STRUCTURAL, 120_000, true),
                new Product("ROD-4", "Threaded rod 1m", Category.STRUCTURAL, 4_500, true),
                new Product("CAM-9", "Cam follower", Category.MOTION, 2_300, true)));

            System.out.println("-- declared JPQL --");
            System.out.println("affordable        = " + repo.affordable(Category.FASTENER, 50));
            System.out.println("positional like   = " + repo.skuLike("%-%"));
            System.out.println("sort appended     = " + repo.byCategory(Category.STRUCTURAL, Sort.by("priceInCents").descending()));
            Page<Product> page = repo.activePage(PageRequest.of(1, 2, Sort.by("sku")));
            System.out.println("page 2 of active  = " + page.getContent() + " of " + page.getTotalElements() + " in " + page.getTotalPages() + " pages");
            System.out.println("native            = " + repo.nativeAbove(1_000).stream().map(r -> r.getSku() + "=" + r.getCents()).toList());
            System.out.println("named query       = " + repo.cheaperThan(100));
            System.out.println("#{#entityName}    = " + repo.total());

            System.out.println("-- projections --");
            System.out.println("closed interface  = " + repo.findByCategoryOrderBySku(Category.FASTENER).stream().map(p -> p.getSku() + "/" + p.getPriceInCents()).toList());
            System.out.println("open interface    = " + repo.findByPriceInCentsGreaterThanOrderBySku(2_000).stream().map(SkuLabel::getLabel).toList());
            System.out.println("record, derived   = " + repo.findByCategoryAndActiveTrueOrderBySku(Category.FASTENER));
            System.out.println("record, declared  = " + repo.prices(Category.STRUCTURAL));
            System.out.println("dynamic -> record = " + repo.findByCategoryOrderBySkuDesc(Category.FASTENER, SkuPrice.class));
            System.out.println("dynamic -> entity = " + repo.findByCategoryOrderBySkuDesc(Category.FASTENER, Product.class));

            System.out.println("-- @Modifying without clearAutomatically: the managed copy goes stale --");
            tx.executeWithoutResult(status -> {
                Product bolt = repo.findById(1L).orElseThrow();
                int changed = repo.repriceStale(Category.FASTENER, 10);
                Product again = repo.findById(1L).orElseThrow();
                System.out.println("rows updated      = " + changed);
                System.out.println("managed copy says = " + bolt.priceInCents + " (same instance: " + (bolt == again) + ")");
            });
            System.out.println("database says     = " + repo.findById(1L).orElseThrow().priceInCents);

            System.out.println("-- @Modifying(clearAutomatically = true): the context is emptied, so the next read hits the database --");
            tx.executeWithoutResult(status -> {
                Product bolt = repo.findById(1L).orElseThrow();
                int changed = repo.repriceClearing(Category.FASTENER, 10);
                Product again = repo.findById(1L).orElseThrow();
                System.out.println("rows updated      = " + changed);
                System.out.println("stale copy says   = " + bolt.priceInCents + ", fresh copy says " + again.priceInCents + " (same instance: " + (bolt == again) + ")");
            });

            System.out.println("-- @Modifying delete --");
            System.out.println("purged            = " + repo.purgeInactive());
            System.out.println("remaining         = " + repo.total());
        };
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(DeclaredQueries.class);
        app.setDefaultProperties(Map.of(
            "spring.main.web-application-type", "none",
            "spring.main.banner-mode", "off",
            "spring.main.log-startup-info", "false",
            "logging.level.root", "WARN",
            "logging.pattern.console", "%level %logger{0}: %msg%n",
            "spring.datasource.url", "jdbc:h2:mem:declared;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto", "create-drop",
            "spring.jpa.show-sql", "true",
            "spring.jpa.open-in-view", "false"));
        app.run(args).close();
    }
}