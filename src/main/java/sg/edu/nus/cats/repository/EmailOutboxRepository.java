package sg.edu.nus.cats.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import sg.edu.nus.cats.domain.EmailOutbox;
import sg.edu.nus.cats.domain.enums.OutboxStatus;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {

    @EntityGraph(attributePaths = { "recipient", "event" })
    @Query("""
            select o from EmailOutbox o
            where o.status = :status and o.nextAttemptAt <= :now
              and (o.leaseUntil is null or o.leaseUntil < :now)
            order by o.id asc
            """)
    List<EmailOutbox> findClaimable(@Param("status") OutboxStatus status, @Param("now") Instant now,
            Pageable pageable);

    @EntityGraph(attributePaths = { "recipient", "event" })
    Page<EmailOutbox> findAllByOrderByIdDesc(Pageable pageable);

    Page<EmailOutbox> findByStatusOrderByIdDesc(OutboxStatus status, Pageable pageable);

    long countByStatus(OutboxStatus status);

    @EntityGraph(attributePaths = { "recipient", "event" })
    List<EmailOutbox> findTop20ByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = { "recipient", "event" })
    List<EmailOutbox> findByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
