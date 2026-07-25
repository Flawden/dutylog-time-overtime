package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.config.SecurityEventLogger;
import ru.daniil.shifts.dto.Dtos.TaskCreateRequest;
import ru.daniil.shifts.dto.Dtos.TaskDto;
import ru.daniil.shifts.dto.Dtos.TaskMetadataDto;
import ru.daniil.shifts.dto.Dtos.TaskUpdateRequest;
import ru.daniil.shifts.dto.Dtos.SubtaskInput;
import ru.daniil.shifts.dto.Dtos.SubtaskUpdateRequest;
import ru.daniil.shifts.dto.Dtos.PageDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayTask;
import ru.daniil.shifts.model.TaskPriority;
import ru.daniil.shifts.model.TaskSubtask;
import ru.daniil.shifts.repo.DayTaskRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class TaskService {
    private static final Comparator<DayTask> TASK_DISPLAY_ORDER = Comparator
            .comparing(DayTask::isDone)
            .thenComparing(TaskService::taskSortDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(DayTask::getDueTime, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(DayTask::getDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(DayTask::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(DayTask::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private final DayTaskRepository tasks;
    private final DayEntryService dayEntryService;
    private final UserTimeService userTimeService;
    private final SecurityEventLogger securityEvents;

    public TaskService(DayTaskRepository tasks,
                       DayEntryService dayEntryService,
                       UserTimeService userTimeService,
                       SecurityEventLogger securityEvents) {
        this.tasks = tasks;
        this.dayEntryService = dayEntryService;
        this.userTimeService = userTimeService;
        this.securityEvents = securityEvents;
    }

    @Transactional(readOnly = true)
    public List<TaskDto> listDay(AppUser user, String date) {
        LocalDate d = dayEntryService.parseDate(date, "Дата должна быть в формате yyyy-MM-dd");
        LocalDateTime now = userTimeService.workNow(user);
        return tasks.findByOwnerAndDateOrderByCreatedAtAscIdAsc(user, d).stream()
                .sorted(TASK_DISPLAY_ORDER)
                .map(task -> TaskDto.from(task, now)).toList();
    }

    @Transactional(readOnly = true)
    public List<TaskDto> listRange(AppUser user, LocalDate from, LocalDate to) {
        dayEntryService.validateRange(from, to);
        LocalDateTime now = userTimeService.workNow(user);
        return tasks.findByOwnerAndDateBetweenOrderByDateAscCreatedAtAscIdAsc(user, from, to).stream()
                .sorted(TASK_DISPLAY_ORDER)
                .map(task -> TaskDto.from(task, now)).toList();
    }


    @Transactional(readOnly = true)
    public TaskMetadataDto metadata(AppUser user) {
        List<String> categories = tasks.findDistinctCategories(user).stream()
                .map(this::cleanCategory)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .limit(100)
                .toList();
        List<String> tags = tasks.findDistinctTags(user).stream()
                .map(this::cleanTag)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .limit(200)
                .toList();
        return new TaskMetadataDto(categories, tags);
    }


    @Transactional(readOnly = true)
    public PageDto<TaskDto> listBoard(AppUser user,
                                      String status,
                                      String category,
                                      String priority,
                                      String q,
                                      String from,
                                      String to,
                                      int page,
                                      int size) {
        LocalDate fromDate = parseOptionalDate(from, "Дата from должна быть в формате yyyy-MM-dd");
        LocalDate toDate = parseOptionalDate(to, "Дата to должна быть в формате yyyy-MM-dd");
        if (fromDate != null && toDate != null) dayEntryService.validateRange(fromDate, toDate);

        String statusFilter = normalizeBoardStatus(status);
        String cat = cleanOptional(category);
        String pr = cleanOptional(priority);
        String query = cleanOptional(q);
        String queryLower = query != null ? query.toLowerCase(Locale.ROOT) : null;
        TaskPriority priorityFilter = null;
        if (pr != null && !"all".equalsIgnoreCase(pr)) {
            try { priorityFilter = TaskPriority.valueOf(pr.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException e) { throw ApiException.badRequest("Неизвестный приоритет задачи"); }
        }

        LocalDateTime now = userTimeService.workNow(user);
        final String categoryFilter = cat;
        final TaskPriority priorityFinal = priorityFilter;
        int safePage = safePage(page);
        int safeSize = safeSize(size);
        List<TaskDto> filtered = tasks.findByOwnerOrderByDoneAscDueDateAscDueTimeAscDateAscCreatedAtAscIdAsc(user).stream()
                .filter(t -> matchStatus(t, statusFilter, now))
                .filter(t -> categoryFilter == null || "all".equalsIgnoreCase(categoryFilter) || categoryFilter.equalsIgnoreCase(t.getCategory() == null ? "" : t.getCategory()))
                .filter(t -> priorityFinal == null || t.getPriority() == priorityFinal)
                .filter(t -> withinTaskBoardRange(t, fromDate, toDate))
                .filter(t -> queryLower == null || taskMatchesQuery(t, queryLower))
                .sorted(TASK_DISPLAY_ORDER)
                .map(task -> TaskDto.from(task, now))
                .toList();
        return PageDto.of(pageSlice(filtered, safePage, safeSize), safePage, safeSize, filtered.size());
    }


    private String normalizeBoardStatus(String status) {
        String normalized = cleanOptional(status);
        if (normalized == null) normalized = "open";
        normalized = normalized.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "all", "open", "done", "overdue", "upcoming" -> normalized;
            default -> throw ApiException.badRequest("Неизвестный статус задач");
        };
    }

    private int safePage(int page) {
        return Math.max(0, page);
    }

    private int safeSize(int size) {
        if (size <= 0) return 50;
        return Math.min(100, Math.max(10, size));
    }

    private <T> List<T> pageSlice(List<T> list, int page, int size) {
        int from = Math.min(list.size(), page * size);
        int to = Math.min(list.size(), from + size);
        return list.subList(from, to);
    }

    private boolean matchStatus(DayTask task, String status, LocalDateTime now) {
        return switch (status) {
            case "all" -> true;
            case "open" -> !task.isDone();
            case "done" -> task.isDone();
            case "overdue" -> isOverdue(task, now);
            case "upcoming" -> !task.isDone() && !isOverdue(task, now);
            default -> throw ApiException.badRequest("Неизвестный статус задач");
        };
    }

    private boolean withinTaskBoardRange(DayTask task, LocalDate from, LocalDate to) {
        LocalDate d = taskSortDate(task);
        if (d == null) return from == null && to == null;
        if (from != null && d.isBefore(from)) return false;
        return to == null || !d.isAfter(to);
    }

    private static LocalDate taskSortDate(DayTask task) {
        return task.getDueDate() != null ? task.getDueDate() : task.getDate();
    }

    private boolean taskMatchesQuery(DayTask task, String q) {
        return contains(task.getText(), q)
                || contains(task.getCategory(), q)
                || contains(task.getDate() != null ? task.getDate().toString() : null, q)
                || contains(task.getDueDate() != null ? task.getDueDate().toString() : null, q)
                || contains(task.getPriority() != null ? task.getPriority().name() : null, q)
                || task.getTags().stream().anyMatch(tag -> contains(tag, q))
                || task.getSubtasks().stream().anyMatch(subtask -> contains(subtask.getText(), q));
    }

    private boolean contains(String value, String q) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(q);
    }

    private boolean isOverdue(DayTask task, LocalDateTime now) {
        if (task.isDone() || task.getDueDate() == null) return false;
        LocalDate today = now.toLocalDate();
        if (task.getDueDate().isBefore(today)) return true;
        if (task.getDueDate().isAfter(today) || task.getDueTime() == null) return false;
        return task.getDueTime().isBefore(now.toLocalTime());
    }

    @Transactional
    public TaskDto create(AppUser user, TaskCreateRequest req) {
        if (req == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        LocalDate d = dayEntryService.parseDate(req.date(), "Дата задачи должна быть в формате yyyy-MM-dd");
        String text = cleanTaskText(req.text());
        DayTask task = new DayTask(user, d, text);
        applyCreateFields(task, req);
        validateBusinessRules(task);
        return TaskDto.from(tasks.save(task), userTimeService.workNow(user));
    }

    @Transactional
    public TaskDto update(AppUser user, Long id, TaskUpdateRequest req) {
        if (req == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        DayTask task = requireOwnedTask(user, id);
        if (req.text() != null) task.setText(cleanTaskText(req.text()));
        if (req.done() != null) task.setDone(req.done());
        if (req.date() != null && !req.date().isBlank()) task.setDate(dayEntryService.parseDate(req.date(), "Дата задачи должна быть в формате yyyy-MM-dd"));
        if (req.category() != null) task.setCategory(cleanCategory(req.category()));
        if (req.tags() != null) task.setTags(cleanTags(req.tags()));
        if (req.priority() != null) task.setPriority(req.priority());
        if (req.dueDate() != null) task.setDueDate(parseOptionalDate(req.dueDate(), "Срок задачи должен быть в формате yyyy-MM-dd"));
        if (req.dueTime() != null) task.setDueTime(parseOptionalTime(req.dueTime(), "Время срока должно быть в формате HH:mm"));
        if (req.reminderEnabled() != null) task.setReminderEnabled(req.reminderEnabled());
        if (req.reminderMinutesBefore() != null) task.setReminderMinutesBefore(req.reminderMinutesBefore());
        if (req.subtasks() != null) reconcileSubtasks(task, req.subtasks());
        if (Boolean.TRUE.equals(req.completeSubtasks()) && Boolean.TRUE.equals(req.done())) {
            task.getSubtasks().forEach(subtask -> subtask.setDone(true));
        }
        if (!task.isReminderEnabled()) task.setReminderMinutesBefore(null);
        validateBusinessRules(task);
        return TaskDto.from(tasks.save(task), userTimeService.workNow(user));
    }

    @Transactional
    public TaskDto updateSubtask(AppUser user, Long taskId, Long subtaskId, SubtaskUpdateRequest req) {
        if (req == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        DayTask task = requireOwnedTask(user, taskId);
        if (subtaskId == null) throw ApiException.badRequest("Не указан id подзадачи");
        TaskSubtask subtask = task.getSubtasks().stream()
                .filter(item -> Objects.equals(item.getId(), subtaskId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Подзадача не найдена"));
        subtask.setDone(Boolean.TRUE.equals(req.done()));
        return TaskDto.from(tasks.save(task), userTimeService.workNow(user));
    }

    @Transactional
    public void delete(AppUser user, Long id) {
        DayTask task = requireOwnedTask(user, id);
        tasks.delete(task);
    }

    private void applyCreateFields(DayTask task, TaskCreateRequest req) {
        task.setCategory(cleanCategory(req.category()));
        task.setTags(cleanTags(req.tags()));
        task.setPriority(req.priority() != null ? req.priority() : TaskPriority.NORMAL);
        task.setDueDate(parseOptionalDate(req.dueDate(), "Срок задачи должен быть в формате yyyy-MM-dd"));
        task.setDueTime(parseOptionalTime(req.dueTime(), "Время срока должно быть в формате HH:mm"));
        task.setReminderEnabled(Boolean.TRUE.equals(req.reminderEnabled()));
        task.setReminderMinutesBefore(req.reminderMinutesBefore());
        reconcileSubtasks(task, req.subtasks());
        if (!task.isReminderEnabled()) task.setReminderMinutesBefore(null);
    }

    private void reconcileSubtasks(DayTask task, List<SubtaskInput> inputs) {
        if (inputs == null) return;
        if (inputs.size() > 50) throw ApiException.badRequest("Подзадач: максимум 50");

        Map<Long, TaskSubtask> existing = new LinkedHashMap<>();
        for (TaskSubtask subtask : task.getSubtasks()) {
            if (subtask.getId() != null) existing.put(subtask.getId(), subtask);
        }

        Set<Long> retainedIds = new LinkedHashSet<>();
        for (int index = 0; index < inputs.size(); index++) {
            SubtaskInput input = inputs.get(index);
            if (input == null) throw ApiException.badRequest("Некорректная подзадача");

            TaskSubtask subtask;
            if (input.id() == null) {
                subtask = new TaskSubtask(task, cleanSubtaskText(input.text()), index);
                task.addSubtask(subtask);
            } else {
                subtask = existing.get(input.id());
                if (subtask == null || !retainedIds.add(input.id())) {
                    throw ApiException.badRequest("Подзадача не принадлежит выбранной задаче");
                }
                subtask.setText(cleanSubtaskText(input.text()));
            }
            subtask.setDone(Boolean.TRUE.equals(input.done()));
            subtask.setSortOrder(index);
            if (input.id() == null || input.dueDate() != null) {
                subtask.setDueDate(parseOptionalDate(
                        input.dueDate(), "Срок подзадачи должен быть в формате yyyy-MM-dd"));
            }
        }

        task.getSubtasks().removeIf(subtask ->
                subtask.getId() != null && !retainedIds.contains(subtask.getId()));
    }


    private void validateBusinessRules(DayTask task) {
        validateDeadline(task);
        validateSubtaskDeadlines(task);
    }

    private void validateDeadline(DayTask task) {
        if (task.getDate() != null && task.getDueDate() != null && task.getDueDate().isBefore(task.getDate())) {
            throw ApiException.badRequest("Срок не может быть раньше времени задачи.");
        }
    }

    private void validateSubtaskDeadlines(DayTask task) {
        if (task.getDate() == null) return;
        boolean invalid = task.getSubtasks().stream()
                .map(TaskSubtask::getDueDate)
                .filter(Objects::nonNull)
                .anyMatch(dueDate -> dueDate.isBefore(task.getDate()));
        if (invalid) {
            throw ApiException.badRequest("Срок подзадачи не может быть раньше даты задачи.");
        }
    }

    private String cleanSubtaskText(String value) {
        String cleaned = cleanRequired(value, "Текст подзадачи не должен быть пустым");
        if (cleaned.length() > 300) throw ApiException.badRequest("Текст подзадачи: максимум 300 символов");
        return cleaned;
    }

    private DayTask requireOwnedTask(AppUser user, Long id) {
        if (id == null) throw ApiException.badRequest("Не указан id задачи");
        DayTask task = tasks.findById(id).orElseThrow(() -> ApiException.notFound("Задача не найдена"));
        if (!Objects.equals(task.getOwner().getId(), user.getId())) {
            securityEvents.warn("AUTHZ_OWNERSHIP_MISMATCH", user.getUsername(), "rejected",
                    "resource=task id=" + id);
            throw ApiException.notFound("Задача не найдена");
        }
        return task;
    }

    private String cleanRequired(String s, String message) {
        String value = s == null ? "" : s.trim();
        if (value.isBlank()) throw ApiException.badRequest(message);
        return value;
    }

    private String cleanTaskText(String value) {
        String cleaned = cleanRequired(value, "Текст задачи не должен быть пустым");
        if (cleaned.length() > 500) throw ApiException.badRequest("Текст задачи: максимум 500 символов");
        return cleaned;
    }

    private String cleanOptional(String s) {
        if (s == null) return null;
        String value = s.trim();
        return value.isBlank() ? null : value;
    }

    private String cleanCategory(String value) {
        String cleaned = cleanOptional(value);
        if (cleaned == null) return null;
        if (cleaned.length() > 80) throw ApiException.badRequest("Категория: максимум 80 символов");
        return cleaned.toLowerCase(Locale.ROOT);
    }

    private List<String> cleanTags(Collection<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            String tag = cleanTag(value);
            if (tag == null) continue;
            if (tag.length() > 40) throw ApiException.badRequest("Тег: максимум 40 символов");
            unique.add(tag);
            if (unique.size() > 10) throw ApiException.badRequest("Тегов задачи: максимум 10");
        }
        return List.copyOf(unique);
    }

    private String cleanTag(String value) {
        String cleaned = cleanOptional(value);
        if (cleaned == null) return null;
        cleaned = cleaned.replaceFirst("^#+", "").trim();
        if (cleaned.isBlank()) return null;
        return cleaned.toLowerCase(Locale.ROOT);
    }

    private LocalDate parseOptionalDate(String value, String message) {
        if (value == null || value.isBlank()) return null;
        return dayEntryService.parseDate(value, message);
    }

    private LocalTime parseOptionalTime(String value, String message) {
        if (value == null || value.isBlank()) return null;
        try { return LocalTime.parse(value); }
        catch (DateTimeParseException e) { throw ApiException.badRequest(message); }
    }
}
