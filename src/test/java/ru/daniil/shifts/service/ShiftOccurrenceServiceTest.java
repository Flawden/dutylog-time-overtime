package ru.daniil.shifts.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.DayUpsertRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.ShiftTypeRepository;
import ru.daniil.shifts.repo.UserRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class ShiftOccurrenceServiceTest {

    @Autowired UserRepository users;
    @Autowired ShiftTypeRepository shiftTypes;
    @Autowired DayEntryRepository days;
    @Autowired DayEntryService dayEntryService;
    @Autowired ShiftOccurrenceService occurrences;
    @Autowired EntityManager entityManager;

    AppUser user;

    @BeforeEach
    void setUp() {
        user = new AppUser("shift-occurrence-owner", "{noop}x");
        user.setWorkTimezone("Asia/Yekaterinburg");
        user.setDisplayTimezone("Asia/Yekaterinburg");
        user = users.save(user);
    }

    @Test
    void datedShiftKeepsItsAbsoluteIdentityAndReprojectsAfterTimezoneMove() {
        ShiftType day = shiftTypes.save(new ShiftType(user, "Дневная", 8, "#F5B841", false,
                LocalTime.of(8, 30), LocalTime.of(17, 0), 30, 8.0));

        var created = dayEntryService.upsert(user, "2026-07-03", request(day.getId(), null));
        assertEquals("2026-07-03T03:30:00Z", created.shiftInterval().startInstant());
        assertEquals("2026-07-03T08:30", created.shiftInterval().displayStart());

        user.setWorkTimezone("Europe/Kyiv");
        user.setDisplayTimezone("Europe/Kyiv");
        users.save(user);
        entityManager.flush();
        entityManager.clear();
        AppUser moved = users.findByUsername("shift-occurrence-owner").orElseThrow();

        var projected = occurrences.listForDisplayRange(
                moved, LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 3));

        assertEquals(1, projected.size());
        assertEquals("2026-07-03T08:30", projected.get(0).sourceStart());
        assertEquals("2026-07-03T17:00", projected.get(0).sourceEnd());
        assertEquals("Asia/Yekaterinburg", projected.get(0).sourceTimezone());
        assertEquals("2026-07-03T06:30", projected.get(0).displayStart());
        assertEquals("2026-07-03T15:00", projected.get(0).displayEnd());
        assertEquals("Europe/Kyiv", projected.get(0).displayTimezone());
    }

    @Test
    void projectionCanMoveTheWholeShiftToTheNextCalendarDay() {
        user.setWorkTimezone("Europe/Kyiv");
        user.setDisplayTimezone("Europe/Kyiv");
        users.save(user);
        ShiftType late = shiftTypes.save(new ShiftType(user, "Поздняя", 8, "#7B8CE0", false,
                LocalTime.of(23, 0), LocalTime.of(7, 0), 0, 8.0));

        dayEntryService.upsert(user, "2026-07-03", request(late.getId(), null));

        user.setWorkTimezone("Asia/Yekaterinburg");
        user.setDisplayTimezone("Asia/Yekaterinburg");
        users.save(user);
        entityManager.flush();
        entityManager.clear();
        AppUser moved = users.findByUsername("shift-occurrence-owner").orElseThrow();

        var julyFourth = occurrences.listForDisplayRange(
                moved, LocalDate.of(2026, 7, 4), LocalDate.of(2026, 7, 4));

        assertEquals(1, julyFourth.size());
        assertEquals("2026-07-03", julyFourth.get(0).sourceDate());
        assertEquals("2026-07-04T01:00", julyFourth.get(0).displayStart());
        assertEquals("2026-07-04T09:00", julyFourth.get(0).displayEnd());
    }

    @Test
    void savingOnlyANoteDoesNotSilentlyGuessTheZoneOfALegacyShift() {
        ShiftType day = shiftTypes.save(new ShiftType(user, "Legacy", 8, "#F5B841", false,
                LocalTime.of(8, 30), LocalTime.of(17, 0), 30, 8.0));
        DayEntry legacy = new DayEntry(user, LocalDate.of(2026, 7, 3));
        legacy.setShiftType(day);
        days.saveAndFlush(legacy);
        assertFalse(legacy.hasShiftOccurrenceSnapshot());

        dayEntryService.upsert(user, "2026-07-03", request(day.getId(), "только заметка"));

        DayEntry reloaded = days.findByOwnerAndDate(user, LocalDate.of(2026, 7, 3)).orElseThrow();
        assertEquals("только заметка", reloaded.getNote());
        assertFalse(reloaded.hasShiftOccurrenceSnapshot(),
                "обычное сохранение дня не должно придумывать исторический часовой пояс");
    }

    @Test
    void explicitLegacyMigrationCapturesOnlySelectedOwnedRows() {
        ShiftType day = shiftTypes.save(new ShiftType(user, "Legacy", 8, "#F5B841", false,
                LocalTime.of(8, 30), LocalTime.of(17, 0), 30, 8.0));
        DayEntry first = new DayEntry(user, LocalDate.of(2026, 7, 3));
        first.setShiftType(day);
        DayEntry second = new DayEntry(user, LocalDate.of(2026, 7, 4));
        second.setShiftType(day);
        days.saveAll(List.of(first, second));
        days.flush();

        occurrences.migrate(user, new ru.daniil.shifts.dto.Dtos.LegacyShiftMigrationRequest(
                "Asia/Yekaterinburg", List.of(first.getId())));

        assertTrue(days.findById(first.getId()).orElseThrow().hasShiftOccurrenceSnapshot());
        assertFalse(days.findById(second.getId()).orElseThrow().hasShiftOccurrenceSnapshot());
    }

    private DayUpsertRequest request(Long shiftTypeId, String note) {
        return new DayUpsertRequest(shiftTypeId, note, null, null, null);
    }
}
