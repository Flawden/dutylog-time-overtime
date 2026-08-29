package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.daniil.shifts.model.AppUser;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static ru.daniil.shifts.service.AverageEarningsBonusP15Formula.WorkMeasureUnit.WORKING_DAYS;
import static ru.daniil.shifts.service.AverageEarningsLegalPolicy.AbsenceTreatment.EXCLUDE_OTHER_RELEASE_FROM_WORK;
import static ru.daniil.shifts.service.AverageEarningsLegalPolicy.LegalBasis.PP_540_P5_E;
import static ru.daniil.shifts.service.AverageEarningsParagraph6ReferenceResolver.BlockingStage.PARAGRAPH_6_AUTHORITY;
import static ru.daniil.shifts.service.AverageEarningsParagraph6ReferenceResolver.BlockingStage.PRIMARY_AUTHORITY;
import static ru.daniil.shifts.service.AverageEarningsParagraph6ReferenceResolver.FallbackReason.ENTIRE_REFERENCE_PERIOD_PARAGRAPH_5_EXCLUDED;
import static ru.daniil.shifts.service.AverageEarningsParagraph6ReferenceResolver.FallbackReason.NO_ACTUALLY_WORKED_TIME;
import static ru.daniil.shifts.service.AverageEarningsParagraph6ReferenceResolver.FallbackReason.NO_FACTUALLY_ACCRUED_WAGE;
import static ru.daniil.shifts.service.AverageEarningsParagraph6ReferenceResolver.Selection.PARAGRAPH_6_EXHAUSTED;
import static ru.daniil.shifts.service.AverageEarningsParagraph6ReferenceResolver.Selection.PARAGRAPH_6_PRECEDING;
import static ru.daniil.shifts.service.AverageEarningsParagraph6ReferenceResolver.Selection.PRIMARY;

@ExtendWith(MockitoExtension.class)
class AverageEarningsParagraph6ReferenceResolverTest {

    private static final LocalDate EVENT = LocalDate.of(2026, 9, 10);
    private static final YearMonth THROUGH = YearMonth.of(2026, 10);
    private static final AverageEarningsReferenceWindow PRIMARY_WINDOW =
            AverageEarningsReferenceWindow.primary(EVENT);
    private static final AverageEarningsReferenceWindow PRECEDING_WINDOW =
            PRIMARY_WINDOW.precedingEqual();

    @Mock
    private AverageEarningsNumeratorCalculationService numerator;

    private AppUser user;
    private AverageEarningsParagraph6ReferenceResolver service;

    @BeforeEach
    void setUp() {
        user = mock(AppUser.class);
        service = new AverageEarningsParagraph6ReferenceResolver(numerator);
    }

    @Test
    void primaryRemainsAuthoritativeWhenAccruedWageAndWorkedTimeExist() {
        var primary = readyNumerator(PRIMARY_WINDOW, true, 120L, List.of());
        when(numerator.calculate(user, EVENT, THROUGH, List.of())).thenReturn(primary);

        var result = service.resolve(user, EVENT, THROUGH, List.of());

        assertTrue(result.ready());
        assertEquals(PRIMARY, result.selection());
        assertEquals(PRIMARY_WINDOW, result.selectedEvidence().window());
        assertFalse(result.primaryEvidence().requiresFallback());
        verify(numerator, never()).calculate(
                eq(user), eq(EVENT), any(AverageEarningsReferenceWindow.class), eq(THROUGH), anyList()
        );
    }

    @Test
    void noFactuallyAccruedWageSelectsOnePrecedingEqualPeriod() {
        doReturn(readyNumerator(PRIMARY_WINDOW, false, 120L, List.of()))
                .when(numerator)
                .calculate(user, EVENT, THROUGH, List.of());
        doReturn(readyNumerator(PRECEDING_WINDOW, true, 100L, List.of()))
                .when(numerator)
                .calculate(user, EVENT, PRECEDING_WINDOW, THROUGH, List.of());

        var result = service.resolve(user, EVENT, THROUGH, List.of());

        assertEquals(PARAGRAPH_6_PRECEDING, result.selection());
        assertEquals(List.of(NO_FACTUALLY_ACCRUED_WAGE),
                result.primaryEvidence().fallbackReasons());
        assertEquals(PRECEDING_WINDOW, result.selectedEvidence().window());
    }

