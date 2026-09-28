package sg.edu.nus.cats.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Technical counter that allocates human readable reference numbers per year. */
@Entity
@Table(name = "reference_sequence")
public class ReferenceSequence {

    @Id
    @Column(name = "sequence_year", nullable = false)
    private int sequenceYear;

    @Column(name = "last_issued", nullable = false)
    private long lastValue;

    public ReferenceSequence() {
    }

    public ReferenceSequence(int sequenceYear, long lastValue) {
        this.sequenceYear = sequenceYear;
        this.lastValue = lastValue;
    }

    public long next() {
        this.lastValue = this.lastValue + 1;
        return this.lastValue;
    }

    public int getSequenceYear() {
        return sequenceYear;
    }

    public void setSequenceYear(int sequenceYear) {
        this.sequenceYear = sequenceYear;
    }

    public long getLastValue() {
        return lastValue;
    }

    public void setLastValue(long lastValue) {
        this.lastValue = lastValue;
    }
}
