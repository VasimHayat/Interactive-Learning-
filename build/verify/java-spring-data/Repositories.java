package lm.repos;

import jakarta.persistence.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@SpringBootApplication
@EnableJpaRepositories(considerNestedRepositories = true)
public class Repositories {

    @Entity(name = "Product")
    @Table(name = "product")
    public static class Product {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE) Long id;
        @Version Long version;                                   // save() looks at this first
        @Column(nullable = false, unique = true, length = 16) String sku;
        int priceInCents;
        protected Product() {}
        Product(String sku, int priceInCents) { this.sku = sku; this.priceInCents = priceInCents; }
        static Product detached(Long id, Long version, String sku, int price) {   // a row we hold without a context
            Product p = new Product(sku, price); p.id = id; p.version = version; return p;
        }
        String sku() { return sku; }
        @Override public String toString() { return "Product[" + id + ", v" + version + ", " + sku + ", " + priceInCents + "c]"; }
    }

    /* An assigned id gives save() nothing to go on: id is never null, there is
       no @Version. Persistable lets the entity say whether it is new. */
    @Entity(name = "Tag")
    @Table(name = "tag")
    public static class Tag implements Persistable<String> {
        @Id String code;
        String label;
        @Transient private boolean isNew = true;
        protected Tag() {}
        Tag(String code, String label) { this.code = code; this.label = label; }
        @Override public String getId() { return code; }
        @Override public boolean isNew() { return isNew; }
        @PostPersist @PostLoad void markNotNew() { isNew = false; }
    }

    @NoRepositoryBean
    public interface BaseRepository<T, ID> extends JpaRepository<T, ID> {   // shared signatures, no bean of its own
        List<T> findAllSortedById();
    }

    public interface ProductRepositoryCustom { List<Product> cheaperThan(int cents); }

    public static class ProductRepositoryCustomImpl implements ProductRepositoryCustom {   // a fragment: hand-written body
        @PersistenceContext EntityManager em;
        @Override public List<Product> cheaperThan(int cents) {
            return em.createQuery("select p from Product p where p.priceInCents < :c order by p.sku", Product.class)
                     .setParameter("c", cents).getResultList();
        }
    }

    public interface ProductRepository extends JpaRepository<Product, Long>, ProductRepositoryCustom {}
    public interface TagRepository extends JpaRepository<Tag, String> {}

    @Bean
    CommandLineRunner demo(ProductRepository products, TagRepository tags, TransactionTemplate tx) {
        return args -> {
            System.out.println("-- the hierarchy --");
            System.out.println("JpaRepository extends " + Arrays.stream(JpaRepository.class.getInterfaces())
                    .map(Class::getSimpleName).sorted().toList());

            System.out.println("-- save: new entity (id null) --");
            Product bolt = products.save(new Product("BOLT-7", 80));
            System.out.println("saved " + bolt);

            System.out.println("-- save: detached entity (id and version set) --");
            Product copy = Product.detached(bolt.id, bolt.version, "BOLT-7", 85);
            Product merged = products.save(copy);
            System.out.println("merged == copy: " + (merged == copy) + ", merged = " + merged);

            System.out.println("-- save: looks detached, but the row does not exist --");
            try {
                products.save(Product.detached(404L, 0L, "GHOST-1", 1));
            } catch (RuntimeException e) {
                System.out.println("threw " + e.getClass().getSimpleName() + " caused by " + rootCause(e).getClass().getSimpleName());
            }

            System.out.println("-- save: Persistable decides --");
            Tag fast = tags.save(new Tag("FAST", "fast mover"));
            fast.label = "fast-moving";
            tags.save(fast);
            System.out.println("tags = " + tags.count());

            System.out.println("-- reads --");
            System.out.println("findById       = " + products.findById(bolt.id).map(Product::toString).orElse("none"));
            System.out.println("findById(404)  = " + products.findById(404L).isPresent());
            tx.executeWithoutResult(status -> {
                Product ref = products.getReferenceById(bolt.id);
                System.out.println("reference type = " + (ref.getClass() == Product.class ? "Product" : "a Hibernate proxy") + " (no select yet)");
                System.out.println("field read     = " + ref.sku + " (a field read bypasses the proxy)");
                System.out.println("getter read    = " + ref.sku());
            });
            System.out.println("existsById     = " + products.existsById(bolt.id));

            System.out.println("-- saveAndFlush inside a transaction --");
            tx.executeWithoutResult(status -> {
                products.save(new Product("NUT-3", 25));
                System.out.println("after save: nothing sent yet");
                products.saveAndFlush(new Product("CAM-9", 2300));
                System.out.println("after saveAndFlush: both inserts sent");
            });

            System.out.println("-- the custom fragment --");
            System.out.println("cheaperThan(100) = " + products.cheaperThan(100));

            System.out.println("-- deletes --");
            products.deleteById(bolt.id);
            System.out.println("remaining = " + products.count());
            products.deleteAllInBatch();
            System.out.println("remaining = " + products.count());
        };
    }

    static Throwable rootCause(Throwable t) { while (t.getCause() != null) t = t.getCause(); return t; }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(Repositories.class);
        app.setDefaultProperties(Map.of(
            "spring.main.web-application-type", "none",
            "spring.main.banner-mode", "off",
            "spring.main.log-startup-info", "false",
            "logging.level.root", "WARN",
            "logging.pattern.console", "%level %logger{0}: %msg%n",
            "spring.datasource.url", "jdbc:h2:mem:repos;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto", "create-drop",
            "spring.jpa.show-sql", "true",
            "spring.jpa.open-in-view", "false"));
        app.run(args).close();
    }
}