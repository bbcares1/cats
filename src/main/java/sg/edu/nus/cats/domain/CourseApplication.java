package sg.edu.nus.cats.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import sg.edu.nus.cats.domain.enums.ApplicationStatus;
import sg.edu.nus.cats.domain.enums.CategoryCode;
import sg.edu.nus.cats.domain.enums.SessionCode;

/**
 * One employee course application and its current state. History is kept in
 * audit events, so state changes only happen through the domain methods below.
 */
@Entity
@Table(name = "course_application", uniqueConstraints = @UniqueConstraint(name = "uk_application_request",
        columnNames = { "employee_id", "client_request_id" }))
public class CourseApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "reference_no", nullable = false, unique = true, length = 30)
    private String referenceNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approver_id", nullable = false)
    private Employee approver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalogue_id")
    private CourseCatalogue catalogue;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_code", nullable = false, length = 20)
    private CategoryCode categoryCode;

    @Column(name = "course_title", nullable = false, length = 200)
    private String courseTitle;

    @Column(name = "provider_name", nullable = false, length = 160)
    private String providerName;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "start_session", nullable = false, length = 2)
    private SessionCode startSession;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_session", nullable = false, length = 2)
    private SessionCode endSession;

    @Column(name = "course_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal courseFee;

    @Column(name = "justification", nullable = false, length = 2000)
    private String justification;

    @Column(name = "work_dissemination", length = 2000)
    private String workDissemination;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.APPLIED;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private Employee reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_comment", length = 2000)
    private String reviewComment;

    @Column(name = "completion_comment", length = 2000)
    private String completionComment;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancel_reason", length = 2000)
    private String cancelReason;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "client_request_id", nullable = false, length = 36)
    private String clientRequestId;

    @Column(name = "create_request_hash", nullable = false, length = 64)
    private String createRequestHash;

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id.trainingDate ASC")
    private List<ApplicationDay> days = new ArrayList<>();

    public void replaceDays(List<ApplicationDay> newDays) {
        days.clear();
        newDays.forEach(this::addDay);
    }

    public void addDay(ApplicationDay day) {
        day.setApplication(this);
        day.getId().setApplicationId(id);
        days.add(day);
    }

    public int getReservedUnits() {
        return days.stream().mapToInt(ApplicationDay::getUnits).sum();
    }

    /** Applies an approval decision. A reason is mandatory for both outcomes. */
    public void decide(boolean approved, Employee decidedBy, String reason, Instant now) {
        requireText(reason, "A reason is required for every approval decision");
        this.status = approved ? ApplicationStatus.APPROVED : ApplicationStatus.REJECTED;
        this.reviewedBy = decidedBy;
        this.reviewedAt = now;
        this.reviewComment = reason.trim();
        this.updatedAt = now;
    }

    public void markUpdated(Instant now) {
        this.status = ApplicationStatus.UPDATED;
        this.updatedAt = now;
    }

    public void markSubmitted(Instant now) {
        this.status = ApplicationStatus.APPLIED;
        this.updatedAt = now;
        this.submittedAt = now;
        this.reviewedBy = null;
        this.reviewedAt = null;
        this.reviewComment = null;
    }

    public void cancel(String reason, Instant now) {
        requireText(reason, "A cancellation reason is required");
        this.status = ApplicationStatus.CANCELLED;
        this.cancelReason = reason.trim();
        this.updatedAt = now;
    }

    public void complete(String experience, Instant now) {
        requireText(experience, "Experience sharing is required before completing a course");
        this.status = ApplicationStatus.COMPLETED;
        this.completionComment = experience.trim();
        this.completedAt = now;
        this.updatedAt = now;
    }

    public void markDeleted(Instant now) {
        this.status = ApplicationStatus.DELETED;
        this.updatedAt = now;
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    /** Start and end must describe a legal day/session range. */
    public void validateSchedule() {
        if (startDate == null || endDate == null || startSession == null || endSession == null) {
            throw new IllegalArgumentException("Course start and end date with sessions are required");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("The start date must not be after the end date");
        }
        if (startDate.equals(endDate) && startSession == SessionCode.PM && endSession == SessionCode.AM) {
            throw new IllegalArgumentException("A same day course cannot start in the afternoon and end in the morning");
        }
        if (!categoryCode.allowsHalfDay() && !(startSession == SessionCode.AM && endSession == SessionCode.PM)) {
            throw new IllegalArgumentException("External and certification courses must run a full day (AM to PM)");
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReferenceNo() {
        return referenceNo;
    }

    public void setReferenceNo(String referenceNo) {
        this.referenceNo = referenceNo;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public Employee getApprover() {
        return approver;
    }

    public void setApprover(Employee approver) {
        this.approver = approver;
    }

    public CourseCatalogue getCatalogue() {
        return catalogue;
    }

    public void setCatalogue(CourseCatalogue catalogue) {
        this.catalogue = catalogue;
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

    public ApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(ApplicationStatus status) {
        this.status = status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Employee getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public String getCompletionComment() {
        return completionComment;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public long getVersion() {
        return version;
    }

    public String getClientRequestId() {
        return clientRequestId;
    }

    public void setClientRequestId(String clientRequestId) {
        this.clientRequestId = clientRequestId;
    }

    public String getCreateRequestHash() {
        return createRequestHash;
    }

    public void setCreateRequestHash(String createRequestHash) {
        this.createRequestHash = createRequestHash;
    }

    public List<ApplicationDay> getDays() {
        return days;
    }

    public int getCourseDays() {
        int units = getReservedUnits();
        return units / 2 + (units % 2 == 0 ? 0 : 1);
    }
}