    @Test
    void noActuallyWorkedTimeSelectsOnePrecedingEqualPeriod() {
        doReturn(readyNumerator(PRIMARY_WINDOW, true, 0L, List.of()))
                .when(numerator)
                .calculate(user, EVENT, THROUGH, List.of());
        doReturn(readyNumerator(PRECEDING_WINDOW, true, 90L, List.of()))
                .when(numerator)
                .calculate(user, EVENT, PRECEDING_WINDOW, THROUGH, List.of());

        var result = service.resolve(user, EVENT, THROUGH, List.of());

        assertEquals(PARAGRAPH_6_PRECEDING, result.selection());
        assertTrue(result.primaryEvidence().fallbackReasons().contains(NO_ACTUALLY_WORKED_TIME));
    }

    @Test
    void noWageAndNoWorkExposeBothFactualReasons() {
        doReturn(readyNumerator(PRIMARY_WINDOW, false, 0L, List.of()))
                .when(numerator)
                .calculate(user, EVENT, THROUGH, List.of());
        doReturn(readyNumerator(PRECEDING_WINDOW, true, 10L, List.of()))
                .when(numerator)
                .calculate(user, EVENT, PRECEDING_WINDOW, THROUGH, List.of());

        var result = service.resolve(user, EVENT, THROUGH, List.of());

        assertEquals(List.of(NO_FACTUALLY_ACCRUED_WAGE, NO_ACTUALLY_WORKED_TIME),
                result.primaryEvidence().fallbackReasons());
    }

    @Test
    void wholeParagraph5PeriodIsExplicitAuditReason() {
        var exclusions = List.of(exclusion(
                PRIMARY_WINDOW.referenceFromDate(),
                PRIMARY_WINDOW.referenceToDate()
        ));
        doReturn(readyNumerator(PRIMARY_WINDOW, true, 0L, exclusions))
                .when(numerator)
                .calculate(user, EVENT, THROUGH, List.of());
        doReturn(readyNumerator(PRECEDING_WINDOW, true, 10L, List.of()))
                .when(numerator)
                .calculate(user, EVENT, PRECEDING_WINDOW, THROUGH, List.of());

        var result = service.resolve(user, EVENT, THROUGH, List.of());

        assertTrue(result.primaryEvidence().wholePeriodParagraph5Excluded());
        assertTrue(result.primaryEvidence().fallbackReasons()
                .contains(ENTIRE_REFERENCE_PERIOD_PARAGRAPH_5_EXCLUDED));
    }

    @Test
    void partialParagraph5ExclusionDoesNotForceFallbackWhenWageAndWorkExist() {
        var exclusions = List.of(exclusion(
                PRIMARY_WINDOW.referenceFromDate(),
                PRIMARY_WINDOW.referenceFromDate().plusDays(10)
        ));
        doReturn(readyNumerator(PRIMARY_WINDOW, true, 20L, exclusions))
                .when(numerator)
                .calculate(user, EVENT, THROUGH, List.of());

        var result = service.resolve(user, EVENT, THROUGH, List.of());

        assertEquals(PRIMARY, result.selection());
        assertFalse(result.primaryEvidence().wholePeriodParagraph5Excluded());
        assertTrue(result.primaryEvidence().fallbackReasons().isEmpty());
    }

    @Test
    void overlappingParagraph5RangesCanProveWholePeriodCoverage() {
        LocalDate split = PRIMARY_WINDOW.referenceFromDate().plusMonths(6);
        var exclusions = List.of(
                exclusion(PRIMARY_WINDOW.referenceFromDate(), split),
                exclusion(split.minusDays(2), PRIMARY_WINDOW.referenceToDate())
        );
        doReturn(readyNumerator(PRIMARY_WINDOW, true, 0L, exclusions))
                .when(numerator)
                .calculate(user, EVENT, THROUGH, List.of());
        doReturn(readyNumerator(PRECEDING_WINDOW, true, 10L, List.of()))
                .when(numerator)
                .calculate(user, EVENT, PRECEDING_WINDOW, THROUGH, List.of());

        var result = service.resolve(user, EVENT, THROUGH, List.of());

        assertTrue(result.primaryEvidence().wholePeriodParagraph5Excluded());
    }

