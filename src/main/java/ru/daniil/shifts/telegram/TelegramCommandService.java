package ru.daniil.shifts.telegram;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.ImportantDayOccurrenceDto;
import ru.daniil.shifts.dto.Dtos.OvertimeAccountDto;
import ru.daniil.shifts.dto.Dtos.OvertimeCreditCreateRequest;
import ru.daniil.shifts.dto.Dtos.OvertimeUsageCreateRequest;
import ru.daniil.shifts.dto.Dtos.TaskCreateRequest;
import ru.daniil.shifts.dto.Dtos.TaskDto;
import ru.daniil.shifts.dto.Dtos.TaskUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.service.ImportantDayService;
import ru.daniil.shifts.service.OvertimeService;
import ru.daniil.shifts.service.TaskService;
import ru.daniil.shifts.service.UserTimeService;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TelegramCommandService {
    private static final DateTimeFormatter RU_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Pattern INTERVAL = Pattern.compile("^([0-2]?\\d(?::[0-5]\\d)?)[\\-–—]([0-2]?\\d(?::[0-5]\\d)?)$");
    private static final Pattern HOURS = Pattern.compile("^\\d+(?:[,.]\\d{1,2})?$");
    private static final Pattern BREAK_TOKEN = Pattern.compile("^(?:обед|break)(\\d{1,4})$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern PLAN_TOKEN = Pattern.compile("^(?:план|plan)(\\d+(?:[,.]\\d{1,2})?)$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final DayEntryRepository dayEntries;
    private final TaskService taskService;
    private final ImportantDayService importantDayService;
    private final OvertimeService overtimeService;
    private final UserTimeService userTimeService = new UserTimeService();

    public TelegramCommandService(DayEntryRepository dayEntries,
                                  TaskService taskService,
                                  ImportantDayService importantDayService,
                                  OvertimeService overtimeService) {
        this.dayEntries = dayEntries;
        this.taskService = taskService;
        this.importantDayService = importantDayService;
        this.overtimeService = overtimeService;
    }

    @Transactional
    public String handle(AppUser user, String text) {
        String cmd = commandOf(text);
        String args = argsOf(text);
        return switch (cmd) {
            case "/today", "/сегодня" -> daySummary(user, userTimeService.today(user), "Сегодня");
            case "/tomorrow", "/завтра" -> daySummary(user, userTimeService.today(user).plusDays(1), "Завтра");
            case "/tasks", "/задачи" -> tasksSummary(user);
            case "/balance", "/overtime", "/баланс", "/переработка" -> balanceSummary(user);
            case "/week", "/неделя" -> weekSummary(user);
            case "/task", "/addtask", "/задача", "/добавить" -> createTask(user, args);
            case "/done", "/готово", "/закрыть" -> completeTask(user, args);
            case "/ppr", "/ппр", "/plus", "/hours", "/начислить", "/переработка+" -> createOvertimeCredit(user, args);
            case "/timeoff", "/отгул", "/списать" -> createTimeOffUsage(user, args);
            case "/help", "/start", "" -> help();
            default -> "Не понял команду. Напиши /help — покажу, что умею.";
        };
    }

    public String help() {
        return String.join("\n",
                "DutyLog: Time & Overtime",
                "",
                "Просмотр:",
                "/today или /сегодня — что сегодня",
                "/tomorrow или /завтра — что завтра",
                "/week или /неделя — ближайшие 7 дней",
                "/tasks или /задачи — открытые задачи",
                "/balance или /баланс — остаток переработок",
                "",
                "Быстрые действия:",
                "/task текст — добавить задачу на сегодня",
                "/task завтра текст — добавить задачу на завтра",
                "/done 12 — закрыть задачу #12",
                "/ppr 17-08 причина — начислить переработку интервалом",
                "/ppr 2 причина — начислить 2 часа вручную",
                "/timeoff 8 причина — списать 8 часов отгула",
                "",
                "Можно указывать дату первым аргументом: /task 2026-07-10 текст, /ppr 10.07 17-20 причина.",
                "Для интервала можно добавить обед и план: /ppr 17-08 обед60 план0 причина."
        );
    }

    private String createTask(AppUser user, String args) {
        if (args == null || args.isBlank()) {
            throw ApiException.badRequest("Напиши текст задачи: /task купить молоко");
        }
        DateAndRest parsed = takeDate(args, userTimeService.today(user));
        String taskText = parsed.rest().trim();
        if (taskText.isBlank()) {
            throw ApiException.badRequest("После даты нужен текст задачи: /task завтра купить молоко");
        }
        TaskDto task = taskService.create(user, new TaskCreateRequest(
                parsed.date().toString(),
                taskText,
                null,
                null,
                null,
                null,
                false,
                null
        ));
        return "Задача добавлена на " + parsed.date().format(RU_DATE) + ":\n#" + task.id() + " " + task.text();
    }

    private String completeTask(AppUser user, String args) {
        String idText = args == null ? "" : args.trim().replaceFirst("^#", "");
        if (idText.isBlank() || !idText.matches("\\d+")) {
            throw ApiException.badRequest("Укажи id задачи: /done 12. Id видно в /tasks.");
        }
        long id = Long.parseLong(idText);
        TaskDto task = taskService.update(user, id, new TaskUpdateRequest(
                null,
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));
        return "Готово. Закрыл задачу #" + task.id() + ": " + task.text();
    }

    private String createOvertimeCredit(AppUser user, String args) {
        if (args == null || args.isBlank()) {
            throw ApiException.badRequest("Формат: /ppr 17-08 причина или /ppr 2 причина");
        }
        DateAndRest dated = takeDate(args, userTimeService.today(user));
        String rest = dated.rest().trim();
        if (rest.isBlank()) throw ApiException.badRequest("Укажи часы или интервал: /ppr 17-08 причина");

        String[] parts = rest.split("\\s+", 2);
        String value = parts[0].trim();
        String tail = parts.length > 1 ? parts[1].trim() : "";

        if (HOURS.matcher(value.replace(',', '.')).matches()) {
            double hours = parseHours(value, "Количество часов должно быть числом: /ppr 2 причина");
            String reason = tail.isBlank() ? "Начислено из Telegram" : tail;
            OvertimeAccountDto account = overtimeService.createCredit(user, new OvertimeCreditCreateRequest(
                    dated.date().toString(),
                    "Telegram: ручное начисление",
                    null,
                    null,
                    0,
                    0.0,
                    hours,
                    reason
            ));
            return "Начислил " + fmt(hours) + " ч на " + dated.date().format(RU_DATE) + ".\nОстаток: " + fmt(account.balanceHours()) + " ч";
        }

        Matcher m = INTERVAL.matcher(value);
        if (!m.matches()) {
            throw ApiException.badRequest("Не понял интервал. Пример: /ppr 17-08 ППР после смены");
        }

        ParsedOvertimeTail parsedTail = parseOvertimeTail(tail);
        LocalTime start = parseTime(m.group(1));
        LocalTime end = parseTime(m.group(2));
        LocalDateTime startAt = LocalDateTime.of(dated.date(), start);
        LocalDateTime endAt = LocalDateTime.of(dated.date(), end);
        if (!endAt.isAfter(startAt)) endAt = endAt.plusDays(1);
        String reason = parsedTail.reason().isBlank() ? "Начислено из Telegram" : parsedTail.reason();

        OvertimeAccountDto account = overtimeService.createCredit(user, new OvertimeCreditCreateRequest(
                dated.date().toString(),
                value,
                startAt.toString(),
                endAt.toString(),
                parsedTail.breakMinutes(),
                parsedTail.plannedHours(),
                null,
                reason
        ));
        double added = account.credits().stream()
                .filter(c -> reason.equals(c.reason()) && c.startDateTime() != null && !c.startDateTime().isBlank())
                .filter(c -> c.startDateTime().startsWith(startAt.toLocalDate().toString()))
                .mapToDouble(c -> c.hours())
                .sum();
        String addedText = added > 0.0001 ? fmt(added) + " ч" : "запись";
        return "Начислил " + addedText + " за " + value + ".\nОстаток: " + fmt(account.balanceHours()) + " ч";
    }

    private String createTimeOffUsage(AppUser user, String args) {
        if (args == null || args.isBlank()) {
            throw ApiException.badRequest("Формат: /timeoff 8 причина или /отгул завтра 8 причина");
        }
        DateAndRest dated = takeDate(args, userTimeService.today(user));
        String rest = dated.rest().trim();
        String[] parts = rest.split("\\s+", 2);
        if (parts.length == 0 || parts[0].isBlank()) {
            throw ApiException.badRequest("Укажи часы списания: /timeoff 8 отгул");
        }
        double hours = parseHours(parts[0], "Часы списания должны быть числом: /timeoff 8 причина");
        String reason = parts.length > 1 && !parts[1].isBlank() ? parts[1].trim() : "Списано из Telegram";
        OvertimeAccountDto account = overtimeService.createUsage(user, new OvertimeUsageCreateRequest(
                dated.date().toString(),
                hours,
                reason
        ));
        return "Списал " + fmt(hours) + " ч на " + dated.date().format(RU_DATE) + ".\nОстаток: " + fmt(account.balanceHours()) + " ч";
    }

    private ParsedOvertimeTail parseOvertimeTail(String tail) {
        int breakMinutes = 0;
        double plannedHours = 0.0;
        StringBuilder reason = new StringBuilder();
        if (tail == null || tail.isBlank()) return new ParsedOvertimeTail(breakMinutes, plannedHours, "");
        for (String token : tail.trim().split("\\s+")) {
            Matcher breakMatcher = BREAK_TOKEN.matcher(token);
            Matcher planMatcher = PLAN_TOKEN.matcher(token);
            if (breakMatcher.matches()) {
                breakMinutes = Integer.parseInt(breakMatcher.group(1));
            } else if (planMatcher.matches()) {
                plannedHours = parseNonNegativeHours(planMatcher.group(1), "План должен быть числом, например план8 или план0");
            } else {
                if (!reason.isEmpty()) reason.append(' ');
                reason.append(token);
            }
        }
        return new ParsedOvertimeTail(breakMinutes, plannedHours, reason.toString().trim());
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
        LocalDate today = userTimeService.today(user);
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
        var taskPage = taskService.listBoard(user, "open", "all", "all", "", null, null, 0, 50);
        List<TaskDto> tasks = taskPage.items();
        long total = taskPage.total();
        if (tasks.isEmpty()) return "Открытых задач нет. Красота.";
        StringBuilder sb = new StringBuilder("Открытые задачи: ").append(total).append("\n");
        tasks.stream().limit(12).forEach(t -> sb.append("\n• ").append(taskLine(t)));
        if (total > 12) sb.append("\n…и ещё ").append(total - 12);
        sb.append("\n\nЗакрыть задачу: /done 12");
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
        sb.append("#").append(t.id()).append(" ");
        if (t.overdue() && !t.done()) sb.append("‼ ");
        sb.append(t.text());
        if (t.category() != null && !t.category().isBlank()) sb.append(" · ").append(t.category());
        if (t.dueDate() != null && !t.dueDate().isBlank()) {
            sb.append(" · срок ").append(t.dueDate());
            if (t.dueTime() != null && !t.dueTime().isBlank()) sb.append(" ").append(t.dueTime());
        }
        return sb.toString();
    }

    private DateAndRest takeDate(String args, LocalDate defaultDate) {
        String trimmed = args == null ? "" : args.trim();
        if (trimmed.isBlank()) return new DateAndRest(defaultDate, "");
        String[] parts = trimmed.split("\\s+", 2);
        Optional<LocalDate> date = parseLooseDate(parts[0], defaultDate);
        if (date.isPresent()) return new DateAndRest(date.get(), parts.length > 1 ? parts[1] : "");
        return new DateAndRest(defaultDate, trimmed);
    }

    private Optional<LocalDate> parseLooseDate(String token, LocalDate today) {
        if (token == null || token.isBlank()) return Optional.empty();
        String t = token.trim().toLowerCase(Locale.ROOT);
        if (t.equals("today") || t.equals("сегодня")) return Optional.of(today);
        if (t.equals("tomorrow") || t.equals("завтра")) return Optional.of(today.plusDays(1));
        try {
            if (t.matches("\\d{4}-\\d{2}-\\d{2}")) return Optional.of(LocalDate.parse(t));
            if (t.matches("\\d{1,2}\\.\\d{1,2}\\.\\d{4}")) {
                String[] p = t.split("\\.");
                return Optional.of(LocalDate.of(Integer.parseInt(p[2]), Integer.parseInt(p[1]), Integer.parseInt(p[0])));
            }
            if (t.matches("\\d{1,2}\\.\\d{1,2}")) {
                String[] p = t.split("\\.");
                return Optional.of(LocalDate.of(today.getYear(), Integer.parseInt(p[1]), Integer.parseInt(p[0])));
            }
        } catch (DateTimeException | NumberFormatException ignored) {
            throw ApiException.badRequest("Дата должна быть yyyy-MM-dd или dd.MM");
        }
        return Optional.empty();
    }

    private LocalTime parseTime(String value) {
        String v = value.trim();
        if (v.matches("^[0-2]?\\d$")) v = String.format(Locale.ROOT, "%02d:00", Integer.parseInt(v));
        if (v.matches("^[0-2]?\\d:[0-5]\\d$")) {
            String[] p = v.split(":");
            v = String.format(Locale.ROOT, "%02d:%s", Integer.parseInt(p[0]), p[1]);
        }
        try { return LocalTime.parse(v); }
        catch (DateTimeParseException e) { throw ApiException.badRequest("Время должно быть HH:mm или просто часом: 17-08"); }
    }

    private double parseHours(String value, String message) {
        double v = parseNonNegativeHours(value, message);
        if (v <= 0) throw ApiException.badRequest("Часы должны быть больше 0");
        return v;
    }

    private double parseNonNegativeHours(String value, String message) {
        try {
            double v = Double.parseDouble(value.replace(',', '.'));
            if (v < 0 || v > 100) throw ApiException.badRequest("Часы должны быть от 0 до 100");
            return v;
        } catch (NumberFormatException e) {
            throw ApiException.badRequest(message);
        }
    }

    private String commandOf(String text) {
        if (text == null || text.isBlank()) return "";
        String first = text.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
        int at = first.indexOf('@');
        if (at >= 0) first = first.substring(0, at);
        return first;
    }

    private String argsOf(String text) {
        if (text == null || text.isBlank()) return "";
        String trimmed = text.trim();
        int space = trimmed.indexOf(' ');
        return space < 0 ? "" : trimmed.substring(space + 1).trim();
    }

    private String fmt(double v) {
        String s = String.format(Locale.US, "%.2f", v);
        return s.replaceAll("\\.00$", "").replaceAll("(\\.\\d)0$", "$1");
    }

    private record DateAndRest(LocalDate date, String rest) {}
    private record ParsedOvertimeTail(int breakMinutes, double plannedHours, String reason) {}
}
