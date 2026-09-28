package sg.edu.nus.cats.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.edu.nus.cats.domain.ApplicationDay;
import sg.edu.nus.cats.domain.AuditEvent;
import sg.edu.nus.cats.domain.CourseApplication;
import sg.edu.nus.cats.domain.CourseCatalogue;
import sg.edu.nus.cats.domain.Employee;
import sg.edu.nus.cats.domain.enums.AggregateType;
import sg.edu.nus.cats.domain.enums.ApplicationStatus;
import sg.edu.nus.cats.domain.enums.AuditEventType;
import sg.edu.nus.cats.domain.enums.CategoryCode;
import sg.edu.nus.cats.domain.enums.LedgerEntryType;
import sg.edu.nus.cats.domain.enums.OutboxTemplateCode;
import sg.edu.nus.cats.dto.ApplicationForm;
import sg.edu.nus.cats.dto.PendingGroup;
import sg.edu.nus.cats.dto.PreviewResult;
import sg.edu.nus.cats.dto.ViewMapper;
import sg.edu.nus.cats.repository.ApprovalAssignmentRepository;
import sg.edu.nus.cats.repository.CourseApplicationRepository;
import sg.edu.nus.cats.repository.CourseCatalogueRepository;
import sg.edu.nus.cats.repository.EmployeeRepository;
import sg.edu.nus.cats.support.AccountBalance;
import sg.edu.nus.cats.support.BusinessException;
import sg.edu.nus.cats.support.ErrorCode;
import sg.edu.nus.cats.support.Hashing;
import sg.edu.nus.cats.support.Json;
import sg.edu.nus.cats.support.WorkingDay;

/**
 * Application lifecycle: preview, create, update, cancel, complete and delete.
 * Every write keeps the ledger, the audit trail and the outbox in step with the
 * application so a failed notification can never invalidate an approval.
 */
@Service
public class ApplicationService {

    private final CourseApplicationRepository applications;
    private final CourseCatalogueRepository catalogues;
    private final EmployeeRepository employees;
    private final ApprovalAssignmentRepository assignments;
    private final WorkingDayService workingDays;
    private final EntitlementService entitlements;
    private final LedgerService ledger;
    private final AuditService audit;
    private final OutboxService outbox;
    private final ReferenceNumberService references;
    private final Clock clock;

    public ApplicationService(CourseApplicationRepository applications, CourseCatalogueRepository catalogues,
            EmployeeRepository employees, ApprovalAssignmentRepository assignments, WorkingDayService workingDays,
            EntitlementService entitlements, LedgerService ledger, AuditService audit, OutboxService outbox,
            ReferenceNumberService references, Clock clock) {
        this.applications = applications;
        this.catalogues = catalogues;
        this.employees = employees;
        this.assignments = assignments;
        this.workingDays = workingDays;
        this.entitlements = entitlements;
        this.ledger = ledger;
        this.audit = audit;
        this.outbox = outbox;
        this.references = references;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- preview

    /** Dry run used by the preview button and by /api/v1/applications/preview. */
    @Transactional(readOnly = true)
    public PreviewResult preview(Long employeeId, ApplicationForm form) {
        Plan plan = plan(employeeId, form, null);
        List<PreviewResult.YearQuota> quotas = new ArrayList<>();
        if (plan.errors().isEmpty()) {
            plan.unitsByYear().forEach((year, units) -> {
                BigDecimal amount = amountFor(plan, year);
                Optional<AccountBalance> found = entitlements.find(employeeId, year);
                if (found.isEmpty()) {
                    plan.errors().add("No " + year + " training entitlement is configured for you. "
                            + "Ask an administrator to set it up.");
                    return;
                }
                AccountBalance balance = found.get();
                try {
                    entitlements.assertAvailable(balance, units, amount, year);
                } catch (BusinessException ex) {
                    plan.errors().add(ex.getMessage());
                }
                quotas.add(new PreviewResult.YearQuota(year, units, balance.entitledUnits(), balance.usedUnits(),
                        balance.availableUnits(), amount, balance.budgetAmount(), balance.usedAmount(),
                        balance.availableAmount(), balance.hasUnitsFor(units), balance.hasBudgetFor(amount),
                        balance.hasUnitsFor(units) && balance.hasBudgetFor(amount) ? "Within entitlement"
                                : "Exceeds entitlement"));
            });
        }
        List<PreviewResult.ScheduleRow> rows = plan.days().stream()
                .map(day -> new PreviewResult.ScheduleRow(day.date(),
                        day.date().getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH),
                        day.sessionCode().getLabel(), day.units()))
                .toList();
        return new PreviewResult(plan.errors(), plan.excluded(), rows, plan.unitsByYear(), quotas, plan.conflicts(),
                plan.notices(), plan.totalUnits(), plan.fee());
    }

