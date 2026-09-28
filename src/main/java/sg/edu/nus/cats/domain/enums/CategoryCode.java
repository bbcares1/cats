package sg.edu.nus.cats.domain.enums;

import java.math.BigDecimal;

/** The three course categories fixed by the assignment. */
public enum CategoryCode {
    INTERNAL("Internal Training"),
    EXTERNAL("External Training"),
    CERTIFICATION("Certification");

    private final String displayName;

    CategoryCode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Internal training is free; external and certification courses are chargeable. */
    public boolean requiresFee() {
        return this != INTERNAL;
    }

    /** Only internal training may be booked as a half day. */
    public boolean allowsHalfDay() {
        return this == INTERNAL;
    }

    public boolean isReimbursable() {
        return this == EXTERNAL || this == CERTIFICATION;
    }

    /** Fixed provider name used for internal courses. */
    public static final String INTERNAL_PROVIDER = "ISS / In-house";

    public void validateFee(BigDecimal fee) {
        if (fee == null) {
            throw new IllegalArgumentException("Course fee is required");
        }
        if (requiresFee() && fee.signum() <= 0) {
            throw new IllegalArgumentException("A chargeable course requires a fee above zero");
        }
        if (!requiresFee() && fee.signum() != 0) {
            throw new IllegalArgumentException("Internal training is free of charge");
        }
    }
}
