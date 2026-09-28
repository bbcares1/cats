package sg.edu.nus.cats.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import sg.edu.nus.cats.support.AccountBalance;
import sg.edu.nus.cats.support.ExcludedDay;

/** Read-only result of the pre-save check. Nothing is written when this runs. */
public class PreviewResult {

    private final List<String> errors;
    private final List<ExcludedDay> excludedDays;
    private final List<ScheduleRow> schedule;
    private final Map<Integer, Integer> unitsByYear;
    private final List<YearQuota> quotas;
    private final List<Conflict> conflicts;
    private final List<String> notices;
    private final int totalUnits;
    private final BigDecimal totalFee;

    public PreviewResult(List<String> errors, List<ExcludedDay> excludedDays, List<ScheduleRow> schedule,
            Map<Integer, Integer> unitsByYear, List<YearQuota> quotas, List<Conflict> conflicts,
            List<String> notices, int totalUnits, BigDecimal totalFee) {
        this.errors = errors;
        this.excludedDays = excludedDays;
        this.schedule = schedule;
        this.unitsByYear = unitsByYear;
        this.quotas = quotas;
        this.conflicts = conflicts;
        this.notices = notices;
        this.totalUnits = totalUnits;
        this.totalFee = totalFee;
    }

    public boolean isEligible() {
        return errors.isEmpty();
    }

    public List<String> getErrors() {
        return errors;
    }

    public List<ExcludedDay> getExcludedDays() {
        return excludedDays;
    }

    public List<ScheduleRow> getSchedule() {
        return schedule;
    }

    public Map<Integer, Integer> getUnitsByYear() {
        return unitsByYear;
    }

    public List<YearQuota> getQuotas() {
        return quotas;
    }

    public List<Conflict> getConflicts() {
        return conflicts;
    }

    public List<String> getNotices() {
        return notices;
    }

    public int getTotalUnits() {
        return totalUnits;
    }

    public BigDecimal getTotalFee() {
        return totalFee;
    }

    public String getTotalDaysLabel() {
        return AccountBalance.formatDays(totalUnits);
    }

    /** One generated training day shown in the preview table. */
    public record ScheduleRow(LocalDate date, String dayOfWeek, String sessionLabel, int units) {
    }

    /** Per calendar year quota check. */
    public record YearQuota(int year, int requestedUnits, int entitledUnits, int usedUnits, int availableUnits,
            BigDecimal requestedAmount, BigDecimal budgetAmount, BigDecimal usedAmount, BigDecimal availableAmount,
            boolean sufficientUnits, boolean sufficientBudget, String accountStatus) {
    }

    /** A clash the employee or manager should know about before saving. */
    public record Conflict(String kind, String label, String detail) {
    }
}
