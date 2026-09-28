package sg.edu.nus.cats.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.edu.nus.cats.domain.CourseApplication;
import sg.edu.nus.cats.domain.enums.ApplicationStatus;
import sg.edu.nus.cats.domain.enums.CategoryCode;
import sg.edu.nus.cats.dto.ViewMapper;
import sg.edu.nus.cats.repository.ApprovalAssignmentRepository;
import sg.edu.nus.cats.repository.CourseApplicationRepository;
import sg.edu.nus.cats.repository.PublicHolidayRepository;

/** Month calendar shared by the employee page (P05) and the manager page (P08). */
@Service
public class CalendarService {

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private final CourseApplicationRepository applications;
    private final ApprovalAssignmentRepository assignments;
    private final PublicHolidayRepository holidays;
    private final Clock clock;

    public CalendarService(CourseApplicationRepository applications, ApprovalAssignmentRepository assignments,
            PublicHolidayRepository holidays, Clock clock) {
        this.applications = applications;
        this.assignments = assignments;
        this.holidays = holidays;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CalendarMonth month(Long viewerId, YearMonth month, CategoryCode category, boolean teamView) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        Set<Long> teamIds = assignments.findByManagerIdOrderByEmployeeIdAsc(viewerId).stream()
                .map(assignment -> assignment.getEmployeeId())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        teamIds.add(viewerId);

        List<CourseApplication> entries = applications.findCalendarEntries(ApplicationStatus.budgetHoldingStatuses(),
                from, to, category);
        Map<LocalDate, List<CalendarItem>> byDate = new HashMap<>();
        for (CourseApplication application : entries) {
            boolean own = application.getEmployee().getId().equals(viewerId);
            if (teamView && !own && !teamIds.contains(application.getEmployee().getId())) {
                continue;
            }
            CalendarItem item = new CalendarItem(
                    application.getId(),
                    application.getReferenceNo(),
                    application.getEmployee().getFullName(),
                    application.getCourseTitle(),
                    application.getCategoryCode().getDisplayName(),
                    application.getStatus().getDisplayName(),
                    application.getStatus().getCssClass(),
                    application.getStartDate(),
                    application.getEndDate(),
                    ViewMapper.period(application),
                    own);
            LocalDate cursor = application.getStartDate();
            while (!cursor.isAfter(application.getEndDate())) {
                if (!cursor.isBefore(from) && !cursor.isAfter(to)) {
                    byDate.computeIfAbsent(cursor, key -> new ArrayList<>()).add(item);
                }
                cursor = cursor.plusDays(1);
            }
        }

        Map<LocalDate, String> holidayNames = new HashMap<>();
        holidays.findByHolidayDateBetweenOrderByHolidayDateAsc(from, to)
                .forEach(holiday -> holidayNames.put(holiday.getHolidayDate(), holiday.getName()));

        List<CalendarWeek> weeks = new ArrayList<>();
        LocalDate cursor = from.with(DayOfWeek.MONDAY);
        LocalDate last = to.with(DayOfWeek.SUNDAY);
        while (!cursor.isAfter(last)) {
            List<CalendarDay> days = new ArrayList<>(7);
            for (int i = 0; i < 7; i++) {
                LocalDate date = cursor.plusDays(i);
                days.add(new CalendarDay(date,
                        date.getDayOfMonth(),
                        date.getMonth() == month.getMonth(),
                        date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY,
                        date.equals(LocalDate.now(clock)),
                        date.equals(LocalDate.now(clock)) ? "Today" : null,
                        holidayNames.get(date),
                        byDate.getOrDefault(date, List.of())));
            }
            weeks.add(new CalendarWeek(days));
            cursor = cursor.plusWeeks(1);
        }

        String label = month.atDay(1).format(MONTH_LABEL);
        LinkedHashMap<String, String> legend = new LinkedHashMap<>();
        legend.put("own", "Your training");
        legend.put("team", "Team member training");
        legend.put("holiday", "Public holiday");
        return new CalendarMonth(label, month.toString(), label, weeks, legend);
    }

    public record CalendarMonth(String title, String isoMonth, String label, List<CalendarWeek> weeks,
            Map<String, String> legend) {
    }

    public record CalendarWeek(List<CalendarDay> days) {
    }

    public record CalendarDay(LocalDate date, int dayOfMonth, boolean inMonth, boolean weekend, boolean today,
            String todayLabel, String holidayName, List<CalendarItem> items) {

        public String getWeekdayLabel() {
            return date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        }

        public String getCssClass() {
            StringBuilder builder = new StringBuilder("cal-day");
            if (!inMonth) {
                builder.append(" out-of-month");
            }
            if (weekend) {
                builder.append(" weekend");
            }
            if (holidayName != null) {
                builder.append(" holiday");
            }
            if (today) {
                builder.append(" today");
            }
            return builder.toString();
        }
    }

    public record CalendarItem(Long applicationId, String referenceNo, String employeeName, String courseTitle,
            String categoryLabel, String statusLabel, String statusCss, LocalDate startDate, LocalDate endDate,
            String periodLabel, boolean own) {
    }
}
