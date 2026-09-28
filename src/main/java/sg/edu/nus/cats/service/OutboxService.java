package sg.edu.nus.cats.service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.edu.nus.cats.domain.AuditEvent;
import sg.edu.nus.cats.domain.EmailOutbox;
import sg.edu.nus.cats.domain.Employee;
import sg.edu.nus.cats.domain.enums.AggregateType;
import sg.edu.nus.cats.domain.enums.AuditEventType;
import sg.edu.nus.cats.domain.enums.OutboxStatus;
import sg.edu.nus.cats.domain.enums.OutboxTemplateCode;
import sg.edu.nus.cats.repository.EmailOutboxRepository;
import sg.edu.nus.cats.support.BusinessException;
import sg.edu.nus.cats.support.ErrorCode;
import sg.edu.nus.cats.support.Json;

/**
 * Transactional outbox: the notification row is written in the same transaction
 * as the business change, so a mail failure can never roll back an approval.
 */
@Service
public class OutboxService {

    private final EmailOutboxRepository outbox;
    private final AuditService audit;
    private final Clock clock;

    public OutboxService(EmailOutboxRepository outbox, AuditService audit, Clock clock) {
        this.outbox = outbox;
        this.audit = audit;
        this.clock = clock;
    }

    /** Puts a failed message back on the queue; the worker picks it up on its next sweep. */
    @Transactional
    public EmailOutbox retry(Long id, Employee actor) {
        EmailOutbox row = outbox.findById(id).orElseThrow(() -> BusinessException.notFound("Email"));
        if (row.getStatus() != OutboxStatus.FAILED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Only failed emails can be retried; this message is " + row.getStatus() + ".");
        }
        String before = row.getStatus() + " after " + row.getAttempts() + " attempt(s)";
        row.setStatus(OutboxStatus.PENDING);
        row.setNextAttemptAt(Instant.now(clock));
        row.setLeaseUntil(null);
        EmailOutbox saved = outbox.save(row);
        audit.record(AggregateType.OUTBOX, "EMAIL-" + saved.getId(), AuditEventType.EMAIL_RETRY, actor,
                before, "PENDING", "Manual retry requested", Json.write(Map.of("attempts", saved.getAttempts(),
                        "lastError", saved.getLastError() == null ? "" : saved.getLastError())));
        return saved;
    }

    @Transactional
    public EmailOutbox enqueue(AuditEvent event, Employee recipient, OutboxTemplateCode templateCode,
            Map<String, Object> payload) {
        Map<String, Object> data = new LinkedHashMap<>(payload);
        data.put("recipientName", recipient.getFullName());
        EmailOutbox row = new EmailOutbox();
        row.setEvent(event);
        row.setRecipient(recipient);
        row.setRecipientEmail(recipient.getEmail());
        row.setTemplateCode(templateCode);
        row.setPayloadJson(Json.write(data));
        row.setStatus(OutboxStatus.PENDING);
        row.setAttempts(0);
        row.setNextAttemptAt(Instant.now(clock));
        row.setCreatedAt(Instant.now(clock));
        return outbox.save(row);
    }
}
