package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class OvertimeHistoricalCorrectionTest {

    @Autowired
    OvertimeService overtime;

    @Autowired
    LedgerIntegrityService ledger;

    @Autowired
    UserRepository users;

    AppUser user;
    LocalDate date;

    @BeforeEach
    void setUp() {
        user = users.save(
                new AppUser(
                        "historical-overtime-correction-user",
                        "{noop}irrelevant"
                )
        );

        date = LocalDate.of(2026, 8, 18);
    }

    @Test
    void ordinaryDerivedReconcileStillRejectsClosedPeriodButHistoricalPathCanCorrectIt() {
        overtime.reconcileActualWorkCredit(
                user,
                date,
                120,
                480,
                "initial derived proof"
        );

        ledger.closePeriod(user, "2026-08");

        assertThrows(
                ApiException.class,
                () -> overtime.reconcileActualWorkCredit(
                        user,
                        date,
                        180,
                        480,
                        "ordinary mutation must stay locked"
                )
        );

        assertDoesNotThrow(
                () -> overtime.reconcileActualWorkCreditHistoricalCorrection(
                        user,
                        date,
                        180,
                        480,
                        "temporal context correction"
                )
        );

        var account = overtime.account(user);

        var credit = account.credits().stream()
                .filter(item -> date.toString().equals(item.workedDate()))
                .filter(item -> "SYSTEM_ACTUAL_WORK".equals(item.sourceKind()))
                .findFirst()
                .orElseThrow();

        assertEquals(180, credit.creditedMinutes());
    }
}
