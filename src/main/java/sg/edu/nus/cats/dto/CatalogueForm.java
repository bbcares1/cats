package sg.edu.nus.cats.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import sg.edu.nus.cats.domain.enums.CategoryCode;

public class CatalogueForm {

    private Long id;

    @NotNull(message = "Select a category")
    private CategoryCode categoryCode;

    @NotNull(message = "Select a provider")
    private Long providerId;

    @NotBlank(message = "Enter the course title")
    @Size(max = 150, message = "The title must be 150 characters or fewer")
    private String title;

    @NotNull(message = "Enter the default fee")
    @DecimalMin(value = "0.00", message = "The default fee cannot be negative")
    private BigDecimal defaultFee = BigDecimal.ZERO;

    @Size(max = 500, message = "The description must be 500 characters or fewer")
    private String description;

    private boolean active = true;

    private Long version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CategoryCode getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(CategoryCode categoryCode) {
        this.categoryCode = categoryCode;
    }

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public BigDecimal getDefaultFee() {
        return defaultFee;
    }

    public void setDefaultFee(BigDecimal defaultFee) {
        this.defaultFee = defaultFee;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
