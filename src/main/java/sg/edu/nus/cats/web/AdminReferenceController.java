package sg.edu.nus.cats.web;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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

import sg.edu.nus.cats.domain.CourseCatalogue;
import sg.edu.nus.cats.domain.Employee;
import sg.edu.nus.cats.domain.TrainingAccount;
import sg.edu.nus.cats.domain.TrainingProvider;
import sg.edu.nus.cats.domain.enums.ApplicationStatus;
import sg.edu.nus.cats.domain.enums.CategoryCode;
import sg.edu.nus.cats.domain.enums.RoleCode;
import sg.edu.nus.cats.dto.CatalogueForm;
import sg.edu.nus.cats.dto.EntitlementForm;
import sg.edu.nus.cats.dto.HolidayForm;
import sg.edu.nus.cats.dto.ProviderForm;
import sg.edu.nus.cats.support.BusinessException;
import sg.edu.nus.cats.repository.CourseApplicationRepository;
import sg.edu.nus.cats.repository.EmployeeRepository;
import sg.edu.nus.cats.repository.TrainingAccountRepository;
import sg.edu.nus.cats.security.CurrentUser;
import sg.edu.nus.cats.service.CatalogueAdminService;
import sg.edu.nus.cats.service.EntitlementService;
import sg.edu.nus.cats.service.LedgerService;
import sg.edu.nus.cats.support.AccountBalance;

/** P13 annual entitlements and P14 catalogue / holiday reference data. */
@Controller
@RequestMapping("/admin")
public class AdminReferenceController {

    private final CurrentUser currentUser;
    private final EntitlementService entitlements;
    private final TrainingAccountRepository accounts;
    private final EmployeeRepository employees;
    private final CatalogueAdminService catalogue;
    private final CourseApplicationRepository applications;
    private final LedgerService ledger;
    private final Clock clock;

    public AdminReferenceController(CurrentUser currentUser, EntitlementService entitlements,
            TrainingAccountRepository accounts, EmployeeRepository employees, CatalogueAdminService catalogue,
            CourseApplicationRepository applications, LedgerService ledger, Clock clock) {
        this.currentUser = currentUser;
        this.entitlements = entitlements;
        this.accounts = accounts;
        this.employees = employees;
        this.catalogue = catalogue;
        this.applications = applications;
        this.ledger = ledger;
        this.clock = clock;
    }

    /* ------------------------------------------------------ P13 entitlements */

    @GetMapping("/entitlements")
    @Transactional(readOnly = true)
    public String entitlements(@RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long employeeId, Model model) {
        int selectedYear = year == null ? LocalDate.now(clock).getYear() : year;
        List<AnnualRow> rows = new ArrayList<>();
        for (TrainingAccount account : accounts.findByCalendarYearOrderByEmployeeFullNameAsc(selectedYear)) {
            rows.add(new AnnualRow(account, ledger.balance(account)));
        }
        model.addAttribute("year", selectedYear);
        model.addAttribute("rows", rows);
        model.addAttribute("employees", employees.findByActiveTrueOrderByFullNameAsc());
        model.addAttribute("employeeId", employeeId);
        model.addAttribute("configuredCount", rows.size());
        model.addAttribute("missingCount", employees.countByActiveTrue() - rows.size());
        model.addAttribute("years", List.of(selectedYear - 1, selectedYear, selectedYear + 1, selectedYear + 2));
        EntitlementForm form = new EntitlementForm();
        form.setCalendarYear(selectedYear);
        form.setEmployeeId(employeeId);
        model.addAttribute("form", form);
        return "admin/entitlements";
    }

    /** The form picks the employee in a dropdown, so the same POST works for every employee. */
    @PostMapping("/entitlements")
    public String saveEntitlementForEmployee(@ModelAttribute("form") EntitlementForm form,
            RedirectAttributes flash) {
        if (form.getEmployeeId() == null) {
            throw new BusinessException(sg.edu.nus.cats.support.ErrorCode.INVALID_REQUEST,
                    "Choose the employee whose entitlement you want to set.");
        }
        return saveEntitlement(form.getEmployeeId(), form, flash);
    }

    @PostMapping("/entitlements/{employeeId}")
    public String saveEntitlement(@PathVariable Long employeeId, @ModelAttribute("form") EntitlementForm form,
            RedirectAttributes flash) {
        form.setEmployeeId(employeeId);
        if (form.getCalendarYear() == null) {
            form.setCalendarYear(LocalDate.now(clock).getYear());
        }
        TrainingAccount saved = entitlements.saveEntitlement(form, currentUser.require());
        flash.addFlashAttribute("message", "Entitlement saved for " + saved.getEmployee().getFullName()
                + " (" + saved.getCalendarYear() + ").");
        return "redirect:/admin/entitlements?year=" + saved.getCalendarYear();
    }

    /** One row of the entitlement table with its explainable balance. */
    public record AnnualRow(TrainingAccount account, AccountBalance balance) {
    }

    /* -------------------------------------------------------- P14 catalogue */

