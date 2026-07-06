package ru.daniil.shifts.telegram;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.ImportantDayOccurrenceDto;
import ru.daniil.shifts.dto.Dtos.OvertimeAccountDto;
import ru.daniil.shifts.dto.Dtos.TaskDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.service.ImportantDayService;
import ru.daniil.shifts.service.OvertimeService;
import ru.daniil.shifts.service.TaskService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class TelegramCommandService {
    private static final DateTimeFormatter RU_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final DayEntryRepository dayEntries;
    private final TaskService taskService;
    private final ImportantDayService importantDayService;
    private final OvertimeService overtimeService;

    public TelegramCommandService(DayEntryRepository dayEntries,
                                  TaskService taskService,
                                  ImportantDayService importantDayService,
                                  OvertimeService overtimeService) {
        this.dayEntries = dayEntries;
        this.taskService = taskService;
        this.importantDayService = importantDayService;
        this.overtimeService = overtimeService;
    }

    @Transactional(readOnly = true)
    public String handle(AppUser user, String text) {
        String cmd = commandOf(text);
        return switch (cmd) {
            case "/today", "/сегодня" -> daySummary(user, LocalDate.now(), "Сегодня");
            case "/tomorrow", "/завтра" -> daySummary(user, LocalDate.now().plusDays(1), "Завтра");
            case "/tasks", "/задачи" -> tasksSummary(user);
            case "/balance", "/overtime", "/баланс", "/переработка" -> balanceSummary(user);
            case "/week", "/неделя" -> weekSummary(user);
            case "/help", "/start", "" -> help();
            default -> "Не понял команду. Напиши /help — покажу, что умею.";
        };
    }

    public String help() {
        return String.join("\n",
                "DutyLog: Time & Overtime",
                "",
                "Команды:",
                "/today или /сегодня — что сегодня",
                "/tomorrow или /завтра — что завтра",
                "/week или /неделя — ближайшие 7 дней",
                "/tasks или /задачи — открытые задачи",
                "/balance или /баланс — остаток переработок",
                "/help — помощь",
                "",
                "В этой версии бот только читает данные. Быстрые действия для задач и переработок добавим следующим заходом."
        );
    }

    private String daySummary(AppUser user, LocalDate date, String label) {
        DayEntry entry = dayEntries.findByOwnerAndDate(user, date).orElse(null);
        List<TaskDto> tasks = taskService.listDay(user, date.toString());
        List<TaskDto> openTasks = tasks.stream().filter(t -> !t.done()).toList();
        List<TaskDto> overdueTasks = tasks.stream().filter(t -> t.overdue() && !t.done()).toList();
        List<ImportantDayOccurrenceDto> important = importantDayService.occurrences(user, date, date);
        OvertimeAccountDto account = overtimeService.account(user);

        StringBuilder sb = new StringBuilder();
        sb.append(label).append(", ").append(date.format(RU_DATE)).append("\n\n");
        sb.append("Смена: ").append(shiftText(entry)).append("\n");
        sb.append("Задачи: ").append(openTasks.size()).append(" открыто");
        if (!overdueTasks.isEmpty()) sb.append(", просрочено ").append(overdueTasks.size());
        sb.append("\n");
        if (!important.isEmpty()) {
            sb.append("Важные дни: ");
            sb.append(important.stream().map(ImportantDayOccurrenceDto::title).limit(4).reduce((a,b) -> a + ", " + b).orElse("—"));
            if (important.size() > 4) sb.append(" и ещё ").append(important.size() - 4);
            sb.append("\n");
        }
        sb.append("Баланс переработок: ").append(fmt(account.balanceHours())).append(" ч");

        if (!openTasks.isEmpty()) {
            sb.append("\n\nБлижайшие задачи:");
            openTasks.stream().limit(5).forEach(t -> sb.append("\n• ").append(taskLine(t)));
        }
        return sb.toString();
    }

    private String weekSummary(AppUser user) {
        LocalDate today = LocalDate.now();
        StringBuilder sb = new StringBuilder("Ближайшие 7 дней\n");
        for (int i = 0; i < 7; i++) {
            LocalDate d = today.plusDays(i);
            DayEntry entry = dayEntries.findByOwnerAndDate(user, d).orElse(null);
            List<TaskDto> open = taskService.listDay(user, d.toString()).stream().filter(t -> !t.done()).toList();
            sb.append("\n").append(d.format(DateTimeFormatter.ofPattern("dd.MM"))).append(" — ")
              .append(shiftText(entry));
            if (!open.isEmpty()) sb.append(" · задач: ").append(open.size());
        }
        return sb.toString();
    }

    private String tasksSummary(AppUser user) {
        List<TaskDto> tasks = taskService.listBoard(user, "open", "all", "all", "", null, null);
        if (tasks.isEmpty()) return "Открытых задач нет. Красота.";
        StringBuilder sb = new StringBuilder("Открытые задачи: ").append(tasks.size()).append("\n");
        tasks.stream().limit(12).forEach(t -> sb.append("\n• ").append(taskLine(t)));
        if (tasks.size() > 12) sb.append("\n…и ещё ").append(tasks.size() - 12);
        return sb.toString();
    }

    private String balanceSummary(AppUser user) {
        OvertimeAccountDto account = overtimeService.account(user);
        return String.join("\n",
                "Баланс переработок",
                "",
                "Начислено: " + fmt(account.totalEarnedHours()) + " ч",
                "Списано: " + fmt(account.totalUsedHours()) + " ч",
                "Остаток: " + fmt(account.balanceHours()) + " ч"
        );
    }

    private String shiftText(DayEntry entry) {
        if (entry == null || entry.getShiftType() == null) return "не назначена";
        ShiftType s = entry.getShiftType();
        StringBuilder sb = new StringBuilder(s.getName());
        if (s.getStartTime() != null && s.getEndTime() != null) {
            sb.append(" ").append(s.getStartTime()).append("–").append(s.getEndTime());
        }
        double plan = s.effectivePlannedHours();
        if (plan > 0.0001) sb.append(" · план ").append(fmt(plan)).append(" ч");
        return sb.toString();
    }

    private String taskLine(TaskDto t) {
        StringBuilder sb = new StringBuilder();
        if (t.overdue() && !t.done()) sb.append("‼ ");
        sb.append(t.text());
        if (t.category() != null && !t.category().isBlank()) sb.append(" · ").append(t.category());
        if (t.dueDate() != null && !t.dueDate().isBlank()) {
            sb.append(" · срок ").append(t.dueDate());
            if (t.dueTime() != null && !t.dueTime().isBlank()) sb.append(" ").append(t.dueTime());
        }
        return sb.toString();
    }

    private String commandOf(String text) {
        if (text == null || text.isBlank()) return "";
        String first = text.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
        int at = first.indexOf('@');
        if (at >= 0) first = first.substring(0, at);
        return first;
    }

    private String fmt(double v) {
        String s = String.format(Locale.US, "%.2f", v);
        return s.replaceAll("\\.00$", "").replaceAll("(\\.\\d)0$", "$1");
    }
}
