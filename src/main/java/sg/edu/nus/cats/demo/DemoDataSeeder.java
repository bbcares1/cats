package sg.edu.nus.cats.demo;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import sg.edu.nus.cats.domain.CourseApplication;
import sg.edu.nus.cats.domain.CourseCatalogue;
import sg.edu.nus.cats.domain.CourseClaim;
import sg.edu.nus.cats.domain.Employee;
import sg.edu.nus.cats.domain.TrainingProvider;
import sg.edu.nus.cats.domain.enums.CategoryCode;
import sg.edu.nus.cats.domain.enums.RoleCode;
import sg.edu.nus.cats.domain.enums.SessionCode;
import sg.edu.nus.cats.dto.ApplicationForm;
import sg.edu.nus.cats.dto.CatalogueForm;
import sg.edu.nus.cats.dto.ClaimForm;
import sg.edu.nus.cats.dto.EntitlementForm;
import sg.edu.nus.cats.dto.HolidayForm;
import sg.edu.nus.cats.dto.ProviderForm;
import sg.edu.nus.cats.dto.RoutingForm;
import sg.edu.nus.cats.dto.StaffForm;
import sg.edu.nus.cats.repository.EmployeeRepository;
import sg.edu.nus.cats.service.ApplicationService;
import sg.edu.nus.cats.service.ApprovalService;
import sg.edu.nus.cats.service.CatalogueAdminService;
import sg.edu.nus.cats.service.ClaimService;
import sg.edu.nus.cats.service.EntitlementService;
import sg.edu.nus.cats.service.StaffAdminService;

