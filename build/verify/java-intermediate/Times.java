import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

public class Times {
    public static void main(String[] args) {
        ZoneId london = ZoneId.of("Europe/London");

        // an order placed the night before the clocks go forward (29 Mar 2026, 01:00 -> 02:00)
        ZonedDateTime placed = LocalDateTime.of(2026, 3, 28, 23, 30).atZone(london);
        System.out.println("placed            = " + placed);
        System.out.println("plusDays(1)       = " + placed.plusDays(1));     // same wall clock, 23h later
        System.out.println("plusHours(24)     = " + placed.plusHours(24));   // 24h later, different wall clock

        // a time that does not exist, and one that exists twice
        ZonedDateTime inGap = LocalDateTime.of(2026, 3, 29, 1, 30).atZone(london);
        System.out.println("01:30 in the gap  = " + inGap);
        ZonedDateTime overlap = LocalDateTime.of(2026, 10, 25, 1, 30).atZone(london);
        System.out.println("01:30 in overlap  = " + overlap + " / later: " + overlap.withLaterOffsetAtOverlap());

        // one instant, three views
        Instant at = placed.toInstant();
        System.out.println("instant           = " + at);
        System.out.println("same instant NYC  = " + at.atZone(ZoneId.of("America/New_York")));
        System.out.println("same instant Tokyo= " + at.atZone(ZoneId.of("Asia/Tokyo")));

        // Period counts calendar units; Duration counts elapsed time
        LocalDate due = placed.toLocalDate().plusDays(10);
        System.out.println("SLA period        = " + Period.between(placed.toLocalDate(), due));
        System.out.println("elapsed to +1 day = " + Duration.between(placed, placed.plusDays(1)));

        // formatting: ISO is locale-free; a pattern needs a Locale
        DateTimeFormatter uk = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.UK);
        System.out.println("ISO_LOCAL_DATE    = " + placed.format(DateTimeFormatter.ISO_LOCAL_DATE));
        System.out.println("pattern, UK       = " + placed.format(uk));

        // calendar arithmetic clamps rather than overflowing
        LocalDate jan31 = LocalDate.of(2026, 1, 31);
        System.out.println("31 Jan + 1 month  = " + jan31.plusMonths(1));
        System.out.println("truncated to hour = " + placed.truncatedTo(ChronoUnit.HOURS).toLocalDateTime());
        YearMonth ym = YearMonth.from(placed);
        System.out.println("year-month        = " + ym + " (" + ym.lengthOfMonth() + " days)");
        System.out.println("day of week       = " + placed.getDayOfWeek() + ", weekend=" + (placed.getDayOfWeek() == DayOfWeek.SATURDAY));
        System.out.println("parsed + 1 week   = " + LocalDate.parse("2026-10-15").plusWeeks(1));

        // every java.time type is immutable: this line does nothing
        placed.plusDays(30);
        System.out.println("still placed on   = " + placed.toLocalDate());
    }
}