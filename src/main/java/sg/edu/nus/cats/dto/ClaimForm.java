package sg.edu.nus.cats.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public class ClaimForm {

    @NotNull(message = "Select the approved course application")
    private Long applicationId;

    @NotNull(message = "Enter the claimed amount")
    @DecimalMin(value = "0.01", message = "The claimed amount must be above zero")
    private BigDecimal amount;

    private boolean paidByEmployee = true;

    private Long approverId;

    private String clientRequestId;

    private Long version;

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public boolean isPaidByEmployee() {
        return paidByEmployee;
    }

    public void setPaidByEmployee(boolean paidByEmployee) {
        this.paidByEmployee = paidByEmployee;
    }

    public Long getApproverId() {
        return approverId;
    }

    public void setApproverId(Long approverId) {
        this.approverId = approverId;
    }

    public String getClientRequestId() {
        return clientRequestId;
    }

    public void setClientRequestId(String clientRequestId) {
        this.clientRequestId = clientRequestId;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
