package sg.edu.nus.cats.support;

import java.math.BigDecimal;

/** Sum of ledger entries for one annual account. */
public record LedgerTotals(int reservedUnits, int committedUnits, BigDecimal reservedAmount,
        BigDecimal committedAmount, BigDecimal reimbursedAmount) {

    public static LedgerTotals empty() {
        return new LedgerTotals(0, 0, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2));
    }

    public int usedUnits() {
        return reservedUnits + committedUnits;
    }

    public BigDecimal usedAmount() {
        return reservedAmount.add(committedAmount);
    }
}
