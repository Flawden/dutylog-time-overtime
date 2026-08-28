package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollEarningKind;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AverageEarningsNumeratorFactsServiceTest {

    @Mock
    private EmploymentHistoryService employment;

    @Mock
    private PayrollHistoricalSemanticEarningsService historical;

    private final AppUser user =
            mock(
                    AppUser.class
            );

    private AverageEarningsNumeratorFactsService service;

    @BeforeEach
    void setUp() {
        service =
                new AverageEarningsNumeratorFactsService(
                        employment,
                        historical
                );
    }

    @Test
    void provenPreEmploymentMonthsAreExplicitZeroAndDoNotRequireSnapshots() {
        LocalDate eventDate =
                LocalDate.of(
                        2026,
                        9,
                        10
                );

        YearMonth eventMonth =
                YearMonth.from(
                        eventDate
                );

        LocalDate referenceFrom =
                eventMonth.minusMonths(12).atDay(1);

        LocalDate referenceTo =
                eventMonth.minusMonths(1).atEndOfMonth();

        LocalDate employedFrom =
                LocalDate.of(
                        2026,
                        6,
                        15
                );

        when(
                employment.resolve(
                        user,
                        referenceFrom,
                        referenceTo
                )
        ).thenReturn(
                EmploymentHistoryService.Resolution.configured(
                        referenceFrom,
                        referenceTo,
                        List.of(
                                new EmploymentHistoryService.CoverageSlice(
                                        7L,
                                        employedFrom,
                                        null,
                                        employedFrom,
                                        referenceTo
                                )
                        )
                )
        );

        List<YearMonth> required =
                List.of(
                        YearMonth.of(2026, 6),
                        YearMonth.of(2026, 7),
                        YearMonth.of(2026, 8)
                );

        when(
                historical.resolveRequiredMonths(
                        user,
                        eventMonth,
                        required
                )
        ).thenReturn(
                readyHistory(
                        eventMonth,
                        required,
                        month -> List.of()
                )
        );

        var result =
                service.resolve(
                        user,
                        eventDate
                );

        assertTrue(
                result.ready()
        );

        assertEquals(
                12,
                result.months()
                        .size()
        );

        assertEquals(
                3L,
                result.months()
                        .stream()
                        .filter(
                                AverageEarningsNumeratorFactsService.MonthFact::employed
                        )
                        .count()
        );

        var may =
                result.months()
                        .stream()
                        .filter(month ->
                                YearMonth.of(2026, 5)
                                        .equals(
                                                month.period()
                                        )
                        )
                        .findFirst()
                        .orElseThrow();

        assertFalse(
                may.employed()
        );

        assertNull(
                may.snapshotRevision()
        );

        assertTrue(
                may.earnings()
                        .isEmpty()
        );

        var june =
                result.months()
                        .stream()
                        .filter(month ->
                                YearMonth.of(2026, 6)
                                        .equals(
                                                month.period()
                                        )
                        )
                        .findFirst()
                        .orElseThrow();

        assertTrue(
                june.employed()
        );

        assertEquals(
                employedFrom,
                june.employmentCoverage()
                        .get(0)
                        .overlapFrom()
        );

        verify(
                historical
        ).resolveRequiredMonths(
                user,
                eventMonth,
                required
        );
    }

    @Test
    void configuredHistoryWhollyOutsideReferenceWindowProducesTwelveZeroMonths() {
        LocalDate eventDate =
                LocalDate.of(
                        2026,
                        9,
                        10
                );

        YearMonth eventMonth =
                YearMonth.from(
                        eventDate
                );

        LocalDate referenceFrom =
                eventMonth.minusMonths(12).atDay(1);

        LocalDate referenceTo =
                eventMonth.minusMonths(1).atEndOfMonth();

        when(
                employment.resolve(
                        user,
                        referenceFrom,
                        referenceTo
                )
        ).thenReturn(
                EmploymentHistoryService.Resolution.configured(
                        referenceFrom,
                        referenceTo,
                        List.of()
                )
        );

        when(
                historical.resolveRequiredMonths(
                        user,
                        eventMonth,
                        List.of()
                )
        ).thenReturn(
                PayrollHistoricalSemanticEarningsService.RequiredResolution.ready(
                        eventMonth.minusMonths(12),
                        eventMonth.minusMonths(1),
                        null,
                        List.of(),
                        List.of()
                )
        );

        var result =
                service.resolve(
                        user,
                        eventDate
                );

        assertTrue(
                result.ready()
        );

        assertEquals(
                12L,
                result.months()
                        .stream()
                        .filter(month ->
                                !month.employed()
                        )
                        .count()
        );

        assertNull(
                result.currencyCode()
        );

        assertEquals(
                0L,
                result.ordinaryCandidateAmountMinor()
        );

        assertEquals(
                0L,
                result.premiumSpecialAmountMinor()
        );

        assertEquals(
                0L,
                result.excludedAmountMinor()
        );
    }

    @Test
    void unconfiguredEmploymentBlocksBeforeHistoricalPayrollLookup() {
        LocalDate eventDate =
                LocalDate.of(
                        2026,
                        9,
                        10
                );

        YearMonth eventMonth =
                YearMonth.from(
                        eventDate
                );

        LocalDate referenceFrom =
                eventMonth.minusMonths(12).atDay(1);

        LocalDate referenceTo =
                eventMonth.minusMonths(1).atEndOfMonth();

        when(
                employment.resolve(
                        user,
                        referenceFrom,
                        referenceTo
                )
        ).thenReturn(
                EmploymentHistoryService.Resolution.unconfigured(
                        referenceFrom,
                        referenceTo
                )
        );

        var result =
                service.resolve(
                        user,
                        eventDate
                );

        assertFalse(
                result.ready()
        );

        assertEquals(
                "AVERAGE_EARNINGS_EMPLOYMENT_HISTORY_UNCONFIGURED",
                result.blockingReason()
        );

        assertNull(
                result.blockingPeriod()
        );

        org.mockito.Mockito.verifyNoInteractions(
                historical
        );
    }

    @Test
    void ordinaryPremiumAndPreservedAverageMoneyStayInSeparateBucketsWithoutProvenanceInference() {
        LocalDate eventDate =
                LocalDate.of(
                        2026,
                        9,
                        10
                );

        YearMonth eventMonth =
                YearMonth.from(
                        eventDate
                );

        stubFullEmployment(
                eventMonth
        );

        List<YearMonth> required =
                twelveMonths(
                        eventMonth
                );

        YearMonth august =
                YearMonth.of(
                        2026,
                        8
                );

        when(
                historical.resolveRequiredMonths(
                        user,
                        eventMonth,
                        required
                )
        ).thenReturn(
                readyHistory(
                        eventMonth,
                        required,
                        month ->
                                august.equals(month)
                                        ? List.of(
                                        earning(
                                                PayrollEarningKind.BASE_PAY,
                                                6_054_800L
                                        ),
                                        earning(
                                                PayrollEarningKind.MONTHLY_BONUS,
                                                2_421_920L
                                        ),
                                        earning(
                                                PayrollEarningKind.VACATION_PAY,
                                                5_160_988L
                                        )
                                )
                                        : List.of()
                )
        );

        var result =
                service.resolve(
                        user,
                        eventDate
                );

        assertTrue(
                result.ready()
        );

        assertEquals(
                6_054_800L,
                result.ordinaryCandidateAmountMinor()
        );

        assertEquals(
                2_421_920L,
                result.premiumSpecialAmountMinor()
        );

        assertEquals(
                5_160_988L,
                result.excludedAmountMinor()
        );

        var augustFact =
                result.months()
                        .get(11);

        assertEquals(
                AverageEarningsLegalPolicy.EarningTreatment.ORDINARY_REMUNERATION,
                augustFact.earnings()
                        .get(0)
                        .treatment()
        );

        assertEquals(
                AverageEarningsLegalPolicy.EarningTreatment.PREMIUM_SPECIAL_RULE,
                augustFact.earnings()
                        .get(1)
                        .treatment()
        );

        assertEquals(
                AverageEarningsLegalPolicy.EarningTreatment.EXCLUDE_PRESERVED_AVERAGE,
                augustFact.earnings()
                        .get(2)
                        .treatment()
        );

        /*
         * Current production freeze normally has no earning/coverage period.
         * E1 must preserve that absence of provenance instead of inventing the
         * posting month or allocating money across absence dates.
         */
        assertNull(
                augustFact.earnings()
                        .get(0)
                        .earningPeriodFrom()
        );

        assertNull(
                augustFact.earnings()
                        .get(0)
                        .coverageFrom()
        );
    }

    @Test
    void unresolvedMedicalCompensationBlocksWholeNumeratorFactsResolution() {
        LocalDate eventDate =
                LocalDate.of(
                        2026,
                        9,
                        10
                );

        YearMonth eventMonth =
                YearMonth.from(
                        eventDate
                );

        stubFullEmployment(
                eventMonth
        );

        List<YearMonth> required =
                twelveMonths(
                        eventMonth
                );

        YearMonth blockedMonth =
                YearMonth.of(
                        2026,
                        4
                );

        when(
                historical.resolveRequiredMonths(
                        user,
                        eventMonth,
                        required
                )
        ).thenReturn(
                readyHistory(
                        eventMonth,
                        required,
                        month ->
                                blockedMonth.equals(month)
                                        ? List.of(
                                        earning(
                                                PayrollEarningKind.MEDICAL_COMPENSATION,
                                                50_000L
                                        )
                                )
                                        : List.of()
                )
        );

        var result =
                service.resolve(
                        user,
                        eventDate
                );

        assertFalse(
                result.ready()
        );

        assertEquals(
                "AVERAGE_EARNINGS_EARNING_LEGAL_TREATMENT_UNRESOLVED",
                result.blockingReason()
        );

        assertEquals(
                blockedMonth,
                result.blockingPeriod()
        );

        assertTrue(
                result.months()
                        .isEmpty()
        );
    }

    @Test
    void historicalSemanticBlockerPropagatesWithoutPartialNumeratorFacts() {
        LocalDate eventDate =
                LocalDate.of(
                        2026,
                        9,
                        10
                );

        YearMonth eventMonth =
                YearMonth.from(
                        eventDate
                );

        stubFullEmployment(
                eventMonth
        );

        List<YearMonth> required =
                twelveMonths(
                        eventMonth
                );

        YearMonth blockedMonth =
                YearMonth.of(
                        2026,
                        1
                );

        when(
                historical.resolveRequiredMonths(
                        user,
                        eventMonth,
                        required
                )
        ).thenReturn(
                PayrollHistoricalSemanticEarningsService.RequiredResolution.blocked(
                        eventMonth.minusMonths(12),
                        eventMonth.minusMonths(1),
                        "HISTORICAL_SEMANTIC_EARNINGS_MANIFEST_MISSING",
                        blockedMonth
                )
        );

        var result =
                service.resolve(
                        user,
                        eventDate
                );

        assertFalse(
                result.ready()
        );

        assertEquals(
                "HISTORICAL_SEMANTIC_EARNINGS_MANIFEST_MISSING",
                result.blockingReason()
        );

        assertEquals(
                blockedMonth,
                result.blockingPeriod()
        );

        assertTrue(
                result.months()
                        .isEmpty()
        );
    }

    private void stubFullEmployment(
            YearMonth eventMonth
    ) {
        LocalDate referenceFrom =
                eventMonth.minusMonths(12).atDay(1);

        LocalDate referenceTo =
                eventMonth.minusMonths(1).atEndOfMonth();

        when(
                employment.resolve(
                        user,
                        referenceFrom,
                        referenceTo
                )
        ).thenReturn(
                EmploymentHistoryService.Resolution.configured(
                        referenceFrom,
                        referenceTo,
                        List.of(
                                new EmploymentHistoryService.CoverageSlice(
                                        1L,
                                        LocalDate.of(2020, 1, 1),
                                        null,
                                        referenceFrom,
                                        referenceTo
                                )
                        )
                )
        );
    }

    private List<YearMonth> twelveMonths(
            YearMonth eventMonth
    ) {
        List<YearMonth> result =
                new ArrayList<>(
                        12
                );

        for (int offset = 12;
                offset >= 1;
                offset--) {
            result.add(
                    eventMonth.minusMonths(
                            offset
                    )
            );
        }

        return result;
    }

    private PayrollHistoricalSemanticEarningsService.RequiredResolution readyHistory(
            YearMonth eventMonth,
            List<YearMonth> required,
            java.util.function.Function<YearMonth, List<PayrollHistoricalSemanticEarningsService.HistoricalEarning>> earnings
    ) {
        List<PayrollHistoricalSemanticEarningsService.HistoricalMonth> months =
                required.stream()
                        .map(month ->
                                new PayrollHistoricalSemanticEarningsService.HistoricalMonth(
                                        month,
                                        1,
                                        "RUB",
                                        earnings.apply(
                                                month
                                        )
                                )
                        )
                        .toList();

        return PayrollHistoricalSemanticEarningsService.RequiredResolution.ready(
                eventMonth.minusMonths(12),
                eventMonth.minusMonths(1),
                required.isEmpty()
                        ? null
                        : "RUB",
                required,
                months
        );
    }

    private PayrollHistoricalSemanticEarningsService.HistoricalEarning earning(
            PayrollEarningKind kind,
            long amountMinor
    ) {
        return new PayrollHistoricalSemanticEarningsService.HistoricalEarning(
                kind,
                kind.phase(),
                amountMinor,
                null,
                null,
                null,
                null,
                null
        );
    }
}
