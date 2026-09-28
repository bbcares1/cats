package sg.edu.nus.cats.domain;

import java.math.BigDecimal;
import java.time.Instant;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import sg.edu.nus.cats.domain.enums.LedgerEntryType;

/**
 * Immutable net change entry. Balances are always the sum of these rows, which is
 * what makes the numbers explainable on screen.
 */
@Entity
@Table(name = "training_ledger", uniqueConstraints = @UniqueConstraint(name = "uk_ledger_event_account",
        columnNames = { "event_id", "account_id" }))
public class TrainingLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private TrainingAccount account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private AuditEvent event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private CourseApplication application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id")
    private CourseClaim claim;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 40)
    private LedgerEntryType entryType;

    @Column(name = "reserved_units_delta", nullable = false)
    private int reservedUnitsDelta;

    @Column(name = "committed_units_delta", nullable = false)
    private int committedUnitsDelta;

    @Column(name = "reserved_amount_delta", nullable = false, precision = 12, scale = 2)
    private BigDecimal reservedAmountDelta = BigDecimal.ZERO;

    @Column(name = "committed_amount_delta", nullable = false, precision = 12, scale = 2)
    private BigDecimal committedAmountDelta = BigDecimal.ZERO;

    @Column(name = "reimbursed_amount_delta", nullable = false, precision = 12, scale = 2)
    private BigDecimal reimbursedAmountDelta = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TrainingAccount getAccount() {
        return account;
    }

    public void setAccount(TrainingAccount account) {
        this.account = account;
    }

    public AuditEvent getEvent() {
        return event;
    }

    public void setEvent(AuditEvent event) {
        this.event = event;
    }

    public CourseApplication getApplication() {
        return application;
    }

    public void setApplication(CourseApplication application) {
        this.application = application;
    }

    public CourseClaim getClaim() {
        return claim;
    }

    public void setClaim(CourseClaim claim) {
        this.claim = claim;
    }

    public LedgerEntryType getEntryType() {
        return entryType;
    }

    public void setEntryType(LedgerEntryType entryType) {
        this.entryType = entryType;
    }

    public int getReservedUnitsDelta() {
        return reservedUnitsDelta;
    }

    public void setReservedUnitsDelta(int reservedUnitsDelta) {
        this.reservedUnitsDelta = reservedUnitsDelta;
    }

    public int getCommittedUnitsDelta() {
        return committedUnitsDelta;
    }

    public void setCommittedUnitsDelta(int committedUnitsDelta) {
        this.committedUnitsDelta = committedUnitsDelta;
    }

    public BigDecimal getReservedAmountDelta() {
        return reservedAmountDelta;
    }

    public void setReservedAmountDelta(BigDecimal reservedAmountDelta) {
        this.reservedAmountDelta = reservedAmountDelta;
    }

    public BigDecimal getCommittedAmountDelta() {
        return committedAmountDelta;
    }

    public void setCommittedAmountDelta(BigDecimal committedAmountDelta) {
        this.committedAmountDelta = committedAmountDelta;
    }

    public BigDecimal getReimbursedAmountDelta() {
        return reimbursedAmountDelta;
    }

    public void setReimbursedAmountDelta(BigDecimal reimbursedAmountDelta) {
        this.reimbursedAmountDelta = reimbursedAmountDelta;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
