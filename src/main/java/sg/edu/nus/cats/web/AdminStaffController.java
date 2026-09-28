package sg.edu.nus.cats.web;

import java.util.List;

import org.springframework.data.domain.Page;
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
import sg.edu.nus.cats.domain.Employee;
import sg.edu.nus.cats.domain.UserAccount;
import sg.edu.nus.cats.domain.enums.RoleCode;
import sg.edu.nus.cats.dto.RoutingForm;
import sg.edu.nus.cats.dto.StaffForm;
import sg.edu.nus.cats.repository.CourseApplicationRepository;
import sg.edu.nus.cats.repository.UserAccountRepository;
import sg.edu.nus.cats.security.CurrentUser;
import sg.edu.nus.cats.service.ApprovalService;
import sg.edu.nus.cats.service.StaffAdminService;
import sg.edu.nus.cats.support.BusinessException;

/** P12 staff and roles, P13 approval routing. */
@Controller
@RequestMapping("/admin")
public class AdminStaffController {

    private final CurrentUser currentUser;
    private final StaffAdminService staff;
    private final ApprovalService approvals;
    private final UserAccountRepository accounts;
    private final CourseApplicationRepository applications;

    public AdminStaffController(CurrentUser currentUser, StaffAdminService staff, ApprovalService approvals,
            UserAccountRepository accounts, CourseApplicationRepository applications) {
        this.currentUser = currentUser;
        this.staff = staff;
        this.approvals = approvals;
        this.accounts = accounts;
        this.applications = applications;
    }

    /* ------------------------------------------------------------- P12 staff */

    @GetMapping("/staff")
    @Transactional(readOnly = true)
    public String list(@RequestParam(required = false) String query,
            @RequestParam(defaultValue = "false") boolean activeOnly,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, Model model) {
        int pageSize = List.of(10, 20, 25).contains(size) ? size : 20;
        Page<Employee> employees = staff.search(query, activeOnly, PageRequest.of(Math.max(0, page), pageSize));
        model.addAttribute("page", employees);
        model.addAttribute("rows", staff.rows(employees));
        model.addAttribute("query", query);
        model.addAttribute("activeOnly", activeOnly);
        model.addAttribute("sizes", List.of(10, 20, 25));
        model.addAttribute("size", pageSize);
        model.addAttribute("roleCodes", RoleCode.values());
        return "admin/staff";
    }

    @GetMapping("/staff/new")
    public String newStaff(Model model) {
        StaffForm form = new StaffForm();
        form.setRoles(List.of(RoleCode.EMPLOYEE));
        model.addAttribute("form", form);
        model.addAttribute("staffId", null);
        model.addAttribute("roleCodes", RoleCode.values());
        return "admin/staff-form";
    }

    @GetMapping("/staff/{id}/edit")
    @Transactional(readOnly = true)
    public String editStaff(@PathVariable Long id, Model model) {
        model.addAttribute("form", staff.toForm(id));
        model.addAttribute("staffId", id);
        model.addAttribute("roleCodes", RoleCode.values());
        model.addAttribute("route", staff.routingRows(null).stream()
                .filter(row -> row.employeeId().equals(id)).findFirst().orElse(null));
        return "admin/staff-form";
    }

    @PostMapping("/staff")
    public String create(@Valid @ModelAttribute("form") StaffForm form, BindingResult binding, Model model,
            RedirectAttributes flash) {
        if (binding.hasErrors()) {
            model.addAttribute("staffId", null);
            model.addAttribute("roleCodes", RoleCode.values());
            return "admin/staff-form";
        }
        staff.create(form, currentUser.require());
        flash.addFlashAttribute("message", "Staff record created. Share the initial password securely.");
        return "redirect:/admin/staff";
    }

