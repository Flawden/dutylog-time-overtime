package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.*;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.repo.AbsencePeriodRepository;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class VacationPlannerServiceTest {

    @Autowired VacationPlannerService planner;
    @Autowired ShiftTypeService shiftTypes;
    @Autowired DayEntryRepository days;
    @Autowired AbsencePeriodRepository periods;
    @Autowired UserRepository users;

    AppUser owner;
    AppUser other;
    Map<String, Long> shifts;

    @BeforeEach
    void setUp() {
        owner = users.save(new AppUser("vacation-service-owner", "{noop}unused"));
        owner.setWorkTimezone("Europe/Chisinau");
        owner.setDisplayTimezone("Europe/Chisinau");
        users.save(owner);
        other = users.save(new AppUser("vacation-service-other", "{noop}unused"));
        shifts = shiftTypes.list(owner).stream().collect(Collectors.toMap(ShiftTypeDto::name, ShiftTypeDto::id));
    }

    @Test
    void firstPlannerCallCreatesCountryNeutralDefaultsExactlyOnce() {
        VacationPlannerDto first = planner.planner(owner, LocalDate.parse("2026-07-30"), null, null);
        VacationPlannerDto second = planner.planner(owner, LocalDate.parse("2026-07-30"), null, null);

        assertEquals(28, first.settings().annualAllowanceDays());
        assertEquals("CALENDAR_DAYS", first.settings().countMode());
        assertEquals("2026-01-01", first.summary().workYearStart());
        assertEquals("2026-12-31", first.summary().workYearEnd());
        assertEquals(java.util.List.of(14, 28, 35), first.durationPresets());
        assertEquals(java.util.List.of("VACATION", "TIME_OFF", "SICK", "UNPAID", "OTHER"),
                first.types().stream().map(AbsenceTypeDto::systemCode).toList());
        assertEquals(5, second.types().size());
    }

    @Test
    void weekdayModeCountsMondayToFridayButKeepsEveryCivilDayInCalendar() {
        planner.updateSettings(owner, new VacationSettingsUpdateRequest(28, 0, "WEEKDAYS", 1, 1));
        AbsenceTypeDto vacation = vacationType(owner);

        AbsencePreviewDto preview = planner.preview(owner,
                new AbsencePreviewRequest(vacation.id(), "2026-08-01", "2026-08-09", null));
        AbsencePeriodDto created = planner.createPeriod(owner,
                new AbsencePeriodCreateRequest(vacation.id(), "Летний отпуск", "2026-08-01", "2026-08-09", "PLANNED", null));

        assertEquals(9, preview.calendarDays());
        assertEquals(5, preview.countedDays());
        assertEquals(5, created.countedDays());
        assertEquals(9, planner.occurrences(owner, LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-09")).size());
        assertEquals(5, planner.planner(owner, LocalDate.parse("2026-08-02"), null, null).summary().plannedDays());
    }

    @Test
    void shiftConflictIsWarningAndAbsenceNeverMutatesTheShiftEntry() {
        DayEntry shift = new DayEntry(owner, LocalDate.parse("2026-08-04"));
        shift.setShiftType(shiftTypes.requireOwnedShiftType(owner, shifts.get("Дневная")));
        days.saveAndFlush(shift);
        AbsenceTypeDto vacation = vacationType(owner);

        AbsencePreviewDto preview = planner.preview(owner,
                new AbsencePreviewRequest(vacation.id(), "2026-08-03", "2026-08-05", null));
        AbsencePeriodDto created = planner.createPeriod(owner,
                new AbsencePeriodCreateRequest(vacation.id(), null, "2026-08-03", "2026-08-05", "APPROVED", null));

        assertEquals(1, preview.shiftConflictCount());
        assertEquals(1, created.shiftConflictCount());
        DayEntry persisted = days.findByOwnerAndDate(owner, LocalDate.parse("2026-08-04")).orElseThrow();
        assertEquals(shifts.get("Дневная"), persisted.getShiftType().getId());
        AbsenceOccurrenceDto occurrence = planner.occurrences(owner, LocalDate.parse("2026-08-04"), LocalDate.parse("2026-08-04")).get(0);
        assertTrue(occurrence.shiftConflict());
        assertTrue(occurrence.replacesShift());
        assertEquals("Дневная", occurrence.plannedShiftName());
        assertTrue(occurrence.plannedShiftMinutes() > 0);
    }

    @Test
    void partialTimeOffChargesHoursPreservesShiftAndAllowsSeparateWindows() {
        planner.updateSettings(owner, new VacationSettingsUpdateRequest(28, 0, "CALENDAR_DAYS", 1, 1, 8.0, 8.0));
        AbsenceTypeDto timeOff = planner.types(owner).stream()
                .filter(type -> "TIME_OFF".equals(type.systemCode())).findFirst().orElseThrow();
        DayEntry shift = new DayEntry(owner, LocalDate.parse("2026-08-06"));
        shift.setShiftType(shiftTypes.requireOwnedShiftType(owner, shifts.get("Дневная")));
        days.saveAndFlush(shift);

        AbsencePeriodDto morning = planner.createPeriod(owner, new AbsencePeriodCreateRequest(
                timeOff.id(), "Врач", "2026-08-06", "2026-08-06", "APPROVED", null,
                "PARTIAL", "09:00", "13:00"));
        AbsencePeriodDto afternoon = planner.createPeriod(owner, new AbsencePeriodCreateRequest(
                timeOff.id(), "Документы", "2026-08-06", "2026-08-06", "PLANNED", null,
                "PARTIAL", "14:00", "16:00"));

        assertEquals(240, morning.chargedMinutes());
        assertEquals(120, afternoon.chargedMinutes());
        assertEquals("PARTIAL", morning.coverage());
        assertTrue(days.findByOwnerAndDate(owner, LocalDate.parse("2026-08-06")).orElseThrow().getShiftType() != null);
        VacationPlannerDto view = planner.planner(owner, LocalDate.parse("2026-08-06"), null, null);
        assertEquals(360, view.summary().timeOffPlannedMinutes());
        assertEquals(120, view.summary().timeOffRemainingMinutes());
        assertEquals(2, view.occurrences().stream().filter(item -> "PARTIAL".equals(item.coverage())).count());

        ApiException overBalance = assertThrows(ApiException.class, () -> planner.createPeriod(owner,
                new AbsencePeriodCreateRequest(timeOff.id(), null, "2026-08-07", "2026-08-07", null, null,
                        "PARTIAL", "09:00", "12:00")));
        assertEquals("TIME_OFF_LIMIT_EXCEEDED", overBalance.getCode());
    }

    @Test
    void fullDayTimeOffUsesPlannedShiftMinutesWithoutDeletingThePlan() {
        AbsenceTypeDto timeOff = planner.types(owner).stream()
                .filter(type -> "TIME_OFF".equals(type.systemCode())).findFirst().orElseThrow();
        DayEntry shift = new DayEntry(owner, LocalDate.parse("2026-08-08"));
        shift.setShiftType(shiftTypes.requireOwnedShiftType(owner, shifts.get("Дневная")));
        days.saveAndFlush(shift);
        planner.updateSettings(owner, new VacationSettingsUpdateRequest(28, 0, "CALENDAR_DAYS", 1, 1, 24.0, 8.0));

        AbsencePeriodDto created = planner.createPeriod(owner, new AbsencePeriodCreateRequest(
                timeOff.id(), "Полный отгул", "2026-08-08", "2026-08-08", "APPROVED", null,
                "FULL_DAY", null, null));
        AbsenceOccurrenceDto occurrence = planner.occurrences(owner, LocalDate.parse("2026-08-08"), LocalDate.parse("2026-08-08")).get(0);

        assertTrue(created.replacesShift());
        assertEquals(occurrence.plannedShiftMinutes(), created.chargedMinutes());
        assertEquals("Дневная", occurrence.plannedShiftName());
        assertNotNull(days.findByOwnerAndDate(owner, LocalDate.parse("2026-08-08")).orElseThrow().getShiftType());
    }

    @Test
    void overlappingAbsenceAndAllowanceOverflowAreRejectedWithStableCodes() {
        AbsenceTypeDto vacation = vacationType(owner);
        planner.updateSettings(owner, new VacationSettingsUpdateRequest(3, 0, "CALENDAR_DAYS", 1, 1));
        planner.createPeriod(owner,
                new AbsencePeriodCreateRequest(vacation.id(), null, "2026-08-01", "2026-08-02", null, null));

        ApiException overlap = assertThrows(ApiException.class, () -> planner.createPeriod(owner,
                new AbsencePeriodCreateRequest(vacation.id(), null, "2026-08-02", "2026-08-03", null, null)));
        assertEquals(HttpStatus.CONFLICT, overlap.getStatus());
        assertEquals("ABSENCE_OVERLAP", overlap.getCode());

        ApiException limit = assertThrows(ApiException.class, () -> planner.createPeriod(owner,
                new AbsencePeriodCreateRequest(vacation.id(), null, "2026-08-10", "2026-08-11", null, null)));
        assertEquals(HttpStatus.CONFLICT, limit.getStatus());
        assertEquals("VACATION_LIMIT_EXCEEDED", limit.getCode());
    }

    @Test
    void workYearBoundaryAndCarryoverProduceIndependentAnnualBalances() {
        planner.updateSettings(owner, new VacationSettingsUpdateRequest(10, 2, "CALENDAR_DAYS", 7, 1));
        AbsenceTypeDto vacation = vacationType(owner);
        planner.createPeriod(owner,
                new AbsencePeriodCreateRequest(vacation.id(), null, "2026-06-28", "2026-07-03", null, null));

        VacationPlannerDto before = planner.planner(owner, LocalDate.parse("2026-06-30"), null, null);
        VacationPlannerDto after = planner.planner(owner, LocalDate.parse("2026-07-01"), null, null);

        assertEquals("2025-07-01", before.summary().workYearStart());
        assertEquals(3, before.summary().plannedDays());
        assertEquals(9, before.summary().remainingDays());
        assertEquals("2026-07-01", after.summary().workYearStart());
        assertEquals(3, after.summary().plannedDays());
        assertEquals(9, after.summary().remainingDays());

        planner.updateSettings(other, new VacationSettingsUpdateRequest(10, 0, "CALENDAR_DAYS", 7, 1));
        AbsenceTypeDto otherVacation = vacationType(other);
        planner.createPeriod(other,
                new AbsencePeriodCreateRequest(otherVacation.id(), null, "2027-08-01", "2027-08-04", null, null));
        ApiException futureOverflow = assertThrows(ApiException.class, () -> planner.updateSettings(other,
                new VacationSettingsUpdateRequest(3, 0, "CALENDAR_DAYS", 7, 1)));
        assertEquals("VACATION_LIMIT_EXCEEDED", futureOverflow.getCode());
    }

    @Test
    void crossWorkYearPreviewReportsTheMostConstrainedBalance() {
        planner.updateSettings(owner, new VacationSettingsUpdateRequest(3, 0, "CALENDAR_DAYS", 1, 1));
        AbsenceTypeDto vacation = vacationType(owner);
        planner.createPeriod(owner,
                new AbsencePeriodCreateRequest(vacation.id(), null, "2027-01-10", "2027-01-11", null, null));

        AbsencePreviewDto preview = planner.preview(owner,
                new AbsencePreviewRequest(vacation.id(), "2026-12-31", "2027-01-02", null));

        assertEquals("2027-01-01", preview.workYearStart());
        assertEquals(2, preview.plannedBefore());
        assertEquals(4, preview.projectedPlanned());
        assertEquals(-1, preview.remainingAfter());
        assertTrue(preview.exceedsAllowance());
        assertEquals(1, preview.exceededBy());
    }

    @Test
    void nonAllowanceAbsenceDoesNotConsumeVacationBalance() {
        AbsenceTypeDto sick = planner.types(owner).stream().filter(t -> "SICK".equals(t.systemCode())).findFirst().orElseThrow();
        AbsencePeriodDto created = planner.createPeriod(owner,
                new AbsencePeriodCreateRequest(sick.id(), "Больничный", "2026-08-01", "2026-08-20", "APPROVED", "Документы переданы"));

        assertEquals(0, created.countedDays());
        assertEquals(0, planner.planner(owner, LocalDate.parse("2026-08-10"), null, null).summary().plannedDays());
        assertTrue(periods.findById(created.id()).isPresent());
    }

    @Test
    void customTypeCrudRemainsOwnerScopedAndUsedTypesCannotBeDeleted() {
        AbsenceTypeDto custom = planner.createType(owner,
                new AbsenceTypeCreateRequest("Учёба", "#123abc", false, 90));
        AbsenceTypeDto updated = planner.updateType(owner, custom.id(),
                new AbsenceTypeUpdateRequest("Учебный отпуск", "#ABCDEF", false, 91));
        assertEquals("Учебный отпуск", updated.name());
        assertEquals("#ABCDEF", updated.color());

        planner.createPeriod(owner,
                new AbsencePeriodCreateRequest(custom.id(), null, "2026-09-01", "2026-09-02", null, null));
        ApiException semanticChange = assertThrows(ApiException.class, () -> planner.updateType(owner, custom.id(),
                new AbsenceTypeUpdateRequest(null, null, true, null)));
        assertEquals("ABSENCE_TYPE_IN_USE", semanticChange.getCode());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, () -> planner.deleteType(owner, custom.id())).getStatus());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> planner.updateType(other, custom.id(),
                new AbsenceTypeUpdateRequest(null, "#000000", null, null))).getStatus());
    }

    @Test
    void updateRecalculatesBalanceAndDeleteRestoresIt() {
        AbsenceTypeDto vacation = vacationType(owner);
        AbsencePeriodDto created = planner.createPeriod(owner,
                new AbsencePeriodCreateRequest(vacation.id(), null, "2026-08-01", "2026-08-03", null, null));
        AbsencePeriodDto updated = planner.updatePeriod(owner, created.id(),
                new AbsencePeriodUpdateRequest(null, "Длиннее", null, "2026-08-05", "APPROVED", null, null, null));

        assertEquals(5, updated.countedDays());
        assertEquals(5, planner.planner(owner, LocalDate.parse("2026-08-02"), null, null).summary().plannedDays());
        planner.deletePeriod(owner, created.id());
        assertEquals(0, planner.planner(owner, LocalDate.parse("2026-08-02"), null, null).summary().plannedDays());
    }

    @Test
    void malformedDatesReversedAndOversizedRangesFailWithoutWritingRows() {
        AbsenceTypeDto vacation = vacationType(owner);
        assertBadRequest(() -> planner.preview(owner,
                new AbsencePreviewRequest(vacation.id(), "01.08.2026", "2026-08-02", null)));
        assertBadRequest(() -> planner.createPeriod(owner,
                new AbsencePeriodCreateRequest(vacation.id(), null, "2026-08-03", "2026-08-02", null, null)));
        assertBadRequest(() -> planner.createPeriod(owner,
                new AbsencePeriodCreateRequest(vacation.id(), null, "2026-01-01", "2027-02-06", null, null)));
        assertTrue(periods.findAll().isEmpty());
    }

    private AbsenceTypeDto vacationType(AppUser user) {
        return planner.types(user).stream().filter(t -> "VACATION".equals(t.systemCode())).findFirst().orElseThrow();
    }

    private void assertBadRequest(org.junit.jupiter.api.function.Executable action) {
        ApiException error = assertThrows(ApiException.class, action);
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
    }
}
