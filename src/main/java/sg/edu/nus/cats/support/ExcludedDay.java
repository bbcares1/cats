package sg.edu.nus.cats.support;

import java.time.LocalDate;

/** A calendar date that was skipped, with the human readable reason. */
public record ExcludedDay(LocalDate date, String reason) {
}
