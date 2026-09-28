package sg.edu.nus.cats.domain.enums;

/** Session snapshot stored per training day. */
public enum DaySessionCode {
    AM("AM", 1),
    PM("PM", 1),
    BOTH("AM/PM", 2);

    private final String label;
    private final int units;

    DaySessionCode(String label, int units) {
        this.label = label;
        this.units = units;
    }

    public String getLabel() {
        return label;
    }

    public int getUnits() {
        return units;
    }
}
