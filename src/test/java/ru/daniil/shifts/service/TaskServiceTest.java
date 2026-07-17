package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.PageDto;
import ru.daniil.shifts.dto.Dtos.TaskCreateRequest;
import ru.daniil.shifts.dto.Dtos.TaskDto;
import ru.daniil.shifts.dto.Dtos.TaskUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.TaskPriority;
import ru.daniil.shifts.repo.DayTaskRepository;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Behavioural regression suite for task persistence, board filtering and validation. */
@SpringBootTest
@Transactional
class TaskServiceTest {

    @Autowired TaskService taskService;
    @Autowired DayTaskRepository taskRepository;
    @Autowired UserRepository users;

    AppUser owner;
    AppUser other;

    @BeforeEach
    void setUp() {
        owner = users.save(new AppUser("task-service-owner", "{noop}unused"));
        other = users.save(new AppUser("task-service-other", "{noop}unused"));
    }

    @Test
    void createTrimsFieldsAppliesDefaultsAndListsOnlyRequestedDay() {
        TaskDto created = taskService.create(owner, new TaskCreateRequest(
                "2026-08-10",
                "  Подготовить отчёт  ",
                "  Работа  ",
                null,
                "",
                "",
                false,
                60
        ));

        assertNotNull(created.id());
        assertEquals("2026-08-10", created.date());
        assertEquals("Подготовить отчёт", created.text());
        assertEquals("Работа", created.category());
        assertEquals(TaskPriority.NORMAL, created.priority());
        assertNull(created.dueDate());
        assertNull(created.dueTime());
        assertFalse(created.reminderEnabled());
        assertNull(created.reminderMinutesBefore(),
                "disabled reminder must not leave an orphan lead-time value");

        taskService.create(owner, request("2026-08-11", "Другая дата"));
        taskService.create(other, request("2026-08-10", "Чужая задача"));

        List<TaskDto> day = taskService.listDay(owner, "2026-08-10");
        assertEquals(1, day.size());
        assertEquals(created.id(), day.get(0).id());
    }

    @Test
    void updateSupportsEveryEditableFieldAndDisablingReminderClearsMinutes() {
        TaskDto created = taskService.create(owner, request("2026-08-10", "Исходная задача"));

        TaskDto updated = taskService.update(owner, created.id(), new TaskUpdateRequest(
                "  Обновлённая задача  ",
                true,
                "2026-08-12",
                "  Дом  ",
                TaskPriority.HIGH,
                "2026-08-15",
                "19:30",
                true,
                45
        ));

        assertEquals("Обновлённая задача", updated.text());
        assertTrue(updated.done());
        assertEquals("2026-08-12", updated.date());
        assertEquals("Дом", updated.category());
        assertEquals(TaskPriority.HIGH, updated.priority());
        assertEquals("2026-08-15", updated.dueDate());
        assertEquals("19:30", updated.dueTime());
        assertTrue(updated.reminderEnabled());
        assertEquals(45, updated.reminderMinutesBefore());

        TaskDto disabled = taskService.update(owner, created.id(), new TaskUpdateRequest(
                null, null, null, "   ", null,
                null, null, false, 120
        ));

        assertNull(disabled.category(), "blank optional category must clear the field");
        assertFalse(disabled.reminderEnabled());
        assertNull(disabled.reminderMinutesBefore(),
                "lead minutes must be cleared even when a stale client sends them with reminderEnabled=false");
    }

    @Test
    void listRangeIsOrderedAndNeverLeaksAnotherUsersTasks() {
        TaskDto second = taskService.create(owner, request("2026-08-11", "Вторая"));
        TaskDto first = taskService.create(owner, request("2026-08-10", "Первая"));
        taskService.create(owner, request("2026-08-20", "За диапазоном"));
        taskService.create(other, request("2026-08-10", "Чужая"));

        List<TaskDto> range = taskService.listRange(
                owner, LocalDate.parse("2026-08-10"), LocalDate.parse("2026-08-12"));

        assertEquals(List.of(first.id(), second.id()), range.stream().map(TaskDto::id).toList());
    }

