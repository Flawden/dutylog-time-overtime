package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollQualifiedQuantity;
import ru.daniil.shifts.service.HolidayPayQualifiedCauseAuthorityService.BlockerKind;
import ru.daniil.shifts.service.HolidayPayQualifiedCauseAuthorityService.Cause;
import ru.daniil.shifts.service.OrdinaryWorkPremiumSourceService.OrdinaryPremiumSource;
import ru.daniil.shifts.service.OrdinaryWorkPremiumSourceService.SourceKind;
import ru.daniil.shifts.service.OrdinaryWorkPremiumSourceService.SourcePiece;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

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

class HolidayPayQualifiedCauseAuthorityServiceTest {

    private static final YearMonth MONTH = YearMonth.of(2026, 2);
    private static final String DATASET_SHA =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String PACK_SHA =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    private final OrdinaryWorkPremiumSourceService ordinary =
            mock(OrdinaryWorkPremiumSourceService.class);
    private final StatutoryPublicHolidayAuthorityService statutory =
            mock(StatutoryPublicHolidayAuthorityService.class);
    private final EmployeeRestDayAuthorityService rest =
            mock(EmployeeRestDayAuthorityService.class);

    private final AppUser user =
            new AppUser(
                    "holiday-qualified-owner",
                    "{noop}unused"
            );

    private final HolidayPayQualifiedCauseAuthorityService service =
            new HolidayPayQualifiedCauseAuthorityService(
                    ordinary,
                    statutory,
                    rest
            );

    private final AtomicLong actualId = new AtomicLong(100L);

    @BeforeEach
    void defaultEveryDayToZeroRegularWork() {
        when(
                ordinary.project(
                        eq(user),
                        any(LocalDate.class)
                )
        ).thenAnswer(invocation ->
                ready(
                        invocation.getArgument(1),
                        List.of()
                )
        );
    }

    @Test
    void publicHolidayOnlyQualifiesPaidRegularMinutesWithExplicitCause() {
        LocalDate date = LocalDate.of(2026, 2, 23);
        when(ordinary.project(user, date)).thenReturn(ready(date, List.of(piece(date, 480, false))));
        when(statutory.resolve(user, date)).thenReturn(publicHoliday(date));
        when(rest.resolve(user, date)).thenReturn(workingDay(date));

        var result = service.resolve(user, MONTH);

        assertTrue(result.ready());
        assertEquals(PayrollQualifiedQuantity.minutes(480), result.quantity());
        assertEquals(1, result.pieces().size());
        assertEquals(Cause.PUBLIC_HOLIDAY, result.pieces().get(0).cause());
        assertTrue(result.pieces().get(0).statutoryResolution().nonWorkingPublicHoliday());
        assertFalse(result.pieces().get(0).restDayResolution().restDay());
    }

    @Test
    void employeeRestDayOnlyQualifiesWithCompleteStatutoryNegativeEvidence() {
        LocalDate date = LocalDate.of(2026, 2, 14);
        when(ordinary.project(user, date)).thenReturn(ready(date, List.of(piece(date, 300, false))));
        when(statutory.resolve(user, date)).thenReturn(notPublicHoliday(date));
        when(rest.resolve(user, date)).thenReturn(restDay(date));

        var result = service.resolve(user, MONTH);

        assertTrue(result.ready());
        assertEquals(PayrollQualifiedQuantity.minutes(300), result.quantity());
        assertEquals(Cause.EMPLOYEE_REST_DAY, result.pieces().get(0).cause());
        assertTrue(result.pieces().get(0).statutoryResolution().provenNotPublicHoliday());
        assertTrue(result.pieces().get(0).restDayResolution().restDay());
    }

    @Test
    void bothLegalCausesCountOnePaidRegularPieceExactlyOnce() {
        LocalDate date = LocalDate.of(2026, 2, 23);
        when(ordinary.project(user, date)).thenReturn(ready(date, List.of(piece(date, 480, false))));
        when(statutory.resolve(user, date)).thenReturn(publicHoliday(date));
        when(rest.resolve(user, date)).thenReturn(restDay(date));

        var result = service.resolve(user, MONTH);

        assertTrue(result.ready());
        assertEquals(480L, result.quantity().value());
        assertEquals(1, result.pieces().size());
        assertEquals(Cause.BOTH, result.pieces().get(0).cause());
    }

