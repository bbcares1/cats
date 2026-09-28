package sg.edu.nus.cats.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.edu.nus.cats.domain.Employee;
import sg.edu.nus.cats.domain.TrainingAccount;
import sg.edu.nus.cats.domain.enums.AggregateType;
import sg.edu.nus.cats.domain.enums.AuditEventType;
import sg.edu.nus.cats.dto.EntitlementForm;
import sg.edu.nus.cats.repository.EmployeeRepository;
import sg.edu.nus.cats.repository.TrainingAccountRepository;
import sg.edu.nus.cats.support.AccountBalance;
import sg.edu.nus.cats.support.BusinessException;
import sg.edu.nus.cats.support.ErrorCode;
import sg.edu.nus.cats.support.Json;

/** Owns training day and budget entitlements plus the quota checks used before saving. */
@Service
public class EntitlementService {

    private final TrainingAccountRepository accounts;
    private final EmployeeRepository employees;
    private final LedgerService ledger;
    private final AuditService audit;
    private final Clock clock;

    public EntitlementService(TrainingAccountRepository accounts, EmployeeRepository employees, LedgerService ledger,
            AuditService audit, Clock clock) {
        this.accounts = accounts;
        this.employees = employees;
        this.ledger = ledger;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<AccountBalance> find(Long employeeId, int calendarYear) {
        return accounts.findByEmployeeIdAndCalendarYear(employeeId, calendarYear).map(ledger::balance);
    }

    @Transactional(readOnly = true)
    public AccountBalance require(Long employeeId, int calendarYear) {
        return find(employeeId, calendarYear).orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_CONFIGURED,
                "No " + calendarYear + " training entitlement is configured for this employee. "
                        + "Ask an administrator to set the entitlement before applying."));
    }

    @Transactional(readOnly = true)
    public List<AccountBalance> balancesOf(Long employeeId) {
        return accounts.findByEmployeeIdOrderByCalendarYearDesc(employeeId).stream().map(ledger::balance).toList();
    }

    @Transactional(readOnly = true)
    public List<AccountBalance> balancesForYear(int calendarYear) {
        return accounts.findByCalendarYearOrderByEmployeeFullNameAsc(calendarYear).stream().map(ledger::balance)
                .toList();
    }

    /** Rejects a reservation that would exceed either entitlement. */
    public void assertAvailable(AccountBalance balance, int units, BigDecimal amount, int year) {
        if (!balance.hasUnitsFor(units)) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_UNITS,
                    "Not enough training days left in " + year + ": " + balance.getAvailableDaysLabel()
                            + " available, " + AccountBalance.formatDays(units) + " requested.");
        }
        if (amount != null && amount.signum() > 0 && !balance.hasBudgetFor(amount)) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BUDGET,
                    "Not enough " + year + " training budget left: $" + balance.availableAmount().toPlainString()
                            + " available, $" + amount.toPlainString() + " requested.");
        }
    }

    @Transactional
    public TrainingAccount saveEntitlement(EntitlementForm form, Employee actor) {
        Employee employee = employees.findById(form.getEmployeeId())
                .orElseThrow(() -> BusinessException.notFound("Employee"));
        TrainingAccount account = accounts
                .findByEmployeeIdAndCalendarYear(form.getEmployeeId(), form.getCalendarYear())
                .orElseGet(TrainingAccount::new);
        boolean isNew = account.getId() == null;
        if (!isNew) {
            requireVersion(account.getVersion(), form.getVersion());
            AccountBalance current = ledger.balance(account);
            if (form.getEntitledUnits() < current.usedUnits() || form.getBudgetAmount().compareTo(current.usedAmount()) < 0) {
                throw new BusinessException(ErrorCode.ENTITLEMENT_BELOW_USAGE,
                        "The new entitlement is below what has already been used "
                                + "(" + current.usedUnits() + " half days, $" + current.usedAmount().toPlainString()
                                + "). Adjust or release the existing applications first.");
            }
        }
        String before = snapshot(account);
        account.setEmployee(employee);
        account.setCalendarYear(form.getCalendarYear());
        account.setEntitledUnits(form.getEntitledUnits());
        account.setBudgetAmount(form.getBudgetAmount());
        account.setUpdatedAt(Instant.now(clock));
        TrainingAccount saved = accounts.save(account);

        audit.record(AggregateType.ACCOUNT, "ACCOUNT-" + saved.getId(),
                AuditEventType.ENTITLEMENT_CHANGED, actor, before, snapshot(saved), null,
                Json.write(java.util.Map.of("employeeId", form.getEmployeeId(),
                        "calendarYear", form.getCalendarYear(),
                        "entitledUnits", form.getEntitledUnits(),
                        "budgetAmount", form.getBudgetAmount().toPlainString())));
        return saved;
    }

    private String snapshot(TrainingAccount account) {
        return account.getId() == null ? "NEW"
                : account.getEntitledUnits() + " days / $" + account.getBudgetAmount().toPlainString();
    }

    private void requireVersion(long current, Long submitted) {
        if (submitted != null && submitted != current) {
            throw new BusinessException(ErrorCode.STALE_VERSION,
                    "This record was changed by someone else. Reload the page and try again.");
        }
    }
}
