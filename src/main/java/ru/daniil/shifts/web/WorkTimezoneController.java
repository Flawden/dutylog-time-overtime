package ru.daniil.shifts.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.WorkTimezoneTerm;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.UserTimeService;
import ru.daniil.shifts.service.WorkTimezoneChangeService;
import ru.daniil.shifts.service.WorkTimezoneHistoryService;
import ru.daniil.shifts.service.exception.ApiException;

import java.security.Principal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * User-facing Temporal Work Context boundary.
 *
 * v27.46.1 exposes date-first timezone history. The underlying domain keeps
 * LocalDateTime effective boundaries so a more precise UX can be added later
 * without replacing the temporal model.
 */
@RestController
@RequestMapping({
        "/api/time/work-context",
        "/api/v1/time/work-context"
})
public class WorkTimezoneController {

    private final CurrentUserService currentUserService;
    private final UserTimeService userTimeService;
    private final WorkTimezoneHistoryService historyService;
    private final WorkTimezoneChangeService changeService;

    public WorkTimezoneController(
            CurrentUserService currentUserService,
            UserTimeService userTimeService,
            WorkTimezoneHistoryService historyService,
            WorkTimezoneChangeService changeService
    ) {
        this.currentUserService = currentUserService;
        this.userTimeService = userTimeService;
        this.historyService = historyService;
        this.changeService = changeService;
    }

    public record WorkTimezoneChangeRequest(
            String effectiveFrom,
            String timezone
    ) {}

    public record WorkTimezoneTermDto(
            String effectiveFrom,
            String timezone,
            boolean baseline
    ) {}

    public record WorkTimezoneHistoryDto(
            String currentTimezone,
            String currentDate,
            List<WorkTimezoneTermDto> terms
    ) {}

    @GetMapping
    public WorkTimezoneHistoryDto history(
            Principal principal
    ) {
        AppUser user =
                currentUserService.requireUser(principal);

        return historyDto(user);
    }

    @PutMapping
    public WorkTimezoneHistoryDto update(
            @RequestBody WorkTimezoneChangeRequest request,
            Principal principal
    ) {
        AppUser user =
                currentUserService.requireUser(principal);

        LocalDate today =
                userTimeService.workToday(user);

        LocalDate effectiveDate =
                parseEffectiveDate(
                        request == null
                                ? null
                                : request.effectiveFrom()
                );

        if (effectiveDate.isAfter(today)) {
            throw ApiException.badRequest(
                    "Будущая смена рабочего часового пояса пока не поддерживается"
            );
        }

        LocalDate baselineDate =
                WorkTimezoneHistoryService
                        .BASELINE_EFFECTIVE_FROM
                        .toLocalDate();

        if (!effectiveDate.isAfter(baselineDate)) {
            throw ApiException.badRequest(
                    "Исходные условия часового пояса защищены от изменения"
            );
        }

        changeService.upsertAndReconcile(
                user,
                effectiveDate.atStartOfDay(),
                request == null
                        ? null
                        : request.timezone()
        );

        return historyDto(user);
    }

    private WorkTimezoneHistoryDto historyDto(
            AppUser user
    ) {
        List<WorkTimezoneTermDto> rows =
                historyService.history(user)
                        .stream()
                        .map(this::dto)
                        .toList();

        return new WorkTimezoneHistoryDto(
                userTimeService.workZone(user).getId(),
                userTimeService.workToday(user).toString(),
                rows
        );
    }

    private WorkTimezoneTermDto dto(
            WorkTimezoneTerm term
    ) {
        boolean baseline =
                WorkTimezoneHistoryService
                        .BASELINE_EFFECTIVE_FROM
                        .equals(term.getEffectiveFrom());

        return new WorkTimezoneTermDto(
                term.getEffectiveFrom()
                        .toLocalDate()
                        .toString(),
                term.getTimezoneId(),
                baseline
        );
    }

    private LocalDate parseEffectiveDate(
            String raw
    ) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest(
                    "Нужно указать дату вступления часового пояса"
            );
        }

        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            throw ApiException.badRequest(
                    "Дата вступления должна быть в формате yyyy-MM-dd"
            );
        }
    }
}
