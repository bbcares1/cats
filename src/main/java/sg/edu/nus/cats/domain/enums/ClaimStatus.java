package sg.edu.nus.cats.domain.enums;

import java.util.EnumSet;
import java.util.Set;

public enum ClaimStatus {
    SUBMITTED("Submitted", "reserved"),
    APPROVED("Approved", "approved"),
    REJECTED("Rejected", "rejected"),
    REIMBURSED("Reimbursed", "completed");

    private final String displayName;
    private final String cssClass;

    ClaimStatus(String displayName, String cssClass) {
        this.displayName = displayName;
        this.cssClass = cssClass;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCssClass() {
        return cssClass;
    }

    public boolean isPendingReview() {
        return this == SUBMITTED;
    }

    /** A rejected revision may be corrected and resubmitted. */
    public boolean isRevisable() {
        return this == REJECTED;
    }

    public static Set<ClaimStatus> openStatuses() {
        return EnumSet.of(SUBMITTED, APPROVED);
    }
}
