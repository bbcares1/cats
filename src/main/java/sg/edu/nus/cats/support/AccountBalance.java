package sg.edu.nus.cats.support;

import java.math.BigDecimal;

import sg.edu.nus.cats.domain.TrainingAccount;

/** Explainable balance view: entitlement minus the ledger totals. */
public record AccountBalance(TrainingAccount account, LedgerTotals totals) {

    public int entitledUnits() {
        return account.getEntitledUnits();
    }

    public int reservedUnits() {
        return totals.reservedUnits();
    }

    public int committedUnits() {
        return totals.committedUnits();
    }

    public int availableUnits() {
        return entitledUnits() - totals.usedUnits();
    }

    public BigDecimal budgetAmount() {
        return account.getBudgetAmount();
    }

    public BigDecimal reservedAmount() {
        return totals.reservedAmount();
    }

    public BigDecimal committedAmount() {
        return totals.committedAmount();
    }

    public BigDecimal reimbursedAmount() {
        return totals.reimbursedAmount();
    }

    public BigDecimal availableAmount() {
        return budgetAmount().subtract(totals.usedAmount());
    }

    /** Half day units already held by pending or committed applications. */
    public int usedUnits() {
        return reservedUnits() + committedUnits();
    }

    /** Amount already reserved or committed against the budget. */
    public BigDecimal usedAmount() {
        return reservedAmount().add(committedAmount());
    }

    public boolean hasUnitsFor(int units) {
        return availableUnits() >= units;
    }

    public boolean hasBudgetFor(BigDecimal amount) {
        return availableAmount().compareTo(amount) >= 0;
    }

    public String getAvailableDaysLabel() {
        return formatDays(Math.max(availableUnits(), 0));
    }

    public static String formatDays(int units) {
        if (units % 2 == 0) {
            return (units / 2) + " day(s)";
        }
        return (units / 2) + ".5 day(s)";
    }
}
