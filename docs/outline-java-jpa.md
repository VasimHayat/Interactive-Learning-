# JPA & Hibernate — module outline and authoring contract

Slug `java-jpa` · meta in `src/meta/java-jpa.json` · accent emerald · prev "Java Advanced" · no next.
Stack pinned in `tools/deps/java-jpa.json`: **Hibernate ORM 7.4.1.Final, Jakarta Persistence 3.2, H2 2.4 (in-memory)**,
Java 21. The build puts those jars on the class path of every verified program, so the SQL echoed on the page is
exactly what this Hibernate version emits.

Running example: the warehouse, finally persisted. `Product` (sku, name, `Category` enum, priceInCents),
`Order` with `OrderLine`s, `StockLevel`, a `Supplier` many-to-many, and a `Discount` hierarchy for the
inheritance section. Same SKUs as the other modules: `BOLT-7`, `NUT-3`, `CAM-9`, `GIRD-1`, `ROD-4`, `PIN-2`, `HUB-8`.

## Section table (ids, numbers, verified class names and widget names are reserved — do not change)

| File | `id` | `data-num` | `data-title` | Verified programs | Sandbox | Stepper ids |
|---|---|---|---|---|---|---|
| 00-how-to-use.html | how-to-use | i | How to use this module | — | — | — |
| 01-prerequisites.html | prerequisites | ii | Prerequisites | — | — | — |
| 02-objectives.html | objectives | iii | Learning objectives | — | — | — |
| 10-why-orm.html | why-orm | 1 | Why an ORM, and what it costs | `JdbcBaseline`, `FirstEntity` | — | `bootstrap` |
| 11-mapping.html | mapping | 2 | Mapping entities to tables | `Mapping` | `id-strategy` | — |
| 12-persistence-context.html | persistence-context | 3 | The persistence context and the entity lifecycle | `Lifecycle` | — | `lifecycle` |
| 13-relationships.html | relationships | 4 | Relationships and the owning side | `Relationships` | `owning-side` | — |
| 14-fetching.html | fetching | 5 | Fetching: lazy, eager and the N+1 problem | `NPlusOne`, `LazyInit` (throws) | `fetch-plan` | — |
| 15-queries.html | queries | 6 | JPQL, the Criteria API and native SQL | `Queries` | — | `jpql-translation` |
| 16-transactions-locking.html | transactions-locking | 7 | Transactions, flushing and locking | `Flushing`, `OptimisticLock` | — | `optimistic-lock` |
| 17-inheritance.html | inheritance | 8 | Inheritance mapping | `InheritanceMapping` (not `Inheritance` — it would shadow the `@Inheritance` annotation) | `inheritance-strategy` | — |
| 18-performance.html | performance | 9 | Batching, projections and caching | `Batching` | `batch-planner` | — |
| 19-schema.html | schema | 10 | Schema generation, validation and migrations | `SchemaValidate` (throws) | `ddl-auto` | — |
| 20-entity-design.html | entity-design | 11 | Designing entities: identity, equality and callbacks | `EntityDesign` | — | `hashcode-trap` |
| 21-debugging.html | debugging | 12 | Reading the SQL and the exceptions | `Exceptions` | — | — |
| 80-lab.html | lab | L | Hands-on lab | `WarehouseLab` | — | — |
| 81-cheatsheet.html | cheatsheet | C | Cheat sheet | — | — | — |
| 82-interview.html | interview | I | Interview questions | — | — | — |
| 83-quiz.html | quiz | Q | Self-assessment quiz | — | — | — |
| 84-glossary.html | glossary | G | Glossary | — | — | — |
| 85-reading.html | reading | R | Further reading | — | — | — |

Extra verified programs are welcome; name them `<SectionWord><Thing>`. Never name a program after a JDK or
library class (`Product`, `Session`, `Query`, `Executors`…) — all programs compile in one directory.

## How a verified program bootstraps (copy this shape)

