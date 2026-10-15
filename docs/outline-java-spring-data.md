# Spring Data JPA — module outline and authoring contract

Slug `java-spring-data` · meta in `src/meta/java-spring-data.json` · accent rose · prev "JPA & Hibernate" · no next.
Stack: **Spring Boot 4.1.1** (BOM-managed: Spring Framework 7.0, Spring Data 2026.0 / spring-data-jpa 4.1.1,
Hibernate 7.4.5, HikariCP 7, Logback 1.5, Jakarta Validation via hibernate-validator 9), **H2 2.4**, Java 21.
Resolved by Maven from `tools/deps/java-spring-data.json` into `build/lib/java-spring-data/` (61 jars) and put on the
class path of every verified program. The SQL echoed on the page is what Spring Boot 4.1 + Hibernate 7.4.5 emit.

Running example: the warehouse as a Spring Boot application. `Product` (sku, name, `Category`, priceInCents),
`Order`/`OrderLine`, `StockLevel`, `Supplier`; repositories per aggregate; an `InventoryService`. Same SKUs as the
other modules.

## Section table (ids, numbers, verified class names and widget names are reserved — do not change)

| File | `id` | `data-num` | `data-title` | Verified programs (`data-package`) | Sandbox | Stepper ids |
|---|---|---|---|---|---|---|
| 00-how-to-use.html | how-to-use | i | How to use this module | — | — | — |
| 01-prerequisites.html | prerequisites | ii | Prerequisites | — | — | — |
| 02-objectives.html | objectives | iii | Learning objectives | — | — | — |
| 10-boot-anatomy.html | boot-anatomy | 1 | A Spring Boot data application, dissected | `BootAnatomy` (lm.anatomy) | `property-precedence` | `autoconfig` |
| 11-repositories.html | repositories | 2 | Repositories: from CrudRepository to JpaRepository | `Repositories` (lm.repos) | — | `save-decision` |
| 12-derived-queries.html | derived-queries | 3 | Derived queries: method names that become SQL | `DerivedQueries` (lm.derived) | `method-name-parser` | — |
| 13-declared-queries.html | declared-queries | 4 | @Query, modifying queries and projections | `DeclaredQueries` (lm.declared) | — | — |
| 14-transactions.html | transactions | 5 | @Transactional: boundaries, propagation and the proxy | `Transactions` (lm.tx) | `propagation` | `tx-proxy` |
| 15-fetching.html | fetching | 6 | Fetching and N+1 in a Spring application | `SpringFetching` (lm.fetching) | `fetch-decision` | — |
| 16-paging.html | paging | 7 | Pagination, sorting, slices and keyset scrolling | `Paging` (lm.paging) | `pagination-cost` | — |
| 17-auditing-events.html | auditing-events | 8 | Auditing, lifecycle and domain events | `Auditing` (lm.auditing) | — | `event-flow` |
| 18-specifications.html | specifications | 9 | Dynamic queries: Specifications and Query by Example | `Specifications` (lm.specs) | `spec-builder` | — |
| 19-validation-errors.html | validation-errors | 10 | Validation and exception translation | `Validation` (lm.validation) | — | — |
| 20-testing.html | testing | 11 | Testing the data layer | `RollbackHarness` (lm.testing) | — | `test-tx` |
| 21-configuration.html | configuration | 12 | Configuration, profiles and production settings | `Profiles` (lm.config) | `hikari-sizer` | — |
| 80-lab.html | lab | L | Hands-on lab | `SpringWarehouseLab` (lm.lab) | — | — |
| 81–85 | cheatsheet / interview / quiz / glossary / reading | C / I / Q / G / R | — | — | — | — |

Never name a program after a Spring, JPA or JDK class. Every program has its OWN package (table above) and declares it on
the first line; the build runs `<package>.<Class>`. Extra programs go in the same package as their section.

## How a verified program bootstraps (copy this shape — it is what keeps stdout deterministic)

```java
package lm.anatomy;

import jakarta.persistence.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import java.util.List;
import java.util.Map;

@SpringBootApplication
@EnableJpaRepositories(considerNestedRepositories = true)   // repositories are nested interfaces of the app class
public class BootAnatomy {

    @Entity(name = "Product")                                // nested entities need an explicit JPQL name
    @Table(name = "product")
    public static class Product {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE) Long id;
        @Column(nullable = false, unique = true, length = 16) String sku;
        int priceInCents;
        protected Product() {}
        Product(String sku, int priceInCents) { this.sku = sku; this.priceInCents = priceInCents; }
        @Override public String toString() { return "Product[" + id + ", " + sku + ", " + priceInCents + "c]"; }
    }

    public interface ProductRepository extends JpaRepository<Product, Long> {
        List<Product> findBySkuStartingWithOrderBySku(String prefix);
    }

    @Bean CommandLineRunner demo(ProductRepository repo) {
        return args -> {
            repo.save(new Product("BOLT-7", 80));
            System.out.println("count = " + repo.count());
        };
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(BootAnatomy.class);
        app.setDefaultProperties(Map.of(
            "spring.main.web-application-type", "none",
            "spring.main.banner-mode", "off",
            "spring.main.log-startup-info", "false",
            "logging.level.root", "WARN",                        // INFO startup chatter would be nondeterministic
            "logging.pattern.console", "%level %logger{0}: %msg%n",   // no timestamps if a WARN does appear
            "spring.datasource.url", "jdbc:h2:mem:anatomy;DB_CLOSE_DELAY=-1",   // one DB per program
            "spring.jpa.hibernate.ddl-auto", "create-drop",
            "spring.jpa.show-sql", "true",                       // SQL to stdout — it is diffed
            "spring.jpa.open-in-view", "false"));                // also silences the OSIV warning
        app.run(args).close();
    }
}
```

