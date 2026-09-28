package sg.edu.nus.cats.domain;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import sg.edu.nus.cats.domain.enums.DaySessionCode;

/** One working day consumed by an application, in half day units. */
@Entity
@Table(name = "application_day")
public class ApplicationDay {

    @EmbeddedId
    private ApplicationDayId id = new ApplicationDayId();

    @MapsId("applicationId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private CourseApplication application;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_code", nullable = false, length = 4)
    private DaySessionCode sessionCode;

    @Column(name = "units", nullable = false)
    private int units;

    public ApplicationDay() {
    }

    public ApplicationDay(LocalDate trainingDate, DaySessionCode sessionCode) {
        this.id = new ApplicationDayId(null, trainingDate);
        this.sessionCode = sessionCode;
        this.units = sessionCode.getUnits();
    }

    public ApplicationDayId getId() {
        return id;
    }

    public void setId(ApplicationDayId id) {
        this.id = id;
    }

    public CourseApplication getApplication() {
        return application;
    }

    public void setApplication(CourseApplication application) {
        this.application = application;
    }

    public DaySessionCode getSessionCode() {
        return sessionCode;
    }

    public void setSessionCode(DaySessionCode sessionCode) {
        this.sessionCode = sessionCode;
    }

    public int getUnits() {
        return units;
    }

    public void setUnits(int units) {
        this.units = units;
    }

    public LocalDate getTrainingDate() {
        return id.getTrainingDate();
    }
}