```java
import jakarta.persistence.*;
import java.util.List;

public class FirstEntity {

    @Entity(name = "Product")              // REQUIRED for a nested class: the JPQL name would otherwise be FirstEntity$Product
    @Table(name = "product")
    public static class Product {          // public static, non-final, protected no-arg constructor
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE)
        private Long id;
        @Column(nullable = false, unique = true, length = 16)
        private String sku;
        private int priceInCents;
        protected Product() {}
        Product(String sku, int priceInCents) { this.sku = sku; this.priceInCents = priceInCents; }
        String sku() { return sku; }
        @Override public String toString() { return "Product[" + id + ", " + sku + ", " + priceInCents + "c]"; }
    }

    static EntityManagerFactory emf(Class<?>... entities) {
        var cfg = new PersistenceConfiguration("warehouse")                       // JPA 3.2: no persistence.xml needed
            .property(PersistenceConfiguration.JDBC_URL, "jdbc:h2:mem:FirstEntity;DB_CLOSE_DELAY=-1")   // one DB per program
            .property(PersistenceConfiguration.JDBC_USER, "sa")
            .property(PersistenceConfiguration.JDBC_PASSWORD, "")
            .property(PersistenceConfiguration.SCHEMAGEN_DATABASE_ACTION, "drop-and-create")
            .property("hibernate.show_sql", "true");                                 // echoes SQL to STDOUT — it is diffed
        for (Class<?> c : entities) cfg.managedClass(c);
        return cfg.createEntityManagerFactory();
    }

    public static void main(String[] args) {
        try (EntityManagerFactory emf = emf(Product.class)) {
            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                em.persist(new Product("BOLT-7", 80));
                em.getTransaction().commit();
            }
            try (EntityManager em = emf.createEntityManager()) {
                List<Product> all = em.createQuery("select p from Product p order by p.sku", Product.class).getResultList();
                System.out.println("loaded " + all);
            }
        }
    }
}
```

What that prints with this Hibernate version (the expected-output block must include every `Hibernate:` line):

```
Hibernate: create global temporary table HTE_product(priceInCents integer, rn_ integer not null, id bigint, sku varchar(16), primary key (rn_)) transactional
Hibernate: drop table if exists product cascade 
Hibernate: drop sequence if exists product_SEQ
Hibernate: create sequence product_SEQ start with 1 increment by 50
Hibernate: create table product (priceInCents integer not null, id bigint not null, sku varchar(16) not null unique, primary key (id))
Hibernate: select next value for product_SEQ
Hibernate: insert into product (priceInCents,sku,id) values (?,?,?)
Hibernate: select p1_0.id,p1_0.priceInCents,p1_0.sku from product p1_0 order by p1_0.sku
loaded [Product[1, BOLT-7, 80c]]
```

- The `HTE_…` global temporary table is Hibernate 7's scratch table for bulk HQL `update`/`delete` on H2; §1 explains it once, later sections just include it.
- `SEQUENCE` allocates in blocks of 50 (`increment by 50`): one `select next value` per 50 inserts. Ids are deterministic in a fresh in-memory DB: 1, 2, 3 … — print them.
- Keep `hibernate.format_sql` off (one line per statement). Column order in generated SQL is Hibernate's, not yours — paste what it prints.
- Statement counting for N+1 and batching: `.property("hibernate.generate_statistics", "true")` then
  `emf.unwrap(org.hibernate.SessionFactory.class).getStatistics().getPrepareStatementCount()` (call `clear()` between phases). Deterministic.
- Hibernate WARN/INFO lines go to **stderr** (not diffed). `WARN: HHH10001002: Using built-in connection pool` is expected.
- A program whose point is an exception (`LazyInitializationException`, `OptimisticLockException`, `SchemaManagementException`) either catches it and prints `e.getClass().getSimpleName()`, or declares `data-verify-throws="…"` and lets it escape.
- Never print: timestamps, `now()`, hash codes, H2 version strings, absolute paths, HashSet order, or `Statistics.getQueryExecutionMaxTime`.
- Each program costs ~8–10 s (Hibernate bootstrap); the build runs four at a time. Prefer one `EntityManagerFactory` per program that demonstrates several things, over many tiny programs.
- Lazy loading needs non-final entity classes and works on nested `public static` classes. `@Enumerated(EnumType.STRING)` for enums. Use `java.time` types directly (no `@Temporal`).

