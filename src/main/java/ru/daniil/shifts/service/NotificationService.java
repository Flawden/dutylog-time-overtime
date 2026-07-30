package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.ImportantDayOccurrenceDto;
import ru.daniil.shifts.dto.Dtos.NotificationReminderDto;
import ru.daniil.shifts.dto.Dtos.NotificationSettingsDto;
import ru.daniil.shifts.dto.Dtos.NotificationSettingsUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.DayTask;
import ru.daniil.shifts.model.NotificationSettings;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.DayTaskRepository;
import ru.daniil.shifts.repo.NotificationSettingsRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Расчёт напоминаний. Пока это «центр правил»: backend отдаёт список будущих
 * напоминаний, а Web/PWA, Android или Telegram уже решают, как именно их показать.
 */
@Service
public class NotificationService {
    private final NotificationSettingsRepository settingsRepo;
    private final DayEntryRepository dayEntryRepository;
    private final DayTaskRepository taskRepository;
    private final ImportantDayService importantDayService;
    private final DayEntryService dayEntryService;
    private final UserTimeService userTimeService;

    public NotificationService(NotificationSettingsRepository settingsRepo,
                               DayEntryRepository dayEntryRepository,
                               DayTaskRepository taskRepository,
                               ImportantDayService importantDayService,
                               DayEntryService dayEntryService,
                               UserTimeService userTimeService) {
        this.settingsRepo = settingsRepo;
        this.dayEntryRepository = dayEntryRepository;
        this.taskRepository = taskRepository;
        this.importantDayService = importantDayService;
        this.dayEntryService = dayEntryService;
        this.userTimeService = userTimeService;
    }

    @Transactional
    public NotificationSettings settingsEntity(AppUser user) {
        return settingsRepo.findByOwner(user).orElseGet(() -> settingsRepo.save(new NotificationSettings(user)));
    }

    @Transactional
    public NotificationSettingsDto settings(AppUser user) {
        return NotificationSettingsDto.from(settingsEntity(user));
    }

    @Transactional
    public NotificationSettingsDto update(AppUser user, NotificationSettingsUpdateRequest req) {
        if (req == null) {
            throw ApiException.badRequest("Некорректный JSON в запросе");
        }
        NotificationSettings s = settingsEntity(user);
        if (req.browserNotificationsEnabled() != null) s.setBrowserNotificationsEnabled(req.browserNotificationsEnabled());
        if (req.shiftRemindersEnabled() != null) s.setShiftRemindersEnabled(req.shiftRemindersEnabled());
        if (req.shiftReminderMinutesBefore() != null) s.setShiftReminderMinutesBefore(req.shiftReminderMinutesBefore());
        if (req.tomorrowDigestEnabled() != null) s.setTomorrowDigestEnabled(req.tomorrowDigestEnabled());
        if (req.tomorrowDigestTime() != null && !req.tomorrowDigestTime().isBlank()) s.setTomorrowDigestTime(parseTime(req.tomorrowDigestTime(), "Время дайджеста должно быть в формате HH:mm"));
        if (req.taskRemindersEnabled() != null) s.setTaskRemindersEnabled(req.taskRemindersEnabled());
        if (req.taskReminderTime() != null && !req.taskReminderTime().isBlank()) s.setTaskReminderTime(parseTime(req.taskReminderTime(), "Время задач должно быть в формате HH:mm"));
        if (req.importantDayRemindersEnabled() != null) s.setImportantDayRemindersEnabled(req.importantDayRemindersEnabled());
        if (req.importantDayDaysBefore() != null) s.setImportantDayDaysBefore(req.importantDayDaysBefore());
        if (req.importantDayReminderTime() != null && !req.importantDayReminderTime().isBlank()) s.setImportantDayReminderTime(parseTime(req.importantDayReminderTime(), "Время важных дней должно быть в формате HH:mm"));
        return NotificationSettingsDto.from(settingsRepo.save(s));
    }

    @Transactional
    public List<NotificationReminderDto> upcoming(AppUser user, LocalDate from, LocalDate to) {
        return upcoming(user, from, to, true);
    }

