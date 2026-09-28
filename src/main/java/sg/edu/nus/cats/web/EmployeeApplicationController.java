package sg.edu.nus.cats.web;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import sg.edu.nus.cats.domain.CourseApplication;
import sg.edu.nus.cats.domain.Employee;
import sg.edu.nus.cats.domain.enums.AggregateType;
import sg.edu.nus.cats.domain.enums.ApplicationStatus;
import sg.edu.nus.cats.domain.enums.CategoryCode;
import sg.edu.nus.cats.dto.ApplicationForm;
import sg.edu.nus.cats.dto.PendingGroup;
import sg.edu.nus.cats.dto.ViewMapper;
import sg.edu.nus.cats.security.CurrentUser;
import sg.edu.nus.cats.service.ApplicationService;
import sg.edu.nus.cats.service.AuditService;
import sg.edu.nus.cats.service.CatalogueAdminService;
import sg.edu.nus.cats.service.ClaimService;
import sg.edu.nus.cats.service.LedgerService;

/** P03 application form, P04 own history, P05 application detail and state changes. */
@Controller
@RequestMapping("/employee/applications")
public class EmployeeApplicationController {

    private static final List<Integer> PAGE_SIZES = List.of(10, 20, 25);

    private final CurrentUser currentUser;
    private final ApplicationService applications;
    private final CatalogueAdminService catalogue;
    private final AuditService audit;
    private final LedgerService ledger;
    private final ClaimService claims;
    private final Clock clock;

    public EmployeeApplicationController(CurrentUser currentUser, ApplicationService applications,
            CatalogueAdminService catalogue, AuditService audit, LedgerService ledger, ClaimService claims,
            Clock clock) {
        this.currentUser = currentUser;
        this.applications = applications;
        this.catalogue = catalogue;
        this.audit = audit;
        this.ledger = ledger;
        this.claims = claims;
        this.clock = clock;
    }

    /* --------------------------------------------------------------- P03 form */

