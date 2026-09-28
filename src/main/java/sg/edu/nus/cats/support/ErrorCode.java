package sg.edu.nus.cats.support;

/** Business error codes. Every rule violation has a code and a correctable message. */
public enum ErrorCode {
    NOT_FOUND(404),
    ACCESS_DENIED(403),
    STALE_VERSION(409),
    DUPLICATE_REQUEST(409),
    INVALID_REQUEST(400),
    INVALID_STATE(409),
    NO_APPROVER(409),
    ACCOUNT_NOT_CONFIGURED(409),
    INSUFFICIENT_UNITS(409),
    INSUFFICIENT_BUDGET(409),
    OVERLAPPING_APPLICATION(409),
    NO_WORKING_DAY(409),
    COURSE_NOT_ENDED(409),
    CLAIM_NOT_ELIGIBLE(409),
    CLAIM_ALREADY_EXISTS(409),
    DOCUMENT_MISSING(400),
    FILE_TOO_LARGE(400),
    ROUTING_INVALID(409),
    DUPLICATE_STAFF_NO(409),
    DUPLICATE_EMAIL(409),
    DUPLICATE_USERNAME(409),
    DUPLICATE_CODE(409),
    ENTITLEMENT_BELOW_USAGE(409),
    ACCOUNT_IN_USE(409);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