    @Test
    void ordinaryWorkingDayIsExcludedEvenWhenLegacyHolidayDimensionIsTrue() {
        LocalDate date = LocalDate.of(2026, 2, 10);
        when(ordinary.project(user, date)).thenReturn(ready(date, List.of(piece(date, 480, true))));
        when(statutory.resolve(user, date)).thenReturn(notPublicHoliday(date));
        when(rest.resolve(user, date)).thenReturn(workingDay(date));

        var result = service.resolve(user, MONTH);

        assertTrue(result.ready());
        assertEquals(PayrollQualifiedQuantity.minutes(0), result.quantity());
        assertTrue(result.pieces().isEmpty());
    }

    @Test
    void statutoryHolidayQualifiesEvenWhenLegacyHolidayDimensionIsFalse() {
        LocalDate date = LocalDate.of(2026, 2, 23);
        when(ordinary.project(user, date)).thenReturn(ready(date, List.of(piece(date, 120, false))));
        when(statutory.resolve(user, date)).thenReturn(publicHoliday(date));
        when(rest.resolve(user, date)).thenReturn(workingDay(date));

        var result = service.resolve(user, MONTH);

        assertTrue(result.ready());
        assertEquals(120L, result.quantity().value());
        assertEquals(Cause.PUBLIC_HOLIDAY, result.pieces().get(0).cause());
    }

    @Test
    void blockedOrdinarySourceFailsClosedWithoutPartialQuantity() {
        LocalDate date = LocalDate.of(2026, 2, 11);
        when(ordinary.project(user, date)).thenReturn(
                OrdinaryPremiumSource.blocked(
                        date,
                        SourceKind.EXPLICIT,
                        480,
                        OrdinaryWorkPremiumSourceService.BLOCK_EXPLICIT_IDENTITY
                )
        );

        var result = service.resolve(user, MONTH);

        assertFalse(result.ready());
        assertNull(result.quantity());
        assertTrue(result.pieces().isEmpty());
        assertEquals(BlockerKind.SOURCE, result.blockers().get(0).kind());
        verify(statutory, never()).resolve(user, date);
        verify(rest, never()).resolve(user, date);
    }

    @Test
    void unresolvedStatutoryAuthorityFailsClosed() {
        LocalDate date = LocalDate.of(2026, 2, 12);
        when(ordinary.project(user, date)).thenReturn(ready(date, List.of(piece(date, 60, false))));
        when(statutory.resolve(user, date)).thenReturn(
                StatutoryPublicHolidayAuthorityService.Resolution.unresolved(
                        date,
                        StatutoryPublicHolidayAuthorityService.REGIONAL_AUTHORITY_MISSING + ":" + date
                )
        );
        when(rest.resolve(user, date)).thenReturn(workingDay(date));

        var result = service.resolve(user, MONTH);

        assertFalse(result.ready());
        assertEquals(BlockerKind.STATUTORY_PUBLIC_HOLIDAY, result.blockers().get(0).kind());
        assertTrue(result.blockers().get(0).reason().contains("REGIONAL_AUTHORITY_MISSING"));
    }

    @Test
    void unresolvedEmployeeRestDayAuthorityFailsClosed() {
        LocalDate date = LocalDate.of(2026, 2, 13);
        when(ordinary.project(user, date)).thenReturn(ready(date, List.of(piece(date, 60, false))));
        when(statutory.resolve(user, date)).thenReturn(notPublicHoliday(date));
        when(rest.resolve(user, date)).thenReturn(
                new EmployeeRestDayAuthorityService.Resolution(
                        EmployeeRestDayAuthorityService.Status.UNRESOLVED,
                        EmployeeRestDayAuthorityService.AuthorityKind.NONE,
                        date,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        EmployeeRestDayAuthorityService.ROSTER_MISSING + ":" + date
                )
        );

        var result = service.resolve(user, MONTH);

        assertFalse(result.ready());
        assertEquals(BlockerKind.EMPLOYEE_REST_DAY, result.blockers().get(0).kind());
        assertTrue(result.blockers().get(0).reason().contains("ROSTER_MISSING"));
    }

    @Test
    void zeroRegularDayNeedsNoHolidayOrRestDayAuthority() {
        LocalDate date = LocalDate.of(2026, 2, 15);
        when(ordinary.project(user, date)).thenReturn(ready(date, List.of()));

        var result = service.resolve(user, MONTH);

        assertTrue(result.ready());
        assertEquals(0L, result.quantity().value());
        verify(statutory, never()).resolve(user, date);
        verify(rest, never()).resolve(user, date);
    }

