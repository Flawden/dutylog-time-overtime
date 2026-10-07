package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.*;
import ru.daniil.shifts.repo.*;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductionCalendarBulkReadTest {
    @Test void readsMonthOnceAndLocalNormalSuppressesBaseOverride() {
        var repository = mock(ProductionCalendarDayRepository.class);
        var norm = mock(WorkNormService.class);
        var user = new AppUser("bulk", "hash");
        var schedule = new DayEntry(user, LocalDate.of(2026,10,1));
        var from = LocalDate.of(2026, 10, 1); var to = from.plusDays(30);
        var base = new ProductionCalendarDay(user, from, "BASE");
        base.update("HOLIDAY", "NORM_OVERRIDE", 0, "NONE", null, "CUSTOM", null);
        var local = new ProductionCalendarDay(user, from, "LOCAL_OVERRIDE");
        local.update("NORMAL", "NONE", null, "NONE", null, "CUSTOM", null);
        when(repository.findByOwnerAndDateBetweenOrderByDateAscLayerAsc(user, from, to)).thenReturn(List.of(local, base));
        when(norm.basePlannedMinutes(schedule)).thenReturn(480);
        var service = new ProductionCalendarService(repository, mock(DayEntryRepository.class), norm,
                mock(AccountingPeriodLockService.class), mock(WorkdayDerivedCompensationService.class));
        var resolved = service.effectiveDays(user, from, to);
        for (var date = from; !date.isAfter(to); date = date.plusDays(1)) {
            assertEquals(480, service.requiredMinutes(user, date, schedule, resolved));
        }
        verify(repository, times(1)).findByOwnerAndDateBetweenOrderByDateAscLayerAsc(user, from, to);
        verifyNoMoreInteractions(repository);
    }
    @Test void appliesZeroAndShortenedNormWithoutChangingSchedule() {
        var norm = mock(WorkNormService.class); var schedule = new DayEntry(new AppUser("bulk", "hash"), LocalDate.of(2026,10,1));
        when(norm.basePlannedMinutes(schedule)).thenReturn(480);
        var date = LocalDate.of(2026,10,1); var day = new ProductionCalendarDay(new AppUser("bulk", "hash"), date, "LOCAL_OVERRIDE");
        day.update("SHORTENED_DAY", "NORM_OVERRIDE", 420, "NONE", null, "CUSTOM", null);
        var service = new ProductionCalendarService(mock(ProductionCalendarDayRepository.class), mock(DayEntryRepository.class), norm,
                mock(AccountingPeriodLockService.class), mock(WorkdayDerivedCompensationService.class));
        assertEquals(420, service.requiredMinutes(new AppUser("bulk", "hash"), date, schedule, java.util.Map.of(date, day)));
        day.update("HOLIDAY", "NORM_OVERRIDE", 0, "NONE", null, "CUSTOM", null);
        assertEquals(0, service.requiredMinutes(new AppUser("bulk", "hash"), date, schedule, java.util.Map.of(date, day)));
    }
}
