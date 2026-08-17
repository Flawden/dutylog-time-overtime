package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.ProductionCalendarDayUpdateRequest;
import ru.daniil.shifts.dto.Dtos.ProductionCalendarMonthDto;
import ru.daniil.shifts.dto.Dtos.ActualWorkIntervalRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.ShiftTypeRepository;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class ProductionCalendarFoundationServiceTest {

    @Autowired UserRepository users;
    @Autowired ShiftTypeRepository shifts;
    @Autowired DayEntryRepository dayEntries;
    @Autowired ProductionCalendarService productionCalendar;
    @Autowired TimeCompensationService timeCompensation;
    @Autowired WorkdayTruthService workdayTruth;
    @Autowired ActualWorkService actualWork;
    @Autowired LedgerIntegrityService ledgerIntegrity;

    AppUser owner;
    ShiftType day;
    ShiftType off;

    @BeforeEach
    void setUp() {
        owner = users.save(new AppUser("production-calendar-owner", "{noop}unused"));
        day = shifts.save(new ShiftType(owner, "День", 8.0, "#123456", false));
        off = shifts.save(new ShiftType(owner, "Выходной", 0.0, "#999999", false));
    }

    @Test
    void holidayNormAndHolidayPayrollClassificationAreIndependent() {
        assign("2026-08-03", day);

        productionCalendar.upsertLocal(owner, "2026-08-03",
                new ProductionCalendarDayUpdateRequest("HOLIDAY", "NORM_OVERRIDE", 0, "HOLIDAY", "Праздник"));
        ProductionCalendarMonthDto month = productionCalendar.month(owner, "2026-08");

        assertEquals(480, month.baseNormMinutes());
        assertEquals(0, month.productionNormMinutes());
        assertEquals(-480, month.adjustmentMinutes());
        assertEquals(480, month.holidayReductionMinutes());
        var date = month.days().stream().filter(item -> item.date().equals("2026-08-03")).findFirst().orElseThrow();
        assertEquals("HOLIDAY", date.payrollEffect());
        assertTrue(date.localOverride());
    }

    @Test
    void holidayPayrollClassificationCanKeepBaseNormUntouched() {
        assign("2026-08-04", day);

        productionCalendar.upsertLocal(owner, "2026-08-04",
                new ProductionCalendarDayUpdateRequest("HOLIDAY", "NONE", null, "HOLIDAY", "Рабочий праздник"));
        ProductionCalendarMonthDto month = productionCalendar.month(owner, "2026-08");

        assertEquals(480, month.baseNormMinutes());
        assertEquals(480, month.productionNormMinutes());
        assertEquals(0, month.adjustmentMinutes());
        var date = month.days().stream().filter(item -> item.date().equals("2026-08-04")).findFirst().orElseThrow();
        assertEquals("NONE", date.scheduleEffect());
        assertEquals("HOLIDAY", date.payrollEffect());
    }

    @Test
    void transferredWorkdayCanAddNormToAPlannedDayOffAndDeleteRestoresBase() {
        assign("2026-08-08", off);
        productionCalendar.upsertLocal(owner, "2026-08-08",
                new ProductionCalendarDayUpdateRequest("TRANSFERRED_WORKDAY", "NORM_OVERRIDE", 420, "NONE", "Перенос"));

        ProductionCalendarMonthDto changed = productionCalendar.month(owner, "2026-08");
        assertEquals(0, changed.baseNormMinutes());
        assertEquals(420, changed.productionNormMinutes());
        assertEquals(420, changed.transferredAdjustmentMinutes());

        productionCalendar.deleteLocal(owner, "2026-08-08");
        ProductionCalendarMonthDto restored = productionCalendar.month(owner, "2026-08");
        assertEquals(0, restored.productionNormMinutes());
        assertEquals(0, restored.affectedDays());
    }

    @Test
    void shortenedDayBecomesCanonicalRequiredMinutesForTimeCompensation() {
        assign("2026-08-19", day);
        productionCalendar.upsertLocal(owner, "2026-08-19",
                new ProductionCalendarDayUpdateRequest("SHORTENED_DAY", "NORM_OVERRIDE", 420, "NONE", "Предпраздничный"));

        var summary = timeCompensation.summary(owner, LocalDate.parse("2026-08-19"), LocalDate.parse("2026-08-19"));
        var row = summary.days().get(0);
        assertEquals(420, row.plannedMinutes());
        assertEquals(420, row.workedMinutes());
        assertEquals("PLAN_DERIVED", row.actualSource());
    }

    @Test
    void workdayTruthJoinsBaseNormRequiredNormAndExplicitReality() {
        assign("2026-08-20", day);
        productionCalendar.upsertLocal(owner, "2026-08-20",
                new ProductionCalendarDayUpdateRequest("SHORTENED_DAY", "NORM_OVERRIDE", 420, "NONE", "Сокращённый"));
        actualWork.create(owner, new ActualWorkIntervalRequest("2026-08-20", "08:00", "16:00", "Фактически работал"));

        var truth = workdayTruth.truth(owner, LocalDate.parse("2026-08-20"));
        assertEquals(480, truth.baseNormMinutes());
        assertEquals(420, truth.requiredNormMinutes());
        assertTrue(truth.explicitActual());
        assertEquals(480, truth.actualMinutes());
        assertEquals(1, truth.actualWork().size());
    }

    @Test
    void closedAccountingPeriodRejectsProductionCalendarMutation() {
        ledgerIntegrity.closePeriod(owner, "2026-08");

        ApiException error = assertThrows(ApiException.class, () -> productionCalendar.upsertLocal(owner, "2026-08-10",
                new ProductionCalendarDayUpdateRequest("HOLIDAY", "NORM_OVERRIDE", 0, "HOLIDAY", null)));
        assertEquals("PERIOD_CLOSED", error.getCode());
    }

    private void assign(String date, ShiftType shift) {
        DayEntry entry = new DayEntry(owner, LocalDate.parse(date));
        entry.setShiftType(shift);
        dayEntries.saveAndFlush(entry);
    }
}
