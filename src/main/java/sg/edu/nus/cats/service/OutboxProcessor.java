package sg.edu.nus.cats.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import sg.edu.nus.cats.config.MailProperties;
import sg.edu.nus.cats.domain.EmailOutbox;
import sg.edu.nus.cats.domain.enums.OutboxStatus;
import sg.edu.nus.cats.repository.EmailOutboxRepository;
import sg.edu.nus.cats.service.mail.MailGateway;
import sg.edu.nus.cats.service.mail.MailTemplateService;

/**
 * Leases a small batch in a short transaction, sends outside the transaction and
 * then records the outcome. Delivery is at least once, which is why the business
 * ledger is written in the business transaction instead of here.
 */
@Service
public class OutboxProcessor {

    private final EmailOutboxRepository outbox;
    private final MailGateway gateway;
    private final MailTemplateService templates;
    private final MailProperties properties;
    private final Clock clock;

    public OutboxProcessor(EmailOutboxRepository outbox, MailGateway gateway, MailTemplateService templates,
            MailProperties properties, Clock clock) {
        this.outbox = outbox;
        this.gateway = gateway;
        this.templates = templates;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Long> claimBatch(int batchSize) {
        Instant now = Instant.now(clock);
        List<EmailOutbox> rows = outbox.findClaimable(OutboxStatus.PENDING, now, PageRequest.of(0, batchSize));
        rows.forEach(row -> {
            row.setStatus(OutboxStatus.SENDING);
            row.setAttempts(row.getAttempts() + 1);
            row.setLeaseUntil(now.plus(Duration.ofMinutes(2)));
        });
        outbox.saveAll(rows);
        return rows.stream().map(EmailOutbox::getId).toList();
    }

    /** Sends one leased row. Never joins the caller's business transaction. */
    public boolean deliver(Long id) {
        EmailOutbox row = outbox.findById(id).orElse(null);
        if (row == null || row.getStatus() != OutboxStatus.SENDING) {
            return false;
        }
        try {
            gateway.send(row.getRecipientEmail(), templates.subject(row), templates.body(row));
            markSent(id);
            return true;
        } catch (Exception ex) {
            markFailed(id, "Delivery failed: " + ex.getClass().getSimpleName());
            return false;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(Long id) {
        outbox.findById(id).ifPresent(row -> {
            row.setStatus(OutboxStatus.SENT);
            row.setSentAt(Instant.now(clock));
            row.setLeaseUntil(null);
            row.setLastError(null);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long id, String error) {
        outbox.findById(id).ifPresent(row -> {
            row.setLeaseUntil(null);
            row.setLastError(error == null ? "Delivery failed" : error.substring(0, Math.min(error.length(), 900)));
            if (row.getAttempts() >= properties.getMaxAttempts()) {
                row.setStatus(OutboxStatus.FAILED);
            } else {
                row.setStatus(OutboxStatus.PENDING);
                row.setNextAttemptAt(Instant.now(clock).plus(Duration.ofSeconds(15L * row.getAttempts())));
            }
        });
    }

    @Transactional
    public void retry(Long id) {
        outbox.findById(id).ifPresent(row -> {
            row.setStatus(OutboxStatus.PENDING);
            row.setNextAttemptAt(Instant.now(clock));
            row.setLeaseUntil(null);
        });
    }
}
