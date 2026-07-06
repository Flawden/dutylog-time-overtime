package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.TaskCreateRequest;
import ru.daniil.shifts.dto.Dtos.TaskDto;
import ru.daniil.shifts.dto.Dtos.TaskUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayTask;
import ru.daniil.shifts.model.TaskPriority;
import ru.daniil.shifts.repo.DayTaskRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
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
