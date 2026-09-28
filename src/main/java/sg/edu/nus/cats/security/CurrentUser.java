package sg.edu.nus.cats.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import sg.edu.nus.cats.domain.Employee;
import sg.edu.nus.cats.repository.EmployeeRepository;
import sg.edu.nus.cats.support.BusinessException;

/** Resolves the acting staff member from the security context. */
@Component
public class CurrentUser {

    private final EmployeeRepository employees;

    public CurrentUser(EmployeeRepository employees) {
        this.employees = employees;
    }

    public CatsUserPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CatsUserPrincipal principal)) {
            throw BusinessException.accessDenied("Sign in first");
        }
        return principal;
    }

    public Long employeeId() {
        return principal().getEmployeeId();
    }

    public Employee require() {
        return employees.findById(employeeId())
                .orElseThrow(() -> BusinessException.accessDenied("Your staff record is no longer active"));
    }
}
