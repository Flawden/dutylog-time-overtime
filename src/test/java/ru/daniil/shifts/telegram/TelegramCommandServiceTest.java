package ru.daniil.shifts.telegram;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.daniil.shifts.dto.Dtos.ImportantDayOccurrenceDto;
import ru.daniil.shifts.dto.Dtos.OvertimeAccountDto;
import ru.daniil.shifts.dto.Dtos.OvertimeCreditCreateRequest;
import ru.daniil.shifts.dto.Dtos.OvertimeUsageCreateRequest;
import ru.daniil.shifts.dto.Dtos.PageDto;
import ru.daniil.shifts.dto.Dtos.TaskCreateRequest;
import ru.daniil.shifts.dto.Dtos.TaskDto;
import ru.daniil.shifts.dto.Dtos.TaskUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.RepeatMode;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.model.TaskPriority;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.service.ImportantDayService;
import ru.daniil.shifts.service.OvertimeService;
import ru.daniil.shifts.service.TaskService;
import ru.daniil.shifts.service.UserTimeService;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Command grammar regression suite for the Telegram bot. */
@ExtendWith(MockitoExtension.class)
class TelegramCommandServiceTest {

    @Mock DayEntryRepository dayEntries;
    @Mock TaskService taskService;
    @Mock ImportantDayService importantDayService;
    @Mock OvertimeService overtimeService;
    @Mock UserTimeService userTimeService;

    TelegramCommandService service;
    AppUser user;

    @BeforeEach
    void setUp() {
        user = new AppUser("telegram-command-owner", "{noop}x");
        lenient().when(userTimeService.today(user)).thenReturn(LocalDate.now());
        service = new TelegramCommandService(dayEntries, taskService, importantDayService, overtimeService, userTimeService);
    }

    @Test
    void helpAliasesBotSuffixAndUnknownCommandAreStable() {
        assertTrue(service.handle(user, null).contains("DutyLog: Time & Overtime"));
        assertTrue(service.handle(user, "   ").contains("/today"));
        assertTrue(service.handle(user, "/help@DutyLogBot").contains("Быстрые действия"));
        assertTrue(service.handle(user, "/start").contains("/balance"));
        assertEquals("Не понял команду. Напиши /help — покажу, что умею.",
                service.handle(user, "/totally-unknown command"));
    }

    @Test
    void quickActionKeyboardLabelsDispatchWithoutSlashCommands() {
        LocalDate today = LocalDate.of(2026, 7, 23);
        when(userTimeService.today(user)).thenReturn(today);
        when(dayEntries.findByOwnerAndDate(eq(user), any(LocalDate.class))).thenReturn(Optional.empty());
        when(taskService.listDay(eq(user), anyString())).thenReturn(List.of());
        when(importantDayService.occurrences(eq(user), any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of());
        when(overtimeService.account(user)).thenReturn(account(12, 4, 8));
        when(taskService.listBoard(eq(user), eq("open"), eq("all"), eq("all"), eq(""), isNull(), isNull(), eq(0), eq(50)))
                .thenReturn(PageDto.of(List.of(), 0, 50, 0));

        assertTrue(service.handle(user, "Сегодня").startsWith("Сегодня, 23.07.2026"));
        assertTrue(service.handle(user, "Завтра").startsWith("Завтра, 24.07.2026"));
        assertTrue(service.handle(user, "Неделя").startsWith("Ближайшие 7 дней"));
        assertEquals("Открытых задач нет. Красота.", service.handle(user, "Задачи"));
        assertTrue(service.handle(user, "Баланс").contains("Остаток: 8 ч"));
        assertTrue(service.handle(user, "Помощь").contains("Основные команды доступны кнопками"));
    }

    @Test
    void createTaskParsesTomorrowAndDelegatesWithoutInventingReminder() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        TaskDto created = task(12L, tomorrow, "Купить молоко", false, false);
        when(taskService.create(eq(user), any(TaskCreateRequest.class))).thenReturn(created);

        String answer = service.handle(user, "/task завтра Купить молоко");

        ArgumentCaptor<TaskCreateRequest> request = ArgumentCaptor.forClass(TaskCreateRequest.class);
        verify(taskService).create(eq(user), request.capture());
        assertEquals(tomorrow.toString(), request.getValue().date());
        assertEquals("Купить молоко", request.getValue().text());
        assertFalse(Boolean.TRUE.equals(request.getValue().reminderEnabled()));
        assertNull(request.getValue().reminderMinutesBefore());
        assertTrue(answer.contains("#12 Купить молоко"));
    }

    @Test
    void taskCommandValidatesMissingTextAndMalformedExplicitDate() {
        assertBadRequest(() -> service.handle(user, "/task"), "Напиши текст задачи");
        assertBadRequest(() -> service.handle(user, "/task завтра"), "После даты нужен текст");
        assertBadRequest(() -> service.handle(user, "/task 31.02 Невозможная дата"), "Дата должна быть");
    }

