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
import ru.daniil.shifts.dto.Dtos.LegacyTaskDeadlineDto;
import ru.daniil.shifts.dto.Dtos.LegacyTaskDeadlineMigrationPreviewDto;
import ru.daniil.shifts.dto.Dtos.LegacyTaskDeadlineMigrationRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayTask;
import ru.daniil.shifts.model.TaskPriority;
import ru.daniil.shifts.model.TaskSubtask;
import ru.daniil.shifts.repo.DayTaskRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.DateTimeException;
import java.time.Duration;
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
import java.util.HashMap;

@Service
public class TaskService {
    private static final Comparator<DayTask> TASK_DISPLAY_ORDER = Comparator
            .comparing(DayTask::isDone)
            .thenComparing(TaskService::taskPlannedStartDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(TaskService::taskPlannedStartTime, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(DayTask::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(DayTask::getDueTime, Comparator.nullsLast(Comparator.naturalOrder()))
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
        Instant nowInstant = userTimeService.nowInstant();
        return tasks.findByOwnerOrderByDoneAscDueDateAscDueTimeAscDateAscCreatedAtAscIdAsc(user).stream()
                .filter(task -> taskIntersectsRange(task, d, d))
                .sorted(TASK_DISPLAY_ORDER)
                .map(task -> TaskDto.from(task, now, nowInstant)).toList();
    }

    @Transactional(readOnly = true)
    public List<TaskDto> listRange(AppUser user, LocalDate from, LocalDate to) {
        dayEntryService.validateRange(from, to);
        LocalDateTime now = userTimeService.workNow(user);
        Instant nowInstant = userTimeService.nowInstant();
        return tasks.findByOwnerOrderByDoneAscDueDateAscDueTimeAscDateAscCreatedAtAscIdAsc(user).stream()
                .filter(task -> taskIntersectsRange(task, from, to))
                .sorted(TASK_DISPLAY_ORDER)
                .map(task -> TaskDto.from(task, now, nowInstant)).toList();
    }


    @Transactional(readOnly = true)
    public TaskDto get(AppUser user, Long id) {
        DayTask task = requireOwnedTask(user, id);
        return TaskDto.from(task, userTimeService.workNow(user), userTimeService.nowInstant());
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
        List<String> projects = tasks.findDistinctProjects(user).stream()
                .map(this::cleanProject)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .limit(100)
                .toList();
        return new TaskMetadataDto(categories, tags, projects);
    }


    /** Source-compatible board query for callers created before project filters. */
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
        return listBoard(user, status, category, "all", priority, q,
                from, to, null, null, page, size);
    }


    /**
     * Source-compatible v27.19.0 signature. The long-standing from/to contract
     * filters by deadline (or the task date when there is no deadline).
     */
    @Transactional(readOnly = true)
    public PageDto<TaskDto> listBoard(AppUser user,
                                      String status,
                                      String category,
                                      String project,
                                      String priority,
                                      String q,
                                      String from,
                                      String to,
                                      int page,
                                      int size) {
        return listBoard(user, status, category, project, priority, q,
                from, to, null, null, page, size);
    }


    @Transactional(readOnly = true)
    public PageDto<TaskDto> listBoard(AppUser user,
                                      String status,
                                      String category,
                                      String project,
                                      String priority,
                                      String q,
                                      String from,
                                      String to,
                                      String scheduledFrom,
                                      String scheduledTo,
                                      int page,
                                      int size) {
        LocalDate fromDate = parseOptionalDate(from, "Дата from должна быть в формате yyyy-MM-dd");
        LocalDate toDate = parseOptionalDate(to, "Дата to должна быть в формате yyyy-MM-dd");
        LocalDate scheduledFromDate = parseOptionalDate(scheduledFrom,
                "Дата scheduledFrom должна быть в формате yyyy-MM-dd");
        LocalDate scheduledToDate = parseOptionalDate(scheduledTo,
                "Дата scheduledTo должна быть в формате yyyy-MM-dd");
        if (fromDate != null && toDate != null) dayEntryService.validateRange(fromDate, toDate);
        if (scheduledFromDate != null && scheduledToDate != null) {
            dayEntryService.validateRange(scheduledFromDate, scheduledToDate);
        }

        String statusFilter = normalizeBoardStatus(status);
        String cat = cleanOptional(category);
        String projectValue = cleanOptional(project);
        String pr = cleanOptional(priority);
        String query = cleanOptional(q);
        String queryLower = query != null ? query.toLowerCase(Locale.ROOT) : null;
        TaskPriority priorityFilter = null;
        if (pr != null && !"all".equalsIgnoreCase(pr)) {
            try { priorityFilter = TaskPriority.valueOf(pr.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException e) { throw ApiException.badRequest("Неизвестный приоритет задачи"); }
        }

        LocalDateTime now = userTimeService.workNow(user);
        Instant nowInstant = userTimeService.nowInstant();
        final String categoryFilter = cat;
        final String projectFilter = projectValue;
        final TaskPriority priorityFinal = priorityFilter;
        int safePage = safePage(page);
        int safeSize = safeSize(size);
        List<TaskDto> filtered = tasks.findByOwnerOrderByDoneAscDueDateAscDueTimeAscDateAscCreatedAtAscIdAsc(user).stream()
                .filter(t -> matchStatus(t, statusFilter, now))
                .filter(t -> categoryFilter == null || "all".equalsIgnoreCase(categoryFilter) || categoryFilter.equalsIgnoreCase(t.getCategory() == null ? "" : t.getCategory()))
                .filter(t -> projectFilter == null || "all".equalsIgnoreCase(projectFilter) || projectFilter.equalsIgnoreCase(t.getProject() == null ? "" : t.getProject()))
                .filter(t -> priorityFinal == null || t.getPriority() == priorityFinal)
                .filter(t -> withinTaskBoardDeadlineRange(t, fromDate, toDate))
                .filter(t -> taskIntersectsRange(t, scheduledFromDate, scheduledToDate))
                .filter(t -> queryLower == null || taskMatchesQuery(t, queryLower))
                .sorted(TASK_DISPLAY_ORDER)
                .map(task -> TaskDto.from(task, now, nowInstant))
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

    private boolean withinTaskBoardDeadlineRange(DayTask task, LocalDate from, LocalDate to) {
        if (from == null && to == null) return true;
        LocalDate effectiveDate = task.getDueDate() != null ? task.getDueDate() : task.getDate();
        if (effectiveDate == null) return false;
        if (from != null && effectiveDate.isBefore(from)) return false;
        return to == null || !effectiveDate.isAfter(to);
    }

    private static boolean taskIntersectsRange(DayTask task, LocalDate from, LocalDate to) {
        LocalDate start = taskPlannedStartDate(task);
        LocalDate end = task.getScheduledEndDate() != null ? task.getScheduledEndDate() : start;
        if (start == null) return from == null && to == null;
        if (from != null && end != null && end.isBefore(from)) return false;
        return to == null || !start.isAfter(to);
    }

    private static LocalDate taskPlannedStartDate(DayTask task) {
        return task.getScheduledStartDate() != null ? task.getScheduledStartDate() : task.getDate();
    }

    private static LocalTime taskPlannedStartTime(DayTask task) {
        return task.isAllDay() ? null : task.getScheduledStartTime();
    }

    private boolean taskMatchesQuery(DayTask task, String q) {
        return contains(task.getText(), q)
                || contains(task.getDescription(), q)
                || contains(task.getCategory(), q)
                || contains(task.getProject(), q)
                || contains(task.getDate() != null ? task.getDate().toString() : null, q)
                || contains(task.getScheduledStartDate() != null ? task.getScheduledStartDate().toString() : null, q)
                || contains(task.getScheduledStartTime() != null ? task.getScheduledStartTime().toString() : null, q)
                || contains(task.getScheduledEndDate() != null ? task.getScheduledEndDate().toString() : null, q)
                || contains(task.getScheduledEndTime() != null ? task.getScheduledEndTime().toString() : null, q)
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
        if (task.getDueInstant() != null) {
            return task.getDueInstant().isBefore(userTimeService.nowInstant());
        }
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
        applyCreateFields(user, task, req);
        validateBusinessRules(task);
        return TaskDto.from(tasks.save(task), userTimeService.workNow(user), userTimeService.nowInstant());
    }

    @Transactional
    public TaskDto update(AppUser user, Long id, TaskUpdateRequest req) {
        if (req == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        DayTask task = requireOwnedTask(user, id);
        if (req.text() != null) task.setText(cleanTaskText(req.text()));
        if (req.description() != null) task.setDescription(cleanDescription(req.description()));
        if (req.done() != null) task.setDone(req.done());
        if (req.date() != null && !req.date().isBlank()) task.setDate(dayEntryService.parseDate(req.date(), "Дата задачи должна быть в формате yyyy-MM-dd"));
        if (req.category() != null) task.setCategory(cleanCategory(req.category()));
        if (req.project() != null) task.setProject(cleanProject(req.project()));
        if (req.tags() != null) task.setTags(cleanTags(req.tags()));
        if (req.priority() != null) task.setPriority(req.priority());
        if (scheduleFieldsProvided(req)) applyScheduleUpdate(user, task, req);
        LocalDate previousDueDate = task.getDueDate();
        LocalTime previousDueTime = task.getDueTime();
        boolean deadlineFieldsProvided = req.dueDate() != null || req.dueTime() != null;
        if (req.dueDate() != null) task.setDueDate(parseOptionalDate(req.dueDate(), "Срок задачи должен быть в формате yyyy-MM-dd"));
        if (req.dueTime() != null) task.setDueTime(parseOptionalTime(req.dueTime(), "Время срока должно быть в формате HH:mm"));
        boolean deadlineValueChanged = !Objects.equals(previousDueDate, task.getDueDate())
                || !Objects.equals(previousDueTime, task.getDueTime());
        if (deadlineFieldsProvided && deadlineValueChanged) captureOrClearDeadline(user, task);
        if (req.reminderEnabled() != null) task.setReminderEnabled(req.reminderEnabled());
        if (req.reminderMinutesBefore() != null) task.setReminderMinutesBefore(req.reminderMinutesBefore());
        if (req.subtasks() != null) reconcileSubtasks(task, req.subtasks());
        if (Boolean.TRUE.equals(req.completeSubtasks()) && Boolean.TRUE.equals(req.done())) {
            task.getSubtasks().forEach(subtask -> subtask.setDone(true));
        }
        if (!task.isReminderEnabled()) task.setReminderMinutesBefore(null);
        validateBusinessRules(task);
        return TaskDto.from(tasks.save(task), userTimeService.workNow(user), userTimeService.nowInstant());
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
        return TaskDto.from(tasks.save(task), userTimeService.workNow(user), userTimeService.nowInstant());
    }

    @Transactional
    public void delete(AppUser user, Long id) {
        DayTask task = requireOwnedTask(user, id);
        tasks.delete(task);
    }

    /**
     * Freezes every legacy timed deadline in the old canonical timezone and
     * then reprojects all absolute deadlines into the new timezone.
     * Date-only deadlines remain floating calendar dates.
     */
    @Transactional
    public void rebaseForTimezoneChange(AppUser user, String previousTimezone, String nextTimezone) {
        ZoneId sourceZone = strictZone(previousTimezone);
        ZoneId targetZone = strictZone(nextTimezone);
        List<DayTask> owned = tasks.findByOwnerOrderByDoneAscDueDateAscDueTimeAscDateAscCreatedAtAscIdAsc(user);
        boolean changed = false;
        for (DayTask task : owned) {
            if (task.getDueDate() != null && task.getDueTime() != null) {
                if (!task.hasAbsoluteDeadline()) captureDeadline(task, sourceZone);
                projectDeadline(task, targetZone);
                changed = true;
            }
            if (!task.isAllDay() && task.getScheduledStartDate() != null && task.getScheduledStartTime() != null) {
                if (!task.hasScheduledStart()) captureSchedule(task, sourceZone);
                projectSchedule(task, targetZone);
                changed = true;
            }
        }
        if (changed) tasks.saveAll(owned);
    }

    @Transactional(readOnly = true)
    public LegacyTaskDeadlineMigrationPreviewDto previewLegacyDeadlines(AppUser user, String sourceTimezone) {
        ZoneId sourceZone = strictZone(sourceTimezone);
        ZoneId targetZone = userTimeService.workZone(user);
        List<LegacyTaskDeadlineDto> rows = legacyTimedTasks(user).stream()
                .map(task -> previewRow(task, sourceZone, targetZone))
                .toList();
        return new LegacyTaskDeadlineMigrationPreviewDto(
                sourceZone.getId(), targetZone.getId(), rows.size(), rows);
    }

    @Transactional
    public LegacyTaskDeadlineMigrationPreviewDto migrateLegacyDeadlines(
            AppUser user, LegacyTaskDeadlineMigrationRequest request) {
        if (request == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        ZoneId sourceZone = strictZone(request.sourceTimezone());
        ZoneId targetZone = userTimeService.workZone(user);
        List<DayTask> legacy = legacyTimedTasks(user);
        Map<Long, DayTask> byId = new HashMap<>();
        for (DayTask task : legacy) byId.put(task.getId(), task);

        if (request.taskIds() == null) throw ApiException.badRequest("Выберите хотя бы одну задачу");
        LinkedHashSet<Long> selected = new LinkedHashSet<>(request.taskIds());
        if (selected.isEmpty()) throw ApiException.badRequest("Выберите хотя бы одну задачу");
        List<DayTask> migrated = new java.util.ArrayList<>();
        for (Long id : selected) {
            DayTask task = byId.get(id);
            if (task == null) {
                throw ApiException.badRequest("Задача уже привязана, удалена или не принадлежит пользователю: " + id);
            }
            captureDeadline(task, sourceZone);
            projectDeadline(task, targetZone);
            migrated.add(task);
        }
        tasks.saveAll(migrated);
        tasks.flush();
        return previewLegacyDeadlines(user, sourceZone.getId());
    }

    private List<DayTask> legacyTimedTasks(AppUser user) {
        return tasks.findByOwnerOrderByDoneAscDueDateAscDueTimeAscDateAscCreatedAtAscIdAsc(user).stream()
                .filter(task -> task.getDueDate() != null && task.getDueTime() != null)
                .filter(task -> !task.hasAbsoluteDeadline())
                .toList();
    }

    private LegacyTaskDeadlineDto previewRow(DayTask task, ZoneId sourceZone, ZoneId targetZone) {
        ZonedDateTime source = userTimeService.resolveLocalDateTime(
                LocalDateTime.of(task.getDueDate(), task.getDueTime()), sourceZone);
        ZonedDateTime projected = source.toInstant().atZone(targetZone);
        return new LegacyTaskDeadlineDto(
                task.getId(), task.getText(),
                task.getDueDate().toString(), task.getDueTime().toString(), sourceZone.getId(),
                projected.toLocalDate().toString(), projected.toLocalTime().toString(), targetZone.getId(),
                source.toInstant().toString());
    }

    private void captureOrClearDeadline(AppUser user, DayTask task) {
        if (task.getDueDate() == null || task.getDueTime() == null) {
            clearDeadlineSnapshot(task);
            return;
        }
        captureDeadline(task, userTimeService.workZone(user));
    }

    private void captureDeadline(DayTask task, ZoneId sourceZone) {
        LocalDate sourceDate = task.getDueDate();
        LocalTime sourceTime = task.getDueTime();
        ZonedDateTime resolved = userTimeService.resolveLocalDateTime(
                LocalDateTime.of(sourceDate, sourceTime), sourceZone);
        task.setDueInstant(resolved.toInstant());
        task.setDueSourceTimezone(sourceZone.getId());
        task.setDueSourceDate(sourceDate);
        task.setDueSourceTime(sourceTime);
    }

    private void projectDeadline(DayTask task, ZoneId targetZone) {
        if (!task.hasAbsoluteDeadline()) return;
        ZonedDateTime projected = task.getDueInstant().atZone(targetZone);
        task.setDueDate(projected.toLocalDate());
        task.setDueTime(projected.toLocalTime());
    }

    private void clearDeadlineSnapshot(DayTask task) {
        task.setDueInstant(null);
        task.setDueSourceTimezone(null);
        task.setDueSourceDate(null);
        task.setDueSourceTime(null);
    }

    private ZoneId strictZone(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank() || value.length() > 80) {
            throw ApiException.badRequest("Часовой пояс должен быть IANA-идентификатором");
        }
        try {
            return ZoneId.of(value);
        } catch (DateTimeException e) {
            throw ApiException.badRequest("Неизвестный часовой пояс: " + value);
        }
    }

    private void applyCreateFields(AppUser user, DayTask task, TaskCreateRequest req) {
        task.setDescription(cleanDescription(req.description()));
        task.setCategory(cleanCategory(req.category()));
        task.setProject(cleanProject(req.project()));
        task.setTags(cleanTags(req.tags()));
        task.setPriority(req.priority() != null ? req.priority() : TaskPriority.NORMAL);
        applyScheduleCreate(user, task, req);
        task.setDueDate(parseOptionalDate(req.dueDate(), "Срок задачи должен быть в формате yyyy-MM-dd"));
        task.setDueTime(parseOptionalTime(req.dueTime(), "Время срока должно быть в формате HH:mm"));
        captureOrClearDeadline(user, task);
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


    private boolean scheduleFieldsProvided(TaskUpdateRequest req) {
        return req.allDay() != null
                || req.scheduledStartDate() != null
                || req.scheduledStartTime() != null
                || req.scheduledEndDate() != null
                || req.scheduledEndTime() != null
                || req.scheduledDurationMinutes() != null;
    }

    private void applyScheduleCreate(AppUser user, DayTask task, TaskCreateRequest req) {
        boolean allDay = req.allDay() == null || req.allDay();
        LocalDate startDate = parseOptionalDate(req.scheduledStartDate(), "Дата начала должна быть в формате yyyy-MM-dd");
        LocalTime startTime = parseOptionalTime(req.scheduledStartTime(), "Время начала должно быть в формате HH:mm");
        LocalDate endDate = parseOptionalDate(req.scheduledEndDate(), "Дата окончания должна быть в формате yyyy-MM-dd");
        LocalTime endTime = parseOptionalTime(req.scheduledEndTime(), "Время окончания должно быть в формате HH:mm");
        applySchedule(user, task, allDay, startDate, startTime, endDate, endTime, req.scheduledDurationMinutes());
    }

    private void applyScheduleUpdate(AppUser user, DayTask task, TaskUpdateRequest req) {
        boolean allDay = req.allDay() != null ? req.allDay() : task.isAllDay();
        LocalDate startDate = req.scheduledStartDate() != null
                ? parseOptionalDate(req.scheduledStartDate(), "Дата начала должна быть в формате yyyy-MM-dd")
                : task.getScheduledStartDate();
        LocalTime startTime = req.scheduledStartTime() != null
                ? parseOptionalTime(req.scheduledStartTime(), "Время начала должно быть в формате HH:mm")
                : task.getScheduledStartTime();
        LocalDate endDate = req.scheduledEndDate() != null
                ? parseOptionalDate(req.scheduledEndDate(), "Дата окончания должна быть в формате yyyy-MM-dd")
                : task.getScheduledEndDate();
        LocalTime endTime = req.scheduledEndTime() != null
                ? parseOptionalTime(req.scheduledEndTime(), "Время окончания должно быть в формате HH:mm")
                : task.getScheduledEndTime();

        Integer duration = req.scheduledDurationMinutes();
        boolean startChanged = req.scheduledStartDate() != null || req.scheduledStartTime() != null;
        boolean endExplicit = req.scheduledEndDate() != null || req.scheduledEndTime() != null;
        if (duration == null && startChanged && !endExplicit
                && task.getScheduledStartInstant() != null && task.getScheduledEndInstant() != null) {
            long existing = Duration.between(task.getScheduledStartInstant(), task.getScheduledEndInstant()).toMinutes();
            if (existing > 0 && existing <= 10080) duration = (int) existing;
        }
        applySchedule(user, task, allDay, startDate, startTime, endDate, endTime, duration);
    }

    private void applySchedule(AppUser user,
                               DayTask task,
                               boolean allDay,
                               LocalDate startDate,
                               LocalTime startTime,
                               LocalDate endDate,
                               LocalTime endTime,
                               Integer durationMinutes) {
        task.setAllDay(allDay);
        if (allDay) {
            clearScheduleSnapshot(task);
            return;
        }
        LocalDate resolvedStartDate = startDate != null ? startDate : task.getDate();
        if (startTime == null) throw ApiException.badRequest("Для задачи со временем укажите начало.");
        task.setScheduledStartDate(resolvedStartDate);
        task.setScheduledStartTime(startTime);
        task.setDate(resolvedStartDate);

        boolean hasEndDate = endDate != null;
        boolean hasEndTime = endTime != null;
        if (hasEndDate != hasEndTime) {
            throw ApiException.badRequest("Для окончания нужны и дата, и время.");
        }
        task.setScheduledEndDate(endDate);
        task.setScheduledEndTime(endTime);
        captureSchedule(task, userTimeService.workZone(user));

        if (durationMinutes != null) {
            if (durationMinutes <= 0 || durationMinutes > 10080) {
                throw ApiException.badRequest("Длительность должна быть от 1 минуты до 7 дней.");
            }
            Instant endInstant = task.getScheduledStartInstant().plus(Duration.ofMinutes(durationMinutes));
            ZonedDateTime localEnd = endInstant.atZone(userTimeService.workZone(user));
            task.setScheduledEndInstant(endInstant);
            task.setScheduledEndDate(localEnd.toLocalDate());
            task.setScheduledEndTime(localEnd.toLocalTime());
            task.setScheduledSourceEndDate(localEnd.toLocalDate());
            task.setScheduledSourceEndTime(localEnd.toLocalTime());
        }
    }

    private void captureSchedule(DayTask task, ZoneId sourceZone) {
        LocalDate startDate = task.getScheduledStartDate();
        LocalTime startTime = task.getScheduledStartTime();
        if (startDate == null || startTime == null) {
            clearScheduleSnapshot(task);
            task.setAllDay(false);
            return;
        }
        ZonedDateTime start = userTimeService.resolveLocalDateTime(LocalDateTime.of(startDate, startTime), sourceZone);
        task.setScheduledStartInstant(start.toInstant());
        task.setScheduledSourceTimezone(sourceZone.getId());
        task.setScheduledSourceStartDate(startDate);
        task.setScheduledSourceStartTime(startTime);

        if (task.getScheduledEndDate() != null && task.getScheduledEndTime() != null) {
            ZonedDateTime end = userTimeService.resolveLocalDateTime(
                    LocalDateTime.of(task.getScheduledEndDate(), task.getScheduledEndTime()), sourceZone);
            task.setScheduledEndInstant(end.toInstant());
            task.setScheduledSourceEndDate(task.getScheduledEndDate());
            task.setScheduledSourceEndTime(task.getScheduledEndTime());
        } else {
            task.setScheduledEndInstant(null);
            task.setScheduledSourceEndDate(null);
            task.setScheduledSourceEndTime(null);
        }
    }

    private void projectSchedule(DayTask task, ZoneId targetZone) {
        if (!task.hasScheduledStart()) return;
        ZonedDateTime start = task.getScheduledStartInstant().atZone(targetZone);
        task.setScheduledStartDate(start.toLocalDate());
        task.setScheduledStartTime(start.toLocalTime());
        task.setDate(start.toLocalDate());
        if (task.hasScheduledEnd()) {
            ZonedDateTime end = task.getScheduledEndInstant().atZone(targetZone);
            task.setScheduledEndDate(end.toLocalDate());
            task.setScheduledEndTime(end.toLocalTime());
        } else {
            task.setScheduledEndDate(null);
            task.setScheduledEndTime(null);
        }
    }

    private void clearScheduleSnapshot(DayTask task) {
        task.setScheduledStartDate(null);
        task.setScheduledStartTime(null);
        task.setScheduledEndDate(null);
        task.setScheduledEndTime(null);
        task.setScheduledStartInstant(null);
        task.setScheduledEndInstant(null);
        task.setScheduledSourceTimezone(null);
        task.setScheduledSourceStartDate(null);
        task.setScheduledSourceStartTime(null);
        task.setScheduledSourceEndDate(null);
        task.setScheduledSourceEndTime(null);
    }


    private void validateBusinessRules(DayTask task) {
        validateSchedule(task);
        validateDeadline(task);
        validateSubtaskDeadlines(task);
    }

    private void validateSchedule(DayTask task) {
        if (task.isAllDay()) return;
        if (task.getScheduledStartDate() == null || task.getScheduledStartTime() == null || task.getScheduledStartInstant() == null) {
            throw ApiException.badRequest("Для задачи со временем укажите начало.");
        }
        boolean hasEndDate = task.getScheduledEndDate() != null;
        boolean hasEndTime = task.getScheduledEndTime() != null;
        if (hasEndDate != hasEndTime) {
            throw ApiException.badRequest("Для окончания нужны и дата, и время.");
        }
        if (task.getScheduledEndInstant() != null) {
            Duration interval = Duration.between(task.getScheduledStartInstant(), task.getScheduledEndInstant());
            if (interval.isZero() || interval.isNegative()) {
                throw ApiException.badRequest("Окончание должно быть позже начала.");
            }
            if (interval.compareTo(Duration.ofDays(7)) > 0) {
                throw ApiException.badRequest("Длительность должна быть не больше 7 дней.");
            }
        }
    }

    private void validateDeadline(DayTask task) {
        if (task.getDueDate() == null) return;
        Instant plannedBoundary = task.getScheduledEndInstant() != null
                ? task.getScheduledEndInstant()
                : task.getScheduledStartInstant();
        if (task.getDueInstant() != null && plannedBoundary != null) {
            if (task.getDueInstant().isBefore(plannedBoundary)) {
                throw ApiException.badRequest("Дедлайн не может быть раньше окончания запланированного интервала.");
            }
            return;
        }
        LocalDate plannedDate = task.getScheduledEndDate() != null
                ? task.getScheduledEndDate()
                : taskPlannedStartDate(task);
        LocalDate deadlineDate = task.hasAbsoluteDeadline() && task.getDueSourceDate() != null
                ? task.getDueSourceDate()
                : task.getDueDate();
        if (plannedDate != null && deadlineDate != null && deadlineDate.isBefore(plannedDate)) {
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

    private String cleanDescription(String value) {
        String cleaned = cleanOptional(value);
        if (cleaned == null) return null;
        if (cleaned.length() > 4000) throw ApiException.badRequest("Описание задачи: максимум 4000 символов");
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

    private String cleanProject(String value) {
        String cleaned = cleanOptional(value);
        if (cleaned == null) return null;
        if (cleaned.length() > 80) throw ApiException.badRequest("Проект: максимум 80 символов");
        return cleaned;
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
