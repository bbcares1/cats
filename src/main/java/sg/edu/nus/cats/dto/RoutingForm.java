package sg.edu.nus.cats.dto;

import jakarta.validation.constraints.NotNull;

public class RoutingForm {

    @NotNull(message = "Select an employee")
    private Long employeeId;

    @NotNull(message = "Select the approving manager")
    private Long managerId;

    private Long version;

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public Long getManagerId() {
        return managerId;
    }

    public void setManagerId(Long managerId) {
        this.managerId = managerId;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
