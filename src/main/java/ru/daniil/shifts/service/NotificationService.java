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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public NotificationService(NotificationSettingsRepository settingsRepo,
                               DayEntryRepository dayEntryRepository,
                               DayTaskRepository taskRepository,
                               ImportantDayService importantDayService,
                               DayEntryService dayEntryService) {
        this.settingsRepo = settingsRepo;
        this.dayEntryRepository = dayEntryRepository;
        this.taskRepository = taskRepository;
        this.importantDayService = importantDayService;
        this.dayEntryService = dayEntryService;
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

        List<DayEntry> days = dayEntryRepository.findByOwnerAndDateBetweenOrderByDateAsc(user, from, to);
        List<DayTask> tasks = taskRepository.findByOwnerAndDateBetweenOrderByDateAscCreatedAtAscIdAsc(user, from, to);
        Map<LocalDate, List<String>> digestParts = new HashMap<>();

        if (s.isShiftRemindersEnabled()) {
            for (DayEntry d : days) {
                ShiftType st = d.getShiftType();
                if (st == null || st.getStartTime() == null || st.effectivePlannedHours() <= 0 || !st.isNotificationsEnabled()) continue;
                int minutesBefore = st.getNotificationMinutesBefore() != null ? st.getNotificationMinutesBefore() : s.getShiftReminderMinutesBefore();
                LocalDateTime shiftStart = LocalDateTime.of(d.getDate(), st.getStartTime());
                LocalDateTime remindAt = shiftStart.minusMinutes(minutesBefore);
                out.add(new NotificationReminderDto(
                        "shift:" + d.getDate(),
                        "SHIFT",
                        d.getDate().toString(),
                        remindAt.toString(),
                        "Смена: " + st.getName(),
                        "Начало " + st.getStartTime() + ", напоминание за " + minutesBefore + " мин." + (st.getNotificationMinutesBefore() != null ? " (настройка смены)" : ""),
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
                    remindAt = LocalDateTime.of(task.getDueDate(), dueTime).minusMinutes(before);
                    details = "Срок " + task.getDueDate() + (task.getDueTime() != null ? " " + task.getDueTime() : "") + (before > 0 ? ", за " + before + " мин." : "");
                } else {
                    remindAt = LocalDateTime.of(task.getDate(), s.getTaskReminderTime());
                    details = task.getDueDate() != null ? "Невыполненная задача, срок " + task.getDueDate() : "Невыполненная задача дня";
                }
                out.add(new NotificationReminderDto(
                        "task:" + task.getId(),
                        "TASK",
                        sourceDate.toString(),
                        remindAt.toString(),
                        "Задача: " + task.getText(),
                        details,
                        30
                ));
                digestParts.computeIfAbsent(sourceDate, k -> new ArrayList<>()).add("задача: " + task.getText());
            }
        }

        if (s.isImportantDayRemindersEnabled()) {
            for (ImportantDayOccurrenceDto item : importantDayService.occurrences(user, from, to)) {
                LocalDate eventDate = LocalDate.parse(item.date());
                LocalDateTime remindAt = LocalDateTime.of(eventDate.minusDays(s.getImportantDayDaysBefore()), s.getImportantDayReminderTime());
                out.add(new NotificationReminderDto(
                        "important:" + item.id() + ":" + item.date(),
                        "IMPORTANT_DAY",
                        item.date(),
                        remindAt.toString(),
                        "Важный день: " + item.title(),
                        s.getImportantDayDaysBefore() == 0 ? "Напоминание в день события" : "За " + s.getImportantDayDaysBefore() + " дн. до события",
                        20
                ));
                digestParts.computeIfAbsent(eventDate, k -> new ArrayList<>()).add("важно: " + item.title());
            }
        }

        if (s.isTomorrowDigestEnabled()) {
            for (Map.Entry<LocalDate, List<String>> e : digestParts.entrySet()) {
                LocalDate sourceDate = e.getKey();
                LocalDateTime remindAt = LocalDateTime.of(sourceDate.minusDays(1), s.getTomorrowDigestTime());
                out.add(new NotificationReminderDto(
                        "digest:" + sourceDate,
                        "TOMORROW_DIGEST",
                        sourceDate.toString(),
                        remindAt.toString(),
                        "Завтра: " + sourceDate,
                        String.join("; ", e.getValue()),
                        40
                ));
            }
        }

        if (!includePast) {
            LocalDateTime now = LocalDateTime.now();
            out.removeIf(r -> {
                try {
                    return LocalDateTime.parse(r.remindAt()).isBefore(now);
                } catch (RuntimeException ex) {
                    return false;
                }
            });
        }

        out.sort(Comparator.comparing(NotificationReminderDto::remindAt).thenComparing(NotificationReminderDto::priority));
        return out;
    }

    @Transactional
    public List<NotificationReminderDto> tomorrow(AppUser user) {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
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
