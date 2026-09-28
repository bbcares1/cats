package sg.edu.nus.cats.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import sg.edu.nus.cats.domain.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByStaffNoIgnoreCase(String staffNo);

    Optional<Employee> findByEmailIgnoreCase(String email);

    List<Employee> findByActiveTrueOrderByFullNameAsc();

    @Query("""
            select e from Employee e
            where (:query is null or lower(e.fullName) like lower(concat('%', :query, '%'))
                   or lower(e.staffNo) like lower(concat('%', :query, '%')))
              and (:activeOnly = false or e.active = true)
            order by e.fullName asc, e.id asc
            """)
    Page<Employee> search(@Param("query") String query, @Param("activeOnly") boolean activeOnly, Pageable pageable);

    @Query("""
            select e from Employee e
            where e.id in (select a.employeeId from ApprovalAssignment a where a.manager.id = :managerId)
            order by e.fullName asc, e.id asc
            """)
    List<Employee> findTeamMembers(@Param("managerId") Long managerId);

    boolean existsByStaffNoIgnoreCase(String staffNo);

    long countByActiveTrue();
}
