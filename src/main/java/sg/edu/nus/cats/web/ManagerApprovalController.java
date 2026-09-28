package sg.edu.nus.cats.web;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import sg.edu.nus.cats.domain.CourseApplication;
import sg.edu.nus.cats.domain.Employee;
import sg.edu.nus.cats.domain.enums.AggregateType;
import sg.edu.nus.cats.domain.enums.ApplicationStatus;
import sg.edu.nus.cats.domain.enums.CategoryCode;
import sg.edu.nus.cats.dto.DecisionForm;
import sg.edu.nus.cats.dto.PendingGroup;
import sg.edu.nus.cats.dto.ViewMapper;
import sg.edu.nus.cats.repository.CourseApplicationRepository;
import sg.edu.nus.cats.security.CurrentUser;
import sg.edu.nus.cats.service.ApplicationService;
import sg.edu.nus.cats.service.ApprovalService;
import sg.edu.nus.cats.service.AuditService;
import sg.edu.nus.cats.service.ClaimService;
import sg.edu.nus.cats.service.EntitlementService;
import sg.edu.nus.cats.support.AccountBalance;

/** P07 approval queue, P08 review page and P09 subordinate history. */
@Controller
@RequestMapping("/manager")
public class ManagerApprovalController {

    private final CurrentUser currentUser;
    private final ApprovalService approvals;
    private final ApplicationService applications;
    private final ClaimService claims;
    private final EntitlementService entitlements;
    private final CourseApplicationRepository repository;
    private final sg.edu.nus.cats.repository.EmployeeRepository employees;
    private final AuditService audit;
    private final Clock clock;

    public ManagerApprovalController(CurrentUser currentUser, ApprovalService approvals, ApplicationService applications,
            ClaimService claims, EntitlementService entitlements, CourseApplicationRepository repository,
            sg.edu.nus.cats.repository.EmployeeRepository employees, AuditService audit, Clock clock) {
        this.currentUser = currentUser;
        this.approvals = approvals;
        this.applications = applications;
        this.claims = claims;
        this.entitlements = entitlements;
        this.repository = repository;
        this.employees = employees;
        this.audit = audit;
        this.clock = clock;
    }

    /* ------------------------------------------------------------- P07 queue */

    @GetMapping("/approvals")
    @Transactional(readOnly = true)
    public String approvals(@RequestParam(defaultValue = "applications") String tab,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size, Model model) {
        Employee manager = currentUser.require();
        int pageSize = List.of(10, 20, 25).contains(size) ? size : 10;
        Page<PendingGroup> groups = approvals.pendingGroups(manager.getId(), PageRequest.of(Math.max(0, page), pageSize));
        model.addAttribute("tab", tab);
        model.addAttribute("groups", groups);
        model.addAttribute("pendingClaimCount", claims.pendingCount(manager.getId()));
        model.addAttribute("pendingClaims", claims.pendingForManager(manager.getId()));
        model.addAttribute("teamSummary", approvals.teamSummary(manager.getId(), LocalDate.now(clock).getYear()));
        return "manager/approvals";
    }

    /* ------------------------------------------------------------ P08 review */

    @GetMapping("/applications/{id}")
    @Transactional(readOnly = true)
    public String review(@PathVariable Long id, Model model) {
        Employee manager = currentUser.require();
        CourseApplication application = applications.require(id);
        if (application.getApprover() == null || !application.getApprover().getId().equals(manager.getId())) {
            throw sg.edu.nus.cats.support.BusinessException
                    .accessDenied("You are not the approving manager for this application.");
        }
        int year = application.getStartDate().getYear();
        AccountBalance balance = entitlements.find(application.getEmployee().getId(), year).orElse(null);
        model.addAttribute("app", application);
        model.addAttribute("view", ViewMapper.applicationView(application,
                application.getStatus().isPendingReview() && !application.getEmployee().getId().equals(manager.getId())));
        model.addAttribute("balance", balance);
        model.addAttribute("balanceYear", year);
        model.addAttribute("balances", applications.balances(application.getEmployee().getId()));
        model.addAttribute("concurrent", repository.findTeamConcurrent(ApplicationStatus.budgetHoldingStatuses(),
                application.getStartDate(), application.getEndDate(), application.getEmployee().getId()));
        model.addAttribute("timeline", audit.timeline(AggregateType.APPLICATION, application.getReferenceNo()));
        model.addAttribute("decisionForm", new DecisionForm());
        model.addAttribute("isOwnApplication", application.getEmployee().getId().equals(manager.getId()));
        return "manager/application-review";
    }

    @PostMapping("/applications/{id}/decision")
    public String decide(@PathVariable Long id, @ModelAttribute("decisionForm") DecisionForm form,
            RedirectAttributes flash) {
        Employee manager = currentUser.require();
        boolean approved = Boolean.TRUE.equals(form.getApproved());
        CourseApplication decided = approvals.decide(manager.getId(), id, approved, form.getReason(), form.getVersion());
        flash.addFlashAttribute("message", decided.getReferenceNo() + " was "
                + (approved ? "approved" : "rejected") + ". The decision is final and was recorded in the audit trail.");
        return "redirect:/manager/applications/" + id;
    }

    /* ------------------------------------------------------ P09 team history */

    @GetMapping("/team/history")
    @Transactional(readOnly = true)
    public String teamHistory(@RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) CategoryCode category,
            @RequestParam(required = false) ApplicationStatus status, Model model) {
        Employee manager = currentUser.require();
        int selectedYear = year == null ? LocalDate.now(clock).getYear() : year;
        List<Employee> team = employees.findTeamMembers(manager.getId());
        model.addAttribute("team", team);
        model.addAttribute("year", selectedYear);
        model.addAttribute("category", category);
        model.addAttribute("status", status);
        model.addAttribute("statuses", ApplicationStatus.values());
        model.addAttribute("categories", CategoryCode.values());
        model.addAttribute("selectedEmployeeId", employeeId);
        if (employeeId != null) {
            boolean allowed = team.stream().anyMatch(member -> member.getId().equals(employeeId));
            if (!allowed) {
                throw sg.edu.nus.cats.support.BusinessException
                        .accessDenied("You can only view the training history of your own team.");
            }
            model.addAttribute("rows", applications.history(employeeId, LocalDate.of(selectedYear, 1, 1),
                    LocalDate.of(selectedYear, 12, 31), category, status == null ? null : List.of(status)));
            model.addAttribute("selectedEmployee", team.stream()
                    .filter(member -> member.getId().equals(employeeId)).findFirst().orElse(null));
            model.addAttribute("balances", applications.balances(employeeId));
        }
        return "manager/team-history";
    }
}
