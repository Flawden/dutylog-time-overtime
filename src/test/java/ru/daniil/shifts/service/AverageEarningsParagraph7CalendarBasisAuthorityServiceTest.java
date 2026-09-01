package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.dto.Dtos.ProductionCalendarDayDto;
import ru.daniil.shifts.model.AppUser;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AverageEarningsParagraph7CalendarBasisAuthorityServiceTest {

    private static final LocalDate EVENT = LocalDate.of(2026, 8, 14);
    private static final LocalDate FROM = LocalDate.of(2026, 8, 1);
    private static final LocalDate TO = LocalDate.of(2026, 8, 13);
    private static final YearMonth EVENT_MONTH = YearMonth.of(2026, 8);

    private EmploymentHistoryService employment;
    private AverageEarningsReferenceFactsService referenceFacts;
    private ProductionCalendarService productionCalendar;
    private AverageEarningsParagraph7CalendarBasisAuthorityService service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        employment = mock(EmploymentHistoryService.class);
        referenceFacts = mock(AverageEarningsReferenceFactsService.class);
        productionCalendar = mock(ProductionCalendarService.class);
        user = mock(AppUser.class);
        service = new AverageEarningsParagraph7CalendarBasisAuthorityService(
                employment,
                referenceFacts,
                productionCalendar
        );
    }

    @Test
    void firstDayOfMonthBlocksWithoutReadingSources() {
        LocalDate event = LocalDate.of(2026, 8, 1);

        var result = service.resolve(user, event);

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7CalendarBasisAuthorityService.NO_PRE_EVENT_RANGE,
                result.blockingReason()
        );
        assertNull(result.basis());
        verifyNoInteractions(employment, referenceFacts, productionCalendar);
    }

    @Test
    void unconfiguredEmploymentBlocksBeforeAbsenceFacts() {
        when(employment.resolve(user, FROM, TO)).thenReturn(
                EmploymentHistoryService.Resolution.unconfigured(FROM, TO)
        );

        var result = service.resolve(user, EVENT);

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7CalendarBasisAuthorityService.EMPLOYMENT_HISTORY_UNCONFIGURED,
                result.blockingReason()
        );
        verifyNoInteractions(referenceFacts, productionCalendar);
    }

    @Test
    void fullPreEventEmploymentCountsCalendarDaysNotWorkedShifts() {
        stubEmployment(FROM, TO);
        stubFacts(List.of());

        var result = service.resolve(user, EVENT);

        assertTrue(result.ready());
        assertEquals(13, result.countableCalendarDays());
        assertEquals(13, result.basis().countableCalendarDays());
        assertEquals(
                AverageEarningsParagraph7CalendarBasisAuthorityService.RULE_ID,
                result.basis().authorityCode()
        );
        assertTrue(result.excludedDates().isEmpty());
        verifyNoInteractions(productionCalendar);
    }

    @Test
    void midMonthEmploymentStartClipsCountableCalendarDays() {
        LocalDate hire = LocalDate.of(2026, 8, 5);
        when(employment.resolve(user, FROM, TO)).thenReturn(
                EmploymentHistoryService.Resolution.configured(
                        FROM,
                        TO,
                        List.of(new EmploymentHistoryService.CoverageSlice(
                                10L,
                                hire,
                                null,
                                hire,
                                TO
                        ))
                )
        );
        stubFacts(List.of());

        var result = service.resolve(user, EVENT);

        assertTrue(result.ready());
        assertEquals(9, result.countableCalendarDays());
    }

    @Test
    void fullDaySickAndUnpaidDatesAreUnionedAndExcluded() {
        stubEmployment(FROM, TO);
        stubFacts(List.of(
                absence(20L, "SICK", "NONE", "SICK_PAY", "FULL_DAY",
                        LocalDate.of(2026, 8, 4), LocalDate.of(2026, 8, 6)),
                absence(21L, "UNPAID", "NONE", "UNPAID", "FULL_DAY",
                        LocalDate.of(2026, 8, 6), LocalDate.of(2026, 8, 7))
        ));

        var result = service.resolve(user, EVENT);

        assertTrue(result.ready());
        assertEquals(9, result.countableCalendarDays());
        assertEquals(List.of(
                LocalDate.of(2026, 8, 4),
                LocalDate.of(2026, 8, 5),
                LocalDate.of(2026, 8, 6),
                LocalDate.of(2026, 8, 7)
        ), result.excludedDates());
        verifyNoInteractions(productionCalendar);
    }

    @Test
    void vacationHolidayInsidePhysicalSpanRemainsCountable() {
        stubEmployment(FROM, TO);
        LocalDate start = LocalDate.of(2026, 8, 10);
        LocalDate holiday = LocalDate.of(2026, 8, 11);
        LocalDate end = LocalDate.of(2026, 8, 12);
        stubFacts(List.of(
                absence(30L, "VACATION", "VACATION_DAYS", "VACATION_ALLOWANCE", "FULL_DAY",
                        start, end)
        ));
        when(productionCalendar.resolvedDay(user, start)).thenReturn(day(start, "NORMAL"));
        when(productionCalendar.resolvedDay(user, holiday)).thenReturn(day(holiday, "HOLIDAY"));
        when(productionCalendar.resolvedDay(user, end)).thenReturn(day(end, "NORMAL"));

        var result = service.resolve(user, EVENT);

        assertTrue(result.ready());
        assertEquals(11, result.countableCalendarDays());
        assertEquals(List.of(start, end), result.excludedDates());
        assertEquals(List.of(holiday), result.retainedVacationHolidayDates());
    }

    @Test
    void federalVacationHolidayIsRetainedWithoutCalendarOverrideLookup() {
        LocalDate event = LocalDate.of(2026, 3, 10);
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 9);
        LocalDate federalHoliday = LocalDate.of(2026, 3, 8);

        when(employment.resolve(user, from, to)).thenReturn(
                EmploymentHistoryService.Resolution.configured(
                        from,
                        to,
                        List.of(new EmploymentHistoryService.CoverageSlice(
                                40L, from, null, from, to
                        ))
                )
        );
        when(referenceFacts.resolveRange(user, YearMonth.of(2026, 3), from, to)).thenReturn(
                new AverageEarningsReferenceFactsService.ReferenceFacts(
                        YearMonth.of(2026, 3),
                        from,
                        to,
                        List.of(absence(
                                41L,
                                "VACATION",
                                "VACATION_DAYS",
                                "VACATION_ALLOWANCE",
                                "FULL_DAY",
                                federalHoliday,
                                federalHoliday
                        ))
                )
        );

        var result = service.resolve(user, event);

        assertTrue(result.ready());
        assertEquals(9, result.countableCalendarDays());
        assertEquals(List.of(federalHoliday), result.retainedVacationHolidayDates());
        verify(productionCalendar, never()).resolvedDay(user, federalHoliday);
    }

    @Test
    void partialAbsenceFailsClosedWithoutFractionalCalendarInference() {
        stubEmployment(FROM, TO);
        LocalDate date = LocalDate.of(2026, 8, 5);
        stubFacts(List.of(
                absence(50L, "TIME_OFF", "TIME_OFF_HOURS", "OVERTIME_BANK", "PARTIAL",
                        date, date)
        ));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.resolve(user, EVENT)
        );

        assertTrue(error.getMessage().contains("FULL_DAY"));
        verifyNoInteractions(productionCalendar);
    }

    @Test
    void postedAbsenceOutsideEmploymentFailsClosed() {
        LocalDate hire = LocalDate.of(2026, 8, 5);
        when(employment.resolve(user, FROM, TO)).thenReturn(
                EmploymentHistoryService.Resolution.configured(
                        FROM,
                        TO,
                        List.of(new EmploymentHistoryService.CoverageSlice(
                                60L, hire, null, hire, TO
                        ))
                )
        );
        stubFacts(List.of(
                absence(61L, "SICK", "NONE", "SICK_PAY", "FULL_DAY",
                        LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 3))
        ));

        assertThrows(IllegalStateException.class, () -> service.resolve(user, EVENT));
        verifyNoInteractions(productionCalendar);
    }

    @Test
    void unresolvedAbsenceFailsClosed() {
        stubEmployment(FROM, TO);
        LocalDate date = LocalDate.of(2026, 8, 5);
        stubFacts(List.of(
                absence(70L, "OTHER", "NONE", "NO_COMPENSATION", "FULL_DAY",
                        date, date)
        ));

        assertThrows(IllegalStateException.class, () -> service.resolve(user, EVENT));
        verifyNoInteractions(productionCalendar);
    }

    @Test
    void defensiveResultValidationBranchesAreCovered() {
        var validBasis = VacationAverageUnifiedDailyResolver.Paragraph7CalendarBasis.of(
                EVENT,
                13,
                AverageEarningsParagraph7CalendarBasisAuthorityService.RULE_ID
        );
        var differentCountBasis = VacationAverageUnifiedDailyResolver.Paragraph7CalendarBasis.of(
                EVENT,
                12,
                AverageEarningsParagraph7CalendarBasisAuthorityService.RULE_ID
        );
        var differentEventBasis = VacationAverageUnifiedDailyResolver.Paragraph7CalendarBasis.of(
                EVENT.minusDays(1),
                12,
                AverageEarningsParagraph7CalendarBasisAuthorityService.RULE_ID
        );
        var differentAuthorityBasis = VacationAverageUnifiedDailyResolver.Paragraph7CalendarBasis.of(
                EVENT,
                13,
                "OTHER_AUTHORITY"
        );

        assertThrows(NullPointerException.class, () ->
                new AverageEarningsParagraph7CalendarBasisAuthorityService(
                        null,
                        referenceFacts,
                        productionCalendar
                ));
        assertThrows(NullPointerException.class, () ->
                new AverageEarningsParagraph7CalendarBasisAuthorityService(
                        employment,
                        null,
                        productionCalendar
                ));
        assertThrows(NullPointerException.class, () ->
                new AverageEarningsParagraph7CalendarBasisAuthorityService(
                        employment,
                        referenceFacts,
                        null
                ));
        assertThrows(NullPointerException.class, () -> service.resolve(null, EVENT));
        assertThrows(NullPointerException.class, () -> service.resolve(user, null));

        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph7CalendarBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH.minusMonths(1), FROM, EVENT,
                        true, null, null, 13, List.of(), List.of(), validBasis
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph7CalendarBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, FROM.plusDays(1), EVENT,
                        true, null, null, 13, List.of(), List.of(), validBasis
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph7CalendarBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, FROM, EVENT.minusDays(1),
                        true, null, null, 13, List.of(), List.of(), validBasis
                ));

        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph7CalendarBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, FROM, EVENT,
                        true, "BLOCK", null, 13, List.of(), List.of(), validBasis
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph7CalendarBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, FROM, EVENT,
                        true, null, "blocked", 13, List.of(), List.of(), validBasis
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph7CalendarBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, FROM, EVENT,
                        true, null, null, 0, List.of(), List.of(), validBasis
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph7CalendarBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, FROM, EVENT,
                        true, null, null, 13, List.of(), List.of(), null
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph7CalendarBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, FROM, EVENT,
                        true, null, null, 13, List.of(), List.of(), differentCountBasis
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph7CalendarBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, FROM, EVENT,
                        true, null, null, 13, List.of(), List.of(), differentEventBasis
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph7CalendarBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, FROM, EVENT,
                        true, null, null, 13, List.of(), List.of(), differentAuthorityBasis
                ));

        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph7CalendarBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, FROM, EVENT,
                        false, null, "blocked", 0, List.of(), List.of(), null
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph7CalendarBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, FROM, EVENT,
                        false, " ", "blocked", 0, List.of(), List.of(), null
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph7CalendarBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, FROM, EVENT,
                        false, "BLOCK", null, 0, List.of(), List.of(), null
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph7CalendarBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, FROM, EVENT,
                        false, "BLOCK", " ", 0, List.of(), List.of(), null
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph7CalendarBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, FROM, EVENT,
                        false, "BLOCK", "blocked", 1, List.of(), List.of(), null
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph7CalendarBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, FROM, EVENT,
                        false, "BLOCK", "blocked", 0, List.of(FROM), List.of(), null
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph7CalendarBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, FROM, EVENT,
                        false, "BLOCK", "blocked", 0, List.of(), List.of(FROM), null
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph7CalendarBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, FROM, EVENT,
                        false, "BLOCK", "blocked", 0, List.of(), List.of(), validBasis
                ));
    }

    private void stubEmployment(LocalDate from, LocalDate to) {
        when(employment.resolve(user, FROM, TO)).thenReturn(
                EmploymentHistoryService.Resolution.configured(
                        FROM,
                        TO,
                        List.of(new EmploymentHistoryService.CoverageSlice(
                                1L,
                                from,
                                null,
                                from,
                                to
                        ))
                )
        );
    }

    private void stubFacts(List<AverageEarningsReferenceFactsService.AbsenceFact> absences) {
        when(referenceFacts.resolveRange(user, EVENT_MONTH, FROM, TO)).thenReturn(
                new AverageEarningsReferenceFactsService.ReferenceFacts(
                        EVENT_MONTH,
                        FROM,
                        TO,
                        absences
                )
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
        LocalTime start = "PARTIAL".equals(coverage) ? LocalTime.of(10, 0) : null;
        LocalTime end = "PARTIAL".equals(coverage) ? LocalTime.of(12, 0) : null;
        Integer knownMinutes = "PARTIAL".equals(coverage) ? 120 : null;
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
                start,
                end,
                0,
                0,
                knownMinutes
        );
    }

    private ProductionCalendarDayDto day(LocalDate date, String kind) {
        return new ProductionCalendarDayDto(
                date.toString(),
                kind,
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
}