    @Test
    void doneCommandValidatesIdAndMarksTaskCompleted() {
        TaskDto completed = task(55L, LocalDate.now(), "Закрываем", true, false);
        when(taskService.update(eq(user), eq(55L), any(TaskUpdateRequest.class))).thenReturn(completed);

        assertBadRequest(() -> service.handle(user, "/done"), "Укажи id задачи");
        assertBadRequest(() -> service.handle(user, "/done abc"), "Укажи id задачи");
        String answer = service.handle(user, "/done #55");

        ArgumentCaptor<TaskUpdateRequest> request = ArgumentCaptor.forClass(TaskUpdateRequest.class);
        verify(taskService).update(eq(user), eq(55L), request.capture());
        assertEquals(Boolean.TRUE, request.getValue().done());
        assertTrue(answer.contains("Закрыл задачу #55"));
    }

    @Test
    void manualOvertimeAcceptsDecimalCommaAndFormatsBalance() {
        when(overtimeService.createCredit(eq(user), any(OvertimeCreditCreateRequest.class)))
                .thenReturn(account(12.5, 2.5, 10.0));

        String answer = service.handle(user, "/ppr 2,5 ППР после смены");

        ArgumentCaptor<OvertimeCreditCreateRequest> request = ArgumentCaptor.forClass(OvertimeCreditCreateRequest.class);
        verify(overtimeService).createCredit(eq(user), request.capture());
        assertEquals(2.5, request.getValue().hours(), 0.0001);
        assertEquals("ППР после смены", request.getValue().reason());
        assertEquals("Telegram: ручное начисление", request.getValue().timeRange());
        assertNull(request.getValue().startDateTime());
        assertTrue(answer.contains("Начислил 2.5 ч"));
        assertTrue(answer.contains("Остаток: 10 ч"));
    }

    @Test
    void intervalOvertimeSupportsDateOvernightBreakPlanAndReason() {
        when(overtimeService.createCredit(eq(user), any(OvertimeCreditCreateRequest.class)))
                .thenReturn(account(4.0, 0.0, 4.0));
        int year = LocalDate.now().getYear();

        String answer = service.handle(user, "/ппр 10.07 20-08 обед60 план8 Ночной ППР");

        ArgumentCaptor<OvertimeCreditCreateRequest> request = ArgumentCaptor.forClass(OvertimeCreditCreateRequest.class);
        verify(overtimeService).createCredit(eq(user), request.capture());
        OvertimeCreditCreateRequest value = request.getValue();
        assertEquals(year + "-07-10", value.date());
        assertEquals(year + "-07-10T20:00", value.startDateTime());
        assertEquals(year + "-07-11T08:00", value.endDateTime());
        assertEquals(60, value.breakMinutes());
        assertEquals(8.0, value.plannedHours(), 0.0001);
        assertEquals("Ночной ППР", value.reason());
        assertNull(value.hours());
        assertTrue(answer.contains("за 20-08"));
    }

    @Test
    void overtimeCommandRejectsBadIntervalsHoursAndPlans() {
        assertBadRequest(() -> service.handle(user, "/ppr"), "Формат");
        assertBadRequest(() -> service.handle(user, "/ppr nonsense"), "Не понял интервал");
        assertBadRequest(() -> service.handle(user, "/ppr 0 причина"), "больше 0");
        assertBadRequest(() -> service.handle(user, "/ppr 101 причина"), "от 0 до 100");
        assertBadRequest(() -> service.handle(user, "/ppr 25-08 причина"), "Время должно быть");
        assertBadRequest(() -> service.handle(user, "/ppr 17-08 план101 причина"), "от 0 до 100");
    }

    @Test
    void timeOffParsesDateHoursAndDefaultReason() {
        when(overtimeService.createUsage(eq(user), any(OvertimeUsageCreateRequest.class)))
                .thenReturn(account(16, 8, 8));

        String answer = service.handle(user, "/отгул завтра 8");

        ArgumentCaptor<OvertimeUsageCreateRequest> request = ArgumentCaptor.forClass(OvertimeUsageCreateRequest.class);
        verify(overtimeService).createUsage(eq(user), request.capture());
        assertEquals(LocalDate.now().plusDays(1).toString(), request.getValue().date());
        assertEquals(8.0, request.getValue().hours(), 0.0001);
        assertEquals("Списано из Telegram", request.getValue().reason());
        assertTrue(answer.contains("Списал 8 ч"));
        assertBadRequest(() -> service.handle(user, "/timeoff"), "Формат");
        assertBadRequest(() -> service.handle(user, "/timeoff zero"), "числом");
    }

