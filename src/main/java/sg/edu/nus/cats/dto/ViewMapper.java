package sg.edu.nus.cats.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import sg.edu.nus.cats.domain.CourseApplication;
import sg.edu.nus.cats.domain.CourseClaim;
import sg.edu.nus.cats.domain.enums.ApplicationStatus;
import sg.edu.nus.cats.support.AccountBalance;

/** Formats domain rows for the Thymeleaf pages so templates stay free of formatting rules. */
public final class ViewMapper {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Singapore");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

    private ViewMapper() {
    }

    public static String date(LocalDate value) {
        return value == null ? "" : DATE.format(value);
    }

    public static String dateTime(Instant value) {
        return value == null ? "" : DATE_TIME.format(value.atZone(BUSINESS_ZONE));
    }

    public static String money(BigDecimal value) {
        if (value == null) {
            return "0.00";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public static String days(int units) {
        return AccountBalance.formatDays(units);
    }

    public static String period(CourseApplication application) {
        if (application == null || application.getStartDate() == null) {
            return "";
        }
        if (application.getStartDate().equals(application.getEndDate())) {
            String session = application.getStartSession() == application.getEndSession()
                    ? application.getStartSession().getLabel()
                    : application.getStartSession().getLabel() + "/" + application.getEndSession().getLabel();
            return DATE.format(application.getStartDate()) + " (" + session + ")";
        }
        return DATE.format(application.getStartDate()) + " (" + application.getStartSession().getLabel()
                + ") to " + DATE.format(application.getEndDate()) + " (" + application.getEndSession().getLabel() + ")";
    }

    public static String claimPeriod(CourseClaim claim) {
        return claim == null ? "" : period(claim.getApplication());
    }

    public static PendingGroup.CourseApplicationView applicationView(CourseApplication application) {
        return applicationView(application, true);
    }

    public static PendingGroup.CourseApplicationView applicationView(CourseApplication application,
            boolean decisionAllowed) {
        ApplicationStatus status = application.getStatus();
        return new PendingGroup.CourseApplicationView(
                application.getId(),
                application.getReferenceNo(),
                application.getCourseTitle(),
                application.getCategoryCode().getDisplayName(),
                application.getProviderName(),
                application.getStartDate(),
                application.getEndDate(),
                period(application),
                application.getReservedUnits(),
                application.getCourseFee(),
                status.getDisplayName(),
                status.getCssClass(),
                dateTime(application.getSubmittedAt()),
                application.getEmployee().getFullName(),
                application.getEmployee().getDepartment(),
                decisionAllowed && status.isPendingReview(),
                application.getVersion());
    }
}
