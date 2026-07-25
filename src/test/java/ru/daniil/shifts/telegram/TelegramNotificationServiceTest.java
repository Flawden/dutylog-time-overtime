package ru.daniil.shifts.telegram;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ru.daniil.shifts.dto.Dtos.NotificationReminderDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.TelegramLink;
import ru.daniil.shifts.model.TelegramNotificationDelivery;
import ru.daniil.shifts.repo.TelegramLinkRepository;
import ru.daniil.shifts.repo.TelegramNotificationDeliveryRepository;
import ru.daniil.shifts.service.NotificationService;
import ru.daniil.shifts.service.UserTimeService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Delivery scheduler contract: due window, deduplication, retry semantics and message formats. */
@ExtendWith(MockitoExtension.class)
class TelegramNotificationServiceTest {

    @Mock TelegramLinkService linkService;
    @Mock TelegramLinkRepository linkRepository;
    @Mock TelegramNotificationDeliveryRepository deliveryRepository;
    @Mock NotificationService notificationService;
    @Mock TelegramBotService botService;
    @Mock UserTimeService userTimeService;

    TelegramNotificationService service;
    AppUser user;
    TelegramLink link;

    @BeforeEach
    void setUp() {
        service = new TelegramNotificationService(
                linkService, linkRepository, deliveryRepository, notificationService, botService, userTimeService);
        ReflectionTestUtils.setField(service, "telegramNotificationsEnabled", true);
        ReflectionTestUtils.setField(service, "lookbackMinutes", 10);
        ReflectionTestUtils.setField(service, "lookaheadMinutes", 1);
        user = new AppUser("telegram-notification-owner", "{noop}x");
        user.setWorkTimezone("UTC");
        user.setDisplayTimezone("UTC");
        link = new TelegramLink(user, 700L);
        lenient().when(userTimeService.inWorkZone(any(Instant.class), any(AppUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, Instant.class).atZone(ZoneOffset.UTC));
        lenient().when(userTimeService.toWorkInstant(any(AppUser.class), any(LocalDateTime.class)))
                .thenAnswer(invocation -> invocation.getArgument(1, LocalDateTime.class).toInstant(ZoneOffset.UTC));
    }

    @Test
    void disabledOrUnconfiguredSchedulerDoesNothing() {
        ReflectionTestUtils.setField(service, "telegramNotificationsEnabled", false);
        service.scanAndSendDueNotifications();
        verifyNoInteractions(linkService, linkRepository, deliveryRepository, notificationService, botService);

        ReflectionTestUtils.setField(service, "telegramNotificationsEnabled", true);
        when(linkService.isConfigured()).thenReturn(false);
        service.scanAndSendDueNotifications();
        verifyNoInteractions(linkRepository, deliveryRepository, notificationService, botService);
    }

    @Test
    void dueReminderIsSentAndPersistedExactlyOnce() {
        LocalDateTime remindAt = LocalDateTime.of(2026, 7, 17, 12, 0);
        when(userTimeService.nowInstant()).thenReturn(remindAt.toInstant(ZoneOffset.UTC));
        NotificationReminderDto reminder = reminder("task-1", "TASK", remindAt, "Проверить отчёт", "до 18:00", "2026-07-17");
        when(linkService.isConfigured()).thenReturn(true);
        when(linkRepository.findByEnabledTrueAndNotificationsEnabledTrue()).thenReturn(List.of(link));
        when(notificationService.upcoming(eq(user), any(), any(), eq(true))).thenReturn(List.of(reminder));
        when(deliveryRepository.existsByOwnerAndReminderIdAndRemindAtInstant(user, "task-1", remindAt.toInstant(ZoneOffset.UTC))).thenReturn(false);
        when(botService.sendMessage(eq(700L), contains("Проверить отчёт"))).thenReturn(true);

        service.scanAndSendDueNotifications();

        ArgumentCaptor<TelegramNotificationDelivery> saved = ArgumentCaptor.forClass(TelegramNotificationDelivery.class);
        verify(deliveryRepository).save(saved.capture());
        assertSame(user, saved.getValue().getOwner());
        assertSame(link, saved.getValue().getTelegramLink());
        assertEquals("task-1", saved.getValue().getReminderId());
        assertEquals("TASK", saved.getValue().getReminderType());
        assertEquals(remindAt, saved.getValue().getRemindAt());
        assertEquals(remindAt.toInstant(ZoneOffset.UTC), saved.getValue().getRemindAtInstant());
    }

    @Test
    void duplicateMalformedAndOutOfWindowRemindersAreSkipped() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 17, 12, 0);
        when(userTimeService.nowInstant()).thenReturn(now.toInstant(ZoneOffset.UTC));
        NotificationReminderDto duplicate = reminder("dup", "SHIFT", now, "Смена", "08:00", "2026-07-17");
        NotificationReminderDto tooOld = reminder("old", "TASK", now.minusHours(2), "Старая", "", "2026-07-17");
        NotificationReminderDto tooFuture = reminder("future", "TASK", now.plusHours(2), "Будущая", "", "2026-07-17");
        NotificationReminderDto malformed = new NotificationReminderDto("bad", "TASK", null, "not-a-date", "Bad", "", 1);
        when(linkService.isConfigured()).thenReturn(true);
        when(linkRepository.findByEnabledTrueAndNotificationsEnabledTrue()).thenReturn(List.of(link));
        when(notificationService.upcoming(eq(user), any(), any(), eq(true)))
                .thenReturn(List.of(duplicate, tooOld, tooFuture, malformed));
        when(deliveryRepository.existsByOwnerAndReminderIdAndRemindAtInstant(user, "dup", now.toInstant(ZoneOffset.UTC))).thenReturn(true);

