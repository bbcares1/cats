package sg.edu.nus.cats.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.edu.nus.cats.domain.TrainingLedger;

public interface TrainingLedgerRepository extends JpaRepository<TrainingLedger, Long> {

    List<TrainingLedger> findByAccountIdOrderByIdAsc(Long accountId);

    List<TrainingLedger> findByApplicationIdOrderByIdAsc(Long applicationId);

    List<TrainingLedger> findByClaimIdOrderByIdAsc(Long claimId);

    boolean existsByEventIdAndAccountId(Long eventId, Long accountId);
}
