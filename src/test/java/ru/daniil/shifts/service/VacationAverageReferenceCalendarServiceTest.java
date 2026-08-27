package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.dto.Dtos.ProductionCalendarDayDto;
import ru.daniil.shifts.model.AppUser;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VacationAverageReferenceCalendarServiceTest {

    private EmploymentHistoryService employment;
    private AverageEarningsReferenceFactsService referenceFacts;
    private ProductionCalendarService productionCalendar;
    private VacationAverageReferenceCalendarService service;
    private AppUser user;

    private final LocalDate eventDate =
            LocalDate.of(
                    2026,
                    8,
                    14
            );

    private final YearMonth eventMonth =
            YearMonth.of(
                    2026,
                    8
            );

    private final LocalDate referenceFrom =
            LocalDate.of(
                    2025,
                    8,
                    1
            );

    private final LocalDate referenceTo =
            LocalDate.of(
                    2026,
                    7,
                    31
            );

    @BeforeEach
    void setUp() {
        employment =
                mock(
                        EmploymentHistoryService.class
                );

        referenceFacts =
                mock(
                        AverageEarningsReferenceFactsService.class
                );

        productionCalendar =
                mock(
                        ProductionCalendarService.class
                );

        user =
                mock(
                        AppUser.class
                );

        service =
                new VacationAverageReferenceCalendarService(
                        employment,
                        referenceFacts,
                        productionCalendar
                );
    }

    @Test
    void unconfiguredEmploymentFailsClosedBeforeAbsenceAssembly() {
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

        assertThrows(
                IllegalStateException.class,
                () ->
                        service.resolve(
                                user,
                                eventDate
                        )
        );

        verifyNoInteractions(
                referenceFacts,
                productionCalendar
        );
    }

    @Test
    void fullEmploymentWithoutAbsencesFeedsTwelveFullMonths() {
        stubEmployment(
                referenceFrom,
                referenceTo
        );

        stubFacts(
                List.of()
        );

        var result =
                service.resolve(
                        user,
                        eventDate
                );

        assertEquals(
                12,
                result.months().size()
        );

        for (var month
                : result.months()) {

            assertEquals(
                    month.month().lengthOfMonth(),
                    month.countableCalendarDays()
            );
        }

        assertTrue(
                result.excludedDates().isEmpty()
        );

        assertTrue(
                result.retainedVacationHolidayDates().isEmpty()
        );

        assertExact(
                result.denominator().denominatorDays(),
                1758,
                5
        );

        verifyNoInteractions(
                productionCalendar
        );
    }

    @Test
    void employmentStartMidMonthProducesExactZeroAndPartialMonths() {
        LocalDate hireDate =
                LocalDate.of(
                        2026,
                        1,
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
                                        10L,
                                        hireDate,
                                        null,
                                        hireDate,
                                        referenceTo
                                )
                        )
                )
        );

        stubFacts(
                List.of()
        );

        var result =
                service.resolve(
                        user,
                        eventDate
                );

        assertEquals(
                0,
                countable(
                        result,
                        YearMonth.of(
                                2025,
                                12
                        )
                )
        );

        assertEquals(
                17,
                countable(
                        result,
                        YearMonth.of(
                                2026,
                                1
                        )
                )
        );

        assertEquals(
                28,
                countable(
                        result,
                        YearMonth.of(
                                2026,
                                2
                        )
                )
        );

        assertEquals(
                31,
                countable(
                        result,
                        YearMonth.of(
                                2026,
                                7
                        )
                )
        );
    }

    @Test
    void vacationHolidayInsidePhysicalSpanRemainsCountable() {
        stubEmployment(
                referenceFrom,
                referenceTo
        );

        LocalDate from =
                LocalDate.of(
                        2026,
                        1,
                        10
                );

        LocalDate holiday =
                LocalDate.of(
                        2026,
                        1,
                        11
                );

        LocalDate to =
                LocalDate.of(
                        2026,
                        1,
                        12
                );

        stubFacts(
                List.of(
                        absence(
                                20L,
                                "VACATION",
                                "VACATION_DAYS",
                                "VACATION_ALLOWANCE",
                                "FULL_DAY",
                                from,
                                to
                        )
                )
        );

        when(
                productionCalendar.resolvedDay(
                        user,
                        from
                )
        ).thenReturn(
                productionDay(
                        from,
                        "NORMAL"
                )
        );

        when(
                productionCalendar.resolvedDay(
                        user,
                        holiday
                )
        ).thenReturn(
                productionDay(
                        holiday,
                        "HOLIDAY"
                )
        );

        when(
                productionCalendar.resolvedDay(
                        user,
                        to
                )
        ).thenReturn(
                productionDay(
                        to,
                        "NORMAL"
                )
        );

        var result =
                service.resolve(
                        user,
                        eventDate
                );

        assertEquals(
                29,
                countable(
                        result,
                        YearMonth.of(
                                2026,
                                1
                        )
                )
        );

        assertEquals(
                List.of(
                        from,
                        to
                ),
                result.excludedDates()
        );

        assertEquals(
                List.of(
                        holiday
                ),
                result.retainedVacationHolidayDates()
        );
    }

    @Test
    void federalHolidayCannotBeErasedByProductionCalendarNormalOverride() {
        stubEmployment(
                referenceFrom,
                referenceTo
        );

        LocalDate from =
                LocalDate.of(
                        2026,
                        2,
                        22
                );

        LocalDate federalHoliday =
                LocalDate.of(
                        2026,
                        2,
                        23
                );

        LocalDate to =
                LocalDate.of(
                        2026,
                        2,
                        24
                );

        stubFacts(
                List.of(
                        absence(
                                25L,
                                "VACATION",
                                "VACATION_DAYS",
                                "VACATION_ALLOWANCE",
                                "FULL_DAY",
                                from,
                                to
                        )
                )
        );

        when(
                productionCalendar.resolvedDay(
                        user,
                        from
                )
        ).thenReturn(
                productionDay(
                        from,
                        "NORMAL"
                )
        );

        when(
                productionCalendar.resolvedDay(
                        user,
                        to
                )
        ).thenReturn(
                productionDay(
                        to,
                        "NORMAL"
                )
        );

        var result =
                service.resolve(
                        user,
                        eventDate
                );

        /*
         * February 2026 contains 28 calendar days.
         * The physical vacation span is 22..24 February.
         * 23 February is an Article-112 federal holiday and therefore
         * remains countable; only 22 and 24 are excluded.
         */
        assertEquals(
                26,
                countable(
                        result,
                        YearMonth.of(
                                2026,
                                2
                        )
                )
        );

        assertEquals(
                List.of(
                        from,
                        to
                ),
                result.excludedDates()
        );

        assertEquals(
                List.of(
                        federalHoliday
                ),
                result.retainedVacationHolidayDates()
        );

        verify(
                productionCalendar,
                never()
        ).resolvedDay(
                user,
                federalHoliday
        );
    }

    @Test
    void overlappingFullDayExclusionsAreUnionedByCalendarDate() {
        stubEmployment(
                referenceFrom,
                referenceTo
        );

        stubFacts(
                List.of(
                        absence(
                                30L,
                                "SICK",
                                "NONE",
                                "SICK_PAY",
                                "FULL_DAY",
                                LocalDate.of(
                                        2026,
                                        1,
                                        10
                                ),
                                LocalDate.of(
                                        2026,
                                        1,
                                        12
                                )
                        ),
                        absence(
                                31L,
                                "UNPAID",
                                "NONE",
                                "UNPAID",
                                "FULL_DAY",
                                LocalDate.of(
                                        2026,
                                        1,
                                        12
                                ),
                                LocalDate.of(
                                        2026,
                                        1,
                                        13
                                )
                        )
                )
        );

        var result =
                service.resolve(
                        user,
                        eventDate
                );

        assertEquals(
                27,
                countable(
                        result,
                        YearMonth.of(
                                2026,
                                1
                        )
                )
        );

        assertEquals(
                4,
                result.excludedDates().size()
        );

        verifyNoInteractions(
                productionCalendar
        );
    }

    @Test
    void partialAndHoursOnlyAbsencesFailClosedWithoutCalendarDayInference() {
        stubEmployment(
                referenceFrom,
                referenceTo
        );

        LocalDate date =
                LocalDate.of(
                        2026,
                        1,
                        15
                );

        when(
                referenceFacts.resolve(
                        user,
                        eventMonth
                )
        ).thenReturn(
                facts(
                        List.of(
                                absence(
                                        40L,
                                        "TIME_OFF",
                                        "TIME_OFF_HOURS",
                                        "OVERTIME_BANK",
                                        "PARTIAL",
                                        date,
                                        date
                                )
                        )
                ),
                facts(
                        List.of(
                                absence(
                                        41L,
                                        "TIME_OFF",
                                        "TIME_OFF_HOURS",
                                        "OVERTIME_BANK",
                                        "HOURS_ONLY",
                                        date,
                                        date
                                )
                        )
                )
        );

        assertThrows(
                IllegalStateException.class,
                () ->
                        service.resolve(
                                user,
                                eventDate
                        )
        );

        assertThrows(
                IllegalStateException.class,
                () ->
                        service.resolve(
                                user,
                                eventDate
                        )
        );

        verifyNoInteractions(
                productionCalendar
        );
    }

    @Test
    void unresolvedLegalAbsenceFailsClosed() {
        stubEmployment(
                referenceFrom,
                referenceTo
        );

        LocalDate date =
                LocalDate.of(
                        2026,
                        1,
                        15
                );

        stubFacts(
                List.of(
                        absence(
                                50L,
                                null,
                                "NONE",
                                "NONE",
                                "FULL_DAY",
                                date,
                                date
                        )
                )
        );

        assertThrows(
                IllegalStateException.class,
                () ->
                        service.resolve(
                                user,
                                eventDate
                        )
        );

        verifyNoInteractions(
                productionCalendar
        );
    }

    @Test
    void postedAbsenceOutsideConfiguredEmploymentFailsClosed() {
        LocalDate hireDate =
                LocalDate.of(
                        2026,
                        1,
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
                                        60L,
                                        hireDate,
                                        null,
                                        hireDate,
                                        referenceTo
                                )
                        )
                )
        );

        stubFacts(
                List.of(
                        absence(
                                61L,
                                "SICK",
                                "NONE",
                                "SICK_PAY",
                                "FULL_DAY",
                                LocalDate.of(
                                        2026,
                                        1,
                                        10
                                ),
                                LocalDate.of(
                                        2026,
                                        1,
                                        12
                                )
                        )
                )
        );

        assertThrows(
                IllegalStateException.class,
                () ->
                        service.resolve(
                                user,
                                eventDate
                        )
        );

        verifyNoInteractions(
                productionCalendar
        );
    }

    private void stubEmployment(
            LocalDate from,
            LocalDate to
    ) {
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
                                        from,
                                        to,
                                        from,
                                        to
                                )
                        )
                )
        );
    }

    private void stubFacts(
            List<AverageEarningsReferenceFactsService.AbsenceFact> absences
    ) {
        when(
                referenceFacts.resolve(
                        user,
                        eventMonth
                )
        ).thenReturn(
                facts(
                        absences
                )
        );
    }

    private AverageEarningsReferenceFactsService.ReferenceFacts facts(
            List<AverageEarningsReferenceFactsService.AbsenceFact> absences
    ) {
        return new AverageEarningsReferenceFactsService.ReferenceFacts(
                eventMonth,
                referenceFrom,
                referenceTo,
                absences
        );
    }

    private AverageEarningsReferenceFactsService.AbsenceFact absence(
            long id,
            String systemCode,
            String balancePolicy,
            String compensationPolicy,
            String coverage,
            LocalDate from,
            LocalDate to
    ) {
        LocalTime startTime =
                "PARTIAL".equals(
                        coverage
                )
                        ? LocalTime.of(
                        10,
                        0
                )
                        : null;

        LocalTime endTime =
                "PARTIAL".equals(
                        coverage
                )
                        ? LocalTime.of(
                        12,
                        0
                )
                        : null;

        Integer knownMinutes =
                null;

        if ("PARTIAL".equals(
                coverage
        )) {
            knownMinutes =
                    Integer.valueOf(
                            120
                    );
        } else if ("HOURS_ONLY".equals(
                coverage
        )) {
            knownMinutes =
                    Integer.valueOf(
                            95
                    );
        }

        int chargedMinutes =
                "HOURS_ONLY".equals(
                        coverage
                )
                        ? 95
                        : 0;

        return new AverageEarningsReferenceFactsService.AbsenceFact(
                id,
                systemCode,
                balancePolicy,
                compensationPolicy,
                "COMPLETED",
                coverage,
                from,
                to,
                from,
                to,
                startTime,
                endTime,
                chargedMinutes,
                0,
                knownMinutes
        );
    }

    private ProductionCalendarDayDto productionDay(
            LocalDate date,
            String dayKind
    ) {
        return new ProductionCalendarDayDto(
                date.toString(),
                dayKind,
                "NONE",
                null,
                "NONE",
                null,
                "NONE",
                null,
                false,
                0,
                0,
                0
        );
    }

    private int countable(
            VacationAverageReferenceCalendarService.Result result,
            YearMonth month
    ) {
        return result
                .months()
                .stream()
                .filter(
                        fact ->
                                fact.month()
                                        .equals(
                                                month
                                        )
                )
                .findFirst()
                .orElseThrow()
                .countableCalendarDays();
    }

    private void assertExact(
            VacationAverageCalendarDenominator.ExactDays actual,
            long numerator,
            long denominator
    ) {
        assertEquals(
                BigInteger.valueOf(
                        numerator
                ),
                actual.numerator()
        );

        assertEquals(
                BigInteger.valueOf(
                        denominator
                ),
                actual.denominator()
        );
    }
}
