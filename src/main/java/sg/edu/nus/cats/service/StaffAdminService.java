package sg.edu.nus.cats.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.edu.nus.cats.domain.ApprovalAssignment;
import sg.edu.nus.cats.domain.Employee;
import sg.edu.nus.cats.domain.UserAccount;
import sg.edu.nus.cats.domain.enums.AggregateType;
import sg.edu.nus.cats.domain.enums.AuditEventType;
import sg.edu.nus.cats.domain.enums.RoleCode;
import sg.edu.nus.cats.dto.RoutingForm;
import sg.edu.nus.cats.dto.StaffForm;
import sg.edu.nus.cats.repository.ApprovalAssignmentRepository;
import sg.edu.nus.cats.repository.EmployeeRepository;
import sg.edu.nus.cats.repository.UserAccountRepository;
import sg.edu.nus.cats.support.BusinessException;
import sg.edu.nus.cats.support.ErrorCode;

/**
 * Staff master data, login accounts, permission roles and approval routing.
 * A manager only reaches an employee through an {@link ApprovalAssignment} row.
 */
@Service
public class StaffAdminService {

    private final EmployeeRepository employees;
    private final UserAccountRepository accounts;
    private final ApprovalAssignmentRepository assignments;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final Clock clock;

    public StaffAdminService(EmployeeRepository employees, UserAccountRepository accounts,
            ApprovalAssignmentRepository assignments, PasswordEncoder passwordEncoder, AuditService audit,
            Clock clock) {
        this.employees = employees;
        this.accounts = accounts;
        this.assignments = assignments;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.clock = clock;
    }

    /** One row of the P13 staff register. */
    public record StaffRow(Long id, String staffNo, String fullName, String email, String department,
            String designationCode, boolean active, String username, boolean accountEnabled,
            List<RoleCode> roles, Long managerId, String managerName, Long version) {
    }

    @Transactional(readOnly = true)
    public Page<Employee> search(String query, boolean activeOnly, Pageable pageable) {
        String normalised = query == null || query.isBlank() ? null : query.trim();
        return employees.search(normalised, activeOnly, pageable);
    }