What the smoke test of that shape printed (expected-output blocks must contain every line, including the `create-drop`
teardown at the end):

```
Hibernate: create global temporary table HTE_product(priceInCents integer, rn_ integer not null, id bigint, sku varchar(16), primary key (rn_)) transactional
Hibernate: drop table if exists product cascade
Hibernate: drop sequence if exists product_seq
Hibernate: create sequence product_seq start with 1 increment by 50
Hibernate: create table product (price_in_cents integer not null, id bigint not null, sku varchar(16) not null unique, primary key (id))
Hibernate: select next value for product_seq
Hibernate: insert into product (price_in_cents,sku,id) values (?,?,?)
Hibernate: select count(*) from product p1_0
count = 1
Hibernate: drop table if exists product cascade
Hibernate: drop sequence if exists product_seq
```

- Spring Boot's default naming strategy is snake_case (`price_in_cents`, `product_seq`) — unlike plain Hibernate in the JPA module. §1 explains it once.
- Logging goes through Logback to **stdout**, so root is WARN and the pattern carries no timestamp. If a WARN still appears in your output, fix its cause (preferred) or set `logging.level.<logger>` to ERROR in the properties map — and mention it in prose. Never leave a line that could vary.
- More than one program may run at once; keep H2 database names unique per program.
- Each program costs ~15 s (Boot + Hibernate bootstrap). Prefer one application per section that demonstrates several things through one `CommandLineRunner` (or several `@Order`ed runners).
- `@Transactional` needs a proxied bean: put services in nested `public static class` with `@Service`/`@Component`; self-invocation bypasses the proxy (§5 teaches this — use it).
- `@Bean CommandLineRunner` runs after the context starts, inside no transaction; call services/repositories from there. For a transactional runner use a `TransactionTemplate` or a `@Service` method.
- Statement counting: `spring.jpa.properties.hibernate.generate_statistics=true`, then `emf.unwrap(SessionFactory.class).getStatistics()`; call `clear()` between phases; print counts only.
- Facts only: never timestamps (`@CreatedDate` → print `!= null`), never bean counts, never ports, never thread names, never Hibernate/Spring version strings, never `hashCode`.

## Learning objectives (the quiz tests exactly these)

1. **Explain** what `spring-boot-starter-data-jpa` auto-configures, in what order, and how the default property set (naming strategy, `ddl-auto`, `open-in-view`) differs from plain Hibernate.
2. **Choose** the right repository interface and predict what `save` does for a new versus an existing entity, including the role of `@Version` and `Persistable`.
3. **Derive** a query from a method name using the keyword grammar, predict its SQL, and know when a derived name has become unreadable.
4. **Declare** queries with `@Query` (JPQL and native), write `@Modifying` updates safely, and choose between interface, class and dynamic projections.
5. **Draw** the transaction boundary of a request through the `@Transactional` proxy, name each propagation level's effect, explain the self-invocation trap and the checked-exception rollback rule.
6. **Diagnose** N+1 in a Spring service from statement counts and fix it with `@EntityGraph` or a fetch query, with `open-in-view` off.
7. **Paginate** with `Pageable`/`Page`/`Slice`, explain the count query, and choose keyset scrolling for deep pages.
8. **Audit** entities with `@CreatedDate`/`@LastModifiedDate`/`@CreatedBy` and publish domain events that fire after commit.
9. **Compose** dynamic filters with `Specification` and Query by Example, and say where each stops being the right tool.
10. **Validate** entities and service inputs with Bean Validation and map Spring's translated `DataAccessException` hierarchy to causes.
11. **Test** the data layer with `@DataJpaTest`, `TestEntityManager`, `@Sql` and Testcontainers, and explain why test transactions roll back.
12. **Configure** profiles, `@ConfigurationProperties`, the Hikari pool and production-safe JPA settings.

Out of scope: Spring MVC/REST controllers (one paragraph on DTOs at the boundary), Spring Security, Spring Data REST,
reactive/R2DBC, Spring Data JDBC (one comparison paragraph), Querydsl (mention), Envers.

## Authoring contract (identical to the other modules — see README.md and `src/sections/java-jpa/*` for live templates)

- Section wrapper, eyebrow "Section N", `<h2>`, completion checkbox `data-done-for="<id>"` on every core section and the lab.
- Core sections: motivating explanation, a **Worked example** verified program whose echoed SQL/facts are read line by line, at least one interactive widget, a gotcha callout, **Check yourself** with ≥3 questions (`data-check`, `data-live`, `data-explain`, every wrong option explained).
- Ids: `q-<section>-N`, `tab-<section>-x` / `panel-<section>-x`, unique across the module. Steppers are **Simulated**.
- Sandbox handler in `src/js/sandboxes/java-spring-data-<name>.js` (`reg('<name>', fn)`, `H.esc`, `H.renderCode(src, { lang: 'java'|'sql', noBar: true })`).
- `<pre data-code … data-verify="X" data-package="lm.x">` and the source's first line `package lm.x;` must agree (the build checks).
- Prose `<`/`&` as entities outside `<pre data-code>`. No `<code>` inside `<option>`.
- Verify in isolation: `node tools/build.mjs --topic=java-spring-data --files=10-boot-anatomy.html,11-repositories.html` then `npx html-validate build/partial/java-spring-data.10-boot-anatomy.html`. Jars are already resolved in `build/lib/java-spring-data/` (61 jars); for scratchpad runs use `-cp "classes;F:/Cluade/learning-modules/build/lib/java-spring-data/*"`.
- Tone: precise, second person, explain the mechanism and the failure it prevents. Cross-reference as "JPA §N" for the JPA module, "Advanced §N", "§N" (this module).
