package sg.edu.nus.cats.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import sg.edu.nus.cats.domain.enums.CategoryCode;

/** The fixed set of course categories. Descriptions are maintainable, codes are not. */
@Entity
@Table(name = "course_category")
public class CourseCategory {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, length = 20)
    private CategoryCode code;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(name = "description", length = 400)
    private String description;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public CategoryCode getCode() {
        return code;
    }

    public void setCode(CategoryCode code) {
        this.code = code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public long getVersion() {
        return version;
    }
}
