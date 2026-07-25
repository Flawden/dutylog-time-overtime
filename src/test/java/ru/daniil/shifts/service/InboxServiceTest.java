package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.InboxCreateRequest;
import ru.daniil.shifts.dto.Dtos.InboxToTaskRequest;
import ru.daniil.shifts.dto.Dtos.InboxUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.InboxItemStatus;
import ru.daniil.shifts.model.TaskPriority;
import ru.daniil.shifts.repo.InboxItemRepository;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class InboxServiceTest {

    @Autowired InboxService inboxService;
    @Autowired InboxItemRepository inboxItems;
    @Autowired UserRepository users;

    AppUser owner;
    AppUser other;

    @BeforeEach
    void setUp() {
        owner = users.save(new AppUser("inbox-service-owner", "{noop}unused"));
        other = users.save(new AppUser("inbox-service-other", "{noop}unused"));
    }

    @Test
    void clientOperationIdMakesRetriesIdempotent() {
        var first = inboxService.create(owner, new InboxCreateRequest(
                "  Позвонить насчёт документов  ", "  capture-123  "));
        var retry = inboxService.create(owner, new InboxCreateRequest(
                "Другой текст не должен создать дубль", "capture-123"));

        assertNotNull(first.id());
        assertEquals(first.id(), retry.id());
        assertEquals("Позвонить насчёт документов", retry.text());
        assertEquals("capture-123", retry.clientOperationId());
        assertEquals(InboxItemStatus.OPEN, retry.status());
        assertEquals(1, inboxService.list(owner, "open").size());
    }

    @Test
    void listAndArchiveRemainOwnerScoped() {
        var newest = inboxService.create(owner, new InboxCreateRequest("Новая мысль", null));
        var archived = inboxService.create(owner, new InboxCreateRequest("Разобранная мысль", null));
        inboxService.update(owner, archived.id(), new InboxUpdateRequest(null, true));
        inboxService.create(other, new InboxCreateRequest("Чужая мысль", null));

        assertEquals(List.of(newest.id()), inboxService.list(owner, "open").stream().map(item -> item.id()).toList());
        assertEquals(List.of(archived.id()), inboxService.list(owner, "archived").stream().map(item -> item.id()).toList());
        assertEquals(2, inboxService.list(owner, "all").size());
    }

    @Test
    void conversionCreatesStructuredTaskAndArchivesSourceAtomically() {
        var captured = inboxService.create(owner, new InboxCreateRequest("Подготовить поездку", "trip-1"));

        var converted = inboxService.convertToTask(owner, captured.id(), new InboxToTaskRequest(
                "2026-08-12", "  Личное  ", List.of("Поездка", "документы"),
                TaskPriority.HIGH, "2026-08-13", "19:30", true, 45));

        assertEquals(InboxItemStatus.ARCHIVED, converted.inboxItem().status());
        assertNotNull(converted.inboxItem().resolvedAt());
        assertEquals("Подготовить поездку", converted.task().text());
        assertEquals("2026-08-12", converted.task().date());
        assertEquals("личное", converted.task().category());
        assertEquals(List.of("поездка", "документы"), converted.task().tags());
        assertEquals("2026-08-13", converted.task().dueDate());
        assertEquals("19:30", converted.task().dueTime());
        assertTrue(converted.task().reminderEnabled());

        ApiException repeated = assertThrows(ApiException.class, () ->
                inboxService.convertToTask(owner, captured.id(), new InboxToTaskRequest(
                        "2026-08-12", null, null, null, null, null, false, null)));
        assertEquals(HttpStatus.CONFLICT, repeated.getStatus());
    }

    @Test
    void foreignIdsAndInvalidInputDoNotLeakResources() {
        var foreign = inboxService.create(other, new InboxCreateRequest("Чужая мысль", null));

        assertNotFound(() -> inboxService.update(owner, foreign.id(), new InboxUpdateRequest("Взлом", null)));
        assertNotFound(() -> inboxService.delete(owner, foreign.id()));
        assertBadRequest(() -> inboxService.create(owner, new InboxCreateRequest("   ", null)));
        assertBadRequest(() -> inboxService.list(owner, "mystery"));
        assertBadRequest(() -> inboxService.create(owner, new InboxCreateRequest("мысль", "x".repeat(81))));
    }

    @Test
    void deleteRemovesOwnedItem() {
        var item = inboxService.create(owner, new InboxCreateRequest("Удалить", null));
        inboxService.delete(owner, item.id());
        assertTrue(inboxItems.findById(item.id()).isEmpty());
    }

    private void assertBadRequest(org.junit.jupiter.api.function.Executable action) {
        ApiException error = assertThrows(ApiException.class, action);
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
    }

    private void assertNotFound(org.junit.jupiter.api.function.Executable action) {
        ApiException error = assertThrows(ApiException.class, action);
        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
        assertEquals("NOT_FOUND", error.getCode());
    }
}
