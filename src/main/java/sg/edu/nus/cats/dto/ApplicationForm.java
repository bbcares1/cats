package sg.edu.nus.cats.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import sg.edu.nus.cats.domain.enums.CategoryCode;
import sg.edu.nus.cats.domain.enums.SessionCode;

/** Bound from the P03 create/update form and from the preview REST call. */
public class ApplicationForm {

    private Long catalogueId;

    @NotNull(message = "Select a training category")
    private CategoryCode categoryCode;

    @Size(max = 150, message = "Course title must be 150 characters or fewer")
    private String courseTitle;

    @Size(max = 120, message = "Provider name must be 120 characters or fewer")
    private String providerName;

    @NotNull(message = "Enter the course start date")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @NotNull(message = "Enter the course end date")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    @NotNull(message = "Select the start session")
    private SessionCode startSession;

    @NotNull(message = "Select the end session")
    private SessionCode endSession;

    @NotNull(message = "Enter the course fee (0 for in-house training)")
    @DecimalMin(value = "0.00", message = "The course fee cannot be negative")
    private BigDecimal courseFee = BigDecimal.ZERO;

    @Size(max = 1000, message = "Justification must be 1000 characters or fewer")
    private String justification;

    @Size(max = 1000, message = "Work dissemination must be 1000 characters or fewer")
    private String workDissemination;

    private String clientRequestId;

    private Long version;

    public String cacheKey(Long employeeId) {
        return String.join("|",
                String.valueOf(employeeId),
                categoryCode == null ? "" : categoryCode.name(),
                blank(courseTitle),
                blank(providerName),
                String.valueOf(startDate),
                String.valueOf(endDate),
                startSession == null ? "" : startSession.name(),
                endSession == null ? "" : endSession.name(),
                courseFee == null ? "0" : courseFee.stripTrailingZeros().toPlainString(),
                blank(justification),
                blank(workDissemination));
    }

    private static String blank(String value) {
        return value == null ? "" : value.trim();
    }

    public Long getCatalogueId() {
        return catalogueId;
    }

    public void setCatalogueId(Long catalogueId) {
        this.catalogueId = catalogueId;
    }

    public CategoryCode getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(CategoryCode categoryCode) {
        this.categoryCode = categoryCode;
    }

    public String getCourseTitle() {
        return courseTitle;
    }

    public void setCourseTitle(String courseTitle) {
        this.courseTitle = courseTitle;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public SessionCode getStartSession() {
        return startSession;
    }

    public void setStartSession(SessionCode startSession) {
        this.startSession = startSession;
    }

    public SessionCode getEndSession() {
        return endSession;
    }

    public void setEndSession(SessionCode endSession) {
        this.endSession = endSession;
    }

    public BigDecimal getCourseFee() {
        return courseFee;
    }

    public void setCourseFee(BigDecimal courseFee) {
        this.courseFee = courseFee;
    }

    public String getJustification() {
        return justification;
    }

    public void setJustification(String justification) {
        this.justification = justification;
    }

    public String getWorkDissemination() {
        return workDissemination;
    }

    public void setWorkDissemination(String workDissemination) {
        this.workDissemination = workDissemination;
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
