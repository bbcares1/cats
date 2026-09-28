package sg.edu.nus.cats.web;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import sg.edu.nus.cats.domain.Employee;
import sg.edu.nus.cats.domain.enums.CategoryCode;
import sg.edu.nus.cats.dto.BudgetReportRow;
import sg.edu.nus.cats.dto.TrainingReportRow;
import sg.edu.nus.cats.repository.EmployeeRepository;
import sg.edu.nus.cats.security.CurrentUser;
import sg.edu.nus.cats.service.ReportService;

/**
 * P10 reports. Both tabs share the same query service, and the CSV export reuses
 * exactly the rows shown on screen so screen and export can never disagree.
 */
@Controller
@RequestMapping("/manager/reports")
public class ManagerReportController {

    private final CurrentUser currentUser;
    private final ReportService reports;
    private final EmployeeRepository employees;
    private final Clock clock;

    public ManagerReportController(CurrentUser currentUser, ReportService reports, EmployeeRepository employees,
            Clock clock) {
        this.currentUser = currentUser;
        this.reports = reports;
        this.employees = employees;
        this.clock = clock;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public String reports(@RequestParam(defaultValue = "training") String tab,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Integer year, @RequestParam(required = false) CategoryCode category,
            @RequestParam(required = false) Long employeeId, Model model) {
        Employee manager = currentUser.require();
        int selectedYear = year == null ? LocalDate.now(clock).getYear() : year;
        LocalDate periodFrom = from == null ? LocalDate.of(selectedYear, 1, 1) : from;
        LocalDate periodTo = to == null ? LocalDate.of(selectedYear, 12, 31) : to;
        Set<Long> scope = scope(manager.getId(), employeeId);

        List<TrainingReportRow> trainingRows = reports.trainingRows(scope, periodFrom, periodTo, category);
        List<BudgetReportRow> budgetRows = reports.budgetRows(scope, selectedYear);

        model.addAttribute("tab", tab);
        model.addAttribute("year", selectedYear);
        model.addAttribute("from", periodFrom);
        model.addAttribute("to", periodTo);
        model.addAttribute("category", category);
        model.addAttribute("employeeId", employeeId);
        model.addAttribute("categories", CategoryCode.values());
        model.addAttribute("team", employees.findTeamMembers(manager.getId()));
        model.addAttribute("trainingRows", trainingRows);
        model.addAttribute("budgetRows", budgetRows);
        model.addAttribute("trainingTotalUnits", trainingRows.stream().mapToInt(TrainingReportRow::units).sum());
        model.addAttribute("summary", summary(scope.size(), employeeId, periodFrom, periodTo, selectedYear, category));
        model.addAttribute("includedStatuses", "Approved and Completed");
        return "manager/reports";
    }

    @GetMapping("/training.csv")
    @Transactional(readOnly = true)
    public ResponseEntity<String> trainingCsv(@RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to, @RequestParam(required = false) Integer year,
            @RequestParam(required = false) CategoryCode category, @RequestParam(required = false) Long employeeId) {
        Employee manager = currentUser.require();
        int selectedYear = year == null ? LocalDate.now(clock).getYear() : year;
        List<TrainingReportRow> rows = reports.trainingRows(scope(manager.getId(), employeeId),
                from == null ? LocalDate.of(selectedYear, 1, 1) : from,
                to == null ? LocalDate.of(selectedYear, 12, 31) : to, category);
        return csv("training-report", reports.trainingCsv(rows));
    }

    @GetMapping("/budget.csv")
    @Transactional(readOnly = true)
    public ResponseEntity<String> budgetCsv(@RequestParam(required = false) Integer year,
            @RequestParam(required = false) CategoryCode category, @RequestParam(required = false) Long employeeId) {
        Employee manager = currentUser.require();
        int selectedYear = year == null ? LocalDate.now(clock).getYear() : year;
        List<BudgetReportRow> rows = reports.budgetRows(scope(manager.getId(), employeeId), selectedYear);
        return csv("budget-report", reports.budgetCsv(rows));
    }

    private ResponseEntity<String> csv(String prefix, String body) {
        String fileName = ReportService.csvFileName(prefix, LocalDate.now(clock));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body('\ufeff' + body);
    }

    /** A manager may only ever report on their own team, whatever the URL says. */
    private Set<Long> scope(Long managerId, Long employeeId) {
        Set<Long> team = reports.teamMemberIds(managerId);
        if (employeeId == null) {
            return team;
        }
        if (!team.contains(employeeId)) {
            throw sg.edu.nus.cats.support.BusinessException
                    .accessDenied("You can only report on employees who report to you.");
        }
        Set<Long> single = new LinkedHashSet<>();
        single.add(employeeId);
        return single;
    }

    private String summary(int teamSize, Long employeeId, LocalDate from, LocalDate to, int year,
            CategoryCode category) {
        String scopeLabel = employeeId == null ? "all " + teamSize + " team member(s)"
                : "one employee";
        return "Showing " + scopeLabel + " | " + sg.edu.nus.cats.dto.ViewMapper.date(from) + " to "
                + sg.edu.nus.cats.dto.ViewMapper.date(to) + " | " + year + " budget year | "
                + (category == null ? "all categories" : category.getDisplayName());
    }
}
