import jakarta.persistence.*;
import org.hibernate.annotations.NaturalId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class Mapping {

    enum Category { FASTENER, STRUCTURAL, TOOLING }

    /** A value object stored inline in the owner's row: three columns, no identity, no table. */
    @Embeddable
    public static class Dimensions {
        private int widthMm, heightMm, depthMm;
        protected Dimensions() {}
        Dimensions(int w, int h, int d) { widthMm = w; heightMm = h; depthMm = d; }
        long volumeMm3() { return (long) widthMm * heightMm * depthMm; }
        @Override public String toString() { return widthMm + "x" + heightMm + "x" + depthMm + " mm"; }
    }

    /** A domain type the database never sees: the converter stores it as one bigint of cents. */
    record Money(long cents) {
        @Override public String toString() { return String.format("%d.%02d", cents / 100, cents % 100); }
    }
    @Converter(autoApply = true)
    public static class MoneyConverter implements AttributeConverter<Money, Long> {
        @Override public Long convertToDatabaseColumn(Money m) { return m == null ? null : m.cents(); }
        @Override public Money convertToEntityAttribute(Long cents) { return cents == null ? null : new Money(cents); }
    }

    @Entity(name = "Product")
    @Table(name = "product")
    @Access(AccessType.FIELD)                                   // map fields, not getters
    public static class Product {
        @Id
        @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_seq")
        @SequenceGenerator(name = "product_seq", sequenceName = "product_seq", allocationSize = 10)
        private Long id;

        @NaturalId                                              // Hibernate: a business key with its own lookup
        @Column(nullable = false, unique = true, length = 16)
        private String sku;

        @Basic(optional = false)
        @Column(length = 80)
        private String name;

        @Enumerated(EnumType.STRING)                            // never ORDINAL
        @Column(nullable = false, length = 20)
        private Category category;

        @Column(name = "unit_price")
        private Money unitPrice;                                // MoneyConverter applies automatically

        @Column(precision = 8, scale = 3)
        private BigDecimal weightKg;

        @Embedded
        @AttributeOverride(name = "widthMm",  column = @Column(name = "dim_w_mm"))
        @AttributeOverride(name = "heightMm", column = @Column(name = "dim_h_mm"))
        @AttributeOverride(name = "depthMm",  column = @Column(name = "dim_d_mm"))
        private Dimensions dimensions;

        private LocalDate introducedOn;                         // java.time maps directly: no @Temporal
        private Instant createdAt;

        @Transient
        private String label;                                   // derived, never a column

        protected Product() {}
        Product(String sku, String name, Category category, Money unitPrice, BigDecimal weightKg,
                Dimensions dimensions, LocalDate introducedOn, Instant createdAt) {
            this.sku = sku; this.name = name; this.category = category; this.unitPrice = unitPrice;
            this.weightKg = weightKg; this.dimensions = dimensions; this.introducedOn = introducedOn; this.createdAt = createdAt;
        }
        Long id() { return id; }
        String sku() { return sku; }
        Category category() { return category; }
        Money unitPrice() { return unitPrice; }
        BigDecimal weightKg() { return weightKg; }
        Dimensions dimensions() { return dimensions; }
        LocalDate introducedOn() { return introducedOn; }
        Instant createdAt() { return createdAt; }
        String label() {
            if (label == null) label = sku + " / " + name;      // computed on demand, cached in the object only
            return label;
        }
    }

    @Entity(name = "Warehouse")
    @Table(name = "warehouse")
    public static class Warehouse {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)   // the database assigns the id at insert
        private Long id;
        @Column(nullable = false, length = 40)
        private String name;
        protected Warehouse() {}
        Warehouse(String name) { this.name = name; }
        Long id() { return id; }
    }

    @Entity(name = "AuditEvent")
    @Table(name = "audit_event")
    public static class AuditEvent {
        @Id @GeneratedValue(strategy = GenerationType.UUID)       // generated in the JVM, random
        private UUID id;
        @Column(nullable = false, length = 120)
        private String message;
        protected AuditEvent() {}
        AuditEvent(String message) { this.message = message; }
        UUID id() { return id; }
    }

    static EntityManagerFactory emf(Class<?>... managed) {
        var cfg = new PersistenceConfiguration("warehouse")
            .property(PersistenceConfiguration.JDBC_URL, "jdbc:h2:mem:Mapping;DB_CLOSE_DELAY=-1")
            .property(PersistenceConfiguration.JDBC_USER, "sa")
            .property(PersistenceConfiguration.JDBC_PASSWORD, "")
            .property(PersistenceConfiguration.SCHEMAGEN_DATABASE_ACTION, "drop-and-create")
            .property("hibernate.show_sql", "true");
        for (Class<?> c : managed) cfg.managedClass(c);
        return cfg.createEntityManagerFactory();
    }

    public static void main(String[] args) {
        try (EntityManagerFactory emf = emf(Product.class, Warehouse.class, AuditEvent.class, MoneyConverter.class)) {

            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                Product bolt = new Product("BOLT-7", "M8 hex bolt", Category.FASTENER, new Money(80),
                        new BigDecimal("0.012"), new Dimensions(8, 8, 40),
                        LocalDate.of(2024, 3, 1), Instant.parse("2026-10-15T08:30:00Z"));
                Warehouse main = new Warehouse("Main");
                AuditEvent event = new AuditEvent("catalogue seeded");
                em.persist(bolt);
                em.persist(main);
                em.persist(event);
                System.out.println("ids before commit: product=" + bolt.id() + " warehouse=" + main.id()
                        + " audit assigned=" + (event.id() != null));
                em.getTransaction().commit();
            }

            try (EntityManager em = emf.createEntityManager()) {
                Product p = em.find(Product.class, 1L);
                System.out.println("category   = " + p.category());
                System.out.println("unitPrice  = " + p.unitPrice());
                System.out.println("weightKg   = " + p.weightKg());
                System.out.println("dimensions = " + p.dimensions() + " -> " + p.dimensions().volumeMm3() + " mm3");
                System.out.println("introduced = " + p.introducedOn() + ", createdAt kept = "
                        + p.createdAt().equals(Instant.parse("2026-10-15T08:30:00Z")));
                System.out.println("label      = " + p.label());

                Object[] raw = (Object[]) em.createNativeQuery(
                        "select category, unit_price, dim_w_mm from product where id = 1").getSingleResult();
                System.out.println("raw row    = " + raw[0] + " | " + raw[1] + " | " + raw[2]);

                Product bySku = em.createQuery("select p from Product p where p.sku = :sku", Product.class)
                        .setParameter("sku", "BOLT-7").getSingleResult();
                System.out.println("by sku     = same instance: " + (bySku == p));
            }
        }
    }
}