package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.TaskCreateRequest;
import ru.daniil.shifts.dto.Dtos.TaskDto;
import ru.daniil.shifts.dto.Dtos.TaskUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayTask;
import ru.daniil.shifts.repo.DayTaskRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
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
        if (req == null) {
            throw ApiException.badRequest("Некорректный JSON в запросе");
        }
        LocalDate d = dayEntryService.parseDate(req.date(), "Дата задачи должна быть в формате yyyy-MM-dd");
        DayTask task = new DayTask(user, d, req.text().trim());
        return TaskDto.from(tasks.save(task));
    }

    @Transactional
    public TaskDto update(AppUser user, Long id, TaskUpdateRequest req) {
        if (req == null) {
            throw ApiException.badRequest("Некорректный JSON в запросе");
        }
        DayTask task = requireOwnedTask(user, id);
        if (req.text() != null) {
            String text = req.text().trim();
            if (text.isBlank()) {
                throw ApiException.badRequest("Текст задачи не должен быть пустым");
            }
            task.setText(text);
        }
        if (req.done() != null) {
            task.setDone(req.done());
        }
        return TaskDto.from(tasks.save(task));
    }

    @Transactional
    public void delete(AppUser user, Long id) {
        DayTask task = requireOwnedTask(user, id);
        tasks.delete(task);
    }

    private DayTask requireOwnedTask(AppUser user, Long id) {
        if (id == null) {
            throw ApiException.badRequest("Не указан id задачи");
        }
        DayTask task = tasks.findById(id)
                .orElseThrow(() -> ApiException.notFound("Задача не найдена"));
        if (!Objects.equals(task.getOwner().getId(), user.getId())) {
            throw ApiException.notFound("Задача не найдена");
        }
        return task;
    }
}
