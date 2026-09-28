package sg.edu.nus.cats.domain.enums;

/** Permission role. Kept separate from the employee designation (job title). */
public enum RoleCode {
    EMPLOYEE,
    MANAGER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
