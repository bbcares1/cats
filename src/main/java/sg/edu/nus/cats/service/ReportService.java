package sg.edu.nus.cats.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.edu.nus.cats.domain.enums.ApplicationStatus;
import sg.edu.nus.cats.domain.enums.CategoryCode;
import sg.edu.nus.cats.dto.BudgetReportRow;
import sg.edu.nus.cats.dto.TrainingReportRow;
import sg.edu.nus.cats.dto.ViewMapper;
import sg.edu.nus.cats.repository.ApprovalAssignmentRepository;
import sg.edu.nus.cats.repository.CourseApplicationRepository;
import sg.edu.nus.cats.support.AccountBalance;

/** Training and budget reporting, including the CSV exports used by managers. */
@Service
public class ReportService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_DATE;

    private final CourseApplicationRepository applications;
    private final ApprovalAssignmentRepository assignments;
    private final EntitlementService entitlements;
    private final sg.edu.nus.cats.repository.EmployeeRepository employees;

    public ReportService(CourseApplicationRepository applications, ApprovalAssignmentRepository assignments,
            EntitlementService entitlements, sg.edu.nus.cats.repository.EmployeeRepository employees) {
        this.applications = applications;
        this.assignments = assignments;
        this.entitlements = entitlements;
        this.employees = employees;
    }

    @Transactional(readOnly = true)
    public List<TrainingReportRow> trainingRows(Collection<Long> employeeIds, LocalDate from, LocalDate to,
            CategoryCode category) {
        if (employeeIds.isEmpty()) {
            return List.of();
        }
        return applications
                .findForTeamReport(employeeIds, from, to, category, ApplicationStatus.reportedStatuses())
                .stream()
                .map(application -> new TrainingReportRow(
                        application.getEmployee().getFullName(),
                        application.getEmployee().getDepartment(),
                        application.getReferenceNo(),
                        application.getCourseTitle(),
                        application.getCategoryCode().getDisplayName(),
                        application.getProviderName(),
                        application.getStartDate(),
                        application.getEndDate(),
                        ViewMapper.period(application),
                        application.getReservedUnits(),
                        application.getStatus().getDisplayName(),
                        application.getCourseFee()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BudgetReportRow> budgetRows(Collection<Long> employeeIds, int calendarYear) {
        return entitlements.balancesForYear(calendarYear).stream()
                .filter(balance -> employeeIds.isEmpty() || employeeIds.contains(balance.account().getEmployee().getId()))
                .map(this::toBudgetRow)
                .toList();
    }

    private BudgetReportRow toBudgetRow(AccountBalance balance) {
        BigDecimal budget = balance.budgetAmount();
        BigDecimal used = balance.reservedAmount().add(balance.committedAmount()).setScale(2, RoundingMode.HALF_UP);
        String utilisation = budget.signum() <= 0 ? "n/a"
                : used.multiply(BigDecimal.valueOf(100)).divide(budget, 1, RoundingMode.HALF_UP).toPlainString() + "%";
        return new BudgetReportRow(balance.account().getEmployee().getFullName(),
                balance.account().getEmployee().getDepartment(),
                balance.account().getCalendarYear(),
                balance.entitledUnits(), balance.reservedUnits(), balance.committedUnits(), balance.availableUnits(),
                budget, balance.reservedAmount(), balance.committedAmount(), balance.reimbursedAmount(),
                balance.availableAmount(), utilisation);
    }

    public String trainingCsv(List<TrainingReportRow> rows) {
        List<List<String>> table = new ArrayList<>();
        table.add(List.of("Employee", "Department", "Reference", "Course", "Category", "Provider", "Start", "End",
                "Period", "Days", "Status", "Fee (SGD)"));
        rows.forEach(row -> table.add(List.of(
                row.employeeName(),
                nullSafe(row.department()),
                row.referenceNo(),
                row.courseTitle(),
                row.categoryLabel(),
                row.providerName(),
                row.startDate() == null ? "" : ISO.format(row.startDate()),
                row.endDate() == null ? "" : ISO.format(row.endDate()),
                row.periodLabel(),
                AccountBalance.formatDays(row.units()),
                row.statusLabel(),
                ViewMapper.money(row.fee()))));
        return render(table);
    }

    public String budgetCsv(List<BudgetReportRow> rows) {
        List<List<String>> table = new ArrayList<>();
        table.add(List.of("Employee", "Department", "Year", "Entitled days", "Reserved days", "Committed days",
                "Available days", "Budget (SGD)", "Reserved (SGD)", "Committed (SGD)", "Reimbursed (SGD)",
                "Available (SGD)", "Utilisation"));
        rows.forEach(row -> table.add(List.of(
                row.employeeName(),
                nullSafe(row.department()),
                String.valueOf(row.calendarYear()),
                AccountBalance.formatDays(row.entitledUnits()),
                AccountBalance.formatDays(row.reservedUnits()),
                AccountBalance.formatDays(row.committedUnits()),
                AccountBalance.formatDays(row.availableUnits()),
                ViewMapper.money(row.budgetAmount()),
                ViewMapper.money(row.reservedAmount()),
                ViewMapper.money(row.committedAmount()),
                ViewMapper.money(row.reimbursedAmount()),
                ViewMapper.money(row.availableAmount()),
                row.utilisationLabel())));
        return render(table);
    }

    private String render(List<List<String>> table) {
        StringBuilder builder = new StringBuilder();
        for (List<String> row : table) {
            List<String> cells = new ArrayList<>(row.size());
            row.forEach(cell -> cells.add(escape(cell)));
            builder.append(String.join(",", cells)).append("\r\n");
        }
        return builder.toString();
    }

    /**
     * Quotes a CSV cell and neutralises spreadsheet formulas. A cell that starts
     * with = + - &#64; or a control character is prefixed with an apostrophe so
     * Excel and Sheets treat it as text instead of a formula.
     */
    static String escape(String value) {
        String text = value == null ? "" : value;
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        boolean needsQuotes = text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r");
        if (needsQuotes) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    /** Employee ids that this manager is allowed to report on. */
    @Transactional(readOnly = true)
    public Set<Long> teamMemberIds(Long managerId) {
        return assignments.findByManagerIdOrderByEmployeeIdAsc(managerId).stream()
                .map(assignment -> assignment.getEmployeeId())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    /** Every active employee id, used by the administrator reports. */
    @Transactional(readOnly = true)
    public Set<Long> allEmployeeIds() {
        return employees.findByActiveTrueOrderByFullNameAsc().stream()
                .map(employee -> employee.getId())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    public static String csvFileName(String prefix, LocalDate today) {
        return prefix + "-" + ISO.format(today) + ".csv";
    }

    public static String monthLabel(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));
    }
}
