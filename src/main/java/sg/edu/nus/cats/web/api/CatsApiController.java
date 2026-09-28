package sg.edu.nus.cats.web.api;

import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import sg.edu.nus.cats.domain.CourseCatalogue;
import sg.edu.nus.cats.domain.Employee;
import sg.edu.nus.cats.domain.enums.CategoryCode;
import sg.edu.nus.cats.dto.ApplicationForm;
import sg.edu.nus.cats.dto.PreviewResult;
import sg.edu.nus.cats.repository.CourseCatalogueRepository;
import sg.edu.nus.cats.security.CurrentUser;
import sg.edu.nus.cats.service.ApplicationService;
import sg.edu.nus.cats.service.CalendarService;
import sg.edu.nus.cats.support.BusinessException;
import sg.edu.nus.cats.support.ErrorCode;

/** JSON endpoints used by the preview panel, the calendar and the course picker. */
@RestController
@RequestMapping("/api/v1")
public class CatsApiController {

    private final ApplicationService applications;
    private final CalendarService calendar;
    private final CourseCatalogueRepository catalogues;
    private final CurrentUser currentUser;
    private final Clock clock;

    public CatsApiController(ApplicationService applications, CalendarService calendar,
            CourseCatalogueRepository catalogues, CurrentUser currentUser, Clock clock) {
        this.applications = applications;
        this.calendar = calendar;
        this.catalogues = catalogues;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /** Dry run of the submission rules. Nothing is written, so it is safe to call on every keystroke. */
    @PostMapping("/applications/preview")
    @Transactional(readOnly = true)
    public ResponseEntity<PreviewResult> preview(@RequestBody ApplicationForm form) {
        Employee employee = currentUser.require();
        return ResponseEntity.ok(applications.preview(employee.getId(), form));
    }

    /** Month grid. scope=team switches to the manager calendar (own team only). */
    @GetMapping("/calendar")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','ADMIN')")
    @Transactional(readOnly = true)
    public Map<String, Object> calendar(@RequestParam(required = false) String month,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "mine") String scope) {
        Employee viewer = currentUser.require();
        YearMonth requested = parseMonth(month);
        CategoryCode categoryCode = parseCategory(category);
        boolean teamView = "team".equalsIgnoreCase(scope)
                && currentUser.principal().getAuthorities().stream()
                        .anyMatch(authority -> "ROLE_MANAGER".equals(authority.getAuthority()));
        CalendarService.CalendarMonth view = calendar.month(viewer.getId(), requested, categoryCode, teamView);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("month", view.isoMonth());
        body.put("label", view.label());
        body.put("scope", teamView ? "team" : "mine");
        body.put("legend", view.legend());
        body.put("days", view.weeks().stream().flatMap(week -> week.days().stream()).map(day -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("date", day.date().toString());
            entry.put("dayOfMonth", day.dayOfMonth());
            entry.put("inMonth", day.inMonth());
            entry.put("holiday", day.holidayName());
            entry.put("isToday", day.today());
            entry.put("items", day.items().stream().map(item -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("applicationId", item.applicationId());
                row.put("referenceNo", item.referenceNo());
                row.put("employeeName", item.employeeName());
                row.put("courseTitle", item.courseTitle());
                row.put("category", item.categoryLabel());
                row.put("status", item.statusLabel());
                row.put("period", item.periodLabel());
                row.put("own", item.own());
                return row;
            }).toList());
            return entry;
        }).toList());
        return body;
    }

    /** Course picker used by the application form. Only active catalogue entries are returned. */
    @GetMapping("/catalogue")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> catalogue(@RequestParam(required = false) String query) {
        currentUser.require();
        List<CourseCatalogue> courses = catalogues.searchActive(query == null || query.isBlank() ? null : query);
        return courses.stream().map(course -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", course.getId());
            row.put("title", course.getTitle());
            row.put("category", course.getCategory().getCode().name());
            row.put("categoryLabel", course.getCategory().getCode().getDisplayName());
            row.put("provider", course.getProvider() == null ? null : course.getProvider().getName());
            row.put("defaultFee", course.getDefaultFee());
            row.put("halfDayAllowed", course.getCategory().getCode().allowsHalfDay());
            row.put("description", course.getDescription());
            row.put("label", course.getTitle() + " (" + course.getCategory().getCode().getDisplayName()
                    + (course.getProvider() == null ? "" : " - " + course.getProvider().getName()) + ")");
            return row;
        }).toList();
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.from(java.time.LocalDate.now(clock));
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "The month must use the YYYY-MM format, for example 2026-03.");
        }
    }

    private CategoryCode parseCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        try {
            return CategoryCode.valueOf(category.toUpperCase(java.util.Locale.ENGLISH));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Unknown course category \"" + category + "\".");
        }
    }

    /** Kept so the JSON error shape stays consistent for 404s from this controller. */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.status(HttpStatus.OK).body(Map.of("status", "UP"));
    }
}
