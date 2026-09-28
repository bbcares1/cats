package sg.edu.nus.cats.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.edu.nus.cats.domain.ReferenceSequence;
import sg.edu.nus.cats.repository.ReferenceSequenceRepository;

/** Allocates references such as CATS-2026-000123 from a locked counter row. */
@Service
public class ReferenceNumberService {

    private final ReferenceSequenceRepository sequences;

    public ReferenceNumberService(ReferenceSequenceRepository sequences) {
        this.sequences = sequences;
    }

    @Transactional
    public String nextApplicationReference(int year) {
        ReferenceSequence sequence = sequences.lockByYear(year)
                .orElseGet(() -> sequences.save(new ReferenceSequence(year, 0L)));
        long value = sequence.next();
        sequences.save(sequence);
        return "CATS-%d-%06d".formatted(year, value);
    }

    @Transactional
    public String nextReimbursementReference(int year) {
        ReferenceSequence sequence = sequences.lockByYear(-year)
                .orElseGet(() -> sequences.save(new ReferenceSequence(-year, 0L)));
        long value = sequence.next();
        sequences.save(sequence);
        return "SIM-%d-%06d".formatted(year, value);
    }
}
