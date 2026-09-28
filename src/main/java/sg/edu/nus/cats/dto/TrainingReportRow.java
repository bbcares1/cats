package sg.edu.nus.cats.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TrainingReportRow(String employeeName, String department, String referenceNo, String courseTitle,
        String categoryLabel, String providerName, LocalDate startDate, LocalDate endDate, String periodLabel,
        int units, String statusLabel, BigDecimal fee) {
}