    @Transactional(readOnly = true)
    public List<StaffRow> rows(Page<Employee> page) {
        Map<Long, ApprovalAssignment> routing = new LinkedHashMap<>();
        assignments.findAll().forEach(row -> routing.put(row.getEmployeeId(), row));
        List<StaffRow> rows = new ArrayList<>();
        for (Employee employee : page.getContent()) {
            UserAccount account = accounts.findByEmployeeId(employee.getId()).orElse(null);
            ApprovalAssignment route = routing.get(employee.getId());
            rows.add(new StaffRow(employee.getId(), employee.getStaffNo(), employee.getFullName(),
                    employee.getEmail(), employee.getDepartment(), employee.getDesignationCode(), employee.isActive(),
                    account == null ? null : account.getUsername(), account != null && account.isEnabled(),
                    account == null ? List.of() : List.copyOf(account.getRoleCodes()),
                    route == null ? null : route.getManager().getId(),
                    route == null ? null : route.getManager().getFullName(), employee.getVersion()));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public Employee require(Long employeeId) {
        return employees.findById(employeeId).orElseThrow(() -> BusinessException.notFound("Employee"));
    }

    @Transactional(readOnly = true)
    public StaffForm toForm(Long employeeId) {
        Employee employee = require(employeeId);
        StaffForm form = new StaffForm();
        form.setId(employee.getId());
        form.setStaffNo(employee.getStaffNo());
        form.setFullName(employee.getFullName());
        form.setEmail(employee.getEmail());
        form.setDepartment(employee.getDepartment());
        form.setDesignationCode(employee.getDesignationCode());
        form.setActive(employee.isActive());
        form.setVersion(employee.getVersion());
        accounts.findByEmployeeId(employeeId).ifPresent(account -> {
            form.setUsername(account.getUsername());
            form.setRoles(new ArrayList<>(account.getRoleCodes()));
        });
        return form;
    }

    @Transactional
    public Employee create(StaffForm form, Employee actor) {
        String staffNo = form.getStaffNo().trim();
        if (employees.existsByStaffNoIgnoreCase(staffNo)) {
            throw new BusinessException(ErrorCode.DUPLICATE_STAFF_NO,
                    "Staff number " + staffNo + " already exists");
        }
        if (form.getEmail() != null && employees.findByEmailIgnoreCase(form.getEmail().trim()).isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL,
                    "Email " + form.getEmail().trim() + " already belongs to another staff member");
        }
        Instant now = clock.instant();
        Employee employee = new Employee();
        employee.setStaffNo(staffNo);
        employee.setFullName(form.getFullName().trim());
        employee.setEmail(form.getEmail().trim());
        employee.setDepartment(form.getDepartment() == null || form.getDepartment().isBlank() ? "Unassigned"
                : form.getDepartment().trim());
        employee.setDesignationCode(form.getDesignationCode() == null || form.getDesignationCode().isBlank()
                ? "STAFF"
                : form.getDesignationCode().trim());
        employee.setActive(form.isActive());
        employee.setCreatedAt(now);
        employee.setUpdatedAt(now);
        employees.save(employee);

        String username = resolveUsername(form, employee);
        String rawPassword = form.getInitialPassword() == null || form.getInitialPassword().isBlank()
                ? defaultPassword()
                : form.getInitialPassword();
        UserAccount account = new UserAccount();
        account.setEmployee(employee);
        account.setUsername(username);
        account.setPasswordHash(passwordEncoder.encode(rawPassword));
        account.setEnabled(form.isActive());
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        for (RoleCode role : rolesOf(form, RoleCode.EMPLOYEE)) {
            account.addRole(role);
        }
        accounts.save(account);

        audit.record(AggregateType.STAFF, employee.getStaffNo(), AuditEventType.STAFF_CREATED, actor, null, null,
                "Created staff " + employee.getStaffNo() + " with roles " + account.getRoleCodes(), null);
        return employee;
    }

    @Transactional
    public Employee update(Long employeeId, StaffForm form, Employee actor) {
        Employee employee = require(employeeId);
        if (form.getVersion() != null && form.getVersion() != employee.getVersion()) {
            throw new BusinessException(ErrorCode.STALE_VERSION,
                    "This staff record changed since the page was opened. Reload and reapply the change.");
        }
        String staffNo = form.getStaffNo().trim();
        if (!staffNo.equalsIgnoreCase(employee.getStaffNo())) {
            if (employees.existsByStaffNoIgnoreCase(staffNo)) {
                throw new BusinessException(ErrorCode.DUPLICATE_STAFF_NO,
                    "Staff number " + staffNo + " already exists");
            }
            employee.setStaffNo(staffNo);
        }
        if (form.getEmail() != null && !form.getEmail().isBlank()) {
            Optional<Employee> clash = employees.findByEmailIgnoreCase(form.getEmail().trim());
            if (clash.isPresent() && !clash.get().getId().equals(employeeId)) {
                throw new BusinessException(ErrorCode.DUPLICATE_EMAIL,
                    "Email " + form.getEmail().trim() + " already belongs to another staff member");
            }
            employee.setEmail(form.getEmail().trim());
        }
        employee.setFullName(form.getFullName().trim());
        employee.setDepartment(form.getDepartment() == null || form.getDepartment().isBlank() ? "Unassigned"
                : form.getDepartment().trim());
        employee.setDesignationCode(form.getDesignationCode() == null || form.getDesignationCode().isBlank()
                ? "STAFF"
                : form.getDesignationCode().trim());
        employee.setActive(form.isActive());
        employee.setUpdatedAt(clock.instant());
        employees.save(employee);

        UserAccount account = accounts.findByEmployeeId(employeeId).orElseGet(() -> {
            UserAccount created = new UserAccount();
            created.setEmployee(employee);
            created.setUsername(resolveUsername(form, employee));
            created.setPasswordHash(passwordEncoder.encode(defaultPassword()));
            created.setCreatedAt(clock.instant());
            return created;
        });
        String username = resolveUsername(form, employee);
        if (!username.equalsIgnoreCase(account.getUsername())) {
            if (accounts.existsByUsernameIgnoreCase(username)) {
                throw new BusinessException(ErrorCode.DUPLICATE_USERNAME,
                        "Username " + username + " is already taken");
            }
            account.setUsername(username);
        }
        account.setEnabled(form.isActive());
        account.setUpdatedAt(clock.instant());
        for (RoleCode role : rolesOf(form, RoleCode.EMPLOYEE)) {
            account.addRole(role);
        }
        account.getRoleCodes().stream().filter(role -> !rolesOf(form, RoleCode.EMPLOYEE).contains(role))
                .forEach(account::removeRole);
        if (form.getInitialPassword() != null && !form.getInitialPassword().isBlank()) {
            account.setPasswordHash(passwordEncoder.encode(form.getInitialPassword()));
        }
        accounts.save(account);

        if (account.hasRole(RoleCode.MANAGER)) {
            // Managers need no routing row of their own, but they must not approve themselves.
            assignments.findWithManagerByEmployeeId(employeeId)
                    .filter(row -> row.getManager().getId().equals(employeeId))
                    .ifPresent(assignments::delete);
        } else {
            assignments.findWithManagerByEmployeeId(employeeId).ifPresent(assignments::delete);
        }

        audit.record(AggregateType.STAFF, employee.getStaffNo(), AuditEventType.STAFF_UPDATED, actor, null, null,
                "Updated staff " + employee.getStaffNo() + " with roles " + account.getRoleCodes(), null);
        return employee;
    }

    @Transactional
    public void assignManager(RoutingForm form, Employee actor) {
        if (form.getEmployeeId().equals(form.getManagerId())) {
            throw new BusinessException(ErrorCode.ROUTING_INVALID, "A manager cannot approve their own claims");
        }
        Employee employee = require(form.getEmployeeId());
        Employee manager = require(form.getManagerId());
        UserAccount managerAccount = accounts.findByEmployeeId(manager.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ROUTING_INVALID,
                        manager.getFullName() + " has no login account"));
        if (!managerAccount.hasRole(RoleCode.MANAGER)) {
            throw new BusinessException(ErrorCode.ROUTING_INVALID,
                    manager.getFullName() + " does not hold the manager role");
        }
        ApprovalAssignment assignment = assignments.findWithManagerByEmployeeId(employee.getId())
                .orElseGet(ApprovalAssignment::new);
        assignment.setEmployeeId(employee.getId());
        assignment.setManager(manager);
        assignment.setUpdatedAt(clock.instant());
        assignments.save(assignment);
        audit.record(AggregateType.ROUTING, employee.getStaffNo(), AuditEventType.ROUTING_CHANGED, actor, null, null,
                "Approving manager for " + employee.getStaffNo() + " set to " + manager.getStaffNo(), null);
    }

