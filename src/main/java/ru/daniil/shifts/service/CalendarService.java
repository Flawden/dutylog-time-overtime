package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.CalendarRangeDto;
import ru.daniil.shifts.dto.Dtos.DayDto;
import ru.daniil.shifts.dto.Dtos.ImportantDayOccurrenceDto;
import ru.daniil.shifts.dto.Dtos.OvertimeSummaryDto;
import ru.daniil.shifts.dto.Dtos.OvertimeAccountDto;
import ru.daniil.shifts.dto.Dtos.NotificationReminderDto;
import ru.daniil.shifts.dto.Dtos.NotificationSettingsDto;
import ru.daniil.shifts.dto.Dtos.ModuleDto;
import ru.daniil.shifts.dto.Dtos.QuickScenarioDto;
import ru.daniil.shifts.dto.Dtos.ShiftTypeDto;
import ru.daniil.shifts.dto.Dtos.TaskDto;
import ru.daniil.shifts.model.AppUser;

import java.time.LocalDate;
import java.util.List;

@Service
public class CalendarService {
    private final DayEntryService dayEntryService;
    private final ShiftTypeService shiftTypeService;
    private final OvertimeService overtimeService;
    private final TaskService taskService;
    private final ImportantDayService importantDayService;
    private final NotificationService notificationService;
    private final QuickScenarioService quickScenarioService;
    private final ModuleService moduleService;

    public CalendarService(DayEntryService dayEntryService,
                           ShiftTypeService shiftTypeService,
                           OvertimeService overtimeService,
                           TaskService taskService,
                           ImportantDayService importantDayService,
                           NotificationService notificationService,
                           QuickScenarioService quickScenarioService,
                           ModuleService moduleService) {
        this.dayEntryService = dayEntryService;
        this.shiftTypeService = shiftTypeService;
        this.overtimeService = overtimeService;
        this.taskService = taskService;
        this.importantDayService = importantDayService;
        this.notificationService = notificationService;
        this.quickScenarioService = quickScenarioService;
        this.moduleService = moduleService;
    }

    @Transactional
    public CalendarRangeDto range(AppUser user, LocalDate from, LocalDate to) {
        dayEntryService.validateRange(from, to);
        List<ModuleDto> modules = moduleService.list(user);
        boolean notesEnabled = moduleService.isEnabled(user, ModuleService.NOTES);
        boolean tasksEnabled = moduleService.isEnabled(user, ModuleService.TASKS);
        boolean overtimeEnabled = moduleService.isEnabled(user, ModuleService.OVERTIME);
        boolean importantEnabled = moduleService.isEnabled(user, ModuleService.IMPORTANT_DATES);
        boolean notificationsEnabled = moduleService.isEnabled(user, ModuleService.NOTIFICATIONS);
        boolean scenariosEnabled = moduleService.isEnabled(user, ModuleService.SCENARIOS);

        List<ShiftTypeDto> shiftTypes = shiftTypeService.list(user);
        List<DayDto> dayEntries = dayEntryService.listRange(user, from, to).stream()
                .map(day -> new DayDto(
                        day.date(),
                        day.shiftTypeId(),
                        notesEnabled ? day.note() : null,
                        day.dayEmoji(),
                        overtimeEnabled ? day.overtimeHours() : 0,
                        overtimeEnabled ? day.timeOffHours() : 0,
                        overtimeEnabled ? day.overtimeBalanceHours() : 0
                ))
                .toList();
        List<TaskDto> tasks = tasksEnabled ? taskService.listRange(user, from, to) : List.of();
        List<ImportantDayOccurrenceDto> importantDays = importantEnabled ? importantDayService.occurrences(user, from, to) : List.of();
        OvertimeSummaryDto overtime = overtimeEnabled
                ? overtimeService.summary(user, from, to)
                : new OvertimeSummaryDto(from.toString(), to.toString(), 0, 0, 0);
        OvertimeAccountDto overtimeAccount = overtimeEnabled
                ? overtimeService.account(user)
                : new OvertimeAccountDto(0, 0, 0, List.of(), List.of());
        NotificationSettingsDto notificationSettings = notificationsEnabled ? notificationService.settings(user) : null;
        List<NotificationReminderDto> reminders = notificationsEnabled ? notificationService.upcoming(user, from, to) : List.of();
        List<QuickScenarioDto> quickScenarios = scenariosEnabled && overtimeEnabled ? quickScenarioService.list(user) : List.of();
        return new CalendarRangeDto(from.toString(), to.toString(), shiftTypes, dayEntries, tasks, importantDays, overtime, overtimeAccount, notificationSettings, reminders, quickScenarios, modules);
    }
}
