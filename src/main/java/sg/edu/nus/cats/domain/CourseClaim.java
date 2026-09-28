package sg.edu.nus.cats.domain;

import java.math.BigDecimal;
import java.time.Instant;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import sg.edu.nus.cats.domain.enums.ClaimStatus;

/**
 * One fee claim per completed application. A rejected claim is corrected and
 * resubmitted by bumping the revision; a second claim row is never created.
 */
@Entity
@Table(name = "course_claim")
public class CourseClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    private CourseApplication application;

    @Column(name = "revision", nullable = false)
    private int revision = 1;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_by_employee", nullable = false)
    private boolean paidByEmployee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approver_id", nullable = false)
    private Employee approver;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ClaimStatus status = ClaimStatus.SUBMITTED;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private Employee reviewedBy;

    @Column(name = "review_comment", length = 2000)
    private String reviewComment;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reimbursed_by")
    private Employee reimbursedBy;

    @Column(name = "reimbursed_at")
    private Instant reimbursedAt;

    @Column(name = "reimbursement_reference", length = 80)
    private String reimbursementReference;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ClaimDocument> documents = new ArrayList<>();

    public void addDocument(ClaimDocument document) {
        document.setClaim(this);
        documents.add(document);
    }

    /** Replaces the documents of the current revision (used when resubmitting). */
    public void replaceDocuments(List<ClaimDocument> newDocuments) {
        documents.clear();
        newDocuments.forEach(this::addDocument);
    }

    public void submitRevision(BigDecimal newAmount, boolean paid, Employee newApprover, Instant now) {
        this.amount = newAmount;
        this.paidByEmployee = paid;
        this.approver = newApprover;
        this.status = ClaimStatus.SUBMITTED;
        this.submittedAt = now;
        this.reviewedBy = null;
        this.reviewedAt = null;
        this.reviewComment = null;
    }

    public void bumpRevision() {
        this.revision = this.revision + 1;
    }

    public void decide(boolean approved, Employee decidedBy, String reason, Instant now) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required for every claim decision");
        }
        this.status = approved ? ClaimStatus.APPROVED : ClaimStatus.REJECTED;
        this.reviewedBy = decidedBy;
        this.reviewedAt = now;
        this.reviewComment = reason.trim();
    }

    public void markReimbursed(Employee admin, String reference, Instant now) {
        this.status = ClaimStatus.REIMBURSED;
        this.reimbursedBy = admin;
        this.reimbursementReference = reference;
        this.reimbursedAt = now;
    }

    public List<ClaimDocument> getDocumentsForCurrentRevision() {
        return documents.stream().filter(doc -> doc.getClaimRevision() == revision).toList();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CourseApplication getApplication() {
        return application;
    }

    public void setApplication(CourseApplication application) {
        this.application = application;
    }

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
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

    public Employee getApprover() {
        return approver;
    }

    public void setApprover(Employee approver) {
        this.approver = approver;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public void setStatus(ClaimStatus status) {
        this.status = status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Employee getReviewedBy() {
        return reviewedBy;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Employee getReimbursedBy() {
        return reimbursedBy;
    }

    public Instant getReimbursedAt() {
        return reimbursedAt;
    }

    public String getReimbursementReference() {
        return reimbursementReference;
    }

    public long getVersion() {
        return version;
    }

    public List<ClaimDocument> getDocuments() {
        return documents;
    }
}
