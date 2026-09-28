package sg.edu.nus.cats.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import sg.edu.nus.cats.domain.CourseApplication;
import sg.edu.nus.cats.domain.enums.ApplicationStatus;
import sg.edu.nus.cats.domain.enums.CategoryCode;

public interface CourseApplicationRepository extends JpaRepository<CourseApplication, Long> {

    Optional<CourseApplication> findByEmployeeIdAndClientRequestId(Long employeeId, String clientRequestId);

    @EntityGraph(attributePaths = { "employee", "approver", "reviewedBy", "days" })
    Optional<CourseApplication> findWithDetailsById(Long id);

    @Query("""
            select a from CourseApplication a
            join fetch a.employee e
            where a.id = :id
            """)
    Optional<CourseApplication> findWithEmployeeById(@Param("id") Long id);

    @Query("""
            select distinct a from CourseApplication a
            join fetch a.employee e
            left join fetch a.approver
            where e.id = :employeeId
              and (:from is null or a.endDate >= :from)
              and (:to is null or a.startDate <= :to)
              and (:category is null or a.categoryCode = :category)
              and (:statuses is null or a.status in :statuses)
            """)
    List<CourseApplication> findForEmployeeHistory(@Param("employeeId") Long employeeId,
            @Param("from") LocalDate from, @Param("to") LocalDate to,
            @Param("category") CategoryCode category,
            @Param("statuses") Collection<ApplicationStatus> statuses);

    @Query("""
            select a from CourseApplication a
            join fetch a.employee e
            where e.id in :employeeIds
              and (:from is null or a.endDate >= :from)
              and (:to is null or a.startDate <= :to)
              and (:category is null or a.categoryCode = :category)
              and a.status in :statuses
            order by e.fullName asc, a.startDate desc, a.id desc
            """)
    List<CourseApplication> findForTeamReport(@Param("employeeIds") Collection<Long> employeeIds,
            @Param("from") LocalDate from, @Param("to") LocalDate to,
            @Param("category") CategoryCode category,
            @Param("statuses") Collection<ApplicationStatus> statuses);

    @Query("""
            select a.employee.id, a.employee.fullName, a.employee.department, count(a)
            from CourseApplication a
            where a.approver.id = :approverId and a.status in :statuses
            group by a.employee.id, a.employee.fullName, a.employee.department
            order by a.employee.fullName asc, a.employee.id asc
            """)
    Page<Object[]> findPendingGroups(@Param("approverId") Long approverId,
            @Param("statuses") Collection<ApplicationStatus> statuses, Pageable pageable);

    @EntityGraph(attributePaths = { "employee", "approver", "reviewedBy" })
    List<CourseApplication> findByApproverIdAndEmployeeIdAndStatusInOrderBySubmittedAtAsc(Long approverId,
            Long employeeId, Collection<ApplicationStatus> statuses);

    @Query("""
            select a from CourseApplication a
            join fetch a.employee e
            where a.status in :statuses
              and a.startDate <= :to and a.endDate >= :from
            order by a.startDate asc, a.id asc
            """)
    List<CourseApplication> findCalendarEntries(@Param("statuses") Collection<ApplicationStatus> statuses,
            @Param("from") LocalDate from, @Param("to") LocalDate to,
            @Param("category") CategoryCode category);

    @Query("""
            select a from CourseApplication a
            where a.employee.id = :employeeId
              and a.status in :statuses
              and a.startDate <= :end and a.endDate >= :start
            """)
    List<CourseApplication> findOverlapping(@Param("employeeId") Long employeeId,
            @Param("start") LocalDate start, @Param("end") LocalDate end,
            @Param("statuses") Collection<ApplicationStatus> statuses);

    @Query("""
            select a from CourseApplication a
            join fetch a.employee e
            where a.status in :statuses
              and a.startDate <= :end and a.endDate >= :start
              and a.employee.id <> :excludeEmployeeId
            order by a.startDate asc
            """)
    List<CourseApplication> findTeamConcurrent(@Param("statuses") Collection<ApplicationStatus> statuses,
            @Param("start") LocalDate start, @Param("end") LocalDate end,
            @Param("excludeEmployeeId") Long excludeEmployeeId);

    @Query("""
            select a from CourseApplication a
            where a.status in :statuses
              and a.startDate <= :end and a.endDate >= :start
              and a.employee.id in :employeeIds
            """)
    List<CourseApplication> findTeamOverlapping(@Param("employeeIds") Collection<Long> employeeIds,
            @Param("start") LocalDate start, @Param("end") LocalDate end,
            @Param("statuses") Collection<ApplicationStatus> statuses);

    @EntityGraph(attributePaths = { "employee", "approver", "catalogue", "catalogue.provider" })
    List<CourseApplication> findByEmployeeIdAndStatusInOrderByStartDateDesc(Long employeeId,
            Collection<ApplicationStatus> statuses);

    long countByEmployeeIdAndStatus(Long employeeId, ApplicationStatus status);

    long countByStatusIn(Collection<ApplicationStatus> statuses);

    long countByEmployeeIdAndStatusIn(Long employeeId, Collection<ApplicationStatus> statuses);

    long countByApproverIdAndStatusIn(Long approverId, Collection<ApplicationStatus> statuses);

    @Query("select a from CourseApplication a where a.status in :statuses and a.endDate < :today")
    List<CourseApplication> findCompletable(@Param("statuses") Collection<ApplicationStatus> statuses,
            @Param("today") LocalDate today);

    @Query("""
            select count(a) from CourseApplication a
            where a.employee.id = :employeeId and a.status in :statuses
            """)
    long countOpen(@Param("employeeId") Long employeeId, @Param("statuses") Collection<ApplicationStatus> statuses);
}
