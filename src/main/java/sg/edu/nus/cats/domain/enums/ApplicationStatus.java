package sg.edu.nus.cats.domain.enums;

import java.util.EnumSet;
import java.util.Set;

/** The seven application states defined by the design pack. */
public enum ApplicationStatus {
    APPLIED("Applied", "reserved"),
    UPDATED("Updated", "reserved"),
    APPROVED("Approved", "approved"),
    REJECTED("Rejected", "rejected"),
    CANCELLED("Cancelled", "neutral"),
    COMPLETED("Completed", "completed"),
    DELETED("Deleted", "neutral");

    private static final Set<ApplicationStatus> PENDING = EnumSet.of(APPLIED, UPDATED);
    private static final Set<ApplicationStatus> TERMINAL = EnumSet.of(REJECTED, CANCELLED, COMPLETED, DELETED);
    private static final Set<ApplicationStatus> BUDGET_HOLDING = EnumSet.of(APPLIED, UPDATED, APPROVED, COMPLETED);

    private final String displayName;
    private final String cssClass;

    ApplicationStatus(String displayName, String cssClass) {
        this.displayName = displayName;
        this.cssClass = cssClass;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCssClass() {
        return cssClass;
    }

    /** Awaiting a manager decision, and therefore editable / withdrawable by the applicant. */
    public boolean isPendingReview() {
        return PENDING.contains(this);
    }

    /** No further business transition is possible without a new application. */
    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /** The state reserves or commits training units and budget. */
    public boolean holdsBudget() {
        return BUDGET_HOLDING.contains(this);
    }

    public boolean isReserved() {
        return isPendingReview();
    }

    public boolean isCommitted() {
        return this == APPROVED || this == COMPLETED;
    }

    public boolean isDecision() {
        return this == APPROVED || this == REJECTED;
    }

    /** Statuses that count as an attended course in the training report. */
    public static Set<ApplicationStatus> reportedStatuses() {
        return EnumSet.of(APPROVED, COMPLETED);
    }

    /** Statuses that still hold training days and budget. */
    public static Set<ApplicationStatus> budgetHoldingStatuses() {
        return EnumSet.copyOf(BUDGET_HOLDING);
    }

    /** Statuses that still wait for a manager decision. */
    public static Set<ApplicationStatus> pendingStatuses() {
        return EnumSet.copyOf(PENDING);
    }
}
