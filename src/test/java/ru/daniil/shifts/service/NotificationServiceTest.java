package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.ImportantDayCreateRequest;
import ru.daniil.shifts.dto.Dtos.NotificationReminderDto;
import ru.daniil.shifts.dto.Dtos.TaskCreateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.DayTask;
import ru.daniil.shifts.model.NotificationSettings;
import ru.daniil.shifts.model.RepeatMode;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.model.TaskPriority;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.DayTaskRepository;
import ru.daniil.shifts.repo.NotificationSettingsRepository;
import ru.daniil.shifts.repo.ShiftTypeRepository;
import ru.daniil.shifts.repo.UserRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioural contract for the reminder engine used by Web/PWA, Android and Telegram.
 * The browser scheduler is only a delivery layer; these tests protect the actual times
 * and the source filtering rules calculated by the backend.
 */
@SpringBootTest
@Transactional
class NotificationServiceTest {

    @Autowired NotificationService notifications;
    @Autowired ImportantDayService importantDays;
    @Autowired UserRepository users;
    @Autowired ShiftTypeRepository shiftTypes;
    @Autowired DayEntryRepository days;
    @Autowired DayTaskRepository tasks;
    @Autowired NotificationSettingsRepository settingsRepo;
    @Autowired ShiftOccurrenceService shiftOccurrences;
    @Autowired TaskService taskService;

    AppUser user;

    @BeforeEach
    void setUp() {
        user = users.save(new AppUser("notification-engine-user", "{noop}unused"));
    }

    @Test
    void calculatesExactShiftTaskImportantDayAndDigestTimes() {
        LocalDate date = LocalDate.of(2026, 7, 20);

        ShiftType shift = new ShiftType(user, "QA shift", 8, "#123456", false,
                LocalTime.of(8, 30), LocalTime.of(16, 30), 0, 8.0);
        shift.setNotificationsEnabled(true);
        shift.setNotificationMinutesBefore(15);
        shift = shiftTypes.save(shift);

        DayEntry day = new DayEntry(user, date);
        day.setShiftType(shift);
        days.save(day);

        DayTask task = new DayTask(user, date, "Проверить уведомление");
        task.setDueDate(date);
        task.setDueTime(LocalTime.of(19, 26));
        task.setReminderEnabled(true);
        task.setReminderMinutesBefore(2);
        task = tasks.save(task);

        var important = importantDays.create(user, new ImportantDayCreateRequest(
                "Годовщина", date.toString(), RepeatMode.NONE, "#F5B841"));

        NotificationSettings settings = notifications.settingsEntity(user);
        settings.setShiftRemindersEnabled(true);
        settings.setTaskRemindersEnabled(true);
        settings.setImportantDayRemindersEnabled(true);
        settings.setImportantDayDaysBefore(3);
        settings.setImportantDayReminderTime(LocalTime.of(9, 15));
        settings.setTomorrowDigestEnabled(true);
        settings.setTomorrowDigestTime(LocalTime.of(18, 0));
        settingsRepo.save(settings);

        List<NotificationReminderDto> result = notifications.upcoming(user, date, date, true);

        assertReminder(result, "shift:" + date, "SHIFT", "2026-07-20T08:15");
        assertReminder(result, "task:" + task.getId(), "TASK", "2026-07-20T19:24");
        assertReminder(result, "important:" + important.id() + ":" + date,
                "IMPORTANT_DAY", "2026-07-17T09:15");
        assertReminder(result, "digest:" + date, "TOMORROW_DIGEST", "2026-07-19T18:00");

        assertEquals(List.of("IMPORTANT_DAY", "TOMORROW_DIGEST", "SHIFT", "TASK"),
                result.stream().map(NotificationReminderDto::type).toList(),
                "reminders must stay sorted by remindAt and then priority");
    }

