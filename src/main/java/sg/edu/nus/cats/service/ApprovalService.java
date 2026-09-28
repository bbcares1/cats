package sg.edu.nus.cats.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.edu.nus.cats.domain.AuditEvent;
import sg.edu.nus.cats.domain.CourseApplication;
import sg.edu.nus.cats.domain.Employee;
import sg.edu.nus.cats.domain.enums.AggregateType;
import sg.edu.nus.cats.domain.enums.ApplicationStatus;
import sg.edu.nus.cats.domain.enums.AuditEventType;
import sg.edu.nus.cats.domain.enums.LedgerEntryType;
import sg.edu.nus.cats.domain.enums.OutboxTemplateCode;
import sg.edu.nus.cats.dto.PendingGroup;
import sg.edu.nus.cats.dto.ViewMapper;
import sg.edu.nus.cats.repository.ApprovalAssignmentRepository;
import sg.edu.nus.cats.repository.CourseApplicationRepository;
import sg.edu.nus.cats.repository.EmployeeRepository;
import sg.edu.nus.cats.support.AccountBalance;
import sg.edu.nus.cats.support.BusinessException;
import sg.edu.nus.cats.support.ErrorCode;

/** Manager side of the application flow: the grouped queue and the decision itself. */
@Service
public class ApprovalService {

    private final CourseApplicationRepository applications;
    private final EmployeeRepository employees;
    private final ApprovalAssignmentRepository assignments;
    private final EntitlementService entitlements;
    private final LedgerService ledger;
    private final AuditService audit;
    private final OutboxService outbox;
    private final Clock clock;

    public ApprovalService(CourseApplicationRepository applications, EmployeeRepository employees,
            ApprovalAssignmentRepository assignments, EntitlementService entitlements, LedgerService ledger,
            AuditService audit, OutboxService outbox, Clock clock) {
        this.applications = applications;
        this.employees = employees;
        this.assignments = assignments;
        this.entitlements = entitlements;
        this.ledger = ledger;
        this.audit = audit;
        this.outbox = outbox;
        this.clock = clock;
    }

    /** Applications grouped per employee, exactly as page P06 shows them. */
    @Transactional(readOnly = true)
    public Page<PendingGroup> pendingGroups(Long managerId, Pageable pageable) {
        Page<Object[]> grouped = applications.findPendingGroups(managerId, ApplicationStatus.pendingStatuses(),
                pageable);
        return grouped.map(row -> {
            Long employeeId = ((Number) row[0]).longValue();
            String employeeName = (String) row[1];
            String department = (String) row[2];
            long count = ((Number) row[3]).longValue();
            List<PendingGroup.CourseApplicationView> rows = applications
                    .findByApproverIdAndEmployeeIdAndStatusInOrderBySubmittedAtAsc(managerId, employeeId,
                            ApplicationStatus.pendingStatuses())
                    .stream()
                    .map(ViewMapper::applicationView)
                    .toList();
            String notice = employeeId.equals(managerId)
                    ? "This is your own application. A different manager must decide it."
                    : null;
            return new PendingGroup(employeeId, employeeName, department, count, rows, notice);
        });
    }

    @Transactional(readOnly = true)
    public List<PendingGroup.CourseApplicationView> pendingForEmployee(Long managerId, Long employeeId) {
        return applications
                .findByApproverIdAndEmployeeIdAndStatusInOrderBySubmittedAtAsc(managerId, employeeId,
                        ApplicationStatus.pendingStatuses())
                .stream()
                .map(ViewMapper::applicationView)
                .toList();
    }

    @Transactional(readOnly = true)
    public long pendingCount(Long managerId) {
        return applications.countByApproverIdAndStatusIn(managerId, ApplicationStatus.pendingStatuses());
    }

