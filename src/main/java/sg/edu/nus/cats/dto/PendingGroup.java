package sg.edu.nus.cats.dto;

import java.time.LocalDate;
import java.util.List;

/** One employee block on the P06 grouped approval queue. */
public class PendingGroup {

    private final Long employeeId;
    private final String employeeName;
    private final String department;
    private final long pendingCount;
    private final List<CourseApplicationView> rows;
    private final boolean selfApplication;
    private final String selfApplicationNotice;

    public PendingGroup(Long employeeId, String employeeName, String department, long pendingCount,
            List<CourseApplicationView> rows) {
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.department = department;
        this.pendingCount = pendingCount;
        this.rows = rows;
        this.selfApplication = false;
        this.selfApplicationNotice = null;
    }

    public PendingGroup(Long employeeId, String employeeName, String department, long pendingCount,
            List<CourseApplicationView> rows, String selfApplicationNotice) {
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.department = department;
        this.pendingCount = pendingCount;
        this.rows = rows;
        this.selfApplication = selfApplicationNotice != null;
        this.selfApplicationNotice = selfApplicationNotice;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public String getDepartment() {
        return department;
    }

    public long getPendingCount() {
        return pendingCount;
    }

    public List<CourseApplicationView> getRows() {
        return rows;
    }

    public boolean isSelfApplication() {
        return selfApplication;
    }

    public String getSelfApplicationNotice() {
        return selfApplicationNotice;
    }

    /** Flattened row used by list pages so templates stay simple. */
    public record CourseApplicationView(Long id, String referenceNo, String courseTitle, String categoryLabel,
            String providerName, LocalDate startDate, LocalDate endDate, String periodLabel, int units,
            java.math.BigDecimal fee, String statusLabel, String statusCss, String submittedLabel,
            String employeeName, String department, boolean decisionAllowed, long version) {
    }
}