    @Transactional
    public List<NotificationReminderDto> upcoming(AppUser user, LocalDate from, LocalDate to, boolean includePast) {
        dayEntryService.validateRange(from, to);
        NotificationSettings s = settingsEntity(user);
        List<NotificationReminderDto> out = new ArrayList<>();

        List<DayEntry> days = shiftDaysForDisplayRange(user, from, to);
        List<DayTask> tasks = taskRowsForDisplayRange(user, from, to);
        Map<LocalDate, List<String>> digestParts = new HashMap<>();

        if (s.isShiftRemindersEnabled()) {
            for (DayEntry d : days) {
                ShiftType st = d.getShiftType();
                if (st == null || st.getStartTime() == null || st.effectivePlannedHours() <= 0 || !st.isNotificationsEnabled()) continue;
                int minutesBefore = st.getNotificationMinutesBefore() != null ? st.getNotificationMinutesBefore() : s.getShiftReminderMinutesBefore();

                if (d.hasShiftOccurrenceSnapshot()) {
                    Instant shiftStartInstant = d.getShiftStartInstant();
                    ZonedDateTime displayedStart = userTimeService.inWorkZone(shiftStartInstant, user);
                    LocalDate displayDate = displayedStart.toLocalDate();
                    if (displayDate.isBefore(from) || displayDate.isAfter(to)) continue;
                    Instant remindAtInstant = shiftStartInstant.minusSeconds(minutesBefore * 60L);
                    out.add(reminderAtInstant(
                            user,
                            "shift:" + d.getDate(),
                            "SHIFT",
                            displayDate.toString(),
                            remindAtInstant,
                            "Смена: " + st.getName(),
                            "Начало " + displayedStart.toLocalTime() + " " + displayedStart.getZone().getId()
                                    + ", напоминание за " + minutesBefore + " мин."
                                    + (st.getNotificationMinutesBefore() != null ? " (настройка смены)" : ""),
                            10
                    ));
                    digestParts.computeIfAbsent(displayDate, k -> new ArrayList<>())
                            .add("смена " + st.getName() + " " + displayedStart.toLocalTime());
                    continue;
                }

                // Compatibility fallback for a legacy row that has not yet been
                // frozen by the migration wizard or a timezone change.
                LocalDateTime shiftStart = LocalDateTime.of(d.getDate(), st.getStartTime());
                LocalDateTime remindAt = shiftStart.minusMinutes(minutesBefore);
                out.add(reminder(
                        user,
                        "shift:" + d.getDate(),
                        "SHIFT",
                        d.getDate().toString(),
                        remindAt,
                        "Смена: " + st.getName(),
                        "Начало " + st.getStartTime() + ", напоминание за " + minutesBefore + " мин."
                                + (st.getNotificationMinutesBefore() != null ? " (настройка смены)" : ""),
                        10
                ));
                digestParts.computeIfAbsent(d.getDate(), k -> new ArrayList<>()).add("смена " + st.getName() + " " + st.getStartTime());
            }
        }

        if (s.isTaskRemindersEnabled()) {
            for (DayTask task : tasks) {
                if (task.isDone()) continue;
                LocalDate sourceDate = task.getDueDate() != null ? task.getDueDate() : task.getDate();
                LocalDateTime remindAt;
                String details;
                if (task.isReminderEnabled() && task.getDueDate() != null) {
                    LocalTime dueTime = task.getDueTime() != null ? task.getDueTime() : s.getTaskReminderTime();
                    int before = task.getReminderMinutesBefore() != null ? task.getReminderMinutesBefore() : 0;
                    if (task.getDueInstant() != null && task.getDueTime() != null) {
                        ZonedDateTime dueLocal = userTimeService.inWorkZone(task.getDueInstant(), user);
                        Instant remindAtInstant = task.getDueInstant().minusSeconds(before * 60L);
                        sourceDate = dueLocal.toLocalDate();
                        details = "Срок " + dueLocal.toLocalDate() + " " + dueLocal.toLocalTime()
                                + " " + dueLocal.getZone().getId()
                                + (before > 0 ? ", за " + before + " мин." : "");
                        out.add(reminderAtInstant(
                                user,
                                "task:" + task.getId(),
                                "TASK",
                                sourceDate.toString(),
                                remindAtInstant,
                                "Задача: " + task.getText(),
                                details,
                                30
                        ));
                        digestParts.computeIfAbsent(sourceDate, k -> new ArrayList<>()).add("задача: " + task.getText());
                        continue;
                    }
                    remindAt = LocalDateTime.of(task.getDueDate(), dueTime).minusMinutes(before);
                    details = "Срок " + task.getDueDate() + (task.getDueTime() != null ? " " + task.getDueTime() : "") + (before > 0 ? ", за " + before + " мин." : "");
                } else {
                    remindAt = LocalDateTime.of(task.getDate(), s.getTaskReminderTime());
                    details = task.getDueDate() != null ? "Невыполненная задача, срок " + task.getDueDate() : "Невыполненная задача дня";
                }
                out.add(reminder(
                        user,
                        "task:" + task.getId(),
                        "TASK",
                        sourceDate.toString(),
                        remindAt,
                        "Задача: " + task.getText(),
                        details,
                        30
                ));
                digestParts.computeIfAbsent(sourceDate, k -> new ArrayList<>()).add("задача: " + task.getText());
            }
        }

        if (s.isImportantDayRemindersEnabled()) {
            Map<String, ImportantDayOccurrenceDto> uniqueEvents = new LinkedHashMap<>();
            for (ImportantDayOccurrenceDto item : importantDayService.occurrences(user, from, to)) {
                String occurrenceKey = item.id() + ":" + item.startDate() + ":" + Objects.toString(item.startInstant(), "all-day");
                uniqueEvents.putIfAbsent(occurrenceKey, item);
            }
            for (ImportantDayOccurrenceDto item : uniqueEvents.values()) {
                LocalDate eventDate = LocalDate.parse(item.startDate() == null ? item.date() : item.startDate());
                List<Integer> offsets = item.reminders() == null ? List.of() : item.reminders();
                if (!offsets.isEmpty()) {
                    for (Integer offset : offsets) {
                        int before = Math.max(0, offset == null ? 0 : offset);
                        String reminderId = "important:" + item.id() + ":" + eventDate + ":" + before;
                        if (!item.allDay() && item.startInstant() != null) {
                            Instant remindAt = Instant.parse(item.startInstant()).minusSeconds(before * 60L);
                            out.add(reminderAtInstant(
                                    user,
                                    reminderId,
                                    "IMPORTANT_DAY",
                                    eventDate.toString(),
                                    remindAt,
                                    "Событие: " + item.title(),
                                    before == 0 ? "Напоминание в момент начала" : "За " + before + " мин. до события",
                                    20
                            ));
                        } else {
                            LocalDateTime remindAt = LocalDateTime.of(eventDate, s.getImportantDayReminderTime()).minusMinutes(before);
                            out.add(reminder(
                                    user,
                                    reminderId,
                                    "IMPORTANT_DAY",
                                    eventDate.toString(),
                                    remindAt,
                                    "Важное событие: " + item.title(),
                                    before == 0 ? "Напоминание в день события" : "За " + before + " мин. до события",
                                    20
                            ));
                        }
                    }
                } else {
                    LocalDateTime remindAt = LocalDateTime.of(
                            eventDate.minusDays(s.getImportantDayDaysBefore()),
                            s.getImportantDayReminderTime());
                    out.add(reminder(
                            user,
                            "important:" + item.id() + ":" + eventDate,
                            "IMPORTANT_DAY",
                            eventDate.toString(),
                            remindAt,
                            "Важное событие: " + item.title(),
                            s.getImportantDayDaysBefore() == 0
                                    ? "Напоминание в день события"
                                    : "За " + s.getImportantDayDaysBefore() + " дн. до события",
                            20
                    ));
                }
                digestParts.computeIfAbsent(eventDate, k -> new ArrayList<>()).add("важно: " + item.title());
            }
        }

        if (s.isTomorrowDigestEnabled()) {
            for (Map.Entry<LocalDate, List<String>> e : digestParts.entrySet()) {
                LocalDate sourceDate = e.getKey();
                LocalDateTime remindAt = LocalDateTime.of(sourceDate.minusDays(1), s.getTomorrowDigestTime());
                out.add(reminder(
                        user,
                        "digest:" + sourceDate,
                        "TOMORROW_DIGEST",
                        sourceDate.toString(),
                        remindAt,
                        "Завтра: " + sourceDate,
                        String.join("; ", e.getValue()),
                        40
                ));
            }
        }

        if (!includePast) {
            Instant now = userTimeService.nowInstant();
            out.removeIf(reminder -> reminderInstant(reminder, user).isBefore(now));
        }

        out.sort(Comparator.comparing((NotificationReminderDto reminder) -> reminderInstant(reminder, user))
                .thenComparing(NotificationReminderDto::priority));
        return out;
    }

