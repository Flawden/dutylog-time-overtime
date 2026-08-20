package ru.daniil.shifts.web;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.daniil.shifts.dto.Dtos.PayrollCompensationComponentCreateRequest;
import ru.daniil.shifts.dto.Dtos.PayrollCompensationComponentVersionDto;
import ru.daniil.shifts.dto.Dtos.PayrollCompensationComponentVersionRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CompensationComponentConfigurationService;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.ModuleService;

import java.security.Principal;
import java.util.List;

/**
 * Native generic compensation component configuration API.
 *
 * There is intentionally no DELETE route:
 * stable component identity and effective history are not disposable state.
 */
@RestController
@RequestMapping({
        "/api/payroll/compensation-components",
        "/api/v1/payroll/compensation-components"
})
public class CompensationComponentController {

    private final CurrentUserService users;
    private final ModuleService modules;
    private final CompensationComponentConfigurationService components;

    public CompensationComponentController(
            CurrentUserService users,
            ModuleService modules,
            CompensationComponentConfigurationService components
    ) {
        this.users = users;
        this.modules = modules;
        this.components = components;
    }

    @GetMapping
    public ResponseEntity<List<PayrollCompensationComponentVersionDto>> history(
            Principal principal
    ) {
        return ResponseEntity.ok()
                .cacheControl(
                        CacheControl.noStore()
                )
                .body(
                        components.history(
                                user(principal)
                        )
                );
    }

    @GetMapping("/effective/{month}")
    public ResponseEntity<List<PayrollCompensationComponentVersionDto>> effective(
            @PathVariable("month") String month,
            Principal principal
    ) {
        return ResponseEntity.ok()
                .cacheControl(
                        CacheControl.noStore()
                )
                .body(
                        components.effective(
                                user(principal),
                                month
                        )
                );
    }

    @PostMapping
    public ResponseEntity<PayrollCompensationComponentVersionDto> create(
            @Valid @RequestBody(required = false)
            PayrollCompensationComponentCreateRequest request,
            Principal principal
    ) {
        return ResponseEntity.status(201)
                .body(
                        components.create(
                                user(principal),
                                request
                        )
                );
    }

    @PutMapping("/{componentId}/versions/{month}")
    public PayrollCompensationComponentVersionDto upsertVersion(
            @PathVariable("componentId") Long componentId,
            @PathVariable("month") String month,
            @Valid @RequestBody(required = false)
            PayrollCompensationComponentVersionRequest request,
            Principal principal
    ) {
        return components.upsertVersion(
                user(principal),
                componentId,
                month,
                request
        );
    }

    private AppUser user(
            Principal principal
    ) {
        AppUser user =
                users.requireUser(
                        principal
                );

        modules.requireEnabled(
                user,
                ModuleService.PAYROLL
        );

        return user;
    }
}
