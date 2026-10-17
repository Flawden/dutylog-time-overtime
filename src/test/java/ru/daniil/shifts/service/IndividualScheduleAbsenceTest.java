package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.*;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.ShiftTypeRepository;
import ru.daniil.shifts.repo.UserRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class IndividualScheduleAbsenceTest {
    @Autowired UserRepository users;
    @Autowired ShiftTypeRepository shifts;
    @Autowired DayEntryRepository days;
    @Autowired VacationPlannerService planner;
    @Autowired TimeCompensationService time;
    @Autowired OvertimeService overtime;
    @Autowired ProductionCalendarService production;
    @Autowired ru.daniil.shifts.repo.AbsencePeriodRepository periods;

    @Test
    void allAbsencePoliciesUseIndividualScheduleAndPreserveItsShifts() {
        for (String code : new String[]{"VACATION", "SICK", "UNPAID", "TIME_OFF"}) {
            AppUser owner = users.save(new AppUser(
                    "absence" + UUID.randomUUID().toString().replace("-", ""),
                    "{noop}unused"));
            owner.setWorkTimezone("Asia/Yekaterinburg");
            owner.setDisplayTimezone("Asia/Yekaterinburg");
            users.saveAndFlush(owner);

            LocalDate first = LocalDate.parse("2026-09-14");
            ShiftType shortShift = shifts.save(new ShiftType(
                    owner, "Короткая", 5.5, "#123456", false,
                    LocalTime.of(10, 0), LocalTime.of(16, 0), 30, 5.5));
            ShiftType night = shifts.save(new ShiftType(
                    owner, "Ночная", 11.0, "#654321", false,
                    LocalTime.of(20, 0), LocalTime.of(8, 0), 60, 11.0));
            ShiftType off = shifts.save(new ShiftType(
                    owner, "Выходной", 0.0, "#888888", false,
                    null, null, 0, 0.0));

            ShiftType[] pattern = {shortShift, night, off};
            for (int index = 0; index < pattern.length; index++) {
                DayEntry entry = new DayEntry(owner, first.plusDays(index));
                entry.setShiftType(pattern[index]);
                days.saveAndFlush(entry);
            }

            if ("TIME_OFF".equals(code)) {
                overtime.createCredit(owner, new OvertimeCreditCreateRequest(
                        first.minusDays(1).toString(), null, null, null,
                        null, null, 20.0, "Запас для отгула"));
            }

            var type = planner.types(owner).stream()
                    .filter(item -> code.equals(item.systemCode()))
                    .findFirst().orElseThrow();
            var absence = planner.createPeriod(owner, new AbsencePeriodCreateRequest(
                    type.id(), code, first.toString(), first.plusDays(2).toString(),
                    "APPROVED", null));

            var source = time.payrollSource(owner, first, first.plusDays(2));
            assertEquals(990, source.plannedMinutes(), code);
            assertEquals(0, source.workedMinutes(), code);
            assertEquals("VACATION".equals(code) ? 990 : 0,
                    source.vacationMinutes(), code);
            assertEquals("SICK".equals(code) ? 990 : 0,
                    source.sickMinutes(), code);
            assertEquals("TIME_OFF".equals(code) ? 990 : 0,
                    source.overtimeCompensatedMinutes(), code);
            assertEquals("UNPAID".equals(code) ? 990 : 0,
                    source.unpaidMinutes(), code);

            var occurrences = planner.occurrences(owner, first, first.plusDays(2));
            assertEquals(3, occurrences.size(), code);
            assertEquals(330, occurrences.get(0).plannedShiftMinutes(), code);
            assertEquals(660, occurrences.get(1).plannedShiftMinutes(), code);
            assertEquals(0, occurrences.get(2).plannedShiftMinutes(), code);

            for (int index = 0; index < pattern.length; index++) {
                assertEquals(pattern[index].getId(),
                        days.findByOwnerAndDate(owner, first.plusDays(index))
                                .orElseThrow().getShiftType().getId(), code);
            }

            var month = production.month(owner, "2026-09");
            assertEquals(990, month.baseNormMinutes(), code);
            assertEquals(3, month.scheduleCoverageDays(), code);
            assertFalse(month.scheduleCoverageComplete(), code);

            if ("TIME_OFF".equals(code)) {
                assertEquals(990, absence.compensatedMinutes());
                assertEquals(210, overtime.balanceMinutes(owner));
                planner.deletePeriod(owner, absence.id());
                assertEquals(1200, overtime.balanceMinutes(owner));
            }
        }
    }

    @Test
    void partialAbsenceOutsideShiftDoesNotConsumeScheduledTime() {
        AppUser owner = users.save(new AppUser(
                "partial" + UUID.randomUUID().toString().replace("-", ""),
                "{noop}unused"));
        owner.setWorkTimezone("Asia/Yekaterinburg");
        owner.setDisplayTimezone("Asia/Yekaterinburg");
        users.saveAndFlush(owner);

        LocalDate date = LocalDate.parse("2026-09-14");
        ShiftType shift = shifts.save(new ShiftType(
                owner, "Индивидуальная", 6.0, "#123456", false,
                LocalTime.of(10, 0), LocalTime.of(16, 0), 0, 6.0));
        DayEntry entry = new DayEntry(owner, date);
        entry.setShiftType(shift);
        days.saveAndFlush(entry);

        overtime.createCredit(owner, new OvertimeCreditCreateRequest(
                date.minusDays(1).toString(), null, null, null,
                null, null, 4.0, "Запас"));

        var type = planner.types(owner).stream()
                .filter(item -> "TIME_OFF".equals(item.systemCode()))
                .findFirst().orElseThrow();

        var absence = planner.createPeriod(owner, new AbsencePeriodCreateRequest(
                type.id(), "До начала смены", date.toString(), date.toString(),
                "APPROVED", null, "PARTIAL", "08:00", "09:00", "OVERTIME_BANK"));

        var source = time.payrollSource(owner, date, date);
        assertAll(
                () -> assertEquals(360, source.plannedMinutes()),
                () -> assertEquals(360, source.workedMinutes()),
                () -> assertEquals(0, source.overtimeCompensatedMinutes()),
                () -> assertEquals(0, absence.compensatedMinutes()),
                () -> assertEquals(240, overtime.balanceMinutes(owner))
        );
    }

    @Test
    void legacyFloatingBreakDoesNotBlockSummaryOrRewriteBankAndPayrollRemainsStrict() {
        AppUser owner = users.save(new AppUser(
                "legacy" + UUID.randomUUID().toString().replace("-", ""), "{noop}unused"));
        owner.setWorkTimezone("Asia/Yekaterinburg");
        users.saveAndFlush(owner);
        LocalDate date = LocalDate.parse("2026-09-14");
        ShiftType shift = shifts.save(new ShiftType(owner, "Плавающий обед", 7.5,
                "#123456", false, LocalTime.of(9, 0), LocalTime.of(17, 0), 30, 7.5));
        for (int index = 0; index < 2; index++) {
            DayEntry day = new DayEntry(owner, date.plusDays(index));
            day.setShiftType(shift);
            days.saveAndFlush(day);
        }
        overtime.createCredit(owner, new OvertimeCreditCreateRequest(
                date.minusDays(1).toString(), null, null, null,
                null, null, 4.0, "Запас"));
        var type = planner.types(owner).stream()
                .filter(item -> "TIME_OFF".equals(item.systemCode())).findFirst().orElseThrow();
        var created = planner.createPeriod(owner, new AbsencePeriodCreateRequest(
                type.id(), "Старое отсутствие", date.toString(), date.toString(),
                "APPROVED", null, "PARTIAL", "10:00", "12:00", "OVERTIME_BANK", 30));
        var period = periods.findById(created.id()).orElseThrow();
        period.setUnpaidBreakOverlapMinutes(null); // Existing row from before V91.
        periods.saveAndFlush(period);
        int balanceBefore = overtime.balanceMinutes(owner);

        var summary = time.summary(owner, date, date.plusDays(1));
        var repeated = time.summary(owner, date, date.plusDays(1));
        assertEquals(summary, repeated);
        assertEquals(900, summary.plannedMinutes());
        assertNull(summary.workedMinutes());
        assertNull(summary.absenceMinutes());
        assertNull(summary.unpaidMinutes());
        assertEquals(1, summary.calculationIssues().size());
        assertEquals(date.toString(), summary.calculationIssues().get(0).date());
        assertEquals(java.util.List.of(created.id()), summary.calculationIssues().get(0).absenceIds());
        assertEquals("ABSENCE_BREAK_OVERLAP_REQUIRED", summary.calculationIssues().get(0).code());
        assertNull(summary.days().get(0).workedMinutes());
        assertNull(summary.days().get(0).absenceMinutes());
        assertEquals(Integer.valueOf(450), summary.days().get(1).workedMinutes());
        assertEquals(90, summary.overtimeUsedMinutes());
        assertEquals(balanceBefore, overtime.balanceMinutes(owner));
        assertNull(periods.findById(created.id()).orElseThrow().getUnpaidBreakOverlapMinutes());
        assertEquals(90, period.getCompensatedMinutes());
        var error = assertThrows(ru.daniil.shifts.service.exception.ApiException.class,
                () -> time.payrollSource(owner, date, date.plusDays(1)));
        assertEquals("ABSENCE_BREAK_OVERLAP_REQUIRED", error.getCode());

        period.setUnpaidBreakOverlapMinutes(30);
        periods.saveAndFlush(period);
        var resolved = time.summary(owner, date, date.plusDays(1));
        assertTrue(resolved.calculationIssues().isEmpty());
        assertEquals(Integer.valueOf(90), resolved.absenceMinutes());
        assertEquals(Integer.valueOf(810), resolved.workedMinutes());
        assertEquals(90, time.payrollSource(owner, date, date.plusDays(1)).overtimeCompensatedMinutes());
        assertEquals(balanceBefore, overtime.balanceMinutes(owner));
    }
}
