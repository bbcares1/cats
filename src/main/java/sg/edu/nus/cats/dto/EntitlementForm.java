package sg.edu.nus.cats.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class EntitlementForm {

    @NotNull(message = "Select an employee")
    private Long employeeId;

    @NotNull(message = "Enter the entitlement year")
    @Min(value = 2000, message = "Enter a valid year")
    @Max(value = 2100, message = "Enter a valid year")
    private Integer calendarYear;

    @NotNull(message = "Enter the entitled training days")
    @Min(value = 0, message = "Entitled days cannot be negative")
    @Max(value = 366, message = "Entitled days cannot exceed 366")
    private Integer entitledUnits;

    @NotNull(message = "Enter the training budget")
    @DecimalMin(value = "0.00", message = "The budget cannot be negative")
    private BigDecimal budgetAmount;

    private Long version;

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public Integer getCalendarYear() {
        return calendarYear;
    }

    public void setCalendarYear(Integer calendarYear) {
        this.calendarYear = calendarYear;
    }

    public Integer getEntitledUnits() {
        return entitledUnits;
    }

    public void setEntitledUnits(Integer entitledUnits) {
        this.entitledUnits = entitledUnits;
    }

    public BigDecimal getBudgetAmount() {
        return budgetAmount;
    }

    public void setBudgetAmount(BigDecimal budgetAmount) {
        this.budgetAmount = budgetAmount;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