    @Test
    void browserInstantUsesTheUsersSavedIanaTimezone() {
        LocalDate date = LocalDate.of(2026, 7, 22);
        user.setWorkTimezone("Asia/Yekaterinburg");
        user = users.save(user);

        DayTask past = new DayTask(user, date, "Уже прошедшая локальная задача");
        past.setDueDate(date);
        past.setDueTime(LocalTime.of(12, 5));
        past.setReminderEnabled(true);
        past.setReminderMinutesBefore(0);
        past = tasks.save(past);
        Long pastId = past.getId();

        DayTask due = new DayTask(user, date, "Текущая локальная задача");
        due.setDueDate(date);
        due.setDueTime(LocalTime.of(14, 5));
        due.setReminderEnabled(true);
        due.setReminderMinutesBefore(0);
        due = tasks.save(due);
        Long dueId = due.getId();

        NotificationSettings settings = notifications.settingsEntity(user);
        settings.setShiftRemindersEnabled(false);
        settings.setTaskRemindersEnabled(true);
        settings.setImportantDayRemindersEnabled(false);
        settings.setTomorrowDigestEnabled(false);
        settingsRepo.save(settings);

        List<NotificationReminderDto> result = notifications.upcoming(user, date, date, true);
        NotificationReminderDto pastReminder = result.stream()
                .filter(r -> r.id().equals("task:" + pastId))
                .findFirst().orElseThrow();
        NotificationReminderDto dueReminder = result.stream()
                .filter(r -> r.id().equals("task:" + dueId))
                .findFirst().orElseThrow();

        assertEquals("2026-07-22T12:05", pastReminder.remindAt());
        assertEquals("2026-07-22T07:05:00Z", pastReminder.remindAtInstant());
        assertEquals("Asia/Yekaterinburg", pastReminder.workTimezone());
        assertEquals("2026-07-22T12:05", pastReminder.displayAt());
        assertEquals("Asia/Yekaterinburg", pastReminder.displayTimezone());
        assertEquals("2026-07-22T14:05", dueReminder.remindAt());
        assertEquals("2026-07-22T09:05:00Z", dueReminder.remindAtInstant());
        assertEquals("2026-07-22T14:05", dueReminder.displayAt());
    }

    @Test
    void shiftReminderFollowsTheOccurrenceInstantAndProjectedCalendarDate() {
        user.setWorkTimezone("Asia/Yekaterinburg");
        user.setDisplayTimezone("Asia/Yekaterinburg");
        user = users.save(user);

        LocalDate sourceDate = LocalDate.of(2026, 7, 3);
        ShiftType shift = new ShiftType(user, "Поздняя", 8, "#334455", false,
                LocalTime.of(23, 0), LocalTime.of(7, 0), 0, 8.0);
        shift.setNotificationsEnabled(true);
        shift = shiftTypes.save(shift);

        DayEntry day = new DayEntry(user, sourceDate);
        day.setShiftType(shift);
        shiftOccurrences.capture(day, ZoneId.of("Europe/Kyiv"));
        days.saveAndFlush(day);

        NotificationSettings settings = notifications.settingsEntity(user);
        settings.setShiftRemindersEnabled(true);
        settings.setShiftReminderMinutesBefore(30);
        settings.setTaskRemindersEnabled(false);
        settings.setImportantDayRemindersEnabled(false);
        settings.setTomorrowDigestEnabled(false);
        settingsRepo.save(settings);

        NotificationReminderDto reminder = notifications
                .upcoming(user, LocalDate.of(2026, 7, 4), LocalDate.of(2026, 7, 4), true)
                .stream().filter(r -> r.type().equals("SHIFT")).findFirst().orElseThrow();

        assertEquals("2026-07-04", reminder.sourceDate());
        assertEquals("2026-07-04T00:30", reminder.remindAt());
        assertEquals("2026-07-03T19:30:00Z", reminder.remindAtInstant());
        assertEquals("Asia/Yekaterinburg", reminder.workTimezone());
        assertTrue(reminder.details().contains("Начало 01:00 Asia/Yekaterinburg"));
    }

