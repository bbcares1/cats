package sg.edu.nus.cats.domain;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ApplicationDayId implements Serializable {

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "training_date", nullable = false)
    private LocalDate trainingDate;

    protected ApplicationDayId() {
    }

    public ApplicationDayId(Long applicationId, LocalDate trainingDate) {
        this.applicationId = applicationId;
        this.trainingDate = trainingDate;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public LocalDate getTrainingDate() {
        return trainingDate;
    }

    public void setTrainingDate(LocalDate trainingDate) {
        this.trainingDate = trainingDate;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ApplicationDayId that)) {
            return false;
        }
        return Objects.equals(applicationId, that.applicationId) && Objects.equals(trainingDate, that.trainingDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(applicationId, trainingDate);
    }
}
