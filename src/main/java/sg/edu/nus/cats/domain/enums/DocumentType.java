package sg.edu.nus.cats.domain.enums;

public enum DocumentType {
    RECEIPT("Receipt"),
    COMPLETION_CERTIFICATE("Completion certificate");

    private final String displayName;

    DocumentType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
