package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.DayNoteCreateRequest;
import ru.daniil.shifts.dto.Dtos.DayNoteDto;
import ru.daniil.shifts.dto.Dtos.DayNoteMoveRequest;
import ru.daniil.shifts.dto.Dtos.DayNoteUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.DayNoteRepository;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class DayNoteServiceTest {

    @Autowired DayNoteService service;
    @Autowired DayNoteRepository notes;
    @Autowired DayEntryRepository days;
    @Autowired UserRepository users;

    AppUser owner;
    AppUser other;

    @BeforeEach
    void setUp() {
        owner = users.save(new AppUser("day-note-owner", "{noop}unused"));
        other = users.save(new AppUser("day-note-other", "{noop}unused"));
    }

    @Test
    void createsIndependentNotesAndKeepsLegacyShadowOfPrimary() {
        DayNoteDto first = service.create(owner,
                new DayNoteCreateRequest("2026-08-10", "Первая", "alpha", false));
        DayNoteDto second = service.create(owner,
                new DayNoteCreateRequest("2026-08-10", "Вторая", "beta", false));

        assertNotNull(first.id());
        assertNotNull(second.id());
        assertEquals(List.of(first.id(), second.id()),
                service.listDate(owner, LocalDate.parse("2026-08-10")).stream().map(DayNoteDto::id).toList());
        assertEquals("alpha", days.findByOwnerAndDate(owner, LocalDate.parse("2026-08-10"))
                .orElseThrow().getNote());
    }

    @Test
    void editingOneNoteNeverOverwritesItsSibling() {
        DayNoteDto first = service.create(owner,
                new DayNoteCreateRequest("2026-08-11", null, "one", false));
        DayNoteDto second = service.create(owner,
                new DayNoteCreateRequest("2026-08-11", null, "two", false));

        DayNoteDto updated = service.update(owner, second.id(),
                new DayNoteUpdateRequest("Second", "two updated", null));

        assertEquals("two updated", updated.content());
        List<DayNoteDto> all = service.listDate(owner, LocalDate.parse("2026-08-11"));
        assertEquals("one", all.stream().filter(n -> n.id().equals(first.id())).findFirst().orElseThrow().content());
        assertEquals("two updated", all.stream().filter(n -> n.id().equals(second.id())).findFirst().orElseThrow().content());
    }

    @Test
    void pinnedNotesComeFirstAndMoveOnlyInsideTheirGroup() {
        DayNoteDto regularA = service.create(owner,
                new DayNoteCreateRequest("2026-08-12", "A", "a", false));
        DayNoteDto regularB = service.create(owner,
                new DayNoteCreateRequest("2026-08-12", "B", "b", false));
        DayNoteDto pinned = service.create(owner,
                new DayNoteCreateRequest("2026-08-12", "P", "p", true));

        List<DayNoteDto> initial = service.listDate(owner, LocalDate.parse("2026-08-12"));
        assertEquals(List.of(pinned.id(), regularA.id(), regularB.id()), initial.stream().map(DayNoteDto::id).toList());

        List<DayNoteDto> moved = service.move(owner, regularB.id(), new DayNoteMoveRequest("UP"));
        assertEquals(List.of(pinned.id(), regularB.id(), regularA.id()), moved.stream().map(DayNoteDto::id).toList());
    }

    @Test
    void deletingPrimaryPromotesNextWithoutDeletingDayData() {
        DayNoteDto first = service.create(owner,
                new DayNoteCreateRequest("2026-08-13", "First", "one", false));
        DayNoteDto second = service.create(owner,
                new DayNoteCreateRequest("2026-08-13", "Second", "two", false));

        service.delete(owner, first.id());

        assertEquals(List.of(second.id()), service.listDate(owner, LocalDate.parse("2026-08-13"))
                .stream().map(DayNoteDto::id).toList());
        assertEquals("two", days.findByOwnerAndDate(owner, LocalDate.parse("2026-08-13"))
                .orElseThrow().getNote());
    }

    @Test
    void ownershipMismatchLooksLikeNotFound() {
        DayNoteDto note = service.create(other,
                new DayNoteCreateRequest("2026-08-14", null, "secret", false));

        ApiException error = assertThrows(ApiException.class,
                () -> service.update(owner, note.id(), new DayNoteUpdateRequest(null, "hacked", null)));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
        assertTrue(notes.findById(note.id()).isPresent());
    }

    @Test
    void legacyClientEditsOnlyPrimaryAndPreservesSiblingNotes() {
        DayNoteDto first = service.create(owner,
                new DayNoteCreateRequest("2026-08-15", null, "old primary", false));
        DayNoteDto second = service.create(owner,
                new DayNoteCreateRequest("2026-08-15", null, "keep me", false));

        service.syncPrimaryFromLegacy(owner, LocalDate.parse("2026-08-15"), "legacy replacement");

        List<DayNoteDto> all = service.listDate(owner, LocalDate.parse("2026-08-15"));
        assertEquals(2, all.size());
        assertEquals("legacy replacement", all.stream().filter(n -> n.id().equals(first.id())).findFirst().orElseThrow().content());
        assertEquals("keep me", all.stream().filter(n -> n.id().equals(second.id())).findFirst().orElseThrow().content());
    }
    @Test
    void blankPrimaryRemainsDiscoverableAndDoesNotShadowItsSibling() {
        DayNoteDto first = service.create(owner,
                new DayNoteCreateRequest("2026-08-16", "Temporary", "", false));
        DayNoteDto second = service.create(owner,
                new DayNoteCreateRequest("2026-08-16", "Second", "sibling", false));

        service.update(owner, first.id(), new DayNoteUpdateRequest("", "", null));

        assertEquals(List.of(first.id(), second.id()), service.listDate(owner, LocalDate.parse("2026-08-16"))
                .stream().map(DayNoteDto::id).toList());
        assertEquals("# Без названия", days.findByOwnerAndDate(owner, LocalDate.parse("2026-08-16"))
                .orElseThrow().getNote());
    }

    @Test
    void searchFindsTitleAndContentAcrossDatesButNeverOtherUsersNotes() {
        DayNoteDto titleMatch = service.create(owner,
                new DayNoteCreateRequest("2026-08-20", "DutyLog release", "checklist", false));
        DayNoteDto contentMatch = service.create(owner,
                new DayNoteCreateRequest("2026-08-21", "Other", "DutyLog retrospective", false));
        service.create(other, new DayNoteCreateRequest("2026-08-22", "DutyLog secret", "hidden", false));

        List<DayNoteDto> result = service.search(owner, "dutylog", null, null, 20);

        assertEquals(List.of(contentMatch.id(), titleMatch.id()), result.stream().map(DayNoteDto::id).toList());
        assertEquals(List.of(titleMatch.id()), service.search(owner, "dutylog",
                LocalDate.parse("2026-08-20"), LocalDate.parse("2026-08-20"), 20)
                .stream().map(DayNoteDto::id).toList());
    }

}
