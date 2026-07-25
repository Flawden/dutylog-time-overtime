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
import ru.daniil.shifts.dto.Dtos.SubtaskInput;
import ru.daniil.shifts.dto.Dtos.SubtaskUpdateRequest;
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
        owner = new AppUser("task-service-owner", "{noop}unused");
        owner.setWorkTimezone("UTC");
        owner.setDisplayTimezone("UTC");
        owner = users.save(owner);
        other = new AppUser("task-service-other", "{noop}unused");
        other.setWorkTimezone("UTC");
        other.setDisplayTimezone("UTC");
        other = users.save(other);
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
        assertEquals("работа", created.category());
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
                List.of(" Дом ", "Покупки"),
                TaskPriority.HIGH,
                "2026-08-15",
                "19:30",
                true,
                45
        ));

        assertEquals("Обновлённая задача", updated.text());
        assertTrue(updated.done());
        assertEquals("2026-08-12", updated.date());
        assertEquals("дом", updated.category());
        assertEquals(List.of("дом", "покупки"), updated.tags());
        assertEquals(TaskPriority.HIGH, updated.priority());
        assertEquals("2026-08-15", updated.dueDate());
        assertEquals("19:30", updated.dueTime());
        assertTrue(updated.reminderEnabled());
        assertEquals(45, updated.reminderMinutesBefore());

        TaskDto disabled = taskService.update(owner, created.id(), new TaskUpdateRequest(
                null, null, null, "   ", List.of(), null,
                "", "", false, 120
        ));

        assertNull(disabled.category(), "blank optional category must clear the field");
        assertTrue(disabled.tags().isEmpty(), "an explicit empty tag list must clear saved tags");
        assertNull(disabled.dueDate(), "an explicit blank due date must clear the field");
        assertNull(disabled.dueTime(), "an explicit blank due time must clear the field");
        assertFalse(disabled.reminderEnabled());
        assertNull(disabled.reminderMinutesBefore(),
                "lead minutes must be cleared even when a stale client sends them with reminderEnabled=false");
    }

    @Test
    void tagsAndMetadataAreNormalisedDeduplicatedAndOwnerScoped() {
        TaskDto created = taskService.create(owner, new TaskCreateRequest(
                "2026-08-10", "Разобрать документы", "  Работа  ",
                List.of("  Документы  ", "#Звонки", "документы", ""),
                TaskPriority.NORMAL, null, null, false, null));
        taskService.create(other, new TaskCreateRequest(
                "2026-08-10", "Чужая", "секрет", List.of("чужой"),
                TaskPriority.NORMAL, null, null, false, null));

        assertEquals("работа", created.category());
        assertEquals(List.of("документы", "звонки"), created.tags());
        var metadata = taskService.metadata(owner);
        assertEquals(List.of("работа"), metadata.categories());
        assertEquals(List.of("документы", "звонки"), metadata.tags());

        PageDto<TaskDto> byTag = board("all", null, null, "звонки", null, null, 0, 50);
        assertEquals(List.of(created.id()), ids(byTag));
    }

    @Test
    void taskTextLengthIsEnforcedInsideServiceIncludingInboxConversions() {
        String tooLong = "x".repeat(501);
        assertBadRequest(() -> taskService.create(owner, new TaskCreateRequest(
                "2026-08-10", tooLong, null, TaskPriority.NORMAL,
                null, null, false, null)));
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
                past.minusDays(1).toString(), "Quarterly report", "Work", TaskPriority.HIGH,
                past.toString(), null, false, null));
        TaskDto futureTask = taskService.create(owner, new TaskCreateRequest(
                today.toString(), "Buy milk", "Home", TaskPriority.LOW,
                future.toString(), "18:00", false, null));
        TaskDto urgent = taskService.create(owner, new TaskCreateRequest(
                today.toString(), "Production incident", "Ops", TaskPriority.URGENT,
                future.toString(), "20:00", false, null));
        TaskDto done = taskService.create(owner, new TaskCreateRequest(
                past.minusDays(1).toString(), "Archived report", "Work", TaskPriority.HIGH,
                past.toString(), null, false, null));
        taskService.update(owner, done.id(), new TaskUpdateRequest(
                null, true, null, null, null, null, null, null, null));
        TaskDto someday = taskService.create(owner, new TaskCreateRequest(
                today.toString(), "Someday task", "Misc", TaskPriority.NORMAL,
                null, null, false, null));
        taskService.create(other, new TaskCreateRequest(
                past.minusDays(1).toString(), "Foreign report", "Work", TaskPriority.HIGH,
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
    void deadlinesValidateTheFinalTaskStateAndAllowTheSameDay() {
        TaskDto sameDay = taskService.create(owner, new TaskCreateRequest(
                "2026-08-10", "Задача", null, TaskPriority.NORMAL,
                "2026-08-10", "18:00", false, null));
        assertEquals("2026-08-10", sameDay.dueDate());

        ApiException createError = assertThrows(ApiException.class, () -> taskService.create(owner,
                new TaskCreateRequest("2026-08-10", "Неверный срок", null, TaskPriority.NORMAL,
                        "2026-08-09", null, false, null)));
        assertEquals(HttpStatus.BAD_REQUEST, createError.getStatus());
        assertEquals("Срок не может быть раньше времени задачи.", createError.getMessage());

        TaskDto valid = taskService.create(owner, new TaskCreateRequest(
                "2026-08-10", "Перенос", null, TaskPriority.NORMAL,
                "2026-08-11", null, false, null));
        ApiException updateError = assertThrows(ApiException.class, () -> taskService.update(owner, valid.id(),
                new TaskUpdateRequest(null, null, "2026-08-12", null,
                        null, null, null, null, null)));
        assertEquals(HttpStatus.BAD_REQUEST, updateError.getStatus());
        assertEquals("Срок не может быть раньше времени задачи.", updateError.getMessage());
    }

    @Test
    void dayAndRangeListsKeepOpenTasksBeforeCompletedTasks() {
        TaskDto completed = taskService.create(owner, request("2026-08-10", "Сначала создана"));
        TaskDto open = taskService.create(owner, request("2026-08-10", "Открытая"));
        taskService.update(owner, completed.id(), new TaskUpdateRequest(
                null, true, null, null, null, null, null, null, null));

        assertEquals(List.of(open.id(), completed.id()),
                taskService.listDay(owner, "2026-08-10").stream().map(TaskDto::id).toList());
        assertEquals(List.of(open.id(), completed.id()),
                taskService.listRange(owner, LocalDate.parse("2026-08-10"), LocalDate.parse("2026-08-10"))
                        .stream().map(TaskDto::id).toList());
    }

    @Test
    void subtaskDeadlinePersistsCanBeClearedAndCannotPrecedeParentDate() {
        TaskDto created = taskService.create(owner, new TaskCreateRequest(
                "2026-08-10", "Документы", null, null, TaskPriority.NORMAL,
                null, null, false, null,
                List.of(new SubtaskInput(null, "Получить справку", false, 0, "2026-08-11"))
        ));
        assertEquals("2026-08-11", created.subtasks().get(0).dueDate());

        TaskDto cleared = taskService.update(owner, created.id(), new TaskUpdateRequest(
                null, null, null, null, null, null, null, null, null, null,
                List.of(new SubtaskInput(created.subtasks().get(0).id(), "Получить справку", false, 0, "")),
                null
        ));
        assertNull(cleared.subtasks().get(0).dueDate());

        ApiException invalid = assertThrows(ApiException.class, () -> taskService.create(owner,
                new TaskCreateRequest(
                        "2026-08-10", "Неверная подзадача", null, null, TaskPriority.NORMAL,
                        null, null, false, null,
                        List.of(new SubtaskInput(null, "Слишком рано", false, 0, "2026-08-09"))
                )));
        assertEquals(HttpStatus.BAD_REQUEST, invalid.getStatus());
        assertEquals("Срок подзадачи не может быть раньше даты задачи.", invalid.getMessage());
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

    @Test
    void subtasksPersistInUserOrderCanBeReconciledAndParticipateInSearch() {
        TaskDto created = taskService.create(owner, new TaskCreateRequest(
                "2026-08-10", "Подготовить поездку", "личное", List.of("поездка"),
                TaskPriority.NORMAL, null, null, false, null,
                List.of(
                        new SubtaskInput(null, "  Купить билеты  ", false, 7),
                        new SubtaskInput(null, "Собрать документы", true, 3)
                )
        ));

        assertEquals(List.of("Купить билеты", "Собрать документы"),
                created.subtasks().stream().map(item -> item.text()).toList());
        assertEquals(List.of(0, 1),
                created.subtasks().stream().map(item -> item.sortOrder()).toList());
        assertFalse(created.subtasks().get(0).done());
        assertTrue(created.subtasks().get(1).done());

        var first = created.subtasks().get(0);
        var second = created.subtasks().get(1);
        TaskDto updated = taskService.update(owner, created.id(), new TaskUpdateRequest(
                null, null, null, null, null, null, null, null, null, null,
                List.of(
                        new SubtaskInput(second.id(), "Документы проверены", true, 99),
                        new SubtaskInput(null, "Заказать трансфер", false, 1)
                ),
                null
        ));

        assertEquals(List.of("Документы проверены", "Заказать трансфер"),
                updated.subtasks().stream().map(item -> item.text()).toList());
        assertEquals(second.id(), updated.subtasks().get(0).id());
        assertFalse(updated.subtasks().stream().anyMatch(item -> first.id().equals(item.id())),
                "omitted child must be deleted by orphan removal");

        PageDto<TaskDto> bySubtask = board("all", null, null, "трансфер", null, null, 0, 50);
        assertEquals(List.of(created.id()), ids(bySubtask));
    }

    @Test
    void subtaskToggleIsOwnerScopedAndParentCompletionCanExplicitlyFinishChildren() {
        TaskDto created = taskService.create(owner, new TaskCreateRequest(
                "2026-08-10", "Релиз", null, null, TaskPriority.NORMAL,
                null, null, false, null,
                List.of(
                        new SubtaskInput(null, "Проверить CI", false, 0),
                        new SubtaskInput(null, "Проверить staging", false, 1)
                )
        ));

        Long firstId = created.subtasks().get(0).id();
        TaskDto toggled = taskService.updateSubtask(owner, created.id(), firstId, new SubtaskUpdateRequest(true));
        assertTrue(toggled.subtasks().get(0).done());
        assertFalse(toggled.subtasks().get(1).done());

        ApiException hidden = assertThrows(ApiException.class,
                () -> taskService.updateSubtask(other, created.id(), firstId, new SubtaskUpdateRequest(true)));
        assertEquals(HttpStatus.NOT_FOUND, hidden.getStatus());

        TaskDto completed = taskService.update(owner, created.id(), new TaskUpdateRequest(
                null, true, null, null, null, null, null, null, null, null,
                null, true
        ));
        assertTrue(completed.done());
        assertTrue(completed.subtasks().stream().allMatch(item -> item.done()));
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