/**
 * Loads a small but complete demonstration data set when the {@code demo} profile is
 * active. Everything is created through the real services, so entitlements, ledger
 * entries, audit events and outbox rows are produced exactly as they would be through
 * the user interface. The seeder is idempotent: it does nothing once staff exist.
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final String DEMO_PASSWORD = "Password123!";

    private final EmployeeRepository employees;
    private final StaffAdminService staff;
    private final EntitlementService entitlements;
    private final CatalogueAdminService catalogue;
    private final ApplicationService applicationService;
    private final ApprovalService approvals;
    private final ClaimService claims;

    public DemoDataSeeder(EmployeeRepository employees, StaffAdminService staff, EntitlementService entitlements,
            CatalogueAdminService catalogue, ApplicationService applicationService, ApprovalService approvals,
            ClaimService claims) {
        this.employees = employees;
        this.staff = staff;
        this.entitlements = entitlements;
        this.catalogue = catalogue;
        this.applicationService = applicationService;
        this.approvals = approvals;
        this.claims = claims;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (employees.count() > 0) {
            log.info("Demo data already present ({} staff records); skipping the demo seed.", employees.count());
            return;
        }
        log.info("Seeding CATS demonstration data...");

        Employee admin = createStaff("ADM001", "Adeline Ng", "adeline.ng@nus.edu.sg", "Human Resources",
                "HR Administrator", "admin", RoleCode.ADMIN);
        Employee marcus = createStaff("MGR001", "Marcus Tan", "marcus.tan@nus.edu.sg", "Engineering",
                "Engineering Manager", "marcus.tan", RoleCode.EMPLOYEE, RoleCode.MANAGER);
        Employee priya = createStaff("MGR002", "Priya Raman", "priya.raman@nus.edu.sg", "Finance",
                "Finance Manager", "priya.raman", RoleCode.EMPLOYEE, RoleCode.MANAGER);
        Employee alice = createStaff("EMP001", "Alice Tan", "alice.tan@nus.edu.sg", "Engineering",
                "Software Engineer", "alice.tan", RoleCode.EMPLOYEE);
        Employee bob = createStaff("EMP002", "Bob Lee", "bob.lee@nus.edu.sg", "Engineering",
                "Software Engineer", "bob.lee", RoleCode.EMPLOYEE);
        Employee cheng = createStaff("EMP003", "Cheng Wei", "cheng.wei@nus.edu.sg", "Finance",
                "Finance Analyst", "cheng.wei", RoleCode.EMPLOYEE);
        Employee divya = createStaff("EMP004", "Divya Suresh", "divya.suresh@nus.edu.sg", "Engineering",
                "Senior Software Engineer", "divya.suresh", RoleCode.EMPLOYEE);

        assign(alice, marcus);
        assign(bob, marcus);
        assign(divya, marcus);
        assign(cheng, priya);
        assign(marcus, admin);
        assign(priya, admin);

        int thisYear = Year.now().getValue();
        for (Employee employee : List.of(admin, marcus, priya, alice, bob, cheng, divya)) {
            grant(employee, thisYear - 1, 20, new BigDecimal("3000.00"));
            grant(employee, thisYear, 20, new BigDecimal("3000.00"));
            grant(employee, thisYear + 1, 20, new BigDecimal("3000.00"));
        }
        grant(marcus, thisYear, 30, new BigDecimal("6000.00"));
        grant(priya, thisYear, 30, new BigDecimal("6000.00"));

        TrainingProvider nus = provider("NUS Institute for Application Studies");
        TrainingProvider online = provider("NUS Online Learning Consortium");
        TrainingProvider certs = provider("Singapore Certification Board");

        CourseCatalogue cloud = course(CategoryCode.EXTERNAL, online, "Cloud Architecture on AWS",
                new BigDecimal("1800.00"));
        CourseCatalogue agile = course(CategoryCode.EXTERNAL, nus, "Agile Practices for Delivery Teams",
                new BigDecimal("1200.00"));
        CourseCatalogue writing = course(CategoryCode.INTERNAL, nus, "Technical Writing at NUS", BigDecimal.ZERO);
        CourseCatalogue certification = course(CategoryCode.CERTIFICATION, certs,
                "AWS Certified Solutions Architect", new BigDecimal("650.00"));

        seedHolidays();

        LocalDate firstMonday = nextWorkingDay(LocalDate.now().plusDays(7).with(DayOfWeek.MONDAY));

        CourseApplication twoDay = submit(alice, cloud, firstMonday, firstMonday.plusDays(1), SessionCode.AM,
                SessionCode.PM, "Rolling out our multi-account platform needs hands-on cloud architecture skills.");
        approvals.decide(marcus.getId(), twoDay.getId(), true, "Directly relevant to the platform roadmap.", null);

        CourseApplication completed = submit(bob, agile, firstMonday, firstMonday, SessionCode.AM, SessionCode.PM,
                "Our delivery team is adopting Scrum next quarter.");
        approvals.decide(marcus.getId(), completed.getId(), true, "Supports the upcoming team transition.", null);
        applicationService.complete(bob.getId(), completed.getId(),
                "Ran the sprint planning workshop for the team on my return.", null);

        CourseApplication pending = submit(cheng, agile, firstMonday.plusDays(14), firstMonday.plusDays(16),
                SessionCode.AM, SessionCode.PM,
                "Finance is moving to quarterly forecasting and needs agile estimating practice.");
        log.debug("Left {} in {}", pending.getReferenceNo(), pending.getStatus());

        CourseApplication awaitingCert = submit(alice, certification, firstMonday.plusDays(28),
                firstMonday.plusDays(28), SessionCode.AM, SessionCode.PM,
                "The platform team needs an in-house certified architect.");
        log.debug("Left {} in {}", awaitingCert.getReferenceNo(), awaitingCert.getStatus());

        CourseApplication internal = submit(divya, writing, firstMonday.plusDays(42), firstMonday.plusDays(43),
                SessionCode.PM, SessionCode.PM, "Refreshing our internal design documentation standards.");
        applicationService.cancel(divya.getId(), internal.getId(),
                "Project deadlines changed, moving this to the next quarter.", null);

        CourseClaim awaitingDecision = claims.submit(alice.getId(), claimForm(twoDay, twoDay.getCourseFee()),
                List.of(receipt("cloud-architecture-receipt.pdf")), List.of());
        log.debug("Left claim {} in {}", awaitingDecision.getId(), awaitingDecision.getStatus());

        CourseClaim settled = claims.submit(bob.getId(), claimForm(completed, new BigDecimal("1200.00")),
                List.of(receipt("agile-course-invoice.pdf")), List.of());
        claims.decide(marcus.getId(), settled.getId(), true, "Receipts match the approved amount.", null);
        claims.reimburse(admin.getId(), settled.getId(), "REIMB-" + thisYear + "-0001", null);

        log.info("Demo data ready. Sign in as admin, marcus.tan or alice.tan with the password {}.",
                DEMO_PASSWORD);
    }

    // ------------------------------------------------------------------- staff

    private Employee createStaff(String staffNo, String fullName, String email, String department,
            String designation, String username, RoleCode... roles) {
        StaffForm form = new StaffForm();
        form.setStaffNo(staffNo);
        form.setFullName(fullName);
        form.setEmail(email);
        form.setDepartment(department);
        form.setDesignationCode(designation);
        form.setActive(true);
        form.setUsername(username);
        form.setInitialPassword(DEMO_PASSWORD);
        form.setRoles(List.of(roles));
        return staff.create(form, null);
    }

    private void assign(Employee employee, Employee manager) {
        RoutingForm form = new RoutingForm();
        form.setEmployeeId(employee.getId());
        form.setManagerId(manager.getId());
        staff.assignManager(form, null);
    }

    private void grant(Employee employee, int year, int entitledUnits, BigDecimal budget) {
        EntitlementForm form = new EntitlementForm();
        form.setEmployeeId(employee.getId());
        form.setCalendarYear(year);
        form.setEntitledUnits(entitledUnits);
        form.setBudgetAmount(budget);
        entitlements.saveEntitlement(form, null);
    }

    // ------------------------------------------------------------- catalogue

    private TrainingProvider provider(String name) {
        ProviderForm form = new ProviderForm();
        form.setName(name);
        form.setActive(true);
        return catalogue.saveProvider(form, null);
    }

    private CourseCatalogue course(CategoryCode category, TrainingProvider provider, String title, BigDecimal fee) {
        CatalogueForm form = new CatalogueForm();
        form.setCategoryCode(category);
        form.setProviderId(provider.getId());
        form.setTitle(title);
        form.setDefaultFee(fee);
        form.setDescription(title + " - delivered by " + provider.getName() + ".");
        form.setActive(true);
        return catalogue.saveCourse(form, null);
    }

    private void seedHolidays() {
        int year = Year.now().getValue();
        holiday(LocalDate.of(year, 1, 1), "New Year's Day", "Declared public holiday");
        holiday(LocalDate.of(year, 5, 1), "Labour Day", "Declared public holiday");
        holiday(LocalDate.of(year, 8, 9), "National Day", "Declared public holiday");
        holiday(LocalDate.of(year, 12, 25), "Christmas Day", "Declared public holiday");
    }

    private void holiday(LocalDate date, String name, String sourceNote) {
        HolidayForm form = new HolidayForm();
        form.setHolidayDate(date);
        form.setName(name);
        form.setSourceNote(sourceNote);
        catalogue.saveHoliday(form, null);
    }

    // --------------------------------------------------------------- activity

    private CourseApplication submit(Employee employee, CourseCatalogue course, LocalDate start, LocalDate end,
            SessionCode startSession, SessionCode endSession, String justification) {
        ApplicationForm form = new ApplicationForm();
        form.setCatalogueId(course.getId());
        form.setCategoryCode(course.getCategory().getCode());
        form.setCourseTitle(course.getTitle());
        form.setProviderName(course.getProvider().getName());
        form.setStartDate(start);
        form.setEndDate(end);
        form.setStartSession(startSession);
        form.setEndSession(endSession);
        form.setCourseFee(course.getDefaultFee());
        form.setJustification(justification);
        form.setWorkDissemination("Share the key takeaways with the team at the next engineering forum.");
        return applicationService.create(employee.getId(), form);
    }

    private ClaimForm claimForm(CourseApplication application, BigDecimal amount) {
        ClaimForm form = new ClaimForm();
        form.setApplicationId(application.getId());
        form.setAmount(amount);
        form.setPaidByEmployee(true);
        return form;
    }

    /** A structurally valid tiny PDF so the upload content sniffing accepts the file. */
    private static MultipartFile receipt(String fileName) {
        String body = "%PDF-1.7\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n";
        return new ByteMultipartFile(fileName, "application/pdf", body.getBytes(StandardCharsets.UTF_8));
    }

    private static LocalDate nextWorkingDay(LocalDate date) {
        LocalDate candidate = date;
        while (candidate.getDayOfWeek() == DayOfWeek.SATURDAY || candidate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    private record ByteMultipartFile(String fileName, String contentType, byte[] content) implements MultipartFile {

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return fileName;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content.clone();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(File destination) throws IOException {
            java.nio.file.Files.write(destination.toPath(), content);
        }
    }
}
