package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.dto.Dtos.ProductionCalendarDayDto;
import ru.daniil.shifts.model.AppUser;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnnualPaidVacationHolidayPolicyTest {
    private ProductionCalendarService productionCalendar;
    private AppUser user;

    @BeforeEach
    void setUp() {
        productionCalendar = mock(ProductionCalendarService.class);
        user = mock(AppUser.class);
    }

    @Test
    void federalHolidayIsLegalBaselineWithoutProductionLookup() {
        LocalDate date = LocalDate.of(2026, 2, 23);
        var fact = AnnualPaidVacationHolidayPolicy.classify(productionCalendar, user, date);
        assertTrue(fact.nonWorkingHoliday());
        assertEquals(AnnualPaidVacationHolidayPolicy.FEDERAL_ARTICLE_112_AUTHORITY, fact.authorityCode());
        assertEquals("HOLIDAY", fact.dayKind());
        verifyNoInteractions(productionCalendar);
    }

    @Test
    void januaryFederalHolidaySetCoversFirstThroughEighth() {
        for (int day = 1; day <= 8; day++) {
            assertTrue(AnnualPaidVacationHolidayPolicy.isFederalNonWorkingHoliday(LocalDate.of(2026, 1, day)));
        }
        assertFalse(AnnualPaidVacationHolidayPolicy.isFederalNonWorkingHoliday(LocalDate.of(2026, 1, 9)));
    }

    @Test
    void configuredRegionalHolidayIsNonWorkingHoliday() {
        LocalDate date = LocalDate.of(2026, 6, 15);
        when(productionCalendar.resolvedDay(user, date)).thenReturn(day(date, "HOLIDAY"));
        var fact = AnnualPaidVacationHolidayPolicy.classify(productionCalendar, user, date);
        assertTrue(fact.nonWorkingHoliday());
        assertEquals(AnnualPaidVacationHolidayPolicy.PRODUCTION_CALENDAR_HOLIDAY_AUTHORITY, fact.authorityCode());
    }

    @Test
    void transferredDayOffIsNotSilentlyPromotedToHoliday() {
        LocalDate date = LocalDate.of(2026, 5, 4);
        when(productionCalendar.resolvedDay(user, date)).thenReturn(day(date, "TRANSFERRED_DAY_OFF"));
        var fact = AnnualPaidVacationHolidayPolicy.classify(productionCalendar, user, date);
        assertFalse(fact.nonWorkingHoliday());
        assertEquals("TRANSFERRED_DAY_OFF", fact.dayKind());
    }

    @Test
    void ordinaryWeekendClassificationRemainsNonHoliday() {
        LocalDate date = LocalDate.of(2026, 8, 30);
        when(productionCalendar.resolvedDay(user, date)).thenReturn(day(date, "NORMAL"));
        var fact = AnnualPaidVacationHolidayPolicy.classify(productionCalendar, user, date);
        assertFalse(fact.nonWorkingHoliday());
        assertEquals(AnnualPaidVacationHolidayPolicy.PRODUCTION_CALENDAR_NON_HOLIDAY_AUTHORITY, fact.authorityCode());
    }

    @Test
    void missingProductionCalendarAuthorityFailsClosed() {
        LocalDate date = LocalDate.of(2026, 8, 31);
        when(productionCalendar.resolvedDay(user, date)).thenReturn(null);
        assertThrows(IllegalStateException.class,
                () -> AnnualPaidVacationHolidayPolicy.classify(productionCalendar, user, date));
    }

    @Test
    void missingProductionDayKindFailsClosed() {
        LocalDate date = LocalDate.of(2026, 8, 31);
        when(productionCalendar.resolvedDay(user, date)).thenReturn(day(date, null));
        assertThrows(IllegalStateException.class,
                () -> AnnualPaidVacationHolidayPolicy.classify(productionCalendar, user, date));
    }

    @Test
    void holidayFactRejectsContradictoryHolidayIdentity() {
        assertThrows(IllegalArgumentException.class,
                () -> new AnnualPaidVacationHolidayPolicy.HolidayFact(
                        LocalDate.of(2026, 8, 31), true, "AUTH", "NORMAL"));
        assertThrows(IllegalArgumentException.class,
                () -> new AnnualPaidVacationHolidayPolicy.HolidayFact(
                        LocalDate.of(2026, 8, 31), false, "AUTH", "HOLIDAY"));
    }

    private ProductionCalendarDayDto day(LocalDate date, String kind) {
        return new ProductionCalendarDayDto(
                date.toString(), kind, "NONE", null, "NONE", null,
                "NONE", null, false, 0, 0, 0);
    }
}
