package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.ImportantDayCreateRequest;
import ru.daniil.shifts.dto.Dtos.ImportantDayDto;
import ru.daniil.shifts.dto.Dtos.ImportantDayOccurrenceDto;
import ru.daniil.shifts.dto.Dtos.ImportantDayUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.ImportantEventType;
import ru.daniil.shifts.model.RepeatMode;
import ru.daniil.shifts.repo.ImportantDayRepository;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Behavioural regression suite for important-day CRUD and recurrence expansion. */
@SpringBootTest
@Transactional
class ImportantDayServiceTest {

    @Autowired ImportantDayService importantDayService;
    @Autowired ImportantDayRepository importantDayRepository;
    @Autowired UserRepository users;

    AppUser owner;
    AppUser other;

    @BeforeEach
    void setUp() {
        owner = users.save(new AppUser("important-day-service-owner", "{noop}unused"));
        other = users.save(new AppUser("important-day-service-other", "{noop}unused"));
    }

    @Test
    void createTrimsTitleAppliesDefaultsAndListsOnlyOwnersRowsInDateOrder() {
        ImportantDayDto later = importantDayService.create(owner,
                new ImportantDayCreateRequest("  Поздняя дата  ", "2026-09-20", null, "  "));
        ImportantDayDto earlier = importantDayService.create(owner,
                new ImportantDayCreateRequest("Ранняя дата", "2026-08-10", RepeatMode.YEARLY, "#A1B2C3"));
        importantDayService.create(other,
                new ImportantDayCreateRequest("Чужая дата", "2026-07-01", RepeatMode.NONE, "#112233"));

        assertNotNull(later.id());
        assertEquals("Поздняя дата", later.title());
        assertEquals(RepeatMode.NONE, later.repeatMode());
        assertEquals("#F5B841", later.color());

        List<ImportantDayDto> ownerDays = importantDayService.list(owner);
        assertEquals(List.of(earlier.id(), later.id()), ownerDays.stream().map(ImportantDayDto::id).toList());
    }

    @Test
    void updateSupportsEveryEditableFieldAndDeleteRemovesOnlyTheOwnedRow() {
        ImportantDayDto created = importantDayService.create(owner,
                new ImportantDayCreateRequest("Исходное событие", "2026-08-10", RepeatMode.NONE, "#123456"));

        ImportantDayDto updated = importantDayService.update(owner, created.id(),
                new ImportantDayUpdateRequest("  Обновлённое событие  ", "2027-02-28", RepeatMode.YEARLY, "#ABCDEF"));

        assertEquals("Обновлённое событие", updated.title());
        assertEquals("2027-02-28", updated.date());
        assertEquals(RepeatMode.YEARLY, updated.repeatMode());
        assertEquals("#ABCDEF", updated.color());

        importantDayService.delete(owner, created.id());
        assertTrue(importantDayService.list(owner).isEmpty());
        assertTrue(importantDayRepository.findById(created.id()).isEmpty());
    }

    @Test
    void oneTimeOccurrencesAreInclusiveAndRemainOwnerScoped() {
        ImportantDayDto inside = importantDayService.create(owner,
                new ImportantDayCreateRequest("В диапазоне", "2026-08-10", RepeatMode.NONE, "#111111"));
        importantDayService.create(owner,
                new ImportantDayCreateRequest("За диапазоном", "2026-08-11", RepeatMode.NONE, "#222222"));
        importantDayService.create(other,
                new ImportantDayCreateRequest("Чужое событие", "2026-08-10", RepeatMode.NONE, "#333333"));

        List<ImportantDayOccurrenceDto> exact = importantDayService.occurrences(
                owner, LocalDate.parse("2026-08-10"), LocalDate.parse("2026-08-10"));

        assertEquals(1, exact.size());
        assertEquals(inside.id(), exact.get(0).id());
        assertEquals("2026-08-10", exact.get(0).date());
    }

