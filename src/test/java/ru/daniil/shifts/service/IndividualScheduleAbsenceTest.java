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
}
