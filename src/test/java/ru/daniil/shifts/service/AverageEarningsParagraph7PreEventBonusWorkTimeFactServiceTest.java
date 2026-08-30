package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollSnapshotP15ScheduledWorkSourceKind;
import ru.daniil.shifts.model.WorkTimeAccountingMode;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventBonusP15Policy.Decision;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventBonusP15Policy.IncompletePreEventTreatment;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventBonusWorkTimeFactService.WorkMeasureUnit;
import ru.daniil.shifts.service.PayrollP15ScheduledWorkFreezeService.RangeFact;
import ru.daniil.shifts.service.PayrollP15ScheduledWorkFreezeService.RangeResult;
import ru.daniil.shifts.service.TimeCompensationService.PayrollSourceSnapshot;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AverageEarningsParagraph7PreEventBonusWorkTimeFactServiceTest {
    private static final LocalDate EVENT = LocalDate.of(2026, 8, 20);
    private static final LocalDate FROM = LocalDate.of(2026, 8, 1);
    private static final LocalDate TO = LocalDate.of(2026, 8, 19);

    private TimeCompensationService timeCompensation;
    private PayrollP15ScheduledWorkFreezeService scheduledWork;
    private AverageEarningsParagraph7PreEventBonusWorkTimeFactService service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        timeCompensation = mock(TimeCompensationService.class);
        scheduledWork = mock(PayrollP15ScheduledWorkFreezeService.class);
        service = new AverageEarningsParagraph7PreEventBonusWorkTimeFactService(
                timeCompensation,
                scheduledWork
        );
        user = mock(AppUser.class);
    }

    @Test
    void constructorRejectsMissingPayrollSourceAuthority() {
        assertThrows(
                NullPointerException.class,
                () -> new AverageEarningsParagraph7PreEventBonusWorkTimeFactService(null, scheduledWork)
        );
    }

    @Test
    void constructorRejectsMissingScheduledWorkAuthority() {
        assertThrows(
                NullPointerException.class,
                () -> new AverageEarningsParagraph7PreEventBonusWorkTimeFactService(timeCompensation, null)
        );
    }

    @Test
    void resolveRejectsNullUser() {
        assertThrows(NullPointerException.class, () -> service.resolve(null, policy(List.of())));
    }

    @Test
    void resolveRejectsNullPolicy() {
        assertThrows(NullPointerException.class, () -> service.resolve(user, null));
    }

    @Test
    void blockedPolicyPropagatesWithoutReadingWorkTime() {
        var result = service.resolve(user, blockedPolicy("B6B2_BLOCKED"));
        assertFalse(result.ready());
        assertEquals("B6B2_BLOCKED", result.blockingReason());
        assertFalse(result.workTimeRequired());
        assertTrue(result.rangeFacts().isEmpty());
        verifyNoInteractions(timeCompensation, scheduledWork);
    }

    @Test
    void mismatchedPolicyWindowBlocksWithoutReadingWorkTime() {
        var policy = policyFor(EVENT, List.of(proportional()));
        when(policy.periodFrom()).thenReturn(FROM.plusDays(1));
        var result = service.resolve(user, policy);
        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventBonusWorkTimeFactService.POLICY_WINDOW_MISMATCH,
                result.blockingReason()
        );
        verifyNoInteractions(timeCompensation, scheduledWork);
    }

    @Test
    void noIncludedBonusNeedsNoWorkTimeAuthority() {
        var result = service.resolve(user, policy(List.of(excluded())));
        assertTrue(result.ready());
        assertFalse(result.workTimeRequired());
        assertNull(result.unit());
        assertEquals(0L, result.workedUnits());
        assertEquals(0L, result.normUnits());
        verifyNoInteractions(timeCompensation, scheduledWork);
    }

    @Test
    void bonusAlreadyAccruedForActualPreEventTimeNeedsNoRatioAuthority() {
        var result = service.resolve(user, policy(List.of(noAdjustment())));
        assertTrue(result.ready());
        assertFalse(result.workTimeRequired());
        assertFalse(result.scheduleFullyWorked());
        verifyNoInteractions(timeCompensation, scheduledWork);
    }

    @Test
    void proportionalBonusOnFirstDayBlocksAtZeroPreEventNormWithoutSourceRead() {
        LocalDate first = LocalDate.of(2026, 8, 1);
        var result = service.resolve(user, policyFor(first, List.of(proportional())));
        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventBonusWorkTimeFactService.PRE_EVENT_NORM_ZERO,
                result.blockingReason()
        );
        verifyNoInteractions(timeCompensation, scheduledWork);
    }

    @Test
    void nullPayrollSourceIsStructuralFailure() {
        var policy = policy(List.of(proportional()));
        when(timeCompensation.payrollSource(user, FROM, TO)).thenReturn(null);
        assertThrows(NullPointerException.class, () -> service.resolve(user, policy));
        verifyNoInteractions(scheduledWork);
    }

    @Test
    void payrollSourceWindowMismatchBlocksBeforeRangeDerivation() {
        var policy = policy(List.of(proportional()));
        when(timeCompensation.payrollSource(user, FROM, TO))
                .thenReturn(source(FROM, TO.minusDays(1)));
        var result = service.resolve(user, policy);
        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventBonusWorkTimeFactService.SOURCE_WINDOW_MISMATCH,
                result.blockingReason()
        );
        verifyNoInteractions(scheduledWork);
    }

    @Test
    void nullRangeAuthorityIsStructuralFailure() {
        var policy = policy(List.of(proportional()));
        PayrollSourceSnapshot source = source(FROM, TO);
        when(timeCompensation.payrollSource(user, FROM, TO)).thenReturn(source);
        when(scheduledWork.deriveRange(user, source)).thenReturn(null);
        assertThrows(NullPointerException.class, () -> service.resolve(user, policy));
    }

    @Test
    void rangeWindowMismatchBlocksWithoutUsingFacts() {
        var policy = policy(List.of(proportional()));
        PayrollSourceSnapshot source = source(FROM, TO);
        when(timeCompensation.payrollSource(user, FROM, TO)).thenReturn(source);
        RangeResult range = mock(RangeResult.class);
        when(range.from()).thenReturn(FROM.plusDays(1));
        when(range.to()).thenReturn(TO);
        when(scheduledWork.deriveRange(user, source)).thenReturn(range);
        var result = service.resolve(user, policy);
        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventBonusWorkTimeFactService.RANGE_WINDOW_MISMATCH,
                result.blockingReason()
        );
        verify(range, never()).facts();
    }

    @Test
    void blockedCanonicalRangePropagatesWithoutPartialFacts() {
        var policy = policy(List.of(proportional()));
        PayrollSourceSnapshot source = source(FROM, TO);
        when(timeCompensation.payrollSource(user, FROM, TO)).thenReturn(source);
        RangeResult range = RangeResult.blocked(
                FROM,
                TO,
                PayrollP15ScheduledWorkFreezeService.RANGE_DERIVATION_INCOMPLETE + ":2026-08-18",
                LocalDate.of(2026, 8, 18)
        );
        when(scheduledWork.deriveRange(user, source)).thenReturn(range);
        var result = service.resolve(user, policy);
        assertFalse(result.ready());
        assertEquals(range.blockingReason(), result.blockingReason());
        assertEquals(range.blockingDate(), result.blockingDate());
        assertTrue(result.rangeFacts().isEmpty());
    }

    @Test
    void summarizedFullyWorkedRangeUsesScheduledMinutes() {
        var result = resolveWithFacts(
                proportional(),
                fact(LocalDate.of(2026, 8, 18), WorkTimeAccountingMode.SUMMARIZED, 480, 480, 0),
                fact(LocalDate.of(2026, 8, 19), WorkTimeAccountingMode.SUMMARIZED, 480, 480, 0)
        );
        assertTrue(result.ready());
        assertTrue(result.workTimeRequired());
        assertEquals(WorkMeasureUnit.WORKING_MINUTES, result.unit());
        assertEquals(960L, result.workedUnits());
        assertEquals(960L, result.normUnits());
        assertTrue(result.scheduleFullyWorked());
    }

    @Test
    void summarizedMissedSchedulePreservesTrueDenominator() {
        var result = resolveWithFacts(
                proportional(),
                fact(LocalDate.of(2026, 8, 18), WorkTimeAccountingMode.SUMMARIZED, 480, 0, 0),
                fact(LocalDate.of(2026, 8, 19), WorkTimeAccountingMode.SUMMARIZED, 480, 480, 0)
        );
        assertTrue(result.ready());
        assertEquals(480L, result.workedUnits());
        assertEquals(960L, result.normUnits());
        assertFalse(result.scheduleFullyWorked());
    }

    @Test
    void workedOutsidePlanNeverImprovesSummarizedP15Coefficient() {
        var result = resolveWithFacts(
                proportional(),
                fact(LocalDate.of(2026, 8, 18), WorkTimeAccountingMode.SUMMARIZED, 480, 0, 600)
        );
        assertTrue(result.ready());
        assertEquals(0L, result.workedUnits());
        assertEquals(480L, result.normUnits());
        assertEquals(600, result.rangeFacts().get(0).workedOutsidePlanMinutes());
    }

    @Test
    void dailyFullyWorkedScheduledDaysUseDayUnits() {
        var result = resolveWithFacts(
                proportional(),
                fact(LocalDate.of(2026, 8, 18), WorkTimeAccountingMode.DAILY, 480, 480, 0),
                fact(LocalDate.of(2026, 8, 19), WorkTimeAccountingMode.DAILY, 480, 480, 0)
        );
        assertTrue(result.ready());
        assertEquals(WorkMeasureUnit.WORKING_DAYS, result.unit());
        assertEquals(2L, result.workedUnits());
        assertEquals(2L, result.normUnits());
        assertTrue(result.scheduleFullyWorked());
    }

    @Test
    void dailyWhollyMissedDayContributesNormButNoWorkedDay() {
        var result = resolveWithFacts(
                proportional(),
                fact(LocalDate.of(2026, 8, 18), WorkTimeAccountingMode.DAILY, 480, 0, 0),
                fact(LocalDate.of(2026, 8, 19), WorkTimeAccountingMode.DAILY, 480, 480, 0)
        );
        assertTrue(result.ready());
        assertEquals(1L, result.workedUnits());
        assertEquals(2L, result.normUnits());
        assertFalse(result.scheduleFullyWorked());
    }

    @Test
    void dailyZeroScheduleFactDoesNotInventNormDay() {
        var result = resolveWithFacts(
                proportional(),
                fact(LocalDate.of(2026, 8, 18), WorkTimeAccountingMode.DAILY, 0, 0, 240),
                fact(LocalDate.of(2026, 8, 19), WorkTimeAccountingMode.DAILY, 480, 480, 0)
        );
        assertTrue(result.ready());
        assertEquals(1L, result.workedUnits());
        assertEquals(1L, result.normUnits());
    }

    @Test
    void dailyPartialScheduledDayBlocksInsteadOfRounding() {
        var result = resolveWithFacts(
                proportional(),
                fact(LocalDate.of(2026, 8, 18), WorkTimeAccountingMode.DAILY, 480, 240, 0)
        );
        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventBonusWorkTimeFactService.DAILY_PARTIAL_DAY_UNRESOLVED
                        + ":2026-08-18",
                result.blockingReason()
        );
        assertTrue(result.rangeFacts().isEmpty());
    }

    @Test
    void mixedAccountingModesBlockInsteadOfMixingUnits() {
        var result = resolveWithFacts(
                proportional(),
                fact(LocalDate.of(2026, 8, 18), WorkTimeAccountingMode.DAILY, 480, 480, 0),
                fact(LocalDate.of(2026, 8, 19), WorkTimeAccountingMode.SUMMARIZED, 480, 480, 0)
        );
        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventBonusWorkTimeFactService.MIXED_ACCOUNTING_MODE,
                result.blockingReason()
        );
    }

    @Test
    void zeroScheduledNormBlocksRequiredRatioAuthority() {
        var result = resolveWithFacts(
                proportional(),
                fact(LocalDate.of(2026, 8, 18), WorkTimeAccountingMode.SUMMARIZED, 0, 0, 240)
        );
        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventBonusWorkTimeFactService.PRE_EVENT_NORM_ZERO,
                result.blockingReason()
        );
    }

    @Test
    void unknownActualWorkAccrualFactRequiresWorkTimeAuthorityForB6B3B() {
        var result = resolveWithFacts(
                requireExplicit(),
                fact(LocalDate.of(2026, 8, 18), WorkTimeAccountingMode.SUMMARIZED, 480, 480, 0)
        );
        assertTrue(result.ready());
        assertTrue(result.workTimeRequired());
        assertTrue(result.scheduleFullyWorked());
    }

    @Test
    void excludedBonusDoesNotForceWorkTimeEvenBesideNoAdjustmentBonus() {
        var result = service.resolve(
                user,
                policy(List.of(excluded(), noAdjustment()))
        );
        assertTrue(result.ready());
        assertFalse(result.workTimeRequired());
        verifyNoInteractions(timeCompensation, scheduledWork);
    }

    @Test
    void exactRangeFactsRemainAvailableAsAuditProvenance() {
        RangeFact first = fact(
                LocalDate.of(2026, 8, 18),
                WorkTimeAccountingMode.SUMMARIZED,
                480,
                480,
                120
        );
        var result = resolveWithFacts(proportional(), first);
        assertTrue(result.ready());
        assertEquals(List.of(first), result.rangeFacts());
        assertEquals("11,12", result.rangeFacts().get(0).plannedDayEntryIds());
        assertEquals("21", result.rangeFacts().get(0).actualWorkIntervalIds());
    }

    @Test
    void rangeFactOutsideLegalWindowBlocksEvenIfRangeEnvelopeClaimsExactWindow() {
        var policy = policy(List.of(proportional()));
        PayrollSourceSnapshot source = source(FROM, TO);
        when(timeCompensation.payrollSource(user, FROM, TO)).thenReturn(source);
        RangeResult range = mock(RangeResult.class);
        when(range.from()).thenReturn(FROM);
        when(range.to()).thenReturn(TO);
        when(range.ready()).thenReturn(true);
        when(range.facts()).thenReturn(List.of(
                fact(EVENT, WorkTimeAccountingMode.SUMMARIZED, 480, 480, 0)
        ));
        when(scheduledWork.deriveRange(user, source)).thenReturn(range);
        var result = service.resolve(user, policy);
        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventBonusWorkTimeFactService.RANGE_WINDOW_MISMATCH,
                result.blockingReason()
        );
        assertEquals(EVENT, result.blockingDate());
    }

    @Test
    void legalRegimeOutsidePp540WindowFailsClosed() {
        LocalDate outside = LocalDate.of(2031, 9, 1);
        var policy = policyFor(outside, List.of(proportional()));
        assertThrows(UnsupportedOperationException.class, () -> service.resolve(user, policy));
        verifyNoInteractions(timeCompensation, scheduledWork);
    }

    private AverageEarningsParagraph7PreEventBonusWorkTimeFactService.Resolution resolveWithFacts(
            Decision decision,
            RangeFact... facts
    ) {
        var policy = policy(List.of(decision));
        PayrollSourceSnapshot source = source(FROM, TO);
        when(timeCompensation.payrollSource(user, FROM, TO)).thenReturn(source);
        when(scheduledWork.deriveRange(user, source))
                .thenReturn(RangeResult.ready(FROM, TO, List.of(facts)));
        return service.resolve(user, policy);
    }

    private AverageEarningsParagraph7PreEventBonusP15Policy.Resolution policy(
            List<Decision> decisions
    ) {
        return policyFor(EVENT, decisions);
    }

    private AverageEarningsParagraph7PreEventBonusP15Policy.Resolution policyFor(
            LocalDate eventDate,
            List<Decision> decisions
    ) {
        var policy = mock(AverageEarningsParagraph7PreEventBonusP15Policy.Resolution.class);
        LocalDate from = YearMonth.from(eventDate).atDay(1);
        when(policy.eventDate()).thenReturn(eventDate);
        when(policy.periodFrom()).thenReturn(from);
        when(policy.cutoffExclusive()).thenReturn(eventDate);
        when(policy.ready()).thenReturn(true);
        when(policy.decisions()).thenReturn(decisions);
        return policy;
    }

    private AverageEarningsParagraph7PreEventBonusP15Policy.Resolution blockedPolicy(
            String reason
    ) {
        var policy = policy(List.of());
        when(policy.ready()).thenReturn(false);
        when(policy.blockingReason()).thenReturn(reason);
        return policy;
    }

    private Decision proportional() {
        return decision(true, IncompletePreEventTreatment.PROPORTIONAL_TO_PRE_EVENT_WORKED_TIME);
    }

    private Decision requireExplicit() {
        return decision(true, IncompletePreEventTreatment.REQUIRE_EXPLICIT_ACTUAL_WORK_ACCRUAL_FACT);
    }

    private Decision noAdjustment() {
        return decision(
                true,
                IncompletePreEventTreatment.NO_ADJUSTMENT_ALREADY_ACCRUED_FOR_ACTUAL_PRE_EVENT_TIME
        );
    }

    private Decision excluded() {
        return decision(false, null);
    }

    private Decision decision(
            boolean included,
            IncompletePreEventTreatment treatment
    ) {
        Decision decision = mock(Decision.class);
        when(decision.included()).thenReturn(included);
        when(decision.incompletePreEventTreatment()).thenReturn(treatment);
        return decision;
    }

    private PayrollSourceSnapshot source(LocalDate from, LocalDate to) {
        return new PayrollSourceSnapshot(
                from,
                to,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                List.of()
        );
    }

    private RangeFact fact(
            LocalDate date,
            WorkTimeAccountingMode mode,
            int schedule,
            int inside,
            int outside
    ) {
        return new RangeFact(
                date,
                42L,
                LocalDate.of(2025, 1, 1),
                mode,
                PayrollSnapshotP15ScheduledWorkSourceKind.EXPLICIT_ACTUAL,
                schedule,
                inside + outside,
                inside,
                schedule,
                inside,
                schedule - inside,
                outside,
                true,
                "11,12",
                "21",
                "a".repeat(64)
        );
    }
}
