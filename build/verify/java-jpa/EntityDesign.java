import jakarta.persistence.*;
import org.hibernate.Session;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class EntityDesign {

    /* Option 1 — equals/hashCode on the generated id. Looks obvious, breaks in a Set. */
    @Entity(name = "IdHashProduct")
    @Table(name = "id_hash_product")
    public static class IdHashProduct {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE) Long id;
        @Column(nullable = false, length = 16) String sku;
        protected IdHashProduct() {}
        IdHashProduct(String sku) { this.sku = sku; }
        @Override public boolean equals(Object o) { return o instanceof IdHashProduct p && Objects.equals(id, p.id); }
        @Override public int hashCode() { return Objects.hashCode(id); }
    }

    /* Option 2 — a business key that never changes. */
    @Entity(name = "SkuProduct")
    @Table(name = "sku_product")
    public static class SkuProduct {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE) Long id;
        @Column(nullable = false, unique = true, length = 16) String sku;
        protected SkuProduct() {}
        SkuProduct(String sku) { this.sku = sku; }
        @Override public boolean equals(Object o) { return o instanceof SkuProduct p && sku.equals(p.sku); }
        @Override public int hashCode() { return sku.hashCode(); }
    }

    /* Option 3 — id equality once assigned, a constant hash so the bucket never moves. */
    @Entity(name = "SafeProduct")
    @Table(name = "safe_product")
    public static class SafeProduct {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE) Long id;
        @Column(nullable = false, length = 16) String sku;
        protected SafeProduct() {}
        SafeProduct(String sku) { this.sku = sku; }
        @Override public boolean equals(Object o) { return o instanceof SafeProduct p && id != null && id.equals(p.id); }
        @Override public int hashCode() { return getClass().hashCode(); }
    }

    /* Auditing: callbacks on the entity plus an external listener. */
    public static class AuditListener {
        static int prePersist, preUpdate;
        @PrePersist void beforeInsert(Object e) { prePersist++; }
        @PreUpdate  void beforeUpdate(Object e) { preUpdate++; }
    }

    @Entity(name = "AuditedProduct")
    @Table(name = "audited_product")
    @EntityListeners(AuditListener.class)
    public static class AuditedProduct {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE) Long id;
        @NaturalId @Column(nullable = false, length = 16) String sku;
        int priceInCents;
        @Column(nullable = false, updatable = false) Instant createdAt;
        @Column(nullable = false) Instant updatedAt;
        protected AuditedProduct() {}
        AuditedProduct(String sku, int priceInCents) { this.sku = sku; this.priceInCents = priceInCents; }
        @PrePersist void stamp() { createdAt = updatedAt = Instant.now(); }
        @PreUpdate  void touch() { updatedAt = Instant.now().plusSeconds(1); }   // +1s so the fact below cannot tie
    }

    /* Soft delete: DELETE becomes UPDATE, and every query gets a filter for free. */
    @Entity(name = "SoftProduct")
    @Table(name = "soft_product")
    @SQLDelete(sql = "update soft_product set deleted = true where id = ?")
    @SQLRestriction("deleted = false")
    public static class SoftProduct {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE) Long id;
        @Column(nullable = false, length = 16) String sku;
        boolean deleted;
        protected SoftProduct() {}
        SoftProduct(String sku) { this.sku = sku; }
    }

    /* Reference data that never changes: Hibernate skips dirty checking entirely. */
    @Entity(name = "Currency")
    @Table(name = "currency")
    @Immutable
    public static class Currency {
        @Id @Column(length = 3) String code;
        String name;
        protected Currency() {}
        Currency(String code, String name) { this.code = code; this.name = name; }
    }

    static EntityManagerFactory emf(boolean showSql, Class<?>... entities) {
        var cfg = new PersistenceConfiguration("warehouse")
            .property(PersistenceConfiguration.JDBC_URL, "jdbc:h2:mem:EntityDesign;DB_CLOSE_DELAY=-1")
            .property(PersistenceConfiguration.JDBC_USER, "sa")
            .property(PersistenceConfiguration.JDBC_PASSWORD, "")
            .property(PersistenceConfiguration.SCHEMAGEN_DATABASE_ACTION, "drop-and-create")
            .property("hibernate.show_sql", String.valueOf(showSql));
        for (Class<?> c : entities) cfg.managedClass(c);
        return cfg.createEntityManagerFactory();
    }

    public static void main(String[] args) {
        System.out.println("-- equality: add to a Set before persist, look it up after --");
        try (EntityManagerFactory emf = emf(false, IdHashProduct.class, SkuProduct.class, SafeProduct.class)) {
            IdHashProduct a = new IdHashProduct("BOLT-7");
            SkuProduct b = new SkuProduct("BOLT-7");
            SafeProduct c = new SafeProduct("BOLT-7");
            Set<IdHashProduct> setA = new HashSet<>(Set.of(a));
            Set<SkuProduct> setB = new HashSet<>(Set.of(b));
            Set<SafeProduct> setC = new HashSet<>(Set.of(c));
            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                em.persist(a); em.persist(b); em.persist(c);
                em.getTransaction().commit();
            }
            System.out.println("id-based hashCode : id=" + a.id + ", set.contains = " + setA.contains(a));
            System.out.println("business key      : id=" + b.id + ", set.contains = " + setB.contains(b));
            System.out.println("id + constant hash: id=" + c.id + ", set.contains = " + setC.contains(c));

            try (EntityManager em = emf.createEntityManager()) {          // a second context: a different Java object
                SafeProduct reloaded = em.find(SafeProduct.class, c.id);
                System.out.println("reloaded == c: " + (reloaded == c) + ", reloaded.equals(c): " + reloaded.equals(c)
                        + ", set.contains(reloaded): " + setC.contains(reloaded));
            }
            System.out.println("two transient SafeProducts equal: " + new SafeProduct("X").equals(new SafeProduct("X")));
        }

        System.out.println("-- callbacks, soft delete, immutable, natural id --");
        try (EntityManagerFactory emf = emf(true, AuditedProduct.class, SoftProduct.class, Currency.class)) {
            System.out.println("- @PrePersist / @PreUpdate -");
            Long auditedId;
            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                AuditedProduct p = new AuditedProduct("BOLT-7", 80);
                em.persist(p);
                em.getTransaction().commit();
                auditedId = p.id;
                System.out.println("createdAt set: " + (p.createdAt != null) + ", createdAt equals updatedAt: " + p.createdAt.equals(p.updatedAt));
            }
            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                AuditedProduct p = em.find(AuditedProduct.class, auditedId);
                p.priceInCents = 90;
                em.getTransaction().commit();
                System.out.println("updatedAt moved after update: " + p.updatedAt.isAfter(p.createdAt));
            }
            System.out.println("listener calls: prePersist=" + AuditListener.prePersist + ", preUpdate=" + AuditListener.preUpdate);

            System.out.println("- @NaturalId lookup -");
            try (EntityManager em = emf.createEntityManager()) {
                AuditedProduct p = em.unwrap(Session.class).byNaturalId(AuditedProduct.class).using("sku", "BOLT-7").load();
                System.out.println("by natural id: " + p.sku + " at " + p.priceInCents + "c");
            }

            System.out.println("- soft delete -");
            Long softId;
            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                SoftProduct s = new SoftProduct("NUT-3");
                em.persist(s);
                em.getTransaction().commit();
                softId = s.id;
            }
            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                em.remove(em.find(SoftProduct.class, softId));
                em.getTransaction().commit();
            }
            try (EntityManager em = emf.createEntityManager()) {
                long jpql = em.createQuery("select count(s) from SoftProduct s", Long.class).getSingleResult();
                Number sql = (Number) em.createNativeQuery("select count(*) from soft_product").getSingleResult();
                System.out.println("JPQL count = " + jpql + ", native count = " + sql);
            }

            System.out.println("- @Immutable: a change is silently ignored -");
            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                em.persist(new Currency("GBP", "Pound sterling"));
                em.getTransaction().commit();
            }
            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                Currency gbp = em.find(Currency.class, "GBP");
                gbp.name = "changed";
                em.getTransaction().commit();
            }
            try (EntityManager em = emf.createEntityManager()) {
                System.out.println("GBP name = " + em.find(Currency.class, "GBP").name);
            }
        }
    }
}