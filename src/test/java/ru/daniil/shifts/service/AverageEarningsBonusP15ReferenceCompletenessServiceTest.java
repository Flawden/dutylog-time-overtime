package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.WorkTimeAccountingMode;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static ru.daniil.shifts.service.AverageEarningsBonusP15Formula.WorkMeasureUnit.WORKING_MINUTES;
import static ru.daniil.shifts.service.AverageEarningsLegalPolicy.AbsenceTreatment.*;
import static ru.daniil.shifts.service.AverageEarningsLegalPolicy.LegalBasis.*;

class AverageEarningsBonusP15ReferenceCompletenessServiceTest {

    private static final LocalDate EVENT = LocalDate.of(2026, 8, 15);
    private static final YearMonth EVENT_MONTH = YearMonth.of(2026, 8);
    private static final YearMonth FROM = YearMonth.of(2025, 8);
    private static final YearMonth TO = YearMonth.of(2026, 7);

    private final AppUser user = mock(AppUser.class);
    private final AverageEarningsBonusP15ReferenceWorkedTimeFactService workedTime =
            mock(AverageEarningsBonusP15ReferenceWorkedTimeFactService.class);
    private final AverageEarningsReferenceFactsService referenceFacts =
            mock(AverageEarningsReferenceFactsService.class);
    private final AverageEarningsBonusP15ReferenceCompletenessService service =
            new AverageEarningsBonusP15ReferenceCompletenessService(workedTime, referenceFacts);

    @Test
    void fullyWorkedScheduleWithoutParagraph5ExclusionsIsFullyWorkedForP15() {
        when(workedTime.resolve(user, EVENT, List.of())).thenReturn(worked(true, 1_000, 1_000, List.of()));
        when(referenceFacts.resolve(user, EVENT_MONTH)).thenReturn(reference(List.of()));

        var result = service.resolve(user, EVENT, List.of());

        assertTrue(result.ready());
        assertTrue(result.scheduleFullyWorked());
        assertFalse(result.paragraph5ExcludedTimePresent());
        assertTrue(result.proportionalNormAuthorityComplete());
        assertTrue(result.referencePeriodFullyWorked());
        assertTrue(result.paragraph5Exclusions().isEmpty());
    }

    @Test
    void vacationIsParagraph5ExclusionAndMakesReferenceNotFullyWorked() {
        when(workedTime.resolve(user, EVENT, List.of())).thenReturn(worked(true, 1_000, 1_000, List.of()));
        when(referenceFacts.resolve(user, EVENT_MONTH)).thenReturn(reference(List.of(
                absence(1, "VACATION", "VACATION_DAYS", "VACATION_ALLOWANCE")
        )));

        var result = service.resolve(user, EVENT, List.of());

        assertTrue(result.ready());
        assertTrue(result.paragraph5ExcludedTimePresent());
        assertFalse(result.referencePeriodFullyWorked());
        assertEquals(1, result.paragraph5Exclusions().size());
        assertEquals(EXCLUDE_PRESERVED_AVERAGE, result.paragraph5Exclusions().get(0).treatment());
        assertEquals(PP_540_P5_A, result.paragraph5Exclusions().get(0).basis());
    }

    @Test
    void sickTimeIsParagraph5BExclusion() {
        stubAbsence(absence(2, "SICK", "NONE", "SICK_PAY"));

        var exclusion = service.resolve(user, EVENT, List.of()).paragraph5Exclusions().get(0);

        assertEquals(EXCLUDE_TEMPORARY_DISABILITY, exclusion.treatment());
        assertEquals(PP_540_P5_B, exclusion.basis());
    }

    @Test
    void unpaidTimeIsParagraph5EExclusion() {
        stubAbsence(absence(3, "UNPAID", "NONE", "UNPAID"));

        var exclusion = service.resolve(user, EVENT, List.of()).paragraph5Exclusions().get(0);

        assertEquals(EXCLUDE_OTHER_RELEASE_FROM_WORK, exclusion.treatment());
        assertEquals(PP_540_P5_E, exclusion.basis());
    }

    @Test
    void timeOffBankIsParagraph5EExclusion() {
        stubAbsence(absence(4, "TIME_OFF", "TIME_OFF_HOURS", "OVERTIME_BANK"));

        var exclusion = service.resolve(user, EVENT, List.of()).paragraph5Exclusions().get(0);

        assertEquals(EXCLUDE_OTHER_RELEASE_FROM_WORK, exclusion.treatment());
        assertEquals(PP_540_P5_E, exclusion.basis());
    }

