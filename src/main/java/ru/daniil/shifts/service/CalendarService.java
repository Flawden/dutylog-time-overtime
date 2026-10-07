package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.CalendarRangeDto;
import ru.daniil.shifts.dto.Dtos.CalendarLayerDto;
import ru.daniil.shifts.dto.Dtos.DayDto;
import ru.daniil.shifts.dto.Dtos.ImportantDayOccurrenceDto;
import ru.daniil.shifts.dto.Dtos.AbsenceOccurrenceDto;
import ru.daniil.shifts.dto.Dtos.OvertimeSummaryDto;
import ru.daniil.shifts.dto.Dtos.OvertimeAccountDto;
import ru.daniil.shifts.dto.Dtos.NotificationReminderDto;
import ru.daniil.shifts.dto.Dtos.NotificationSettingsDto;
import ru.daniil.shifts.dto.Dtos.ModuleDto;
import ru.daniil.shifts.dto.Dtos.QuickScenarioDto;
import ru.daniil.shifts.dto.Dtos.ShiftTypeDto;
import ru.daniil.shifts.dto.Dtos.ShiftOccurrenceDto;
import ru.daniil.shifts.dto.Dtos.TaskDto;
import ru.daniil.shifts.model.AppUser;

import java.time.LocalDate;
import java.util.List;

@Service
public class CalendarService {
    private final DayEntryService dayEntryService;
    private final ShiftTypeService shiftTypeService;
    private final ShiftOccurrenceService shiftOccurrenceService;
    private final OvertimeService overtimeService;
    private final TaskService taskService;
    private final ImportantDayService importantDayService;
    private final NotificationService notificationService;
    private final QuickScenarioService quickScenarioService;
    private final ModuleService moduleService;
    private final CalendarLayerService calendarLayerService;
    private final VacationPlannerService vacationPlannerService;

    public CalendarService(DayEntryService dayEntryService,
                           ShiftTypeService shiftTypeService,
                           ShiftOccurrenceService shiftOccurrenceService,
                           OvertimeService overtimeService,
                           TaskService taskService,
                           ImportantDayService importantDayService,
                           NotificationService notificationService,
                           QuickScenarioService quickScenarioService,
                           ModuleService moduleService,
                           CalendarLayerService calendarLayerService,
                           VacationPlannerService vacationPlannerService) {
        this.dayEntryService = dayEntryService;
        this.shiftTypeService = shiftTypeService;
        this.shiftOccurrenceService = shiftOccurrenceService;
        this.overtimeService = overtimeService;
        this.taskService = taskService;
        this.importantDayService = importantDayService;
        this.notificationService = notificationService;
        this.quickScenarioService = quickScenarioService;
        this.moduleService = moduleService;
        this.calendarLayerService = calendarLayerService;
        this.vacationPlannerService = vacationPlannerService;
    }

    @Transactional
    public CalendarRangeDto range(AppUser user, LocalDate from, LocalDate to) {
        dayEntryService.validateRange(from, to);
        List<ModuleDto> modules = moduleService.list(user);
        boolean notesEnabled = modules.stream().anyMatch(module -> ModuleService.NOTES.equals(module.key()) && module.enabled());
        boolean tasksEnabled = modules.stream().anyMatch(module -> ModuleService.TASKS.equals(module.key()) && module.enabled());
        boolean overtimeEnabled = modules.stream().anyMatch(module -> ModuleService.OVERTIME.equals(module.key()) && module.enabled());
        boolean importantEnabled = modules.stream().anyMatch(module -> ModuleService.IMPORTANT_DATES.equals(module.key()) && module.enabled());
        boolean vacationEnabled = modules.stream().anyMatch(module -> ModuleService.VACATION.equals(module.key()) && module.enabled());
        boolean notificationsEnabled = modules.stream().anyMatch(module -> ModuleService.NOTIFICATIONS.equals(module.key()) && module.enabled());
        boolean scenariosEnabled = modules.stream().anyMatch(module -> ModuleService.SCENARIOS.equals(module.key()) && module.enabled());

        List<ShiftTypeDto> shiftTypes = shiftTypeService.list(user);
        List<DayDto> dayEntries = dayEntryService.listRange(user, from, to).stream()
                .map(day -> new DayDto(
                        day.date(),
                        day.shiftTypeId(),
                        notesEnabled ? day.note() : null,
                        day.dayEmoji(),
                        overtimeEnabled ? day.overtimeHours() : 0,
                        overtimeEnabled ? day.timeOffHours() : 0,
                        overtimeEnabled ? day.overtimeBalanceHours() : 0,
                        day.version(),
                        day.updatedAt(),
                        day.shiftInterval(),
                        notesEnabled ? day.notes() : List.of()
                ))
                .toList();
        List<ShiftOccurrenceDto> shiftOccurrences = shiftOccurrenceService.listForDisplayRange(user, from, to);
        List<TaskDto> tasks = tasksEnabled ? taskService.listRange(user, from, to) : List.of();
        List<ImportantDayOccurrenceDto> importantDays = importantEnabled ? importantDayService.occurrences(user, from, to) : List.of();
        List<AbsenceOccurrenceDto> absences = vacationEnabled ? vacationPlannerService.occurrences(user, from, to) : List.of();
        OvertimeAccountDto overtimeAccount = overtimeEnabled
                ? overtimeService.account(user)
                : new OvertimeAccountDto(0, 0, 0, List.of(), List.of());
        OvertimeSummaryDto overtime = overtimeEnabled
                ? overtimeService.summary(user, from, to, overtimeAccount)
                : new OvertimeSummaryDto(from.toString(), to.toString(), 0, 0, 0);
        NotificationSettingsDto notificationSettings = notificationsEnabled ? notificationService.settings(user) : null;
        List<NotificationReminderDto> reminders = notificationsEnabled ? notificationService.upcoming(user, from, to) : List.of();
        List<QuickScenarioDto> quickScenarios = scenariosEnabled && overtimeEnabled ? quickScenarioService.list(user) : List.of();
        List<CalendarLayerDto> calendarLayers = calendarLayerService.listForRange(user, from, to);
        return new CalendarRangeDto(from.toString(), to.toString(), shiftTypes, dayEntries, shiftOccurrences, tasks, importantDays, absences, overtime, overtimeAccount, notificationSettings, reminders, quickScenarios, calendarLayers, modules);
    }
}
