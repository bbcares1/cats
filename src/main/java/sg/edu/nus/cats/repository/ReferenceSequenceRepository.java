package sg.edu.nus.cats.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import sg.edu.nus.cats.domain.ReferenceSequence;

public interface ReferenceSequenceRepository extends JpaRepository<ReferenceSequence, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReferenceSequence r where r.sequenceYear = :year")
    Optional<ReferenceSequence> lockByYear(@Param("year") int year);
}
