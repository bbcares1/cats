package sg.edu.nus.cats.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import sg.edu.nus.cats.domain.CourseClaim;
import sg.edu.nus.cats.domain.enums.ClaimStatus;

public interface CourseClaimRepository extends JpaRepository<CourseClaim, Long> {

    Optional<CourseClaim> findByApplicationId(Long applicationId);

    @EntityGraph(attributePaths = { "application", "application.employee", "approver", "reviewedBy", "reimbursedBy" })
    Optional<CourseClaim> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = { "application", "application.employee", "approver", "documents" })
    List<CourseClaim> findByApproverIdAndStatusInOrderBySubmittedAtAsc(Long approverId,
            Collection<ClaimStatus> statuses);

    @EntityGraph(attributePaths = { "application", "application.employee" })
    List<CourseClaim> findByApplicationEmployeeIdOrderBySubmittedAtDesc(Long employeeId);

    @EntityGraph(attributePaths = { "application", "application.employee", "approver", "reviewedBy", "reimbursedBy",
            "documents" })
    List<CourseClaim> findByStatusOrderBySubmittedAtAsc(ClaimStatus status);

    long countByApproverIdAndStatusIn(Long approverId, Collection<ClaimStatus> statuses);

    long countByStatus(ClaimStatus status);

    long countByStatusIn(Collection<ClaimStatus> statuses);

    @EntityGraph(attributePaths = { "application", "application.employee", "approver" })
    @Query("select c from CourseClaim c where c.application.employee.id = :employeeId")
    List<CourseClaim> findByEmployee(@Param("employeeId") Long employeeId);
}
