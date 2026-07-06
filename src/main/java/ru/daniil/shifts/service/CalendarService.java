package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.CalendarRangeDto;
import ru.daniil.shifts.dto.Dtos.DayDto;
import ru.daniil.shifts.dto.Dtos.ImportantDayOccurrenceDto;
import ru.daniil.shifts.dto.Dtos.OvertimeSummaryDto;
import ru.daniil.shifts.dto.Dtos.OvertimeAccountDto;
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

    public CalendarService(DayEntryService dayEntryService,
                           ShiftTypeService shiftTypeService,
                           OvertimeService overtimeService,
                           TaskService taskService,
                           ImportantDayService importantDayService) {
        this.dayEntryService = dayEntryService;
        this.shiftTypeService = shiftTypeService;
        this.overtimeService = overtimeService;
        this.taskService = taskService;
        this.importantDayService = importantDayService;
    }

    @Transactional
    public CalendarRangeDto range(AppUser user, LocalDate from, LocalDate to) {
        dayEntryService.validateRange(from, to);
        List<ShiftTypeDto> shiftTypes = shiftTypeService.list(user);
        List<DayDto> dayEntries = dayEntryService.listRange(user, from, to);
        List<TaskDto> tasks = taskService.listRange(user, from, to);
        List<ImportantDayOccurrenceDto> importantDays = importantDayService.occurrences(user, from, to);
        OvertimeSummaryDto overtime = overtimeService.summary(user, from, to);
        OvertimeAccountDto overtimeAccount = overtimeService.account(user);
        return new CalendarRangeDto(from.toString(), to.toString(), shiftTypes, dayEntries, tasks, importantDays, overtime, overtimeAccount);
    }
}
