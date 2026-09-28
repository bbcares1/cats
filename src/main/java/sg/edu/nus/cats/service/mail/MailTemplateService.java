package sg.edu.nus.cats.service.mail;

import java.util.Map;

import org.springframework.stereotype.Service;

import sg.edu.nus.cats.config.MailProperties;
import sg.edu.nus.cats.domain.EmailOutbox;
import sg.edu.nus.cats.domain.enums.OutboxTemplateCode;
import sg.edu.nus.cats.support.Json;

/** Renders the plain text notification. Links are always relative to the app. */
@Service
public class MailTemplateService {

    private final MailProperties properties;

    public MailTemplateService(MailProperties properties) {
        this.properties = properties;
    }

    public String subject(EmailOutbox row) {
        String reference = string(row, "reference");
        return switch (row.getTemplateCode()) {
            case APPLICATION_SUBMITTED -> "New training application " + reference + " awaits your decision";
            case APPLICATION_APPROVED -> "Training application " + reference + " approved";
            case APPLICATION_REJECTED -> "Training application " + reference + " rejected";
            case CLAIM_SUBMITTED -> "Fee claim " + reference + " awaits your decision";
            case CLAIM_APPROVED -> "Fee claim " + reference + " approved";
            case CLAIM_REJECTED -> "Fee claim " + reference + " rejected";
            case REIMBURSEMENT_RECORDED -> "Reimbursement recorded for " + reference;
        };
    }

    public String body(EmailOutbox row) {
        Map<String, Object> payload = Json.readMap(row.getPayloadJson());
        String link = properties.getBaseUrl() + path(row.getTemplateCode(), payload);
        StringBuilder builder = new StringBuilder();
        builder.append("Dear ").append(payload.getOrDefault("recipientName", row.getRecipient().getFullName()))
                .append(",\n\n");
        builder.append(payload.getOrDefault("summary", "")).append('\n');
        Object reason = payload.get("reason");
        if (reason != null && !String.valueOf(reason).isBlank()) {
            builder.append("\nReason: ").append(reason).append('\n');
        }
        builder.append("\nOpen the record (sign in required): ").append(link).append('\n');
        return builder.toString();
    }

    private String path(OutboxTemplateCode templateCode, Map<String, Object> payload) {
        Object id = payload.get("applicationId");
        Object claimId = payload.get("claimId");
        return switch (templateCode) {
            case APPLICATION_SUBMITTED -> "/manager/applications/" + id;
            case APPLICATION_APPROVED, APPLICATION_REJECTED -> "/employee/applications/" + id;
            case CLAIM_SUBMITTED -> "/manager/claims/" + claimId;
            case CLAIM_APPROVED, CLAIM_REJECTED, REIMBURSEMENT_RECORDED -> "/employee/claims/" + claimId;
        };
    }

    private String string(EmailOutbox row, String key) {
        Object value = Json.readMap(row.getPayloadJson()).get(key);
        return value == null ? String.valueOf(row.getId()) : String.valueOf(value);
    }
}
