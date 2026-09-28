package sg.edu.nus.cats.domain.enums;

/** Half-day marker used for the start and end of a course. */
public enum SessionCode {
    AM("AM"),
    PM("PM");

    private final String label;

    SessionCode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
