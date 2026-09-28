package sg.edu.nus.cats.web;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import sg.edu.nus.cats.domain.AuditEvent;
import sg.edu.nus.cats.domain.EmailOutbox;
import sg.edu.nus.cats.domain.enums.AggregateType;
import sg.edu.nus.cats.domain.enums.ApplicationStatus;
import sg.edu.nus.cats.domain.enums.ClaimStatus;
import sg.edu.nus.cats.domain.enums.OutboxStatus;
import sg.edu.nus.cats.repository.AuditEventRepository;
import sg.edu.nus.cats.repository.CourseApplicationRepository;
import sg.edu.nus.cats.repository.CourseClaimRepository;
import sg.edu.nus.cats.repository.EmailOutboxRepository;
import sg.edu.nus.cats.repository.EmployeeRepository;
import sg.edu.nus.cats.security.CurrentUser;
import sg.edu.nus.cats.service.OutboxService;
import sg.edu.nus.cats.support.BusinessException;

/** P16 audit trail and email outbox operations, plus the admin landing page. */
@Controller
@RequestMapping("/admin")
public class AdminOperationsController {

    private final CurrentUser currentUser;
    private final AuditEventRepository auditEvents;
    private final EmailOutboxRepository outbox;
    private final OutboxService outboxService;
    private final EmployeeRepository employees;
    private final CourseApplicationRepository applications;
    private final CourseClaimRepository claims;
    private final Clock clock;

    public AdminOperationsController(CurrentUser currentUser, AuditEventRepository auditEvents,
            EmailOutboxRepository outbox, OutboxService outboxService, EmployeeRepository employees,
            CourseApplicationRepository applications, CourseClaimRepository claims, Clock clock) {
        this.currentUser = currentUser;
        this.auditEvents = auditEvents;
        this.outbox = outbox;
        this.outboxService = outboxService;
        this.employees = employees;
        this.applications = applications;
        this.claims = claims;
        this.clock = clock;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public String dashboard(Model model) {
        model.addAttribute("pendingApplications",
                applications.countByStatusIn(ApplicationStatus.pendingStatuses()));
        model.addAttribute("undecidedClaims", claims.countByStatusIn(ClaimStatus.openStatuses()));
        model.addAttribute("awaitingReimbursement", claims.countByStatus(ClaimStatus.APPROVED));
        model.addAttribute("queuedEmails", outbox.countByStatus(OutboxStatus.PENDING));
        model.addAttribute("failedEmails", outbox.countByStatus(OutboxStatus.FAILED));
        model.addAttribute("activeStaff", employees.countByActiveTrue());
        model.addAttribute("outboxRows", outbox.findTop20ByOrderByCreatedAtDesc().stream()
                .map(this::outboxRow).toList());
        model.addAttribute("recentAudit", auditEvents.findTop20ByOrderByCreatedAtDesc().stream()
                .map(this::auditRow).toList());
        return "admin/dashboard";
    }

    /* ------------------------------------------------------- P16 audit trail */

    @GetMapping("/operations")
    @Transactional(readOnly = true)
    public String operations(
            @RequestParam(required = false) String aggregateType,
            @RequestParam(required = false) String aggregateKey,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "false") boolean failedOnly,
            Model model) {
        LocalDate start = from == null ? LocalDate.now(clock).minusDays(30) : from;
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        if (end.isBefore(start)) {
            throw new BusinessException(sg.edu.nus.cats.support.ErrorCode.INVALID_REQUEST,
                    "The end date must not be before the start date.");
        }
        AggregateType aggregate = aggregateType == null || aggregateType.isBlank()
                ? null
                : AggregateType.valueOf(aggregateType);
        Instant fromInstant = start.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        Instant toInstant = end.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();

        Page<AuditEvent> events = auditEvents.search(aggregate, blankToNull(aggregateKey), null, fromInstant,
                toInstant, PageRequest.of(Math.max(0, page), 25));
        model.addAttribute("page", events);
        model.addAttribute("auditRows", events.getContent().stream().map(this::auditRow).toList());
        model.addAttribute("aggregateTypes", AggregateType.values());
        model.addAttribute("aggregateType", aggregateType);
        model.addAttribute("aggregateKey", aggregateKey);
        model.addAttribute("from", start);
        model.addAttribute("to", end);

        List<EmailOutbox> queue = failedOnly
                ? outbox.findByStatusOrderByCreatedAtAsc(OutboxStatus.FAILED)
                : outbox.findTop20ByOrderByCreatedAtDesc();
        model.addAttribute("failedOnly", failedOnly);
        model.addAttribute("outboxRows", queue.stream().map(this::outboxRow).toList());
        model.addAttribute("outboxCounts", outboxCounts());
        return "admin/operations";
    }

    @PostMapping("/outbox/{id}/retry")
    public String retry(@PathVariable Long id, RedirectAttributes flash) {
        EmailOutbox message = outboxService.retry(id, currentUser.require());
        flash.addFlashAttribute(message.getStatus() == OutboxStatus.FAILED ? "warning" : "message",
                "Email " + message.getId() + " queued again (status " + message.getStatus() + ").");
        return "redirect:/admin/operations";
    }

    /* --------------------------------------------------------------- helpers */

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private List<String> outboxCounts() {
        List<String> counts = new ArrayList<>();
        for (OutboxStatus status : OutboxStatus.values()) {
            counts.add(status + ": " + outbox.countByStatus(status));
        }
        return counts;
    }

    private AuditRow auditRow(AuditEvent event) {
        return new AuditRow(event.getId(), event.getCreatedAt(), event.getAggregateType(),
                event.getAggregateKey(), event.getEventType(), event.getActorLabel(),
                event.getFromState(), event.getToState(), event.getReason(), event.getCorrelationId());
    }

    private OutboxRow outboxRow(EmailOutbox message) {
        return new OutboxRow(message.getId(), message.getRecipientEmail(), message.getTemplateCode(),
                message.getStatus(), message.getAttempts(), message.getLastError(),
                message.getCreatedAt(), message.getSentAt(), message.getNextAttemptAt());
    }

    public record AuditRow(Long id, Instant occurredAt, AggregateType aggregateType, String aggregateKey,
            sg.edu.nus.cats.domain.enums.AuditEventType eventType, String actor, String fromState, String toState,
            String reason, String correlationId) {
    }

    public record OutboxRow(Long id, String recipientEmail, sg.edu.nus.cats.domain.enums.OutboxTemplateCode template,
            OutboxStatus status, int attempts, String lastError, Instant createdAt, Instant sentAt,
            Instant nextAttemptAt) {

        public boolean isRetryable() {
            return status == OutboxStatus.FAILED;
        }

        public String age() {
            long minutes = ChronoUnit.MINUTES.between(createdAt, Instant.now());
            return minutes < 60 ? minutes + " min ago" : (minutes / 60) + " h ago";
        }
    }
}