    @Test
    void gapBetweenParagraph5RangesDoesNotClaimWholePeriodCoverage() {
        LocalDate split = PRIMARY_WINDOW.referenceFromDate().plusMonths(6);
        var exclusions = List.of(
                exclusion(PRIMARY_WINDOW.referenceFromDate(), split.minusDays(2)),
                exclusion(split, PRIMARY_WINDOW.referenceToDate())
        );
        doReturn(readyNumerator(PRIMARY_WINDOW, true, 0L, exclusions))
                .when(numerator)
                .calculate(user, EVENT, THROUGH, List.of());
        doReturn(readyNumerator(PRECEDING_WINDOW, true, 10L, List.of()))
                .when(numerator)
                .calculate(user, EVENT, PRECEDING_WINDOW, THROUGH, List.of());

        var result = service.resolve(user, EVENT, THROUGH, List.of());

        assertFalse(result.primaryEvidence().wholePeriodParagraph5Excluded());
    }

    @Test
    void precedingPeriodWithNoWageIsMarkedExhaustedInsteadOfScanningAgain() {
        doReturn(readyNumerator(PRIMARY_WINDOW, false, 100L, List.of()))
                .when(numerator)
                .calculate(user, EVENT, THROUGH, List.of());
        doReturn(readyNumerator(PRECEDING_WINDOW, false, 100L, List.of()))
                .when(numerator)
                .calculate(user, EVENT, PRECEDING_WINDOW, THROUGH, List.of());

        var result = service.resolve(user, EVENT, THROUGH, List.of());

        assertEquals(PARAGRAPH_6_EXHAUSTED, result.selection());
        assertTrue(result.selectedEvidence().requiresFallback());
        verify(numerator).calculate(user, EVENT, THROUGH, List.of());
        verify(numerator).calculate(user, EVENT, PRECEDING_WINDOW, THROUGH, List.of());
        verifyNoMoreInteractions(numerator);
    }

    @Test
    void precedingPeriodWithNoWorkIsMarkedExhaustedInsteadOfScanningAgain() {
        doReturn(readyNumerator(PRIMARY_WINDOW, false, 50L, List.of()))
                .when(numerator)
                .calculate(user, EVENT, THROUGH, List.of());
        doReturn(readyNumerator(PRECEDING_WINDOW, true, 0L, List.of()))
                .when(numerator)
                .calculate(user, EVENT, PRECEDING_WINDOW, THROUGH, List.of());

        var result = service.resolve(user, EVENT, THROUGH, List.of());

        assertEquals(PARAGRAPH_6_EXHAUSTED, result.selection());
        assertTrue(result.selectedEvidence().fallbackReasons().contains(NO_ACTUALLY_WORKED_TIME));
    }

    @Test
    void precedingPeriodPreservesRealLegalEventDate() {
        doReturn(readyNumerator(PRIMARY_WINDOW, false, 100L, List.of()))
                .when(numerator)
                .calculate(user, EVENT, THROUGH, List.of());
        doReturn(readyNumerator(PRECEDING_WINDOW, true, 100L, List.of()))
                .when(numerator)
                .calculate(user, EVENT, PRECEDING_WINDOW, THROUGH, List.of());

        service.resolve(user, EVENT, THROUGH, List.of());

        verify(numerator).calculate(user, EVENT, PRECEDING_WINDOW, THROUGH, List.of());
    }

    @Test
    void precedingPeriodIsExactlyTheTwelveMonthsImmediatelyBeforePrimary() {
        doReturn(readyNumerator(PRIMARY_WINDOW, false, 100L, List.of()))
                .when(numerator)
                .calculate(user, EVENT, THROUGH, List.of());
        doReturn(readyNumerator(PRECEDING_WINDOW, true, 100L, List.of()))
                .when(numerator)
                .calculate(user, EVENT, PRECEDING_WINDOW, THROUGH, List.of());

        var result = service.resolve(user, EVENT, THROUGH, List.of());

        assertEquals(PRIMARY_WINDOW.referenceFrom().minusMonths(12),
                result.selectedEvidence().window().referenceFrom());
        assertEquals(PRIMARY_WINDOW.referenceTo().minusMonths(12),
                result.selectedEvidence().window().referenceTo());
        assertEquals(PRIMARY_WINDOW.eventMonth(), result.selectedEvidence().window().eventMonth());
    }

