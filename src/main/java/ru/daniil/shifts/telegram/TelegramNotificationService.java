package ru.daniil.shifts.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.daniil.shifts.dto.Dtos.NotificationReminderDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.TelegramLink;
import ru.daniil.shifts.model.TelegramNotificationDelivery;
import ru.daniil.shifts.repo.TelegramLinkRepository;
import ru.daniil.shifts.repo.TelegramNotificationDeliveryRepository;
import ru.daniil.shifts.service.NotificationService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * Доставка уже рассчитанных backend-напоминаний в Telegram.
 * Сами правила времени живут в NotificationService, а здесь только доставка и защита от дублей.
 */
@Service
public class TelegramNotificationService {
    private static final Logger log = LoggerFactory.getLogger(TelegramNotificationService.class);
    private static final DateTimeFormatter RU_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final TelegramLinkService linkService;
    private final TelegramLinkRepository linkRepository;
    private final TelegramNotificationDeliveryRepository deliveryRepository;
    private final NotificationService notificationService;
    private final TelegramBotService botService;

    @Value("${dutylog.telegram.notifications-enabled:true}")
    private boolean telegramNotificationsEnabled;

    @Value("${dutylog.telegram.notification-lookback-minutes:10}")
    private int lookbackMinutes;

    @Value("${dutylog.telegram.notification-lookahead-minutes:1}")
    private int lookaheadMinutes;

    public TelegramNotificationService(TelegramLinkService linkService,
                                       TelegramLinkRepository linkRepository,
                                       TelegramNotificationDeliveryRepository deliveryRepository,
                                       NotificationService notificationService,
                                       TelegramBotService botService) {
        this.linkService = linkService;
        this.linkRepository = linkRepository;
        this.deliveryRepository = deliveryRepository;
        this.notificationService = notificationService;
        this.botService = botService;
    }

    @Scheduled(fixedDelayString = "${dutylog.telegram.notification-scan-delay-ms:60000}", initialDelay = 15000)
    public void scanAndSendDueNotifications() {
        if (!telegramNotificationsEnabled || !linkService.isConfigured()) return;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dueFrom = now.minusMinutes(Math.max(1, lookbackMinutes));
        LocalDateTime dueTo = now.plusMinutes(Math.max(0, lookaheadMinutes));

        List<TelegramLink> links = linkRepository.findByEnabledTrueAndNotificationsEnabledTrue();
        for (TelegramLink link : links) {
            try {
                sendDueForLink(link, dueFrom, dueTo);
            } catch (Exception e) {
                log.warn("Telegram notification scan failed for link {}: {}", link.getId(), e.getMessage());
            }
        }
    }

    private void sendDueForLink(TelegramLink link, LocalDateTime dueFrom, LocalDateTime dueTo) {
        AppUser user = link.getOwner();
        LocalDate fromDate = dueFrom.toLocalDate().minusDays(1);
        LocalDate toDate = dueTo.toLocalDate().plusDays(14);

        List<NotificationReminderDto> reminders = notificationService.upcoming(user, fromDate, toDate, true);
        for (NotificationReminderDto reminder : reminders) {
            LocalDateTime remindAt = parseDateTime(reminder.remindAt());
            if (remindAt == null || remindAt.isBefore(dueFrom) || remindAt.isAfter(dueTo)) continue;
            if (deliveryRepository.existsByOwnerAndReminderIdAndRemindAt(user, reminder.id(), remindAt)) continue;

            boolean sent = botService.sendMessage(link.getTelegramChatId(), formatMessage(reminder));
            if (sent) {
                deliveryRepository.save(new TelegramNotificationDelivery(user, link, reminder.id(), reminder.type(), remindAt));
            }
        }
    }

    private String formatMessage(NotificationReminderDto reminder) {
        String date = formatSourceDate(reminder.sourceDate());
        String title = reminder.title() == null ? "Напоминание" : reminder.title();
        String details = reminder.details() == null ? "" : reminder.details();
        return switch (reminder.type()) {
            case "SHIFT" -> String.join("\n",
                    "⏰ Смена скоро начнётся",
                    "",
                    title,
                    details,
                    "Дата: " + date
            );
            case "TASK" -> String.join("\n",
                    "📝 Напоминание о задаче",
                    "",
                    title,
                    details,
                    "Дата: " + date
            );
            case "IMPORTANT_DAY" -> String.join("\n",
                    "⭐ Важный день",
                    "",
                    title,
                    details,
                    "Дата события: " + date
            );
            case "TOMORROW_DIGEST" -> String.join("\n",
                    "🌙 Дайджест на завтра",
                    "",
                    title,
                    details.isBlank() ? "Планов пока нет." : details
            );
            default -> String.join("\n", "🔔 DutyLog", "", title, details, "Дата: " + date);
        };
    }

    private LocalDateTime parseDateTime(String value) {
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException | NullPointerException e) {
            return null;
        }
    }

    private String formatSourceDate(String value) {
        try {
            return LocalDate.parse(value).format(RU_DATE);
        } catch (Exception e) {
            return value == null ? "—" : value;
        }
    }
}
