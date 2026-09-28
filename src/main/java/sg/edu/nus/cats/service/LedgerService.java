package sg.edu.nus.cats.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.edu.nus.cats.domain.AuditEvent;
import sg.edu.nus.cats.domain.CourseApplication;
import sg.edu.nus.cats.domain.CourseClaim;
import sg.edu.nus.cats.domain.TrainingAccount;
import sg.edu.nus.cats.domain.TrainingLedger;
import sg.edu.nus.cats.domain.enums.LedgerEntryType;
import sg.edu.nus.cats.repository.TrainingLedgerRepository;
import sg.edu.nus.cats.support.AccountBalance;
import sg.edu.nus.cats.support.LedgerTotals;

/**
 * The ledger is append only. Balances are always recomputed from the entries so
 * that every number on screen can be explained by its history.
 */
@Service
public class LedgerService {

    private final TrainingLedgerRepository ledger;

    public LedgerService(TrainingLedgerRepository ledger) {
        this.ledger = ledger;
    }

    @Transactional
    public TrainingLedger record(TrainingAccount account, AuditEvent event, CourseApplication application,
            CourseClaim claim, LedgerEntryType entryType, int reservedUnitsDelta, int committedUnitsDelta,
            BigDecimal reservedAmountDelta, BigDecimal committedAmountDelta, BigDecimal reimbursedAmountDelta,
            Instant now) {
        TrainingLedger entry = new TrainingLedger();
        entry.setAccount(account);
        entry.setEvent(event);
        entry.setApplication(application);
        entry.setClaim(claim);
        entry.setEntryType(entryType);
        entry.setReservedUnitsDelta(reservedUnitsDelta);
        entry.setCommittedUnitsDelta(committedUnitsDelta);
        entry.setReservedAmountDelta(scale(reservedAmountDelta));
        entry.setCommittedAmountDelta(scale(committedAmountDelta));
        entry.setReimbursedAmountDelta(scale(reimbursedAmountDelta));
        entry.setCreatedAt(now);
        return ledger.save(entry);
    }

    @Transactional(readOnly = true)
    public LedgerTotals totals(Long accountId) {
        return totals(ledger.findByAccountIdOrderByIdAsc(accountId));
    }

    private LedgerTotals totals(List<TrainingLedger> entries) {
        int reservedUnits = 0;
        int committedUnits = 0;
        BigDecimal reservedAmount = BigDecimal.ZERO;
        BigDecimal committedAmount = BigDecimal.ZERO;
        BigDecimal reimbursedAmount = BigDecimal.ZERO;
        for (TrainingLedger entry : entries) {
            reservedUnits += entry.getReservedUnitsDelta();
            committedUnits += entry.getCommittedUnitsDelta();
            reservedAmount = reservedAmount.add(entry.getReservedAmountDelta());
            committedAmount = committedAmount.add(entry.getCommittedAmountDelta());
            reimbursedAmount = reimbursedAmount.add(entry.getReimbursedAmountDelta());
        }
        return new LedgerTotals(reservedUnits, committedUnits, scale(reservedAmount), scale(committedAmount),
                scale(reimbursedAmount));
    }

    @Transactional(readOnly = true)
    public AccountBalance balance(TrainingAccount account) {
        return new AccountBalance(account, totals(account.getId()));
    }

    @Transactional(readOnly = true)
    public List<TrainingLedger> history(Long accountId) {
        return ledger.findByAccountIdOrderByIdAsc(accountId);
    }

    @Transactional(readOnly = true)
    public List<TrainingLedger> forApplication(Long applicationId) {
        return ledger.findByApplicationIdOrderByIdAsc(applicationId);
    }

    @Transactional(readOnly = true)
    public List<TrainingLedger> forClaim(Long claimId) {
        return ledger.findByClaimIdOrderByIdAsc(claimId);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
