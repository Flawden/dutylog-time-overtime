package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.daniil.shifts.dto.Dtos.*;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.CompensationTerm;
import ru.daniil.shifts.model.PayrollSettings;
import ru.daniil.shifts.model.PayrollSnapshot;
import ru.daniil.shifts.model.TimeAccountingPeriod;
import ru.daniil.shifts.repo.CompensationTermRepository;
import ru.daniil.shifts.repo.PayrollAdjustmentRepository;
import ru.daniil.shifts.repo.PayrollSettingsRepository;
import ru.daniil.shifts.repo.PayrollSnapshotRepository;
import ru.daniil.shifts.repo.TimeAccountingPeriodRepository;
import ru.daniil.shifts.service.CompensationCalculationService.Result;
import ru.daniil.shifts.service.PayrollSettlementPricingService.SettlementLine;
import ru.daniil.shifts.service.PayrollSettlementPreviewService.SettlementPreview;
import ru.daniil.shifts.service.TimeCompensationService.PayrollSourceSnapshot;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PayrollSettlementSnapshotServiceTest {

    @Test
    void readySettlementIsFrozenIntoImmutablePayrollSnapshotWithFingerprint() {
        PayrollSettingsRepository settings =
                mock(PayrollSettingsRepository.class);

        CompensationTermRepository compensationTerms =
                mock(CompensationTermRepository.class);

        PayrollAdjustmentRepository adjustments =
                mock(PayrollAdjustmentRepository.class);

        PayrollSnapshotRepository snapshots =
                mock(PayrollSnapshotRepository.class);

        TimeAccountingPeriodRepository accountingPeriods =
                mock(TimeAccountingPeriodRepository.class);

        TimeCompensationService timeCompensation =
                mock(TimeCompensationService.class);

        LedgerIntegrityService ledgerIntegrity =
                mock(LedgerIntegrityService.class);

        ProductionCalendarService productionCalendar =
                mock(ProductionCalendarService.class);

        CompensationCalculationService calculation =
                mock(CompensationCalculationService.class);

        PayrollSettlementPreviewService settlementPricing =
                mock(PayrollSettlementPreviewService.class);

        PayrollOrdinaryPremiumPreviewService ordinaryPremiumPricing =
                mock(
                        PayrollOrdinaryPremiumPreviewService.class
                );


        PayrollService payroll =
                new PayrollService(
                        settings,
                        compensationTerms,
                        adjustments,
                        snapshots,
                        accountingPeriods,
                        timeCompensation,
                        ledgerIntegrity,
                        productionCalendar,
                        calculation,
                        settlementPricing
                ,
                        ordinaryPremiumPricing);

        /*
         * Settlement snapshot regression owns settlement semantics only.
         * Ordinary premium is intentionally empty in this fixture.
         */
        org.mockito.Mockito.when(
                ordinaryPremiumPricing.preview(
                        org.mockito.ArgumentMatchers.any(
                                ru.daniil.shifts.model.AppUser.class
                        ),
                        org.mockito.ArgumentMatchers.any(
                                java.time.YearMonth.class
                        ),
                        org.mockito.ArgumentMatchers.anyString()
                )
        ).thenAnswer(invocation ->
                new PayrollOrdinaryPremiumPreviewService.OrdinaryPremiumPreview(
                        (java.time.YearMonth) invocation.getArgument(1),
                        true,
                        null,
                        null,
                        60,
                        100_000L,
                        20_000L,
                        true,
                        "b".repeat(64),
                        java.util.List.of()
                )
        );


        AppUser user =
                new AppUser(
                        "snapshot-settlement-user",
                        "{noop}unused"
                );

        LocalDate month =
                LocalDate.of(
                        2026,
                        8,
                        1
                );

        TimeAccountingPeriod period =
                mock(TimeAccountingPeriod.class);

        Instant closedAt =
                Instant.parse(
                        "2026-09-01T00:00:00Z"
                );

        when(period.isClosed())
                .thenReturn(true);

        when(period.getClosedAt())
                .thenReturn(closedAt);

        when(
                accountingPeriods
                        .findForUpdateByOwnerAndPeriodMonth(
                                user,
                                month
                        )
        ).thenReturn(
                Optional.of(period)
        );

        when(
                ledgerIntegrity.inspect(
                        user,
                        month,
                        LocalDate.of(
                                2026,
                                8,
                                31
                        )
                )
        ).thenReturn(
                new LedgerIntegrityDto(
                        "2026-08-01",
                        "2026-08-31",
                        true,
                        0,
                        0,
                        0,
                        0,
                        0,
                        List.of(),
                        List.of(),
                        List.of()
                )
        );

        CompensationTerm term =
                mock(CompensationTerm.class);

        when(term.getPayMode())
                .thenReturn("HOURLY");

        when(term.getCurrencyCode())
                .thenReturn("RUB");

        when(term.getEffectiveFrom())
                .thenReturn(month);

        when(term.getHourlyRateMinor())
                .thenReturn(100_000L);

        when(term.getMonthlySalaryMinor())
                .thenReturn(null);

        when(
                compensationTerms
                        .findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                                user,
                                month
                        )
        ).thenReturn(
                Optional.of(term)
        );

        ProductionCalendarMonthDto production =
                new ProductionCalendarMonthDto(
                        "2026-08",
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        false,
                        List.of()
                );

        when(
                productionCalendar.month(
                        user,
                        "2026-08"
                )
        ).thenReturn(
                production
        );

        PayrollSourceSnapshot source =
                new PayrollSourceSnapshot(
                        month,
                        LocalDate.of(
                                2026,
                                8,
                                31
                        ),
                        480,
                        480,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        480,
                        480,
                        List.of()
                );

        when(
                timeCompensation.payrollSource(
                        user,
                        month,
                        LocalDate.of(
                                2026,
                                8,
                                31
                        )
                )
        ).thenReturn(
                source
        );

        when(
                adjustments
                        .findByOwnerAndPeriodMonthOrderByIdAsc(
                                user,
                                month
                        )
        ).thenReturn(
                List.of()
        );

        String fingerprint =
                "a".repeat(
                        64
                );

        SettlementLine line =
                new SettlementLine(
                        10L,
                        LocalDate.of(
                                2026,
                                8,
                                20
                        ),
                        "RUB",
                        60,
                        100_000L,
                        50_000L,
                        150_000L,
                        fingerprint
                );

        when(
                settlementPricing.preview(
                        user,
                        YearMonth.of(
                                2026,
                                8
                        ),
                        "RUB"
                )
        ).thenReturn(
                new SettlementPreview(
                        YearMonth.of(
                                2026,
                                8
                        ),
                        true,
                        null,
                        null,
                        1,
                        60,
                        100_000L,
                        50_000L,
                        150_000L,
                        fingerprint,
                        List.of(line)
                )
        );

        when(
                calculation.calculate(
                        term,
                        source,
                        0
                )
        ).thenReturn(
                new Result(
                        "HOURLY",
                        100_000L,
                        null,
                        100_000L,
                        0,
                        0,
                        800_000L
                )
        );

        PayrollSettings legacySettings =
                mock(PayrollSettings.class);

        when(
                settings.findByOwner(
                        user
                )
        ).thenReturn(
                Optional.of(legacySettings)
        );

        when(
                snapshots
                        .findFirstByOwnerAndPeriodMonthOrderByRevisionDesc(
                                user,
                                month
                        )
        ).thenReturn(
                Optional.empty()
        );

        when(
                snapshots.saveAndFlush(
                        any(PayrollSnapshot.class)
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        PayrollSnapshotDto result =
                payroll.calculate(
                        user,
                        "2026-08"
                );

        assertEquals(
                1,
                result.revision()
        );

        assertEquals(
                1,
                result.settlementCount()
        );

        assertEquals(
                60,
                result.settlementMinutes()
        );

        assertEquals(
                100_000L,
                result.settlementBasePayMinor()
        );

        assertEquals(
                50_000L,
                result.settlementPremiumPayMinor()
        );

        assertEquals(
                150_000L,
                result.settlementPayMinor()
        );

        assertEquals(
                fingerprint,
                result.settlementPricingFingerprint()
        );

        assertEquals(
                970_000L,
                result.totalPayMinor()
        );

        assertEquals(
                64,
                result.calculationHash()
                        .length()
        );

        ArgumentCaptor<PayrollSnapshot> captor =
                ArgumentCaptor.forClass(
                        PayrollSnapshot.class
                );

        verify(snapshots)
                .saveAndFlush(
                        captor.capture()
                );

        PayrollSnapshot stored =
                captor.getValue();

        assertEquals(
                60,
                stored.getOrdinaryPremiumMinutes()
        );

        assertEquals(
                100_000L,
                stored.getOrdinaryPremiumReferenceBasePayMinor()
        );

        assertEquals(
                20_000L,
                stored.getOrdinaryPremiumPayMinor()
        );

        assertEquals(
                "b".repeat(64),
                stored.getOrdinaryPremiumPricingFingerprint()
        );

        assertEquals(
                fingerprint,
                stored.getSettlementPricingFingerprint()
        );

        assertEquals(
                150_000L,
                stored.getSettlementPayMinor()
        );

        assertEquals(
                970_000L,
                stored.getTotalPayMinor()
        );
    }
}