    @GetMapping("/catalogue")
    @Transactional(readOnly = true)
    public String cataloguePage(@RequestParam(defaultValue = "courses") String tab,
            @RequestParam(required = false) String query, Model model) {
        model.addAttribute("tab", tab);
        model.addAttribute("query", query);
        model.addAttribute("categories", catalogue.allCategories());
        model.addAttribute("providers", catalogue.allProviders());
        model.addAttribute("courses", query == null || query.isBlank() ? catalogue.allCourses()
                : catalogue.searchCourses(query));
        model.addAttribute("categoryCodes", CategoryCode.values());
        model.addAttribute("catalogueForm", new CatalogueForm());
        model.addAttribute("providerForm", new ProviderForm());
        model.addAttribute("holidayForm", new HolidayForm());
        model.addAttribute("holidays", catalogue.holidaysIn(LocalDate.now(clock).getYear()));
        model.addAttribute("holidayYear", LocalDate.now(clock).getYear());
        model.addAttribute("affected", affectedByHolidayChange());
        return "admin/catalogue";
    }

    @PostMapping("/catalogue/courses")
    public String saveCourse(@ModelAttribute("catalogueForm") CatalogueForm form, RedirectAttributes flash) {
        CourseCatalogue course = catalogue.saveCourse(form, currentUser.require());
        flash.addFlashAttribute("message", "Course \"" + course.getTitle() + "\" saved.");
        return "redirect:/admin/catalogue?tab=courses";
    }

    @PostMapping("/catalogue/courses/{id}/toggle")
    public String toggleCourse(@PathVariable Long id, @RequestParam boolean active, RedirectAttributes flash) {
        catalogue.setCourseActive(id, active, currentUser.require());
        flash.addFlashAttribute("message", "Course " + (active ? "activated" : "deactivated")
                + ". Historic applications keep their reference to it.");
        return "redirect:/admin/catalogue?tab=courses";
    }

    @PostMapping("/catalogue/providers")
    public String saveProvider(@ModelAttribute("providerForm") ProviderForm form, RedirectAttributes flash) {
        TrainingProvider provider = catalogue.saveProvider(form, currentUser.require());
        flash.addFlashAttribute("message", "Provider \"" + provider.getName() + "\" saved.");
        return "redirect:/admin/catalogue?tab=providers";
    }

    @PostMapping("/catalogue/providers/{id}/toggle")
    public String toggleProvider(@PathVariable Long id, @RequestParam boolean active, RedirectAttributes flash) {
        catalogue.setProviderActive(id, active, currentUser.require());
        flash.addFlashAttribute("message", "Provider " + (active ? "activated" : "deactivated") + ".");
        return "redirect:/admin/catalogue?tab=providers";
    }

    /* --------------------------------------------------------- P14 holidays */

    @GetMapping("/holidays")
    @Transactional(readOnly = true)
    public String holidays(@RequestParam(required = false) Integer year, Model model) {
        int selectedYear = year == null ? LocalDate.now(clock).getYear() : year;
        model.addAttribute("year", selectedYear);
        model.addAttribute("holidays", catalogue.holidaysIn(selectedYear));
        model.addAttribute("holidayForm", new HolidayForm());
        model.addAttribute("years", List.of(selectedYear - 1, selectedYear, selectedYear + 1, selectedYear + 2));
        model.addAttribute("affected", affectedByHolidayChange());
        return "admin/holidays";
    }

    @PostMapping("/holidays")
    public String saveHoliday(@ModelAttribute("holidayForm") HolidayForm form, RedirectAttributes flash) {
        catalogue.saveHoliday(form, currentUser.require());
        flash.addFlashAttribute("message", "Public holiday saved for " + form.getHolidayDate()
                + ". Training day calculations that touch this date were refreshed.");
        return "redirect:/admin/holidays?year=" + form.getHolidayDate().getYear();
    }

    @PostMapping("/holidays/{date}/delete")
    public String deleteHoliday(@PathVariable String date, RedirectAttributes flash) {
        LocalDate holidayDate = LocalDate.parse(date);
        catalogue.deleteHoliday(holidayDate, currentUser.require());
        flash.addFlashAttribute("message", "Public holiday removed for " + holidayDate + ".");
        return "redirect:/admin/holidays?year=" + holidayDate.getYear();
    }

    /** Employees with applications still holding days, shown next to a holiday change. */
    private List<String> affectedByHolidayChange() {
        List<String> affected = new ArrayList<>();
        for (Employee employee : employees.findByActiveTrueOrderByFullNameAsc()) {
            long open = applications.countByEmployeeIdAndStatusIn(employee.getId(),
                    ApplicationStatus.pendingStatuses());
            if (open > 0) {
                affected.add(employee.getFullName() + " (" + open + ")");
            }
        }
        return affected;
    }

    /** Administrators only ever see the roles they may grant. */
    @ModelAttribute("assignableRoles")
    public List<RoleCode> assignableRoles() {
        return List.of(RoleCode.values());
    }
}
