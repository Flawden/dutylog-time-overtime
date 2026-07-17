package ru.daniil.shifts.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.DayDto;
import ru.daniil.shifts.dto.Dtos.DayFillRequest;
import ru.daniil.shifts.dto.Dtos.DayUpsertRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression coverage for the built-in schedule patterns and calendar boundaries. */
@SpringBootTest
@Transactional
class CalendarPatternServiceTest {

    @Autowired DayEntryService dayEntries;
    @Autowired ShiftTypeService shiftTypes;
    @Autowired UserRepository users;
    @Autowired EntityManager entityManager;

    AppUser owner;
    Long dayId;
    Long nightId;
    Long offId;

    @BeforeEach
    void setUp() {
        owner = users.save(new AppUser("calendar-pattern-owner", "{noop}unused"));
        Map<String, Long> ids = shiftTypes.list(owner).stream()
                .collect(Collectors.toMap(s -> s.name(), s -> s.id()));
        dayId = ids.get("Дневная");
        nightId = ids.get("Ночная");
        offId = ids.get("Выходной");
    }

    @Test
    void twoOnTwoOffRepeatsAcrossTheYearBoundary() {
        List<DayDto> result = dayEntries.fillSchedule(owner, new DayFillRequest(
                "2026-12-30", 8, List.of(dayId, dayId, offId, offId), true));

        assertDatesAndShifts(result,
                List.of("2026-12-30", "2026-12-31", "2027-01-01", "2027-01-02",
                        "2027-01-03", "2027-01-04", "2027-01-05", "2027-01-06"),
                List.of(dayId, dayId, offId, offId, dayId, dayId, offId, offId));
    }

    @Test
    void dayNightFortyEightCrossesLeapDayWithoutLosingThePattern() {
        List<DayDto> result = dayEntries.fillSchedule(owner, new DayFillRequest(
                "2028-02-27", 6, List.of(dayId, nightId, offId, offId), true));

        assertDatesAndShifts(result,
                List.of("2028-02-27", "2028-02-28", "2028-02-29",
                        "2028-03-01", "2028-03-02", "2028-03-03"),
                List.of(dayId, nightId, offId, offId, dayId, nightId));
    }

    @Test
    void weeklyPatternRotatedFromThursdayKeepsSaturdayAndSundayOff() {
        // Canonical five-day pattern is Mon..Sun. Starting on Thursday requires
        // the frontend rotation [Thu, Fri, Sat, Sun, Mon, Tue, Wed].
        List<Long> rotatedFromThursday = List.of(dayId, dayId, offId, offId, dayId, dayId, dayId);
        List<DayDto> result = dayEntries.fillSchedule(owner, new DayFillRequest(
                "2026-07-16", 11, rotatedFromThursday, true));

        Map<String, Long> byDate = result.stream()
                .collect(Collectors.toMap(DayDto::date, DayDto::shiftTypeId));
        assertEquals(dayId, byDate.get("2026-07-16")); // Thu
        assertEquals(dayId, byDate.get("2026-07-17")); // Fri
        assertEquals(offId, byDate.get("2026-07-18")); // Sat
        assertEquals(offId, byDate.get("2026-07-19")); // Sun
        assertEquals(dayId, byDate.get("2026-07-20")); // Mon
        assertEquals(offId, byDate.get("2026-07-25")); // next Sat
        assertEquals(offId, byDate.get("2026-07-26")); // next Sun
    }

    @Test
    void overwriteChangesOnlyTheShiftAndPreservesDayMetadata() {
        dayEntries.upsert(owner, "2026-08-05",
                new DayUpsertRequest(nightId, "# важная заметка", "🔥", 5.5, 1.5));

        dayEntries.fillSchedule(owner,
                new DayFillRequest("2026-08-05", 1, List.of(dayId), true));
        entityManager.clear();

        DayDto saved = dayEntries.listRange(owner,
                        java.time.LocalDate.parse("2026-08-05"),
                        java.time.LocalDate.parse("2026-08-05"))
                .get(0);
        assertEquals(dayId, saved.shiftTypeId());
        assertEquals("# важная заметка", saved.note());
        assertEquals("🔥", saved.dayEmoji());
        assertEquals(5.5, saved.overtimeHours());
        assertEquals(1.5, saved.timeOffHours());
    }

    @Test
    void noOverwriteKeepsExistingShiftButStillFillsEmptyDates() {
        dayEntries.upsert(owner, "2026-08-06",
                new DayUpsertRequest(nightId, "не стирать", null, null, null));

        List<DayDto> result = dayEntries.fillSchedule(owner,
                new DayFillRequest("2026-08-05", 3, List.of(dayId), false));

        assertDatesAndShifts(result,
                List.of("2026-08-05", "2026-08-06", "2026-08-07"),
                List.of(dayId, nightId, dayId));
        assertEquals("не стирать", result.get(1).note());
    }

    @Test
    void foreignPatternShiftIsRejectedBeforeAnyDatesAreWritten() {
        AppUser other = users.save(new AppUser("calendar-pattern-other", "{noop}unused"));
        Long foreignDayId = shiftTypes.list(other).stream()
                .filter(s -> "Дневная".equals(s.name()))
                .findFirst().orElseThrow().id();

        ApiException error = assertThrows(ApiException.class,
                () -> dayEntries.fillSchedule(owner, new DayFillRequest(
                        "2026-09-01", 4, List.of(dayId, foreignDayId), true)));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
        assertTrue(dayEntries.listRange(owner,
                java.time.LocalDate.parse("2026-09-01"),
                java.time.LocalDate.parse("2026-09-04")).isEmpty());
    }

    private static void assertDatesAndShifts(List<DayDto> actual,
                                             List<String> expectedDates,
                                             List<Long> expectedShiftIds) {
        assertEquals(expectedDates, actual.stream().map(DayDto::date).toList());
        assertEquals(expectedShiftIds, actual.stream().map(DayDto::shiftTypeId).toList());
    }
}