    private List<DayTask> taskRowsForDisplayRange(AppUser user, LocalDate from, LocalDate to) {
        Map<Long, DayTask> unique = new LinkedHashMap<>();
        for (DayTask task : taskRepository.findByOwnerAndDateBetweenOrderByDateAscCreatedAtAscIdAsc(user, from, to)) {
            unique.put(task.getId(), task);
        }
        for (DayTask task : taskRepository.findByOwnerAndDueDateBetweenOrderByDueDateAscDueTimeAscCreatedAtAscIdAsc(user, from, to)) {
            unique.putIfAbsent(task.getId(), task);
        }
        return new ArrayList<>(unique.values());
    }

    private List<DayEntry> shiftDaysForDisplayRange(AppUser user, LocalDate from, LocalDate to) {
        ZoneId zone = userTimeService.workZone(user);
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
        Map<Long, DayEntry> unique = new LinkedHashMap<>();

        for (DayEntry entry : dayEntryRepository
                .findByOwnerAndShiftStartInstantLessThanAndShiftEndInstantGreaterThanOrderByShiftStartInstantAsc(
                        user, end, start)) {
            unique.put(entry.getId(), entry);
        }
        // Legacy local rows have no absolute index. A two-day buffer is enough
        // for all civil IANA offsets and keeps the compatibility query bounded.
        for (DayEntry entry : dayEntryRepository.findByOwnerAndDateBetweenOrderByDateAsc(
                user, from.minusDays(2), to.plusDays(2))) {
            if (entry.getShiftType() != null && !entry.hasShiftOccurrenceSnapshot()) {
                unique.putIfAbsent(entry.getId(), entry);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private NotificationReminderDto reminderAtInstant(AppUser user,
                                                        String id,
                                                        String type,
                                                        String sourceDate,
                                                        Instant remindAtInstant,
                                                        String title,
                                                        String details,
                                                        int priority) {
        ZonedDateTime local = userTimeService.inWorkZone(remindAtInstant, user);
        String localValue = local.toLocalDateTime().toString();
        String timezone = local.getZone().getId();
        return new NotificationReminderDto(
                id, type, sourceDate, localValue, title, details, priority,
                remindAtInstant.toString(), timezone, localValue, timezone
        );
    }

    private NotificationReminderDto reminder(AppUser user,
                                               String id,
                                               String type,
                                               String sourceDate,
                                               LocalDateTime remindAt,
                                               String title,
                                               String details,
                                               int priority) {
        Instant instant = userTimeService.toWorkInstant(user, remindAt);
        return new NotificationReminderDto(
                id,
                type,
                sourceDate,
                remindAt.toString(),
                title,
                details,
                priority,
                instant.toString(),
                userTimeService.workZone(user).getId(),
                userTimeService.inDisplayZone(instant, user).toLocalDateTime().toString(),
                userTimeService.displayZone(user).getId()
        );
    }

    private Instant reminderInstant(NotificationReminderDto reminder, AppUser user) {
        if (reminder.remindAtInstant() != null && !reminder.remindAtInstant().isBlank()) {
            try {
                return Instant.parse(reminder.remindAtInstant());
            } catch (RuntimeException ignored) {
                // Compatibility fallback for old or partially migrated clients.
            }
        }
        return userTimeService.toWorkInstant(user, LocalDateTime.parse(reminder.remindAt()));
    }

    @Transactional
    public List<NotificationReminderDto> tomorrow(AppUser user) {
        LocalDate tomorrow = userTimeService.today(user).plusDays(1);
        return upcoming(user, tomorrow, tomorrow, true);
    }

    public LocalDate parseDate(String value, String message) {
        return dayEntryService.parseDate(value, message);
    }

    private LocalTime parseTime(String value, String message) {
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException | NullPointerException e) {
            throw ApiException.badRequest(message);
        }
    }
}