    // ----------------------------------------------------------------- create

    @Transactional
    public CourseApplication create(Long employeeId, ApplicationForm form) {
        Employee employee = requireEmployee(employeeId);
        String requestHash = Hashing.sha256Hex(form.cacheKey(employeeId));
        String clientRequestId = blankToNull(form.getClientRequestId());
        if (clientRequestId != null) {
            Optional<CourseApplication> replayed = applications
                    .findByEmployeeIdAndClientRequestId(employeeId, clientRequestId);
            if (replayed.isPresent()) {
                CourseApplication existing = replayed.get();
                if (requestHash.equals(existing.getCreateRequestHash())) {
                    return existing;
                }
                throw new BusinessException(ErrorCode.DUPLICATE_REQUEST,
                        "This submission was already received with different details. "
                                + "Reload the page before submitting again.");
            }
        }

        Plan plan = plan(employeeId, form, null);
        requireNoErrors(plan);
        Employee approver = resolveApprover(employee);

        Map<Integer, AccountBalance> balances = new TreeMap<>();
        plan.unitsByYear().forEach((year, units) -> {
            AccountBalance balance = entitlements.require(employeeId, year);
            entitlements.assertAvailable(balance, units, amountFor(plan, year), year);
            balances.put(year, balance);
        });

        Instant now = Instant.now(clock);
        CourseApplication application = plan.prototype();
        application.setEmployee(employee);
        application.setApprover(approver);
        application.setReferenceNo(references.nextApplicationReference(plan.startYear()));
        application.setClientRequestId(clientRequestId);
        application.setCreateRequestHash(requestHash);
        application.markSubmitted(now);
        CourseApplication saved = applications.saveAndFlush(application);

        plan.days().forEach(day -> saved.addDay(new ApplicationDay(day.date(), day.sessionCode())));
        applications.saveAndFlush(saved);

        AuditEvent event = audit.record(AggregateType.APPLICATION, key(saved), AuditEventType.SUBMITTED, employee, null,
                saved.getStatus().name(), null, snapshot(saved));
        balances.forEach((year, balance) -> ledger.record(balance.account(), event, saved, null,
                LedgerEntryType.RESERVE, plan.unitsByYear().get(year), 0, amountFor(plan, year), BigDecimal.ZERO,
                BigDecimal.ZERO, now));
        notifyApprover(event, saved, approver);
        return saved;
    }

    // ----------------------------------------------------------------- update

