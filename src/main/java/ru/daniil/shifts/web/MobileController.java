package ru.daniil.shifts.web;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import ru.daniil.shifts.dto.Dtos.CalendarRangeDto;
import ru.daniil.shifts.dto.Dtos.DayDto;
import ru.daniil.shifts.dto.Dtos.MobileBootstrapDto;
import ru.daniil.shifts.dto.Dtos.MobileSyncRequest;
import ru.daniil.shifts.dto.Dtos.MobileSyncResultDto;
import ru.daniil.shifts.dto.Dtos.MobileUserDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CalendarService;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.DayEntryService;
import ru.daniil.shifts.service.ModuleService;

import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Удобные агрегированные endpoint'ы под Android.
 * Они не заменяют обычный REST API, а уменьшают количество запросов с телефона.
 */
@RestController
@RequestMapping("/api/mobile")
public class MobileController {
    private final CurrentUserService currentUserService;
    private final DayEntryService dayEntryService;
    private final CalendarService calendarService;
    private final ModuleService moduleService;

    public MobileController(CurrentUserService currentUserService,
                            DayEntryService dayEntryService,
                            CalendarService calendarService,
                            ModuleService moduleService) {
        this.currentUserService = currentUserService;
        this.dayEntryService = dayEntryService;
        this.calendarService = calendarService;
        this.moduleService = moduleService;
    }

    /**
     * Первый запрос после старта приложения: профиль + календарь диапазона + задачи + важные дни + баланс.
     * Пример: GET /api/mobile/bootstrap?from=2026-07-01&to=2026-08-31
     */
    @GetMapping("/bootstrap")
    public MobileBootstrapDto bootstrap(@RequestParam("from") String from,
                                        @RequestParam("to") String to,
                                        Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        LocalDate fromDate = dayEntryService.parseDate(from, "Дата from должна быть в формате yyyy-MM-dd");
        LocalDate toDate = dayEntryService.parseDate(to, "Дата to должна быть в формате yyyy-MM-dd");
        CalendarRangeDto calendar = calendarService.range(current, fromDate, toDate);
        return new MobileBootstrapDto(
                Instant.now().toString(),
                new MobileUserDto(current.getUsername()),
                calendar
        );
    }

    /**
     * Пакетная синхронизация изменений дней из offline-очереди Android.
     * Задачи и важные дни пока синхронизируются обычными /api/tasks и /api/important-days.
     */
    @PostMapping("/sync")
    public MobileSyncResultDto sync(@Valid @RequestBody(required = false) MobileSyncRequest req,
                                    Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        List<DayDto> changed = new ArrayList<>();
        if (req != null && req.days() != null) {
            req.days().forEach(dayChange -> requireEnabledModulesForMobileDayChange(current, dayChange));
            req.days().forEach(dayChange -> {
                DayDto saved = dayEntryService.patchMobileDay(current, dayChange);
                if (saved != null) {
                    changed.add(saved);
                }
            });
        }
        return new MobileSyncResultDto(
                Instant.now().toString(),
                changed,
                new LinkedHashMap<>()
        );
    }

    private void requireEnabledModulesForMobileDayChange(AppUser current, ru.daniil.shifts.dto.Dtos.MobileDayChangeRequest dayChange) {
        if (dayChange == null) {
            return;
        }
        if (dayChange.note() != null || Boolean.TRUE.equals(dayChange.clearNote())) {
            moduleService.requireEnabled(current, ModuleService.NOTES);
        }
        if (dayChange.overtimeHours() != null || dayChange.timeOffHours() != null) {
            moduleService.requireEnabled(current, ModuleService.OVERTIME);
        }
    }
}
