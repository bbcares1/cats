package sg.edu.nus.cats.domain.enums;

/** Append-only ledger entry kinds. Corrections use compensating entries. */
public enum LedgerEntryType {
    RESERVE,
    UPDATE_RESERVATION,
    COMMIT,
    RELEASE,
    REIMBURSE
}
