package ru.daniil.shifts.web;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.daniil.shifts.dto.Dtos.VacationPayPreviewDto;
import ru.daniil.shifts.dto.Dtos.VacationPayPreviewRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.ModuleService;
import ru.daniil.shifts.service.VacationPayApplicationService;
import ru.daniil.shifts.service.exception.ApiException;

import java.security.Principal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

/** Read-only HTTP boundary for canonical annual-vacation pay preview. */
@RestController
@RequestMapping({"/api/payroll/vacation-pay", "/api/v1/payroll/vacation-pay"})
public class VacationPayController {
    private final CurrentUserService users;
    private final ModuleService modules;
    private final VacationPayApplicationService vacationPay;

    public VacationPayController(
            CurrentUserService users,
            ModuleService modules,
            VacationPayApplicationService vacationPay
    ) {
        this.users = Objects.requireNonNull(users, "Vacation pay HTTP requires current-user service");
        this.modules = Objects.requireNonNull(modules, "Vacation pay HTTP requires module service");
        this.vacationPay = Objects.requireNonNull(vacationPay, "Vacation pay HTTP requires application service");
    }

    @PostMapping("/preview")
    public ResponseEntity<VacationPayPreviewDto> preview(
            @Valid @RequestBody(required = false) VacationPayPreviewRequest request,
            Principal principal
    ) {
        AppUser user = user(principal);
        if (request == null) {
            throw ApiException.badRequest("Тело запроса расчёта отпускных обязательно");
        }

        LocalDate eventDate = parseEventDate(request.eventDate());
        YearMonth discoveryThroughMonth = parseMonth(
                request.discoveryThroughMonth(),
                "discoveryThroughMonth должен быть в формате yyyy-MM"
        );
        List<YearMonth> provenNoPayrollMonths = parseProofMonths(request.provenNoPayrollMonths());

        VacationPayApplicationService.Resolution resolution = Objects.requireNonNull(
                vacationPay.resolve(
                        user,
                        eventDate,
                        request.absencePeriodId(),
                        discoveryThroughMonth,
                        provenNoPayrollMonths
                ),
                "Vacation pay application returned null"
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(toDto(resolution));
    }

    private AppUser user(Principal principal) {
        AppUser user = users.requireUser(principal);
        modules.requireEnabled(user, ModuleService.PAYROLL);
        return user;
    }

    private static LocalDate parseEventDate(String value) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest("eventDate должен быть в формате yyyy-MM-dd");
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            throw ApiException.badRequest("eventDate должен быть в формате yyyy-MM-dd");
        }
    }

    private static YearMonth parseMonth(String value, String message) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(message);
        }
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException ex) {
            throw ApiException.badRequest(message);
        }
    }

    private static List<YearMonth> parseProofMonths(List<String> values) {
        if (values == null) {
            throw ApiException.badRequest(
                    "provenNoPayrollMonths должен быть передан явно, даже если список пуст"
            );
        }
        return values.stream()
                .map(value -> parseMonth(
                        value,
                        "Каждый provenNoPayrollMonths должен быть в формате yyyy-MM"
                ))
                .toList();
    }

    private static VacationPayPreviewDto toDto(VacationPayApplicationService.Resolution resolution) {
        return new VacationPayPreviewDto(
                resolution.eventDate().toString(),
                resolution.eventMonth().toString(),
                resolution.requestedAbsencePeriodId(),
                resolution.discoveryThroughMonth().toString(),
                resolution.provenNoPayrollMonths().stream().map(YearMonth::toString).toList(),
                resolution.ready(),
                enumName(resolution.selectedBasis()),
                enumName(resolution.blockingStage()),
                resolution.blockingReason(),
                resolution.upstreamBlockingReason(),
                resolution.currencyCode(),
                resolution.vacationPayMinor(),
                resolution.payableCalendarDays()
        );
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
