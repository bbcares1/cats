package sg.edu.nus.cats.support;

import java.time.LocalDate;

import sg.edu.nus.cats.domain.enums.DaySessionCode;

/** One expanded training day, ready to be stored in application_day. */
public record WorkingDay(LocalDate date, DaySessionCode sessionCode, int units) {
}
