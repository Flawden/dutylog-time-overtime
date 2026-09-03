package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.ProductionCalendarDay;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.ProductionCalendarDayRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmployeeRestDayAuthorityServiceTest {
    DayEntryRepository dayEntries;
    ProductionCalendarDayRepository productionDays;
    EmployeeRestDayAuthorityService service;
    AppUser owner;
    LocalDate date;

    @BeforeEach
    void setUp() {
        dayEntries = mock(DayEntryRepository.class);
        productionDays = mock(ProductionCalendarDayRepository.class);
        service = new EmployeeRestDayAuthorityService(
                dayEntries,
                productionDays
        );
        owner = new AppUser(
                "rest-day-owner",
                "{noop}irrelevant"
        );
        date = LocalDate.of(2026, 8, 8);
    }

    @Test
    void missingDatedRosterFailsClosedInsteadOfGuessingWeekend() {
        var result = service.resolve(owner, date);

        assertFalse(result.ready());
        assertEquals(
                EmployeeRestDayAuthorityService.Status.UNRESOLVED,
                result.status()
        );
        assertEquals(
                EmployeeRestDayAuthorityService.ROSTER_MISSING + ":" + date,
                result.blockingReason()
        );
    }

    @Test
    void canonicalBuiltinOffIsEmployeeRestDay() {
        ShiftType off = builtinOff();
        DayEntry entry = assigned(off);
        when(dayEntries.findByOwnerAndDate(owner, date))
                .thenReturn(Optional.of(entry));

        var result = service.resolve(owner, date);

        assertTrue(result.ready());
        assertTrue(result.restDay());
        assertEquals(
                EmployeeRestDayAuthorityService.AuthorityKind.DATED_ROSTER,
                result.authorityKind()
        );
    }

    @Test
    void ordinaryBuiltinShiftIsWorkingDayEvenThoughWeekdayIsIrrelevant() {
        ShiftType day = new ShiftType(
                owner,
                "Дневная",
                8.0,
                "#111111",
                true,
                LocalTime.of(8, 30),
                LocalTime.of(17, 0),
                30,
                8.0
        );
        when(dayEntries.findByOwnerAndDate(owner, date))
                .thenReturn(Optional.of(assigned(day)));

        var result = service.resolve(owner, date);

        assertTrue(result.ready());
        assertEquals(
                EmployeeRestDayAuthorityService.Status.WORKING_DAY,
                result.status()
        );
    }

    @Test
    void positiveCustomShiftIsWorkingDayButZeroUntimedCustomShiftIsAmbiguous() {
        ShiftType positive = new ShiftType(
                owner,
                "Подмена",
                4.0,
                "#111111",
                false,
                LocalTime.of(10, 0),
                LocalTime.of(14, 0),
                0,
                4.0
        );
        when(dayEntries.findByOwnerAndDate(owner, date))
                .thenReturn(Optional.of(assigned(positive)));

        assertEquals(
                EmployeeRestDayAuthorityService.Status.WORKING_DAY,
                service.resolve(owner, date).status()
        );

        ShiftType ambiguous = new ShiftType(
                owner,
                "Отдых?",
                0.0,
                "#111111",
                false
        );
        when(dayEntries.findByOwnerAndDate(owner, date))
                .thenReturn(Optional.of(assigned(ambiguous)));

        var unresolved = service.resolve(owner, date);
        assertFalse(unresolved.ready());
        assertEquals(
                EmployeeRestDayAuthorityService.ROSTER_AMBIGUOUS + ":" + date,
                unresolved.blockingReason()
        );
    }

    @Test
    void contradictoryCanonicalOffFailsClosed() {
        ShiftType off = new ShiftType(
                owner,
                "Выходной",
                8.0,
                "#111111",
                true,
                LocalTime.of(8, 0),
                LocalTime.of(17, 0),
                0,
                8.0
        );
        when(dayEntries.findByOwnerAndDate(owner, date))
                .thenReturn(Optional.of(assigned(off)));

        var result = service.resolve(owner, date);

        assertFalse(result.ready());
        assertEquals(
                EmployeeRestDayAuthorityService.CANONICAL_OFF_CONTRADICTORY + ":" + date,
                result.blockingReason()
        );
    }

    @Test
    void transferredDayOffOverridesWorkingRosterAndPreservesSourceProvenance() {
        ProductionCalendarDay transfer = transfer(
                "LOCAL_OVERRIDE",
                "TRANSFERRED_DAY_OFF",
                0,
                "CUSTOM",
                "manual-transfer"
        );
        when(productionDays.findByOwnerAndDateAndLayer(owner, date, "LOCAL_OVERRIDE"))
                .thenReturn(Optional.of(transfer));
        when(dayEntries.findByOwnerAndDate(owner, date))
                .thenReturn(Optional.of(assigned(workingCustom())));

        var result = service.resolve(owner, date);

        assertTrue(result.restDay());
        assertEquals(
                EmployeeRestDayAuthorityService.AuthorityKind.PRODUCTION_CALENDAR_TRANSFER,
                result.authorityKind()
        );
        assertEquals("LOCAL_OVERRIDE", result.sourceLayer());
        assertEquals("CUSTOM", result.sourceType());
        assertEquals("manual-transfer", result.sourceRef());
        verify(dayEntries, never()).findByOwnerAndDate(owner, date);
    }

    @Test
    void transferredWorkdayOverridesCanonicalOffRoster() {
        ProductionCalendarDay transfer = transfer(
                "BASE",
                "TRANSFERRED_WORKDAY",
                420,
                "OFFICIAL",
                "gov-transfer"
        );
        when(productionDays.findByOwnerAndDateAndLayer(owner, date, "BASE"))
                .thenReturn(Optional.of(transfer));
        when(dayEntries.findByOwnerAndDate(owner, date))
                .thenReturn(Optional.of(assigned(builtinOff())));

        var result = service.resolve(owner, date);

        assertEquals(
                EmployeeRestDayAuthorityService.Status.WORKING_DAY,
                result.status()
        );
        assertEquals(
                EmployeeRestDayAuthorityService.AuthorityKind.PRODUCTION_CALENDAR_TRANSFER,
                result.authorityKind()
        );
        verify(dayEntries, never()).findByOwnerAndDate(owner, date);
    }

    @Test
    void contradictoryTransferRuleFailsClosedWithoutFallingBackToRoster() {
        ProductionCalendarDay invalid = transfer(
                "LOCAL_OVERRIDE",
                "TRANSFERRED_DAY_OFF",
                420,
                "CUSTOM",
                null
        );
        when(productionDays.findByOwnerAndDateAndLayer(owner, date, "LOCAL_OVERRIDE"))
                .thenReturn(Optional.of(invalid));

        var result = service.resolve(owner, date);

        assertFalse(result.ready());
        assertEquals(
                EmployeeRestDayAuthorityService.TRANSFER_RULE_INVALID + ":" + date,
                result.blockingReason()
        );
        verify(dayEntries, never()).findByOwnerAndDate(owner, date);
    }

    @Test
    void localTransferPrecedesBaseAndPlainHolidayDoesNotBecomeRestDayAuthority() {
        ProductionCalendarDay baseOff = transfer(
                "BASE",
                "TRANSFERRED_DAY_OFF",
                0,
                "OFFICIAL",
                "base"
        );
        ProductionCalendarDay localWork = transfer(
                "LOCAL_OVERRIDE",
                "TRANSFERRED_WORKDAY",
                480,
                "CUSTOM",
                "local"
        );
        when(productionDays.findByOwnerAndDateAndLayer(owner, date, "LOCAL_OVERRIDE"))
                .thenReturn(Optional.of(localWork));
        when(productionDays.findByOwnerAndDateAndLayer(owner, date, "BASE"))
                .thenReturn(Optional.of(baseOff));

        assertEquals(
                EmployeeRestDayAuthorityService.Status.WORKING_DAY,
                service.resolve(owner, date).status()
        );

        ProductionCalendarDay holiday = new ProductionCalendarDay(
                owner,
                date,
                "LOCAL_OVERRIDE"
        );
        holiday.update(
                "HOLIDAY",
                "NORM_OVERRIDE",
                0,
                "HOLIDAY",
                "Праздник",
                "CUSTOM",
                null
        );
        when(productionDays.findByOwnerAndDateAndLayer(owner, date, "LOCAL_OVERRIDE"))
                .thenReturn(Optional.of(holiday));
        when(dayEntries.findByOwnerAndDate(owner, date))
                .thenReturn(Optional.of(assigned(builtinOff())));

        assertTrue(service.resolve(owner, date).restDay());
    }

    private DayEntry assigned(ShiftType shift) {
        DayEntry entry = new DayEntry(owner, date);
        entry.setShiftType(shift);
        return entry;
    }

    private ShiftType builtinOff() {
        return new ShiftType(
                owner,
                "Выходной",
                0.0,
                "#6FBF73",
                true,
                null,
                null,
                0,
                0.0
        );
    }

    private ShiftType workingCustom() {
        return new ShiftType(
                owner,
                "Рабочая",
                8.0,
                "#111111",
                false,
                LocalTime.of(8, 0),
                LocalTime.of(17, 0),
                0,
                8.0
        );
    }

    private ProductionCalendarDay transfer(
            String layer,
            String kind,
            int minutes,
            String sourceType,
            String sourceRef
    ) {
        ProductionCalendarDay day = new ProductionCalendarDay(
                owner,
                date,
                layer
        );
        day.update(
                kind,
                "NORM_OVERRIDE",
                minutes,
                "NONE",
                kind,
                sourceType,
                sourceRef
        );
        return day;
    }
}
