package sg.edu.nus.cats.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.stereotype.Component;

import sg.edu.nus.cats.domain.CourseApplication;
import sg.edu.nus.cats.domain.CourseClaim;
import sg.edu.nus.cats.dto.ViewMapper;

/**
 * Formatting helper usable from Thymeleaf as {@code ${@fmt.dateTime(...)}}.
 * Keeping it here means no template has to know about time zones or number scales.
 */
@Component("fmt")
public class ViewFormats {

    public String date(LocalDate value) {
        return ViewMapper.date(value);
    }

    public String dateTime(Instant value) {
        return ViewMapper.dateTime(value);
    }

    public String money(BigDecimal value) {
        return ViewMapper.money(value);
    }

    public String days(int units) {
        return ViewMapper.days(units);
    }

    /** Instant, rendered in the business time zone, for example "4 Mar 2026, 14:05". */
    public String timestamp(Instant value) {
        return ViewMapper.dateTime(value);
    }

    /** "1 Mar 2026 (AM)" or "1 Mar 2026 (AM) to 3 Mar 2026 (PM)". */
    public String period(CourseApplication application) {
        return ViewMapper.period(application);
    }

    public String claimPeriod(CourseClaim claim) {
        return ViewMapper.claimPeriod(claim);
    }

    /** "March 2026" for a YYYY-MM value, used by the calendar month picker. */
    public String monthLabel(String isoMonth) {
        return java.time.YearMonth.parse(isoMonth)
                .atDay(1)
                .format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.ENGLISH));
    }

    /** Short label used in list pages. */
    public String shortDate(LocalDate value) {
        return value == null ? "" : ViewMapper.date(value);
    }
}
