package sg.edu.nus.cats.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class HolidayForm {

    @NotNull(message = "Enter the holiday date")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate holidayDate;

    @NotBlank(message = "Enter the holiday name")
    @Size(max = 120, message = "The holiday name must be 120 characters or fewer")
    private String name;

    @Size(max = 200, message = "The source note must be 200 characters or fewer")
    private String sourceNote;

    public LocalDate getHolidayDate() {
        return holidayDate;
    }

    public void setHolidayDate(LocalDate holidayDate) {
        this.holidayDate = holidayDate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSourceNote() {
        return sourceNote;
    }

    public void setSourceNote(String sourceNote) {
        this.sourceNote = sourceNote;
    }
}