    @Transactional
    public CourseApplication update(Long employeeId, Long applicationId, ApplicationForm form) {
        Employee employee = requireEmployee(employeeId);
        CourseApplication application = requireOwned(employeeId, applicationId);
        requirePendingReview(application, "changed");
        requireVersion(application.getVersion(), form.getVersion());

        Plan plan = plan(employeeId, form, applicationId);
        requireNoErrors(plan);

        Instant now = Instant.now(clock);
        Map<Integer, Integer> previousUnits = unitsByYearOf(application);
        AuditEvent event = audit.record(AggregateType.APPLICATION, key(application), AuditEventType.UPDATED, employee,
                application.getStatus().name(), ApplicationStatus.UPDATED.name(), null, snapshot(application));

        release(employeeId, application, event, previousUnits, application.getCourseFee(),
                LedgerEntryType.UPDATE_RESERVATION, now);
        reserve(employeeId, application, event, plan, now);

        copyEditableFields(application, plan.prototype());
        application.getDays().clear();
        applications.flush();
        plan.days().forEach(day -> application.addDay(new ApplicationDay(day.date(), day.sessionCode())));
        application.markUpdated(now);
        applications.saveAndFlush(application);

        notifyApprover(event, application, application.getApprover());
        return application;
    }

    // --------------------------------------------------------- state changes

