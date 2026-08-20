package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.OvertimeCreditCreateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
class OvertimeSettlementReadModelSegregationTest {

    @Autowired UserRepository users;
    @Autowired OvertimeService overtime;
    @Autowired OvertimeSettlementService settlements;
    @Autowired TimeCompensationService compensation;

    AppUser owner;

    @BeforeEach
    void setUp() {
        owner =
                users.save(
                        new AppUser(
                                "settlement-read-"
                                        + UUID.randomUUID()
                                        .toString()
                                        .substring(0, 10),
                                "{noop}unused"
                        )
                );
    }

    @Test
    void settlementReducesBankButNeverBecomesTimeOffOrApprovalCompensation() {
        LocalDate creditDate =
                LocalDate.parse(
                        "2026-08-01"
                );

        LocalDate settlementDate =
                LocalDate.parse(
                        "2026-08-03"
                );

        overtime.createCredit(
                owner,
                new OvertimeCreditCreateRequest(
                        creditDate.toString(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        2.0,
                        "read-model segregation"
                )
        );

        settlements.create(
                owner,
                settlementDate,
                60,
                "к оплате"
        );

        var account =
                overtime.account(
                        owner
                );

        assertEquals(
                2.0,
                account.totalEarnedHours()
        );

        assertEquals(
                1.0,
                account.totalUsedHours()
        );

        assertEquals(
                1.0,
                account.balanceHours()
        );

        assertEquals(
                "SETTLEMENT",
                account.usages()
                        .get(0)
                        .sourceKind()
        );

        var summary =
                overtime.summary(
                        owner,
                        creditDate,
                        creditDate
                );

        assertEquals(
                2.0,
                summary.overtimeHours()
        );

        assertEquals(
                0.0,
                summary.timeOffHours(),
                "cash settlement is not time-off"
        );

        assertEquals(
                1.0,
                summary.balanceHours(),
                "cash settlement still reduces the one canonical bank"
        );

        var ledger =
                overtime.ledger(
                        owner,
                        creditDate,
                        creditDate
                );

        assertEquals(
                1,
                ledger.size()
        );

        assertEquals(
                0.0,
                ledger.get(0)
                        .timeOffHours()
        );

        assertEquals(
                1.0,
                ledger.get(0)
                        .balanceHours()
        );

        var time =
                compensation.summary(
                        owner,
                        settlementDate,
                        settlementDate
                );

        assertEquals(
                60,
                time.overtimeUsedMinutes(),
                "operational Time Bank usage remains visible"
        );

        assertEquals(
                0,
                time.compensatedMinutes(),
                "settlement must not cover scheduled time as an overtime-bank absence"
        );

        assertEquals(
                0,
                time.overtimeReservedMinutes(),
                "cash settlement is not an absence reservation"
        );

        assertEquals(
                0,
                time.overtimePostedMinutes(),
                "cash settlement is not an absence approval posting"
        );
    }
}