    @Test
    void taskReminderKeepsTheDeadlineInstantAcrossTimezoneChanges() {
        user.setWorkTimezone("Asia/Yekaterinburg");
        user.setDisplayTimezone("Asia/Yekaterinburg");
        user = users.save(user);

        var task = taskService.create(user, new TaskCreateRequest(
                "2035-07-26", "Проверить срок", null, null, TaskPriority.NORMAL,
                "2035-07-26", "14:10", true, 0));

        taskService.rebaseForTimezoneChange(user, "Asia/Yekaterinburg", "Europe/Moscow");
        user.setWorkTimezone("Europe/Moscow");
        user.setDisplayTimezone("Europe/Moscow");
        user = users.save(user);

        NotificationSettings settings = notifications.settingsEntity(user);
        settings.setShiftRemindersEnabled(false);
        settings.setTaskRemindersEnabled(true);
        settings.setImportantDayRemindersEnabled(false);
        settings.setTomorrowDigestEnabled(false);
        settingsRepo.save(settings);

        NotificationReminderDto reminder = notifications
                .upcoming(user, LocalDate.of(2035, 7, 26), LocalDate.of(2035, 7, 26), true)
                .stream().filter(r -> r.id().equals("task:" + task.id())).findFirst().orElseThrow();

        assertEquals("2035-07-26T12:10", reminder.remindAt());
        assertEquals("2035-07-26T09:10:00Z", reminder.remindAtInstant());
        assertEquals("Europe/Moscow", reminder.workTimezone());
        assertEquals("2035-07-26", reminder.sourceDate());
        assertTrue(reminder.details().contains("12:10 Europe/Moscow"));
    }

    @Test
    void doneTasksAndShiftTypesWithNotificationsDisabledAreExcluded() {
        LocalDate date = LocalDate.of(2026, 8, 1);

        ShiftType shift = new ShiftType(user, "Silent shift", 8, "#654321", false,
                LocalTime.of(8, 0), LocalTime.of(16, 0), 0, 8.0);
        shift.setNotificationsEnabled(false);
        shift = shiftTypes.save(shift);
        DayEntry day = new DayEntry(user, date);
        day.setShiftType(shift);
        days.save(day);

        DayTask task = new DayTask(user, date, "Уже выполнено");
        task.setDueDate(date);
        task.setDueTime(LocalTime.of(12, 0));
        task.setReminderEnabled(true);
        task.setReminderMinutesBefore(10);
        task.setDone(true);
        tasks.save(task);

        NotificationSettings settings = notifications.settingsEntity(user);
        settings.setShiftRemindersEnabled(true);
        settings.setTaskRemindersEnabled(true);
        settings.setImportantDayRemindersEnabled(false);
        settings.setTomorrowDigestEnabled(false);
        settingsRepo.save(settings);

        List<NotificationReminderDto> result = notifications.upcoming(user, date, date, true);
        assertTrue(result.isEmpty(), "silent shifts and completed tasks must not create reminders");
    }

    @Test
    void remindersNeverLeakBetweenUsers() {
        AppUser other = users.save(new AppUser("notification-other-user", "{noop}unused"));
        LocalDate date = LocalDate.of(2026, 9, 10);

        DayTask foreignTask = new DayTask(other, date, "Чужая задача");
        foreignTask.setDueDate(date);
        foreignTask.setDueTime(LocalTime.of(10, 0));
        foreignTask.setReminderEnabled(true);
        tasks.save(foreignTask);

        NotificationSettings settings = notifications.settingsEntity(user);
        settings.setShiftRemindersEnabled(false);
        settings.setTaskRemindersEnabled(true);
        settings.setImportantDayRemindersEnabled(false);
        settings.setTomorrowDigestEnabled(false);
        settingsRepo.save(settings);

        List<NotificationReminderDto> result = notifications.upcoming(user, date, date, true);
        assertFalse(result.stream().anyMatch(r -> r.title().contains("Чужая")));
        assertTrue(result.isEmpty(), "a user with no own sources must receive no reminders");
    }

    private static void assertReminder(List<NotificationReminderDto> result,
                                       String id,
                                       String type,
                                       String remindAt) {
        NotificationReminderDto reminder = result.stream()
                .filter(r -> id.equals(r.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing reminder " + id + ": " + result));
        assertEquals(type, reminder.type());
        assertEquals(remindAt, reminder.remindAt());
    }
}