## Learning objectives (the quiz tests exactly these)

1. **Explain** the object–relational impedance mismatch, what JPA standardises and what Hibernate adds, and read the SQL Hibernate emits for a simple persist and load.
2. **Map** an entity with `@Id`, a generation strategy chosen for the database, `@Column` constraints, enums as strings, `java.time` columns and `@Embeddable` value objects, and say what each generation strategy costs.
3. **Trace** an entity through transient, managed, detached and removed states, predict when the first-level cache serves a load, and explain dirty checking and flush timing.
4. **Model** many-to-one, one-to-many, many-to-many and one-to-one associations, identify the owning side, and keep both sides in sync with cascade and orphan removal chosen deliberately.
5. **Diagnose** the N+1 problem from statement counts and fix it with `join fetch`, an entity graph or batch fetching; explain `LazyInitializationException` and why open-session-in-view hides rather than solves it.
6. **Write** JPQL with joins, parameters, aggregates, constructor projections and pagination; build the same query with the Criteria API; know when native SQL is the right choice.
7. **Control** transaction boundaries and flushing, and implement optimistic locking with `@Version` and pessimistic locking with `LockModeType`, naming the failure each prevents.
8. **Choose** an inheritance strategy (single table, joined, table per class, mapped superclass) from the DDL and query shape each produces.
9. **Tune** writes with JDBC batching and ordered inserts, reads with DTO projections and read-only sessions, and explain what the second-level and query caches can and cannot do.
10. **Manage** schema with generation modes for development, `validate` for safety, and a migration tool for production; explain why `update` is never a deployment strategy.
11. **Design** entities that behave in collections (identity, `equals`/`hashCode` on a business key or a stable id), use lifecycle callbacks and entity listeners for auditing, and model soft deletes.
12. **Read** `show_sql` output and statistics to explain a slow page, and map the common exceptions to their causes and fixes.

Out of scope: Spring Data / Spring Boot (one paragraph on how it wraps all this), Envers, multi-tenancy, reactive Hibernate, jOOQ (one mention as the alternative), connection pools in depth (HikariCP mention).

## Authoring contract (identical to the other modules — see README.md and `src/sections/java-advanced/*` for live templates)

- Section wrapper, eyebrow "Section N", `<h2>`, completion checkbox `data-done-for="<id>"` on every core section and the lab.
- Core sections: motivating explanation (mechanism, not rules), a **Worked example** verified program whose echoed SQL is read line by line in the prose that follows it, at least one interactive widget, a gotcha callout, **Check yourself** with ≥3 questions (`data-check`, `data-live`, `data-explain`, every wrong option explained).
- Ids: `q-<section>-N`, `tab-<section>-x` / `panel-<section>-x`, unique across the module. Steppers are **Simulated** (badge, pre-authored steps, every `line` must exist in `code`).
- Sandbox handler in `src/js/sandboxes/java-jpa-<name>.js` (`reg('<name>', fn)`, `H.esc`, `H.renderCode(src, { lang: 'sql' or 'java', noBar: true })` — `sql` falls back to plain if the highlighter has no SQL grammar; that is fine).
- Prose `<`/`&` as entities outside `<pre data-code>`. No `<code>` inside `<option>`.
- Verify in isolation: `node tools/build.mjs --topic=java-jpa --files=10-why-orm.html,11-mapping.html` then `npx html-validate build/partial/java-jpa.10-why-orm.html`. The first build copies the jars into `build/lib/java-jpa/` automatically.
- Tone: precise, second person, explain the mechanism and the failure it prevents. Cross-reference as "Fundamentals §N", "Intermediate §N", "Advanced §N", "§N" (this module).