        service.scanAndSendDueNotifications();

        verifyNoInteractions(botService);
        verify(deliveryRepository, never()).save(any());
    }

    @Test
    void legacyLocalDeliveryWithoutInstantStillPreventsDuplicateSend() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 17, 12, 0);
        when(userTimeService.nowInstant()).thenReturn(now.toInstant(ZoneOffset.UTC));
        NotificationReminderDto legacy = reminder("legacy", "TASK", now, "Старая доставка", "", "2026-07-17");
        when(linkService.isConfigured()).thenReturn(true);
        when(linkRepository.findByEnabledTrueAndNotificationsEnabledTrue()).thenReturn(List.of(link));
        when(notificationService.upcoming(eq(user), any(), any(), eq(true))).thenReturn(List.of(legacy));
        when(deliveryRepository.existsByOwnerAndReminderIdAndRemindAtInstant(
                user, "legacy", now.toInstant(ZoneOffset.UTC))).thenReturn(false);
        when(deliveryRepository.existsByOwnerAndReminderIdAndRemindAtAndRemindAtInstantIsNull(
                user, "legacy", now)).thenReturn(true);

        service.scanAndSendDueNotifications();

        verifyNoInteractions(botService);
        verify(deliveryRepository, never()).save(any());
    }

    @Test
    void failedTelegramSendIsRetriedLaterInsteadOfMarkedDelivered() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 17, 12, 0);
        when(userTimeService.nowInstant()).thenReturn(now.toInstant(ZoneOffset.UTC));
        NotificationReminderDto reminder = reminder("retry", "IMPORTANT_DAY", now, "День рождения", "", "2026-07-17");
        when(linkService.isConfigured()).thenReturn(true);
        when(linkRepository.findByEnabledTrueAndNotificationsEnabledTrue()).thenReturn(List.of(link));
        when(notificationService.upcoming(eq(user), any(), any(), eq(true))).thenReturn(List.of(reminder));
        when(botService.sendMessage(eq(700L), anyString())).thenReturn(false);

        service.scanAndSendDueNotifications();

        verify(botService).sendMessage(eq(700L), contains("День рождения"));
        verify(deliveryRepository, never()).save(any());
    }

    @Test
    void oneBrokenLinkDoesNotBlockOtherUsers() {
        AppUser brokenUser = new AppUser("telegram-broken", "{noop}x");
        TelegramLink brokenLink = new TelegramLink(brokenUser, 1L);
        LocalDateTime now = LocalDateTime.of(2026, 7, 17, 12, 0);
        when(userTimeService.nowInstant()).thenReturn(now.toInstant(ZoneOffset.UTC));
        NotificationReminderDto reminder = reminder("ok", "TOMORROW_DIGEST", now, "Завтра", "План", "2026-07-18");
        when(linkService.isConfigured()).thenReturn(true);
        when(linkRepository.findByEnabledTrueAndNotificationsEnabledTrue()).thenReturn(List.of(brokenLink, link));
        when(notificationService.upcoming(eq(brokenUser), any(), any(), eq(true)))
                .thenThrow(new IllegalStateException("broken user data"));
        when(notificationService.upcoming(eq(user), any(), any(), eq(true))).thenReturn(List.of(reminder));
        when(botService.sendMessage(eq(700L), anyString())).thenReturn(true);

        service.scanAndSendDueNotifications();

        verify(botService).sendMessage(eq(700L), contains("Дайджест на завтра"));
        verify(deliveryRepository).save(any(TelegramNotificationDelivery.class));
    }

    @Test
    void everyReminderTypeHasStableHumanReadableMessage() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 17, 12, 0);
        assertMessage("SHIFT", "⏰ Смена скоро начнётся", "Дата: 17.07.2026", now);
        assertMessage("TASK", "📝 Напоминание о задаче", "Дата: 17.07.2026", now);
        assertMessage("IMPORTANT_DAY", "⭐ Важный день", "Дата события: 17.07.2026", now);
        assertMessage("TOMORROW_DIGEST", "🌙 Дайджест на завтра", "Планов пока нет.", now);
        assertMessage("OTHER", "🔔 DutyLog", "Дата: 17.07.2026", now);

        NotificationReminderDto malformedDate = reminder("x", "TASK", now, null, null, "not-a-date");
        String malformedMessage = ReflectionTestUtils.invokeMethod(service, "formatMessage", malformedDate);
        assertNotNull(malformedMessage);
        assertTrue(malformedMessage.contains("not-a-date"));

        NotificationReminderDto nullDate = reminder("x2", "TASK", now, null, null, null);
        String nullMessage = ReflectionTestUtils.invokeMethod(service, "formatMessage", nullDate);
        assertNotNull(nullMessage);
        assertTrue(nullMessage.contains("Дата: —"));
    }

    @Test
    void negativeWindowSettingsAreClampedSafely() {
        ReflectionTestUtils.setField(service, "lookbackMinutes", -50);
        ReflectionTestUtils.setField(service, "lookaheadMinutes", -50);
        when(userTimeService.nowInstant()).thenReturn(Instant.parse("2026-07-17T12:00:00Z"));
        when(linkService.isConfigured()).thenReturn(true);
        when(linkRepository.findByEnabledTrueAndNotificationsEnabledTrue()).thenReturn(List.of(link));
        when(notificationService.upcoming(eq(user), any(), any(), eq(true))).thenReturn(List.of());

        service.scanAndSendDueNotifications();

        verify(notificationService).upcoming(eq(user), any(), any(), eq(true));
        verifyNoInteractions(botService);
    }

    private NotificationReminderDto reminder(String id,
                                             String type,
                                             LocalDateTime remindAt,
                                             String title,
                                             String details,
                                             String sourceDate) {
        return new NotificationReminderDto(
                id, type, sourceDate, remindAt.toString(), title, details, 1,
                remindAt.toInstant(ZoneOffset.UTC).toString());
    }

    private void assertMessage(String type, String expectedHeader, String expectedTail, LocalDateTime now) {
        NotificationReminderDto reminder = reminder("id-" + type, type, now, "Заголовок", "", "2026-07-17");
        String message = ReflectionTestUtils.invokeMethod(service, "formatMessage", reminder);
        assertNotNull(message);
        assertTrue(message.contains(expectedHeader), message);
        assertTrue(message.contains(expectedTail), message);
    }
}