    @Transactional
    public CourseApplication cancel(Long employeeId, Long applicationId, String reason, Long expectedVersion) {
        Employee employee = requireEmployee(employeeId);
        CourseApplication application = requireOwned(employeeId, applicationId);
        if (!application.getStatus().isPendingReview() && application.getStatus() != ApplicationStatus.APPROVED) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "An application that is " + application.getStatus().getDisplayName().toLowerCase()
                            + " can no longer be cancelled.");
        }
        requireVersion(application.getVersion(), expectedVersion);
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "A cancellation reason is required.");
        }
        Instant now = Instant.now(clock);
        AuditEvent event = audit.record(AggregateType.APPLICATION, key(application), AuditEventType.CANCELLED, employee,
                application.getStatus().name(), ApplicationStatus.CANCELLED.name(), reason, snapshot(application));
        release(employeeId, application, event, unitsByYearOf(application), application.getCourseFee(),
                LedgerEntryType.RELEASE, now);
        application.cancel(reason, now);
        return applications.saveAndFlush(application);
    }

    @Transactional
    public CourseApplication complete(Long employeeId, Long applicationId, String experience, Long expectedVersion) {
        Employee employee = requireEmployee(employeeId);
        CourseApplication application = requireOwned(employeeId, applicationId);
        if (application.getStatus() != ApplicationStatus.APPROVED) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Only an approved application can be marked as completed.");
        }
        requireVersion(application.getVersion(), expectedVersion);
        if (experience == null || experience.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Share what you learned before marking the course as completed.");
        }
        if (!application.getEndDate().isBefore(LocalDate.now(clock))) {
            throw new BusinessException(ErrorCode.COURSE_NOT_ENDED,
                    "The course has not ended yet. Complete it after " + ViewMapper.date(application.getEndDate()) + ".");
        }
        Instant now = Instant.now(clock);
        audit.record(AggregateType.APPLICATION, key(application), AuditEventType.COMPLETED, employee,
                application.getStatus().name(), ApplicationStatus.COMPLETED.name(), experience, snapshot(application));
        application.complete(experience, now);
        return applications.saveAndFlush(application);
    }

    @Transactional
    public CourseApplication delete(Long employeeId, Long applicationId, Long expectedVersion) {
        Employee employee = requireEmployee(employeeId);
        CourseApplication application = requireOwned(employeeId, applicationId);
        requirePendingReview(application, "deleted");
        requireVersion(application.getVersion(), expectedVersion);
        Instant now = Instant.now(clock);
        AuditEvent event = audit.record(AggregateType.APPLICATION, key(application), AuditEventType.DELETED, employee,
                application.getStatus().name(), ApplicationStatus.DELETED.name(), null, snapshot(application));
        release(employeeId, application, event, unitsByYearOf(application), application.getCourseFee(),
                LedgerEntryType.RELEASE, now);
        application.markDeleted(now);
        return applications.saveAndFlush(application);
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public CourseApplication require(Long applicationId) {
        return applications.findWithDetailsById(applicationId)
                .orElseThrow(() -> BusinessException.notFound("Application"));
    }

    @Transactional(readOnly = true)
    public CourseApplication requireOwned(Long employeeId, Long applicationId) {
        CourseApplication application = applications.findWithDetailsById(applicationId)
                .orElseThrow(() -> BusinessException.notFound("Application"));
        if (!application.getEmployee().getId().equals(employeeId)) {
            throw BusinessException.accessDenied("This application belongs to another employee.");
        }
        return application;
    }

    @Transactional(readOnly = true)
    public List<PendingGroup.CourseApplicationView> history(Long employeeId, LocalDate from, LocalDate to,
            CategoryCode category, List<ApplicationStatus> statuses) {
        List<ApplicationStatus> filter = statuses == null || statuses.isEmpty() ? null : statuses;
        return applications.findForEmployeeHistory(employeeId, from, to, category, filter).stream()
                .map(ViewMapper::applicationView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AccountBalance> balances(Long employeeId) {
        return entitlements.balancesOf(employeeId);
    }

    // -------------------------------------------------------------- internals

    private Plan plan(Long employeeId, ApplicationForm form, Long excludeApplicationId) {
        List<String> errors = new ArrayList<>();
        List<String> notices = new ArrayList<>();
        List<PreviewResult.Conflict> conflicts = new ArrayList<>();

        CourseCatalogue catalogue = null;
        if (form.getCatalogueId() != null) {
            catalogue = catalogues.findById(form.getCatalogueId()).orElse(null);
            if (catalogue == null) {
                errors.add("The selected catalogue course no longer exists.");
            }
        }
        CategoryCode category = form.getCategoryCode() != null ? form.getCategoryCode()
                : catalogue == null || catalogue.getCategory() == null ? null : catalogue.getCategory().getCode();
        if (category == null) {
            errors.add("Select a training category.");
        }

        CourseApplication prototype = new CourseApplication();
        prototype.setCatalogue(catalogue);
        prototype.setCategoryCode(category);
        prototype.setCourseTitle(trimToNull(
                form.getCourseTitle() != null ? form.getCourseTitle() : catalogue == null ? null : catalogue.getTitle()));
        prototype.setProviderName(resolveProvider(form, catalogue, category));
        prototype.setStartDate(form.getStartDate());
        prototype.setEndDate(form.getEndDate());
        prototype.setStartSession(form.getStartSession());
        prototype.setEndSession(form.getEndSession());
        prototype.setCourseFee(resolveFee(form, catalogue));
        prototype.setJustification(trimToNull(form.getJustification()));
        prototype.setWorkDissemination(trimToNull(form.getWorkDissemination()));

        if (isBlank(prototype.getCourseTitle())) {
            errors.add("Enter the course title.");
        }
        if (isBlank(prototype.getProviderName())) {
            errors.add("Enter the training provider.");
        }
        if (isBlank(prototype.getJustification())) {
            errors.add("Explain why this training is needed.");
        }
        if (form.getStartDate() != null && form.getEndDate() != null
                && form.getStartDate().isAfter(form.getEndDate())) {
            errors.add("The start date must not be after the end date.");
        }
        if (category != null) {
            try {
                prototype.validateSchedule();
            } catch (IllegalArgumentException ex) {
                errors.add(ex.getMessage());
            }
            try {
                category.validateFee(prototype.getCourseFee());
            } catch (IllegalArgumentException ex) {
                errors.add(ex.getMessage());
            }
        }

        List<WorkingDay> days = List.of();
        List<sg.edu.nus.cats.support.ExcludedDay> excluded = List.of();
        Map<Integer, Integer> unitsByYear = Map.of();
        int totalUnits = 0;
        if (errors.isEmpty()) {
            WorkingDayService.Expansion expansion = workingDays.expand(prototype.getStartDate(), prototype.getEndDate(),
                    prototype.getStartSession(), prototype.getEndSession(), category);
            if (expansion.isEmpty()) {
                errors.add("Every date in this period is a weekend or a public holiday. "
                        + "Choose a period that contains at least one working day.");
            } else {
                days = expansion.days();
                excluded = expansion.excluded();
                unitsByYear = workingDays.unitsByYear(days);
                totalUnits = expansion.totalUnits();
                if (!excluded.isEmpty()) {
                    notices.add(excluded.size() + " date(s) were skipped because they are weekends or public holidays.");
                }
                if (unitsByYear.size() > 1) {
                    notices.add("This course crosses a calendar year: training days are charged to each year and the "
                            + "fee is counted in " + prototype.getStartDate().getYear() + ".");
                }
                if (days.stream().anyMatch(day -> day.units() == 1)) {
                    notices.add("Half day sessions consume one half day unit; three half days are added to the account "
                            + "as one and a half days.");
                }
                for (CourseApplication other : applications.findOverlapping(employeeId, prototype.getStartDate(),
                        prototype.getEndDate(), ApplicationStatus.budgetHoldingStatuses())) {
                    if (excludeApplicationId != null && excludeApplicationId.equals(other.getId())) {
                        continue;
                    }
                    conflicts.add(new PreviewResult.Conflict("OVERLAP",
                            "Overlaps " + other.getReferenceNo(),
                            other.getStatus().getDisplayName() + ": " + ViewMapper.period(other)));
                }
                if (!conflicts.isEmpty()) {
                    notices.add("You already have training booked in this period. Review the clash before submitting.");
                }
            }
        }
        return new Plan(prototype, days, excluded, unitsByYear, totalUnits, conflicts, notices, errors);
    }

    private int startYearOf(Plan plan) {
        return plan.prototype().getStartDate() == null ? LocalDate.now(clock).getYear()
                : plan.prototype().getStartDate().getYear();
    }

    private BigDecimal amountFor(Plan plan, int year) {
        return year == startYearOf(plan) ? plan.fee() : BigDecimal.ZERO;
    }

    private BigDecimal resolveFee(ApplicationForm form, CourseCatalogue catalogue) {
        BigDecimal fee = form.getCourseFee();
        if (fee == null && catalogue != null) {
            fee = catalogue.getDefaultFee();
        }
        return fee == null ? BigDecimal.ZERO : fee.setScale(2, RoundingMode.HALF_UP);
    }

    private String resolveProvider(ApplicationForm form, CourseCatalogue catalogue, CategoryCode category) {
        if (category == CategoryCode.INTERNAL) {
            return CategoryCode.INTERNAL_PROVIDER;
        }
        if (form.getProviderName() != null && !form.getProviderName().isBlank()) {
            return form.getProviderName().trim();
        }
        return catalogue == null || catalogue.getProvider() == null ? null : catalogue.getProvider().getName();
    }

    private Map<Integer, Integer> unitsByYearOf(CourseApplication application) {
        Map<Integer, Integer> result = new TreeMap<>();
        application.getDays().forEach(day -> result.merge(day.getTrainingDate().getYear(), day.getUnits(), Integer::sum));
        return result;
    }

    private void release(Long employeeId, CourseApplication application, AuditEvent event, Map<Integer, Integer> units,
            BigDecimal fee, LedgerEntryType entryType, Instant now) {
        int feeYear = application.getStartDate() == null ? -1 : application.getStartDate().getYear();
        BigDecimal released = fee == null ? BigDecimal.ZERO : fee;
        units.forEach((year, amount) -> entitlements.find(employeeId, year).ifPresent(balance -> ledger.record(
                balance.account(), event, application, null, entryType, -amount, 0,
                year == feeYear ? released.negate() : BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, now)));
    }

    private void reserve(Long employeeId, CourseApplication application, AuditEvent event, Plan plan, Instant now) {
        plan.unitsByYear().forEach((year, units) -> {
            AccountBalance balance = entitlements.require(employeeId, year);
            entitlements.assertAvailable(balance, units, amountFor(plan, year), year);
            ledger.record(balance.account(), event, application, null, LedgerEntryType.UPDATE_RESERVATION, units, 0,
                    amountFor(plan, year), BigDecimal.ZERO, BigDecimal.ZERO, now);
        });
    }

    private void copyEditableFields(CourseApplication target, CourseApplication source) {
        target.setCatalogue(source.getCatalogue());
        target.setCategoryCode(source.getCategoryCode());
        target.setCourseTitle(source.getCourseTitle());
        target.setProviderName(source.getProviderName());
        target.setStartDate(source.getStartDate());
        target.setEndDate(source.getEndDate());
        target.setStartSession(source.getStartSession());
        target.setEndSession(source.getEndSession());
        target.setCourseFee(source.getCourseFee());
        target.setJustification(source.getJustification());
        target.setWorkDissemination(source.getWorkDissemination());
    }

    private Employee requireEmployee(Long employeeId) {
        return employees.findById(employeeId).orElseThrow(() -> BusinessException.notFound("Employee"));
    }

    private Employee resolveApprover(Employee employee) {
        Employee manager = assignments.findWithManagerByEmployeeId(employee.getId())
                .map(assignment -> assignment.getManager())
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_APPROVER,
                        "No approving manager is assigned to you yet. Ask an administrator to set your reporting line."));
        if (manager.getId().equals(employee.getId())) {
            throw new BusinessException(ErrorCode.ROUTING_INVALID,
                    "Your approving manager is yourself. Ask an administrator to assign a different manager.");
        }
        return manager;
    }

    private void requireNoErrors(Plan plan) {
        if (!plan.errors().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, String.join(" ", plan.errors()));
        }
    }

    private void requirePendingReview(CourseApplication application, String action) {
        if (!application.getStatus().isPendingReview()) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "An application that is " + application.getStatus().getDisplayName().toLowerCase() + " cannot be "
                            + action + ".");
        }
    }

    private void requireVersion(long current, Long submitted) {
        if (submitted != null && submitted != current) {
            throw new BusinessException(ErrorCode.STALE_VERSION,
                    "Someone else changed this application. Reload the page and try again.");
        }
    }

    private void notifyApprover(AuditEvent event, CourseApplication application, Employee approver) {
        outbox.enqueue(event, approver, OutboxTemplateCode.APPLICATION_SUBMITTED,
                Map.of("reference", application.getReferenceNo(),
                        "applicationId", application.getId(),
                        "recipientName", approver.getFullName(),
                        "summary", application.getEmployee().getFullName() + " submitted "
                                + application.getCourseTitle() + " (" + ViewMapper.period(application)
                                + ") for your decision."));
    }

    private static String key(CourseApplication application) {
        return application.getReferenceNo() == null ? "APP-" + application.getId() : application.getReferenceNo();
    }

    private static String snapshot(CourseApplication application) {
        return Json.write(Map.of(
                "status", application.getStatus().name(),
                "category", application.getCategoryCode() == null ? "" : application.getCategoryCode().name(),
                "startDate", String.valueOf(application.getStartDate()),
                "endDate", String.valueOf(application.getEndDate()),
                "units", application.getReservedUnits(),
                "fee", ViewMapper.money(application.getCourseFee())));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String blankToNull(String value) {
        return trimToNull(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Internal working result of one preview or validation pass. */
    private record Plan(CourseApplication prototype, List<WorkingDay> days,
            List<sg.edu.nus.cats.support.ExcludedDay> excluded, Map<Integer, Integer> unitsByYear, int totalUnits,
            List<PreviewResult.Conflict> conflicts, List<String> notices, List<String> errors) {

        BigDecimal fee() {
            return prototype.getCourseFee();
        }

        int startYear() {
            return prototype.getStartDate() == null ? 0 : prototype.getStartDate().getYear();
        }
    }
}
