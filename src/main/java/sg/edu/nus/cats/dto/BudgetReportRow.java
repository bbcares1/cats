package sg.edu.nus.cats.dto;

import java.math.BigDecimal;

public record BudgetReportRow(String employeeName, String department, int calendarYear, int entitledUnits,
        int reservedUnits, int committedUnits, int availableUnits, BigDecimal budgetAmount, BigDecimal reservedAmount,
        BigDecimal committedAmount, BigDecimal reimbursedAmount, BigDecimal availableAmount, String utilisationLabel) {
}