    @Test
    void taskAndBalanceSummariesCoverEmptyLongAndFractionalStates() {
        when(taskService.listBoard(eq(user), eq("open"), eq("all"), eq("all"), eq(""), isNull(), isNull(), eq(0), eq(50)))
                .thenReturn(PageDto.of(List.of(), 0, 50, 0));
        assertEquals("Открытых задач нет. Красота.", service.handle(user, "/tasks"));

        List<TaskDto> many = new ArrayList<>();
        for (long i = 1; i <= 13; i++) many.add(task(i, LocalDate.now(), "Задача " + i, false, i == 1));
        when(taskService.listBoard(eq(user), eq("open"), eq("all"), eq("all"), eq(""), isNull(), isNull(), eq(0), eq(50)))
                .thenReturn(PageDto.of(many, 0, 50, 13));
        String tasks = service.handle(user, "/задачи");
        assertTrue(tasks.contains("Открытые задачи: 13"));
        assertTrue(tasks.contains("‼ Задача 1"));
        assertTrue(tasks.contains("…и ещё 1"));

        when(overtimeService.account(user)).thenReturn(account(12.25, 3.5, 8.75));
        String balance = service.handle(user, "/баланс");
        assertTrue(balance.contains("Начислено: 12.25 ч"));
        assertTrue(balance.contains("Списано: 3.5 ч"));
        assertTrue(balance.contains("Остаток: 8.75 ч"));
    }

    @Test
    void todayAndWeekSummariesCombineShiftTasksImportantDaysAndBalance() {
        LocalDate today = LocalDate.now();
        ShiftType night = new ShiftType(user, "Ночная", 11, "#123456", false,
                LocalTime.of(20, 0), LocalTime.of(8, 0), 60, 11.0);
        DayEntry todayEntry = new DayEntry(user, today);
        todayEntry.setShiftType(night);
        when(dayEntries.findByOwnerAndDate(user, today)).thenReturn(Optional.of(todayEntry));
        when(taskService.listDay(user, today.toString())).thenReturn(List.of(
                task(1L, today, "Открытая", false, false),
                task(2L, today, "Просроченная", false, true),
                task(3L, today, "Готовая", true, false)
        ));
        when(importantDayService.occurrences(user, today, today)).thenReturn(List.of(
                new ImportantDayOccurrenceDto(1L, today.toString(), "День проекта", RepeatMode.YEARLY, "#ffffff")
        ));
        when(overtimeService.account(user)).thenReturn(account(10, 2, 8));

        String summary = service.handle(user, "/сегодня");
        assertTrue(summary.contains("Смена: Ночная 20:00–08:00 · план 11 ч"));
        assertTrue(summary.contains("Задачи: 2 открыто, просрочено 1"));
        assertTrue(summary.contains("Важные дни: День проекта"));
        assertTrue(summary.contains("Баланс переработок: 8 ч"));

        LocalDate tomorrow = today.plusDays(1);
        when(dayEntries.findByOwnerAndDate(user, tomorrow)).thenReturn(Optional.empty());
        when(taskService.listDay(user, tomorrow.toString())).thenReturn(List.of());
        when(importantDayService.occurrences(user, tomorrow, tomorrow)).thenReturn(List.of());
        String tomorrowSummary = service.handle(user, "/tomorrow@DutyLogBot");
        assertTrue(tomorrowSummary.startsWith("Завтра, " + tomorrow.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))));

        when(dayEntries.findByOwnerAndDate(eq(user), any(LocalDate.class))).thenReturn(Optional.empty());
        when(taskService.listDay(eq(user), anyString())).thenReturn(List.of());
        String week = service.handle(user, "/week@DutyLogBot");
        assertTrue(week.startsWith("Ближайшие 7 дней"));
        assertEquals(7, week.lines().filter(line -> line.contains(" — ")).count());
        assertTrue(week.contains("не назначена"));
    }


    @Test
    void todaySummaryStillAnswersWhenOneDataSectionFails() {
        LocalDate today = LocalDate.of(2026, 7, 23);
        when(userTimeService.today(user)).thenReturn(today);
        when(dayEntries.findByOwnerAndDate(user, today)).thenReturn(Optional.empty());
        when(taskService.listDay(user, today.toString())).thenReturn(List.of());
        when(importantDayService.occurrences(user, today, today)).thenReturn(List.of());
        when(overtimeService.account(user)).thenThrow(new IllegalStateException("broken overtime projection"));

        String summary = service.handle(user, "/today");

        assertTrue(summary.startsWith("Сегодня, 23.07.2026"));
        assertTrue(summary.contains("Смена: не назначена"));
        assertTrue(summary.contains("Задачи: 0 открыто"));
        assertTrue(summary.contains("Баланс переработок: временно недоступен"));
        assertTrue(summary.contains("Часть данных не загрузилась: баланс переработок"));
    }

    private TaskDto task(Long id, LocalDate date, String text, boolean done, boolean overdue) {
        return new TaskDto(id, date.toString(), text, done, "работа", TaskPriority.NORMAL,
                date.toString(), "18:00", false, null, overdue);
    }

    private OvertimeAccountDto account(double earned, double used, double balance) {
        return new OvertimeAccountDto(earned, used, balance, List.of(), List.of());
    }

    private void assertBadRequest(Runnable action, String messagePart) {
        ApiException error = assertThrows(ApiException.class, action::run);
        assertTrue(error.getMessage().contains(messagePart), error.getMessage());
    }
}
