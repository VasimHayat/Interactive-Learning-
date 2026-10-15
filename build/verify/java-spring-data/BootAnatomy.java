package lm.anatomy;

import jakarta.persistence.*;
import jakarta.persistence.metamodel.EntityType;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.PlatformTransactionManager;
import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootApplication
@EnableJpaRepositories(considerNestedRepositories = true)   // the repositories below are nested interfaces
public class BootAnatomy {

    @Entity(name = "Product")                                // nested entities need an explicit JPQL name
    @Table(name = "product")
    public static class Product {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE) Long id;
        @Column(nullable = false, unique = true, length = 16) String sku;
        int priceInCents;                                    // becomes price_in_cents — read on
        protected Product() {}
        Product(String sku, int priceInCents) { this.sku = sku; this.priceInCents = priceInCents; }
        @Override public String toString() { return "Product[" + id + ", " + sku + ", " + priceInCents + "c]"; }
    }

    public interface ProductRepository extends JpaRepository<Product, Long> {
        List<Product> findBySkuStartingWithOrderBySku(String prefix);   // no body: derived from the name (§3)
    }

    @Bean
    CommandLineRunner demo(DataSource dataSource, PlatformTransactionManager txManager,
                           EntityManagerFactory emf, ProductRepository repo, Environment env) {
        return args -> {
            System.out.println("-- what the starter configured --");
            System.out.println("DataSource          = " + dataSource.getClass().getSimpleName());
            System.out.println("TransactionManager  = " + txManager.getClass().getSimpleName());
            System.out.println("repository is proxy = " + AopUtils.isAopProxy(repo));
            System.out.println("proxy interfaces    = " + Arrays.stream(repo.getClass().getInterfaces())
                    .map(Class::getSimpleName).filter(n -> n.contains("Repository")).sorted().toList());
            System.out.println("entities            = " + emf.getMetamodel().getEntities().stream()
                    .map(EntityType::getName).sorted().toList());
            System.out.println("naming strategy     = " + String.valueOf(emf.getProperties().get("hibernate.physical_naming_strategy"))
                    .replaceAll(".*\\.", ""));
            System.out.println("ddl-auto            = " + env.getProperty("spring.jpa.hibernate.ddl-auto"));
            System.out.println("open-in-view        = " + env.getProperty("spring.jpa.open-in-view"));

            System.out.println("-- one save, one derived query --");
            repo.save(new Product("BOLT-7", 80));
            repo.save(new Product("NUT-3", 25));
            repo.save(new Product("BOLT-9", 95));
            System.out.println("count = " + repo.count());
            System.out.println("bolts = " + repo.findBySkuStartingWithOrderBySku("BOLT"));
        };
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(BootAnatomy.class);
        app.setDefaultProperties(Map.of(
            "spring.main.web-application-type", "none",
            "spring.main.banner-mode", "off",
            "spring.main.log-startup-info", "false",
            "logging.level.root", "WARN",
            "logging.pattern.console", "%level %logger{0}: %msg%n",
            "spring.datasource.url", "jdbc:h2:mem:anatomy;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto", "create-drop",
            "spring.jpa.show-sql", "true",
            "spring.jpa.open-in-view", "false"));
        app.run(args).close();
    }
}