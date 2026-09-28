package sg.edu.nus.cats.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.edu.nus.cats.domain.ClaimDocument;

public interface ClaimDocumentRepository extends JpaRepository<ClaimDocument, Long> {

    List<ClaimDocument> findByClaimIdOrderByClaimRevisionDescIdAsc(Long claimId);

    List<ClaimDocument> findByClaimIdAndClaimRevision(Long claimId, int claimRevision);
}
