package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.TimeCompensationService.PayrollSourceDay;
import ru.daniil.shifts.service.TimeCompensationService.PayrollSourceSnapshot;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AverageEarningsParagraph7PreEventWorkFactServiceTest {

    private static final LocalDate EVENT = LocalDate.of(2026, 8, 20);
    private static final LocalDate FROM = LocalDate.of(2026, 8, 1);
    private static final LocalDate THROUGH = LocalDate.of(2026, 8, 19);

    @Mock
    private TimeCompensationService timeCompensation;

    @Mock
    private AppUser user;

    private AverageEarningsParagraph7PreEventWorkFactService service;

    @BeforeEach
    void setUp() {
        service = new AverageEarningsParagraph7PreEventWorkFactService(
                timeCompensation
        );
    }

    @Test
    void constructorRequiresTimeCompensationAuthority() {
        assertThrows(
                NullPointerException.class,
                () -> new AverageEarningsParagraph7PreEventWorkFactService(null)
        );
    }

    @Test
    void resolveRequiresUser() {
        assertThrows(
                NullPointerException.class,
                () -> service.resolve(null, EVENT)
        );
    }

    @Test
    void resolveRequiresEventDate() {
        assertThrows(
                NullPointerException.class,
                () -> service.resolve(user, null)
        );
    }

    @Test
    void firstDayOfMonthHasEmptyPreEventWindowWithoutReadingPayrollSource() {
        LocalDate first = LocalDate.of(2026, 8, 1);

        var result = service.resolve(user, first);

        assertEquals(first, result.eventDate());
        assertEquals(first, result.periodFrom());
        assertEquals(first, result.cutoffExclusive());
        assertEquals(0, result.workedDayCount());
        assertEquals(0L, result.workedMinutes());
        assertFalse(result.workedTimePresent());
        assertTrue(result.workedDays().isEmpty());
        verifyNoInteractions(timeCompensation);
    }

    @Test
    void readsOnlyMonthStartThroughDayBeforeEvent() {
        PayrollSourceSnapshot source = source(
                List.of(day(LocalDate.of(2026, 8, 4), 480, 480, 480)),
                480
        );
        when(timeCompensation.payrollSource(user, FROM, THROUGH))
                .thenReturn(source);

        service.resolve(user, EVENT);

        verify(timeCompensation).payrollSource(user, FROM, THROUGH);
        verifyNoMoreInteractions(timeCompensation);
    }

    @Test
    void countsOnlyDaysWithPositiveActuallyWorkedMinutes() {
        PayrollSourceSnapshot source = source(
                List.of(
                        day(LocalDate.of(2026, 8, 3), 480, 480, 480),
                        day(LocalDate.of(2026, 8, 4), 480, 0, 0),
                        day(LocalDate.of(2026, 8, 5), 360, 300, 300)
                ),
                780
        );
        when(timeCompensation.payrollSource(user, FROM, THROUGH))
                .thenReturn(source);

        var result = service.resolve(user, EVENT);

        assertEquals(2, result.workedDayCount());
        assertEquals(780L, result.workedMinutes());
        assertTrue(result.workedTimePresent());
        assertEquals(
                List.of(
                        LocalDate.of(2026, 8, 3),
                        LocalDate.of(2026, 8, 5)
                ),
                result.workedDays().stream()
                        .map(AverageEarningsParagraph7PreEventWorkFactService.WorkedDayFact::date)
                        .toList()
        );
    }

    @Test
    void paidAbsenceWithoutWorkedMinutesDoesNotBecomeParagraph7WorkedDay() {
        PayrollSourceDay absenceOnly = new PayrollSourceDay(
                LocalDate.of(2026, 8, 6),
                480,
                0,
                480,
                0,
                0,
                0,
                0
        );
        PayrollSourceSnapshot source = snapshot(
                FROM,
                THROUGH,
                480,
                0,
                480,
                0,
                0,
                0,
                0,
                480,
                480,
                480,
                List.of(absenceOnly)
        );
        when(timeCompensation.payrollSource(user, FROM, THROUGH))
                .thenReturn(source);

        var result = service.resolve(user, EVENT);

        assertEquals(0, result.workedDayCount());
        assertEquals(0L, result.workedMinutes());
        assertFalse(result.workedTimePresent());
    }

    @Test
    void zeroWorkSourceIsReadyZeroAuthorityRatherThanBlocker() {
        PayrollSourceSnapshot source = source(List.of(), 0);
        when(timeCompensation.payrollSource(user, FROM, THROUGH))
                .thenReturn(source);

        var result = service.resolve(user, EVENT);

        assertEquals(0, result.workedDayCount());
        assertEquals(0L, result.workedMinutes());
        assertTrue(result.workedDays().isEmpty());
    }

    @Test
    void nullPayrollSourceIsStructuralFailure() {
        when(timeCompensation.payrollSource(user, FROM, THROUGH))
                .thenReturn(null);

        assertThrows(
                NullPointerException.class,
                () -> service.resolve(user, EVENT)
        );
    }

    @Test
    void sourceStartMustMatchLegalEventMonthStart() {
        PayrollSourceSnapshot source = snapshot(
                FROM.plusDays(1),
                THROUGH,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                List.of()
        );
        when(timeCompensation.payrollSource(user, FROM, THROUGH))
                .thenReturn(source);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.resolve(user, EVENT)
        );

        assertEquals(
                AverageEarningsParagraph7PreEventWorkFactService.SOURCE_WINDOW_MISMATCH,
                ex.getMessage()
        );
    }

    @Test
    void sourceEndMustBeDayImmediatelyBeforeEvent() {
        PayrollSourceSnapshot source = snapshot(
                FROM,
                THROUGH.minusDays(1),
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                List.of()
        );
        when(timeCompensation.payrollSource(user, FROM, THROUGH))
                .thenReturn(source);

        assertThrows(
                IllegalStateException.class,
                () -> service.resolve(user, EVENT)
        );
    }

    @Test
    void sourceDayCannotReachEventDate() {
        PayrollSourceSnapshot source = source(
                List.of(day(EVENT, 480, 480, 480)),
                480
        );
        when(timeCompensation.payrollSource(user, FROM, THROUGH))
                .thenReturn(source);

        assertThrows(
                IllegalStateException.class,
                () -> service.resolve(user, EVENT)
        );
    }

    @Test
    void sourceDayCannotPrecedeEventMonth() {
        PayrollSourceSnapshot source = source(
                List.of(day(FROM.minusDays(1), 480, 480, 480)),
                480
        );
        when(timeCompensation.payrollSource(user, FROM, THROUGH))
                .thenReturn(source);

        assertThrows(
                IllegalStateException.class,
                () -> service.resolve(user, EVENT)
        );
    }

    @Test
    void sourceDaysMustBeStrictlyChronological() {
        PayrollSourceSnapshot source = source(
                List.of(
                        day(LocalDate.of(2026, 8, 5), 480, 480, 480),
                        day(LocalDate.of(2026, 8, 4), 480, 480, 480)
                ),
                960
        );
        when(timeCompensation.payrollSource(user, FROM, THROUGH))
                .thenReturn(source);

        assertThrows(
                IllegalStateException.class,
                () -> service.resolve(user, EVENT)
        );
    }

    @Test
    void negativeSourceMinutesAreRejected() {
        PayrollSourceSnapshot source = source(
                List.of(day(LocalDate.of(2026, 8, 5), 480, -1, 0)),
                -1
        );
        when(timeCompensation.payrollSource(user, FROM, THROUGH))
                .thenReturn(source);

        assertThrows(
                IllegalStateException.class,
                () -> service.resolve(user, EVENT)
        );
    }

    @Test
    void hourlyBaseWorkedMinutesCannotExceedWorkedMinutes() {
        PayrollSourceSnapshot source = source(
                List.of(day(LocalDate.of(2026, 8, 5), 480, 300, 301)),
                300
        );
        when(timeCompensation.payrollSource(user, FROM, THROUGH))
                .thenReturn(source);

        assertThrows(
                IllegalStateException.class,
                () -> service.resolve(user, EVENT)
        );
    }

    @Test
    void workedMinuteAggregateMustMatchCanonicalPayrollSource() {
        PayrollSourceSnapshot source = source(
                List.of(day(LocalDate.of(2026, 8, 5), 480, 300, 300)),
                301
        );
        when(timeCompensation.payrollSource(user, FROM, THROUGH))
                .thenReturn(source);

        assertThrows(
                IllegalStateException.class,
                () -> service.resolve(user, EVENT)
        );
    }

    @Test
    void workedDayFactPreservesPlannedAndHourlyBaseAuditQuantities() {
        PayrollSourceSnapshot source = source(
                List.of(day(LocalDate.of(2026, 8, 5), 420, 360, 300)),
                360
        );
        when(timeCompensation.payrollSource(user, FROM, THROUGH))
                .thenReturn(source);

        var fact = service.resolve(user, EVENT).workedDays().get(0);

        assertEquals(420, fact.plannedMinutes());
        assertEquals(360, fact.workedMinutes());
        assertEquals(300, fact.hourlyBaseWorkedMinutes());
    }

    private static PayrollSourceDay day(
            LocalDate date,
            int planned,
            int worked,
            int hourlyBaseWorked
    ) {
        return new PayrollSourceDay(
                date,
                planned,
                worked,
                0,
                0,
                0,
                0,
                hourlyBaseWorked
        );
    }

    private static PayrollSourceSnapshot source(
            List<PayrollSourceDay> days,
            int workedMinutes
    ) {
        return snapshot(
                FROM,
                THROUGH,
                0,
                workedMinutes,
                0,
                0,
                0,
                0,
                0,
                0,
                Math.max(0, workedMinutes),
                Math.max(0, workedMinutes),
                days
        );
    }

    private static PayrollSourceSnapshot snapshot(
            LocalDate from,
            LocalDate to,
            int plannedMinutes,
            int workedMinutes,
            int vacationMinutes,
            int sickMinutes,
            int overtimeCompensatedMinutes,
            int unpaidMinutes,
            int timeAdjustmentMinutes,
            int paidAbsenceMinutes,
            int payableMinutes,
            int hourlyBasePayableMinutes,
            List<PayrollSourceDay> days
    ) {
        return new PayrollSourceSnapshot(
                from,
                to,
                plannedMinutes,
                workedMinutes,
                vacationMinutes,
                sickMinutes,
                overtimeCompensatedMinutes,
                unpaidMinutes,
                timeAdjustmentMinutes,
                paidAbsenceMinutes,
                payableMinutes,
                hourlyBasePayableMinutes,
                days
        );
    }
}
