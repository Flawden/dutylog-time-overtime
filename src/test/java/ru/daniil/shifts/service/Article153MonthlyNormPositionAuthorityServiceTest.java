package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollQualifiedQuantity;
import ru.daniil.shifts.service.Article153EconomicLegalPolicy.NormPosition;
import ru.daniil.shifts.service.Article153EconomicLegalPolicy.PayMode;
import ru.daniil.shifts.service.Article153MonthlyNormPositionAuthorityService.BlockerKind;
import ru.daniil.shifts.service.HistoricalCompensationRateService.HistoricalBaseRate;
import ru.daniil.shifts.service.HolidayPayQualifiedCauseAuthorityService.Cause;
import ru.daniil.shifts.service.HolidayPayQualifiedCauseAuthorityService.QualifiedPiece;
import ru.daniil.shifts.service.OrdinaryWorkPremiumSourceService.SourceKind;
import ru.daniil.shifts.service.OrdinaryWorkPremiumSourceService.SourcePiece;
import ru.daniil.shifts.service.TimeCompensationService.PayrollSourceDay;
import ru.daniil.shifts.service.TimeCompensationService.PayrollSourceSnapshot;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Article153MonthlyNormPositionAuthorityServiceTest {

    private static final YearMonth MONTH = YearMonth.of(2026, 5);

    private final HolidayPayQualifiedCauseAuthorityService qualified =
            mock(HolidayPayQualifiedCauseAuthorityService.class);
    private final HistoricalCompensationRateService rates =
            mock(HistoricalCompensationRateService.class);
    private final TimeCompensationService time =
            mock(TimeCompensationService.class);
    private final AppUser user = mock(AppUser.class);

    private final Article153MonthlyNormPositionAuthorityService service =
            new Article153MonthlyNormPositionAuthorityService(
                    qualified,
                    rates,
                    time
            );

    @BeforeEach
    void defaultQualifiedEmpty() {
        when(qualified.resolve(user, MONTH)).thenReturn(
                new HolidayPayQualifiedCauseAuthorityService.Resolution(
                        MONTH,
                        true,
                        PayrollQualifiedQuantity.minutes(0),
                        List.of(),
                        List.of()
                )
        );
    }

    @Test
    void emptyQualifiedMonthNeedsNoCompensationOrWorkedNormAuthority() {
        var result = service.resolve(user, MONTH);

        assertTrue(result.ready());
        assertEquals(0L, result.quantity().value());
        assertTrue(result.pieces().isEmpty());
        verify(rates, never()).resolve(eq(user), any(LocalDate.class));
        verify(time, never()).payrollSource(eq(user), any(), any());
    }

    @Test
    void hourlyQualifiedPieceIsExplicitlyNotApplicableToMonthlySalaryNorm() {
        LocalDate date = LocalDate.of(2026, 5, 9);
        QualifiedPiece piece = piece(date, 180, 11L);
        qualifiedReady(piece);
        when(rates.resolve(user, date)).thenReturn(hourly(date));

        var result = service.resolve(user, MONTH);

        assertTrue(result.ready());
        assertEquals(PayMode.HOURLY, result.pieces().get(0).payMode());
        assertEquals(NormPosition.NOT_APPLICABLE, result.pieces().get(0).normPosition());
        assertNull(result.pieces().get(0).productionNormMinutes());
        verify(time, never()).payrollSource(eq(user), any(), any());
    }

    @Test
    void salaryQualifiedDayEntirelyBeforeBoundaryIsWithinMonthlyNorm() {
        LocalDate date = LocalDate.of(2026, 5, 9);
        QualifiedPiece piece = piece(date, 120, 12L);
        qualifiedReady(piece);
        when(rates.resolve(user, date)).thenReturn(salary(date, 9_600));
        when(time.payrollSource(user, MONTH.atDay(1), MONTH.atEndOfMonth()))
                .thenReturn(source(
                        day(LocalDate.of(2026, 5, 1), 4_000),
                        day(date, 300)
                ));

        var result = service.resolve(user, MONTH);

        assertTrue(result.ready());
        var norm = result.pieces().get(0);
        assertEquals(NormPosition.WITHIN_MONTHLY_NORM, norm.normPosition());
        assertEquals(9_600, norm.productionNormMinutes());
        assertEquals(4_000L, norm.workedMinutesBeforeDate());
        assertEquals(300, norm.workedMinutesOnDate());
    }

    @Test
    void salaryQualifiedDayEntirelyAfterBoundaryIsAboveMonthlyNorm() {
        LocalDate date = LocalDate.of(2026, 5, 29);
        QualifiedPiece piece = piece(date, 120, 13L);
        qualifiedReady(piece);
        when(rates.resolve(user, date)).thenReturn(salary(date, 9_600));
        when(time.payrollSource(user, MONTH.atDay(1), MONTH.atEndOfMonth()))
                .thenReturn(source(
                        day(LocalDate.of(2026, 5, 28), 9_700),
                        day(date, 120)
                ));

        var result = service.resolve(user, MONTH);

        assertTrue(result.ready());
        assertEquals(
                NormPosition.ABOVE_MONTHLY_NORM,
                result.pieces().get(0).normPosition()
        );
    }

    @Test
    void nonHolidayWorkedMinutesBeforeQualifiedDateConsumeMonthlyNorm() {
        LocalDate date = LocalDate.of(2026, 5, 20);
        QualifiedPiece piece = piece(date, 60, 14L);
        qualifiedReady(piece);
        when(rates.resolve(user, date)).thenReturn(salary(date, 2_000));
        when(time.payrollSource(user, MONTH.atDay(1), MONTH.atEndOfMonth()))
                .thenReturn(source(
                        day(LocalDate.of(2026, 5, 2), 900),
                        day(LocalDate.of(2026, 5, 10), 1_200),
                        day(date, 60)
                ));

        var result = service.resolve(user, MONTH);

        assertTrue(result.ready());
        assertEquals(2_100L, result.pieces().get(0).workedMinutesBeforeDate());
        assertEquals(
                NormPosition.ABOVE_MONTHLY_NORM,
                result.pieces().get(0).normPosition()
        );
    }

    @Test
    void normBoundaryCrossingInsideQualifiedDateFailsClosed() {
        LocalDate date = LocalDate.of(2026, 5, 20);
        qualifiedReady(piece(date, 120, 15L));
        when(rates.resolve(user, date)).thenReturn(salary(date, 2_000));
        when(time.payrollSource(user, MONTH.atDay(1), MONTH.atEndOfMonth()))
                .thenReturn(source(
                        day(LocalDate.of(2026, 5, 19), 1_900),
                        day(date, 300)
                ));

        var result = service.resolve(user, MONTH);

        assertFalse(result.ready());
        assertNull(result.quantity());
        assertTrue(result.pieces().isEmpty());
        assertEquals(BlockerKind.MONTHLY_NORM_BOUNDARY, result.blockers().get(0).kind());
        assertTrue(result.blockers().get(0).reason().contains("before=1900"));
    }

    @Test
    void qualifiedAuthorityBlockerPropagatesWithoutPartialNormResult() {
        LocalDate date = LocalDate.of(2026, 5, 9);
        when(qualified.resolve(user, MONTH)).thenReturn(
                new HolidayPayQualifiedCauseAuthorityService.Resolution(
                        MONTH,
                        false,
                        null,
                        List.of(),
                        List.of(new HolidayPayQualifiedCauseAuthorityService.BlockingDay(
                                date,
                                HolidayPayQualifiedCauseAuthorityService.BlockerKind.SOURCE,
                                "SOURCE_BLOCKED"
                        ))
                )
        );

        var result = service.resolve(user, MONTH);

        assertFalse(result.ready());
        assertEquals(BlockerKind.QUALIFIED_AUTHORITY, result.blockers().get(0).kind());
        verify(rates, never()).resolve(eq(user), any(LocalDate.class));
        verify(time, never()).payrollSource(eq(user), any(), any());
    }

    @Test
    void salaryQualifiedPieceRequiresCanonicalWorkedDay() {
        LocalDate date = LocalDate.of(2026, 5, 9);
        qualifiedReady(piece(date, 60, 16L));
        when(rates.resolve(user, date)).thenReturn(salary(date, 9_600));
        when(time.payrollSource(user, MONTH.atDay(1), MONTH.atEndOfMonth()))
                .thenReturn(source(day(LocalDate.of(2026, 5, 8), 300)));

        var result = service.resolve(user, MONTH);

        assertFalse(result.ready());
        assertEquals(BlockerKind.PAYROLL_SOURCE, result.blockers().get(0).kind());
        assertTrue(result.blockers().get(0).reason().contains("SOURCE_WORKED_DAY_MISSING"));
    }

    @Test
    void payrollSourceWindowMismatchFailsClosed() {
        LocalDate date = LocalDate.of(2026, 5, 9);
        qualifiedReady(piece(date, 60, 17L));
        when(rates.resolve(user, date)).thenReturn(salary(date, 9_600));
        PayrollSourceSnapshot source = new PayrollSourceSnapshot(
                MONTH.atDay(2),
                MONTH.atEndOfMonth(),
                0, 60, 0, 0, 0, 0, 0, 0, 60, 60,
                List.of(day(date, 60))
        );
        when(time.payrollSource(user, MONTH.atDay(1), MONTH.atEndOfMonth()))
                .thenReturn(source);

        var result = service.resolve(user, MONTH);

        assertFalse(result.ready());
        assertTrue(result.blockers().get(0).reason().contains("WINDOW_MISMATCH"));
    }

    @Test
    void payrollSourceWorkedTotalMustEqualItsDayFacts() {
        LocalDate date = LocalDate.of(2026, 5, 9);
        qualifiedReady(piece(date, 60, 18L));
        when(rates.resolve(user, date)).thenReturn(salary(date, 9_600));
        PayrollSourceSnapshot source = new PayrollSourceSnapshot(
                MONTH.atDay(1),
                MONTH.atEndOfMonth(),
                0, 999, 0, 0, 0, 0, 0, 0, 999, 999,
                List.of(day(date, 60))
        );
        when(time.payrollSource(user, MONTH.atDay(1), MONTH.atEndOfMonth()))
                .thenReturn(source);

        var result = service.resolve(user, MONTH);

        assertFalse(result.ready());
        assertTrue(result.blockers().get(0).reason().contains("TOTAL_MISMATCH"));
    }

    @Test
    void monthlyPayModeCannotChangeAcrossQualifiedPieces() {
        LocalDate first = LocalDate.of(2026, 5, 9);
        LocalDate second = LocalDate.of(2026, 5, 10);
        QualifiedPiece firstPiece = piece(first, 60, 19L);
        QualifiedPiece secondPiece = piece(second, 60, 20L);
        when(qualified.resolve(user, MONTH)).thenReturn(
                new HolidayPayQualifiedCauseAuthorityService.Resolution(
                        MONTH,
                        true,
                        PayrollQualifiedQuantity.minutes(120),
                        List.of(firstPiece, secondPiece),
                        List.of()
                )
        );
        when(rates.resolve(user, first)).thenReturn(hourly(first));
        when(rates.resolve(user, second)).thenReturn(salary(second, 9_600));

        var result = service.resolve(user, MONTH);

        assertFalse(result.ready());
        assertEquals(BlockerKind.COMPENSATION, result.blockers().get(0).kind());
        assertTrue(result.blockers().get(0).reason().contains("PAY_MODE_INCONSISTENT"));
    }

    @Test
    void decisionFingerprintIsStableAndSourceIdentitySensitive() {
        LocalDate date = LocalDate.of(2026, 5, 9);
        QualifiedPiece firstPiece = piece(date, 60, 21L);
        qualifiedReady(firstPiece);
        when(rates.resolve(user, date)).thenReturn(hourly(date));

        String first = service.resolve(user, MONTH).pieces().get(0).decisionFingerprint();
        String second = service.resolve(user, MONTH).pieces().get(0).decisionFingerprint();

        assertEquals(first, second);
        assertTrue(first.matches("[0-9a-f]{64}"));
    }

    private void qualifiedReady(QualifiedPiece... pieces) {
        long minutes = List.of(pieces).stream().mapToLong(QualifiedPiece::minutes).sum();
        when(qualified.resolve(user, MONTH)).thenReturn(
                new HolidayPayQualifiedCauseAuthorityService.Resolution(
                        MONTH,
                        true,
                        PayrollQualifiedQuantity.minutes(minutes),
                        List.of(pieces),
                        List.of()
                )
        );
    }

    private QualifiedPiece piece(LocalDate date, int minutes, long id) {
        Instant start = date.atStartOfDay().toInstant(java.time.ZoneOffset.UTC).plusSeconds(id);
        SourcePiece source = new SourcePiece(
                date,
                SourceKind.EXPLICIT,
                id,
                null,
                start,
                start.plusSeconds(minutes * 60L),
                "UTC",
                minutes,
                false,
                false
        );

        var statutory = mock(StatutoryPublicHolidayAuthorityService.Resolution.class);
        var rest = mock(EmployeeRestDayAuthorityService.Resolution.class);
        when(statutory.date()).thenReturn(date);
        when(statutory.ready()).thenReturn(true);
        when(statutory.nonWorkingPublicHoliday()).thenReturn(true);
        when(rest.date()).thenReturn(date);
        when(rest.ready()).thenReturn(true);
        when(rest.restDay()).thenReturn(false);

        return new QualifiedPiece(
                date,
                Cause.PUBLIC_HOLIDAY,
                source,
                statutory,
                rest
        );
    }

    private HistoricalBaseRate hourly(LocalDate date) {
        return new HistoricalBaseRate(
                date,
                MONTH,
                MONTH.atDay(1),
                "HOURLY",
                "RUB",
                50_000L,
                null
        );
    }

    private HistoricalBaseRate salary(LocalDate date, int norm) {
        return new HistoricalBaseRate(
                date,
                MONTH,
                MONTH.atDay(1),
                "SALARY",
                "RUB",
                50_000L,
                norm
        );
    }

    private PayrollSourceDay day(LocalDate date, int worked) {
        return new PayrollSourceDay(
                date,
                0,
                worked,
                0,
                0,
                0,
                0,
                worked
        );
    }

    private PayrollSourceSnapshot source(PayrollSourceDay... days) {
        int worked = List.of(days).stream().mapToInt(PayrollSourceDay::workedMinutes).sum();
        return new PayrollSourceSnapshot(
                MONTH.atDay(1),
                MONTH.atEndOfMonth(),
                0,
                worked,
                0,
                0,
                0,
                0,
                0,
                0,
                worked,
                worked,
                List.of(days)
        );
    }
}