    @Transactional
    public CourseApplication decide(Long managerId, Long applicationId, boolean approved, String reason,
            Long expectedVersion) {
        Employee manager = employees.findById(managerId).orElseThrow(() -> BusinessException.notFound("Manager"));
        CourseApplication application = applications.findWithDetailsById(applicationId)
                .orElseThrow(() -> BusinessException.notFound("Application"));

        if (application.getApprover() == null || !application.getApprover().getId().equals(managerId)) {
            throw BusinessException.accessDenied("You are not the approving manager for this application.");
        }
        if (application.getEmployee().getId().equals(managerId)) {
            throw BusinessException.accessDenied("You cannot decide your own training application.");
        }
        if (!application.getStatus().isPendingReview()) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "This application is already " + application.getStatus().getDisplayName().toLowerCase()
                            + " and cannot be decided again.");
        }
        if (expectedVersion != null && expectedVersion != application.getVersion()) {
            throw new BusinessException(ErrorCode.STALE_VERSION,
                    "This application was changed after you opened it. Reload the queue and decide again.");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "A reason is required for every decision.");
        }

        Instant now = Instant.now(clock);
        Map<Integer, Integer> units = unitsByYearOf(application);
        int feeYear = application.getStartDate().getYear();
        AuditEvent event = audit.record(AggregateType.APPLICATION, application.getReferenceNo(),
                approved ? AuditEventType.APPROVED : AuditEventType.REJECTED, manager,
                application.getStatus().name(),
                approved ? ApplicationStatus.APPROVED.name() : ApplicationStatus.REJECTED.name(), reason,
                snapshot(application));

        units.forEach((year, amount) -> entitlements.find(application.getEmployee().getId(), year)
                .ifPresent(balance -> ledger.record(balance.account(), event, application, null,
                        approved ? LedgerEntryType.COMMIT : LedgerEntryType.RELEASE,
                        approved ? -amount : -amount,
                        approved ? amount : 0,
                        negativeIfPositive(amountFor(application, year, feeYear)),
                        approved ? amountFor(application, year, feeYear) : BigDecimal.ZERO,
                        BigDecimal.ZERO, now)));

        application.decide(approved, manager, reason, now);
        CourseApplication saved = applications.saveAndFlush(application);

        outbox.enqueue(event, saved.getEmployee(),
                approved ? OutboxTemplateCode.APPLICATION_APPROVED : OutboxTemplateCode.APPLICATION_REJECTED,
                Map.of("reference", saved.getReferenceNo(),
                        "applicationId", saved.getId(),
                        "recipientName", saved.getEmployee().getFullName(),
                        "reason", reason,
                        "summary", (approved ? "Approved: " : "Rejected: ") + saved.getCourseTitle() + " ("
                                + ViewMapper.period(saved) + ")."));
        return saved;
    }

    private BigDecimal amountFor(CourseApplication application, int year, int feeYear) {
        return year == feeYear ? application.getCourseFee() : BigDecimal.ZERO;
    }

    private BigDecimal negativeIfPositive(BigDecimal value) {
        return value.signum() > 0 ? value.negate() : BigDecimal.ZERO;
    }

    private Map<Integer, Integer> unitsByYearOf(CourseApplication application) {
        Map<Integer, Integer> result = new TreeMap<>();
        application.getDays().forEach(day -> result.merge(day.getTrainingDate().getYear(), day.getUnits(), Integer::sum));
        return result;
    }

    private static String snapshot(CourseApplication application) {
        return sg.edu.nus.cats.support.Json.write(Map.of(
                "reference", application.getReferenceNo(),
                "status", application.getStatus().name(),
                "units", application.getReservedUnits(),
                "fee", ViewMapper.money(application.getCourseFee())));
    }

    /** Balances of every employee reporting to this manager, for the team dashboard. */
    @Transactional(readOnly = true)
    public List<TeamMemberSummary> teamSummary(Long managerId, int calendarYear) {
        return assignments.findByManagerIdOrderByEmployeeIdAsc(managerId).stream()
                .map(assignment -> employees.findById(assignment.getEmployeeId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(member -> {
            AccountBalance balance = entitlements.find(member.getId(), calendarYear).orElse(null);
            return new TeamMemberSummary(member.getId(), member.getFullName(), member.getDepartment(), balance);
        }).toList();
    }

    public record TeamMemberSummary(Long employeeId, String fullName, String department, AccountBalance balance) {
    }
}
