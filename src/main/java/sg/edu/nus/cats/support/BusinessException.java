package sg.edu.nus.cats.support;

/** Expected business rule violation, mapped to a specific HTTP status. */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode code;

    public BusinessException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public static BusinessException notFound(String what) {
        return new BusinessException(ErrorCode.NOT_FOUND, what + " was not found");
    }

    public static BusinessException accessDenied(String message) {
        return new BusinessException(ErrorCode.ACCESS_DENIED, message);
    }

    public ErrorCode getCode() {
        return code;
    }
}