    @Test
    void multiplePiecesAndMultipleDatesAggregateWithoutDoubleCountingBoth() {
        LocalDate first = LocalDate.of(2026, 2, 21);
        LocalDate second = LocalDate.of(2026, 2, 23);

        when(ordinary.project(user, first)).thenReturn(
                ready(
                        first,
                        List.of(
                                piece(first, 120, false),
                                piece(first, 180, false)
                        )
                )
        );
        when(statutory.resolve(user, first)).thenReturn(notPublicHoliday(first));
        when(rest.resolve(user, first)).thenReturn(restDay(first));

        when(ordinary.project(user, second)).thenReturn(
                ready(second, List.of(piece(second, 480, false)))
        );
        when(statutory.resolve(user, second)).thenReturn(publicHoliday(second));
        when(rest.resolve(user, second)).thenReturn(restDay(second));

        var result = service.resolve(user, MONTH);

        assertTrue(result.ready());
        assertEquals(780L, result.quantity().value());
        assertEquals(3, result.pieces().size());
        assertEquals(Cause.EMPLOYEE_REST_DAY, result.pieces().get(0).cause());
        assertEquals(Cause.EMPLOYEE_REST_DAY, result.pieces().get(1).cause());
        assertEquals(Cause.BOTH, result.pieces().get(2).cause());
    }

    private OrdinaryPremiumSource ready(
            LocalDate date,
            List<SourcePiece> pieces
    ) {
        return OrdinaryPremiumSource.ready(
                date,
                SourceKind.EXPLICIT,
                pieces.stream().mapToInt(SourcePiece::minutes).sum(),
                pieces
        );
    }

    private SourcePiece piece(
            LocalDate date,
            int minutes,
            boolean legacyHoliday
    ) {
        long id = actualId.incrementAndGet();
        Instant start = date.atTime(8, 0).toInstant(ZoneOffset.UTC).plusSeconds(id);
        Instant end = start.plusSeconds(minutes * 60L);

        return new SourcePiece(
                date,
                SourceKind.EXPLICIT,
                id,
                null,
                start,
                end,
                "UTC",
                minutes,
                false,
                legacyHoliday
        );
    }

    private StatutoryPublicHolidayAuthorityService.Resolution publicHoliday(
            LocalDate date
    ) {
        return new StatutoryPublicHolidayAuthorityService.Resolution(
                date,
                StatutoryPublicHolidayAuthorityService.Status.NON_WORKING_PUBLIC_HOLIDAY,
                null,
                new StatutoryPublicHolidayAuthorityService.Provenance(
                        1L,
                        "RU",
                        "RU-KYA",
                        StatutoryPublicHolidayAuthorityService.AuthorityKind.FEDERAL_ARTICLE_112,
                        "RU_TK_RF_ARTICLE_112_CALENDAR_2026_V1",
                        "TK RF Article 112",
                        "2026-v1",
                        "federal-source",
                        "DEFENDER_OF_FATHERLAND_DAY",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );
    }

    private StatutoryPublicHolidayAuthorityService.Resolution notPublicHoliday(
            LocalDate date
    ) {
        return new StatutoryPublicHolidayAuthorityService.Resolution(
                date,
                StatutoryPublicHolidayAuthorityService.Status.NOT_NON_WORKING_PUBLIC_HOLIDAY,
                null,
                new StatutoryPublicHolidayAuthorityService.Provenance(
                        1L,
                        "RU",
                        "RU-KYA",
                        StatutoryPublicHolidayAuthorityService.AuthorityKind.REGIONAL_DATASET,
                        "RU-KYA-2026",
                        "complete federal plus regional review",
                        "RU-KYA-2026-v1",
                        "regional-source-pack",
                        null,
                        501L,
                        DATASET_SHA,
                        true,
                        RegionalStatutoryHolidayDatasetService.SOURCE_PACK_SCHEMA_V1,
                        PACK_SHA,
                        "EXHAUSTIVE REGIONAL LEGAL REVIEW 2026",
                        null
                )
        );
    }

    private EmployeeRestDayAuthorityService.Resolution restDay(
            LocalDate date
    ) {
        return new EmployeeRestDayAuthorityService.Resolution(
                EmployeeRestDayAuthorityService.Status.REST_DAY,
                EmployeeRestDayAuthorityService.AuthorityKind.DATED_ROSTER,
                date,
                1001L,
                2001L,
                null,
                null,
                null,
                null,
                null
        );
    }

    private EmployeeRestDayAuthorityService.Resolution workingDay(
            LocalDate date
    ) {
        return new EmployeeRestDayAuthorityService.Resolution(
                EmployeeRestDayAuthorityService.Status.WORKING_DAY,
                EmployeeRestDayAuthorityService.AuthorityKind.DATED_ROSTER,
                date,
                1002L,
                2002L,
                null,
                null,
                null,
                null,
                null
        );
    }
}