    @PostMapping("/staff/{id}/update")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") StaffForm form, BindingResult binding,
            Model model, RedirectAttributes flash) {
        if (binding.hasErrors()) {
            model.addAttribute("staffId", id);
            model.addAttribute("roleCodes", RoleCode.values());
            return "admin/staff-form";
        }
        if (id.equals(currentUser.employeeId()) && !form.getRoles().contains(RoleCode.ADMIN)) {
            throw new BusinessException(sg.edu.nus.cats.support.ErrorCode.INVALID_REQUEST,
                    "You cannot remove your own administrator role. Ask another administrator to do it.");
        }
        staff.update(id, form, currentUser.require());
        flash.addFlashAttribute("message", "Staff record updated.");
        return "redirect:/admin/staff";
    }

    @PostMapping("/staff/{id}/archive")
    public String archive(@PathVariable Long id, RedirectAttributes flash) {
        Employee actor = currentUser.require();
        if (id.equals(actor.getId())) {
            throw new BusinessException(sg.edu.nus.cats.support.ErrorCode.INVALID_REQUEST,
                    "You cannot disable your own login account.");
        }
        long openApplications = applications.countOpen(id,
                sg.edu.nus.cats.domain.enums.ApplicationStatus.pendingStatuses());
        UserAccount account = accounts.findByEmployeeId(id)
                .orElseThrow(() -> BusinessException.notFound("Login account"));
        if (account.hasRole(RoleCode.ADMIN) && countOtherActiveAdmins(account) == 0) {
            throw new BusinessException(sg.edu.nus.cats.support.ErrorCode.INVALID_REQUEST,
                    "This is the last active administrator account and cannot be disabled.");
        }
        if (openApplications > 0) {
            flash.addFlashAttribute("warning", openApplications + " application(s) from this staff member are still "
                    + "waiting for a decision. Reassign or decide them first.");
        }
        long pendingForManager = approvals.pendingCount(id);
        staff.setAccountEnabled(id, false, actor);
        flash.addFlashAttribute("message", "Login account disabled."
                + (pendingForManager > 0
                        ? " " + pendingForManager + " application(s) awaiting this manager must be reassigned."
                        : ""));
        return "redirect:/admin/staff";
    }

    private long countOtherActiveAdmins(UserAccount account) {
        return accounts.findAll().stream()
                .filter(UserAccount::isEnabled)
                .filter(other -> !other.getId().equals(account.getId()))
                .filter(other -> other.hasRole(RoleCode.ADMIN))
                .count();
    }

    /* ---------------------------------------------------------- P13 routing */

    @GetMapping("/approvals")
    @Transactional(readOnly = true)
    public String routing(@RequestParam(required = false) String query, Model model) {
        model.addAttribute("rows", staff.routingRows(query));
        model.addAttribute("managers", staff.assignableManagers());
        model.addAttribute("query", query);
        model.addAttribute("routingForm", new RoutingForm());
        return "admin/routing";
    }

    @PostMapping("/approvals/{employeeId}")
    public String assign(@PathVariable Long employeeId, @RequestParam Long managerId,
            RedirectAttributes flash) {
        Employee actor = currentUser.require();
        RoutingForm form = new RoutingForm();
        form.setEmployeeId(employeeId);
        form.setManagerId(managerId);
        long pending = applications.countByEmployeeIdAndStatusIn(employeeId,
                sg.edu.nus.cats.domain.enums.ApplicationStatus.pendingStatuses());
        staff.assignManager(form, actor);
        flash.addFlashAttribute("message", "Routing saved." + (pending > 0
                ? " " + pending + " pending application(s) were explicitly reassigned to the new manager."
                : ""));
        return "redirect:/admin/approvals";
    }

    @PostMapping("/approvals/{employeeId}/remove")
    public String remove(@PathVariable Long employeeId, RedirectAttributes flash) {
        long pending = applications.countByEmployeeIdAndStatusIn(employeeId,
                sg.edu.nus.cats.domain.enums.ApplicationStatus.pendingStatuses());
        if (pending > 0) {
            throw new BusinessException(sg.edu.nus.cats.support.ErrorCode.ROUTING_INVALID,
                    pending + " application(s) are still waiting for a decision. Assign another manager first, "
                            + "otherwise these requests would be stranded without an approver.");
        }
        staff.removeRouting(employeeId, currentUser.require());
        flash.addFlashAttribute("message", "Approving manager removed.");
        return "redirect:/admin/approvals";
    }
}
