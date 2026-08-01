package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.TimeAccountingPeriod;
import ru.daniil.shifts.repo.TimeAccountingPeriodRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Small dependency-only guard for mutations that affect a closed payroll snapshot.
 *
 * It deliberately owns no posting or audit logic, so calendar services can enforce
 * period locks without depending on the larger ledger workflow service.
 */
@Service
public class AccountingPeriodLockService {
    private final TimeAccountingPeriodRepository periods;

    public AccountingPeriodLockService(TimeAccountingPeriodRepository periods) {
        this.periods = periods;
    }

    @Transactional(readOnly = true)
    public void assertOpen(AppUser user, LocalDate date) {
        if (date == null) return;
        LocalDate month = YearMonth.from(date).atDay(1);
        if (periods.findByOwnerAndPeriodMonth(user, month)
                .map(TimeAccountingPeriod::isClosed)
                .orElse(false)) {
            throw ApiException.conflict("PERIOD_CLOSED",
                    "Расчётный период " + YearMonth.from(date)
                            + " закрыт. Добавь корректировку или сначала открой период.");
        }
    }

    @Transactional(readOnly = true)
    public void assertRangeOpen(AppUser user, LocalDate from, LocalDate to) {
        if (from == null || to == null) return;
        YearMonth cursor = YearMonth.from(from);
        YearMonth end = YearMonth.from(to);
        while (!cursor.isAfter(end)) {
            assertOpen(user, cursor.atDay(1));
            cursor = cursor.plusMonths(1);
        }
    }
}
