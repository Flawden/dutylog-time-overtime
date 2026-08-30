package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AverageEarningsOrderedFallbackResolverTest {

    private static final LocalDate EVENT = LocalDate.of(2026, 8, 20);

    @Test
    void primarySelectionStopsBeforeParagraph7AndParagraph8() {
        var p6 = p6Ready(AverageEarningsParagraph6ReferenceResolver.Selection.PRIMARY, EVENT);
        Supplier<AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution> p7 = mock(Supplier.class);
        Supplier<AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution> p8 = mock(Supplier.class);

        var resolved = AverageEarningsOrderedFallbackResolver.resolve(p6, p7, p8);

        assertTrue(resolved.ready());
        assertEquals(AverageEarningsOrderedFallbackResolver.Selection.PRIMARY_REFERENCE_PERIOD, resolved.selection());
        assertEquals(AverageEarningsReferenceWindow.primary(EVENT), resolved.selectedReferenceWindow());
        assertNull(resolved.paragraph7Authority());
        assertNull(resolved.paragraph8Authority());
        verifyNoInteractions(p7, p8);
    }

    @Test
    void paragraph6PrecedingSelectionStopsBeforeParagraph7AndParagraph8() {
        var p6 = p6Ready(AverageEarningsParagraph6ReferenceResolver.Selection.PARAGRAPH_6_PRECEDING, EVENT);
        Supplier<AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution> p7 = mock(Supplier.class);
        Supplier<AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution> p8 = mock(Supplier.class);

        var resolved = AverageEarningsOrderedFallbackResolver.resolve(p6, p7, p8);

        assertTrue(resolved.ready());
        assertEquals(AverageEarningsOrderedFallbackResolver.Selection.PARAGRAPH_6_PRECEDING_REFERENCE_PERIOD, resolved.selection());
        assertEquals(AverageEarningsReferenceWindow.primary(EVENT).precedingEqual(), resolved.selectedReferenceWindow());
        verifyNoInteractions(p7, p8);
    }

    @Test
    void paragraph6ExhaustedThenParagraph7WithWageAndWorkSelectsParagraph7WithoutParagraph8() {
        var p6 = p6Ready(AverageEarningsParagraph6ReferenceResolver.Selection.PARAGRAPH_6_EXHAUSTED, EVENT);
        var p7Resolution = p7Ready(EVENT, true, true);
        Supplier<AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution> p7 = supplierOf(p7Resolution);
        Supplier<AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution> p8 = mock(Supplier.class);

        var resolved = AverageEarningsOrderedFallbackResolver.resolve(p6, p7, p8);

        assertTrue(resolved.ready());
        assertEquals(AverageEarningsOrderedFallbackResolver.Selection.PARAGRAPH_7_PRE_EVENT_ACCRUED_WAGE, resolved.selection());
        assertSame(p7Resolution, resolved.paragraph7Authority());
        assertNull(resolved.paragraph8Authority());
        assertTrue(resolved.paragraph7ExhaustionReasons().isEmpty());
        verify(p7).get();
        verifyNoInteractions(p8);
    }

    @Test
    void paragraph7WithoutAccruedWageFallsThroughToParagraph8() {
        var resolved = resolveToParagraph8(false, true);
        assertEquals(List.of(AverageEarningsOrderedFallbackResolver.Paragraph7ExhaustionReason.NO_PRE_EVENT_ACCRUED_WAGE), resolved.paragraph7ExhaustionReasons());
    }

    @Test
    void paragraph7WithoutWorkedTimeFallsThroughToParagraph8() {
        var resolved = resolveToParagraph8(true, false);
        assertEquals(List.of(AverageEarningsOrderedFallbackResolver.Paragraph7ExhaustionReason.NO_PRE_EVENT_ACTUALLY_WORKED_TIME), resolved.paragraph7ExhaustionReasons());
    }

    @Test
    void paragraph7WithoutWageAndWorkedTimeCarriesBothExhaustionReasons() {
        var resolved = resolveToParagraph8(false, false);
        assertEquals(List.of(
                AverageEarningsOrderedFallbackResolver.Paragraph7ExhaustionReason.NO_PRE_EVENT_ACCRUED_WAGE,
                AverageEarningsOrderedFallbackResolver.Paragraph7ExhaustionReason.NO_PRE_EVENT_ACTUALLY_WORKED_TIME
        ), resolved.paragraph7ExhaustionReasons());
    }

    @Test
    void paragraph6BlockedNeverEvaluatesLaterAuthorities() {
        var p6 = p6Blocked(EVENT, "P6_BLOCK");
        Supplier<AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution> p7 = mock(Supplier.class);
        Supplier<AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution> p8 = mock(Supplier.class);

        var resolved = AverageEarningsOrderedFallbackResolver.resolve(p6, p7, p8);

        assertFalse(resolved.ready());
        assertEquals(AverageEarningsOrderedFallbackResolver.BlockingStage.PARAGRAPH_6, resolved.blockingStage());
        assertEquals("P6_BLOCK", resolved.blockingReason());
        verifyNoInteractions(p7, p8);
    }

    @Test
    void paragraph7BlockedNeverFallsThroughToParagraph8() {
        var p6 = p6Ready(AverageEarningsParagraph6ReferenceResolver.Selection.PARAGRAPH_6_EXHAUSTED, EVENT);
        var p7Resolution = p7Blocked(EVENT, "P7_BLOCK", "p7 blocked");
        Supplier<AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution> p7 = supplierOf(p7Resolution);
        Supplier<AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution> p8 = mock(Supplier.class);

        var resolved = AverageEarningsOrderedFallbackResolver.resolve(p6, p7, p8);

        assertFalse(resolved.ready());
        assertEquals(AverageEarningsOrderedFallbackResolver.BlockingStage.PARAGRAPH_7, resolved.blockingStage());
        assertEquals("P7_BLOCK", resolved.blockingReason());
        verify(p7).get();
        verifyNoInteractions(p8);
    }

    @Test
    void paragraph8BlockedBlocksFinalSelection() {
        var p6 = p6Ready(AverageEarningsParagraph6ReferenceResolver.Selection.PARAGRAPH_6_EXHAUSTED, EVENT);
        var p7Resolution = p7Ready(EVENT, false, false);
        var p8Resolution = p8Blocked(EVENT, "P8_BLOCK", "p8 blocked");

        var resolved = AverageEarningsOrderedFallbackResolver.resolve(
                p6,
                supplierOf(p7Resolution),
                supplierOf(p8Resolution)
        );

        assertFalse(resolved.ready());
        assertEquals(AverageEarningsOrderedFallbackResolver.BlockingStage.PARAGRAPH_8, resolved.blockingStage());
        assertEquals("P8_BLOCK", resolved.blockingReason());
        assertNull(resolved.selection());
    }

    @Test
    void paragraph7EventDateMismatchBlocksBeforeParagraph8() {
        assertParagraph7IdentityMismatch(
                LocalDate.of(2026, 8, 19),
                YearMonth.from(EVENT).atDay(1),
                EVENT
        );
    }

    @Test
    void paragraph7PeriodStartMismatchBlocksBeforeParagraph8() {
        assertParagraph7IdentityMismatch(
                EVENT,
                LocalDate.of(2026, 8, 2),
                EVENT
        );
    }

    @Test
    void paragraph7CutoffMismatchBlocksBeforeParagraph8() {
        assertParagraph7IdentityMismatch(
                EVENT,
                YearMonth.from(EVENT).atDay(1),
                LocalDate.of(2026, 8, 19)
        );
    }

    @Test
    void paragraph8EventDateMismatchBlocks() {
        assertParagraph8IdentityMismatch(
                LocalDate.of(2026, 8, 19),
                YearMonth.from(EVENT),
                YearMonth.from(EVENT).atDay(1),
                AverageEarningsLegalPolicy.LegalRegime.RU_PP_540_2025
        );
    }

    @Test
    void paragraph8EventMonthMismatchBlocks() {
        assertParagraph8IdentityMismatch(
                EVENT,
                YearMonth.of(2026, 7),
                YearMonth.from(EVENT).atDay(1),
                AverageEarningsLegalPolicy.LegalRegime.RU_PP_540_2025
        );
    }

    @Test
    void paragraph8CompensationBoundaryMismatchBlocks() {
        assertParagraph8IdentityMismatch(
                EVENT,
                YearMonth.from(EVENT),
                LocalDate.of(2026, 7, 1),
                AverageEarningsLegalPolicy.LegalRegime.RU_PP_540_2025
        );
    }

    @Test
    void paragraph8LegalRegimeMismatchBlocks() {
        assertParagraph8IdentityMismatch(
                EVENT,
                YearMonth.from(EVENT),
                YearMonth.from(EVENT).atDay(1),
                null
        );
    }

    @Test
    void primaryWindowMismatchBlocksWithoutDownstreamEvaluation() {
        var p6 = p6ReadyWithWindow(
                AverageEarningsParagraph6ReferenceResolver.Selection.PRIMARY,
                EVENT,
                AverageEarningsReferenceWindow.primary(EVENT).precedingEqual()
        );
        Supplier<AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution> p7 = mock(Supplier.class);
        Supplier<AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution> p8 = mock(Supplier.class);

        var resolved = AverageEarningsOrderedFallbackResolver.resolve(p6, p7, p8);

        assertFalse(resolved.ready());
        assertEquals(AverageEarningsOrderedFallbackResolver.AUTHORITY_EVENT_IDENTITY_MISMATCH, resolved.blockingReason());
        verifyNoInteractions(p7, p8);
    }

    @Test
    void paragraph6PrecedingWindowMismatchBlocksWithoutDownstreamEvaluation() {
        var p6 = p6ReadyWithWindow(
                AverageEarningsParagraph6ReferenceResolver.Selection.PARAGRAPH_6_PRECEDING,
                EVENT,
                AverageEarningsReferenceWindow.primary(EVENT)
        );
        Supplier<AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution> p7 = mock(Supplier.class);
        Supplier<AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution> p8 = mock(Supplier.class);

        var resolved = AverageEarningsOrderedFallbackResolver.resolve(p6, p7, p8);

        assertFalse(resolved.ready());
        assertEquals(AverageEarningsOrderedFallbackResolver.BlockingStage.PARAGRAPH_6, resolved.blockingStage());
        verifyNoInteractions(p7, p8);
    }

    @Test
    void paragraph6EventMonthMismatchBlocksWithoutDownstreamEvaluation() {
        var p6 = p6Ready(AverageEarningsParagraph6ReferenceResolver.Selection.PARAGRAPH_6_EXHAUSTED, EVENT);
        when(p6.eventMonth()).thenReturn(YearMonth.of(2026, 7));
        Supplier<AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution> p7 = mock(Supplier.class);
        Supplier<AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution> p8 = mock(Supplier.class);

        var resolved = AverageEarningsOrderedFallbackResolver.resolve(p6, p7, p8);

        assertFalse(resolved.ready());
        assertEquals(AverageEarningsOrderedFallbackResolver.BlockingStage.PARAGRAPH_6, resolved.blockingStage());
        verifyNoInteractions(p7, p8);
    }

    @Test
    void nullParagraph6IsRejectedBeforeSupplierEvaluation() {
        Supplier<AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution> p7 = mock(Supplier.class);
        Supplier<AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution> p8 = mock(Supplier.class);
        assertThrows(NullPointerException.class, () -> AverageEarningsOrderedFallbackResolver.resolve(null, p7, p8));
        verifyNoInteractions(p7, p8);
    }

    @Test
    void nullParagraph7SupplierIsRejected() {
        var p6 = p6Ready(AverageEarningsParagraph6ReferenceResolver.Selection.PRIMARY, EVENT);
        Supplier<AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution> p8 = mock(Supplier.class);
        assertThrows(NullPointerException.class, () -> AverageEarningsOrderedFallbackResolver.resolve(p6, null, p8));
        verifyNoInteractions(p8);
    }

    @Test
    void nullParagraph8SupplierIsRejected() {
        var p6 = p6Ready(AverageEarningsParagraph6ReferenceResolver.Selection.PRIMARY, EVENT);
        Supplier<AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution> p7 = mock(Supplier.class);
        assertThrows(NullPointerException.class, () -> AverageEarningsOrderedFallbackResolver.resolve(p6, p7, null));
        verifyNoInteractions(p7);
    }

    @Test
    void nullParagraph7ResultIsRejectedOnlyWhenParagraph7IsReached() {
        var p6 = p6Ready(AverageEarningsParagraph6ReferenceResolver.Selection.PARAGRAPH_6_EXHAUSTED, EVENT);
        Supplier<AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution> p7 = mock(Supplier.class);
        Supplier<AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution> p8 = mock(Supplier.class);
        when(p7.get()).thenReturn(null);

        assertThrows(NullPointerException.class, () -> AverageEarningsOrderedFallbackResolver.resolve(p6, p7, p8));
        verify(p7).get();
        verifyNoInteractions(p8);
    }

    @Test
    void nullParagraph8ResultIsRejectedOnlyAfterParagraph7IsExhausted() {
        var p6 = p6Ready(AverageEarningsParagraph6ReferenceResolver.Selection.PARAGRAPH_6_EXHAUSTED, EVENT);
        var p7Resolution = p7Ready(EVENT, false, true);
        Supplier<AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution> p7 = supplierOf(p7Resolution);
        Supplier<AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution> p8 = mock(Supplier.class);
        when(p8.get()).thenReturn(null);

        assertThrows(NullPointerException.class, () -> AverageEarningsOrderedFallbackResolver.resolve(p6, p7, p8));
        verify(p7).get();
        verify(p8).get();
    }

    @Test
    void unsupportedLegalRegimeFailsBeforeDownstreamEvaluation() {
        LocalDate unsupported = LocalDate.of(2025, 8, 31);
        var p6 = p6Ready(AverageEarningsParagraph6ReferenceResolver.Selection.PRIMARY, unsupported);
        Supplier<AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution> p7 = mock(Supplier.class);
        Supplier<AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution> p8 = mock(Supplier.class);

        assertThrows(UnsupportedOperationException.class, () -> AverageEarningsOrderedFallbackResolver.resolve(p6, p7, p8));
        verifyNoInteractions(p7, p8);
    }

    @Test
    void blockedResultRejectsPartialSelection() {
        assertThrows(IllegalArgumentException.class, () -> new AverageEarningsOrderedFallbackResolver.Resolution(
                EVENT,
                YearMonth.from(EVENT),
                false,
                AverageEarningsOrderedFallbackResolver.BlockingStage.PARAGRAPH_7,
                "BLOCKED",
                "blocked",
                AverageEarningsOrderedFallbackResolver.Selection.PARAGRAPH_7_PRE_EVENT_ACCRUED_WAGE,
                null,
                null,
                null,
                null,
                List.of()
        ));
    }

    private static AverageEarningsOrderedFallbackResolver.Resolution resolveToParagraph8(
            boolean wagePresent,
            boolean workPresent
    ) {
        var p6 = p6Ready(AverageEarningsParagraph6ReferenceResolver.Selection.PARAGRAPH_6_EXHAUSTED, EVENT);
        var p7Resolution = p7Ready(EVENT, wagePresent, workPresent);
        var p8Resolution = p8Ready(EVENT);
        Supplier<AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution> p7 = supplierOf(p7Resolution);
        Supplier<AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution> p8 = supplierOf(p8Resolution);

        var resolved = AverageEarningsOrderedFallbackResolver.resolve(p6, p7, p8);

        assertTrue(resolved.ready());
        assertEquals(AverageEarningsOrderedFallbackResolver.Selection.PARAGRAPH_8_ESTABLISHED_TARIFF_OR_SALARY, resolved.selection());
        assertSame(p7Resolution, resolved.paragraph7Authority());
        assertSame(p8Resolution, resolved.paragraph8Authority());
        verify(p7).get();
        verify(p8).get();
        return resolved;
    }

    private static void assertParagraph7IdentityMismatch(
            LocalDate eventDate,
            LocalDate periodFrom,
            LocalDate cutoff
    ) {
        var p6 = p6Ready(AverageEarningsParagraph6ReferenceResolver.Selection.PARAGRAPH_6_EXHAUSTED, EVENT);
        var p7Resolution = mock(AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution.class);
        when(p7Resolution.eventDate()).thenReturn(eventDate);
        when(p7Resolution.periodFrom()).thenReturn(periodFrom);
        when(p7Resolution.cutoffExclusive()).thenReturn(cutoff);
        when(p7Resolution.ready()).thenReturn(true);
        Supplier<AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution> p8 = mock(Supplier.class);

        var resolved = AverageEarningsOrderedFallbackResolver.resolve(p6, supplierOf(p7Resolution), p8);

        assertFalse(resolved.ready());
        assertEquals(AverageEarningsOrderedFallbackResolver.BlockingStage.PARAGRAPH_7, resolved.blockingStage());
        assertEquals(AverageEarningsOrderedFallbackResolver.AUTHORITY_EVENT_IDENTITY_MISMATCH, resolved.blockingReason());
        verifyNoInteractions(p8);
    }

    private static void assertParagraph8IdentityMismatch(
            LocalDate eventDate,
            YearMonth eventMonth,
            LocalDate boundary,
            AverageEarningsLegalPolicy.LegalRegime legalRegime
    ) {
        var p6 = p6Ready(AverageEarningsParagraph6ReferenceResolver.Selection.PARAGRAPH_6_EXHAUSTED, EVENT);
        var p7Resolution = p7Ready(EVENT, false, false);
        var p8Resolution = mock(AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution.class);
        when(p8Resolution.eventDate()).thenReturn(eventDate);
        when(p8Resolution.eventMonth()).thenReturn(eventMonth);
        when(p8Resolution.compensationBoundary()).thenReturn(boundary);
        when(p8Resolution.legalRegime()).thenReturn(legalRegime);
        when(p8Resolution.ready()).thenReturn(true);

        var resolved = AverageEarningsOrderedFallbackResolver.resolve(
                p6,
                supplierOf(p7Resolution),
                supplierOf(p8Resolution)
        );

        assertFalse(resolved.ready());
        assertEquals(AverageEarningsOrderedFallbackResolver.BlockingStage.PARAGRAPH_8, resolved.blockingStage());
        assertEquals(AverageEarningsOrderedFallbackResolver.AUTHORITY_EVENT_IDENTITY_MISMATCH, resolved.blockingReason());
    }

    private static AverageEarningsParagraph6ReferenceResolver.Resolution p6Ready(
            AverageEarningsParagraph6ReferenceResolver.Selection selection,
            LocalDate eventDate
    ) {
        AverageEarningsReferenceWindow window = switch (selection) {
            case PRIMARY -> AverageEarningsReferenceWindow.primary(eventDate);
            case PARAGRAPH_6_PRECEDING, PARAGRAPH_6_EXHAUSTED ->
                    AverageEarningsReferenceWindow.primary(eventDate).precedingEqual();
        };
        return p6ReadyWithWindow(selection, eventDate, window);
    }

    private static AverageEarningsParagraph6ReferenceResolver.Resolution p6ReadyWithWindow(
            AverageEarningsParagraph6ReferenceResolver.Selection selection,
            LocalDate eventDate,
            AverageEarningsReferenceWindow window
    ) {
        var p6 = mock(AverageEarningsParagraph6ReferenceResolver.Resolution.class);
        var evidence = mock(AverageEarningsParagraph6ReferenceResolver.PeriodEvidence.class);
        when(p6.eventDate()).thenReturn(eventDate);
        when(p6.eventMonth()).thenReturn(YearMonth.from(eventDate));
        when(p6.ready()).thenReturn(true);
        when(p6.selection()).thenReturn(selection);
        when(p6.selectedEvidence()).thenReturn(evidence);
        when(evidence.window()).thenReturn(window);
        return p6;
    }

    private static AverageEarningsParagraph6ReferenceResolver.Resolution p6Blocked(
            LocalDate eventDate,
            String reason
    ) {
        var p6 = mock(AverageEarningsParagraph6ReferenceResolver.Resolution.class);
        when(p6.eventDate()).thenReturn(eventDate);
        when(p6.eventMonth()).thenReturn(YearMonth.from(eventDate));
        when(p6.ready()).thenReturn(false);
        when(p6.blockingReason()).thenReturn(reason);
        return p6;
    }

    private static AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution p7Ready(
            LocalDate eventDate,
            boolean wagePresent,
            boolean workPresent
    ) {
        var p7 = mock(AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution.class);
        when(p7.eventDate()).thenReturn(eventDate);
        when(p7.periodFrom()).thenReturn(YearMonth.from(eventDate).atDay(1));
        when(p7.cutoffExclusive()).thenReturn(eventDate);
        when(p7.ready()).thenReturn(true);
        when(p7.accruedWagePresent()).thenReturn(wagePresent);
        when(p7.workedTimePresent()).thenReturn(workPresent);
        return p7;
    }

    private static AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution p7Blocked(
            LocalDate eventDate,
            String reason,
            String message
    ) {
        var p7 = p7Ready(eventDate, false, false);
        when(p7.ready()).thenReturn(false);
        when(p7.blockingReason()).thenReturn(reason);
        when(p7.blockingMessage()).thenReturn(message);
        return p7;
    }

    private static AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution p8Ready(
            LocalDate eventDate
    ) {
        var p8 = mock(AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution.class);
        when(p8.eventDate()).thenReturn(eventDate);
        when(p8.eventMonth()).thenReturn(YearMonth.from(eventDate));
        when(p8.compensationBoundary()).thenReturn(YearMonth.from(eventDate).atDay(1));
        when(p8.legalRegime()).thenReturn(AverageEarningsLegalPolicy.LegalRegime.RU_PP_540_2025);
        when(p8.ready()).thenReturn(true);
        return p8;
    }

    private static AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution p8Blocked(
            LocalDate eventDate,
            String reason,
            String message
    ) {
        var p8 = p8Ready(eventDate);
        when(p8.ready()).thenReturn(false);
        when(p8.blockingReason()).thenReturn(reason);
        when(p8.blockingMessage()).thenReturn(message);
        return p8;
    }

    @SuppressWarnings("unchecked")
    private static <T> Supplier<T> supplierOf(T value) {
        Supplier<T> supplier = mock(Supplier.class);
        when(supplier.get()).thenReturn(value);
        return supplier;
    }
}
