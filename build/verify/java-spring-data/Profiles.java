package lm.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@SpringBootApplication
@EnableJpaRepositories(considerNestedRepositories = true)
@EnableConfigurationProperties(Profiles.WarehouseProps.class)
public class Profiles {

    // ── a tiny entity so the data source and the seed script have something to hit ──
    @Entity(name = "Product")
    @Table(name = "product")
    public static class Product {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE) Long id;
        @Column(nullable = false, unique = true, length = 16) String sku;
        int priceInCents;
        protected Product() {}
    }
    public interface ProductRepository extends JpaRepository<Product, Long> {}

    // ── one interface, two implementations, one of them active per profile ──
    public interface PricingPolicy { String describe(); }

    @Component @Profile("dev")
    public static class ListPricePolicy implements PricingPolicy {
        public String describe() { return "list price, no rounding (dev)"; }
    }
    @Component @Profile("prod")
    public static class ContractPricePolicy implements PricingPolicy {
        public String describe() { return "contract price with psychological rounding (prod)"; }
    }

    // ── type-safe, validated configuration ──
    @ConfigurationProperties(prefix = "warehouse")
    @Validated
    public record WarehouseProps(@NotBlank String region,
                                 @Min(1) int reorderThreshold,
                                 List<String> prioritySkus) {}

    @Bean CommandLineRunner show(Environment env, PricingPolicy pricing, WarehouseProps props,
                                 DataSource dataSource, EntityManagerFactory emf, ProductRepository products,
                                 @Value("${warehouse.currency:EUR}") String currency) {
        return args -> {
            System.out.println("active profiles = " + String.join(",", env.getActiveProfiles()));
            System.out.println("pricing policy  = " + pricing.describe());
            System.out.println("props           = " + props);
            System.out.println("currency        = " + currency + "   (not set anywhere: the @Value default)");

            HikariDataSource hikari = (HikariDataSource) dataSource;
            System.out.println("pool            = " + hikari.getPoolName() + ", max " + hikari.getMaximumPoolSize()
                    + ", min idle " + hikari.getMinimumIdle());
            System.out.println("batch_size      = " + emf.getProperties().get("hibernate.jdbc.batch_size")
                    + "   (reached Hibernate through spring.jpa.properties.*)");

            System.out.println("seeded rows     = " + products.count() + "   (inserted by the spring.sql.init script, not by Hibernate)");

            System.out.println("warehouse.site  = " + env.getProperty("warehouse.site")
                    + "   (default properties said 'default-properties')");
            System.out.println("warehouse.region= " + env.getProperty("warehouse.region")
                    + "   (only the default properties define it)");
        };
    }

    public static void main(String[] args) throws Exception {
        // a seed script Spring Boot will run after Hibernate has created the schema
        Path seed = Path.of("warehouse-seed.sql");
        Files.writeString(seed, """
            insert into product (id, sku, price_in_cents) values (1, 'BOLT-7', 80);
            insert into product (id, sku, price_in_cents) values (2, 'NUT-3', 25);
            insert into product (id, sku, price_in_cents) values (3, 'CAM-9', 2300);
            """);
        // a system property outranks the application's default properties
        System.setProperty("warehouse.site", "system-property");
        try {
            SpringApplication app = new SpringApplication(Profiles.class);
            app.setDefaultProperties(Map.ofEntries(
                Map.entry("spring.main.web-application-type", "none"),
                Map.entry("spring.main.banner-mode", "off"),
                Map.entry("spring.main.log-startup-info", "false"),
                Map.entry("logging.level.root", "WARN"),
                Map.entry("logging.pattern.console", "%level %logger{0}: %msg%n"),
                Map.entry("spring.profiles.active", "dev"),
                Map.entry("spring.datasource.url", "jdbc:h2:mem:config;DB_CLOSE_DELAY=-1"),
                Map.entry("spring.datasource.hikari.pool-name", "warehouse-pool"),
                Map.entry("spring.datasource.hikari.maximum-pool-size", "4"),
                Map.entry("spring.datasource.hikari.minimum-idle", "1"),
                Map.entry("spring.jpa.hibernate.ddl-auto", "create-drop"),
                Map.entry("spring.jpa.show-sql", "true"),
                Map.entry("spring.jpa.open-in-view", "false"),
                Map.entry("spring.jpa.properties.hibernate.jdbc.batch_size", "20"),
                Map.entry("spring.jpa.defer-datasource-initialization", "true"),
                Map.entry("spring.sql.init.mode", "always"),
                Map.entry("spring.sql.init.data-locations", "file:warehouse-seed.sql"),
                Map.entry("warehouse.region", "eu-west"),
                Map.entry("warehouse.reorder-threshold", "25"),
                Map.entry("warehouse.priority-skus", "BOLT-7,GIRD-1"),
                Map.entry("warehouse.site", "default-properties")));
            app.run(args).close();
        } finally {
            Files.deleteIfExists(seed);
        }
    }
}