package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AbsencePeriod;
import ru.daniil.shifts.model.AbsenceType;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.AbsencePeriodRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AverageEarningsReferenceFactsServiceTest {

    private AbsencePeriodRepository absences;
    private LedgerIntegrityService ledgerIntegrity;
    private AverageEarningsReferenceFactsService service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        absences =
                mock(
                        AbsencePeriodRepository.class
                );

        ledgerIntegrity =
                mock(
                        LedgerIntegrityService.class
                );

        user =
                mock(
                        AppUser.class
                );

        when(
                ledgerIntegrity.posts(
                        anyString()
                )
        ).thenAnswer(invocation ->
                Set.of(
                        "APPROVED",
                        "COMPLETED"
                ).contains(
                        invocation.getArgument(0)
                )
        );

        when(
                ledgerIntegrity.normalizeStatus(
                        anyString()
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        service =
                new AverageEarningsReferenceFactsService(
                        absences,
                        ledgerIntegrity
                );
    }

    @Test
    void referenceWindowUsesTwelvePrecedingCalendarMonthsAndOnlyPostedFacts() {
        YearMonth eventMonth =
                YearMonth.of(
                        2026,
                        8
                );

        LocalDate expectedFrom =
                LocalDate.of(
                        2025,
                        8,
                        1
                );

        LocalDate expectedTo =
                LocalDate.of(
                        2026,
                        7,
                        31
                );

        doReturn(
                List.of(
                                        period(
                                                1L,
                                                "PLANNED",
                                                "VACATION",
                                                "VACATION_DAYS",
                                                "VACATION_ALLOWANCE",
                                                "FULL_DAY",
                                                LocalDate.of(2025, 8, 1),
                                                LocalDate.of(2025, 8, 2),
                                                null,
                                                null,
                                                0,
                                                0
                                        ),
                                        period(
                                                2L,
                                                "SUBMITTED",
                                                "SICK",
                                                "NONE",
                                                "SICK_PAY",
                                                "FULL_DAY",
                                                LocalDate.of(2025, 9, 1),
                                                LocalDate.of(2025, 9, 1),
                                                null,
                                                null,
                                                0,
                                                0
                                        ),
                                        period(
                                                3L,
                                                "APPROVED",
                                                "VACATION",
                                                "VACATION_DAYS",
                                                "VACATION_ALLOWANCE",
                                                "FULL_DAY",
                                                LocalDate.of(2026, 1, 1),
                                                LocalDate.of(2026, 1, 5),
                                                null,
                                                null,
                                                0,
                                                0
                                        ),
                                        period(
                                                4L,
                                                "COMPLETED",
                                                "SICK",
                                                "NONE",
                                                "SICK_PAY",
                                                "FULL_DAY",
                                                LocalDate.of(2026, 3, 1),
                                                LocalDate.of(2026, 3, 2),
                                                null,
                                                null,
                                                0,
                                                0
                                        )
                                )
        ).when(
                absences
        ).findByOwnerAndEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDateAscIdAsc(
                                user,
                                expectedFrom,
                                expectedTo
                        );

        var result =
                service.resolve(
                        user,
                        eventMonth
                );

        assertEquals(
                eventMonth,
                result.eventMonth()
        );

        assertEquals(
                expectedFrom,
                result.referenceFrom()
        );

        assertEquals(
                expectedTo,
                result.referenceTo()
        );

        assertEquals(
                List.of(
                        3L,
                        4L
                ),
                result
                        .absences()
                        .stream()
                        .map(
                                AverageEarningsReferenceFactsService
                                        .AbsenceFact::periodId
                        )
                        .toList()
        );

        verify(
                absences
        ).findByOwnerAndEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDateAscIdAsc(
                user,
                expectedFrom,
                expectedTo
        );
    }

    @Test
    void explicitPreviousReferenceWindowQueriesItsOwnCalendarBounds() {
        YearMonth eventMonth = YearMonth.of(2026, 8);
        AverageEarningsReferenceWindow window = new AverageEarningsReferenceWindow(
                eventMonth,
                YearMonth.of(2024, 8),
                YearMonth.of(2025, 7)
        );

        when(absences.findByOwnerAndEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDateAscIdAsc(
                user,
                LocalDate.of(2024, 8, 1),
                LocalDate.of(2025, 7, 31)
        )).thenReturn(List.of());

        var result = service.resolve(user, window);

        assertEquals(eventMonth, result.eventMonth());
        assertEquals(LocalDate.of(2024, 8, 1), result.referenceFrom());
        assertEquals(LocalDate.of(2025, 7, 31), result.referenceTo());
        verify(absences).findByOwnerAndEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDateAscIdAsc(
                user,
                LocalDate.of(2024, 8, 1),
                LocalDate.of(2025, 7, 31)
        );
    }

    @Test
    void machineFactsAndSourceBoundarySurviveWithoutDisplayInference() {
        doReturn(
                List.of(
                                        period(
                                                7L,
                                                "APPROVED",
                                                null,
                                                "NONE",
                                                "NO_COMPENSATION",
                                                "FULL_DAY",
                                                LocalDate.of(2025, 7, 29),
                                                LocalDate.of(2025, 8, 3),
                                                null,
                                                null,
                                                0,
                                                0
                                        )
                                )
        ).when(
                absences
        ).findByOwnerAndEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDateAscIdAsc(
                                eq(user),
                                any(),
                                any()
                        );

        var fact =
                service
                        .resolve(
                                user,
                                YearMonth.of(
                                        2026,
                                        8
                                )
                        )
                        .absences()
                        .get(0);

        assertNull(
                fact.systemCode()
        );

        assertEquals(
                "NONE",
                fact.balancePolicy()
        );

        assertEquals(
                "NO_COMPENSATION",
                fact.compensationPolicy()
        );

        assertEquals(
                "APPROVED",
                fact.status()
        );

        assertEquals(
                "FULL_DAY",
                fact.coverage()
        );

        assertEquals(
                LocalDate.of(
                        2025,
                        7,
                        29
                ),
                fact.sourceFrom()
        );

        assertEquals(
                LocalDate.of(
                        2025,
                        8,
                        3
                ),
                fact.sourceTo()
        );

        assertEquals(
                LocalDate.of(
                        2025,
                        8,
                        1
                ),
                fact.overlapFrom()
        );

        assertEquals(
                LocalDate.of(
                        2025,
                        8,
                        3
                ),
                fact.overlapTo()
        );

        assertNull(
                fact.knownMinutes(),
                "FULL_DAY must never invent schedule minutes"
        );
    }

    @Test
    void knownMinutesExistOnlyWhenStoredFactsMakeThemExact() {
        doReturn(
                List.of(
                                        period(
                                                11L,
                                                "APPROVED",
                                                "OTHER",
                                                "NONE",
                                                "NO_COMPENSATION",
                                                "PARTIAL",
                                                LocalDate.of(2026, 1, 10),
                                                LocalDate.of(2026, 1, 10),
                                                LocalTime.of(10, 0),
                                                LocalTime.of(12, 30),
                                                0,
                                                0
                                        ),
                                        period(
                                                12L,
                                                "APPROVED",
                                                "TIME_OFF",
                                                "TIME_OFF_HOURS",
                                                "OVERTIME_BANK",
                                                "HOURS_ONLY",
                                                LocalDate.of(2026, 1, 11),
                                                LocalDate.of(2026, 1, 11),
                                                null,
                                                null,
                                                95,
                                                95
                                        ),
                                        period(
                                                13L,
                                                "APPROVED",
                                                "VACATION",
                                                "VACATION_DAYS",
                                                "VACATION_ALLOWANCE",
                                                "FULL_DAY",
                                                LocalDate.of(2026, 1, 12),
                                                LocalDate.of(2026, 1, 12),
                                                null,
                                                null,
                                                0,
                                                0
                                        )
                                )
        ).when(
                absences
        ).findByOwnerAndEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDateAscIdAsc(
                                eq(user),
                                any(),
                                any()
                        );

        List<
                AverageEarningsReferenceFactsService.AbsenceFact
        > facts =
                service
                        .resolve(
                                user,
                                YearMonth.of(
                                        2026,
                                        8
                                )
                        )
                        .absences();

        assertEquals(
                150,
                facts
                        .get(0)
                        .knownMinutes()
        );

        assertEquals(
                95,
                facts
                        .get(1)
                        .knownMinutes()
        );

        assertNull(
                facts
                        .get(2)
                        .knownMinutes()
        );
    }

    @Test
    void malformedPostedPartialFactFailsClosed() {
        doReturn(
                List.of(
                                        period(
                                                21L,
                                                "APPROVED",
                                                "OTHER",
                                                "NONE",
                                                "NO_COMPENSATION",
                                                "PARTIAL",
                                                LocalDate.of(2026, 2, 1),
                                                LocalDate.of(2026, 2, 1),
                                                null,
                                                null,
                                                0,
                                                0
                                        )
                                )
        ).when(
                absences
        ).findByOwnerAndEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDateAscIdAsc(
                                eq(user),
                                any(),
                                any()
                        );

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                service.resolve(
                                        user,
                                        YearMonth.of(
                                                2026,
                                                8
                                        )
                                )
                );

        assertTrue(
                error
                        .getMessage()
                        .contains(
                                "PARTIAL"
                        )
        );
    }

    @Test
    void exactRangeUsesCallerBoundsAndClipsPostedAbsence() {
        YearMonth eventMonth = YearMonth.of(2026, 8);
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 13);

        AbsencePeriod spanning = period(
                31L,
                "COMPLETED",
                "SICK",
                "NONE",
                "SICK_PAY",
                "FULL_DAY",
                LocalDate.of(2026, 7, 29),
                LocalDate.of(2026, 8, 20),
                null,
                null,
                0,
                0
        );

        when(absences.findByOwnerAndEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDateAscIdAsc(
                user,
                from,
                to
        )).thenReturn(List.of(spanning));

        var result = service.resolveRange(user, eventMonth, from, to);

        assertEquals(eventMonth, result.eventMonth());
        assertEquals(from, result.referenceFrom());
        assertEquals(to, result.referenceTo());
        assertEquals(1, result.absences().size());
        assertEquals(from, result.absences().get(0).overlapFrom());
        assertEquals(to, result.absences().get(0).overlapTo());
        verify(absences).findByOwnerAndEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDateAscIdAsc(
                user,
                from,
                to
        );
    }

    @Test
    void exactRangeRejectsReversedBoundsBeforeRepositoryRead() {
        LocalDate from = LocalDate.of(2026, 8, 13);
        LocalDate to = LocalDate.of(2026, 8, 1);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.resolveRange(
                        user,
                        YearMonth.of(2026, 8),
                        from,
                        to
                )
        );

        verifyNoInteractions(absences);
    }

    @Test
    void missingIdentityInputsFailClosed() {
        assertThrows(
                NullPointerException.class,
                () ->
                        service.resolve(
                                null,
                                YearMonth.of(
                                        2026,
                                        8
                                )
                        )
        );

        assertThrows(
                NullPointerException.class,
                () ->
                        service.resolve(
                                user,
                                (YearMonth) null
                        )
        );
    }

    private AbsencePeriod period(
            long id,
            String status,
            String systemCode,
            String balancePolicy,
            String compensationPolicy,
            String coverage,
            LocalDate from,
            LocalDate to,
            LocalTime startTime,
            LocalTime endTime,
            int chargedMinutes,
            int compensatedMinutes
    ) {
        AbsenceType type =
                mock(
                        AbsenceType.class
                );

        when(
                type.getSystemCode()
        ).thenReturn(
                systemCode
        );

        when(
                type.getBalancePolicy()
        ).thenReturn(
                balancePolicy
        );

        AbsencePeriod period =
                mock(
                        AbsencePeriod.class
                );

        when(
                period.getId()
        ).thenReturn(
                id
        );

        when(
                period.getType()
        ).thenReturn(
                type
        );

        when(
                period.getStatus()
        ).thenReturn(
                status
        );

        when(
                period.getCoverage()
        ).thenReturn(
                coverage
        );

        when(
                period.getStartDate()
        ).thenReturn(
                from
        );

        when(
                period.getEndDate()
        ).thenReturn(
                to
        );

        when(
                period.getStartTime()
        ).thenReturn(
                startTime
        );

        when(
                period.getEndTime()
        ).thenReturn(
                endTime
        );

        when(
                period.getCompensationPolicy()
        ).thenReturn(
                compensationPolicy
        );

        when(
                period.getChargedMinutes()
        ).thenReturn(
                chargedMinutes
        );

        when(
                period.getCompensatedMinutes()
        ).thenReturn(
                compensatedMinutes
        );

        return period;
    }
}