    @Test
    void unresolvedOtherAbsenceBlocksWithoutPartialAuthority() {
        stubAbsence(absence(9, "OTHER", "NONE", "NONE"));

        var result = service.resolve(user, EVENT, List.of());

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsBonusP15ReferenceCompletenessService.P5_ABSENCE_TREATMENT_UNRESOLVED,
                result.blockingReason()
        );
        assertEquals(Long.valueOf(9L), result.blockingAbsencePeriodId());
        assertNull(result.referenceWorkedTime());
        assertTrue(result.paragraph5Exclusions().isEmpty());
    }

    @Test
    void workedTimeBlockerPropagatesBeforeParagraph5FactsAreRead() {
        when(workedTime.resolve(user, EVENT, List.of())).thenReturn(
                AverageEarningsBonusP15ReferenceWorkedTimeFactService.Resolution.blocked(
                        EVENT, EVENT_MONTH, FROM, TO, "WORK_BLOCK", YearMonth.of(2026, 3)
                )
        );

        var result = service.resolve(user, EVENT, List.of());

        assertFalse(result.ready());
        assertEquals("WORK_BLOCK", result.blockingReason());
        assertEquals(YearMonth.of(2026, 3), result.blockingPeriod());
        verifyNoInteractions(referenceFacts);
    }

    @Test
    void scheduledWorkGapMakesReferenceNotFullyWorkedEvenWithoutParagraph5Facts() {
        when(workedTime.resolve(user, EVENT, List.of())).thenReturn(worked(false, 900, 1_000, List.of()));
        when(referenceFacts.resolve(user, EVENT_MONTH)).thenReturn(reference(List.of()));

        var result = service.resolve(user, EVENT, List.of());

        assertTrue(result.ready());
        assertFalse(result.scheduleFullyWorked());
        assertFalse(result.referencePeriodFullyWorked());
        assertTrue(result.proportionalNormAuthorityComplete());
    }

    @Test
    void explicitNoPayrollMonthMakesTwelveMonthCompletenessFalseWithoutInventingNorm() {
        YearMonth zero = YearMonth.of(2025, 8);
        when(workedTime.resolve(user, EVENT, List.of(zero))).thenReturn(worked(true, 1_000, 1_000, List.of(zero)));
        when(referenceFacts.resolve(user, EVENT_MONTH)).thenReturn(reference(List.of()));

        var result = service.resolve(user, EVENT, List.of(zero));

        assertTrue(result.ready());
        assertEquals(List.of(zero), result.noPayrollMonths());
        assertFalse(result.proportionalNormAuthorityComplete());
        assertFalse(result.referencePeriodFullyWorked());
    }

    @Test
    void workedTimeAuthorityWindowMismatchBlocksInsteadOfAdaptingIt() {
        var mismatched = AverageEarningsBonusP15ReferenceWorkedTimeFactService.Resolution.ready(
                LocalDate.of(2026, 9, 1),
                YearMonth.of(2026, 9),
                YearMonth.of(2025, 9),
                YearMonth.of(2026, 8),
                WorkTimeAccountingMode.SUMMARIZED,
                new AverageEarningsBonusP15Formula.ReferenceWorkedTimeFact(WORKING_MINUTES, 1, 1),
                true,
                twelveMonths(YearMonth.of(2025, 9), List.of())
        );
        when(workedTime.resolve(user, EVENT, List.of())).thenReturn(mismatched);

        var result = service.resolve(user, EVENT, List.of());

        assertFalse(result.ready());
        assertEquals(AverageEarningsBonusP15ReferenceCompletenessService.REFERENCE_FACT_WINDOW_MISMATCH,
                result.blockingReason());
        verifyNoInteractions(referenceFacts);
    }

    @Test
    void absenceAuthorityWindowMismatchBlocksInsteadOfReusingWrongReference() {
        when(workedTime.resolve(user, EVENT, List.of())).thenReturn(worked(true, 1_000, 1_000, List.of()));
        when(referenceFacts.resolve(user, EVENT_MONTH)).thenReturn(
                new AverageEarningsReferenceFactsService.ReferenceFacts(
                        EVENT_MONTH,
                        FROM.atDay(2),
                        TO.atEndOfMonth(),
                        List.of()
                )
        );

        var result = service.resolve(user, EVENT, List.of());

        assertFalse(result.ready());
        assertEquals(AverageEarningsBonusP15ReferenceCompletenessService.REFERENCE_FACT_WINDOW_MISMATCH,
                result.blockingReason());
    }

    @Test
    void nullReferenceFactsAuthorityResultIsRejected() {
        when(workedTime.resolve(user, EVENT, List.of())).thenReturn(worked(true, 1_000, 1_000, List.of()));
        when(referenceFacts.resolve(user, EVENT_MONTH)).thenReturn(null);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.resolve(user, EVENT, List.of())
        );

        assertEquals("Average earnings reference facts authority returned null", failure.getMessage());
    }

    @Test
    void machineTupleContradictionFailsClosedInsteadOfReclassifyingAbsence() {
        stubAbsence(absence(12, "VACATION", "NONE", "VACATION_ALLOWANCE"));

        assertThrows(IllegalStateException.class, () -> service.resolve(user, EVENT, List.of()));
    }

    @Test
    void noPayrollProofsArePassedUnchangedToF3F3Authority() {
        List<YearMonth> zero = List.of(YearMonth.of(2025, 8), YearMonth.of(2025, 9));
        when(workedTime.resolve(user, EVENT, zero)).thenReturn(worked(true, 1_000, 1_000, zero));
        when(referenceFacts.resolve(user, EVENT_MONTH)).thenReturn(reference(List.of()));

        service.resolve(user, EVENT, zero);

        ArgumentCaptor<List<YearMonth>> captor = ArgumentCaptor.forClass(List.class);
        verify(workedTime).resolve(eq(user), eq(EVENT), captor.capture());
        assertEquals(zero, captor.getValue());
    }

    private void stubAbsence(AverageEarningsReferenceFactsService.AbsenceFact fact) {
        when(workedTime.resolve(user, EVENT, List.of())).thenReturn(worked(true, 1_000, 1_000, List.of()));
        when(referenceFacts.resolve(user, EVENT_MONTH)).thenReturn(reference(List.of(fact)));
    }

    private AverageEarningsReferenceFactsService.ReferenceFacts reference(
            List<AverageEarningsReferenceFactsService.AbsenceFact> facts
    ) {
        return new AverageEarningsReferenceFactsService.ReferenceFacts(
                EVENT_MONTH,
                FROM.atDay(1),
                TO.atEndOfMonth(),
                facts
        );
    }

    private AverageEarningsReferenceFactsService.AbsenceFact absence(
            long id,
            String code,
            String balance,
            String compensation
    ) {
        return new AverageEarningsReferenceFactsService.AbsenceFact(
                id,
                code,
                balance,
                compensation,
                "APPROVED",
                "FULL_DAY",
                LocalDate.of(2026, 2, 10),
                LocalDate.of(2026, 2, 10),
                LocalDate.of(2026, 2, 10),
                LocalDate.of(2026, 2, 10),
                null,
                null,
                0,
                0,
                null
        );
    }

    private AverageEarningsBonusP15ReferenceWorkedTimeFactService.Resolution worked(
            boolean fullyWorked,
            long worked,
            long norm,
            List<YearMonth> zeroMonths
    ) {
        return AverageEarningsBonusP15ReferenceWorkedTimeFactService.Resolution.ready(
                EVENT,
                EVENT_MONTH,
                FROM,
                TO,
                WorkTimeAccountingMode.SUMMARIZED,
                new AverageEarningsBonusP15Formula.ReferenceWorkedTimeFact(
                        WORKING_MINUTES,
                        worked,
                        norm
                ),
                fullyWorked,
                twelveMonths(FROM, zeroMonths)
        );
    }

    private List<AverageEarningsBonusP15ReferenceWorkedTimeFactService.ResolvedMonth> twelveMonths(
            YearMonth from,
            List<YearMonth> zeroMonths
    ) {
        List<AverageEarningsBonusP15ReferenceWorkedTimeFactService.ResolvedMonth> result = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            YearMonth month = from.plusMonths(i);
            if (zeroMonths.contains(month)) {
                result.add(AverageEarningsBonusP15ReferenceWorkedTimeFactService.ResolvedMonth.noPayroll(month));
            } else {
                result.add(new AverageEarningsBonusP15ReferenceWorkedTimeFactService.ResolvedMonth(
                        month,
                        false,
                        100L + i,
                        1,
                        WorkTimeAccountingMode.SUMMARIZED,
                        1,
                        1,
                        1,
                        "0".repeat(64)
                ));
            }
        }
        return result;
    }
}