    @Test
    void monthlyRecurrenceClampsTheThirtyFirstToTheLastDayOfShortMonths() {
        ImportantDayDto monthly = importantDayService.create(owner,
                new ImportantDayCreateRequest("Оплата", "2026-01-31", RepeatMode.MONTHLY, "#445566"));

        List<ImportantDayOccurrenceDto> occurrences = importantDayService.occurrences(
                owner, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-04-30"));

        assertEquals(List.of("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30"),
                occurrences.stream().filter(o -> o.id().equals(monthly.id()))
                        .map(ImportantDayOccurrenceDto::date).toList());
    }

    @Test
    void yearlyLeapDayFallsBackToFebruaryTwentyEighthAndReturnsOnLeapYears() {
        ImportantDayDto leap = importantDayService.create(owner,
                new ImportantDayCreateRequest("29 февраля", "2024-02-29", RepeatMode.YEARLY, "#778899"));

        List<ImportantDayOccurrenceDto> nonLeap = importantDayService.occurrences(
                owner, LocalDate.parse("2025-01-01"), LocalDate.parse("2025-12-31"));
        List<ImportantDayOccurrenceDto> leapYear = importantDayService.occurrences(
                owner, LocalDate.parse("2028-01-01"), LocalDate.parse("2028-12-31"));

        assertEquals(List.of("2025-02-28"), nonLeap.stream()
                .filter(o -> o.id().equals(leap.id())).map(ImportantDayOccurrenceDto::date).toList());
        assertEquals(List.of("2028-02-29"), leapYear.stream()
                .filter(o -> o.id().equals(leap.id())).map(ImportantDayOccurrenceDto::date).toList());
    }

    @Test
    void occurrencesAreSortedByDateThenTitleAndDoNotDuplicateOneSourceEvent() {
        importantDayService.create(owner,
                new ImportantDayCreateRequest("Бета", "2026-08-10", RepeatMode.NONE, "#111111"));
        importantDayService.create(owner,
                new ImportantDayCreateRequest("Альфа", "2026-08-10", RepeatMode.NONE, "#222222"));
        importantDayService.create(owner,
                new ImportantDayCreateRequest("Раньше", "2026-08-09", RepeatMode.NONE, "#333333"));

        List<ImportantDayOccurrenceDto> result = importantDayService.occurrences(
                owner, LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"));

        assertEquals(List.of("Раньше", "Альфа", "Бета"),
                result.stream().map(ImportantDayOccurrenceDto::title).toList());
        assertEquals(3, result.stream().map(ImportantDayOccurrenceDto::id).distinct().count());
    }

    @Test
    void malformedDatesRangesBlankUpdatesAndNullPayloadsFailAsBadRequests() {
        assertBadRequest(() -> importantDayService.create(owner, null));
        assertBadRequest(() -> importantDayService.create(owner,
                new ImportantDayCreateRequest("Событие", "10.08.2026", RepeatMode.NONE, "#123456")));

        ImportantDayDto created = importantDayService.create(owner,
                new ImportantDayCreateRequest("Событие", "2026-08-10", RepeatMode.NONE, "#123456"));

        assertBadRequest(() -> importantDayService.update(owner, created.id(), null));
        assertBadRequest(() -> importantDayService.update(owner, created.id(),
                new ImportantDayUpdateRequest("   ", null, null, null)));
        assertBadRequest(() -> importantDayService.update(owner, created.id(),
                new ImportantDayUpdateRequest(null, "10.08.2026", null, null)));
        assertBadRequest(() -> importantDayService.occurrences(owner,
                LocalDate.parse("2026-08-11"), LocalDate.parse("2026-08-10")));
        assertBadRequest(() -> importantDayService.occurrences(owner,
                LocalDate.parse("2026-01-01"), LocalDate.parse("2027-01-03")));
        assertBadRequest(() -> importantDayService.delete(owner, null));
    }

    @Test
    void foreignAndMissingIdsRemainNotFound() {
        ImportantDayDto foreign = importantDayService.create(other,
                new ImportantDayCreateRequest("Чужое событие", "2026-08-10", RepeatMode.NONE, "#123456"));

        assertNotFound(() -> importantDayService.update(owner, foreign.id(),
                new ImportantDayUpdateRequest("Взломано", null, null, null)));
        assertNotFound(() -> importantDayService.delete(owner, foreign.id()));
        assertNotFound(() -> importantDayService.delete(owner, Long.MAX_VALUE));
    }

    @Test
    void timedEventKeepsCanonicalInstantAndReprojectsInChangedUserTimezone() {
        owner.setWorkTimezone("Europe/Chisinau");
        users.save(owner);
        ImportantDayDto created = importantDayService.create(owner,
                new ImportantDayCreateRequest(
                        "Созвон", "2026-08-10", RepeatMode.NONE, "#334455",
                        ImportantEventType.EVENT, "2026-08-10", false,
                        "22:30", "23:30", "Europe/Chisinau", "Online",
                        "Обсудить релиз", "☎", "DutyLog", List.of(30)));

        assertEquals(ImportantEventType.EVENT, created.eventType());
        assertEquals("Europe/Chisinau", created.sourceTimezone());
        assertNotNull(created.startInstant());

        owner.setWorkTimezone("Asia/Tokyo");
        users.save(owner);
        List<ImportantDayOccurrenceDto> projected = importantDayService.occurrences(
                owner, LocalDate.parse("2026-08-11"), LocalDate.parse("2026-08-11"));

        assertEquals(1, projected.size());
        assertEquals("2026-08-11", projected.get(0).date());
        assertEquals(created.startInstant(), projected.get(0).startInstant());
        assertEquals("Asia/Tokyo", projected.get(0).displayTimezone());
    }

    @Test
    void allDayPeriodEmitsEachVisibleDateAndStaysFloating() {
        ImportantDayDto created = importantDayService.create(owner,
                new ImportantDayCreateRequest(
                        "Отпуск", "2026-08-29", RepeatMode.NONE, "#ABC123",
                        ImportantEventType.PERIOD, "2026-09-02", true,
                        null, null, null, "Море", null, "☀", "Отдых", List.of(1440)));

        List<ImportantDayOccurrenceDto> occurrences = importantDayService.occurrences(
                owner, LocalDate.parse("2026-08-30"), LocalDate.parse("2026-09-01"));

        assertEquals(List.of("2026-08-30", "2026-08-31", "2026-09-01"),
                occurrences.stream().filter(item -> item.id().equals(created.id()))
                        .map(ImportantDayOccurrenceDto::date).toList());
        assertTrue(occurrences.stream().allMatch(ImportantDayOccurrenceDto::allDay));
        assertTrue(occurrences.stream().allMatch(item -> item.startInstant() == null));
    }

    @Test
    void invalidPeriodAndTimedEndAreRejected() {
        assertBadRequest(() -> importantDayService.create(owner,
                new ImportantDayCreateRequest(
                        "Плохой период", "2026-08-10", RepeatMode.NONE, "#123456",
                        ImportantEventType.PERIOD, "2026-08-09", true,
                        null, null, null, null, null, null, null, List.of())));
        assertBadRequest(() -> importantDayService.create(owner,
                new ImportantDayCreateRequest(
                        "Плохое время", "2026-08-10", RepeatMode.NONE, "#123456",
                        ImportantEventType.EVENT, "2026-08-10", false,
                        "18:00", "17:00", "Europe/Chisinau", null, null, null, null, List.of())));
    }

    private void assertBadRequest(org.junit.jupiter.api.function.Executable action) {
        ApiException error = assertThrows(ApiException.class, action);
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals("BAD_REQUEST", error.getCode());
    }

    private void assertNotFound(org.junit.jupiter.api.function.Executable action) {
        ApiException error = assertThrows(ApiException.class, action);
        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
        assertEquals("NOT_FOUND", error.getCode());
    }
}
