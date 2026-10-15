import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class JdbcBaseline {

    record Product(long id, String sku, int priceInCents) {}

    public static void main(String[] args) throws SQLException {
        String url = "jdbc:h2:mem:JdbcBaseline;DB_CLOSE_DELAY=-1";
        try (Connection c = DriverManager.getConnection(url, "sa", "")) {
            c.setAutoCommit(false);                         // a transaction starts here

            try (Statement s = c.createStatement()) {
                s.execute("create table product (" +
                          "id bigint generated always as identity primary key, " +
                          "sku varchar(16) not null unique, " +
                          "price_in_cents int not null)");
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "insert into product (sku, price_in_cents) values (?, ?)")) {
                ps.setString(1, "BOLT-7"); ps.setInt(2, 80); ps.executeUpdate();
                ps.setString(1, "NUT-3");  ps.setInt(2, 25); ps.executeUpdate();
            }
            c.commit();

            List<Product> cheap = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "select id, sku, price_in_cents from product where price_in_cents < ? order by sku")) {
                ps.setInt(1, 100);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {                      // row -> object, by hand, column by column
                        cheap.add(new Product(rs.getLong("id"), rs.getString("sku"), rs.getInt("price_in_cents")));
                    }
                }
            }
            System.out.println("mapped by hand = " + cheap);

            try (PreparedStatement ps = c.prepareStatement(
                    "update product set price_in_cents = ? where sku = ?")) {
                ps.setInt(1, 90); ps.setString(2, "BOLT-7");
                System.out.println("rows updated   = " + ps.executeUpdate());
            }
            c.commit();
        }
    }
}