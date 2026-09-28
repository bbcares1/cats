package sg.edu.nus.cats.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Used by both the application decision and the claim decision forms. */
public class DecisionForm {

    @NotNull(message = "Choose approve or reject")
    private Boolean approved;

    @NotBlank(message = "A reason is required for every decision")
    @Size(max = 500, message = "The reason must be 500 characters or fewer")
    private String reason;

    private Long version;

    private String decisionScope = "single";

    public boolean isApproved() {
        return Boolean.TRUE.equals(approved);
    }

    public Boolean getApproved() {
        return approved;
    }

    public void setApproved(Boolean approved) {
        this.approved = approved;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getDecisionScope() {
        return decisionScope;
    }

    public void setDecisionScope(String decisionScope) {
        this.decisionScope = decisionScope;
    }
}
