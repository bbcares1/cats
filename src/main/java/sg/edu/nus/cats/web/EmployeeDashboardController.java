package sg.edu.nus.cats.web;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import sg.edu.nus.cats.domain.CourseApplication;
import sg.edu.nus.cats.domain.Employee;
import sg.edu.nus.cats.domain.enums.ApplicationStatus;
import sg.edu.nus.cats.dto.ViewMapper;
import sg.edu.nus.cats.repository.CourseApplicationRepository;
import sg.edu.nus.cats.security.CurrentUser;
import sg.edu.nus.cats.service.ApplicationService;
import sg.edu.nus.cats.service.ApprovalService;
import sg.edu.nus.cats.service.EntitlementService;
import sg.edu.nus.cats.support.AccountBalance;

/**
 * P02 — employee home. Four cards: remaining days, remaining budget, pending
 * approvals and courses that may be completed; plus the "needs your action" list.
 */
@Controller
@RequestMapping("/employee")
public class EmployeeDashboardController {

    private final CurrentUser currentUser;
    private final ApplicationService applications;
    private final EntitlementService entitlements;
    private final ApprovalService approvals;
    private final CourseApplicationRepository repository;
    private final Clock clock;

    public EmployeeDashboardController(CurrentUser currentUser, ApplicationService applications,
            EntitlementService entitlements, ApprovalService approvals, CourseApplicationRepository repository,
            Clock clock) {
        this.currentUser = currentUser;
        this.applications = applications;
        this.entitlements = entitlements;
        this.approvals = approvals;
        this.repository = repository;
        this.clock = clock;
    }

    @GetMapping("/dashboard")
    @Transactional(readOnly = true)
    public String dashboard(Model model) {
        Employee employee = currentUser.require();
        int year = LocalDate.now(clock).getYear();
        AccountBalance balance = entitlements.find(employee.getId(), year).orElse(null);

        List<PendingGroupView> needsAction = repository
                .findByEmployeeIdAndStatusInOrderByStartDateDesc(employee.getId(), ApplicationStatus.pendingStatuses())
                .stream()
                .map(application -> new PendingGroupView(ViewMapper.applicationView(application, false),
                        application.getStatus() == ApplicationStatus.UPDATED
                                ? "Updated and waiting for a new decision"
                                : "Waiting for a decision"))
                .toList();

        List<CourseApplication> completable = repository.findCompletable(List.of(ApplicationStatus.APPROVED),
                LocalDate.now(clock));

        model.addAttribute("year", year);
        model.addAttribute("employee", employee);
        model.addAttribute("balance", balance);
        model.addAttribute("pendingCount", repository.countOpen(employee.getId(),
                ApplicationStatus.pendingStatuses()));
        model.addAttribute("needsAction", needsAction);
        model.addAttribute("completable", completable);
        model.addAttribute("recent", applications.history(employee.getId(), null, null, null, null).stream()
                .limit(5).toList());
        model.addAttribute("balances", applications.balances(employee.getId()));
        return "employee/dashboard";
    }

    /** Card row plus the reason it needs attention. */
    public record PendingGroupView(sg.edu.nus.cats.dto.PendingGroup.CourseApplicationView row, String reason) {
    }
}