    @Test
    void primaryBlockerPropagatesWithoutAttemptingParagraph6() {
        var blocked = blockedNumerator("PRIMARY_FACT_BLOCKED", YearMonth.of(2026, 1));
        when(numerator.calculate(user, EVENT, THROUGH, List.of())).thenReturn(blocked);

        var result = service.resolve(user, EVENT, THROUGH, List.of());

        assertFalse(result.ready());
        assertEquals(PRIMARY_AUTHORITY, result.blockingStage());
        assertEquals("PRIMARY_FACT_BLOCKED", result.blockingReason());
        verify(numerator, never()).calculate(
                eq(user), eq(EVENT), any(AverageEarningsReferenceWindow.class), eq(THROUGH), anyList()
        );
    }

    @Test
    void precedingBlockerPropagatesWithoutInventingParagraph7Or8() {
        doReturn(readyNumerator(PRIMARY_WINDOW, false, 100L, List.of()))
                .when(numerator)
                .calculate(user, EVENT, THROUGH, List.of());
        doReturn(blockedNumerator("P6_FACT_BLOCKED", YearMonth.of(2025, 2)))
                .when(numerator)
                .calculate(user, EVENT, PRECEDING_WINDOW, THROUGH, List.of());

        var result = service.resolve(user, EVENT, THROUGH, List.of());

        assertFalse(result.ready());
        assertEquals(PARAGRAPH_6_AUTHORITY, result.blockingStage());
        assertEquals("P6_FACT_BLOCKED", result.blockingReason());
    }

    @Test
    void primaryAuthorityWindowMismatchBlocksBeforeFallbackSelection() {
        var mismatch = authorityOnlyResolution(PRIMARY_WINDOW.precedingEqual(), true);
        when(numerator.calculate(user, EVENT, THROUGH, List.of())).thenReturn(mismatch);

        var result = service.resolve(user, EVENT, THROUGH, List.of());

        assertFalse(result.ready());
        assertEquals(PRIMARY_AUTHORITY, result.blockingStage());
        assertEquals(AverageEarningsParagraph6ReferenceResolver.AUTHORITY_WINDOW_MISMATCH,
                result.blockingReason());
    }

    @Test
    void precedingAuthorityWindowMismatchBlocks() {
        doReturn(readyNumerator(PRIMARY_WINDOW, false, 100L, List.of()))
                .when(numerator)
                .calculate(user, EVENT, THROUGH, List.of());
        var mismatch = authorityOnlyResolution(PRIMARY_WINDOW, true);
        when(numerator.calculate(user, EVENT, PRECEDING_WINDOW, THROUGH, List.of()))
                .thenReturn(mismatch);

        var result = service.resolve(user, EVENT, THROUGH, List.of());

        assertFalse(result.ready());
        assertEquals(PARAGRAPH_6_AUTHORITY, result.blockingStage());
        assertEquals(AverageEarningsParagraph6ReferenceResolver.AUTHORITY_WINDOW_MISMATCH,
                result.blockingReason());
    }

    @Test
    void noPayrollProofsAreForwardedToBothAuthoritiesUnchanged() {
        List<YearMonth> proof = List.of(YearMonth.of(2024, 10));
        doReturn(readyNumerator(PRIMARY_WINDOW, false, 100L, List.of()))
                .when(numerator)
                .calculate(user, EVENT, THROUGH, proof);
        doReturn(readyNumerator(PRECEDING_WINDOW, true, 100L, List.of()))
                .when(numerator)
                .calculate(user, EVENT, PRECEDING_WINDOW, THROUGH, proof);

        service.resolve(user, EVENT, THROUGH, proof);

        verify(numerator).calculate(user, EVENT, THROUGH, proof);
        verify(numerator).calculate(user, EVENT, PRECEDING_WINDOW, THROUGH, proof);
    }

    @Test
    void finalNumeratorAmountIsNotUsedAsParagraph6EligibilityEvidence() {
        var ready = readyNumerator(PRIMARY_WINDOW, true, 100L, List.of());
        when(numerator.calculate(user, EVENT, THROUGH, List.of())).thenReturn(ready);

        var result = service.resolve(user, EVENT, THROUGH, List.of());

        assertEquals(PRIMARY, result.selection());
        verify(ready, never()).numeratorAmountMinor();
    }

    @Test
    void nullNumeratorAuthorityFailsHardInsteadOfGuessingFallback() {
        when(numerator.calculate(user, EVENT, THROUGH, List.of())).thenReturn(null);

        assertThrows(
                NullPointerException.class,
                () -> service.resolve(user, EVENT, THROUGH, List.of())
        );
    }

