package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.daniil.shifts.dto.Dtos.OvertimeCreditCreateRequest;
import ru.daniil.shifts.dto.Dtos.OvertimeUsageCreateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.OvertimeAllocation;
import ru.daniil.shifts.repo.OvertimeAllocationRepository;
import ru.daniil.shifts.repo.OvertimeSettlementRepository;
import ru.daniil.shifts.repo.OvertimeUsageRepository;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class OvertimeSettlementFoundationTest {

    @Autowired UserRepository users;
    @Autowired OvertimeService overtime;
    @Autowired OvertimeSettlementService settlements;
    @Autowired OvertimeSettlementRepository settlementRows;
    @Autowired OvertimeUsageRepository usageRows;
    @Autowired OvertimeAllocationRepository allocations;
    @Autowired LedgerIntegrityService ledger;

    AppUser owner;

    @BeforeEach
    void setUp() {
        owner =
                users.save(
                        new AppUser(
                                "settlement-"
                                        + UUID.randomUUID()
                                        .toString()
                                        .substring(0, 10),
                                "{noop}unused"
                        )
                );
    }

    @Test
    void settlementConsumesTheSameCanonicalFifoAfterExistingUsage() {
        overtime.createCredit(
                owner,
                manualCredit(
                        "2026-08-01",
                        2.0
                )
        );

        /*
         * Legacy MANUAL usage is used only as a fixture proving that settlement
         * joins the already existing FIFO instead of creating a second queue.
         */
        overtime.createUsage(
                owner,
                new OvertimeUsageCreateRequest(
                        "2026-08-02",
                        0.5,
                        "legacy first debit"
                )
        );

        var settlement =
                settlements.create(
                        owner,
                        LocalDate.parse(
                                "2026-08-03"
                        ),
                        60,
                        "Оплатить один час"
                );

        assertNotNull(
                settlement.getId()
        );

        var usage =
                usageRows
                        .findByOwnerAndSourceSettlementId(
                                owner,
                                settlement.getId()
                        )
                        .orElseThrow();

        assertEquals(
                "SETTLEMENT",
                usage.getSourceKind()
        );

        assertEquals(
                60,
                usage.getRequestedMinutes()
        );

        assertEquals(
                settlement.getId(),
                usage.getSourceSettlementId()
        );

        assertNull(
                usage.getSourceAbsenceId()
        );

        assertEquals(
                "POSTED",
                usage.getPostingState()
        );

        var settlementAllocations =
                allocations.findByUsage(
                        usage
                );

        assertEquals(
                1,
                settlementAllocations.size()
        );

        OvertimeAllocation allocation =
                settlementAllocations.get(0);

        assertEquals(
                30,
                allocation.getCreditOffsetStartMinutes(),
                "settlement must continue canonical FIFO after the earlier 30-minute debit"
        );

        assertEquals(
                60,
                allocation.getAllocatedMinutes()
        );

        assertEquals(
                30,
                overtime.balanceMinutes(owner)
        );

        var accountUsage =
                overtime.account(owner)
                        .usages()
                        .stream()
                        .filter(row ->
                                row.id().equals(
                                        usage.getId()
                                )
                        )
                        .findFirst()
                        .orElseThrow();

        assertEquals(
                "SETTLEMENT",
                accountUsage.sourceKind()
        );

        assertFalse(
                accountUsage.editable(),
                "settlement-owned bank debit must never be edited as a legacy time-off usage"
        );

        ApiException directDelete =
                assertThrows(
                        ApiException.class,
                        () -> overtime.deleteUsage(
                                owner,
                                usage.getId()
                        )
                );

        assertEquals(
                "SETTLEMENT_USAGE_MANAGED_BY_SETTLEMENT",
                directDelete.getCode()
        );

        /*
         * Settlement is not a legacy MANUAL usage candidate.
         */
        assertEquals(
                1,
                overtime.legacyManualUsages(owner)
                        .size()
        );

        settlements.delete(
                owner,
                settlement.getId()
        );

        assertTrue(
                usageRows
                        .findByOwnerAndSourceSettlementId(
                                owner,
                                settlement.getId()
                        )
                        .isEmpty()
        );

        assertTrue(
                settlementRows
                        .findByOwnerAndId(
                                owner,
                                settlement.getId()
                        )
                        .isEmpty()
        );

        assertEquals(
                90,
                overtime.balanceMinutes(owner),
                "deleting settlement restores only its 60-minute debit"
        );
    }

    @Test
    void failedOverBalanceSettlementLeavesNoBusinessRowBehind() {
        overtime.createCredit(
                owner,
                manualCredit(
                        "2026-08-01",
                        1.0
                )
        );

        ApiException error =
                assertThrows(
                        ApiException.class,
                        () -> settlements.create(
                                owner,
                                LocalDate.parse(
                                        "2026-08-03"
                                ),
                                120,
                                "слишком много"
                        )
                );

        assertTrue(
                error.getMessage()
                        .contains(
                                "Недостаточно переработки"
                        )
        );

        assertTrue(
                settlements.list(owner)
                        .isEmpty(),
                "failed FIFO projection must roll back the settlement row atomically"
        );

        assertEquals(
                60,
                overtime.balanceMinutes(owner)
        );
    }

    @Test
    void closedSettlementPeriodBlocksMutationAndDeletion() {
        overtime.createCredit(
                owner,
                manualCredit(
                        "2026-08-01",
                        2.0
                )
        );

        var settlement =
                settlements.create(
                        owner,
                        LocalDate.parse(
                                "2026-08-03"
                        ),
                        60,
                        "к оплате"
                );

        ledger.closePeriod(
                owner,
                "2026-08"
        );

        ApiException update =
                assertThrows(
                        ApiException.class,
                        () -> settlements.update(
                                owner,
                                settlement.getId(),
                                LocalDate.parse(
                                        "2026-08-04"
                                ),
                                30,
                                "изменить после закрытия"
                        )
                );

        assertEquals(
                "PERIOD_CLOSED",
                update.getCode()
        );

        ApiException delete =
                assertThrows(
                        ApiException.class,
                        () -> settlements.delete(
                                owner,
                                settlement.getId()
                        )
                );

        assertEquals(
                "PERIOD_CLOSED",
                delete.getCode()
        );

        assertEquals(
                60,
                overtime.balanceMinutes(owner)
        );

        assertTrue(
                settlementRows
                        .findByOwnerAndId(
                                owner,
                                settlement.getId()
                        )
                        .isPresent()
        );
    }

    private OvertimeCreditCreateRequest manualCredit(
            String date,
            double hours
    ) {
        return new OvertimeCreditCreateRequest(
                date,
                null,
                null,
                null,
                null,
                null,
                hours,
                "settlement foundation"
        );
    }
}
