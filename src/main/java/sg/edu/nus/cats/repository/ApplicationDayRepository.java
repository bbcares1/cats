package sg.edu.nus.cats.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import sg.edu.nus.cats.domain.ApplicationDay;
import sg.edu.nus.cats.domain.ApplicationDayId;

public interface ApplicationDayRepository extends JpaRepository<ApplicationDay, ApplicationDayId> {

    @Query("""
            select d from ApplicationDay d
            join fetch d.application a
            where a.employee.id = :employeeId
              and a.status in :statuses
              and d.id.trainingDate between :from and :to
            order by d.id.trainingDate asc
            """)
    List<ApplicationDay> findForEmployeeBetween(@Param("employeeId") Long employeeId,
            @Param("from") LocalDate from, @Param("to") LocalDate to,
            @Param("statuses") java.util.Collection<sg.edu.nus.cats.domain.enums.ApplicationStatus> statuses);

    @Query("select coalesce(sum(d.units), 0) from ApplicationDay d where d.application.employee.id = :employeeId and d.application.status in :statuses")
    Long sumUnitsForEmployee(@Param("employeeId") Long employeeId,
            @Param("statuses") java.util.Collection<sg.edu.nus.cats.domain.enums.ApplicationStatus> statuses);
}
