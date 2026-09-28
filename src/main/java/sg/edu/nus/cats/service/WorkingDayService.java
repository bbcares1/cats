package sg.edu.nus.cats.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import sg.edu.nus.cats.domain.PublicHoliday;
import sg.edu.nus.cats.domain.enums.CategoryCode;
import sg.edu.nus.cats.domain.enums.DaySessionCode;
import sg.edu.nus.cats.domain.enums.SessionCode;
import sg.edu.nus.cats.repository.PublicHolidayRepository;
import sg.edu.nus.cats.support.ExcludedDay;
import sg.edu.nus.cats.support.WorkingDay;

/**
 * Expands a course period into the working days that are actually consumed.
 * Weekends and admin maintained public holidays are excluded, half days are
 * expressed in half day units and complete middle days count as full days.
 */
@Service
public class WorkingDayService {

    private final PublicHolidayRepository holidays;

    public WorkingDayService(PublicHolidayRepository holidays) {
        this.holidays = holidays;
    }

    public Expansion expand(LocalDate start, LocalDate end, SessionCode startSession, SessionCode endSession,
            CategoryCode category) {
        List<WorkingDay> days = new ArrayList<>();
        List<ExcludedDay> excluded = new ArrayList<>();
        Set<LocalDate> holidayDates = holidays.findByHolidayDateBetween(start, end).stream()
                .collect(Collectors.toMap(PublicHoliday::getHolidayDate, PublicHoliday::getName)).keySet();

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (isWeekend(date)) {
                excluded.add(new ExcludedDay(date, "Weekend"));
                continue;
            }
            if (holidayDates.contains(date)) {
                excluded.add(new ExcludedDay(date, "Public holiday"));
                continue;
            }
            days.add(new WorkingDay(date, resolveSession(date, start, end, startSession, endSession), 0));
        }

        List<WorkingDay> withUnits = days.stream()
                .map(day -> new WorkingDay(day.date(), day.sessionCode(), day.sessionCode().getUnits()))
                .toList();
        int units = withUnits.stream().mapToInt(WorkingDay::units).sum();
        return new Expansion(withUnits, excluded, units);
    }

    /**
     * First day: AM means the whole day is consumed, PM means the afternoon only.
     * Last day: PM means the whole day, AM means the morning only. Middle days are
     * full days. A single day course uses the start/end session combination.
     */
    private DaySessionCode resolveSession(LocalDate date, LocalDate start, LocalDate end, SessionCode startSession,
            SessionCode endSession) {
        if (start.equals(end)) {
            if (startSession == SessionCode.AM && endSession == SessionCode.PM) {
                return DaySessionCode.BOTH;
            }
            if (startSession == SessionCode.AM) {
                return DaySessionCode.AM;
            }
            return endSession == SessionCode.PM ? DaySessionCode.PM : DaySessionCode.AM;
        }
        if (date.equals(start)) {
            return startSession == SessionCode.AM ? DaySessionCode.BOTH : DaySessionCode.PM;
        }
        if (date.equals(end)) {
            return endSession == SessionCode.PM ? DaySessionCode.BOTH : DaySessionCode.AM;
        }
        return DaySessionCode.BOTH;
    }

    private boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    /** Half day units split by calendar year, used for cross year courses. */
    public java.util.Map<Integer, Integer> unitsByYear(List<WorkingDay> days) {
        java.util.Map<Integer, Integer> result = new java.util.TreeMap<>();
        days.forEach(day -> result.merge(day.date().getYear(), day.units(), Integer::sum));
        return result;
    }

    public record Expansion(List<WorkingDay> days, List<ExcludedDay> excluded, int totalUnits) {

        public boolean isEmpty() {
            return days.isEmpty();
        }
    }
}
