package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.CompensationComponent;
import ru.daniil.shifts.model.CompensationComponentVersion;
import ru.daniil.shifts.model.CompensationComponentVersion.CalculationBase;
import ru.daniil.shifts.model.CompensationComponentVersion.CalculationType;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.service.CompensationComponentCalculationService.Projection;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AverageEarningsParagraph7PreEventHarmfulCompensationServiceTest {
    private AppUser user;
    private CompensationComponentResolverService resolver;
    private CompensationComponentCalculationService calculator;
    private AverageEarningsParagraph7PreEventHarmfulCompensationService service;

    @BeforeEach
    void setUp() {
        user = mock(AppUser.class);
        resolver = mock(CompensationComponentResolverService.class);
        calculator = spy(new CompensationComponentCalculationService());
        service = new AverageEarningsParagraph7PreEventHarmfulCompensationService(
                resolver,
                calculator
        );
    }

    @Test
    void constructorRejectsMissingResolver() {
        assertThrows(
                NullPointerException.class,
                () -> new AverageEarningsParagraph7PreEventHarmfulCompensationService(
                        null,
                        calculator
                )
        );
    }

    @Test
    void constructorRejectsMissingCanonicalCalculator() {
        assertThrows(
                NullPointerException.class,
                () -> new AverageEarningsParagraph7PreEventHarmfulCompensationService(
                        resolver,
                        null
                )
        );
    }

    @Test
    void nullUserRejected() {
        assertThrows(
                NullPointerException.class,
                () -> service.resolve(null, workedOrdinary(LocalDate.of(2026, 8, 3), 100_000L))
        );
    }

    @Test
    void nullOrdinaryPremiumRejected() {
        assertThrows(
                NullPointerException.class,
                () -> service.resolve(user, null)
        );
    }

    @Test
    void blockedOrdinaryPremiumRejectedBeforeComponentResolution() {
        LocalDate eventDate = LocalDate.of(2026, 8, 3);
        var semantic = workedSemantic(eventDate, 100_000L);
        var blocked = new AverageEarningsParagraph7PreEventOrdinaryPremiumService.Resolution(
                eventDate,
                eventDate.withDayOfMonth(1),
                eventDate,
                false,
                "ORDINARY_BLOCK",
                "blocked",
                "source",
                semantic,
                0,
                0L,
                null,
                List.of(),
                List.of()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.resolve(user, blocked)
        );
        verifyNoInteractions(resolver, calculator);
    }

    @Test
    void nestedAuthorityWindowMismatchBlocksBeforeComponentResolution() {
        LocalDate authorityEvent = LocalDate.of(2026, 8, 3);
        LocalDate ordinaryEvent = LocalDate.of(2026, 8, 4);
        var semantic = workedSemantic(authorityEvent, 100_000L);
        var ordinary = new AverageEarningsParagraph7PreEventOrdinaryPremiumService.Resolution(
                ordinaryEvent,
                ordinaryEvent.withDayOfMonth(1),
                ordinaryEvent,
                true,
                null,
                null,
                null,
                semantic,
                60,
                0L,
                "RUB",
                List.of(),
                List.of()
        );

        var result = service.resolve(user, ordinary);

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventHarmfulCompensationService.AUTHORITY_WINDOW_MISMATCH,
                result.blockingReason()
        );
        assertEquals(0L, result.harmfulAmountMinor());
        assertTrue(result.lines().isEmpty());
        verifyNoInteractions(resolver, calculator);
    }

    @Test
    void noWorkedTimeReturnsReadyZeroWithoutComponentResolution() {
        LocalDate eventDate = LocalDate.of(2026, 8, 3);

        var result = service.resolve(user, noWorkOrdinary(eventDate));

        assertTrue(result.ready());
        assertEquals(0L, result.harmfulAmountMinor());
        assertNull(result.currencyCode());
        assertFalse(result.harmfulMoneyPresent());
        assertTrue(result.lines().isEmpty());
        verifyNoInteractions(resolver, calculator);
    }

    @Test
    void noEnabledHarmfulConfigurationIsProvenZeroForWorkedRange() {
        LocalDate eventDate = LocalDate.of(2026, 8, 3);
        doReturn(List.of(otherVersion(1L, 11L)))
                .when(resolver)
                .resolve(user, YearMonth.of(2026, 8));

        var result = service.resolve(user, workedOrdinary(eventDate, 100_000L));

        assertTrue(result.ready());
        assertEquals("RUB", result.currencyCode());
        assertEquals(0L, result.harmfulAmountMinor());
        assertTrue(result.lines().isEmpty());
        verifyNoInteractions(calculator);
    }

    @Test
    void disabledHarmfulConfigurationDoesNotCreateMoney() {
        LocalDate eventDate = LocalDate.of(2026, 8, 3);
        doReturn(List.of(harmfulVersion(
                        1L,
                        11L,
                        false,
                        CalculationType.PERCENT_OF_BASE,
                        CalculationBase.EARNED_BASE_PAY,
                        400
                )))
                .when(resolver)
                .resolve(user, YearMonth.of(2026, 8));

        var result = service.resolve(user, workedOrdinary(eventDate, 100_000L));

        assertTrue(result.ready());
        assertEquals(0L, result.harmfulAmountMinor());
        verifyNoInteractions(calculator);
    }

    @Test
    void harmfulPercentOfPreEventEarnedBaseUsesCanonicalCalculator() {
        LocalDate eventDate = LocalDate.of(2026, 8, 3);
        doReturn(List.of(harmfulVersion(
                        1L,
                        11L,
                        true,
                        CalculationType.PERCENT_OF_BASE,
                        CalculationBase.EARNED_BASE_PAY,
                        400
                )))
                .when(resolver)
                .resolve(user, YearMonth.of(2026, 8));

        var result = service.resolve(user, workedOrdinary(eventDate, 100_000L));

        assertTrue(result.ready());
        assertEquals(4_000L, result.harmfulAmountMinor());
        assertEquals("RUB", result.currencyCode());
        assertEquals(1, result.lines().size());
        assertEquals(PayrollEarningKind.HARMFUL_CONDITIONS, result.lines().get(0).earningKind());
        assertEquals(100_000L, result.lines().get(0).referenceBaseMinor());
        verify(calculator).calculate(any(), anyList());
    }

    @Test
    void multipleHarmfulComponentsPreserveNativePerComponentRoundingAndSum() {
        LocalDate eventDate = LocalDate.of(2026, 8, 3);
        doReturn(List.of(
                        harmfulVersion(1L, 11L, true, CalculationType.PERCENT_OF_BASE, CalculationBase.EARNED_BASE_PAY, 400),
                        harmfulVersion(2L, 12L, true, CalculationType.PERCENT_OF_BASE, CalculationBase.EARNED_BASE_PAY, 150)
                ))
                .when(resolver)
                .resolve(user, YearMonth.of(2026, 8));

        var result = service.resolve(user, workedOrdinary(eventDate, 100_001L));

        assertTrue(result.ready());
        assertEquals(5_500L, result.harmfulAmountMinor());
        assertEquals(2, result.lines().size());
        assertEquals(
                result.harmfulAmountMinor(),
                result.lines().stream().mapToLong(CompensationComponentCalculationService.CalculatedLine::amountMinor).sum()
        );
    }

    @Test
    void halfMinorBoundaryMatchesCanonicalCompensationCalculator() {
        LocalDate eventDate = LocalDate.of(2026, 8, 3);
        doReturn(List.of(harmfulVersion(
                        1L,
                        11L,
                        true,
                        CalculationType.PERCENT_OF_BASE,
                        CalculationBase.EARNED_BASE_PAY,
                        5_000
                )))
                .when(resolver)
                .resolve(user, YearMonth.of(2026, 8));

        var result = service.resolve(user, workedOrdinary(eventDate, 1L));

        assertEquals(1L, result.harmfulAmountMinor());
        assertEquals(1L, result.lines().get(0).amountMinor());
    }

    @Test
    void workedTimeWithZeroBasePayProducesZeroWithoutFallbackMeaning() {
        LocalDate eventDate = LocalDate.of(2026, 8, 3);
        doReturn(List.of(harmfulVersion(
                        1L,
                        11L,
                        true,
                        CalculationType.PERCENT_OF_BASE,
                        CalculationBase.EARNED_BASE_PAY,
                        400
                )))
                .when(resolver)
                .resolve(user, YearMonth.of(2026, 8));

        var result = service.resolve(user, workedOrdinary(eventDate, 0L));

        assertTrue(result.ready());
        assertEquals(0L, result.harmfulAmountMinor());
        assertFalse(result.harmfulMoneyPresent());
        assertEquals(1, result.lines().size());
        assertEquals(0L, result.lines().get(0).referenceBaseMinor());
    }

    @Test
    void fixedAmountHarmfulBlocksInsteadOfProratingMonthlyMoney() {
        assertUnsafeShapeBlocks(
                CalculationType.FIXED_AMOUNT,
                null,
                null
        );
    }

    @Test
    void nominalSalaryHarmfulBlocksInsteadOfUsingWholeMonthSalary() {
        assertUnsafeShapeBlocks(
                CalculationType.PERCENT_OF_BASE,
                CalculationBase.NOMINAL_SALARY,
                400
        );
    }

    @Test
    void localEligibleHarmfulBlocksUntilItsOwnRangeBoundAuthorityExists() {
        assertUnsafeShapeBlocks(
                CalculationType.PERCENT_OF_BASE,
                CalculationBase.LOCAL_ELIGIBLE_EARNINGS,
                400
        );
    }

    @Test
    void oneUnsafeHarmfulComponentClearsOtherwiseCalculableMoney() {
        LocalDate eventDate = LocalDate.of(2026, 8, 3);
        doReturn(List.of(
                        harmfulVersion(1L, 11L, true, CalculationType.PERCENT_OF_BASE, CalculationBase.EARNED_BASE_PAY, 400),
                        harmfulVersion(2L, 12L, true, CalculationType.PERCENT_OF_BASE, CalculationBase.NOMINAL_SALARY, 100)
                ))
                .when(resolver)
                .resolve(user, YearMonth.of(2026, 8));

        var result = service.resolve(user, workedOrdinary(eventDate, 100_000L));

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventHarmfulCompensationService.CONFIGURATION_NOT_RANGE_BOUND,
                result.blockingReason()
        );
        assertEquals(0L, result.harmfulAmountMinor());
        assertTrue(result.lines().isEmpty());
        verifyNoInteractions(calculator);
    }

    @Test
    void resolverNullResultIsStructuralFailure() {
        LocalDate eventDate = LocalDate.of(2026, 8, 3);
        when(resolver.resolve(user, YearMonth.of(2026, 8)))
                .thenReturn(null);

        assertThrows(
                NullPointerException.class,
                () -> service.resolve(user, workedOrdinary(eventDate, 100_000L))
        );
    }

    @Test
    void resolverNullVersionIsStructuralFailure() {
        LocalDate eventDate = LocalDate.of(2026, 8, 3);
        when(resolver.resolve(user, YearMonth.of(2026, 8)))
                .thenReturn(java.util.Arrays.asList((CompensationComponentVersion) null));

        assertThrows(
                IllegalStateException.class,
                () -> service.resolve(user, workedOrdinary(eventDate, 100_000L))
        );
    }

    @Test
    void effectiveVersionAfterEventMonthBoundaryIsStructuralFailure() {
        LocalDate eventDate = LocalDate.of(2026, 8, 3);
        var version = harmfulVersion(
                1L,
                11L,
                true,
                CalculationType.PERCENT_OF_BASE,
                CalculationBase.EARNED_BASE_PAY,
                400
        );
        when(version.getEffectiveFrom()).thenReturn(LocalDate.of(2026, 9, 1));
        when(resolver.resolve(user, YearMonth.of(2026, 8))).thenReturn(List.of(version));

        assertThrows(
                IllegalStateException.class,
                () -> service.resolve(user, workedOrdinary(eventDate, 100_000L))
        );
    }

    @Test
    void invalidComponentRuleBecomesFailClosedBlocker() {
        LocalDate eventDate = LocalDate.of(2026, 8, 3);
        var version = harmfulVersion(
                1L,
                11L,
                true,
                CalculationType.PERCENT_OF_BASE,
                CalculationBase.EARNED_BASE_PAY,
                400
        );
        when(version.getDisplayName()).thenReturn("   ");
        when(resolver.resolve(user, YearMonth.of(2026, 8))).thenReturn(List.of(version));

        var result = service.resolve(user, workedOrdinary(eventDate, 100_000L));

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventHarmfulCompensationService.COMPONENT_INVALID,
                result.blockingReason()
        );
        assertEquals(0L, result.harmfulAmountMinor());
        assertTrue(result.lines().isEmpty());
        verifyNoInteractions(calculator);
    }

    @Test
    void canonicalCalculatorFailureBecomesFailClosedBlockerWithoutPartialMoney() {
        LocalDate eventDate = LocalDate.of(2026, 8, 3);
        var version = harmfulVersion(
                1L,
                11L,
                true,
                CalculationType.PERCENT_OF_BASE,
                CalculationBase.EARNED_BASE_PAY,
                400
        );
        when(resolver.resolve(user, YearMonth.of(2026, 8))).thenReturn(List.of(version));
        CompensationComponentCalculationService broken = mock(CompensationComponentCalculationService.class);
        when(broken.calculate(any(), anyList())).thenThrow(new IllegalArgumentException("broken"));
        var brokenService = new AverageEarningsParagraph7PreEventHarmfulCompensationService(
                resolver,
                broken
        );

        var result = brokenService.resolve(user, workedOrdinary(eventDate, 100_000L));

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventHarmfulCompensationService.CALCULATION_BLOCKED,
                result.blockingReason()
        );
        assertEquals(0L, result.harmfulAmountMinor());
        assertTrue(result.lines().isEmpty());
    }

    @Test
    void nonHarmfulGenericComponentsAreIgnoredInsteadOfInferredFromDisplayName() {
        LocalDate eventDate = LocalDate.of(2026, 8, 3);
        var other = otherVersion(1L, 11L);
        when(other.getDisplayName()).thenReturn("Вредность 4%");
        when(resolver.resolve(user, YearMonth.of(2026, 8))).thenReturn(List.of(other));

        var result = service.resolve(user, workedOrdinary(eventDate, 100_000L));

        assertTrue(result.ready());
        assertEquals(0L, result.harmfulAmountMinor());
        verifyNoInteractions(calculator);
    }

    private void assertUnsafeShapeBlocks(
            CalculationType type,
            CalculationBase base,
            Integer rateBps
    ) {
        LocalDate eventDate = LocalDate.of(2026, 8, 3);
        doReturn(List.of(harmfulVersion(
                        1L,
                        11L,
                        true,
                        type,
                        base,
                        rateBps
                )))
                .when(resolver)
                .resolve(user, YearMonth.of(2026, 8));

        var result = service.resolve(user, workedOrdinary(eventDate, 100_000L));

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventHarmfulCompensationService.CONFIGURATION_NOT_RANGE_BOUND,
                result.blockingReason()
        );
        assertEquals(0L, result.harmfulAmountMinor());
        assertNull(result.currencyCode());
        assertTrue(result.lines().isEmpty());
        verifyNoInteractions(calculator);
    }

    private CompensationComponentVersion harmfulVersion(
            long componentId,
            long versionId,
            boolean enabled,
            CalculationType type,
            CalculationBase base,
            Integer rateBps
    ) {
        CompensationComponent component = mock(CompensationComponent.class);
        when(component.getId()).thenReturn(componentId);
        CompensationComponentVersion version = mock(CompensationComponentVersion.class);
        when(version.getId()).thenReturn(versionId);
        when(version.getComponent()).thenReturn(component);
        when(version.getEffectiveFrom()).thenReturn(LocalDate.of(2026, 8, 1));
        when(version.getDisplayName()).thenReturn("Harmful conditions");
        when(version.getEarningKind()).thenReturn(PayrollEarningKind.HARMFUL_CONDITIONS);
        when(version.getCalculationType()).thenReturn(type);
        when(version.getCalculationBase()).thenReturn(base);
        when(version.getRateBps()).thenReturn(rateBps);
        if (type == CalculationType.FIXED_AMOUNT) {
            when(version.getAmountMinor()).thenReturn(10_000L);
            when(version.getCurrencyCode()).thenReturn("RUB");
        } else {
            when(version.getAmountMinor()).thenReturn(null);
            when(version.getCurrencyCode()).thenReturn(null);
        }
        when(version.isEnabled()).thenReturn(enabled);
        return version;
    }

    private CompensationComponentVersion otherVersion(long componentId, long versionId) {
        CompensationComponentVersion version = harmfulVersion(
                componentId,
                versionId,
                true,
                CalculationType.PERCENT_OF_BASE,
                CalculationBase.EARNED_BASE_PAY,
                400
        );
        when(version.getEarningKind()).thenReturn(PayrollEarningKind.MONTHLY_BONUS);
        return version;
    }

    private AverageEarningsParagraph7PreEventOrdinaryPremiumService.Resolution workedOrdinary(
            LocalDate eventDate,
            long basePayAmountMinor
    ) {
        return new AverageEarningsParagraph7PreEventOrdinaryPremiumService.Resolution(
                eventDate,
                eventDate.withDayOfMonth(1),
                eventDate,
                true,
                null,
                null,
                null,
                workedSemantic(eventDate, basePayAmountMinor),
                60,
                0L,
                "RUB",
                List.of(),
                List.of()
        );
    }

    private AverageEarningsParagraph7PreEventOrdinaryPremiumService.Resolution noWorkOrdinary(
            LocalDate eventDate
    ) {
        return new AverageEarningsParagraph7PreEventOrdinaryPremiumService.Resolution(
                eventDate,
                eventDate.withDayOfMonth(1),
                eventDate,
                true,
                null,
                null,
                null,
                noWorkSemantic(eventDate),
                0,
                0L,
                null,
                List.of(),
                List.of()
        );
    }

    private AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution workedSemantic(
            LocalDate eventDate,
            long basePayAmountMinor
    ) {
        LocalDate periodFrom = eventDate.withDayOfMonth(1);
        var work = AverageEarningsParagraph7PreEventWorkFactService.Resolution.ready(
                eventDate,
                periodFrom,
                eventDate,
                1,
                60L,
                List.of(
                        new AverageEarningsParagraph7PreEventWorkFactService.WorkedDayFact(
                                periodFrom,
                                60,
                                60,
                                60
                        )
                )
        );
        var authority = new AverageEarningsParagraph7PreEventBasePayAuthorityService.Resolution(
                eventDate,
                periodFrom,
                eventDate,
                true,
                null,
                null,
                work,
                periodFrom,
                "HOURLY",
                "RUB",
                6_000L,
                null,
                null,
                60L
        );
        var basePay = new AverageEarningsParagraph7PreEventBasePayFormula.Calculation(
                authority,
                basePayAmountMinor
        );
        return new AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution(
                eventDate,
                periodFrom,
                eventDate,
                true,
                null,
                null,
                basePay,
                List.of()
        );
    }

    private AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution noWorkSemantic(
            LocalDate eventDate
    ) {
        LocalDate periodFrom = eventDate.withDayOfMonth(1);
        var work = AverageEarningsParagraph7PreEventWorkFactService.Resolution.ready(
                eventDate,
                periodFrom,
                eventDate,
                0,
                0L,
                List.of()
        );
        var authority = new AverageEarningsParagraph7PreEventBasePayAuthorityService.Resolution(
                eventDate,
                periodFrom,
                eventDate,
                true,
                null,
                null,
                work,
                null,
                null,
                null,
                null,
                null,
                null,
                0L
        );
        var basePay = new AverageEarningsParagraph7PreEventBasePayFormula.Calculation(
                authority,
                0L
        );
        return new AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution(
                eventDate,
                periodFrom,
                eventDate,
                true,
                null,
                null,
                basePay,
                List.of()
        );
    }
}
