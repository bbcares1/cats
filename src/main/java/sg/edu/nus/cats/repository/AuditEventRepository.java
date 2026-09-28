package sg.edu.nus.cats.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import sg.edu.nus.cats.domain.AuditEvent;
import sg.edu.nus.cats.domain.enums.AggregateType;
import sg.edu.nus.cats.domain.enums.AuditEventType;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    @EntityGraph(attributePaths = "actor")
    List<AuditEvent> findByAggregateTypeAndAggregateKeyOrderByCreatedAtAscIdAsc(AggregateType aggregateType,
            String aggregateKey);

    @EntityGraph(attributePaths = "actor")
    @Query("""
            select e from AuditEvent e
            where (:aggregateType is null or e.aggregateType = :aggregateType)
              and (:aggregateKey is null or e.aggregateKey like concat('%', :aggregateKey, '%'))
              and (:eventType is null or e.eventType = :eventType)
              and (:from is null or e.createdAt >= :from)
              and (:to is null or e.createdAt <= :to)
            order by e.createdAt desc, e.id desc
            """)
    Page<AuditEvent> search(@Param("aggregateType") AggregateType aggregateType,
            @Param("aggregateKey") String aggregateKey, @Param("eventType") AuditEventType eventType,
            @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    @EntityGraph(attributePaths = "actor")
    List<AuditEvent> findTop20ByOrderByCreatedAtDesc();
}
