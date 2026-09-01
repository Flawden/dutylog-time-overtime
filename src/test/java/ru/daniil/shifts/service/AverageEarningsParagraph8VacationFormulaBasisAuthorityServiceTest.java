package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.dto.Dtos.ProductionCalendarMonthDto;
import ru.daniil.shifts.model.AppUser;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AverageEarningsParagraph8VacationFormulaBasisAuthorityServiceTest {

    private static final LocalDate EVENT = LocalDate.of(2026, 8, 14);
    private static final YearMonth EVENT_MONTH = YearMonth.of(2026, 8);

    private ProductionCalendarService productionCalendar;
    private AverageEarningsParagraph8VacationFormulaBasisAuthorityService service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        productionCalendar = mock(ProductionCalendarService.class);
        user = mock(AppUser.class);
        service = new AverageEarningsParagraph8VacationFormulaBasisAuthorityService(
                productionCalendar
        );
    }

    @Test
    void monthlyOfficialSalaryBuildsExplicitFormulaBasisWithoutCalendarReads() {
        var result = service.resolve(user, EVENT, salaryAuthority(EVENT));

        assertTrue(result.ready());
        assertEquals(
                VacationAverageUnifiedDailyResolver.Paragraph8FormulaPolicy
                        .MONTHLY_OFFICIAL_SALARY_DIV_29_3,
                result.basis().policy()
        );
        assertEquals(
                AverageEarningsParagraph8VacationFormulaBasisAuthorityService.MONTHLY_SALARY_RULE_ID,
                result.basis().authorityCode()
        );
        assertNull(result.annualNormMinutes());
        assertTrue(result.annualNormMonths().isEmpty());
        verifyNoInteractions(productionCalendar);
    }

    @Test
    void hourlyTariffSumsTwelveCompleteProductionNormMonths() {
        when(productionCalendar.month(eq(user), anyString())).thenAnswer(invocation -> {
            YearMonth month = YearMonth.parse(invocation.getArgument(1));
            return month(month, 10_000 + month.getMonthValue());
        });

        var result = service.resolve(user, EVENT, hourlyAuthority(EVENT));

        long expected = 0L;
        for (int month = 1; month <= 12; month++) {
            expected += 10_000 + month;
        }

        assertTrue(result.ready());
        assertEquals(expected, result.annualNormMinutes());
        assertEquals(12, result.annualNormMonths().size());
        assertEquals(expected, result.basis().annualNormMinutes());
        assertEquals(
                VacationAverageUnifiedDailyResolver.Paragraph8FormulaPolicy
                        .HOURLY_TARIFF_AVERAGE_MONTHLY_NORM_DIV_29_3,
                result.basis().policy()
        );
        assertEquals(
                AverageEarningsParagraph8VacationFormulaBasisAuthorityService.HOURLY_TARIFF_RULE_ID,
                result.basis().authorityCode()
        );
        verify(productionCalendar, times(12)).month(eq(user), anyString());
    }

    @Test
    void hourlyTariffReadsOnlyEventCalendarYearJanuaryThroughDecember() {
        when(productionCalendar.month(eq(user), anyString())).thenAnswer(invocation -> {
            YearMonth month = YearMonth.parse(invocation.getArgument(1));
            return month(month, 9_600);
        });

        var result = service.resolve(user, EVENT, hourlyAuthority(EVENT));

        assertTrue(result.ready());
        assertEquals(YearMonth.of(2026, 1), result.annualNormMonths().get(0).month());
        assertEquals(YearMonth.of(2026, 12), result.annualNormMonths().get(11).month());
        for (var fact : result.annualNormMonths()) {
            assertEquals(2026, fact.month().getYear());
        }
    }

    @Test
    void incompleteScheduleCoverageFailsClosedBeforeLaterMonths() {
        when(productionCalendar.month(user, "2026-01")).thenReturn(
                month(YearMonth.of(2026, 1), 10_000)
        );
        when(productionCalendar.month(user, "2026-02")).thenReturn(
                incompleteMonth(YearMonth.of(2026, 2), 9_000)
        );

        var result = service.resolve(user, EVENT, hourlyAuthority(EVENT));

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph8VacationFormulaBasisAuthorityService
                        .ANNUAL_NORM_SCHEDULE_COVERAGE_INCOMPLETE,
                result.blockingReason()
        );
        assertNull(result.basis());
        verify(productionCalendar, never()).month(user, "2026-03");
    }

    @Test
    void productionCalendarMonthIdentityMismatchFailsClosed() {
        when(productionCalendar.month(user, "2026-01")).thenReturn(
                month(YearMonth.of(2026, 2), 10_000)
        );

        var result = service.resolve(user, EVENT, hourlyAuthority(EVENT));

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph8VacationFormulaBasisAuthorityService
                        .ANNUAL_NORM_MONTH_IDENTITY_MISMATCH,
                result.blockingReason()
        );
    }

    @Test
    void zeroAnnualProductionNormFailsClosed() {
        when(productionCalendar.month(eq(user), anyString())).thenAnswer(invocation -> {
            YearMonth month = YearMonth.parse(invocation.getArgument(1));
            return month(month, 0);
        });

        var result = service.resolve(user, EVENT, hourlyAuthority(EVENT));

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph8VacationFormulaBasisAuthorityService.ANNUAL_NORM_NON_POSITIVE,
                result.blockingReason()
        );
        verify(productionCalendar, times(12)).month(eq(user), anyString());
    }

    @Test
    void blockedUpstreamParagraph8AuthorityDoesNotReadCalendar() {
        var upstream = AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution.blocked(
                EVENT,
                EVENT_MONTH,
                EVENT_MONTH.atDay(1),
                AverageEarningsLegalPolicy.requireRegime(EVENT),
                "UPSTREAM_BLOCK",
                "blocked"
        );

        var result = service.resolve(user, EVENT, upstream);

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph8VacationFormulaBasisAuthorityService.UPSTREAM_AUTHORITY_REQUIRED,
                result.blockingReason()
        );
        verifyNoInteractions(productionCalendar);
    }

    @Test
    void mismatchedReadyUpstreamEventIdentityFailsClosedWithoutCalendar() {
        LocalDate otherEvent = LocalDate.of(2026, 7, 20);

        var result = service.resolve(user, EVENT, hourlyAuthority(otherEvent));

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph8VacationFormulaBasisAuthorityService.UPSTREAM_IDENTITY_MISMATCH,
                result.blockingReason()
        );
        verifyNoInteractions(productionCalendar);
    }

    @Test
    void defensiveFormulaAndProvenanceValidationBranchesAreCovered() {
        var monthlyBasis = VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis.monthlySalary(
                EVENT,
                "RUB",
                AverageEarningsParagraph8VacationFormulaBasisAuthorityService.MONTHLY_SALARY_RULE_ID
        );
        var monthlyWrongAuthority = VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis.monthlySalary(
                EVENT,
                "RUB",
                "OTHER_MONTHLY_AUTHORITY"
        );
        var monthlyUsd = VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis.monthlySalary(
                EVENT,
                "USD",
                AverageEarningsParagraph8VacationFormulaBasisAuthorityService.MONTHLY_SALARY_RULE_ID
        );
        var monthlyOtherEvent = VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis.monthlySalary(
                EVENT.minusDays(1),
                "RUB",
                AverageEarningsParagraph8VacationFormulaBasisAuthorityService.MONTHLY_SALARY_RULE_ID
        );
        var hourlyBasis = VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis.hourlyTariff(
                EVENT,
                "RUB",
                AverageEarningsParagraph8VacationFormulaBasisAuthorityService.HOURLY_TARIFF_RULE_ID,
                120_000L
        );
        var hourlyWrongAuthority = VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis.hourlyTariff(
                EVENT,
                "RUB",
                "OTHER_HOURLY_AUTHORITY",
                120_000L
        );
        var fullYear = completeNormYear(10_000);

        assertThrows(NullPointerException.class, () ->
                new AverageEarningsParagraph8VacationFormulaBasisAuthorityService(null));
        assertThrows(NullPointerException.class, () ->
                service.resolve(null, EVENT, salaryAuthority(EVENT)));
        assertThrows(NullPointerException.class, () ->
                service.resolve(user, null, salaryAuthority(EVENT)));

        var nullUpstream = service.resolve(user, EVENT, null);
        assertFalse(nullUpstream.ready());
        assertEquals(
                AverageEarningsParagraph8VacationFormulaBasisAuthorityService.UPSTREAM_AUTHORITY_REQUIRED,
                nullUpstream.blockingReason()
        );

        when(productionCalendar.month(user, "2026-01")).thenReturn(null);
        assertThrows(
                NullPointerException.class,
                () -> service.resolve(user, EVENT, hourlyAuthority(EVENT))
        );
        reset(productionCalendar);

        when(productionCalendar.month(user, "2026-01")).thenReturn(
                new ProductionCalendarMonthDto(
                        "2026-01", 10_000, 10_000, 0, 0, 0, 0, 0,
                        30, true, List.of()
                )
        );
        var mismatchedCoverage = service.resolve(user, EVENT, hourlyAuthority(EVENT));
        assertFalse(mismatchedCoverage.ready());
        assertEquals(
                AverageEarningsParagraph8VacationFormulaBasisAuthorityService
                        .ANNUAL_NORM_SCHEDULE_COVERAGE_INCOMPLETE,
                mismatchedCoverage.blockingReason()
        );
        reset(productionCalendar);

        when(productionCalendar.month(user, "2026-01")).thenReturn(
                new ProductionCalendarMonthDto(
                        "2026-01", 0, -1, 0, 0, 0, 0, 0,
                        31, true, List.of()
                )
        );
        assertThrows(
                IllegalStateException.class,
                () -> service.resolve(user, EVENT, hourlyAuthority(EVENT))
        );
        reset(productionCalendar);

        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph8VacationFormulaBasisAuthorityService.MonthNormFact(
                        EVENT_MONTH, -1, EVENT_MONTH.lengthOfMonth()
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph8VacationFormulaBasisAuthorityService.MonthNormFact(
                        EVENT_MONTH, 1, EVENT_MONTH.lengthOfMonth() - 1
                ));

        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph8VacationFormulaBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH.minusMonths(1), true,
                        null, null,
                        AverageEarningsParagraph8TariffSalaryAuthorityService.EstablishedBasis.MONTHLY_OFFICIAL_SALARY,
                        "RUB", null, List.of(), monthlyBasis
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph8VacationFormulaBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, true,
                        "BLOCK", null,
                        AverageEarningsParagraph8TariffSalaryAuthorityService.EstablishedBasis.MONTHLY_OFFICIAL_SALARY,
                        "RUB", null, List.of(), monthlyBasis
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph8VacationFormulaBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, true,
                        null, "blocked",
                        AverageEarningsParagraph8TariffSalaryAuthorityService.EstablishedBasis.MONTHLY_OFFICIAL_SALARY,
                        "RUB", null, List.of(), monthlyBasis
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph8VacationFormulaBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, true,
                        null, null, null,
                        "RUB", null, List.of(), monthlyBasis
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph8VacationFormulaBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, true,
                        null, null,
                        AverageEarningsParagraph8TariffSalaryAuthorityService.EstablishedBasis.MONTHLY_OFFICIAL_SALARY,
                        null, null, List.of(), monthlyBasis
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph8VacationFormulaBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, true,
                        null, null,
                        AverageEarningsParagraph8TariffSalaryAuthorityService.EstablishedBasis.MONTHLY_OFFICIAL_SALARY,
                        "rub", null, List.of(), monthlyBasis
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph8VacationFormulaBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, true,
                        null, null,
                        AverageEarningsParagraph8TariffSalaryAuthorityService.EstablishedBasis.MONTHLY_OFFICIAL_SALARY,
                        "RUB", null, List.of(), null
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph8VacationFormulaBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, true,
                        null, null,
                        AverageEarningsParagraph8TariffSalaryAuthorityService.EstablishedBasis.MONTHLY_OFFICIAL_SALARY,
                        "RUB", null, List.of(), hourlyBasis
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph8VacationFormulaBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, true,
                        null, null,
                        AverageEarningsParagraph8TariffSalaryAuthorityService.EstablishedBasis.MONTHLY_OFFICIAL_SALARY,
                        "RUB", null, List.of(), monthlyUsd
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph8VacationFormulaBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, true,
                        null, null,
                        AverageEarningsParagraph8TariffSalaryAuthorityService.EstablishedBasis.MONTHLY_OFFICIAL_SALARY,
                        "RUB", null, List.of(), monthlyOtherEvent
                ));

        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph8VacationFormulaBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, true,
                        null, null,
                        AverageEarningsParagraph8TariffSalaryAuthorityService.EstablishedBasis.MONTHLY_OFFICIAL_SALARY,
                        "RUB", 1L, List.of(), monthlyBasis
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph8VacationFormulaBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, true,
                        null, null,
                        AverageEarningsParagraph8TariffSalaryAuthorityService.EstablishedBasis.MONTHLY_OFFICIAL_SALARY,
                        "RUB", null, List.of(fullYear.get(0)), monthlyBasis
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph8VacationFormulaBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, true,
                        null, null,
                        AverageEarningsParagraph8TariffSalaryAuthorityService.EstablishedBasis.MONTHLY_OFFICIAL_SALARY,
                        "RUB", null, List.of(), monthlyWrongAuthority
                ));

        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph8VacationFormulaBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, true,
                        null, null,
                        AverageEarningsParagraph8TariffSalaryAuthorityService.EstablishedBasis.HOURLY_TARIFF_RATE,
                        "RUB", null, fullYear, hourlyBasis
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph8VacationFormulaBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, true,
                        null, null,
                        AverageEarningsParagraph8TariffSalaryAuthorityService.EstablishedBasis.HOURLY_TARIFF_RATE,
                        "RUB", 0L, fullYear, hourlyBasis
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph8VacationFormulaBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, true,
                        null, null,
                        AverageEarningsParagraph8TariffSalaryAuthorityService.EstablishedBasis.HOURLY_TARIFF_RATE,
                        "RUB", 120_000L, List.of(), hourlyBasis
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph8VacationFormulaBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, true,
                        null, null,
                        AverageEarningsParagraph8TariffSalaryAuthorityService.EstablishedBasis.HOURLY_TARIFF_RATE,
                        "RUB", 120_000L, fullYear, hourlyWrongAuthority
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph8VacationFormulaBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, true,
                        null, null,
                        AverageEarningsParagraph8TariffSalaryAuthorityService.EstablishedBasis.HOURLY_TARIFF_RATE,
                        "RUB", 120_001L, fullYear, hourlyBasis
                ));

        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph8VacationFormulaBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, false,
                        null, "blocked", null, null, null, List.of(), null
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph8VacationFormulaBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, false,
                        " ", "blocked", null, null, null, List.of(), null
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph8VacationFormulaBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, false,
                        "BLOCK", null, null, null, null, List.of(), null
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph8VacationFormulaBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, false,
                        "BLOCK", " ", null, null, null, List.of(), null
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph8VacationFormulaBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, false,
                        "BLOCK", "blocked",
                        AverageEarningsParagraph8TariffSalaryAuthorityService.EstablishedBasis.MONTHLY_OFFICIAL_SALARY,
                        null, null, List.of(), null
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph8VacationFormulaBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, false,
                        "BLOCK", "blocked", null,
                        "RUB", null, List.of(), null
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph8VacationFormulaBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, false,
                        "BLOCK", "blocked", null,
                        null, 1L, List.of(), null
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph8VacationFormulaBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, false,
                        "BLOCK", "blocked", null,
                        null, null, List.of(fullYear.get(0)), null
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph8VacationFormulaBasisAuthorityService.Resolution(
                        EVENT, EVENT_MONTH, false,
                        "BLOCK", "blocked", null,
                        null, null, List.of(), monthlyBasis
                ));
    }

    private List<AverageEarningsParagraph8VacationFormulaBasisAuthorityService.MonthNormFact> completeNormYear(
            int productionNormMinutes
    ) {
        java.util.ArrayList<AverageEarningsParagraph8VacationFormulaBasisAuthorityService.MonthNormFact> result =
                new java.util.ArrayList<>(12);
        for (int monthNumber = 1; monthNumber <= 12; monthNumber++) {
            YearMonth month = YearMonth.of(EVENT.getYear(), monthNumber);
            result.add(
                    new AverageEarningsParagraph8VacationFormulaBasisAuthorityService.MonthNormFact(
                            month,
                            productionNormMinutes,
                            month.lengthOfMonth()
                    )
            );
        }
        return List.copyOf(result);
    }

    private AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution salaryAuthority(
            LocalDate eventDate
    ) {
        YearMonth month = YearMonth.from(eventDate);
        return AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution.ready(
                eventDate,
                month,
                month.atDay(1),
                AverageEarningsLegalPolicy.requireRegime(eventDate),
                month.minusMonths(3).atDay(1),
                AverageEarningsParagraph8TariffSalaryAuthorityService.EstablishedBasis
                        .MONTHLY_OFFICIAL_SALARY,
                "SALARY",
                "RUB",
                null,
                120_000L
        );
    }

    private AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution hourlyAuthority(
            LocalDate eventDate
    ) {
        YearMonth month = YearMonth.from(eventDate);
        return AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution.ready(
                eventDate,
                month,
                month.atDay(1),
                AverageEarningsLegalPolicy.requireRegime(eventDate),
                month.minusMonths(3).atDay(1),
                AverageEarningsParagraph8TariffSalaryAuthorityService.EstablishedBasis
                        .HOURLY_TARIFF_RATE,
                "HOURLY",
                "RUB",
                750L,
                null
        );
    }

    private ProductionCalendarMonthDto month(
            YearMonth month,
            int productionNormMinutes
    ) {
        return new ProductionCalendarMonthDto(
                month.toString(),
                productionNormMinutes,
                productionNormMinutes,
                0,
                0,
                0,
                0,
                0,
                month.lengthOfMonth(),
                true,
                List.of()
        );
    }

    private ProductionCalendarMonthDto incompleteMonth(
            YearMonth month,
            int productionNormMinutes
    ) {
        return new ProductionCalendarMonthDto(
                month.toString(),
                productionNormMinutes,
                productionNormMinutes,
                0,
                0,
                0,
                0,
                0,
                month.lengthOfMonth() - 1,
                false,
                List.of()
        );
    }
}