    @Test
    void unsupportedLegalEventDateFailsBeforeReadingFacts() {
        LocalDate unsupported = LocalDate.of(2032, 1, 10);

        assertThrows(
                UnsupportedOperationException.class,
                () -> service.resolve(user, unsupported, YearMonth.of(2032, 2), List.of())
        );
        verifyNoInteractions(numerator);
    }

    @Test
    void nullInputsFailFast() {
        assertThrows(NullPointerException.class,
                () -> service.resolve(null, EVENT, THROUGH, List.of()));
        assertThrows(NullPointerException.class,
                () -> service.resolve(user, null, THROUGH, List.of()));
        assertThrows(NullPointerException.class,
                () -> service.resolve(user, EVENT, null, List.of()));
        assertThrows(NullPointerException.class,
                () -> service.resolve(user, EVENT, THROUGH, null));
    }

    private AverageEarningsNumeratorCalculationService.Resolution readyNumerator(
            AverageEarningsReferenceWindow window,
            boolean wagePresent,
            long workedUnits,
            List<AverageEarningsBonusP15ReferenceCompletenessService.Paragraph5Exclusion> exclusions
    ) {
        var result = mock(AverageEarningsNumeratorCalculationService.Resolution.class);
        var facts = mock(AverageEarningsNumeratorFactsService.Resolution.class);
        var p15 = mock(AverageEarningsBonusP15CalculationPipelineService.Resolution.class);
        var completeness = mock(AverageEarningsBonusP15ReferenceCompletenessService.Resolution.class);

        when(result.ready()).thenReturn(true);
        when(result.eventDate()).thenReturn(EVENT);
        when(result.eventMonth()).thenReturn(window.eventMonth());
        when(result.referenceFrom()).thenReturn(window.referenceFrom());
        lenient().when(result.referenceTo()).thenReturn(window.referenceTo());
        lenient().when(result.discoveryThroughMonth()).thenReturn(THROUGH);
        when(result.numeratorFacts()).thenReturn(facts);
        when(result.p15()).thenReturn(p15);

        when(facts.ordinaryCandidateAmountMinor()).thenReturn(wagePresent ? 100L : 0L);
        lenient().when(facts.premiumSpecialAmountMinor()).thenReturn(0L);

        when(p15.referenceCompleteness()).thenReturn(completeness);
        when(completeness.referenceWorkedTime()).thenReturn(
                new AverageEarningsBonusP15Formula.ReferenceWorkedTimeFact(
                        WORKING_DAYS,
                        workedUnits,
                        Math.max(1L, workedUnits)
                )
        );
        when(completeness.paragraph5Exclusions()).thenReturn(exclusions);

        return result;
    }

    private AverageEarningsNumeratorCalculationService.Resolution authorityOnlyResolution(
            AverageEarningsReferenceWindow window,
            boolean ready
    ) {
        var result = mock(AverageEarningsNumeratorCalculationService.Resolution.class);
        when(result.ready()).thenReturn(ready);
        when(result.eventDate()).thenReturn(EVENT);
        when(result.eventMonth()).thenReturn(window.eventMonth());
        when(result.referenceFrom()).thenReturn(window.referenceFrom());
        lenient().when(result.referenceTo()).thenReturn(window.referenceTo());
        lenient().when(result.discoveryThroughMonth()).thenReturn(THROUGH);
        return result;
    }

    private AverageEarningsNumeratorCalculationService.Resolution blockedNumerator(
            String reason,
            YearMonth period
    ) {
        var result = mock(AverageEarningsNumeratorCalculationService.Resolution.class);
        when(result.ready()).thenReturn(false);
        when(result.blockingReason()).thenReturn(reason);
        when(result.blockingPeriod()).thenReturn(period);
        return result;
    }

    private AverageEarningsBonusP15ReferenceCompletenessService.Paragraph5Exclusion exclusion(
            LocalDate from,
            LocalDate to
    ) {
        return new AverageEarningsBonusP15ReferenceCompletenessService.Paragraph5Exclusion(
                1L,
                "UNPAID",
                EXCLUDE_OTHER_RELEASE_FROM_WORK,
                PP_540_P5_E,
                from,
                to,
                null
        );
    }
}
