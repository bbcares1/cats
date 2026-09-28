package sg.edu.nus.cats.dto;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import sg.edu.nus.cats.domain.enums.RoleCode;

public class StaffForm {

    private Long id;

    @NotBlank(message = "Enter the staff number")
    @Size(max = 30, message = "The staff number must be 30 characters or fewer")
    private String staffNo;

    @NotBlank(message = "Enter the full name")
    @Size(max = 120, message = "The full name must be 120 characters or fewer")
    private String fullName;

    @NotBlank(message = "Enter the work email")
    @Email(message = "Enter a valid email address")
    @Size(max = 150, message = "The email must be 150 characters or fewer")
    private String email;

    @Size(max = 120, message = "The department must be 120 characters or fewer")
    private String department;

    @Size(max = 30, message = "The designation code must be 30 characters or fewer")
    private String designationCode;

    private boolean active = true;

    @Size(min = 4, max = 60, message = "The username must be between 4 and 60 characters")
    private String username;

    @Size(max = 100, message = "The initial password must be 100 characters or fewer")
    private String initialPassword;

    private List<RoleCode> roles = new ArrayList<>();

    private Long version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStaffNo() {
        return staffNo;
    }

    public void setStaffNo(String staffNo) {
        this.staffNo = staffNo;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getDesignationCode() {
        return designationCode;
    }

    public void setDesignationCode(String designationCode) {
        this.designationCode = designationCode;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getInitialPassword() {
        return initialPassword;
    }

    public void setInitialPassword(String initialPassword) {
        this.initialPassword = initialPassword;
    }

    public List<RoleCode> getRoles() {
        return roles;
    }

    public void setRoles(List<RoleCode> roles) {
        this.roles = roles == null ? new ArrayList<>() : roles;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