    @Test
    void boardFiltersStatusCategoryPriorityQueryAndDateRange() {
        LocalDate today = LocalDate.now();
        LocalDate past = today.minusDays(2);
        LocalDate future = today.plusDays(2);

        TaskDto overdue = taskService.create(owner, new TaskCreateRequest(
                today.toString(), "Quarterly report", "Work", TaskPriority.HIGH,
                past.toString(), null, false, null));
        TaskDto futureTask = taskService.create(owner, new TaskCreateRequest(
                today.toString(), "Buy milk", "Home", TaskPriority.LOW,
                future.toString(), "18:00", false, null));
        TaskDto urgent = taskService.create(owner, new TaskCreateRequest(
                today.toString(), "Production incident", "Ops", TaskPriority.URGENT,
                future.toString(), "20:00", false, null));
        TaskDto done = taskService.create(owner, new TaskCreateRequest(
                today.toString(), "Archived report", "Work", TaskPriority.HIGH,
                past.toString(), null, false, null));
        taskService.update(owner, done.id(), new TaskUpdateRequest(
                null, true, null, null, null, null, null, null, null));
        TaskDto someday = taskService.create(owner, new TaskCreateRequest(
                today.toString(), "Someday task", "Misc", TaskPriority.NORMAL,
                null, null, false, null));
        taskService.create(other, new TaskCreateRequest(
                today.toString(), "Foreign report", "Work", TaskPriority.HIGH,
                past.toString(), null, false, null));

        PageDto<TaskDto> overduePage = board("overdue", null, null, null, null, null, 0, 50);
        assertEquals(List.of(overdue.id()), ids(overduePage));

        PageDto<TaskDto> donePage = board("done", null, null, null, null, null, 0, 50);
        assertEquals(List.of(done.id()), ids(donePage));

        PageDto<TaskDto> filtered = board("all", "work", "high", "report", null, null, 0, 50);
        assertEquals(2, filtered.total());
        assertTrue(ids(filtered).containsAll(List.of(overdue.id(), done.id())));

        PageDto<TaskDto> searched = board("all", null, null, "milk", null, null, 0, 50);
        assertEquals(List.of(futureTask.id()), ids(searched));

        PageDto<TaskDto> urgentPage = board("all", null, "urgent", null, null, null, 0, 50);
        assertEquals(List.of(urgent.id()), ids(urgentPage),
                "URGENT is a supported priority and must not be rejected as an unknown filter");

        PageDto<TaskDto> ranged = board(
                "all", null, null, null, future.toString(), future.toString(), 0, 50);
        assertEquals(List.of(futureTask.id(), urgent.id()), ids(ranged));

        PageDto<TaskDto> upcoming = board("upcoming", null, null, null, null, null, 0, 50);
        assertTrue(ids(upcoming).containsAll(List.of(futureTask.id(), someday.id())));
        assertFalse(ids(upcoming).contains(overdue.id()));
        assertFalse(ids(upcoming).contains(done.id()));
    }

    @Test
    void boardPaginationUsesSafeBoundsAndStableMetadata() {
        for (int i = 0; i < 12; i++) {
            taskService.create(owner, request("2026-08-10", "Задача " + i));
        }

        PageDto<TaskDto> first = board("all", null, null, null, null, null, -5, 1);
        assertEquals(0, first.page());
        assertEquals(10, first.size(), "requested size below the minimum must be clamped to ten");
        assertEquals(12, first.total());
        assertEquals(2, first.totalPages());
        assertEquals(10, first.items().size());
        assertFalse(first.hasPrevious());
        assertTrue(first.hasNext());

        PageDto<TaskDto> second = board("all", null, null, null, null, null, 1, 10);
        assertEquals(2, second.items().size());
        assertTrue(second.hasPrevious());
        assertFalse(second.hasNext());
    }

    @Test
    void malformedInputAndUnknownFiltersFailAsBadRequests() {
        assertBadRequest(() -> taskService.listDay(owner, "10.08.2026"));
        assertBadRequest(() -> taskService.listRange(
                owner, LocalDate.parse("2026-08-12"), LocalDate.parse("2026-08-10")));
        assertBadRequest(() -> board("mystery", null, null, null, null, null, 0, 50));
        assertBadRequest(() -> board("all", null, "critical", null, null, null, 0, 50));
        assertBadRequest(() -> taskService.create(owner, request("2026-08-10", "   ")));
        assertBadRequest(() -> taskService.update(owner, null,
                new TaskUpdateRequest(null, null, null, null, null, null, null, null, null)));
    }

    @Test
    void deleteRemovesOnlyTheOwnedTask() {
        TaskDto owned = taskService.create(owner, request("2026-08-10", "Удалить меня"));
        TaskDto untouched = taskService.create(owner, request("2026-08-10", "Оставить меня"));

        taskService.delete(owner, owned.id());

        assertTrue(taskRepository.findById(owned.id()).isEmpty());
        assertTrue(taskRepository.findById(untouched.id()).isPresent());
    }

    private TaskCreateRequest request(String date, String text) {
        return new TaskCreateRequest(date, text, null, null, null, null, false, null);
    }

    private PageDto<TaskDto> board(String status,
                                    String category,
                                    String priority,
                                    String query,
                                    String from,
                                    String to,
                                    int page,
                                    int size) {
        return taskService.listBoard(owner, status, category, priority, query, from, to, page, size);
    }

    private List<Long> ids(PageDto<TaskDto> page) {
        return page.items().stream().map(TaskDto::id).toList();
    }

    private void assertBadRequest(org.junit.jupiter.api.function.Executable action) {
        ApiException error = assertThrows(ApiException.class, action);
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
    }
}
