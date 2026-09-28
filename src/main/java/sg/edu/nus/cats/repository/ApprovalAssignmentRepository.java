package sg.edu.nus.cats.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import sg.edu.nus.cats.domain.ApprovalAssignment;

public interface ApprovalAssignmentRepository extends JpaRepository<ApprovalAssignment, Long> {

    @EntityGraph(attributePaths = "manager")
    Optional<ApprovalAssignment> findWithManagerByEmployeeId(Long employeeId);

    @EntityGraph(attributePaths = "manager")
    List<ApprovalAssignment> findByManagerIdOrderByEmployeeIdAsc(Long managerId);

    boolean existsByEmployeeId(Long employeeId);

    boolean existsByManagerId(Long managerId);

    @EntityGraph(attributePaths = { "manager" })
    Page<ApprovalAssignment> findAll(Pageable pageable);
}
