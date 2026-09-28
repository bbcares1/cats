package sg.edu.nus.cats.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.edu.nus.cats.domain.PublicHoliday;

public interface PublicHolidayRepository extends JpaRepository<PublicHoliday, LocalDate> {

    List<PublicHoliday> findByHolidayDateBetweenOrderByHolidayDateAsc(LocalDate from, LocalDate to);

    List<PublicHoliday> findByHolidayDateBetween(LocalDate from, LocalDate to);

    long countByHolidayDateBetween(LocalDate from, LocalDate to);
}