    @Transactional
    public void removeRouting(Long employeeId, Employee actor) {
        Employee employee = require(employeeId);
        assignments.findWithManagerByEmployeeId(employeeId).ifPresent(assignments::delete);
        audit.record(AggregateType.ROUTING, employee.getStaffNo(), AuditEventType.ROUTING_CHANGED, actor, null, null,
                "Approving manager removed for " + employee.getStaffNo(), null);
    }

    @Transactional
    public void setAccountEnabled(Long employeeId, boolean enabled, Employee actor) {
        UserAccount account = accounts.findByEmployeeId(employeeId)
                .orElseThrow(() -> BusinessException.notFound("Login account"));
        account.setEnabled(enabled);
        account.setUpdatedAt(clock.instant());
        accounts.save(account);
        audit.record(AggregateType.ACCOUNT, account.getUsername(), AuditEventType.ACCOUNT_DISABLED, actor, null, null,
                "Login account " + account.getUsername() + (enabled ? " enabled" : " disabled"), null);
    }

    @Transactional(readOnly = true)
    public List<Employee> assignableManagers() {
        List<UserAccount> managers = accounts.findAll().stream()
                .filter(account -> account.isEnabled() && account.hasRole(RoleCode.MANAGER))
                .sorted(Comparator.comparing(account -> account.getEmployee().getFullName()))
                .toList();
        return managers.stream().map(UserAccount::getEmployee).toList();
    }

    @Transactional(readOnly = true)
    public List<RoutingRow> routingRows(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase();
        Map<Long, ApprovalAssignment> routing = new LinkedHashMap<>();
        assignments.findAll().forEach(row -> routing.put(row.getEmployeeId(), row));
        List<RoutingRow> rows = new ArrayList<>();
        for (Employee employee : employees.findAll()) {
            ApprovalAssignment route = routing.get(employee.getId());
            rows.add(new RoutingRow(employee.getId(), employee.getStaffNo(), employee.getFullName(),
                    employee.getDepartment(), route == null ? null : route.getManager().getId(),
                    route == null ? null : route.getManager().getFullName()));
        }
        return rows.stream()
                .filter(row -> needle.isEmpty() || row.fullName().toLowerCase().contains(needle)
                        || row.staffNo().toLowerCase().contains(needle))
                .sorted(Comparator.comparing(RoutingRow::fullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Routing table row for P14. */
    public record RoutingRow(Long employeeId, String staffNo, String fullName, String department, Long managerId,
            String managerName) {
    }

    private List<RoleCode> rolesOf(StaffForm form, RoleCode fallback) {
        List<RoleCode> roles = form.getRoles() == null ? new ArrayList<>() : new ArrayList<>(form.getRoles());
        roles.removeIf(java.util.Objects::isNull);
        if (roles.isEmpty()) {
            roles.add(fallback);
        }
        return roles;
    }

    private String resolveUsername(StaffForm form, Employee employee) {
        if (form.getUsername() != null && !form.getUsername().isBlank()) {
            return form.getUsername().trim().toLowerCase();
        }
        return employee.getStaffNo().trim().toLowerCase();
    }

    private String defaultPassword() {
        return "Cats@2026";
    }
}
