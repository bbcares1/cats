package sg.edu.nus.cats.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ReimburseForm {

    @NotBlank(message = "Enter the reimbursement reference")
    @Size(max = 60, message = "The reference must be 60 characters or fewer")
    private String reference;

    private Long version;

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
