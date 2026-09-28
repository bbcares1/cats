package sg.edu.nus.cats.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.edu.nus.cats.domain.TrainingAccount;

public interface TrainingAccountRepository extends JpaRepository<TrainingAccount, Long> {

    Optional<TrainingAccount> findByEmployeeIdAndCalendarYear(Long employeeId, int calendarYear);

    List<TrainingAccount> findByCalendarYearOrderByEmployeeFullNameAsc(int calendarYear);

    List<TrainingAccount> findByEmployeeIdOrderByCalendarYearDesc(Long employeeId);

    boolean existsByEmployeeIdAndCalendarYear(Long employeeId, int calendarYear);
}
