import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.List;

public class InheritanceMapping {

    /** Not an entity: its columns are copied into every entity table that extends it. */
    @MappedSuperclass
    public static abstract class Auditable {
        @Column(nullable = false)
        protected LocalDate createdOn = LocalDate.of(2026, 10, 15);
    }

    // ── SINGLE_TABLE: one table, a discriminator column, nullable subclass columns ──
    @Entity(name = "Discount")
    @Table(name = "discount")
    @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
    @DiscriminatorColumn(name = "kind", length = 8)
    public static abstract class Discount extends Auditable {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE)
        protected Long id;
        @Column(nullable = false, length = 16)
        protected String code;
        protected Discount() {}
        Discount(String code) { this.code = code; }
        abstract long apply(long cents);
        @Override public String toString() { return getClass().getSimpleName() + "(" + code + ")"; }
    }

    @Entity(name = "PercentOff") @DiscriminatorValue("PCT")
    public static class PercentOff extends Discount {
        private Integer percent;                 // Integer, not int: the column must be nullable for other kinds
        protected PercentOff() {}
        PercentOff(String code, int percent) { super(code); this.percent = percent; }
        int percent() { return percent; }
        @Override long apply(long cents) { return cents - cents * percent / 100; }
    }

    @Entity(name = "FixedOff") @DiscriminatorValue("FIX")
    public static class FixedOff extends Discount {
        private Integer offCents;
        protected FixedOff() {}
        FixedOff(String code, int offCents) { super(code); this.offCents = offCents; }
        @Override long apply(long cents) { return Math.max(0, cents - offCents); }
    }

    // ── JOINED: one table per class, subclass tables share the parent's primary key ──
    @Entity(name = "Payment")
    @Table(name = "payment")
    @Inheritance(strategy = InheritanceType.JOINED)
    public static abstract class Payment {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE)
        protected Long id;
        protected long amountCents;
        protected Payment() {}
        Payment(long amountCents) { this.amountCents = amountCents; }
        @Override public String toString() { return getClass().getSimpleName() + "(" + amountCents + "c)"; }
    }

    @Entity(name = "CardPayment") @Table(name = "card_payment")
    public static class CardPayment extends Payment {
        @Column(nullable = false, length = 4)
        private String last4;                    // NOT NULL is possible: the column lives in its own table
        protected CardPayment() {}
        CardPayment(long amountCents, String last4) { super(amountCents); this.last4 = last4; }
    }

    @Entity(name = "BankTransfer") @Table(name = "bank_transfer")
    public static class BankTransfer extends Payment {
        @Column(nullable = false, length = 34)
        private String iban;
        protected BankTransfer() {}
        BankTransfer(long amountCents, String iban) { super(amountCents); this.iban = iban; }
    }

    // ── TABLE_PER_CLASS: one complete table per concrete class, polymorphic reads are a UNION ──
    @Entity(name = "Shipment")
    @Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
    public static abstract class Shipment {
        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE)   // IDENTITY is impossible here: ids must be unique ACROSS tables
        protected Long id;
        protected int weightKg;
        protected Shipment() {}
        Shipment(int weightKg) { this.weightKg = weightKg; }
        @Override public String toString() { return getClass().getSimpleName() + "(" + weightKg + "kg)"; }
    }

    @Entity(name = "ParcelShipment") @Table(name = "parcel_shipment")
    public static class ParcelShipment extends Shipment {
        private String carrier;
        protected ParcelShipment() {}
        ParcelShipment(int weightKg, String carrier) { super(weightKg); this.carrier = carrier; }
    }

    @Entity(name = "FreightShipment") @Table(name = "freight_shipment")
    public static class FreightShipment extends Shipment {
        private int pallets;
        protected FreightShipment() {}
        FreightShipment(int weightKg, int pallets) { super(weightKg); this.pallets = pallets; }
    }

    static EntityManagerFactory emf() {
        return new PersistenceConfiguration("warehouse")
            .managedClass(Discount.class).managedClass(PercentOff.class).managedClass(FixedOff.class)
            .managedClass(Payment.class).managedClass(CardPayment.class).managedClass(BankTransfer.class)
            .managedClass(Shipment.class).managedClass(ParcelShipment.class).managedClass(FreightShipment.class)
            .property(PersistenceConfiguration.JDBC_URL, "jdbc:h2:mem:InheritanceMapping;DB_CLOSE_DELAY=-1")
            .property(PersistenceConfiguration.JDBC_USER, "sa")
            .property(PersistenceConfiguration.JDBC_PASSWORD, "")
            .property(PersistenceConfiguration.SCHEMAGEN_DATABASE_ACTION, "drop-and-create")
            .property("hibernate.show_sql", "true")
            .createEntityManagerFactory();
    }

    static void section(String title) { System.out.println("-- " + title + " --"); }

    public static void main(String[] args) {
        try (EntityManagerFactory emf = emf()) {
            section("insert one of each");
            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                em.persist(new PercentOff("SPRING10", 10));
                em.persist(new FixedOff("WELCOME5", 500));
                em.persist(new CardPayment(12_000, "4242"));
                em.persist(new BankTransfer(250_000, "GB33BUKB20201555555555"));
                em.persist(new ParcelShipment(2, "DHL"));
                em.persist(new FreightShipment(900, 3));
                em.getTransaction().commit();
            }

            try (EntityManager em = emf.createEntityManager()) {
                section("SINGLE_TABLE polymorphic query: one select, filtered by the discriminator");
                List<Discount> discounts = em.createQuery("select d from Discount d order by d.id", Discount.class).getResultList();
                for (Discount d : discounts) System.out.println(d + " applied to 10000c = " + d.apply(10_000));

                section("type() and treat(): asking for one subtype");
                System.out.println(em.createQuery("select d from Discount d where type(d) = PercentOff", Discount.class).getResultList());
                System.out.println(em.createQuery("select treat(d as PercentOff).percent from Discount d where type(d) = PercentOff", Integer.class).getResultList());

                section("JOINED polymorphic query: the parent table left-joins every subclass table");
                System.out.println(em.createQuery("select p from Payment p order by p.id", Payment.class).getResultList());

                section("JOINED subtype query: an inner join to the parent");
                System.out.println(em.createQuery("select c from CardPayment c", CardPayment.class).getResultList());

                section("TABLE_PER_CLASS polymorphic query: a UNION of every concrete table");
                System.out.println(em.createQuery("select s from Shipment s order by s.id", Shipment.class).getResultList());

                section("find() by id works for every strategy");
                System.out.println(em.find(Payment.class, 1L) + ", " + em.find(Shipment.class, 2L));
            }
        }
    }
}