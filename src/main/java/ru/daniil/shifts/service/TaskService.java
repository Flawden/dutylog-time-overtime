package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.TaskCreateRequest;
import ru.daniil.shifts.dto.Dtos.TaskDto;
import ru.daniil.shifts.dto.Dtos.TaskUpdateRequest;
import ru.daniil.shifts.dto.Dtos.PageDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayTask;
import ru.daniil.shifts.model.TaskPriority;
import ru.daniil.shifts.repo.DayTaskRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class TaskService {
    private final DayTaskRepository tasks;
    private final DayEntryService dayEntryService;

    public TaskService(DayTaskRepository tasks, DayEntryService dayEntryService) {
        this.tasks = tasks;
        this.dayEntryService = dayEntryService;
    }

    @Transactional(readOnly = true)
    public List<TaskDto> listDay(AppUser user, String date) {
        LocalDate d = dayEntryService.parseDate(date, "Дата должна быть в формате yyyy-MM-dd");
        return tasks.findByOwnerAndDateOrderByCreatedAtAscIdAsc(user, d).stream()
                .map(TaskDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TaskDto> listRange(AppUser user, LocalDate from, LocalDate to) {
        dayEntryService.validateRange(from, to);
        return tasks.findByOwnerAndDateBetweenOrderByDateAscCreatedAtAscIdAsc(user, from, to).stream()
                .map(TaskDto::from).toList();
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

        String st = cleanOptional(status);
        if (st == null) st = "open";
        String cat = cleanOptional(category);
        String pr = cleanOptional(priority);
        String query = cleanOptional(q);
        String queryLower = query != null ? query.toLowerCase(Locale.ROOT) : null;
        TaskPriority priorityFilter = null;
        if (pr != null && !"all".equalsIgnoreCase(pr)) {
            try { priorityFilter = TaskPriority.valueOf(pr.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException e) { throw ApiException.badRequest("Неизвестный приоритет задачи"); }
        }

        final String statusFilter = st.toLowerCase(Locale.ROOT);
        final String categoryFilter = cat;
        final TaskPriority priorityFinal = priorityFilter;
        int safePage = safePage(page);
        int safeSize = safeSize(size);
        List<TaskDto> filtered = tasks.findByOwnerOrderByDoneAscDueDateAscDueTimeAscDateAscCreatedAtAscIdAsc(user).stream()
                .filter(t -> matchStatus(t, statusFilter))
                .filter(t -> categoryFilter == null || "all".equalsIgnoreCase(categoryFilter) || categoryFilter.equalsIgnoreCase(t.getCategory() == null ? "" : t.getCategory()))
                .filter(t -> priorityFinal == null || t.getPriority() == priorityFinal)
                .filter(t -> withinTaskBoardRange(t, fromDate, toDate))
                .filter(t -> queryLower == null || taskMatchesQuery(t, queryLower))
                .sorted(Comparator
                        .comparing(DayTask::isDone)
                        .thenComparing((DayTask t) -> taskSortDate(t), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(t -> t.getDueTime(), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(DayTask::getDate)
                        .thenComparing(DayTask::getCreatedAt)
                        .thenComparing(DayTask::getId))
                .map(TaskDto::from)
                .toList();
        return PageDto.of(pageSlice(filtered, safePage, safeSize), safePage, safeSize, filtered.size());
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

    private boolean matchStatus(DayTask task, String status) {
        return switch (status) {
            case "all" -> true;
            case "open" -> !task.isDone();
            case "done" -> task.isDone();
            case "overdue" -> isOverdue(task);
            case "upcoming" -> !task.isDone() && !isOverdue(task);
            default -> throw ApiException.badRequest("Неизвестный статус задач");
        };
    }

    private boolean withinTaskBoardRange(DayTask task, LocalDate from, LocalDate to) {
        LocalDate d = taskSortDate(task);
        if (d == null) return from == null && to == null;
        if (from != null && d.isBefore(from)) return false;
        return to == null || !d.isAfter(to);
    }

    private LocalDate taskSortDate(DayTask task) {
        return task.getDueDate() != null ? task.getDueDate() : task.getDate();
    }

    private boolean taskMatchesQuery(DayTask task, String q) {
        return contains(task.getText(), q)
                || contains(task.getCategory(), q)
                || contains(task.getDate() != null ? task.getDate().toString() : null, q)
                || contains(task.getDueDate() != null ? task.getDueDate().toString() : null, q)
                || contains(task.getPriority() != null ? task.getPriority().name() : null, q);
    }

    private boolean contains(String value, String q) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(q);
    }

    private boolean isOverdue(DayTask task) {
        if (task.isDone() || task.getDueDate() == null) return false;
        LocalDate today = LocalDate.now();
        if (task.getDueDate().isBefore(today)) return true;
        if (task.getDueDate().isAfter(today) || task.getDueTime() == null) return false;
        return task.getDueTime().isBefore(LocalTime.now());
    }

    @Transactional
    public TaskDto create(AppUser user, TaskCreateRequest req) {
        if (req == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        LocalDate d = dayEntryService.parseDate(req.date(), "Дата задачи должна быть в формате yyyy-MM-dd");
        String text = cleanRequired(req.text(), "Текст задачи не должен быть пустым");
        DayTask task = new DayTask(user, d, text);
        applyCreateFields(task, req);
        return TaskDto.from(tasks.save(task));
    }

    @Transactional
    public TaskDto update(AppUser user, Long id, TaskUpdateRequest req) {
        if (req == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        DayTask task = requireOwnedTask(user, id);
        if (req.text() != null) task.setText(cleanRequired(req.text(), "Текст задачи не должен быть пустым"));
        if (req.done() != null) task.setDone(req.done());
        if (req.date() != null && !req.date().isBlank()) task.setDate(dayEntryService.parseDate(req.date(), "Дата задачи должна быть в формате yyyy-MM-dd"));
        if (req.category() != null) task.setCategory(cleanOptional(req.category()));
        if (req.priority() != null) task.setPriority(req.priority());
        if (req.dueDate() != null) task.setDueDate(parseOptionalDate(req.dueDate(), "Срок задачи должен быть в формате yyyy-MM-dd"));
        if (req.dueTime() != null) task.setDueTime(parseOptionalTime(req.dueTime(), "Время срока должно быть в формате HH:mm"));
        if (req.reminderEnabled() != null) task.setReminderEnabled(req.reminderEnabled());
        if (req.reminderMinutesBefore() != null) task.setReminderMinutesBefore(req.reminderMinutesBefore());
        if (!task.isReminderEnabled()) task.setReminderMinutesBefore(null);
        return TaskDto.from(tasks.save(task));
    }

    @Transactional
    public void delete(AppUser user, Long id) {
        DayTask task = requireOwnedTask(user, id);
        tasks.delete(task);
    }

    private void applyCreateFields(DayTask task, TaskCreateRequest req) {
        task.setCategory(cleanOptional(req.category()));
        task.setPriority(req.priority() != null ? req.priority() : TaskPriority.NORMAL);
        task.setDueDate(parseOptionalDate(req.dueDate(), "Срок задачи должен быть в формате yyyy-MM-dd"));
        task.setDueTime(parseOptionalTime(req.dueTime(), "Время срока должно быть в формате HH:mm"));
        task.setReminderEnabled(Boolean.TRUE.equals(req.reminderEnabled()));
        task.setReminderMinutesBefore(req.reminderMinutesBefore());
        if (!task.isReminderEnabled()) task.setReminderMinutesBefore(null);
    }

    private DayTask requireOwnedTask(AppUser user, Long id) {
        if (id == null) throw ApiException.badRequest("Не указан id задачи");
        DayTask task = tasks.findById(id).orElseThrow(() -> ApiException.notFound("Задача не найдена"));
        if (!Objects.equals(task.getOwner().getId(), user.getId())) throw ApiException.notFound("Задача не найдена");
        return task;
    }

    private String cleanRequired(String s, String message) {
        String value = s == null ? "" : s.trim();
        if (value.isBlank()) throw ApiException.badRequest(message);
        return value;
    }

    private String cleanOptional(String s) {
        if (s == null) return null;
        String value = s.trim();
        return value.isBlank() ? null : value;
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