    @GetMapping("/new")
    public String newForm(Model model) {
        ApplicationForm form = new ApplicationForm();
        form.setClientRequestId(java.util.UUID.randomUUID().toString());
        prepareForm(model, form, null, null);
        return "employee/application-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Employee employee = currentUser.require();
        CourseApplication application = applications.requireOwned(employee.getId(), id);
        ApplicationForm form = new ApplicationForm();
        form.setCatalogueId(application.getCatalogue() == null ? null : application.getCatalogue().getId());
        form.setCategoryCode(application.getCategoryCode());
        form.setCourseTitle(application.getCourseTitle());
        form.setProviderName(application.getProviderName());
        form.setStartDate(application.getStartDate());
        form.setEndDate(application.getEndDate());
        form.setStartSession(application.getStartSession());
        form.setEndSession(application.getEndSession());
        form.setCourseFee(application.getCourseFee());
        form.setJustification(application.getJustification());
        form.setWorkDissemination(application.getWorkDissemination());
        form.setVersion(application.getVersion());
        form.setClientRequestId(application.getClientRequestId());
        prepareForm(model, form, id, application);
        return "employee/application-form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") ApplicationForm form, BindingResult binding,
            Model model, RedirectAttributes flash) {
        Employee employee = currentUser.require();
        if (binding.hasErrors()) {
            prepareForm(model, form, null, null);
            return "employee/application-form";
        }
        CourseApplication created = applications.create(employee.getId(), form);
        flash.addFlashAttribute("message", "Application " + created.getReferenceNo() + " submitted to "
                + created.getApprover().getFullName() + ".");
        return "redirect:/employee/applications/" + created.getId();
    }

    @PostMapping("/{id}/update")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") ApplicationForm form,
            BindingResult binding, Model model, RedirectAttributes flash) {
        Employee employee = currentUser.require();
        if (binding.hasErrors()) {
            prepareForm(model, form, id, applications.requireOwned(employee.getId(), id));
            return "employee/application-form";
        }
        CourseApplication updated = applications.update(employee.getId(), id, form);
        flash.addFlashAttribute("message", "Application " + updated.getReferenceNo() + " updated and returned to "
                + updated.getApprover().getFullName() + ".");
        return "redirect:/employee/applications/" + id;
    }

    private void prepareForm(Model model, ApplicationForm form, Long applicationId, CourseApplication application) {
        model.addAttribute("form", form);
        model.addAttribute("applicationId", applicationId);
        model.addAttribute("application", application);
        model.addAttribute("categories", CategoryCode.values());
        model.addAttribute("courses", catalogue.searchCourses(null));
        model.addAttribute("sessions", sg.edu.nus.cats.domain.enums.SessionCode.values());
    }

    /* ------------------------------------------------------------ P04 history */

    @GetMapping
    @Transactional(readOnly = true)
    public String history(@RequestParam(required = false) Integer year,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) CategoryCode category,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {
        Employee employee = currentUser.require();
        int selectedYear = year == null ? LocalDate.now(clock).getYear() : year;
        int pageSize = PAGE_SIZES.contains(size) ? size : 20;
        LocalDate from = LocalDate.of(selectedYear, 1, 1);
        LocalDate to = LocalDate.of(selectedYear, 12, 31);

        List<PendingGroup.CourseApplicationView> rows = new ArrayList<>(
                applications.history(employee.getId(), from, to, category,
                        status == null ? null : List.of(status)));
        if (query != null && !query.isBlank()) {
            String needle = query.trim().toLowerCase();
            rows.removeIf(row -> !row.courseTitle().toLowerCase().contains(needle)
                    && !row.referenceNo().toLowerCase().contains(needle));
        }
        int total = rows.size();
        int pageIndex = Math.max(0, page);
        int fromIndex = Math.min(pageIndex * pageSize, total);
        int toIndex = Math.min(fromIndex + pageSize, total);
        Page<PendingGroup.CourseApplicationView> pageData = new PageImpl<>(rows.subList(fromIndex, toIndex),
                PageRequest.of(pageIndex, pageSize), total);

        model.addAttribute("page", pageData);
        model.addAttribute("year", selectedYear);
        model.addAttribute("status", status);
        model.addAttribute("category", category);
        model.addAttribute("query", query);
        model.addAttribute("sizes", PAGE_SIZES);
        model.addAttribute("size", pageSize);
        model.addAttribute("statuses", ApplicationStatus.values());
        model.addAttribute("categories", CategoryCode.values());
        model.addAttribute("historyView", true);
        model.addAttribute("listUrl", "/employee/applications");
        return "fragments/application-list";
    }

    /* ------------------------------------------------------------- P05 detail */

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public String detail(@PathVariable Long id, Model model) {
        Employee employee = currentUser.require();
        CourseApplication application = applications.requireOwned(employee.getId(), id);
        model.addAttribute("application", application);
        model.addAttribute("view", ViewMapper.applicationView(application, false));
        model.addAttribute("timeline", audit.timeline(AggregateType.APPLICATION, application.getReferenceNo()));
        model.addAttribute("ledger", ledger.forApplication(application.getId()));
        model.addAttribute("claim", claims.findByApplication(application.getId()).orElse(null));
        model.addAttribute("balances", applications.balances(employee.getId()));
        model.addAttribute("allowedActions", allowedActions(application));
        model.addAttribute("canClaim", claims.claimableApplications(employee.getId()).stream()
                .anyMatch(candidate -> candidate.getId().equals(application.getId())));
        model.addAttribute("today", LocalDate.now(clock));
        return "employee/application-detail";
    }

    private List<String> allowedActions(CourseApplication application) {
        List<String> actions = new ArrayList<>();
        if (application.getStatus().isPendingReview()) {
            actions.add("edit");
            actions.add("delete");
        }
        if (application.getStatus().isPendingReview() || application.getStatus() == ApplicationStatus.APPROVED) {
            actions.add("cancel");
        }
        if (application.getStatus() == ApplicationStatus.APPROVED
                && application.getEndDate() != null && application.getEndDate().isBefore(LocalDate.now(clock))) {
            actions.add("complete");
        }
        return actions;
    }

    /* ------------------------------------------------------------ P05 actions */

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, @RequestParam String reason, @RequestParam Long version,
            RedirectAttributes flash) {
        Employee employee = currentUser.require();
        applications.cancel(employee.getId(), id, reason, version);
        flash.addFlashAttribute("message", "Application cancelled and the reserved training days released.");
        return "redirect:/employee/applications/" + id;
    }

    @PostMapping("/{id}/complete")
    public String complete(@PathVariable Long id, @RequestParam String experience, @RequestParam Long version,
            RedirectAttributes flash) {
        Employee employee = currentUser.require();
        applications.complete(employee.getId(), id, experience, version);
        flash.addFlashAttribute("message", "Course marked as completed. You can now submit a fee claim.");
        return "redirect:/employee/applications/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, @RequestParam Long version, RedirectAttributes flash) {
        Employee employee = currentUser.require();
        applications.delete(employee.getId(), id, version);
        flash.addFlashAttribute("message", "Application withdrawn and removed from your active list.");
        return "redirect:/employee/applications";
    }

    @GetMapping("/preview")
    public String previewRedirect() {
        return "redirect:/employee/applications/new";
    }
}
