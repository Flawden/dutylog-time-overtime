package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.TaskCreateRequest;
import ru.daniil.shifts.dto.Dtos.TaskUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.TaskPriority;
import ru.daniil.shifts.repo.DayTaskRepository;
import ru.daniil.shifts.repo.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Persistence rules for task reminder fields used by the notification engine. */
@SpringBootTest
@Transactional
class TaskReminderServiceTest {

    @Autowired TaskService tasks;
    @Autowired DayTaskRepository taskRepo;
    @Autowired UserRepository users;

    AppUser user;

    @BeforeEach
    void setUp() {
        user = users.save(new AppUser("task-reminder-user", "{noop}unused"));
    }

    @Test
    void enabledReminderKeepsDueTimeAndLeadMinutes() {
        var created = tasks.create(user, new TaskCreateRequest(
                "2026-07-16",
                "Проверить задачу",
                "qa",
                TaskPriority.NORMAL,
                "2026-07-16",
                "19:26",
                true,
                2
        ));

        var entity = taskRepo.findById(created.id()).orElseThrow();
        assertTrue(entity.isReminderEnabled());
        assertEquals(2, entity.getReminderMinutesBefore());
        assertEquals("19:26", entity.getDueTime().toString());
    }

    @Test
    void disablingReminderClearsStaleLeadMinutes() {
        var created = tasks.create(user, new TaskCreateRequest(
                "2026-07-16", "Проверить задачу", null, TaskPriority.NORMAL,
                "2026-07-16", "19:26", true, 2));

        tasks.update(user, created.id(), new TaskUpdateRequest(
                null, null, null, null, null,
                null, null, false, null));

        var entity = taskRepo.findById(created.id()).orElseThrow();
        assertFalse(entity.isReminderEnabled());
        assertNull(entity.getReminderMinutesBefore());
    }

    @Test
    void reminderDisabledOnCreateCannotLeaveOrphanLeadMinutes() {
        var created = tasks.create(user, new TaskCreateRequest(
                "2026-07-16", "Без напоминания", null, TaskPriority.NORMAL,
                "2026-07-16", "19:26", false, 60));

        var entity = taskRepo.findById(created.id()).orElseThrow();
        assertFalse(entity.isReminderEnabled());
        assertNull(entity.getReminderMinutesBefore());
    }
}
