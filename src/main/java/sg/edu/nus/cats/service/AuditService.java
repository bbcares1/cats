package sg.edu.nus.cats.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.edu.nus.cats.domain.AuditEvent;
import sg.edu.nus.cats.domain.Employee;
import sg.edu.nus.cats.domain.enums.AggregateType;
import sg.edu.nus.cats.domain.enums.AuditEventType;
import sg.edu.nus.cats.repository.AuditEventRepository;

/** Writes the append-only timeline inside the caller's business transaction. */
@Service
public class AuditService {

    private final AuditEventRepository events;
    private final Clock clock;

    public AuditService(AuditEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public AuditEvent record(AggregateType aggregateType, String aggregateKey, AuditEventType eventType,
            Employee actor, String fromState, String toState, String reason, String snapshotJson) {
        AuditEvent event = new AuditEvent();
        event.setAggregateType(aggregateType);
        event.setAggregateKey(aggregateKey);
        event.setEventType(eventType);
        event.setActor(actor);
        event.setFromState(fromState);
        event.setToState(toState);
        event.setReason(trim(reason));
        event.setSnapshotJson(trim(snapshotJson));
        event.setCorrelationId(UUID.randomUUID().toString());
        event.setCreatedAt(Instant.now(clock));
        return events.save(event);
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> timeline(AggregateType type, String key) {
        return events.findByAggregateTypeAndAggregateKeyOrderByCreatedAtAscIdAsc(type, key);
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
